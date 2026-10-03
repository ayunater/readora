package ui;

import rightpanel.AppFonts;

import javax.swing.*;
import java.awt.*;

/**
 * Single source of truth for Readora's visual language.
 *
 * <p>Every screen (Home, Recent, Starred, Tools, Settings and the PDF
 * viewer) builds its chrome from these helpers so that corner radii,
 * control heights, fonts and spacing are identical everywhere, instead
 * of each screen carrying its own numbers.</p>
 */
public final class Ui {

    /** Corner radius for cards, tables and panels. */
    public static final int ARC = 20;
    /** Corner radius for buttons, text fields and combo boxes. */
    public static final int CONTROL_ARC = 16;
    /** Height shared by every button, field and combo box. */
    public static final int CONTROL_H = 40;
    /** Standard gap between sibling components. */
    public static final int GAP = 15;
    /** Standard padding inside a screen. */
    public static final int PAD = 20;

    private Ui() { }

    // ------------------------------------------------------------------
    // Colours
    // ------------------------------------------------------------------

    /**
     * A surface colour slightly lifted from the current panel background.
     * Brightens dark themes and darkens light ones, always clamped to the
     * valid 0-255 range (the old unclamped "+10" crashed on light themes).
     */
    public static Color raised() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) base = Color.DARK_GRAY;
        int luma = (base.getRed() * 299 + base.getGreen() * 587 + base.getBlue() * 114) / 1000;
        int d = luma < 140 ? 10 : -10;
        return new Color(clamp(base.getRed() + d), clamp(base.getGreen() + d), clamp(base.getBlue() + d));
    }

    /** Muted text colour for subtitles and hints. */
    public static Color muted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : Color.GRAY;
    }

    /** Hairline colour used for dividers. */
    public static Color line() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : Color.GRAY;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    /** Screen title (32 pt title font). */
    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.title(Font.PLAIN, 32));
        return l;
    }

    /** Screen subtitle (14 pt body font). */
    public static JLabel subtitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.body(Font.PLAIN, 14));
        return l;
    }

    /** Section heading inside a screen. */
    public static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.body(Font.BOLD, 18));
        return l;
    }

    /**
     * Standard screen header: title over subtitle. Returned panel exposes
     * the two labels through {@link Header#title} and {@link Header#subtitle}
     * so a screen can update them (the viewer shows the open file here).
     */
    public static Header header(String title, String subtitle) {
        return new Header(title, subtitle);
    }

    public static final class Header extends JPanel {
        public final JLabel title;
        public final JLabel subtitle;

        Header(String t, String s) {
            super(new GridLayout(2, 1, 0, 2));
            setOpaque(false);
            title = title(t);
            subtitle = subtitle(s);
            add(title);
            add(subtitle);
        }
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    /** A standard rounded button. */
    public static JButton button(String text) {
        JButton b = new JButton(text);
        styleButton(b);
        return b;
    }

    /** Applies the standard button look to an existing button. */
    public static void styleButton(AbstractButton b) {
        b.setFont(AppFonts.body(Font.PLAIN, 14));
        b.setFocusPainted(false);
        b.putClientProperty("FlatLaf.style", "arc:" + CONTROL_ARC);
        b.setPreferredSize(new Dimension(Math.max(110, b.getPreferredSize().width + 24), CONTROL_H));
    }

    /** A rounded button with the accent colour, used for the main action on a screen. */
    public static JButton primaryButton(String text) {
        JButton b = button(text);
        b.putClientProperty("FlatLaf.style",
                "arc:" + CONTROL_ARC + "; background:$Component.accentColor; foreground:#ffffff; borderWidth:0");
        return b;
    }

    /** A large rounded tile button (Home quick actions, Tools grid). Height comes from the layout. */
    public static JButton tile(String text) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.body(Font.PLAIN, 14));
        b.setFocusPainted(false);
        b.putClientProperty("FlatLaf.style", "arc:" + ARC);
        return b;
    }

    /** A small square icon-style button (e.g. page nav arrows, zoom +/-). */
    public static JButton squareButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.body(Font.BOLD, 16));
        b.setFocusPainted(false);
        b.setToolTipText(tooltip);
        b.putClientProperty("FlatLaf.style", "arc:" + CONTROL_ARC);
        b.setPreferredSize(new Dimension(CONTROL_H, CONTROL_H));
        return b;
    }

    /** Rounded text field of standard height. */
    public static void styleField(JTextField f) {
        f.setFont(AppFonts.body(Font.PLAIN, 14));
        f.putClientProperty("FlatLaf.style", "arc:" + CONTROL_ARC + "; margin:2,10,2,10");
        f.setPreferredSize(new Dimension(f.getPreferredSize().width, CONTROL_H));
    }

    /** Rounded combo box of standard height. */
    public static void styleCombo(JComboBox<?> c) {
        c.setFont(AppFonts.body(Font.PLAIN, 14));
        c.putClientProperty("FlatLaf.style", "arc:" + CONTROL_ARC);
        c.setPreferredSize(new Dimension(Math.max(220, c.getPreferredSize().width), CONTROL_H));
    }

    // ------------------------------------------------------------------
    // Containers
    // ------------------------------------------------------------------

    /** A rounded, slightly raised surface. Re-tints itself when the theme changes. */
    public static class Card extends JPanel {
        public Card(LayoutManager lm) {
            super(lm);
            init();
        }

        public Card() {
            this(new BorderLayout());
        }

        private void init() {
            putClientProperty("FlatLaf.style", "arc:" + ARC);
            setBackground(raised());
        }

        @Override
        public void updateUI() {
            super.updateUI();
            // updateUI runs from the JPanel constructor too, before init().
            setBackground(raised());
        }
    }

    /** A card with inner padding. */
    public static Card card(LayoutManager lm, int pad) {
        Card c = new Card(lm);
        c.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
        return c;
    }

    /** Wraps a scroll pane in the standard rounded look (used by tables and lists). */
    public static void styleScroll(JScrollPane sp) {
        // Keep FlatLaf's own scroll-pane border: the "arc" style rounds *that* border.
        sp.putClientProperty("FlatLaf.style", "arc:" + ARC);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getHorizontalScrollBar().setUnitIncrement(18);
    }

    /** Centred row of buttons shown at the bottom of a screen. */
    public static JPanel buttonBar(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        p.setOpaque(false);
        for (JComponent c : items) p.add(c);
        return p;
    }

    /** Standard empty border for a whole screen. */
    public static void padScreen(JComponent screen) {
        screen.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
    }

    /** Styles a JTable consistently (row height, header, no grid). */
    public static void styleTable(JTable t) {
        t.setRowHeight(34);
        t.setFont(AppFonts.body(Font.PLAIN, 14));
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.getTableHeader().setFont(AppFonts.body(Font.BOLD, 14));
        t.getTableHeader().setReorderingAllowed(false);
    }
}
