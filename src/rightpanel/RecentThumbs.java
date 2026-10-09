package rightpanel;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * First-page previews for the Home screen's recent files. Rendered one at a time on a background thread
 * and remembered in a small cache, so Home appears instantly and fills in as previews arrive.
 * Password-protected files are never opened here (their preview would defeat the password); they show a lock.
 */
public final class RecentThumbs {

    /** A finished preview, or the reason there is none. */
    public static final class Thumb {
        public final BufferedImage image;
        public final boolean locked;
        public final boolean failed;

        Thumb(BufferedImage image, boolean locked, boolean failed) {
            this.image = image;
            this.locked = locked;
            this.failed = failed;
        }
    }

    private static final int WIDTH_PX = 300;
    private static final Map<String, Thumb> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Thumb> e) { return size() > 40; }
    };
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "readora-recent-thumbs");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private RecentThumbs() { }

    private static String key(File f) { return f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length(); }

    /**
     * @return the preview if it is ready; otherwise null, and {@code onReady} runs on the UI thread when it is
     */
    public static Thumb get(File f, Runnable onReady) {
        String k = key(f);
        synchronized (CACHE) {
            Thumb t = CACHE.get(k);
            if (t != null) return t;
        }
        POOL.execute(() -> {
            Thumb t = render(f);
            synchronized (CACHE) { CACHE.put(k, t); }
            SwingUtilities.invokeLater(onReady);
        });
        return null;
    }

    private static Thumb render(File f) {
        if (!f.isFile()) return new Thumb(null, false, true);
        try (PDDocument d = Loader.loadPDF(f)) {
            if (d.getNumberOfPages() == 0) return new Thumb(null, false, true);
            float pageW = d.getPage(0).getCropBox().getWidth();
            float scale = WIDTH_PX / Math.max(1f, pageW);
            return new Thumb(new PDFRenderer(d).renderImage(0, scale, ImageType.RGB), false, false);
        } catch (InvalidPasswordException e) {
            return new Thumb(null, true, false);
        } catch (Exception | OutOfMemoryError e) {
            return new Thumb(null, false, true);
        }
    }
}
