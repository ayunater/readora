package viewer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Size of every page in PDF points (1/72 inch), after applying each page's
 * crop box and rotation, so mixed-size and rotated documents lay out
 * correctly. Reading this is cheap; nothing is rendered.
 */
public final class PageGeometry {

    /** Points per CSS pixel: 100% zoom means 96 DPI, like a browser. */
    public static final float PX_PER_PT = 96f / 72f;

    private final float[] widths;
    private final float[] heights;

    private PageGeometry(float[] w, float[] h) {
        this.widths = w;
        this.heights = h;
    }

    public static PageGeometry empty() {
        return new PageGeometry(new float[0], new float[0]);
    }

    public static PageGeometry of(PDDocument doc) {
        int n = doc.getNumberOfPages();
        float[] w = new float[n];
        float[] h = new float[n];
        for (int i = 0; i < n; i++) {
            float[] wh = sizeOf(doc.getPage(i));
            w[i] = wh[0];
            h[i] = wh[1];
        }
        return new PageGeometry(w, h);
    }

    /** {width, height} of one page in points, after crop box and rotation. */
    public static float[] sizeOf(PDPage page) {
        PDRectangle box = page.getCropBox();
        float w = box.getWidth(), h = box.getHeight();
        if (w <= 0 || h <= 0) { w = 612; h = 792; }          // malformed box: assume Letter
        int rot = ((page.getRotation() % 360) + 360) % 360;
        if (rot == 90 || rot == 270) { float t = w; w = h; h = t; }
        return new float[]{w, h};
    }

    public int count()             { return widths.length; }
    public float width(int page)   { return widths[page]; }
    public float height(int page)  { return heights[page]; }

    public float maxWidth() {
        float m = 0;
        for (float v : widths) m = Math.max(m, v);
        return m;
    }
}
