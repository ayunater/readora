package viewer.edit;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * One thing added to a page: text, shape, drawing or picture. Positions are in visual page space
 * (see {@link PageSpace}). Items are plain mutable data; {@link EditModel} records changes for undo.
 */
public abstract class EditItem {

    /** Zero-based page index. */
    public int page;
    /** Bounding box in points, visual space. */
    public final Rectangle2D.Double bounds = new Rectangle2D.Double();

    public abstract EditItem copy();

    /** Overwrites this item's state with {@code o}'s (same class). Used by undo/redo. */
    public abstract void copyFrom(EditItem o);

    /** Sets the bounding box; items made of points rescale them to fit. */
    public void setBounds(Rectangle2D nb) { bounds.setRect(nb); }

    public void translate(double dx, double dy) {
        bounds.x += dx;
        bounds.y += dy;
    }

    protected void copyBase(EditItem o) {
        page = o.page;
        bounds.setRect(o.bounds);
    }

    // ------------------------------------------------------------------

    /** A text box. The height follows the wrapped text (see {@link EditFonts}). */
    public static final class Text extends EditItem {
        public String text = "Text";
        /** Helvetica, Times or Courier. */
        public String family = "Helvetica";
        public boolean bold, italic;
        public float size = 14;
        public Color color = Color.BLACK;
        /** 0 left, 1 centre, 2 right. */
        public int align;

        @Override public EditItem copy() { Text t = new Text(); t.copyFrom(this); return t; }

        @Override public void copyFrom(EditItem o) {
            Text t = (Text) o;
            copyBase(t);
            text = t.text; family = t.family; bold = t.bold; italic = t.italic;
            size = t.size; color = t.color; align = t.align;
        }
    }

    /** Rectangle, ellipse, straight line or highlighter band. */
    public static final class Shape extends EditItem {
        public enum Kind { RECT, ELLIPSE, LINE, HIGHLIGHT }

        public Kind kind = Kind.RECT;
        public Color stroke = new Color(0xD32F2F);
        /** Null for no fill. */
        public Color fill;
        public float width = 2;
        public float opacity = 1;
        /** For lines: true runs from bottom-left to top-right of the box, false from top-left to bottom-right. */
        public boolean rising;

        @Override public EditItem copy() { Shape s = new Shape(); s.copyFrom(this); return s; }

        @Override public void copyFrom(EditItem o) {
            Shape s = (Shape) o;
            copyBase(s);
            kind = s.kind; stroke = s.stroke; fill = s.fill; width = s.width; opacity = s.opacity; rising = s.rising;
        }
    }

    /** A freehand stroke. */
    public static final class Ink extends EditItem {
        public final List<Point2D.Double> points = new ArrayList<>();
        public Color color = new Color(0x1565C0);
        public float width = 3;

        public void recomputeBounds() {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Point2D.Double p : points) {
                minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
                maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
            }
            if (points.isEmpty()) bounds.setRect(0, 0, 0, 0);
            else bounds.setRect(minX, minY, maxX - minX, maxY - minY);
        }

        @Override public void setBounds(Rectangle2D nb) {
            double ow = Math.max(bounds.width, 1e-6), oh = Math.max(bounds.height, 1e-6);
            for (Point2D.Double p : points) {
                p.x = nb.getX() + (p.x - bounds.x) / ow * nb.getWidth();
                p.y = nb.getY() + (p.y - bounds.y) / oh * nb.getHeight();
            }
            bounds.setRect(nb);
        }

        @Override public void translate(double dx, double dy) {
            for (Point2D.Double p : points) { p.x += dx; p.y += dy; }
            super.translate(dx, dy);
        }

        @Override public EditItem copy() { Ink i = new Ink(); i.copyFrom(this); return i; }

        @Override public void copyFrom(EditItem o) {
            Ink i = (Ink) o;
            copyBase(i);
            points.clear();
            for (Point2D.Double p : i.points) points.add(new Point2D.Double(p.x, p.y));
            color = i.color; width = i.width;
        }
    }

    /** A picture placed on the page. */
    public static final class Pic extends EditItem {
        public File file;
        /** For on-screen drawing only; the file is what gets embedded. */
        public BufferedImage preview;

        @Override public EditItem copy() { Pic p = new Pic(); p.copyFrom(this); return p; }

        @Override public void copyFrom(EditItem o) {
            Pic p = (Pic) o;
            copyBase(p);
            file = p.file; preview = p.preview;
        }
    }
}
