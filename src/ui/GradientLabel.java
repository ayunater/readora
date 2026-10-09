package ui;

import javax.swing.*;
import java.awt.*;

/** A label whose text is painted with the brand gradient (blue to violet to magenta). */
public final class GradientLabel extends JComponent {

    private String text;

    public GradientLabel(String text, Font font) {
        this.text = text;
        setFont(font);
        setOpaque(false);
    }

    public void setText(String t) { text = t; revalidate(); repaint(); }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        return new Dimension(fm.stringWidth(text) + in.left + in.right, fm.getHeight() + in.top + in.bottom);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            Insets in = getInsets();
            int w = Math.max(1, fm.stringWidth(text));
            g.setPaint(new LinearGradientPaint(in.left, 0, in.left + w, 0,
                    new float[]{0f, 0.5f, 1f}, new Color[]{Ui.BLUE, Ui.VIOLET, Ui.MAGENTA}));
            g.drawString(text, in.left, in.top + fm.getAscent());
        } finally {
            g.dispose();
        }
    }
}
