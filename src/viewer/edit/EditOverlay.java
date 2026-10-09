package viewer.edit;

import ui.Ui;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Draws pending edits over the pages (a live preview of what will be written) plus the selection frame,
 * and answers "what is under this point?". All item geometry is in points; screen sizes (handles, grab
 * tolerance) are converted using the current pixels-per-point.
 */
public final class EditOverlay implements PageOverlay {

    /** Handle order: 0 N, 1 NE, 2 E, 3 SE, 4 S, 5 SW, 6 W, 7 NW. */
    public static final int HANDLES = 8;
    private static final int HANDLE_PX = 9;
    private static final int GRAB_PX = 6;

    private final EditModel model;
    /** An item being created (not in the model yet). */
    private EditItem live;
    /** A dashed rubber-band box shown while dragging out a text box. */
    private Rectangle2D.Double band;
    private int bandPage = -1;

    public EditOverlay(EditModel model) { this.model = model; }

    public void setLive(EditItem it) { live = it; }

    public void setBand(int page, Rectangle2D.Double r) { bandPage = page; band = r; }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    @Override
    public void paint(Graphics2D g0, int page, Rectangle b, double k) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setClip(b);
            Graphics2D pt = (Graphics2D) g.create();
            pt.translate(b.x, b.y);
            pt.scale(k, k);
            for (EditItem it : model.items()) if (it.page == page) paintItem(pt, it);
            if (live != null && live.page == page) paintItem(pt, live);
            pt.dispose();

            if (band != null && bandPage == page) {
                g.setColor(Ui.accent());
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
                g.draw(toScreen(band, b, k));
            }
            EditItem sel = model.selected();
            if (sel != null && sel.page == page) paintSelection(g, sel, b, k);
        } finally {
            g.dispose();
        }
    }

    private static Rectangle2D toScreen(Rectangle2D r, Rectangle b, double k) {
        return new Rectangle2D.Double(b.x + r.getX() * k, b.y + r.getY() * k, r.getWidth() * k, r.getHeight() * k);
    }

    private void paintSelection(Graphics2D g, EditItem sel, Rectangle b, double k) {
        Rectangle2D r = toScreen(sel.bounds, b, k);
        g.setColor(Ui.accent());
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 3f}, 0f));
        g.draw(r);
        g.setStroke(new BasicStroke(1.5f));
        for (int h = 0; h < HANDLES; h++) {
            Point2D p = handlePoint(r, h);
            Rectangle2D sq = new Rectangle2D.Double(p.getX() - HANDLE_PX / 2.0, p.getY() - HANDLE_PX / 2.0, HANDLE_PX, HANDLE_PX);
            g.setColor(Color.WHITE);
            g.fill(sq);
            g.setColor(Ui.accent());
            g.draw(sq);
        }
    }

    private static Point2D handlePoint(Rectangle2D r, int h) {
        double x0 = r.getMinX(), x1 = r.getCenterX(), x2 = r.getMaxX(), y0 = r.getMinY(), y1 = r.getCenterY(), y2 = r.getMaxY();
        switch (h) {
            case 0: return new Point2D.Double(x1, y0);
            case 1: return new Point2D.Double(x2, y0);
            case 2: return new Point2D.Double(x2, y1);
            case 3: return new Point2D.Double(x2, y2);
            case 4: return new Point2D.Double(x1, y2);
            case 5: return new Point2D.Double(x0, y2);
            case 6: return new Point2D.Double(x0, y1);
            default: return new Point2D.Double(x0, y0);
        }
    }

    /** Draws one item in point space (the graphics is already translated and scaled to the page). */
    static void paintItem(Graphics2D g, EditItem it) {
        Rectangle2D.Double b = it.bounds;
        if (it instanceof EditItem.Shape s) {
            Composite old = g.getComposite();
            if (s.kind == EditItem.Shape.Kind.REDACT) {
                g.setColor(new Color(0, 0, 0, 215));
                g.fill(new Rectangle2D.Double(b.x, b.y, b.width, b.height));
                g.setColor(new Color(0xE0334F));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 3f}, 0f));
                g.draw(new Rectangle2D.Double(b.x, b.y, b.width, b.height));
                return;
            }
            boolean hl = s.kind == EditItem.Shape.Kind.HIGHLIGHT;
            float alpha = hl ? 0.45f : s.opacity;
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            java.awt.Shape shape = s.kind == EditItem.Shape.Kind.ELLIPSE
                    ? new Ellipse2D.Double(b.x, b.y, b.width, b.height)
                    : s.kind == EditItem.Shape.Kind.LINE
                    ? (s.rising ? new Line2D.Double(b.x, b.y + b.height, b.x + b.width, b.y)
                                : new Line2D.Double(b.x, b.y, b.x + b.width, b.y + b.height))
                    : new Rectangle2D.Double(b.x, b.y, b.width, b.height);
            if (s.fill != null && s.kind != EditItem.Shape.Kind.LINE) { g.setColor(s.fill); g.fill(shape); }
            if (s.stroke != null && s.width > 0 && !hl) {
                g.setColor(s.stroke);
                g.setStroke(new BasicStroke(s.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(shape);
            }
            g.setComposite(old);
        } else if (it instanceof EditItem.Ink ink) {
            if (ink.points.isEmpty()) return;
            g.setColor(ink.color);
            g.setStroke(new BasicStroke(ink.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double path = new Path2D.Double();
            Point2D.Double p0 = ink.points.get(0);
            path.moveTo(p0.x, p0.y);
            if (ink.points.size() == 1) path.lineTo(p0.x, p0.y);
            for (int i = 1; i < ink.points.size(); i++) path.lineTo(ink.points.get(i).x, ink.points.get(i).y);
            g.draw(path);
        } else if (it instanceof EditItem.Pic p) {
            if (p.preview != null) {
                AffineTransform t = new AffineTransform();
                t.translate(b.x, b.y);
                t.scale(b.width / p.preview.getWidth(), b.height / p.preview.getHeight());
                g.drawImage(p.preview, t, null);
            }
        } else if (it instanceof EditItem.Text t) {
            EditFonts.Layout layout = EditFonts.layout(t);
            String logical = t.family.equals("Times") ? Font.SERIF : t.family.equals("Courier") ? Font.MONOSPACED : Font.SANS_SERIF;
            Font f = new Font(logical, (t.bold ? Font.BOLD : 0) | (t.italic ? Font.ITALIC : 0), 1).deriveFont(t.size);
            g.setFont(f);
            g.setColor(t.color);
            for (int i = 0; i < layout.lines.size(); i++) {
                String line = layout.lines.get(i);
                if (line.isEmpty()) continue;
                double x = b.x;
                float w = layout.width(line);
                if (t.align == 1) x += (b.width - w) / 2;
                else if (t.align == 2) x += b.width - w;
                g.drawString(line, (float) x, (float) (b.y + i * t.size * EditFonts.LEADING + t.size * EditFonts.BASELINE));
            }
        }
    }

    // ------------------------------------------------------------------
    // Hit testing
    // ------------------------------------------------------------------

    /**
     * Topmost item at {@code p} (points) on {@code page}, or null. Highlighter bands act as a background
     * layer: they are only picked when nothing else is there, so text under a highlight stays selectable.
     */
    public EditItem hit(List<EditItem> items, int page, Point2D p, double k) {
        double tol = GRAB_PX / k;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = items.size() - 1; i >= 0; i--) {
                EditItem it = items.get(i);
                boolean highlight = it instanceof EditItem.Shape s && s.kind == EditItem.Shape.Kind.HIGHLIGHT;
                if (it.page == page && highlight == (pass == 1) && contains(it, p, tol)) return it;
            }
        }
        return null;
    }

    private static boolean contains(EditItem it, Point2D p, double tol) {
        Rectangle2D.Double b = it.bounds;
        Rectangle2D grown = new Rectangle2D.Double(b.x - tol, b.y - tol, b.width + 2 * tol, b.height + 2 * tol);
        if (!grown.contains(p)) return false;
        if (it instanceof EditItem.Shape s) {
            switch (s.kind) {
                case LINE: {
                    Line2D l = s.rising ? new Line2D.Double(b.x, b.y + b.height, b.x + b.width, b.y)
                                        : new Line2D.Double(b.x, b.y, b.x + b.width, b.y + b.height);
                    return l.ptSegDist(p) <= Math.max(tol, s.width / 2);
                }
                case ELLIPSE: {
                    Ellipse2D e = new Ellipse2D.Double(b.x - tol, b.y - tol, b.width + 2 * tol, b.height + 2 * tol);
                    if (s.fill != null) return e.contains(p);
                    Ellipse2D inner = new Ellipse2D.Double(b.x + tol, b.y + tol, Math.max(0, b.width - 2 * tol), Math.max(0, b.height - 2 * tol));
                    return e.contains(p) && !inner.contains(p);
                }
                case HIGHLIGHT:
                    return true;
                default: {
                    if (s.fill != null) return true;
                    Rectangle2D inner = new Rectangle2D.Double(b.x + tol, b.y + tol, Math.max(0, b.width - 2 * tol), Math.max(0, b.height - 2 * tol));
                    return !inner.contains(p);                    // an unfilled box is grabbed by its edge
                }
            }
        }
        if (it instanceof EditItem.Ink ink) {
            double reach = Math.max(tol, ink.width / 2 + 2);
            if (ink.points.size() == 1) return ink.points.get(0).distance(p) <= reach;
            for (int i = 1; i < ink.points.size(); i++) {
                if (new Line2D.Double(ink.points.get(i - 1), ink.points.get(i)).ptSegDist(p) <= reach) return true;
            }
            return false;
        }
        return true;                                              // text boxes and pictures: the whole box
    }

    /** Which resize handle of {@code it} is at {@code p}, or -1. */
    public int handleAt(EditItem it, Point2D p, double k) {
        Rectangle2D r = it.bounds;
        double reach = (HANDLE_PX / 2.0 + 2) / k;
        for (int h = 0; h < HANDLES; h++) {
            Point2D hp = handlePoint(r, h);
            if (Math.abs(hp.getX() - p.getX()) <= reach && Math.abs(hp.getY() - p.getY()) <= reach) return h;
        }
        return -1;
    }

    /** New bounds when handle {@code h} of {@code r0} is dragged to {@code p}. */
    public static Rectangle2D.Double resized(Rectangle2D r0, int h, Point2D p, double min) {
        double left = r0.getMinX(), right = r0.getMaxX(), top = r0.getMinY(), bottom = r0.getMaxY();
        if (h == 7 || h == 0 || h == 1) top = p.getY();
        if (h == 1 || h == 2 || h == 3) right = p.getX();
        if (h == 3 || h == 4 || h == 5) bottom = p.getY();
        if (h == 5 || h == 6 || h == 7) left = p.getX();
        double x = Math.min(left, right), y = Math.min(top, bottom);
        return new Rectangle2D.Double(x, y, Math.max(min, Math.abs(right - left)), Math.max(min, Math.abs(bottom - top)));
    }

    /** The mouse cursor to show over a handle. */
    public static int cursorFor(int h) {
        switch (h) {
            case 0: return Cursor.N_RESIZE_CURSOR;
            case 1: return Cursor.NE_RESIZE_CURSOR;
            case 2: return Cursor.E_RESIZE_CURSOR;
            case 3: return Cursor.SE_RESIZE_CURSOR;
            case 4: return Cursor.S_RESIZE_CURSOR;
            case 5: return Cursor.SW_RESIZE_CURSOR;
            case 6: return Cursor.W_RESIZE_CURSOR;
            default: return Cursor.NW_RESIZE_CURSOR;
        }
    }
}
