package viewer.edit;

import java.awt.Color;

/** The editor's current "pen": what new items look like. Shared by the toolbar and the controller. */
public final class EditProps {
    public static final Color HIGHLIGHT = new Color(0xFFEB3B);

    public Color color = new Color(0xD32F2F);        // strokes, lines, freehand
    public Color textColor = Color.BLACK;
    public Color fill;                               // null = no fill
    public float width = 2;
    public String family = "Helvetica";
    public boolean bold, italic;
    public float size = 14;
}
