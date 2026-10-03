package rightpanel;

import ui.Dialogs;
import app.Main;
import store.FileListStore;
import ui.Ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;

/**
 * Landing screen: logo and tagline, the latest recent files, and quick
 * actions. The recent list reloads each time the screen is shown.
 */
public class home extends JPanel {

    private static final String APP_NAME = "Readora: The PDF App";

    private final Main main;
    private final JLabel titleLabel;
    private final DefaultTableModel model;
    private final JTable table;
    private List<FileListStore.Entry> entries = List.of();
    private Timer typing;

    public home(Main main, Runnable openPdfAction, Runnable recentAction,
                Runnable toolsAction, Runnable settingsAction) {
        this.main = main;
        setLayout(new BorderLayout(Ui.GAP, Ui.GAP));
        setOpaque(false);
        Ui.padScreen(this);

        // ---- hero ----
        titleLabel = new JLabel("", loadLogo(), SwingConstants.CENTER);
        titleLabel.setFont(AppFonts.title(Font.PLAIN, 54));
        JLabel tagline = Ui.subtitle("Read, manage, and organize PDFs");
        tagline.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel hero = new JPanel(new BorderLayout());
        hero.setOpaque(false);
        hero.add(titleLabel, BorderLayout.CENTER);
        hero.add(tagline, BorderLayout.SOUTH);
        add(hero, BorderLayout.CENTER);

        // ---- bottom: recents + quick actions ----
        JPanel south = new JPanel(new GridLayout(1, 2, Ui.GAP, 0));
        south.setOpaque(false);
        south.setPreferredSize(new Dimension(0, 260));

        JPanel recentPanel = new JPanel(new BorderLayout(0, 10));
        recentPanel.setOpaque(false);
        recentPanel.add(Ui.section("Recents"), BorderLayout.NORTH);
        model = new DefaultTableModel(new Object[]{"File", "Opened At"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(1).setMinWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });
        JScrollPane sp = new JScrollPane(table);
        Ui.styleScroll(sp);
        recentPanel.add(sp, BorderLayout.CENTER);
        JButton refresh = Ui.button("Refresh");
        refresh.addActionListener(e -> reload());
        recentPanel.add(Ui.buttonBar(refresh), BorderLayout.SOUTH);
        south.add(recentPanel);

        JPanel actionsWrap = new JPanel(new BorderLayout(0, 10));
        actionsWrap.setOpaque(false);
        actionsWrap.add(Ui.section("Quick Actions"), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);
        grid.add(action("Open PDF", openPdfAction));
        grid.add(action("Recent", recentAction));
        grid.add(action("Tools", toolsAction));
        grid.add(action("Settings", settingsAction));
        actionsWrap.add(grid, BorderLayout.CENTER);
        south.add(actionsWrap);
        add(south, BorderLayout.SOUTH);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { reload(); }
        });
        reload();
        startTyping();
    }

    private JButton action(String text, Runnable r) {
        JButton b = Ui.tile(text);
        b.setFont(AppFonts.body(Font.BOLD, 14));
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Reloads the (up to 8) most recent files. */
    public void reload() {
        entries = recent.STORE.load();
        model.setRowCount(0);
        int n = 0;
        for (FileListStore.Entry e : entries) {
            if (n++ == 8) break;
            model.addRow(new Object[]{e.name(), e.when()});
        }
    }

    private void openSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= entries.size()) return;
        FileListStore.Entry e = entries.get(row);
        if (!e.exists()) {
            Dialogs.showMessageDialog(this, "This file no longer exists:\n" + e.path(),
                    "File not found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            main.showPdf(e.file);
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not open file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Types the app name out one character at a time, once. */
    private void startTyping() {
        final int[] i = {0};
        typing = new Timer(45, e -> {
            titleLabel.setText(APP_NAME.substring(0, ++i[0]));
            if (i[0] >= APP_NAME.length()) typing.stop();
        });
        typing.start();
    }

    private ImageIcon loadLogo() {
        java.net.URL url = getClass().getResource("/logo.png");
        if (url == null) return new ImageIcon();
        Image img = new ImageIcon(url).getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }
}
