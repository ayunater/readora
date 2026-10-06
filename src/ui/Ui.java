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

    /** Brand gradient, sampled from the app icon: blue -> violet -> magenta. */
    public static final Color BLUE = new Color(0x4FA3FF);
    public static final Color VIOLET = new Color(0x6D5CFF);
    public static final Color MAGENTA = new Color(0xD060F6);

    private Ui() { }

    // ------------------------------------------------------------------
    // Colours
    // ------------------------------------------------------------------

    /** True when the active theme has a dark background. */
    public static boolean isDark() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) return true;
        return (base.getRed() * 299 + base.getGreen() * 587 + base.getBlue() * 114) / 1000 < 140;
    }

    /**
     * A card/sidebar surface sitting above the page background: a touch
     * lighter on dark themes, white on light ones. Always a valid colour
     * (the old unclamped "+10" threw on light themes).
     */
    public static Color raised() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) base = Color.DARK_GRAY;
        if (!isDark()) return Color.WHITE;
        return new Color(clamp(base.getRed() + 10), clamp(base.getGreen() + 10), clamp(base.getBlue() + 10));
    }

    /** A recessed surface for the area pages are drawn on, so white pages stand out in both themes. */
    public static Color sunken() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) base = Color.DARK_GRAY;
        int d = isDark() ? -4 : -12;
        return new Color(clamp(base.getRed() + d), clamp(base.getGreen() + d), clamp(base.getBlue() + d));
    }

    /** The theme accent (violet). */
    public static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        return c != null ? c : VIOLET;
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

    /** The main action on a screen or dialog: a button filled with the brand gradient. */
    public static JButton primaryButton(String text) {
        GradientButton b = new GradientButton(text);
        b.setPreferredSize(new Dimension(Math.max(110, b.getPreferredSize().width + 24), CONTROL_H));
        return b;
    }

    /** Rounded button painted with the icon's blue -> violet -> magenta gradient. */
    public static final class GradientButton extends JButton {
        GradientButton(String text) {
            super(text);
            setFont(AppFonts.body(Font.BOLD, 14));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                float[] stops = {0f, 0.5f, 1f};
                Color[] cols = {BLUE, VIOLET, MAGENTA};
                g.setPaint(new LinearGradientPaint(0, 0, w, 0, stops, cols));
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(0.35f));
                g.fillRoundRect(0, 0, w, h, CONTROL_ARC, CONTROL_ARC);
                g.setComposite(AlphaComposite.SrcOver);
                if (getModel().isPressed()) {
                    g.setColor(new Color(0, 0, 0, 55));
                    g.fillRoundRect(0, 0, w, h, CONTROL_ARC, CONTROL_ARC);
                } else if (getModel().isRollover()) {
                    g.setColor(new Color(255, 255, 255, 38));
                    g.fillRoundRect(0, 0, w, h, CONTROL_ARC, CONTROL_ARC);
                }
                if (hasFocus()) {
                    g.setColor(new Color(255, 255, 255, 170));
                    g.setStroke(new BasicStroke(2f));
                    g.drawRoundRect(2, 2, w - 5, h - 5, CONTROL_ARC - 2, CONTROL_ARC - 2);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(g0);
        }
    }

    // ------------------------------------------------------------------
    // Icons (SVG)
    // ------------------------------------------------------------------

    /** A themed icon from {@code resources/icons/<name>.svg}, drawn in the theme's text colour. */
    public static SvgIcon icon(String name, int size) {
        return icon(name, size, SvgIcon.Tint.TEXT);
    }

    public static SvgIcon icon(String name, int size, SvgIcon.Tint tint) {
        try {
            return new SvgIcon(Svg.load("/icons/" + name + ".svg"), size, size, tint);
        } catch (RuntimeException e) {
            // A missing icon must never take a window down with it: log it and show an empty slot.
            System.err.println("Readora: icon '" + name + "' could not be loaded (" + e.getMessage() + ")");
            return new SvgIcon(BLANK, size, size, tint);
        }
    }

    private static final Svg.Doc BLANK;
    static {
        try {
            BLANK = Svg.parse(new java.io.ByteArrayInputStream("<svg viewBox='0 0 24 24'/>".getBytes()));
        } catch (java.io.IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** The Readora logo ({@code resources/logo.svg}) at the given size. */
    public static SvgIcon logo(int size) {
        return new SvgIcon(Svg.load("/logo.svg"), size, size, SvgIcon.Tint.NONE);
    }

    private static java.util.List<Image> appIcons;

    /**
     * The logo rendered at every size a window system asks for (title bar, taskbar, alt-tab),
     * so each is rasterised from the vector rather than downscaled from one bitmap.
     */
    public static synchronized java.util.List<Image> appIcons() {
        if (appIcons == null) {
            Svg.Doc logo = Svg.load("/logo.svg");
            java.util.List<Image> list = new java.util.ArrayList<>();
            for (int px : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}) {
                list.add(logo.toImage(px, px, Color.BLACK));
            }
            appIcons = java.util.List.copyOf(list);
        }
        return appIcons;
    }

    /** A medium-size logo bitmap, for APIs that take a single image. */
    public static Image appIcon() {
        return appIcons().get(7);
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

    /** A square, icon-only button (page arrows, zoom +/-, tool palettes). */
    public static JButton iconButton(String iconName, String tooltip) {
        JButton b = new JButton(icon(iconName, 18));
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

    /** Gives a scrolled view the inner padding a bordered, rounded scroll card needs. */
    public static void padView(JComponent view) {
        view.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
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
