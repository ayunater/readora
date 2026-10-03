package dialogs;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import rightpanel.AppFonts;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders a PDF file entry as a small card: a thumbnail of the PDF's
 * first page on the left, and its filename + page count on the right.
 *
 * Originally built for {@link MergeDialog}'s file list ({@link
 * ListCellRenderer}), and reused in "compact" mode as a {@link
 * TableCellRenderer} for the {@code recent} and {@code starred}
 * screens' file tables, so recently-opened and starred documents show
 * the same thumbnail-plus-name treatment as the merge dialog instead of
 * a bare filename string. Compact mode shrinks the thumbnail and drops
 * the page-count line so the card fits a normal table row height.
 *
 * <p><b>Thumbnails load asynchronously.</b> Opening the Recent/Starred
 * screens used to block on rendering every visible row's first page
 * before the panel appeared. Rendering now happens on a small
 * background thread pool ({@link #THUMBNAIL_EXECUTOR}): a row shows a
 * blank thumbnail immediately, and the real image pops in once
 * rendered, with the owning list/table repainted at that point via
 * {@link SwingUtilities#invokeLater}. Results are cached in {@link
 * #THUMBNAIL_CACHE} (keyed by file path + compact/full mode) so
 * scrolling or re-opening the same file doesn't re-render it, and
 * {@link #PENDING} tracks in-flight loads so the same file isn't
 * queued twice while its first render is still running.
 *
 * <p>Fonts are resolved via {@link AppFonts#body(int, int)}, which
 * falls back gracefully on platforms where "Segoe UI" (a Windows system
 * font) isn't installed.
 */
public class PdfCellRenderer
        extends JPanel
        implements ListCellRenderer<File>, TableCellRenderer {

    /** Thumbnail width/height used in the full-size (merge dialog) layout. */
    private static final int FULL_THUMB_WIDTH = 70;
    private static final int FULL_THUMB_HEIGHT = 100;

    /** Thumbnail width/height used in compact (table row) layout. */
    private static final int COMPACT_THUMB_WIDTH = 42;
    private static final int COMPACT_THUMB_HEIGHT = 58;

    /**
     * A rendered thumbnail plus the page-count text that came from the
     * same PDFBox open, cached together so a cache hit never needs to
     * reopen the file just for the count.
     */
    private static class Thumb {

        final ImageIcon icon;
        final String pageInfo;

        Thumb(ImageIcon icon, String pageInfo) {
            this.icon = icon;
            this.pageInfo = pageInfo;
        }
    }

    /**
     * Shared across every {@code PdfCellRenderer} instance (list and
     * table, recent and starred) so the same file only ever gets
     * rendered once per mode. Keyed by absolute path + "#compact"/"#full"
     * since the two modes render at different sizes/DPIs.
     */
    private static final Map<String, Thumb> THUMBNAIL_CACHE = new ConcurrentHashMap<>();

    /** Keys currently being rendered, so a fast scroll doesn't queue the same file repeatedly. */
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    /** Small fixed pool for thumbnail rendering; daemon threads so they never block JVM shutdown. */
    private static final ExecutorService THUMBNAIL_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread thread = new Thread(r, "pdf-thumbnail-loader");
                thread.setDaemon(true);
                return thread;
            });

    /** Whether this instance renders the compact (table row) layout. */
    private final boolean compact;

    /** First-page thumbnail of the PDF being rendered for this row. */
    JLabel thumbnail =
            new JLabel();

    /** The PDF's filename. */
    JLabel name =
            new JLabel();

    /** Page count, or an error message if the file couldn't be read. Hidden in compact mode. */
    JLabel info =
            new JLabel();

    /** Full-size constructor, used by {@link MergeDialog}'s file list. */
    public PdfCellRenderer() {
        this(false);
    }

    /**
     * @param compact true for a smaller thumbnail with no page-count line,
     *                sized to fit inside a normal JTable row (used by the
     *                {@code recent} and {@code starred} screens)
     */
    public PdfCellRenderer(boolean compact) {

        this.compact = compact;

        setLayout(
                new BorderLayout(
                        10,
                        10
                )
        );

        thumbnail.setPreferredSize(
                compact
                        ? new Dimension(COMPACT_THUMB_WIDTH, COMPACT_THUMB_HEIGHT)
                        : new Dimension(FULL_THUMB_WIDTH, FULL_THUMB_HEIGHT)
        );

        JPanel text =
                new JPanel();

        text.setLayout(
                new BoxLayout(
                        text,
                        BoxLayout.Y_AXIS
                )
        );

        name.setFont(
                AppFonts.body(
                        Font.BOLD,
                        compact ? 13 : 15
                )
        );

        info.setFont(
                AppFonts.body(
                        Font.PLAIN,
                        12
                )
        );

        text.add(name);

        if (!compact) {
            text.add(info);
        }

        add(thumbnail, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);

        setBorder(
                BorderFactory.createEmptyBorder(
                        5,
                        compact ? 8 : 5,
                        5,
                        5
                )
        );

        setOpaque(true);
    }

    /**
     * Configures the shared labels/thumbnail for {@code file} and picks
     * selected- vs normal-state colors. Shared by both the list and
     * table rendering entry points below.
     *
     * <p>If a thumbnail for this file is already cached, it's applied
     * immediately (no flicker). Otherwise the thumbnail is left blank
     * for this paint and a background render is kicked off via {@link
     * #requestThumbnail}, which repaints {@code repaintTarget} once the
     * result is ready.
     *
     * @param file the PDF this row represents
     * @param selected whether this row is currently selected
     * @param selectionBg background color to use when selected
     * @param selectionFg foreground color to use when selected
     * @param normalBg background color to use when not selected
     * @param normalFg foreground color to use when not selected
     * @param repaintTarget the list/table this row belongs to, repainted
     *                      once a background-rendered thumbnail is ready
     */
    private void configureFor(
            File file,
            boolean selected,
            Color selectionBg,
            Color selectionFg,
            Color normalBg,
            Color normalFg,
            JComponent repaintTarget
    ) {

        name.setText(
                file != null ? file.getName() : ""
        );

        if (file == null) {

            thumbnail.setIcon(null);

            if (!compact) {
                info.setText("");
            }

        } else {

            String key = file.getAbsolutePath() + (compact ? "#compact" : "#full");

            Thumb cached = THUMBNAIL_CACHE.get(key);

            if (cached != null) {

                thumbnail.setIcon(cached.icon);

                if (!compact) {
                    info.setText(cached.pageInfo);
                }

            } else {

                // Nothing to show yet this paint — requestThumbnail will
                // trigger a repaint once the background render finishes.
                thumbnail.setIcon(null);

                if (!compact) {
                    info.setText("Loading\u2026");
                }

                requestThumbnail(file, key, repaintTarget);
            }
        }

        if (selected) {
            setBackground(selectionBg);
            setForeground(selectionFg);
        } else {
            setBackground(normalBg);
            setForeground(normalFg);
        }
    }

    /**
     * Renders {@code file}'s first-page thumbnail (and page count) on
     * {@link #THUMBNAIL_EXECUTOR} rather than the calling (paint)
     * thread, caches the result under {@code key}, and repaints {@code
     * repaintTarget} on the EDT so the now-cached thumbnail actually
     * gets drawn. A given key is only ever in flight once — a repeat
     * call while still pending (e.g. from fast scrolling) is a no-op.
     */
    private void requestThumbnail(File file, String key, JComponent repaintTarget) {

        if (!PENDING.add(key)) {
            return;
        }

        int thumbWidth = compact ? COMPACT_THUMB_WIDTH : FULL_THUMB_WIDTH;
        int dpi = compact ? 32 : 35;

        THUMBNAIL_EXECUTOR.submit(() -> {

            ImageIcon icon = null;
            String pageInfo = "Preview unavailable";

            try (PDDocument doc =
                         Loader.loadPDF(file)) {

                PDFRenderer renderer =
                        new PDFRenderer(doc);

                // Page 0 = first page (PDFBox page indices are 0-based).
                BufferedImage image =
                        renderer.renderImageWithDPI(
                                0,
                                dpi
                        );

                // Fixed width, proportional height (-1 preserves aspect ratio).
                Image scaled =
                        image.getScaledInstance(
                                thumbWidth,
                                -1,
                                Image.SCALE_SMOOTH
                        );

                icon = new ImageIcon(scaled);

                pageInfo = doc.getNumberOfPages() + " Pages";

            } catch (Exception ex) {

                // Corrupt/unreadable/missing file: cache "no thumbnail"
                // so we don't keep retrying it on every repaint.
            }

            THUMBNAIL_CACHE.put(key, new Thumb(icon, pageInfo));
            PENDING.remove(key);

            if (repaintTarget != null) {
                SwingUtilities.invokeLater(repaintTarget::repaint);
            }
        });
    }

    /**
     * Called by Swing for every visible row to configure this shared
     * renderer instance for the given file, then returns {@code this}
     * to be painted at that row's position.
     *
     * @param file the PDF this row represents
     * @param selected whether this row is currently selected, used to
     *                 pick selected- vs normal-state colors
     * @return this renderer, configured and ready to paint
     */
    @Override
    public Component getListCellRendererComponent(
            JList<? extends File> list,
            File file,
            int index,
            boolean selected,
            boolean focus
    ) {

        configureFor(
                file,
                selected,
                list.getSelectionBackground(),
                list.getSelectionForeground(),
                list.getBackground(),
                list.getForeground(),
                list
        );

        return this;
    }

    /**
     * Table-row entry point used by the {@code recent} and {@code
     * starred} screens. {@code value} is expected to be the row's
     * filename or path {@link String}; the actual {@link File} to
     * render is resolved from the table's "Path" column (column 1) so
     * the thumbnail matches the file actually on disk rather than just
     * its display name.
     */
    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
    ) {

        File file = null;

        Object pathValue = table.getValueAt(row, 1);

        if (pathValue != null) {
            file = new File(pathValue.toString());
        }

        configureFor(
                file,
                isSelected,
                table.getSelectionBackground(),
                table.getSelectionForeground(),
                table.getBackground(),
                table.getForeground(),
                table
        );

        return this;
    }
}
