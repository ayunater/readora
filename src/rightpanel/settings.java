package rightpanel;

import ui.Dialogs;
import app.AppInfo;
import app.Main;
import store.AppSettings;
import ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Set;

/**
 * Settings screen: theme, behaviour toggles, maintenance and About.
 * Values live in {@code settings.properties} via {@link AppSettings}.
 * Checkbox changes save immediately; the theme is applied with its button.
 */
public class settings extends JPanel {

    private final Main main;
    private final JCheckBox rememberRecent = new JCheckBox("Remember recent files");
    private final JCheckBox confirmExit = new JCheckBox("Confirm before exit");

    /** Files in the cache folder that hold user data and must survive "Clear Cache". */
    private static final Set<String> KEEP = Set.of(
            "settings.properties", "recent.tsv", "starred.tsv", "recent.csv", "starred.csv");

    public settings(Main main) {
        this.main = main;
        setLayout(new BorderLayout(0, Ui.GAP));
        setOpaque(false);
        Ui.padScreen(this);

        add(Ui.header("Settings", "Customize " + AppInfo.NAME), BorderLayout.NORTH);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        // ---- Appearance: Readora Dark / Readora Light, applied the moment you pick one ----
        ButtonGroup themeGroup = new ButtonGroup();
        JPanel themeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        themeRow.setOpaque(false);
        for (String name : ThemeManager.THEMES) {
            JToggleButton t = new JToggleButton(name.replace("Readora ", ""));
            Ui.styleButton(t);
            t.setSelected(ThemeManager.isDark(name) == Ui.isDark());   // reflects what is actually applied
            t.addActionListener(e -> {
                ThemeManager.applyTheme(name);
                AppSettings.set("theme", name);
                main.refreshTheme();
            });
            themeGroup.add(t);
            themeRow.add(t);
        }
        column.add(group("Appearance", themeRow));

        // ---- Behaviour ----
        rememberRecent.setSelected(AppSettings.rememberRecent());
        confirmExit.setSelected(AppSettings.confirmExit());
        for (JCheckBox c : new JCheckBox[]{rememberRecent, confirmExit}) {
            c.setFont(AppFonts.body(Font.PLAIN, 14));
            c.setOpaque(false);
        }
        rememberRecent.addActionListener(e ->
                AppSettings.set("rememberRecent", String.valueOf(rememberRecent.isSelected())));
        confirmExit.addActionListener(e ->
                AppSettings.set("confirmExit", String.valueOf(confirmExit.isSelected())));
        column.add(Box.createVerticalStrut(Ui.GAP));
        column.add(group("Behavior", rememberRecent, confirmExit));

        // ---- Maintenance ----
        JButton clearRecent = Ui.button("Clear Recent Files");
        clearRecent.addActionListener(e -> {
            recent.STORE.clear();
            Dialogs.showMessageDialog(this, "Recent history cleared.");
        });
        JButton clearCache = Ui.button("Clear Cache");
        clearCache.addActionListener(e -> clearCache());
        column.add(Box.createVerticalStrut(Ui.GAP));
        column.add(group("Maintenance", row(clearRecent, clearCache)));

        // ---- About ----
        JButton about = Ui.button("About " + AppInfo.NAME);
        about.addActionListener(e -> Dialogs.showMessageDialog(this,
                AppInfo.NAME + " v" + AppInfo.VERSION + "\n"
                        + "A modern PDF utility for reading, organizing,\n"
                        + "converting, and managing PDF documents.\n\n"
                        + "Built with Java Swing, FlatLaf and Apache PDFBox.\n"
                        + "Developed by Ayush",
                "About " + AppInfo.NAME, JOptionPane.INFORMATION_MESSAGE));
        column.add(Box.createVerticalStrut(Ui.GAP));
        column.add(group("About", row(about)));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(column, BorderLayout.NORTH);
        Ui.padView(top);
        JScrollPane scroll = new JScrollPane(top);
        Ui.styleScroll(scroll);                       // same rounded frame as the Recent table
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
    }

    /** Removes temporary data only — never settings or the Recent/Starred lists. */
    private void clearCache() {
        File dir = new File(AppPaths.getCacheDir());
        File[] files = dir.listFiles();
        int removed = 0;
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !KEEP.contains(f.getName()) && f.delete()) removed++;
            }
        }
        Dialogs.showMessageDialog(this, removed == 0
                ? "Cache is already empty."
                : "Cache cleared (" + removed + " file" + (removed == 1 ? "" : "s") + ").");
    }

    private static JPanel row(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        for (JComponent c : items) p.add(c);
        return p;
    }

    /** A titled rounded card holding the given rows. */
    private static JPanel group(String title, JComponent... rows) {
        Ui.Card card = Ui.card(new BorderLayout(0, 10), Ui.GAP);
        card.add(Ui.section(title), BorderLayout.NORTH);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        for (JComponent r : rows) {
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(r);
            body.add(Box.createVerticalStrut(6));
        }
        card.add(body, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 400));
        return card;
    }
}
