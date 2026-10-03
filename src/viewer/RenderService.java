package viewer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Renders pages on a single background thread into a size-bounded cache.
 *
 * <ul>
 *   <li><b>Lazy:</b> only pages the view asks for are rendered. The view
 *       tells the service which pages are currently wanted; requests for
 *       pages that scrolled away are skipped before any work is done.</li>
 *   <li><b>Newest first:</b> the most recent request runs first, so the
 *       page you scrolled to is drawn before the ones you flew past.</li>
 *   <li><b>Bounded:</b> the page cache and thumbnail cache evict the least
 *       recently used image once they exceed their byte budget.</li>
 *   <li><b>Robust:</b> a page that fails to render is remembered as failed
 *       (drawn as an error placeholder) and never stops other pages.
 *       Oversized requests are scaled down to a pixel cap instead of
 *       exhausting memory.</li>
 * </ul>
 */
public final class RenderService {

    /** Largest page bitmap we will ever allocate (pixels). ~36 MP = ~144 MB as RGB int. */
    static final long MAX_PIXELS = 36_000_000L;

    private final PdfSession session;
    private final Runnable onRendered;           // invoked on the EDT, coalesced

    private final ImageCache pageCache;
    private final ImageCache thumbCache = new ImageCache(48L << 20);
    private final Set<Key> failed = Collections.synchronizedSet(new HashSet<>());
    private final Set<Key> pending = Collections.synchronizedSet(new HashSet<>());

    private volatile Set<Integer> wantedPages = Set.of();
    private volatile Set<Integer> wantedThumbs = Set.of();

    private final AtomicLong seq = new AtomicLong();
    private final AtomicBoolean repaintQueued = new AtomicBoolean();
    private final ThreadPoolExecutor exec;

    private PDFRenderer renderer;
    private int rendererGeneration = -1;

    /** Identifies one rendered bitmap. {@code scaleMilli} is the scale x 1000. */
    record Key(int generation, int page, int scaleMilli, boolean thumb) { }

    /**
     * Makes PDFBox scan the system fonts now, on a background thread. The
     * first render otherwise pays that 1-2 second cost while you wait.
     */
    public static void warmUp() {
        Thread t = new Thread(() -> {
            try {
                // Asking the mapper for any font forces the one-time system font scan.
                org.apache.pdfbox.pdmodel.font.FontMappers.instance()
                        .getFontBoxFont("Helvetica", null);
            } catch (Throwable ignored) { /* purely an optimisation */ }
        }, "readora-font-warmup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public RenderService(PdfSession session, Runnable onRendered) {
        this.session = session;
        this.onRendered = onRendered;
        long budget = Math.min(512L << 20, Runtime.getRuntime().maxMemory() / 4);
        this.pageCache = new ImageCache(Math.max(96L << 20, budget));
        this.exec = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "readora-render");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    // ------------------------------------------------------------------
    // Requests from the UI thread
    // ------------------------------------------------------------------

    /** Pages currently on screen (plus a small buffer). */
    public void setWantedPages(Set<Integer> pages)  { wantedPages = pages; }
    public void setWantedThumbs(Set<Integer> pages) { wantedThumbs = pages; }

    /**
     * Returns the page bitmap for {@code scale}, or {@code null} while it
     * is still rendering (a render is queued). If a bitmap of the same
     * page at another zoom exists, {@link #stale} returns it for use as a
     * stand-in so the view never flashes blank while zooming.
     */
    public BufferedImage page(int page, float scale, boolean visible) {
        return fetch(pageCache, new Key(session.generation(), page, milli(scale), false), visible ? 0 : 1);
    }

    public BufferedImage thumb(int page, float scale) {
        return fetch(thumbCache, new Key(session.generation(), page, milli(scale), true), 2);
    }

    /** Best available bitmap for a page at any scale (for stretching while the real one renders). */
    public BufferedImage stale(int page, boolean thumb) {
        return (thumb ? thumbCache : pageCache).anyForPage(session.generation(), page);
    }

    public boolean failed(int page, float scale, boolean thumb) {
        return failed.contains(new Key(session.generation(), page, milli(scale), thumb));
    }

    /**
     * @param priority 0 = a page you can see, 1 = prefetch, 2 = thumbnails.
     *                 Lower runs first; within a priority the newest request runs first.
     */
    private BufferedImage fetch(ImageCache cache, Key key, int priority) {
        BufferedImage img = cache.get(key);
        if (img != null) return img;
        if (failed.contains(key)) return null;
        if (pending.add(key)) {
            exec.execute(new Task(key, priority, seq.incrementAndGet()));
        }
        return null;
    }

    private static int milli(float scale) { return Math.max(1, Math.round(scale * 1000f)); }

    /** Drops every cached bitmap (zoom change keeps stale ones for stand-ins; this is for new documents). */
    public void clear() {
        pageCache.clear();
        thumbCache.clear();
        failed.clear();
        pending.clear();
    }

    public void shutdown() {
        exec.shutdownNow();
        clear();
    }

    // ------------------------------------------------------------------
    // Background work
    // ------------------------------------------------------------------

    private final class Task implements Runnable, Comparable<Task> {
        final Key key;
        final int priority;
        final long order;

        Task(Key key, int priority, long order) { this.key = key; this.priority = priority; this.order = order; }

        @Override public int compareTo(Task o) {
            if (priority != o.priority) return Integer.compare(priority, o.priority);
            return Long.compare(o.order, order);                                   // newest first
        }

        @Override public void run() {
            try {
                Set<Integer> wanted = key.thumb ? wantedThumbs : wantedPages;
                if (key.generation != session.generation() || !wanted.contains(key.page)) return;
                BufferedImage img = render(key);
                if (img == null) return;
                (key.thumb ? thumbCache : pageCache).put(key, img);
                requestRepaint();
            } catch (OutOfMemoryError oom) {
                pageCache.clear();
                failed.add(key);
                requestRepaint();
            } catch (Throwable t) {
                System.err.println("Page " + (key.page + 1) + " failed to render: " + t);
                failed.add(key);
                requestRepaint();
            } finally {
                pending.remove(key);
            }
        }
    }

    private BufferedImage render(Key key) throws Exception {
        session.lock().lock();
        try {
            PDDocument doc = session.document();
            if (doc == null || key.generation != session.generation()) return null;
            if (renderer == null || rendererGeneration != key.generation) {
                renderer = new PDFRenderer(doc);
                renderer.setSubsamplingAllowed(false);
                rendererGeneration = key.generation;
            }
            float scale = key.scaleMilli / 1000f;
            float[] wh = PageGeometry.sizeOf(doc.getPage(key.page));
            float w = wh[0], h = wh[1];
            long px = (long) Math.ceil(w * scale) * (long) Math.ceil(h * scale);
            if (px > MAX_PIXELS) scale *= (float) Math.sqrt((double) MAX_PIXELS / px);
            return renderer.renderImage(key.page, scale, ImageType.RGB);
        } finally {
            session.lock().unlock();
        }
    }

    private void requestRepaint() {
        if (repaintQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                repaintQueued.set(false);
                onRendered.run();
            });
        }
    }

    // ------------------------------------------------------------------
    // Byte-bounded LRU cache
    // ------------------------------------------------------------------

    private static final class ImageCache {
        private final long maxBytes;
        private long bytes;
        private final LinkedHashMap<Key, BufferedImage> map = new LinkedHashMap<>(64, 0.75f, true);

        ImageCache(long maxBytes) { this.maxBytes = maxBytes; }

        synchronized BufferedImage get(Key k) { return map.get(k); }

        synchronized void put(Key k, BufferedImage img) {
            BufferedImage prev = map.put(k, img);
            if (prev != null) bytes -= size(prev);
            bytes += size(img);
            Iterator<Map.Entry<Key, BufferedImage>> it = map.entrySet().iterator();
            while (bytes > maxBytes && map.size() > 1 && it.hasNext()) {
                Map.Entry<Key, BufferedImage> e = it.next();
                if (e.getKey().equals(k)) continue;
                bytes -= size(e.getValue());
                it.remove();
            }
        }

        synchronized BufferedImage anyForPage(int generation, int page) {
            BufferedImage best = null;
            for (Map.Entry<Key, BufferedImage> e : map.entrySet()) {
                Key k = e.getKey();
                if (k.generation() == generation && k.page() == page) best = e.getValue();   // later = more recent
            }
            return best;
        }

        synchronized void clear() { map.clear(); bytes = 0; }

        private static long size(BufferedImage i) { return (long) i.getWidth() * i.getHeight() * 4L; }
    }
}
