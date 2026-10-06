package viewer.ops;

/**
 * A single rectangular region to highlight on a PDF page, expressed in
 * PDF coordinate space (points, origin at the bottom-left of the page)
 * rather than screen pixels.
 *
 * Used to mark the location of a search match so it can be painted on
 * top of the rendered page image. The viewer scales these values by the
 * current zoom level at paint time, so the same HighlightArea stays
 * correctly positioned as the user zooms in or out.
 *
 * <p>Note: {@link #getPage()} is <b>1-indexed</b> to match the page
 * numbers the viewer shows on screen, not the 0-based page indices
 * PDFBox itself uses internally. The viewer subtracts one before use.
 */
public class HighlightArea {

    /** 1-based number of the page this highlight belongs to (matches the on-screen page number). */
    private int page;

    /** X position of the highlight's left edge, in PDF points. */
    private float x;

    /** Y position of the highlight's top edge, in PDF points (bottom-left origin). */
    private float y;

    /** Width of the highlighted region, in PDF points. */
    private float width;

    /** Height of the highlighted region, in PDF points. */
    private float height;

    /**
     * Creates a highlight for a specific page and rectangular region.
     *
     * @param page   1-based page number the highlight appears on
     * @param x      left edge, in PDF points
     * @param y      top edge, in PDF points
     * @param width  width of the region, in PDF points
     * @param height height of the region, in PDF points
     */
    public HighlightArea(
            int page,
            float x,
            float y,
            float width,
            float height
    ) {
        this.page = page;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return the 1-based page number this highlight belongs to */
    public int getPage() { return page; }

    /** @return the left edge of the highlight, in PDF points */
    public float getX() { return x; }

    /** @return the top edge of the highlight, in PDF points */
    public float getY() { return y; }

    /** @return the width of the highlighted region, in PDF points */
    public float getWidth() { return width; }

    /** @return the height of the highlighted region, in PDF points */
    public float getHeight() { return height; }
}
