package rightpanel;

import ui.Dialogs;
import app.Main;
import store.FileListStore;
import ui.Ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Shared implementation of the "Recent" and "Starred" screens: a search
 * box, a table of files, and a bottom button bar. The two screens differ
 * only in their wording and in which {@link FileListStore} they show, so
 * they are now thin subclasses instead of two ~425-line copies.
 *
 * <p>The list reloads every time the screen is shown, so a file opened
 * (or starred) elsewhere appears immediately without pressing Refresh.</p>
 */
public abstract class FileListScreen extends JPanel {

    private final Main main;
    private final FileListStore store;
    private final String emptyHint;

    private final JTextField searchField = new JTextField();
    private final DefaultTableModel model;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JLabel emptyLabel = new JLabel("", SwingConstants.CENTER);
    private List<FileListStore.Entry> entries = List.of();

    protected FileListScreen(Main main, FileListStore store, String title, String subtitle,
                             String whenColumn, String removeLabel, boolean withClear, String emptyHint) {
        this.main = main;
        this.store = store;
        this.emptyHint = emptyHint;

        setLayout(new BorderLayout(0, Ui.GAP));
        setOpaque(false);
        Ui.padScreen(this);

        // ---- header ----
        add(Ui.header(title, subtitle), BorderLayout.NORTH);

        // ---- search + table ----
        JPanel center = new JPanel(new BorderLayout(0, Ui.GAP));
        center.setOpaque(false);

        searchField.putClientProperty("JTextField.placeholderText", "Search " + title.toLowerCase() + "…");
        Ui.styleField(searchField);
        JButton searchBtn = Ui.button("Search");
        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchBtn, BorderLayout.EAST);
        center.add(searchRow, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[]{"File", "Path", whenColumn}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(560);
        table.getColumnModel().getColumn(2).setPreferredWidth(160);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JScrollPane scroll = new JScrollPane(table);
        Ui.styleScroll(scroll);
        emptyLabel.setFont(AppFonts.body(Font.PLAIN, 14));
        emptyLabel.setForeground(Ui.muted());
        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(scroll, BorderLayout.CENTER);
        stack.add(emptyLabel, BorderLayout.SOUTH);
        center.add(stack, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        // ---- bottom bar ----
        JButton open = Ui.primaryButton("Open");
        JButton refresh = Ui.button("Refresh");
        JButton remove = Ui.button(removeLabel);
        JPanel bar = withClear
                ? Ui.buttonBar(open, refresh, remove, clearButton())
                : Ui.buttonBar(open, refresh, remove);
        add(bar, BorderLayout.SOUTH);

        // ---- behaviour ----
        open.addActionListener(e -> openSelected());
        refresh.addActionListener(e -> reload());
        remove.addActionListener(e -> removeSelected());
        searchBtn.addActionListener(e -> applyFilter());
        searchField.addActionListener(e -> applyFilter());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) openSelected();
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); openSelected(); }
                else if (e.getKeyCode() == KeyEvent.VK_DELETE) removeSelected();
            }
        });
        // Reload whenever this card is brought to the front.
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { reload(); }
        });
        reload();
    }

    private JButton clearButton() {
        JButton clear = Ui.button("Clear");
        clear.addActionListener(e -> {
            if (entries.isEmpty()) return;
            int ok = Dialogs.showConfirmDialog(this, "Clear the whole list?", "Confirm",
                    JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) { store.clear(); reload(); }
        });
        return clear;
    }

    /** Re-reads the list from disk. */
    public void reload() {
        entries = store.load();
        model.setRowCount(0);
        for (FileListStore.Entry e : entries) {
            model.addRow(new Object[]{e.name(), e.path(), e.when()});
        }
        applyFilter();
        emptyLabel.setText(entries.isEmpty() ? emptyHint : " ");
    }

    private void applyFilter() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q), 0, 1));
        }
    }

    private FileListStore.Entry selected() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        String path = (String) model.getValueAt(table.convertRowIndexToModel(row), 1);
        for (FileListStore.Entry e : entries) if (e.path().equals(path)) return e;
        return null;
    }

    private void openSelected() {
        FileListStore.Entry e = selected();
        if (e == null) {
            Dialogs.showMessageDialog(this, "Select a file first.");
            return;
        }
        if (!e.exists()) {
            int r = Dialogs.showConfirmDialog(this,
                    "This file no longer exists:\n" + e.path() + "\n\nRemove it from the list?",
                    "File not found", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r == JOptionPane.YES_OPTION) { store.remove(e.file); reload(); }
            return;
        }
        try {
            main.showPdf(e.file);
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not open file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSelected() {
        FileListStore.Entry e = selected();
        if (e == null) {
            Dialogs.showMessageDialog(this, "Select a file first.");
            return;
        }
        store.remove(e.file);
        reload();
    }
}
