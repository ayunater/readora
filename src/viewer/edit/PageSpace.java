package viewer.edit;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.geom.AffineTransform;

/**
 * Converts between what you see and how a PDF page stores it.
 *
 * <p>Edits are positioned in <b>visual space</b>: points, origin at the top-left of the page as displayed
 * (after the page's crop box and rotation), y pointing down. PDF content streams use the unrotated page's
 * bottom-left origin with y pointing up, so a page rotated 90 degrees needs its coordinates turned.
 * {@link #visualToPdf()} is that conversion; with it applied as the content stream's base transform,
 * everything can be drawn in visual coordinates and lands in the right place on pages rotated 0/90/180/270.</p>
 */
public final class PageSpace {

    private final AffineTransform visualToPdf;
    private final double width, height;

    private PageSpace(AffineTransform t, double w, double h) {
        this.visualToPdf = t;
        this.width = w;
        this.height = h;
    }

    public static PageSpace of(PDPage page) {
        PDRectangle box = page.getCropBox();
        double llx = box.getLowerLeftX(), lly = box.getLowerLeftY(), w = box.getWidth(), h = box.getHeight();
        int rot = ((page.getRotation() % 360) + 360) % 360;
        // AffineTransform(m00, m10, m01, m11, m02, m12):  x' = m00*x + m01*y + m02,  y' = m10*x + m11*y + m12
        switch (rot) {
            case 90:  return new PageSpace(new AffineTransform(0, 1, 1, 0, llx, lly), h, w);
            case 180: return new PageSpace(new AffineTransform(-1, 0, 0, 1, llx + w, lly), w, h);
            case 270: return new PageSpace(new AffineTransform(0, -1, -1, 0, llx + w, lly + h), h, w);
            default:  return new PageSpace(new AffineTransform(1, 0, 0, -1, llx, lly + h), w, h);
        }
    }

    /** Visual (top-left origin, y down) to PDF user space. */
    public AffineTransform visualToPdf() { return new AffineTransform(visualToPdf); }

    /** Page width as displayed, in points. */
    public double width()  { return width; }

    /** Page height as displayed, in points. */
    public double height() { return height; }
}
