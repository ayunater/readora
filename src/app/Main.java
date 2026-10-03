package app;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import rightpanel.home;

import rightpanel.*;
import store.AppSettings;
import ui.Ui;
import viewer.PdfDropHandler;
import viewer.PdfViewer;

/**
 * The application's main window and entry point.
 *
 * Structurally, this is a {@link JFrame} split into two regions via
 * {@code GridBagLayout}: a collapsible left sidebar ({@link #leftPanel})
 * with navigation buttons, and a right-hand content area
 * ({@link #rightPanel}) holding a {@link CardLayout} ({@link #cards})
 * that swaps between the app's screens — Home, Recent, Starred, Tools,
 * Settings, and the PDF viewer itself. Sidebar buttons switch cards via
 * {@link #actionPerformed(ActionEvent)}; the Home screen's quick-action
 * buttons switch cards via callback lambdas passed into its
 * constructor instead, since {@code home} doesn't have a reference to
 * this class (see {@code home}'s javadoc for why).
 *
 * {@link #main(String[])} is the JVM entry point: it loads the saved
 * theme (if any) and applies it via {@link ThemeManager} *before*
 * constructing this frame, since a FlatLaf theme has to be installed
 * before any Swing components exist for it to take effect on them
 * automatically.
 */
public class Main extends JFrame implements ActionListener {

        /**
         * The PDF viewer screen; kept as a field so other screens (Home, Recent,
         * Starred, Tools) can hand it files to open via {@link #showPdf(File)}.
         */
        PdfViewer viewerPage;

        /**
         * Current sidebar width as a fraction of total window width (0.12 = expanded,
         * 0.05 = collapsed), animated by {@link #animateSidebar(double)}.
         */
        private double sidebarWeight = 0.12;

        /**
         * Drives the sidebar collapse/expand animation; stopped and replaced on each
         * toggle.
         */
        private Timer sidebarTimer;

        /**
         * The frame's content pane, laid out with GridBagLayout to hold
         * {@link #leftPanel} and {@link #rightPanel} side by side.
         */
        Container root;

        /** Whether the sidebar is currently in its collapsed (icon-only) state. */
        boolean collapsed = false;

        /**
         * Holds every screen (Home, Recent, Starred, Tools, Settings, Viewer), switched
         * via {@link #cardLayout}.
         */
        JPanel cards;
        CardLayout cardLayout;

        JPanel leftPanel;
        JPanel leftTopPanel;
        JPanel leftBottomPanel;

        JButton menuButton;
        JButton homeButton;
        JButton recentButton;
        JButton starredButton;
        JButton toolsButton;
        JButton exitButton;
        JButton settingButton;

        JPanel rightPanel;

        /**
         * Builds the entire main window: sidebar, navigation buttons, and
         * every screen (each constructed once and added to {@link #cards}
         * up front, rather than lazily). Called only from
         * {@link #main(String[])} via {@code SwingUtilities.invokeLater},
         * so the active FlatLaf theme (applied in {@code main} before this
         * runs) is already in effect on every component built here.
         */
        Main() {
                ImageIcon icon = new ImageIcon(getClass().getResource("/logo.png"));
                setTitle(AppInfo.NAME);
                setIconImage(icon.getImage());
                Rectangle bounds = GraphicsEnvironment
                                .getLocalGraphicsEnvironment()
                                .getMaximumWindowBounds();

                setSize(
                                (int) (bounds.width * 0.85),
                                (int) (bounds.height * 0.85));
                setLocationRelativeTo(null);
                setLayout(new BorderLayout(5, 5));
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                setMinimumSize(new Dimension(850, 600));

                root = getContentPane();
                root.setLayout(new GridBagLayout());

                
                Color brighter = Ui.raised();

                leftPanel = new JPanel(new GridBagLayout());

                leftPanel.putClientProperty("FlatLaf.style", "arc: 16");

                leftPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                leftPanel.setBackground(brighter);

                addComponent(root,
                                leftPanel,
                                0, 0,
                                1, 1, 0.12, 1,
                                GridBagConstraints.BOTH,
                                null,
                                GridBagConstraints.CENTER);

                menuButton = new JButton(
                                "Menu",
                                loadIcon("/menu.png"));

                addComponent(leftPanel,
                                menuButton,
                                0, 0, 1, 1,
                                1.0, 1.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(0, 0, 10, 0),
                                GridBagConstraints.NORTH);

                menuButton.setPreferredSize(new Dimension(
                                0, 60));

                menuButton.addActionListener(e -> {
                        collapsed = !collapsed;
                        double target = collapsed ? 0.05 : 0.12;

                        if (collapsed) {
                                updateSidebarText();
                        }

                        animateSidebar(target);
                });

                leftTopPanel = new JPanel(new GridBagLayout());
                addComponent(leftPanel, leftTopPanel,
                                0, 1, 1, 1,
                                1.0, 0.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(15, 0, 20, 0), // top, left, bottom, right
                                GridBagConstraints.NORTH);

                leftTopPanel.putClientProperty("FlatLaf.style", "arc: 20");

                Dimension size = new Dimension(0, 60);

                homeButton = new JButton(
                                "Home",
                                loadIcon("/home.png"));

                addComponent(leftTopPanel, homeButton,
                                0, 0, 1, 1,
                                1.0, 0.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(4, 6, 4, 6),
                                GridBagConstraints.CENTER);

                homeButton.setPreferredSize(size);

                homeButton.addActionListener(this);
                homeButton.setActionCommand("Home");

                recentButton = new JButton(
                                "Recent",
                                loadIcon("/recent.png"));

                addComponent(leftTopPanel, recentButton,
                                0, 1, 1, 1,
                                1.0, 0.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(4, 6, 4, 6),
                                GridBagConstraints.CENTER);

                recentButton.setPreferredSize(size);

                recentButton.addActionListener(this);
                recentButton.setActionCommand("Recent");

                starredButton = new JButton(
                                "Starred",
                                loadIcon("/star.png"));

                addComponent(leftTopPanel, starredButton,
                                0, 2, 1, 1,
                                1.0, 0.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(4, 6, 4, 6),
                                GridBagConstraints.CENTER);

                starredButton.setPreferredSize(size);

                starredButton.addActionListener(this);
                starredButton.setActionCommand("Starred");

                toolsButton = new JButton(
                                "Tools",
                                loadIcon("/tools.png"));

                addComponent(leftTopPanel, toolsButton,
                                0, 3, 1, 1,
                                1.0, 0.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(4, 6, 4, 6),
                                GridBagConstraints.CENTER);

                toolsButton.setPreferredSize(size);

                toolsButton.addActionListener(this);
                toolsButton.setActionCommand("Tools");

                leftBottomPanel = new JPanel();
                leftBottomPanel.setLayout(new GridBagLayout());
                addComponent(leftPanel, leftBottomPanel,
                                0, 3, 1, 1,
                                1.0, 1.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(0, 0, 0, 0),
                                GridBagConstraints.SOUTH);

                leftBottomPanel.putClientProperty("FlatLaf.style", "arc: 20");

                settingButton = new JButton(
                                "Setting",
                                loadIcon("/settings.png"));

                addComponent(leftBottomPanel, settingButton,
                                0, 0, 1, 1,
                                1.0, 0.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(6, 6, 6, 6),
                                GridBagConstraints.CENTER);

                settingButton.setPreferredSize(size);

                settingButton.addActionListener(this);
                settingButton.setActionCommand("Settings");

                exitButton = new JButton(
                                "Exit",
                                loadIcon("/exit.png"));

                addComponent(leftBottomPanel, exitButton,
                                0, 1, 1, 1,
                                1.0, 1.0,
                                GridBagConstraints.HORIZONTAL,
                                new Insets(6, 6, 6, 6),
                                GridBagConstraints.CENTER);

                exitButton.setPreferredSize(size);

                exitButton.addActionListener(this);
                exitButton.setActionCommand("Exit");

                String buttonStyle = "arc:20";

                menuButton.putClientProperty("FlatLaf.style", buttonStyle);
                homeButton.putClientProperty("FlatLaf.style", buttonStyle);
                recentButton.putClientProperty("FlatLaf.style", buttonStyle);
                starredButton.putClientProperty("FlatLaf.style", buttonStyle);
                toolsButton.putClientProperty("FlatLaf.style", buttonStyle);
                settingButton.putClientProperty("FlatLaf.style", buttonStyle);
                exitButton.putClientProperty("FlatLaf.style", buttonStyle);

                menuButton.setBackground(brighter);
                homeButton.setBackground(brighter);
                recentButton.setBackground(brighter);
                starredButton.setBackground(brighter);
                toolsButton.setBackground(brighter);
                settingButton.setBackground(brighter);
                exitButton.setBackground(brighter);

                rightPanel = new JPanel(new GridBagLayout());

                rightPanel.putClientProperty("FlatLaf.style", "arc: 16");

                addComponent(root,
                                rightPanel,
                                1, 0,
                                1, 1, 0.88, 1,
                                GridBagConstraints.BOTH,
                                null,
                                GridBagConstraints.CENTER);

                cardLayout = new CardLayout();
                cards = new JPanel(cardLayout);

                cards.setOpaque(false);

                addComponent(rightPanel,
                                cards,
                                0, 0, 1, 1,
                                0.9, 1,
                                GridBagConstraints.BOTH,
                                new Insets(15, 15, 15, 15),
                                GridBagConstraints.CENTER);

                home homePage = new home(this,
                                this::openPdfDialog,
                                () -> cardLayout.show(cards, "RECENT"),
                                () -> cardLayout.show(cards, "TOOLS"),
                                () -> cardLayout.show(cards, "SETTINGS"));

                homePage.putClientProperty(
                                "FlatLaf.style",
                                "arc:20");

                homePage.setPreferredSize(new Dimension(0, 0));

                cards.add(homePage, "HOME");

                recent recentPage = new recent(this);

                recentPage.putClientProperty(
                                "FlatLaf.style",
                                "arc:20");

                recentPage.setPreferredSize(new Dimension(0, 0));

                cards.add(recentPage, "RECENT");

                starred starredPage = new starred(this);

                starredPage.putClientProperty(
                                "FlatLaf.style",
                                "arc:20");

                starredPage.setPreferredSize(new Dimension(0, 0));

                cards.add(starredPage, "STARRED");

                settings settingsPage = new settings(this);

                settingsPage.putClientProperty(
                                "FlatLaf.style",
                                "arc:20");

                settingsPage.setPreferredSize(new Dimension(0, 0));

                cards.add(settingsPage, "SETTINGS");

                viewerPage = new PdfViewer();

                viewerPage.putClientProperty(
                                "FlatLaf.style",
                                "arc:20");

                viewerPage.setPreferredSize(new Dimension(0, 0));

                cards.add(viewerPage, "VIEWER");

                PdfDropHandler.install(cards, f -> {
                        try {
                                showPdf(f);
                        } catch (IOException ex) {
                                JOptionPane.showMessageDialog(this, "Could not open file: " + ex.getMessage(),
                                                "Error", JOptionPane.ERROR_MESSAGE);
                        }
                });

                tools toolsPage = new tools(viewerPage,
                                () -> cardLayout.show(cards, "VIEWER"));

                toolsPage.putClientProperty(
                                "FlatLaf.style",
                                "arc:20");

                toolsPage.setPreferredSize(new Dimension(0, 0));

                cards.add(toolsPage, "TOOLS");

                setVisible(true);
        }

        /**
         * Adds {@code comp} to {@code container} (a GridBagLayout container)
         * at the given grid position with the given sizing/fill/anchor
         * behavior. Centralizes the otherwise-repetitive
         * {@code GridBagConstraints} setup used throughout the constructor
         * for every sidebar element.
         *
         * @param insets per-component margins, or {@code null} to use a
         *               default 5px margin on all sides
         */
        private void addComponent(
                        Container container,
                        Component comp,
                        int x, int y,
                        int width, int height,
                        double weightx, double weighty,
                        int fill,
                        Insets insets,
                        int anchor) {
                GridBagConstraints gbc = new GridBagConstraints();

                gbc.gridx = x;
                gbc.gridy = y;
                gbc.gridwidth = width;
                gbc.gridheight = height;

                gbc.weightx = weightx;
                gbc.weighty = weighty;

                gbc.fill = fill;
                gbc.insets = (insets != null) ? insets : new Insets(5, 5, 5, 5);
                gbc.anchor = anchor;

                container.add(comp, gbc);
        }

        /**
         * Loads a sidebar icon from the app's bundled resources (on the
         * classpath, inside the jar) and scales it to a fixed 28x28 icon
         * size.
         *
         * Uses {@code getClass().getResource(path)} rather than a plain
         * filesystem path — this reads the image out of the jar itself, so
         * it works identically regardless of the app's current working
         * directory (IntelliJ dev run, portable jar, or any OS's installer
         * layout), unlike a relative file path which depends on exactly
         * where the app happens to be launched from. The resources folder
         * is marked as a classpath resources root, so its contents (not
         * the folder itself) land at the classpath root — hence paths here
         * look like {@code "/home.png"}, not {@code "/resources/home.png"}.
         *
         * @param path classpath-relative resource path, e.g. "/home.png"
         * @return the scaled icon, or an empty icon if the resource couldn't be found
         */
        private ImageIcon loadIcon(String path) {

                int width = 28;
                int height = 28;

                java.net.URL url = getClass().getResource(path);

                if (url == null) {
                        System.err.println("Missing resource: " + path);
                        return new ImageIcon();
                }

                ImageIcon icon = new ImageIcon(url);

                Image scaled = icon.getImage().getScaledInstance(
                                width,
                                height,
                                Image.SCALE_SMOOTH);

                return new ImageIcon(scaled);
        }

        /**
         * Shows/hides every sidebar button's text label based on
         * {@link #collapsed} — collapsed shows icons only, expanded shows
         * icon + label. Called mid-animation by {@link #animateSidebar} so
         * the text disappears/reappears at the right point in the
         * collapse/expand transition rather than abruptly at the start.
         */
        private void updateSidebarText() {

                String text = collapsed ? "" : "Menu";
                menuButton.setText(text);

                text = collapsed ? "" : "Home";
                homeButton.setText(text);

                text = collapsed ? "" : "Recent";
                recentButton.setText(text);

                text = collapsed ? "" : "Starred";
                starredButton.setText(text);

                text = collapsed ? "" : "Tools";
                toolsButton.setText(text);

                text = collapsed ? "" : "Setting";
                settingButton.setText(text);

                text = collapsed ? "" : "Exit";
                exitButton.setText(text);
        }

        /**
         * Handles every sidebar navigation button plus Exit. Navigation
         * cases just switch {@link #cardLayout} to the matching card name.
         * Exit re-reads the "confirmExit" preference directly from disk
         * (rather than asking the live Settings screen) so the check still
         * works correctly even if the user never opened Settings this
         * session.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
                String command = e.getActionCommand();
                switch (command) {
                        case "Home":
                                cardLayout.show(cards, "HOME");
                                break;
                        case "Recent":
                                cardLayout.show(cards, "RECENT");
                                break;
                        case "Starred":
                                cardLayout.show(cards, "STARRED");
                                break;
                        case "Tools":
                                cardLayout.show(cards, "TOOLS");
                                break;
                        case "Settings":
                                cardLayout.show(cards, "SETTINGS");
                                break;
                        case "Exit":

                                boolean confirmExit = AppSettings.confirmExit();
                                if (confirmExit) {

                                        int choice = JOptionPane.showConfirmDialog(
                                                        this,
                                                        "Do you want to close Readora?",
                                                        "Exit",
                                                        JOptionPane.YES_NO_OPTION);

                                        if (choice == JOptionPane.YES_OPTION) {
                                                shutdownAndExit();
                                        }

                                } else {
                                        shutdownAndExit();
                                }

                                break;
                }
        }

        /**
         * Re-skins every already-visible component after the user picks a
         * new theme mid-session. {@code applyTheme(...)} alone only
         * installs the new LookAndFeel for components created afterward;
         * {@code updateComponentTreeUI} is what actually walks the existing
         * component tree and applies it retroactively. The sidebar's custom
         * "brighter than panel background" color is recomputed and
         * reapplied afterward since that's a manually-set color, not
         * something the LookAndFeel controls automatically. Called by
         * {@code settings.java} after {@code ThemeManager.applyTheme(...)}.
         */
        public void refreshTheme() {

                SwingUtilities.updateComponentTreeUI(this);

                
                Color brighter = Ui.raised();

                leftPanel.setBackground(brighter);

                menuButton.setBackground(brighter);
                homeButton.setBackground(brighter);
                recentButton.setBackground(brighter);
                starredButton.setBackground(brighter);
                toolsButton.setBackground(brighter);
                settingButton.setBackground(brighter);
                exitButton.setBackground(brighter);

                repaint();
                revalidate();
        }

        /**
         * Smoothly animates {@link #sidebarWeight} from its current value
         * to {@code target} (0.05 collapsed / 0.12 expanded), ticking every
         * 15ms via a Swing {@link Timer} and re-laying-out the window each
         * tick via {@link #updateLayoutAnimated()}. Cancels any
         * already-running animation first, so rapidly toggling the menu
         * button doesn't stack multiple timers fighting each other.
         *
         * @param target the sidebar weight to animate toward
         */
        private void animateSidebar(double target) {

                if (sidebarTimer != null &&
                                sidebarTimer.isRunning()) {
                        sidebarTimer.stop();
                }

                sidebarTimer = new Timer(15, null);

                sidebarTimer.addActionListener(e -> {

                        double speed = 0.009;

                        if (sidebarWeight < target) {
                                sidebarWeight += speed;

                                if (sidebarWeight >= target) {
                                        sidebarWeight = target;
                                        sidebarTimer.stop();
                                        updateSidebarText();
                                        leftPanel.setMinimumSize(
                                                        new Dimension(100, 0));
                                }
                        } else {
                                sidebarWeight -= speed;

                                if (sidebarWeight <= target) {
                                        sidebarWeight = target;
                                        sidebarTimer.stop();
                                        leftPanel.setMinimumSize(
                                                        new Dimension(80, 0));
                                }
                        }

                        updateLayoutAnimated();
                });

                sidebarTimer.start();
        }

        /**
         * Applies the current {@link #sidebarWeight} to the GridBagLayout
         * constraints of {@link #leftPanel} and {@link #rightPanel} (giving
         * the remainder to the right panel) and forces a re-layout. Called
         * on every tick of the sidebar collapse/expand animation.
         */
        private void updateLayoutAnimated() {

                GridBagLayout layout = (GridBagLayout) root.getLayout();

                GridBagConstraints left = layout.getConstraints(leftPanel);

                GridBagConstraints right = layout.getConstraints(rightPanel);

                left.weightx = sidebarWeight;
                right.weightx = 1.0 - sidebarWeight;

                layout.setConstraints(leftPanel, left);
                layout.setConstraints(rightPanel, right);

                root.revalidate();
                root.repaint();
        }

        /**
         * JVM entry point. Reads the saved theme name (if any) from
         * {@link AppPaths#settingsFile()} and applies it via
         * {@link ThemeManager#applyTheme(String)} <b>before</b>
         * constructing the main window — a FlatLaf theme must be installed
         * prior to component creation to take effect on them automatically.
         * Defaults to "Gruvbox Dark Hard" if no settings file exists yet
         * (first run) or the file doesn't specify a theme, so the app never
         * starts out unstyled.
         *
         * The frame itself is built on the Event Dispatch Thread via
         * {@code SwingUtilities.invokeLater}, as required for any Swing UI
         * construction.
         */
        public static void main(String[] args) {

                String theme = AppSettings.theme();
                ThemeManager.applyTheme(theme);

                SwingUtilities.invokeLater(Main::new);
        }

        /**
         * Switches to the PDF viewer card and opens {@code file} in it,
         * clearing any password that was cached for a previously-open
         * document first. Called by every screen that lets the user pick a
         * file to open (Home's "Open PDF" quick action, Recent, Starred).
         *
         * @param file the PDF to open
         */
        public void showPdf(File file) throws IOException {
                cardLayout.show(cards, "VIEWER");
                viewerPage.resetPdfPassword();
                viewerPage.openPdf(file);
        }

        /** Home's "Open PDF" action: choose a file, then show it in the viewer. */
        private void openPdfDialog() {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                        return;
                }
                try {
                        showPdf(chooser.getSelectedFile());
                } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this, "Could not open file: " + ex.getMessage(),
                                        "Error", JOptionPane.ERROR_MESSAGE);
                }
        }

        private void shutdownAndExit() {
                if (viewerPage != null) {
                        viewerPage.shutdown();
                }
                System.exit(0);
        }
}
