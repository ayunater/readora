package viewer;

import rightpanel.AppFonts;
import viewer.edit.PageOverlay;
import viewer.ops.HighlightArea;
import ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * One component that draws every page of the document, stacked vertically.
 *
 * <p>It does not create a component per page. It computes where each page
 * sits, and in {@code paintComponent} draws only the pages that intersect
 * the visible area, fetching bitmaps from the {@link RenderService}. That
 * keeps memory and startup time flat whether the PDF has 3 pages or 3,000,
 * and lets every page have its own size.</p>
 */
final class PageCanvas extends JComponent implements Scrollable, viewer.edit.PageHost {

    static final int MARGIN = 24;
    static final int GAP = 16;

    private final RenderService render;

    private PageGeometry geo = PageGeometry.empty();
    private double zoom = 1.0;
    private int[] tops = new int[0];
    private int contentW, contentH;

    private PageOverlay overlay;

    void setOverlay(PageOverlay o) { overlay = o; repaint(); }

    // ---- viewer.edit.PageHost: what the editor needs to know about the pages ----
    @Override public JComponent component()          { return this; }
    @Override public int pageCount()                 { return geo.count(); }
    @Override public int pageAt(Point p)             { return Math.max(0, pageAtY(p.y)); }
    @Override public double pxPerPt()                { return zoom * PageGeometry.PX_PER_PT; }
    @Override public double pageWidthPt(int page)    { return geo.width(page); }
    @Override public double pageHeightPt(int page)   { return geo.height(page); }

    private final Map<Integer, List<HighlightArea>> highlights = new HashMap<>();
    private HighlightArea current;

    PageCanvas(RenderService render) {
        this.render = render;
        setOpaque(false);
        setFocusable(true);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    void setLayoutData(PageGeometry geo, double zoom) {
        this.geo = geo;
        this.zoom = zoom;
        int n = geo.count();
        tops = new int[n];
        int y = MARGIN;
        for (int i = 0; i < n; i++) {
            tops[i] = y;
            y += pageH(i) + GAP;
        }
        contentH = n == 0 ? 0 : y - GAP + MARGIN;
        int maxW = 0;
        for (int i = 0; i < n; i++) maxW = Math.max(maxW, pageW(i));
        contentW = maxW + 2 * MARGIN;
        revalidate();
        repaint();
    }

    double zoom()                { return zoom; }
    PageGeometry geometry()      { return geo; }
    int pageW(int i)             { return Math.max(1, Math.round(geo.width(i) * (float) (zoom * PageGeometry.PX_PER_PT))); }
    int pageH(int i)             { return Math.max(1, Math.round(geo.height(i) * (float) (zoom * PageGeometry.PX_PER_PT))); }
    int pageTop(int i)           { return tops[i]; }

    @Override
    public Rectangle pageBounds(int i) {
        int w = pageW(i);
        return new Rectangle((getWidth() - w) / 2, tops[i], w, pageH(i));
    }

    /** Index of the page whose band (page plus the gap below it) contains {@code y}; clamped. */
    int pageAtY(int y) {
        int n = tops.length;
        if (n == 0) return -1;
        int lo = 0, hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (tops[mid] <= y) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    @Override public Dimension getPreferredSize() { return new Dimension(contentW, contentH); }

    // ------------------------------------------------------------------
    // Highlights (search hits)
    // ------------------------------------------------------------------

    void setHighlights(List<HighlightArea> all, HighlightArea current) {
        highlights.clear();
        if (all != null) {
            for (HighlightArea h : all) {
                int p = h.getPage() - 1;                 // search pages are 1-based
                highlights.computeIfAbsent(p, k -> new ArrayList<>()).add(h);
            }
        }
        this.current = current;
        repaint();
    }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    private float deviceScale() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) return 1f;
        AffineTransform t = gc.getDefaultTransform();
        return (float) Math.max(1.0, t.getScaleX());
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (geo.count() == 0) {
            paintEmptyState((Graphics2D) g0);
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Rectangle vis = getVisibleRect();
            int first = pageAtY(vis.y);
            int last = pageAtY(vis.y + vis.height);
            Set<Integer> wanted = new LinkedHashSet<>();
            for (int i = first; i <= last; i++) wanted.add(i);
            // Prefetch neighbours so the next page is usually ready when you get there.
            if (last + 1 < geo.count()) wanted.add(last + 1);
            if (last + 2 < geo.count()) wanted.add(last + 2);
            if (first - 1 >= 0) wanted.add(first - 1);
            render.setWantedPages(wanted);

            float scale = (float) (zoom * PageGeometry.PX_PER_PT) * deviceScale();
            Rectangle clip = g.getClipBounds() != null ? g.getClipBounds() : vis;
            for (int i : wanted) {
                Rectangle b = pageBounds(i);
                boolean onScreen = i >= first && i <= last && b.intersects(clip.x - 6, clip.y - 6, clip.width + 12, clip.height + 12);
                BufferedImage img = render.page(i, scale, i >= first && i <= last);   // prefetch pages queue at lower priority
                if (onScreen) paintPage(g, i, b, img, scale);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintEmptyState(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Ui.muted());
            g.setFont(AppFonts.body(Font.PLAIN, 16));
            String msg = "Open a PDF to start reading, or drop a file here";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
        } finally {
            g.dispose();
        }
    }

    private void paintPage(Graphics2D g, int i, Rectangle b, BufferedImage img, float scale) {
        // soft shadow + white sheet
        g.setColor(new Color(0, 0, 0, 55));
        g.fillRoundRect(b.x + 3, b.y + 4, b.width, b.height, 4, 4);
        g.setColor(Color.WHITE);
        g.fillRect(b.x, b.y, b.width, b.height);

        BufferedImage shown = img != null ? img : render.stale(i, false);
        if (shown != null) {
            g.drawImage(shown, b.x, b.y, b.width, b.height, null);
        } else if (render.failed(i, scale, false)) {
            paintCentered(g, b, "Page " + (i + 1) + " could not be displayed");
        } else {
            paintCentered(g, b, "Rendering page " + (i + 1) + "…");
        }
        g.setColor(new Color(0, 0, 0, 40));
        g.drawRect(b.x, b.y, b.width - 1, b.height - 1);

        if (overlay != null) overlay.paint(g, i, b, zoom * PageGeometry.PX_PER_PT);

        List<HighlightArea> hs = highlights.get(i);
        if (hs != null) {
            double s = zoom * PageGeometry.PX_PER_PT;
            for (HighlightArea h : hs) {
                int hx = b.x + (int) Math.round(h.getX() * s);
                int hh = (int) Math.max(4, Math.round(h.getHeight() * s));
                int hy = b.y + (int) Math.round(h.getY() * s) - hh;
                int hw = (int) Math.max(4, Math.round(h.getWidth() * s));
                boolean cur = h == current;
                g.setColor(cur ? new Color(255, 140, 0, 120) : new Color(255, 230, 0, 100));
                g.fillRoundRect(hx - 1, hy - 1, hw + 2, hh + 2, 4, 4);
                if (cur) {
                    g.setColor(new Color(255, 120, 0, 230));
                    g.drawRoundRect(hx - 1, hy - 1, hw + 2, hh + 2, 4, 4);
                }
            }
        }
    }

    private static void paintCentered(Graphics2D g, Rectangle b, String text) {
        g.setColor(new Color(120, 120, 120));
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 14f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, b.x + (b.width - fm.stringWidth(text)) / 2, b.y + b.height / 2);
    }

    /** Where in this component a point on a page is (used to scroll a search hit into view). */
    Rectangle highlightBounds(HighlightArea h) {
        int p = h.getPage() - 1;
        if (p < 0 || p >= geo.count()) return new Rectangle();
        Rectangle b = pageBounds(p);
        double s = zoom * PageGeometry.PX_PER_PT;
        int hh = (int) Math.max(4, Math.round(h.getHeight() * s));
        return new Rectangle(b.x + (int) Math.round(h.getX() * s),
                b.y + (int) Math.round(h.getY() * s) - hh,
                (int) Math.max(4, Math.round(h.getWidth() * s)), hh);
    }

    // ------------------------------------------------------------------
    // Scrollable
    // ------------------------------------------------------------------

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(600, 800); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 48; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
        return o == SwingConstants.VERTICAL ? Math.max(48, (int) (r.height * 0.9)) : Math.max(48, (int) (r.width * 0.9));
    }
    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport v && contentW <= v.getWidth();
    }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport v && contentH <= v.getHeight();
    }
}
