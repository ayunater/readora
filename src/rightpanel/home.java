package rightpanel;

import app.AppInfo;
import app.Main;
import store.FileListStore;
import ui.Dialogs;
import ui.GradientLabel;
import ui.SvgIcon;
import ui.Ui;
import viewer.PdfDropHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

/**
 * The landing screen: a branded header, a drop zone for opening a PDF, quick actions, and the most recent
 * files as cards with a preview of their first page.
 *
 * <p>Everything reloads when the screen is shown, so a file opened elsewhere appears immediately.</p>
 */
public class home extends JPanel {

    private static final int RECENT_SHOWN = 5;

    private final Main main;
    private final Runnable openPdfAction, recentAction;
    private final JPanel recentArea = new JPanel(new BorderLayout());

    public home(Main main, Runnable newPdfAction, Runnable openPdfAction, Runnable recentAction,
                Runnable starredAction, Runnable toolsAction, Runnable settingsAction) {
        this.main = main;
        this.openPdfAction = openPdfAction;
        this.recentAction = recentAction;
        setLayout(new BorderLayout());
        setOpaque(false);

        // A GridBagLayout column: every row is stretched to the full width, whatever it contains.
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
        JLabel tip = new JLabel("Tip: drag a PDF anywhere onto this window to open it.", Ui.icon("lightbulb", 14, SvgIcon.Tint.MUTED), SwingConstants.LEADING);
        tip.setIconTextGap(8);
        tip.setForeground(Ui.muted());
        tip.setFont(AppFonts.body(Font.PLAIN, 12));
        recentArea.setOpaque(false);

        addRow(content, 0, hero(), 0);
        addRow(content, 1, new DropZone(), 20);
        addRow(content, 2, heading("Quick actions", null, null), 26);
        addRow(content, 3, actions(newPdfAction, openPdfAction, starredAction, toolsAction, settingsAction), 10);
        addRow(content, 4, heading("Recent files", "View all", recentAction), 26);
        addRow(content, 5, recentArea, 10);
        addRow(content, 6, tip, 18);
        GridBagConstraints fill = new GridBagConstraints();
        fill.gridx = 0; fill.gridy = 7; fill.weighty = 1; fill.fill = GridBagConstraints.BOTH;
        content.add(Box.createGlue(), fill);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { reload(); }
        });
        reload();
    }

    private static void addRow(JPanel page, int y, JComponent c, int gapAbove) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = y; g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(gapAbove, 0, 0, 0);
        page.add(c, g);
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    private JComponent hero() {
        JPanel p = new JPanel(new BorderLayout(18, 0));
        p.setOpaque(false);
        p.add(new JLabel(Ui.logo(84)), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        int hour = LocalTime.now().getHour();
        JLabel hello = new JLabel(hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening");
        hello.setFont(AppFonts.body(Font.PLAIN, 13));
        hello.setForeground(Ui.muted());
        GradientLabel name = new GradientLabel("Readora", AppFonts.title(Font.PLAIN, 50));
        JLabel tagline = new JLabel("Read, edit and secure your PDFs");
        tagline.setFont(AppFonts.body(Font.PLAIN, 15));
        for (JComponent c : new JComponent[]{hello, name, tagline}) { c.setAlignmentX(Component.LEFT_ALIGNMENT); text.add(c); }
        p.add(text, BorderLayout.CENTER);

        JLabel version = new JLabel("v" + AppInfo.VERSION);
        version.setForeground(Ui.muted());
        version.setFont(AppFonts.body(Font.PLAIN, 12));
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(version, BorderLayout.NORTH);
        p.add(east, BorderLayout.EAST);
        return p;
    }

    private JComponent heading(String text, String linkText, Runnable link) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(Ui.section(text), BorderLayout.WEST);
        if (link != null) {
            JButton b = new JButton(linkText, Ui.icon("arrow-right", 14, SvgIcon.Tint.ACCENT));
            b.setHorizontalTextPosition(SwingConstants.LEADING);
            b.setIconTextGap(6);
            b.setFont(AppFonts.body(Font.PLAIN, 13));
            b.setFocusPainted(false);
            b.putClientProperty("JButton.buttonType", "borderless");
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> link.run());
            p.add(b, BorderLayout.EAST);
        }
        return p;
    }

    // ------------------------------------------------------------------
    // Quick actions
    // ------------------------------------------------------------------

    private JComponent actions(Runnable newPdf, Runnable open, Runnable starred, Runnable tools, Runnable settings) {
        JPanel row = new JPanel(new GridLayout(1, 5, 12, 0));
        row.setOpaque(false);
        row.add(new ActionCard("file-plus", "New PDF", "Start from a blank page", newPdf));
        row.add(new ActionCard("folder-open", "Open PDF", "Browse your files", open));
        row.add(new ActionCard("star", "Starred", "Your favourites", starred));
        row.add(new ActionCard("wrench", "Tools", "Merge, split, convert, secure", tools));
        row.add(new ActionCard("settings", "Settings", "Theme and preferences", settings));
        return row;
    }

    /** A large button with an icon, a title and a one-line description. */
    private static final class ActionCard extends JButton {
        ActionCard(String icon, String title, String desc, Runnable action) {
            setLayout(new BorderLayout(0, 6));
            setFocusPainted(false);
            putClientProperty("FlatLaf.style", "arc:" + Ui.ARC + "; margin:16,16,14,16");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel i = new JLabel(Ui.icon(icon, 28, SvgIcon.Tint.BRAND));
            i.setHorizontalAlignment(SwingConstants.LEFT);
            JLabel t = new JLabel(title);
            t.setFont(AppFonts.body(Font.BOLD, 15));
            JLabel d = new JLabel(desc);
            d.setFont(AppFonts.body(Font.PLAIN, 12));
            d.setForeground(Ui.muted());
            JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
            text.setOpaque(false);
            text.add(t);
            text.add(d);
            add(i, BorderLayout.NORTH);
            add(text, BorderLayout.CENTER);
            setPreferredSize(new Dimension(150, 118));
            addActionListener(e -> action.run());
        }
    }

    // ------------------------------------------------------------------
    // Drop zone
    // ------------------------------------------------------------------

    /** A dashed target that lights up while a file is dragged over it; clicking it opens the file chooser. */
    private final class DropZone extends JPanel {
        private boolean hover;

        DropZone() {
            setOpaque(false);
            setLayout(new GridBagLayout());
            setPreferredSize(new Dimension(100, 112));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            setToolTipText("Click to choose a PDF, or drop one here");

            JPanel inner = new JPanel(new BorderLayout(16, 0));
            inner.setOpaque(false);
            inner.add(new JLabel(Ui.icon("file-up", 40, SvgIcon.Tint.BRAND)), BorderLayout.WEST);
            JPanel t = new JPanel(new GridLayout(2, 1, 0, 2));
            t.setOpaque(false);
            JLabel a = new JLabel("Drop a PDF here to open it");
            a.setFont(AppFonts.body(Font.BOLD, 17));
            JLabel b = new JLabel("or click to browse your files");
            b.setFont(AppFonts.body(Font.PLAIN, 13));
            b.setForeground(Ui.muted());
            t.add(a);
            t.add(b);
            inner.add(t, BorderLayout.CENTER);
            add(inner);

            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { setHover(true); }
                @Override public void mouseExited(MouseEvent e) { setHover(false); }
                @Override public void mouseClicked(MouseEvent e) { openPdfAction.run(); }
            };
            addMouseListener(m);
            inner.addMouseListener(m);
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "open");
            getActionMap().put("open", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { openPdfAction.run(); }
            });
            DropTarget dt = PdfDropHandler.hoverTarget(this, f -> openFile(f), this::setHover);
            if (dt != null) setDropTarget(dt);
        }

        private void setHover(boolean on) { hover = on; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth() - 2, h = getHeight() - 2;
                boolean focus = isFocusOwner();
                Color base = Ui.accent();
                g.setColor(hover ? new Color(base.getRed(), base.getGreen(), base.getBlue(), 34) : Ui.raised());
                g.fillRoundRect(1, 1, w, h, Ui.ARC, Ui.ARC);
                if (hover || focus) {
                    g.setPaint(new LinearGradientPaint(0, 0, getWidth(), 0, new float[]{0f, .5f, 1f}, new Color[]{Ui.BLUE, Ui.VIOLET, Ui.MAGENTA}));
                    g.setStroke(new BasicStroke(2f));
                } else {
                    g.setColor(Ui.line());
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{7f, 6f}, 0f));
                }
                g.drawRoundRect(1, 1, w, h, Ui.ARC, Ui.ARC);
            } finally {
                g.dispose();
            }
        }
    }

    // ------------------------------------------------------------------
    // Recent files
    // ------------------------------------------------------------------

    /** Reloads the recent-file cards. */
    public void reload() {
        List<FileListStore.Entry> all = recent.STORE.load();
        recentArea.removeAll();
        if (all.isEmpty()) {
            recentArea.add(emptyState(), BorderLayout.CENTER);
        } else {
            JPanel grid = new JPanel(new GridLayout(1, RECENT_SHOWN, 12, 0));
            grid.setOpaque(false);
            int shown = 0;
            for (FileListStore.Entry e : all) {
                if (shown == RECENT_SHOWN) break;
                grid.add(new RecentCard(e));
                shown++;
            }
            // Pad to exactly RECENT_SHOWN cells so every card keeps the same width (a sixth cell would add a column).
            for (; shown < RECENT_SHOWN; shown++) { JPanel filler = new JPanel(); filler.setOpaque(false); grid.add(filler); }
            recentArea.add(grid, BorderLayout.CENTER);
        }
        recentArea.revalidate();
        recentArea.repaint();
    }

    private JComponent emptyState() {
        Ui.Card card = new Ui.Card(new GridBagLayout());
        card.setPreferredSize(new Dimension(100, 150));
        JPanel in = new JPanel(new GridLayout(3, 1, 0, 4));
        in.setOpaque(false);
        JLabel icon = new JLabel(Ui.icon("file-text", 34, SvgIcon.Tint.MUTED));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel a = new JLabel("No recent files yet", SwingConstants.CENTER);
        a.setFont(AppFonts.body(Font.BOLD, 15));
        JLabel b = new JLabel("Open or create a PDF and it will show up here.", SwingConstants.CENTER);
        b.setFont(AppFonts.body(Font.PLAIN, 13));
        b.setForeground(Ui.muted());
        in.add(icon);
        in.add(a);
        in.add(b);
        card.add(in);
        return card;
    }

    private void openFile(File f) {
        try {
            main.showPdf(f);
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not open \"" + f.getName() + "\":\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** One recent file: a preview of its first page, its name, and when it was opened. */
    private final class RecentCard extends JButton {
        private final FileListStore.Entry entry;
        private final Preview preview;

        RecentCard(FileListStore.Entry entry) {
            this.entry = entry;
            setLayout(new BorderLayout(0, 8));
            setFocusPainted(false);
            putClientProperty("FlatLaf.style", "arc:" + Ui.ARC + "; margin:10,10,10,10");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(entry.path());
            preview = new Preview();
            JLabel name = new JLabel(entry.name());
            name.setFont(AppFonts.body(Font.BOLD, 13));
            JLabel meta = new JLabel(entry.exists() ? ago(entry.timeMillis) + "  \u2022  " + fileSize(entry.file.length()) : "File not found");
            meta.setFont(AppFonts.body(Font.PLAIN, 12));
            meta.setForeground(Ui.muted());
            JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
            text.setOpaque(false);
            text.add(name);
            text.add(meta);
            add(preview, BorderLayout.CENTER);
            add(text, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(150, 250));
            addActionListener(e -> open());

            JPopupMenu menu = new JPopupMenu();
            JMenuItem openItem = new JMenuItem("Open");
            openItem.addActionListener(e -> open());
            JMenuItem copy = new JMenuItem("Copy path");
            copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(entry.path()), null));
            JMenuItem remove = new JMenuItem("Remove from list");
            remove.addActionListener(e -> { recent.STORE.remove(entry.file); reload(); });
            menu.add(openItem);
            menu.add(copy);
            menu.addSeparator();
            menu.add(remove);
            setComponentPopupMenu(menu);
        }

        private void open() {
            if (!entry.exists()) {
                int r = Dialogs.showConfirmDialog(home.this, "This file no longer exists:\n" + entry.path() + "\n\nRemove it from the list?",
                        "File not found", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r == JOptionPane.YES_OPTION) { recent.STORE.remove(entry.file); reload(); }
                return;
            }
            openFile(entry.file);
        }

        /** The thumbnail area. */
        private final class Preview extends JComponent {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    int w = getWidth(), h = getHeight();
                    g.setColor(Ui.sunken());
                    g.fillRoundRect(0, 0, w, h, 14, 14);
                    RecentThumbs.Thumb t = entry.exists() ? RecentThumbs.get(entry.file, () -> repaint()) : null;
                    if (t != null && t.image != null) {
                        int iw = t.image.getWidth(), ih = t.image.getHeight();
                        double s = Math.min((w - 24) / (double) iw, (h - 24) / (double) ih);
                        int dw = (int) Math.round(iw * s), dh = (int) Math.round(ih * s);
                        int x = (w - dw) / 2, y = (h - dh) / 2;
                        g.setColor(new Color(0, 0, 0, 50));
                        g.fillRoundRect(x + 2, y + 3, dw, dh, 4, 4);
                        g.drawImage(t.image, x, y, dw, dh, null);
                        g.setColor(new Color(0, 0, 0, 45));
                        g.drawRect(x, y, dw - 1, dh - 1);
                    } else {
                        String icon = t != null && t.locked ? "lock" : "file-text";
                        String label = !entry.exists() ? "Missing" : t != null && t.locked ? "Password protected" : t != null ? "No preview" : "";
                        Ui.icon(icon, 34, SvgIcon.Tint.MUTED).paintIcon(this, g, (w - 34) / 2, h / 2 - 28);
                        if (!label.isEmpty()) {
                            g.setFont(AppFonts.body(Font.PLAIN, 12));
                            g.setColor(Ui.muted());
                            FontMetrics fm = g.getFontMetrics();
                            g.drawString(label, (w - fm.stringWidth(label)) / 2, h / 2 + 24);
                        }
                    }
                } finally {
                    g.dispose();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) { long h = s / 3600; return h + (h == 1 ? " hour ago" : " hours ago"); }
        if (s < 2 * 86400) return "yesterday";
        if (s < 7 * 86400) return (s / 86400) + " days ago";
        return new SimpleDateFormat("d MMM yyyy").format(new Date(t));
    }

    static String fileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
