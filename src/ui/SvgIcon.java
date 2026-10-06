package ui;

import javax.swing.*;
import java.awt.*;

/**
 * An {@link Icon} drawn from an SVG. It is resolution independent (crisp at any HiDPI scale) and its colour is
 * resolved <i>at paint time</i> from the active theme, so icons follow dark/light switches with no reload.
 */
public final class SvgIcon implements Icon {

    /** How {@code currentColor} in the SVG is resolved. */
    public enum Tint {
        /** The theme's text colour. */ TEXT,
        /** Subdued text colour. */ MUTED,
        /** The theme accent (violet). */ ACCENT,
        /** The brand gradient, blue to magenta, across the icon. */ BRAND,
        /** White (for icons on coloured badges). */ WHITE,
        /** Do not touch colours (logos with their own fills). */ NONE
    }

    private final Svg.Doc doc;
    private final int width, height;
    private final Tint tint;

    public SvgIcon(Svg.Doc doc, int width, int height, Tint tint) {
        this.doc = doc;
        this.width = width;
        this.height = height;
        this.tint = tint;
    }

    public SvgIcon withSize(int w, int h) { return new SvgIcon(doc, w, h, tint); }
    public SvgIcon withTint(Tint t)       { return new SvgIcon(doc, width, height, t); }

    @Override public int getIconWidth()  { return width; }
    @Override public int getIconHeight() { return height; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            if (c != null && !c.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
            doc.paint(g, x, y, width, height, paintFor(c));
        } finally {
            g.dispose();
        }
    }

    private Paint paintFor(Component c) {
        switch (tint) {
            case MUTED:  return Ui.muted();
            case ACCENT: return Ui.accent();
            case WHITE:  return Color.WHITE;
            case BRAND:
                return new LinearGradientPaint(
                        (float) doc.viewBoxX(), (float) doc.viewBoxY(),
                        (float) (doc.viewBoxX() + doc.viewBoxWidth()), (float) (doc.viewBoxY() + doc.viewBoxHeight()),
                        new float[]{0f, 1f}, new Color[]{Ui.BLUE, Ui.MAGENTA});
            case NONE:   return Color.BLACK;
            default: {
                Color fg = c != null ? c.getForeground() : null;
                Color lf = UIManager.getColor("Label.foreground");
                return lf != null ? lf : fg != null ? fg : Color.GRAY;
            }
        }
    }
}
