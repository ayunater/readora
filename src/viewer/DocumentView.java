package viewer;

import ui.Ui;
import viewer.ops.HighlightArea;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * The scrolling document pane: owns zoom, fit modes, the current page and
 * navigation. It draws through a {@link PageCanvas} and reports changes
 * to the toolbar and thumbnail strip via listeners, so those components
 * need no knowledge of how pages are laid out.
 */
public final class DocumentView extends Ui.Card {

    public static final double MIN_ZOOM = 0.10;
    public static final double MAX_ZOOM = 4.00;
    private static final double[] STEPS = {0.10, 0.25, 0.50, 0.75, 1.00, 1.25, 1.50, 2.00, 3.00, 4.00};

    /** AUTO = the comfortable size picked when a document opens; it keeps adapting to the window until the user zooms. */
    private enum Fit { NONE, WIDTH, PAGE, AUTO }

    private final PageCanvas canvas;
    private final ZoomingScrollPane scroll;
    private PageGeometry geo = PageGeometry.empty();
    private double zoom = 1.0;
    private Fit fit = Fit.NONE;
    private int currentPage = 0;

    private IntConsumer pageListener = p -> { };
    private DoubleConsumer zoomListener = z -> { };

    public DocumentView(RenderService render) {
        super(new BorderLayout());
        canvas = new PageCanvas(render);
        scroll = new ZoomingScrollPane(canvas);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(48);
        scroll.getHorizontalScrollBar().setUnitIncrement(48);
        add(scroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        scroll.getVerticalScrollBar().addAdjustmentListener(e -> onScrolled());
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (fit != Fit.NONE && geo.count() > 0) applyFit();
                onScrolled();
            }
        });
        installPanning();
        installKeys();
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    public void onPageChanged(IntConsumer l) { pageListener = l; }
    public void onZoomChanged(DoubleConsumer l) { zoomListener = l; }

    // ------------------------------------------------------------------
    // Document
    // ------------------------------------------------------------------

    /** Shows a (new or reloaded) document. {@code keepView} preserves zoom and scroll position. */
    public void setDocument(PageGeometry g, boolean keepView) {
        int keepPage = currentPage;
        this.geo = g;
        if (!keepView) {
            fit = Fit.AUTO;
            currentPage = 0;
            zoom = 1.0;
        }
        canvas.setLayoutData(g, zoom);
        if (!keepView && g.count() > 0) {
            SwingUtilities.invokeLater(() -> {
                applyFit();
                // On mixed-size documents the canvas is as wide as the widest page; start centred.
                JViewport vp = scroll.getViewport();
                int cx = Math.max(0, (canvas.getPreferredSize().width - vp.getWidth()) / 2);
                vp.setViewPosition(new Point(cx, 0));
                onScrolled();
            });
        } else if (keepView) {
            SwingUtilities.invokeLater(() -> {
                goToPage(Math.min(keepPage, Math.max(0, g.count() - 1)));
                onScrolled();
            });
        }
        zoomListener.accept(zoom);
    }

    public void clear() {
        setDocument(PageGeometry.empty(), false);
        pageListener.accept(0);
    }

    public int pageCount()    { return geo.count(); }
    public int currentPage()  { return currentPage; }
    public double zoom()      { return zoom; }
    public void repaintPages() { canvas.repaint(); }
    public JComponent canvas() { return canvas; }

    // ------------------------------------------------------------------
    // Zoom
    // ------------------------------------------------------------------

    public void zoomIn() {
        for (double s : STEPS) if (s > zoom + 0.001) { setZoom(s); return; }
        setZoom(MAX_ZOOM);
    }

    public void zoomOut() {
        for (int i = STEPS.length - 1; i >= 0; i--) if (STEPS[i] < zoom - 0.001) { setZoom(STEPS[i]); return; }
        setZoom(MIN_ZOOM);
    }

    public void setZoom(double z) {
        fit = Fit.NONE;
        setZoomInternal(clamp(z), false);
    }

    public void fitWidth() {
        fit = Fit.WIDTH;
        applyFit();
    }

    public void fitPage() {
        fit = Fit.PAGE;
        applyFit();
    }

    private void applyFit() {
        if (geo.count() == 0) return;
        double z = switch (fit) {
            case WIDTH -> fitWidthZoom();
            case PAGE -> fitPageZoom();
            default -> Math.min(1.25, Math.max(0.5, fitWidthZoom()));      // AUTO
        };
        setZoomInternal(clamp(z), false);
    }

    private double fitWidthZoom() {
        int p = Math.max(0, Math.min(currentPage, geo.count() - 1));
        int avail = Math.max(100, scroll.getViewport().getWidth() - 2 * PageCanvas.MARGIN - scrollbarWidth());
        return avail / (geo.width(p) * (double) PageGeometry.PX_PER_PT);
    }

    private double fitPageZoom() {
        int p = Math.max(0, Math.min(currentPage, geo.count() - 1));
        int availW = Math.max(100, scroll.getViewport().getWidth() - 2 * PageCanvas.MARGIN - scrollbarWidth());
        int availH = Math.max(100, scroll.getViewport().getHeight() - 2 * PageCanvas.MARGIN);
        return Math.min(availW / (geo.width(p) * (double) PageGeometry.PX_PER_PT),
                availH / (geo.height(p) * (double) PageGeometry.PX_PER_PT));
    }

    private int scrollbarWidth() { return scroll.getVerticalScrollBar().getPreferredSize().width; }

    private static double clamp(double z) { return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z)); }

    /** Applies a zoom while keeping the same spot of the document at the top of the view. */
    private void setZoomInternal(double z, boolean initial) {
        if (Math.abs(z - zoom) < 0.0005 && !initial) return;
        JViewport vp = scroll.getViewport();
        int page = Math.max(0, canvas.pageAtY(vp.getViewPosition().y));
        double frac = 0;
        if (geo.count() > 0) {
            frac = (vp.getViewPosition().y - canvas.pageTop(page)) / (double) canvas.pageH(page);
        }
        double xCenter = vp.getViewPosition().x + vp.getWidth() / 2.0;
        double xFrac = canvas.getWidth() > 0 ? xCenter / canvas.getWidth() : 0.5;

        zoom = z;
        canvas.setLayoutData(geo, zoom);
        scroll.validate();                                    // so the new extent is known
        if (geo.count() > 0 && !initial) {
            int ny = canvas.pageTop(page) + (int) Math.round(frac * canvas.pageH(page));
            int nx = (int) Math.round(xFrac * canvas.getPreferredSize().width - vp.getWidth() / 2.0);
            vp.setViewPosition(new Point(Math.max(0, nx), Math.max(0, ny)));
        }
        zoomListener.accept(zoom);
        onScrolled();
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    public void goToPage(int page) {
        if (geo.count() == 0) return;
        page = Math.max(0, Math.min(page, geo.count() - 1));
        JViewport vp = scroll.getViewport();
        vp.setViewPosition(new Point(vp.getViewPosition().x, Math.max(0, canvas.pageTop(page) - PageCanvas.MARGIN / 2)));
        setCurrent(page);
    }

    public void nextPage() { goToPage(currentPage + 1); }
    public void prevPage() { goToPage(currentPage - 1); }

    /** Scrolls so a search hit is centred in the view. */
    public void showHighlight(HighlightArea h) {
        if (h == null || geo.count() == 0) return;
        Rectangle r = canvas.highlightBounds(h);
        JViewport vp = scroll.getViewport();
        int y = Math.max(0, r.y - vp.getHeight() / 3);
        int x = Math.max(0, r.x + r.width / 2 - vp.getWidth() / 2);
        vp.setViewPosition(new Point(Math.min(x, Math.max(0, canvas.getPreferredSize().width - vp.getWidth())), y));
        setCurrent(h.getPage() - 1);
    }

    public void setHighlights(List<HighlightArea> all, HighlightArea current) {
        canvas.setHighlights(all, current);
    }

    private void onScrolled() {
        if (geo.count() == 0) return;
        JViewport vp = scroll.getViewport();
        int refY = vp.getViewPosition().y + vp.getHeight() / 3;
        setCurrent(Math.max(0, canvas.pageAtY(refY)));
    }

    private void setCurrent(int page) {
        if (page != currentPage) {
            currentPage = page;
            pageListener.accept(page);
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private void installPanning() {
        MouseAdapter pan = new MouseAdapter() {
            Point start, origin;
            @Override public void mousePressed(MouseEvent e) {
                canvas.requestFocusInWindow();
                start = e.getLocationOnScreen();
                origin = scroll.getViewport().getViewPosition();
                canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (start == null) return;
                Point now = e.getLocationOnScreen();
                JViewport vp = scroll.getViewport();
                int maxX = Math.max(0, canvas.getWidth() - vp.getWidth());
                int maxY = Math.max(0, canvas.getHeight() - vp.getHeight());
                int nx = Math.max(0, Math.min(maxX, origin.x - (now.x - start.x)));
                int ny = Math.max(0, Math.min(maxY, origin.y - (now.y - start.y)));
                vp.setViewPosition(new Point(nx, ny));
            }
            @Override public void mouseReleased(MouseEvent e) {
                start = null;
                canvas.setCursor(Cursor.getDefaultCursor());
            }
        };
        canvas.addMouseListener(pan);
        canvas.addMouseMotionListener(pan);
    }

    private void installKeys() {
        InputMap im = canvas.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = canvas.getActionMap();
        bind(im, am, "zoomIn",  KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), this::zoomIn);
        bind(im, am, "zoomIn2", KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), this::zoomIn);
        bind(im, am, "zoomOut", KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), this::zoomOut);
        bind(im, am, "zoomOut2", KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), this::zoomOut);
        bind(im, am, "zoom100", KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), () -> setZoom(1.0));
        bind(im, am, "nextPage", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), this::nextPage);
        bind(im, am, "prevPage", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), this::prevPage);
        bind(im, am, "firstPage", KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), () -> goToPage(0));
        bind(im, am, "lastPage", KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), () -> goToPage(Integer.MAX_VALUE));
        bind(im, am, "pgDn", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), () -> scrollBlock(1));
        bind(im, am, "pgUp", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), () -> scrollBlock(-1));
        bind(im, am, "down", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), () -> scrollUnit(1));
        bind(im, am, "up", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), () -> scrollUnit(-1));
    }

    private static void bind(InputMap im, ActionMap am, String name, KeyStroke ks, Runnable r) {
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    private void scrollBlock(int dir) {
        JViewport vp = scroll.getViewport();
        Point p = vp.getViewPosition();
        int max = Math.max(0, canvas.getHeight() - vp.getHeight());
        vp.setViewPosition(new Point(p.x, Math.max(0, Math.min(max, p.y + dir * (int) (vp.getHeight() * 0.9)))));
    }

    private void scrollUnit(int dir) {
        JViewport vp = scroll.getViewport();
        Point p = vp.getViewPosition();
        int max = Math.max(0, canvas.getHeight() - vp.getHeight());
        vp.setViewPosition(new Point(p.x, Math.max(0, Math.min(max, p.y + dir * 60))));
    }

    /** Scroll pane where Ctrl + wheel zooms instead of scrolling. */
    private final class ZoomingScrollPane extends JScrollPane {
        ZoomingScrollPane(Component c) { super(c); }

        @Override
        protected void processMouseWheelEvent(MouseWheelEvent e) {
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) zoomIn(); else if (e.getWheelRotation() > 0) zoomOut();
                e.consume();
                return;
            }
            super.processMouseWheelEvent(e);
        }
    }
}
