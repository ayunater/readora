package ui;

import rightpanel.AppFonts;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;

/**
 * Readora's own dialogs, replacing {@link JOptionPane}.
 *
 * <p>The method names, parameters and return codes match {@code JOptionPane}
 * (use {@code JOptionPane.YES_OPTION}, {@code OK_CANCEL_OPTION}, ... as
 * before), so a call site only changes its class name. What changes is the
 * look: themed window and title bar, a gradient badge for the message type,
 * wrapped text, and the app's own rounded buttons with the gradient
 * primary action.</p>
 *
 * <p>Like {@code JOptionPane}, call these on the UI thread.</p>
 */
public final class Dialogs {

    private Dialogs() { }

    // ------------------------------------------------------------------
    // JOptionPane-compatible API
    // ------------------------------------------------------------------

    public static void showMessageDialog(Component parent, Object message) {
        showMessageDialog(parent, message, "Message", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showMessageDialog(Component parent, Object message, String title, int messageType) {
        show(parent, message, title, messageType, new String[]{"OK"}, new int[]{JOptionPane.OK_OPTION}, 0);
    }

    public static int showConfirmDialog(Component parent, Object message) {
        return showConfirmDialog(parent, message, "Select an Option", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int optionType) {
        return showConfirmDialog(parent, message, title, optionType, JOptionPane.QUESTION_MESSAGE);
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int optionType,
                                        int messageType) {
        return switch (optionType) {
            case JOptionPane.YES_NO_OPTION -> show(parent, message, title, messageType,
                    new String[]{"No", "Yes"}, new int[]{JOptionPane.NO_OPTION, JOptionPane.YES_OPTION}, 1);
            case JOptionPane.OK_CANCEL_OPTION -> show(parent, message, title, messageType,
                    new String[]{"Cancel", "OK"}, new int[]{JOptionPane.CANCEL_OPTION, JOptionPane.OK_OPTION}, 1);
            case JOptionPane.YES_NO_CANCEL_OPTION -> show(parent, message, title, messageType,
                    new String[]{"Cancel", "No", "Yes"},
                    new int[]{JOptionPane.CANCEL_OPTION, JOptionPane.NO_OPTION, JOptionPane.YES_OPTION}, 2);
            default -> show(parent, message, title, messageType,
                    new String[]{"OK"}, new int[]{JOptionPane.OK_OPTION}, 0);
        };
    }

    /** Asks for one line of text. Returns {@code null} if cancelled. */
    public static String showInputDialog(Component parent, Object message, String title, int messageType) {
        JTextField field = new JTextField(28);
        Ui.styleField(field);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(textOf(message), BorderLayout.NORTH);
        body.add(field, BorderLayout.CENTER);
        int r = show(parent, body, title, messageType, new String[]{"Cancel", "OK"},
                new int[]{JOptionPane.CANCEL_OPTION, JOptionPane.OK_OPTION}, 1, field);
        return r == JOptionPane.OK_OPTION ? field.getText() : null;
    }

    public static String showInputDialog(Component parent, Object message) {
        return showInputDialog(parent, message, "Input", JOptionPane.QUESTION_MESSAGE);
    }

    // ------------------------------------------------------------------
    // Window shell (shared with AppFileChooser)
    // ------------------------------------------------------------------

    /** A modal, theme-decorated dialog window owned by {@code parent}'s window. */
    static JDialog shell(Component parent, String title) {
        Window owner = parent instanceof Window w ? w
                : parent != null ? SwingUtilities.getWindowAncestor(parent)
                : KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Image icon = Ui.appIcon();
        if (icon != null) d.setIconImage(icon);
        return d;
    }

    /** Escape runs {@code action}. */
    static void onEscape(JDialog d, Runnable action) {
        d.getRootPane().registerKeyboardAction(e -> action.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // ------------------------------------------------------------------
    // Implementation
    // ------------------------------------------------------------------

    private static int show(Component parent, Object message, String title, int messageType,
                            String[] labels, int[] codes, int primaryIndex) {
        return show(parent, message, title, messageType, labels, codes, primaryIndex, null);
    }

    /**
     * @param labels       button captions, left to right
     * @param codes        value returned for each button
     * @param primaryIndex the gradient, default button (Enter)
     * @param initialFocus component to focus on open (e.g. an input field), or null
     */
    private static int show(Component parent, Object message, String title, int messageType,
                            String[] labels, int[] codes, int primaryIndex,
                            JComponent initialFocus) {
        JDialog d = shell(parent, title);
        int[] result = {JOptionPane.CLOSED_OPTION};

        JPanel root = new JPanel(new BorderLayout(0, 22));
        root.setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));

        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        if (messageType != JOptionPane.PLAIN_MESSAGE) {
            JPanel badgeHolder = new JPanel(new BorderLayout());
            badgeHolder.setOpaque(false);
            badgeHolder.add(new Badge(messageType), BorderLayout.NORTH);
            body.add(badgeHolder, BorderLayout.WEST);
        }
        body.add(message instanceof JComponent c ? c : textOf(message), BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bar.setOpaque(false);
        JButton primary = null;
        for (int i = 0; i < labels.length; i++) {
            JButton b = i == primaryIndex ? Ui.primaryButton(labels[i]) : Ui.button(labels[i]);
            final int code = codes[i];
            b.addActionListener(e -> { result[0] = code; d.dispose(); });
            if (i == primaryIndex) primary = b;
            bar.add(b);
        }
        root.add(bar, BorderLayout.SOUTH);

        d.setContentPane(root);
        if (primary != null) d.getRootPane().setDefaultButton(primary);
        // Escape / the title-bar X count as "cancel" (CLOSED_OPTION), exactly like JOptionPane.
        onEscape(d, d::dispose);

        d.pack();
        Dimension size = d.getPreferredSize();
        d.setMinimumSize(new Dimension(Math.max(400, size.width), size.height));
        d.setResizable(message instanceof JComponent);
        d.setLocationRelativeTo(d.getOwner());
        final JButton focusTarget = primary;
        d.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                if (initialFocus != null) initialFocus.requestFocusInWindow();
                else if (focusTarget != null && !(message instanceof JComponent)) focusTarget.requestFocusInWindow();
            }
        });
        d.setVisible(true);
        return result[0];
    }

    /** Plain text or simple HTML, wrapped to a readable width. */
    private static JComponent textOf(Object message) {
        String text = String.valueOf(message);
        if (text.regionMatches(true, 0, "<html", 0, 5)) {
            JLabel l = new JLabel(text);
            l.setFont(AppFonts.body(Font.PLAIN, 14));
            return l;
        }
        JTextArea area = new JTextArea(text.strip());
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        area.setFont(AppFonts.body(Font.PLAIN, 14));
        area.setColumns(Math.min(52, Math.max(26, longestLine(text))));
        return area;
    }

    private static int longestLine(String text) {
        int max = 0;
        for (String line : text.split("\n")) max = Math.max(max, line.length());
        return max;
    }

    /** Round gradient badge with a glyph for the message type. */
    private static final class Badge extends JComponent {
        private final int type;

        Badge(int type) {
            this.type = type;
            setPreferredSize(new Dimension(44, 44));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color from, to;
                String glyph;
                switch (type) {
                    case JOptionPane.ERROR_MESSAGE -> { from = new Color(0xFF6B8A); to = new Color(0xE0334F); glyph = "\u2715"; }
                    case JOptionPane.WARNING_MESSAGE -> { from = new Color(0xFFC247); to = new Color(0xFF7A18); glyph = "!"; }
                    case JOptionPane.QUESTION_MESSAGE -> { from = Ui.VIOLET; to = Ui.MAGENTA; glyph = "?"; }
                    default -> { from = Ui.BLUE; to = Ui.VIOLET; glyph = "i"; }
                }
                int s = Math.min(getWidth(), getHeight());
                g.setPaint(new GradientPaint(0, 0, from, s, s, to));
                g.fill(new Ellipse2D.Float(0, 0, s, s));
                g.setColor(Color.WHITE);
                g.setFont(AppFonts.body(Font.BOLD, 24));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(glyph, (s - fm.stringWidth(glyph)) / 2f, (s + fm.getAscent() - fm.getDescent()) / 2f);
            } finally {
                g.dispose();
            }
        }
    }
}
