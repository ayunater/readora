package viewer.edit;

import javax.swing.JComponent;
import java.awt.Point;
import java.awt.Rectangle;

/** What the editor needs from the page view: where pages are on screen and how big they are. */
public interface PageHost {
    JComponent component();
    int pageCount();
    /** Page index under a point of the component (clamped to the nearest page). */
    int pageAt(Point p);
    /** Screen rectangle of a page. */
    Rectangle pageBounds(int page);
    /** Pixels per point at the current zoom. */
    double pxPerPt();
    /** Displayed page size in points (after rotation). */
    double pageWidthPt(int page);
    double pageHeightPt(int page);
}
