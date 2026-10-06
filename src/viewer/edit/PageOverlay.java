package viewer.edit;

import java.awt.Graphics2D;
import java.awt.Rectangle;

/** Painted by the page view on top of each page. */
public interface PageOverlay {
    void paint(Graphics2D g, int page, Rectangle pageBounds, double pxPerPt);
}
