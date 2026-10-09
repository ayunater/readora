package viewer.ops;

import rightpanel.AppFonts;
import ui.Dialogs;
import ui.SecretField;
import ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;

/**
 * The form for protecting a PDF: open password with confirmation, a strength meter, a generator,
 * optional restrictions with their own permissions password, and the encryption strength.
 */
public final class SecurityDialog {

    public enum Mode {
        ADD("Protect with a password",
                "Choose how the file is protected. A password to open it keeps it private; restrictions limit what people can do with it."),
        CHANGE("Change passwords",
                "Set new passwords for this file. The old ones stop working once you save."),
        PERMISSIONS("Change permissions",
                "Choose what people may do with this file. Enter the passwords to protect it with.");

        final String title, intro;
        Mode(String t, String i) { title = t; intro = i; }
    }

    private SecurityDialog() { }

    /** @return the chosen options, or null if cancelled. The caller should {@link ProtectionOptions#wipe wipe} them after use. */
    public static ProtectionOptions ask(Component parent, Mode mode, ProtectionOptions initial) {
        Form form = new Form(initial, mode);
        while (true) {
            int r = Dialogs.showConfirmDialog(parent, form.panel, mode.title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) { form.wipe(); return null; }
            ProtectionOptions o = form.read();
            String problem = o == null ? form.problem : o.validate();
            if (problem != null) {
                Dialogs.showMessageDialog(parent, problem, mode.title, JOptionPane.WARNING_MESSAGE);
                if (o != null) o.wipe();
                continue;
            }
            if (o.passwordsMatch() && Dialogs.showConfirmDialog(parent,
                    "The open password and the permissions password are the same.\nAnyone who can open the file could then also remove its restrictions.\n\nUse the same password anyway?",
                    mode.title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) { o.wipe(); continue; }
            if (weak(o.openPassword) && Dialogs.showConfirmDialog(parent,
                    "The open password is easy to guess.\n\nUse it anyway?", mode.title,
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) { o.wipe(); continue; }
            form.wipe();
            return o;
        }
    }

    private static boolean weak(char[] pw) {
        if (pw.length == 0) return false;
        var level = PasswordStrength.evaluate(pw).level();
        return level == PasswordStrength.Level.WEAK;
    }

    // ------------------------------------------------------------------

    private static final class Form {
        final JPanel panel = new JPanel(new GridBagLayout());
        final SecretField open = new SecretField(22), openConfirm = new SecretField(22);
        final SecretField perm = new SecretField(22), permConfirm = new SecretField(22);
        final JCheckBox restrict = new JCheckBox("Restrict what people can do with this file");
        final JPanel restrictPanel = new JPanel(new GridBagLayout());
        final JCheckBox[] boxes = new JCheckBox[ProtectionOptions.PERMISSIONS.length];
        final JComboBox<String> strength = new JComboBox<>(new String[]{"AES-256  (recommended)", "AES-128  (for older readers)"});
        final StrengthBar bar = new StrengthBar();
        final JLabel hint = new JLabel(" ");
        String problem;

        Form(ProtectionOptions initial, Mode mode) {
            panel.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;

            JLabel intro = new JLabel("<html><div style='width:430px'>" + mode.intro + "</div></html>");
            intro.setFont(AppFonts.body(Font.PLAIN, 13));
            intro.setForeground(Ui.muted());
            c.gridy = 0; c.insets = new Insets(0, 0, 12, 0);
            panel.add(intro, c);

            // ---- open password ----
            c.gridy = 1; c.insets = new Insets(0, 0, 4, 0);
            panel.add(section("lock", "Password to open the file", "Leave empty to let anyone open it."), c);
            c.gridy = 2; c.insets = new Insets(0, 0, 6, 0);
            panel.add(open, c);
            c.gridy = 3;
            panel.add(openConfirm, c);
            openConfirm.field().putClientProperty("JTextField.placeholderText", "Type it again to confirm");
            open.field().putClientProperty("JTextField.placeholderText", "Password");

            JPanel meter = new JPanel(new BorderLayout(10, 0));
            meter.setOpaque(false);
            bar.setPreferredSize(new Dimension(150, 8));
            JPanel barBox = new JPanel(new GridBagLayout());
            barBox.setOpaque(false);
            barBox.add(bar);
            hint.setFont(AppFonts.body(Font.PLAIN, 12));
            meter.add(barBox, BorderLayout.WEST);
            meter.add(hint, BorderLayout.CENTER);
            JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            tools.setOpaque(false);
            JButton gen = Ui.button("Generate");
            gen.setIcon(Ui.icon("dices", 16));
            gen.setIconTextGap(6);
            gen.setToolTipText("Create a strong random password");
            gen.addActionListener(e -> {
                char[] g = PasswordStrength.generate(16);
                open.setChars(g); openConfirm.setChars(g);
                open.setRevealed(true); openConfirm.setRevealed(true);
                Arrays.fill(g, '\0');
            });
            JButton copy = Ui.iconButton("copy", "Copy the password to the clipboard");
            copy.addActionListener(e -> {
                char[] p = open.getChars();
                if (p.length > 0) Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(new String(p)), null);
                Arrays.fill(p, '\0');
            });
            tools.add(gen);
            tools.add(copy);
            meter.add(tools, BorderLayout.EAST);
            c.gridy = 4; c.insets = new Insets(0, 0, 14, 0);
            panel.add(meter, c);
            open.addChangeListener(this::updateMeter);

            // ---- restrictions ----
            restrict.setOpaque(false);
            restrict.setFont(AppFonts.body(Font.BOLD, 14));
            c.gridy = 5; c.insets = new Insets(0, 0, 4, 0);
            panel.add(restrict, c);

            GridBagConstraints r = new GridBagConstraints();
            r.gridx = 0; r.gridwidth = 2; r.weightx = 1; r.fill = GridBagConstraints.HORIZONTAL; r.anchor = GridBagConstraints.WEST;
            restrictPanel.setOpaque(false);
            r.gridy = 0; r.insets = new Insets(0, 24, 4, 0);
            JLabel pLabel = new JLabel("Permissions password: needed to change these settings later");
            pLabel.setFont(AppFonts.body(Font.PLAIN, 12));
            pLabel.setForeground(Ui.muted());
            restrictPanel.add(pLabel, r);
            r.gridy = 1; r.insets = new Insets(0, 24, 6, 0);
            perm.field().putClientProperty("JTextField.placeholderText", "Permissions password");
            restrictPanel.add(perm, r);
            r.gridy = 2;
            permConfirm.field().putClientProperty("JTextField.placeholderText", "Type it again to confirm");
            restrictPanel.add(permConfirm, r);
            JPanel grid = new JPanel(new GridLayout(0, 2, 12, 2));
            grid.setOpaque(false);
            JLabel allow = new JLabel("People may:");
            allow.setFont(AppFonts.body(Font.PLAIN, 12));
            allow.setForeground(Ui.muted());
            r.gridy = 3; r.insets = new Insets(8, 24, 2, 0);
            restrictPanel.add(allow, r);
            for (int i = 0; i < boxes.length; i++) {
                boxes[i] = new JCheckBox(ProtectionOptions.PERMISSIONS[i], initial.allowed[i]);
                boxes[i].setOpaque(false);
                boxes[i].setFont(AppFonts.body(Font.PLAIN, 13));
                grid.add(boxes[i]);
            }
            r.gridy = 4; r.insets = new Insets(0, 24, 0, 0);
            restrictPanel.add(grid, r);
            c.gridy = 6; c.insets = new Insets(0, 0, 12, 0);
            panel.add(restrictPanel, c);
            restrict.setSelected(initial.restrictsAnything());
            restrictPanel.setVisible(restrict.isSelected());
            restrict.addActionListener(e -> {
                restrictPanel.setVisible(restrict.isSelected());
                Window w = SwingUtilities.getWindowAncestor(panel);
                if (w != null) w.pack();
            });

            // ---- encryption ----
            JPanel enc = new JPanel(new BorderLayout(10, 0));
            enc.setOpaque(false);
            JLabel el = new JLabel("Encryption");
            el.setFont(AppFonts.body(Font.BOLD, 14));
            Ui.styleCombo(strength);
            enc.add(el, BorderLayout.WEST);
            enc.add(strength, BorderLayout.CENTER);
            c.gridy = 7; c.insets = new Insets(0, 0, 0, 0);
            panel.add(enc, c);
            updateMeter();
        }

        private JComponent section(String icon, String title, String sub) {
            JPanel p = new JPanel(new BorderLayout(8, 0));
            p.setOpaque(false);
            JLabel t = new JLabel(title, Ui.icon(icon, 16, ui.SvgIcon.Tint.BRAND), SwingConstants.LEADING);
            t.setIconTextGap(8);
            t.setFont(AppFonts.body(Font.BOLD, 14));
            JLabel s = new JLabel(sub);
            s.setFont(AppFonts.body(Font.PLAIN, 12));
            s.setForeground(Ui.muted());
            p.add(t, BorderLayout.WEST);
            p.add(s, BorderLayout.CENTER);
            s.setHorizontalAlignment(SwingConstants.RIGHT);
            return p;
        }

        void updateMeter() {
            char[] p = open.getChars();
            var res = PasswordStrength.evaluate(p);
            Arrays.fill(p, '\0');
            bar.set(res.level());
            hint.setText(res.level() == PasswordStrength.Level.EMPTY ? " " : label(res.level()) + " \u2013 " + res.hint());
        }

        private static String label(PasswordStrength.Level l) {
            return switch (l) { case WEAK -> "Weak"; case FAIR -> "Fair"; case GOOD -> "Good"; case STRONG -> "Strong"; default -> ""; };
        }

        /** Builds options from the form, or returns null and sets {@link #problem} when the confirmations don't match. */
        ProtectionOptions read() {
            problem = null;
            char[] o = open.getChars(), oc = openConfirm.getChars();
            if (!Arrays.equals(o, oc)) { problem = "The two open passwords don't match."; Arrays.fill(o, '\0'); Arrays.fill(oc, '\0'); return null; }
            Arrays.fill(oc, '\0');
            ProtectionOptions opt = new ProtectionOptions();
            opt.openPassword = o;
            opt.keyBits = strength.getSelectedIndex() == 0 ? 256 : 128;
            if (restrict.isSelected()) {
                char[] p = perm.getChars(), pc = permConfirm.getChars();
                if (!Arrays.equals(p, pc)) {
                    problem = "The two permissions passwords don't match.";
                    Arrays.fill(p, '\0'); Arrays.fill(pc, '\0'); opt.wipe();
                    return null;
                }
                Arrays.fill(pc, '\0');
                opt.permissionsPassword = p;
                for (int i = 0; i < boxes.length; i++) opt.allowed[i] = boxes[i].isSelected();
            }
            return opt;
        }

        void wipe() {
            open.clear(); openConfirm.clear(); perm.clear(); permConfirm.clear();
        }
    }

    /** Four-segment meter. */
    private static final class StrengthBar extends JComponent {
        private PasswordStrength.Level level = PasswordStrength.Level.EMPTY;

        void set(PasswordStrength.Level l) { level = l; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int filled = switch (level) { case WEAK -> 1; case FAIR -> 2; case GOOD -> 3; case STRONG -> 4; default -> 0; };
                Color on = switch (level) {
                    case WEAK -> new Color(0xE0334F); case FAIR -> new Color(0xFF9F1C);
                    case GOOD -> new Color(0xB5CC18); default -> new Color(0x2EC27E); };
                int gap = 4, w = (getWidth() - 3 * gap) / 4, h = getHeight();
                for (int i = 0; i < 4; i++) {
                    g.setColor(i < filled ? on : Ui.line());
                    g.fillRoundRect(i * (w + gap), 0, w, h, h, h);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
