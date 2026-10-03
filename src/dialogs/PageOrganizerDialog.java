package dialogs;

import ui.Dialogs;
import ui.AppFileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import rightpanel.AppFonts;
import rightpanel.ProgressDialog;
import viewer.PdfViewer;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An interactive, visual modal studio for organizing pages of a PDF.
 *
 * <p>The dialog is <b>mode-driven</b>: it accepts a {@link Mode} enum at
 * construction time that adapts its header, title, subtitle, pre-selection,
 * and toolbar highlights to match the caller's intent — so "Rotate Pages",
 * "Remove Pages", "Reorder Pages", and "Organize Pages" each feel purpose-
 * built even though they share one class.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li>{@link Mode#ORGANIZE}  — all features enabled, neutral header</li>
 *   <li>{@link Mode#REORDER}   — toolbar highlights Move section, all pages pre-selected</li>
 *   <li>{@link Mode#ROTATE}    — toolbar highlights Rotate section, all pages pre-selected</li>
 *   <li>{@link Mode#DELETE}    — toolbar highlights Delete section, all pages pre-selected</li>
 *   <li>{@link Mode#EXTRACT}   — toolbar highlights Extract section, all pages pre-selected</li>
 * </ul>
 */
public class PageOrganizerDialog extends JDialog {

    // =========================================================================
    // MODE ENUM
    // =========================================================================

    /**
     * Declares <em>why</em> the dialog was opened, so the UI can adapt its
     * header, toolbar highlights, and pre-selection state accordingly.
     */
    public enum Mode {

        /**
         * Full organizer — all features visible and equally prominent.
         * Used by the "Organize" toolbar button and "Organize Pages" in the Tools screen.
         */
        ORGANIZE("Page Organizer", "Reorder, rotate, delete & extract pages", "✦"),

        /**
         * Focus mode for page reordering.
         * Used by the "Reorder Pages" tool. Move arrows are highlighted.
         */
        REORDER("Reorder Pages", "Drag or use arrow buttons to rearrange page order", "⇄"),

        /**
         * Focus mode for page rotation.
         * Used by the "Rotate Pages" tool. Rotation buttons are highlighted.
         */
        ROTATE("Rotate Pages", "Select pages and rotate clockwise or counter-clockwise", "⟳"),

        /**
         * Focus mode for page deletion.
         * Used by the "Remove Pages" tool. Delete button is highlighted.
         */
        DELETE("Remove Pages", "Select pages to mark for removal, then Save & Apply", "🗑"),

        /**
         * Focus mode for extracting a subset of pages to a new PDF.
         * Used by the "Extract Pages" tool. Extract button is highlighted.
         */
        EXTRACT("Extract Pages", "Select pages to extract into a separate PDF file", "📥");

        final String title;
        final String subtitle;
        final String icon;

        Mode(String title, String subtitle, String icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }

    // =========================================================================
    // FIELDS
    // =========================================================================

    private final PDDocument originalDocument;
    private final File originalFile;
    private final PdfViewer viewer;
    private final Mode mode;

    /** Working list of page items tracking order, rotation, deletion, and selection. */
    private final List<PageItem> pageItems = new ArrayList<>();

    /** Panel holding all page cards, using WrapLayout for dynamic wrapping. */
    private JPanel gridPanel;
    private JScrollPane scrollPane;

    private JLabel statusLabel;

    // Toolbar button references (needed for mode-based highlighting)
    private JButton rotateLeftBtn;
    private JButton rotateRightBtn;
    private JButton deleteBtn;
    private JButton moveLeftBtn;
    private JButton moveRightBtn;
    private JButton extractSelectedBtn;

    // Toolbar group panels (needed for mode-based background highlight)
    private JPanel selectionGroup;
    private JPanel rotateGroup;
    private JPanel moveGroup;
    private JPanel deleteGroup;
    private JPanel extractGroup;

    private JSlider zoomSlider;

    /** Current card width in pixels. */
    private int cardWidth = 210;

    /** Drag-and-drop reordering state. */
    private PageCardPanel draggedCard = null;

    /** Background executor for thumbnail rendering — single-threaded to avoid PDFBox concurrent access. */
    private static final ExecutorService THUMB_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "readora-organizer-thumb-worker");
        t.setDaemon(true);
        return t;
    });

    // =========================================================================
    // PAGE ITEM MODEL
    // =========================================================================

    private static class PageItem {
        final int originalIndex;
        int rotationDelta = 0;
        boolean isDeleted = false;
        boolean isSelected = false;
        BufferedImage thumbnail = null;

        PageItem(int originalIndex) {
            this.originalIndex = originalIndex;
        }

        void rotateClockwise()        { rotationDelta = (rotationDelta + 90) % 360; }
        void rotateCounterClockwise() { rotationDelta = (rotationDelta + 270) % 360; }
        int  getNetRotation(int base) { return (base + rotationDelta) % 360; }
    }

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Full constructor; prefer calling via the convenience factory methods
     * in {@link viewer.PdfViewer} (e.g. {@code openPageOrganizer(Mode.ROTATE)}).
     *
     * @param owner    the owning {@link Frame}
     * @param document the currently-open {@link PDDocument}
     * @param file     the source file (may be {@code null})
     * @param viewer   the viewer panel that owns the document
     * @param mode     which operation brought the user here
     */
    public PageOrganizerDialog(Frame owner, PDDocument document, File file,
                               PdfViewer viewer, Mode mode) {
        super(owner, "Readora — " + mode.title, true);
        this.originalDocument = document;
        this.originalFile = file;
        this.viewer = viewer;
        this.mode = mode;

        Rectangle screenBounds = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(1060, Math.min(1360, (int) (screenBounds.width  * 0.88)));
        int h = Math.max(700,  Math.min(900,  (int) (screenBounds.height * 0.88)));

        setSize(w, h);
        setMinimumSize(new Dimension(960, 620));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        initData();
        initUI();
        applyModePreSelection();
        loadThumbnailsAsync();
        updateStatus();
    }

    /**
     * Backwards-compatible convenience constructor that defaults to {@link Mode#ORGANIZE}.
     * Existing call-sites that haven't been updated yet will keep working.
     */
    public PageOrganizerDialog(Frame owner, PDDocument document, File file, PdfViewer viewer) {
        this(owner, document, file, viewer, Mode.ORGANIZE);
    }

    // =========================================================================
    // DATA INIT
    // =========================================================================

    private void initData() {
        int total = originalDocument.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            pageItems.add(new PageItem(i));
        }
    }

    /**
     * Applies mode-specific pre-selection so the user lands in a "ready to act" state:
     * <ul>
     *   <li>ROTATE / DELETE / EXTRACT → all pages pre-selected (user just hits the action)</li>
     *   <li>REORDER / ORGANIZE → no pre-selection (user manually picks what to move)</li>
     * </ul>
     */
    private void applyModePreSelection() {
        if (mode == Mode.ROTATE || mode == Mode.DELETE || mode == Mode.EXTRACT) {
            for (PageItem item : pageItems) {
                item.isSelected = true;
            }
        }
    }

    // =========================================================================
    // UI CONSTRUCTION
    // =========================================================================

    private void initUI() {
        Color baseBg  = UIManager.getColor("Panel.background");
        Color sepColor = UIManager.getColor("Separator.foreground");
        if (sepColor == null) sepColor = new Color(180, 180, 180, 60);

        // -------------------------------------------------------------------
        // A. BRANDED HEADER  (changes per mode)
        // -------------------------------------------------------------------
        JPanel headerPanel = new JPanel(new BorderLayout(16, 0));
        headerPanel.setOpaque(true);
        if (baseBg != null) headerPanel.setBackground(baseBg);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, sepColor),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)));

        JPanel brandLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brandLeft.setOpaque(false);

        // App logo
        ImageIcon logoIcon = loadScaledIcon("/logo.png", 42, 42);
        brandLeft.add(logoIcon != null ? new JLabel(logoIcon) : makeTextLabel(mode.icon, Font.PLAIN, 28));

        // Mode icon badge
        JLabel modeIconLabel = new JLabel(mode.icon);
        modeIconLabel.setFont(rightpanel.AppFonts.body(Font.PLAIN, 22));
        modeIconLabel.setForeground(getAccentColor());
        brandLeft.add(modeIconLabel);

        // Title + subtitle block (vary per mode)
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(mode.title);
        titleLabel.setFont(AppFonts.title(Font.PLAIN, 28));

        String fileName = originalFile != null ? originalFile.getName() : "Document";
        JLabel subtitleLabel = new JLabel(
                mode.subtitle + "  •  " + fileName + "  •  " + originalDocument.getNumberOfPages() + " pages");
        subtitleLabel.setFont(AppFonts.body(Font.PLAIN, 12));
        subtitleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        titleBlock.add(titleLabel);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(subtitleLabel);
        brandLeft.add(titleBlock);

        headerPanel.add(brandLeft, BorderLayout.WEST);

        // Zoom slider (right side of header)
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerRight.setOpaque(false);

        JLabel zoomLabel = new JLabel("Zoom:");
        zoomLabel.setFont(AppFonts.body(Font.PLAIN, 12));
        zoomLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerRight.add(zoomLabel);

        zoomSlider = new JSlider(160, 280, cardWidth);
        zoomSlider.setPreferredSize(new Dimension(130, 26));
        zoomSlider.setOpaque(false);
        zoomSlider.setFocusable(false);
        zoomSlider.setToolTipText("Adjust card thumbnail size");
        zoomSlider.addChangeListener(e -> { cardWidth = zoomSlider.getValue(); rebuildCards(); });
        headerRight.add(zoomSlider);

        headerPanel.add(headerRight, BorderLayout.EAST);

        // -------------------------------------------------------------------
        // B. ACTION TOOLBAR  (groups highlighted per mode)
        // -------------------------------------------------------------------
        JPanel toolbarPanel = new JPanel(new BorderLayout(10, 0));
        toolbarPanel.setOpaque(true);
        if (baseBg != null) {
            Color barBg = new Color(
                    Math.max(0, baseBg.getRed() - 4),
                    Math.max(0, baseBg.getGreen() - 4),
                    Math.max(0, baseBg.getBlue() - 4));
            toolbarPanel.setBackground(barBg);
        }
        toolbarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, sepColor),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftActions.setOpaque(false);

        // --- Selection group ---
        selectionGroup = buildToolGroup();
        selectionGroup.add(createToolButton("Select All", e -> selectAll(true)));
        selectionGroup.add(createToolButton("Deselect",   e -> selectAll(false)));
        selectionGroup.add(createToolButton("Invert",     e -> invertSelection()));
        leftActions.add(selectionGroup);

        leftActions.add(makeSep());

        // --- Rotate group ---
        rotateGroup = buildToolGroup();
        rotateLeftBtn  = createToolButton("⟲ Rotate Left",  e -> rotateSelected(false));
        rotateRightBtn = createToolButton("⟳ Rotate Right", e -> rotateSelected(true));
        rotateGroup.add(rotateLeftBtn);
        rotateGroup.add(rotateRightBtn);
        leftActions.add(rotateGroup);

        leftActions.add(makeSep());

        // --- Move group ---
        moveGroup = buildToolGroup();
        moveLeftBtn  = createToolButton("◀ Move Left",  e -> moveSelected(-1));
        moveRightBtn = createToolButton("▶ Move Right", e -> moveSelected(1));
        moveGroup.add(moveLeftBtn);
        moveGroup.add(moveRightBtn);
        leftActions.add(moveGroup);

        leftActions.add(makeSep());

        // --- Delete group ---
        deleteGroup = buildToolGroup();
        deleteBtn = createToolButton("🗑 Delete / Restore", e -> toggleDeleteSelected());
        deleteGroup.add(deleteBtn);
        leftActions.add(deleteGroup);

        leftActions.add(makeSep());

        // --- Extract group ---
        extractGroup = buildToolGroup();
        extractSelectedBtn = createToolButton("📥 Extract Selected", e -> extractSelectedPages());
        extractGroup.add(extractSelectedBtn);
        leftActions.add(extractGroup);

        // Apply mode-specific toolbar highlights
        applyModeToolbarHighlights();

        // Status pill (right of toolbar)
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);

        JPanel statusPill = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        statusPill.putClientProperty("FlatLaf.style", "arc: 16");
        statusPill.setOpaque(true);
        Color accent = getAccentColor();
        statusPill.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));

        statusLabel = new JLabel("Loading…");
        statusLabel.setFont(AppFonts.body(Font.BOLD, 13));
        statusPill.add(statusLabel);
        rightActions.add(statusPill);

        toolbarPanel.add(leftActions,  BorderLayout.WEST);
        toolbarPanel.add(rightActions, BorderLayout.EAST);

        // Pack header + toolbar into north region
        JPanel northContainer = new JPanel(new BorderLayout());
        northContainer.add(headerPanel,  BorderLayout.NORTH);
        northContainer.add(toolbarPanel, BorderLayout.SOUTH);
        add(northContainer, BorderLayout.NORTH);

        // -------------------------------------------------------------------
        // C. CARD GRID
        // -------------------------------------------------------------------
        gridPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 20, 20));
        gridPanel.setOpaque(false);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));

        scrollPane = new JScrollPane(gridPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(28);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // -------------------------------------------------------------------
        // D. FOOTER BAR
        // -------------------------------------------------------------------
        JPanel bottomBar = new JPanel(new BorderLayout(16, 0));
        bottomBar.setOpaque(true);
        if (baseBg != null) bottomBar.setBackground(baseBg);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, sepColor),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)));

        JLabel tipLabel = new JLabel(getModeTip());
        tipLabel.setFont(AppFonts.body(Font.PLAIN, 12));
        tipLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomButtons.setOpaque(false);

        JButton resetBtn  = createActionButton("Reset All",  e -> resetAll());
        JButton cancelBtn = createActionButton("Cancel",     e -> dispose());
        JButton saveBtn   = createAccentActionButton(getSaveButtonLabel(), e -> saveAndApply());

        bottomButtons.add(resetBtn);
        bottomButtons.add(cancelBtn);
        bottomButtons.add(saveBtn);

        bottomBar.add(tipLabel,     BorderLayout.WEST);
        bottomBar.add(bottomButtons, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        rebuildCards();
    }

    // =========================================================================
    // MODE-ADAPTIVE HELPERS
    // =========================================================================

    /**
     * Applies a translucent accent highlight to the toolbar button group(s)
     * most relevant to the current mode, leaving all other groups unstyled.
     */
    private void applyModeToolbarHighlights() {
        switch (mode) {
            case ROTATE:
                highlightGroup(rotateGroup, true);
                break;
            case REORDER:
                highlightGroup(moveGroup, true);
                break;
            case DELETE:
                highlightGroup(deleteGroup, true);
                break;
            case EXTRACT:
                highlightGroup(extractGroup, true);
                break;
            case ORGANIZE:
            default:
                // No single group highlighted — all equal
                break;
        }
    }

    /**
     * Gives a toolbar group panel a subtle accent-tinted background and
     * a rounded border to draw the user's eye to it.
     */
    private void highlightGroup(JPanel group, boolean on) {
        if (!on) {
            group.setOpaque(false);
            group.setBorder(null);
            return;
        }
        Color accent = getAccentColor();
        group.setOpaque(true);
        group.setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 28));
        group.putClientProperty("FlatLaf.style", "arc: 14");
        group.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90), 1, true),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
    }

    /** Returns the contextual tip text shown in the footer based on the active mode. */
    private String getModeTip() {
        switch (mode) {
            case ROTATE:
                return "💡 All pages are pre-selected. Use ⟲ / ⟳ to rotate, then Save & Apply to write the new PDF.";
            case REORDER:
                return "💡 Drag cards or use ◀ ▶ buttons to reorder. Save & Apply writes the new page order.";
            case DELETE:
                return "💡 All pages are pre-selected. Deselect pages you want to keep, then click Delete / Restore, then Save & Apply.";
            case EXTRACT:
                return "💡 All pages are pre-selected. Deselect pages you don't need, then click Extract Selected.";
            case ORGANIZE:
            default:
                return "💡 Click cards to select. Drag or use ◀ ▶ to reorder. ⟲ ⟳ to rotate. 🗑 to delete. 📥 to extract.";
        }
    }

    /** Returns a primary-action button label that fits the mode. */
    private String getSaveButtonLabel() {
        switch (mode) {
            case ROTATE:  return "Save Rotated PDF";
            case REORDER: return "Save Reordered PDF";
            case DELETE:  return "Save & Remove Pages";
            case EXTRACT: return "Save & Apply";
            case ORGANIZE:
            default:      return "Save & Apply Changes";
        }
    }

    // =========================================================================
    // TOOLBAR GROUP / BUTTON FACTORIES
    // =========================================================================

    /** Creates a transparent flow panel that wraps a group of toolbar buttons. */
    private JPanel buildToolGroup() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        return p;
    }

    private JSeparator makeSep() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 24));
        return sep;
    }

    private JLabel makeTextLabel(String text, int style, int size) {
        JLabel l = new JLabel(text);
        l.setFont(rightpanel.AppFonts.body(style, size));
        return l;
    }

    private JButton createToolButton(String text, ActionListener listener) {
        JButton btn = new JButton(text);
        btn.putClientProperty("FlatLaf.style", "arc: 16");
        btn.setFont(AppFonts.body(Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(5, 11, 5, 11));
        btn.addActionListener(listener);
        return btn;
    }

    private JButton createActionButton(String text, ActionListener listener) {
        JButton btn = new JButton(text);
        btn.putClientProperty("FlatLaf.style", "arc: 20");
        btn.setFont(AppFonts.body(Font.PLAIN, 13));
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(110, 38));
        btn.addActionListener(listener);
        return btn;
    }

    private JButton createAccentActionButton(String text, ActionListener listener) {
        JButton btn = new JButton(text);
        btn.putClientProperty("FlatLaf.style", "arc: 20");
        btn.setFont(AppFonts.body(Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(195, 38));
        btn.setBackground(getAccentColor());
        btn.setForeground(Color.WHITE);
        btn.addActionListener(listener);
        return btn;
    }

    private Color getAccentColor() {
        Color accent = UIManager.getColor("Component.accentColor");
        return accent != null ? accent : ui.Ui.accent();
    }

    private ImageIcon loadScaledIcon(String resourcePath, int w, int h) {
        try {
            java.net.URL url = getClass().getResource(resourcePath);
            if (url != null) {
                Image img = new ImageIcon(url).getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                return new ImageIcon(img);
            }
        } catch (Exception ignored) { }
        return null;
    }

    // =========================================================================
    // THUMBNAIL LOADING
    // =========================================================================

    private void loadThumbnailsAsync() {
        THUMB_POOL.submit(() -> {
            try {
                PDFRenderer renderer = new PDFRenderer(originalDocument);
                float targetW = 280f;
                for (int i = 0; i < pageItems.size(); i++) {
                    final int idx = i;
                    try {
                        float pageW = originalDocument.getPage(idx).getMediaBox().getWidth();
                        float scale = targetW / Math.max(1f, pageW);
                        BufferedImage img = renderer.renderImage(idx, Math.max(0.35f, scale), ImageType.BGR);
                        SwingUtilities.invokeLater(() -> {
                            for (PageItem item : pageItems) {
                                if (item.originalIndex == idx) { item.thumbnail = img; break; }
                            }
                            gridPanel.repaint();
                        });
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        });
    }

    // =========================================================================
    // CARD GRID
    // =========================================================================

    private void rebuildCards() {
        gridPanel.removeAll();
        for (int i = 0; i < pageItems.size(); i++) {
            gridPanel.add(new PageCardPanel(pageItems.get(i), i + 1));
        }
        gridPanel.revalidate();
        gridPanel.repaint();
        updateStatus();
    }

    private void updateStatus() {
        int total    = pageItems.size();
        int selected = 0, deleted = 0;
        for (PageItem item : pageItems) {
            if (item.isSelected) selected++;
            if (item.isDeleted)  deleted++;
        }
        int active = total - deleted;
        statusLabel.setText(String.format("%d Active Pages  •  %d Selected%s",
                active, selected,
                deleted > 0 ? "  •  " + deleted + " Deleted" : ""));

        boolean hasSel = selected > 0;
        rotateLeftBtn.setEnabled(hasSel || total > 0);
        rotateRightBtn.setEnabled(hasSel || total > 0);
        deleteBtn.setEnabled(hasSel);
        moveLeftBtn.setEnabled(hasSel);
        moveRightBtn.setEnabled(hasSel);
        extractSelectedBtn.setEnabled(hasSel);
    }

    // =========================================================================
    // SELECTION ACTIONS
    // =========================================================================

    private void selectAll(boolean select) {
        for (PageItem item : pageItems) item.isSelected = select;
        rebuildCards();
    }

    private void invertSelection() {
        for (PageItem item : pageItems) item.isSelected = !item.isSelected;
        rebuildCards();
    }

    // =========================================================================
    // PAGE MANIPULATION ACTIONS
    // =========================================================================

    private void rotateSelected(boolean clockwise) {
        boolean anySelected = pageItems.stream().anyMatch(p -> p.isSelected);
        for (PageItem item : pageItems) {
            if (!anySelected || item.isSelected) {
                if (clockwise) item.rotateClockwise();
                else           item.rotateCounterClockwise();
            }
        }
        rebuildCards();
    }

    private void toggleDeleteSelected() {
        for (PageItem item : pageItems) {
            if (item.isSelected) item.isDeleted = !item.isDeleted;
        }
        rebuildCards();
    }

    private void moveSelected(int direction) {
        if (direction < 0) {
            for (int i = 1; i < pageItems.size(); i++) {
                if (pageItems.get(i).isSelected && !pageItems.get(i - 1).isSelected) {
                    PageItem tmp = pageItems.get(i);
                    pageItems.set(i, pageItems.get(i - 1));
                    pageItems.set(i - 1, tmp);
                }
            }
        } else {
            for (int i = pageItems.size() - 2; i >= 0; i--) {
                if (pageItems.get(i).isSelected && !pageItems.get(i + 1).isSelected) {
                    PageItem tmp = pageItems.get(i);
                    pageItems.set(i, pageItems.get(i + 1));
                    pageItems.set(i + 1, tmp);
                }
            }
        }
        rebuildCards();
    }

    private void resetAll() {
        pageItems.sort((a, b) -> Integer.compare(a.originalIndex, b.originalIndex));
        for (PageItem item : pageItems) {
            item.rotationDelta = 0;
            item.isDeleted     = false;
            item.isSelected    = false;
        }
        rebuildCards();
    }

    // =========================================================================
    // EXTRACT ACTION
    // =========================================================================

    private void extractSelectedPages() {
        List<PageItem> selected = new ArrayList<>();
        for (PageItem item : pageItems) {
            if (item.isSelected && !item.isDeleted) selected.add(item);
        }
        if (selected.isEmpty()) {
            Dialogs.showMessageDialog(this,
                    "Please select at least one active page to extract.",
                    "No Pages Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new AppFileChooser();
        chooser.setDialogTitle("Save Extracted Pages As");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Documents", "pdf"));
        String baseName = originalFile != null
                ? originalFile.getName().replaceFirst("[.][^.]+$", "") : "extracted";
        chooser.setSelectedFile(new File(baseName + "_extracted.pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File outFile = ensurePdfExtension(chooser.getSelectedFile());
        if (!outputAllowed(outFile)) return;
        ProgressDialog progress = ProgressDialog.show(this, "Extracting selected pages…", true);

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                try (PDDocument newDoc = new PDDocument()) {
                    for (PageItem item : selected) {
                        PDPage orig = originalDocument.getPage(item.originalIndex);
                        PDPage imp  = newDoc.importPage(orig);
                        imp.setRotation(item.getNetRotation(orig.getRotation()));
                    }
                    newDoc.save(outFile);
                }
                return null;
            }
            @Override protected void done() {
                progress.dispose();
                try {
                    get();
                    int opt = Dialogs.showConfirmDialog(PageOrganizerDialog.this,
                            "Extracted " + selected.size() + " pages successfully.\nOpen the extracted PDF now?",
                            "Extraction Complete", JOptionPane.YES_NO_OPTION);
                    if (opt == JOptionPane.YES_OPTION) { dispose(); viewer.openGeneratedPDF(outFile); }
                } catch (Exception ex) {
                    Dialogs.showMessageDialog(PageOrganizerDialog.this,
                            "Failed to extract pages: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // =========================================================================
    // SAVE & APPLY ACTION
    // =========================================================================

    private void saveAndApply() {
        List<PageItem> activePages = new ArrayList<>();
        for (PageItem item : pageItems) {
            if (!item.isDeleted) activePages.add(item);
        }
        if (activePages.isEmpty()) {
            Dialogs.showMessageDialog(this,
                    "Cannot save an empty PDF. Please restore at least one page.",
                    "No Pages Left", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new AppFileChooser();
        chooser.setDialogTitle("Save Organized PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Documents", "pdf"));
        String baseName = originalFile != null
                ? originalFile.getName().replaceFirst("[.][^.]+$", "") : "organized";
        chooser.setSelectedFile(new File(baseName + "_organized.pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File outFile = ensurePdfExtension(chooser.getSelectedFile());
        if (!outputAllowed(outFile)) return;
        ProgressDialog progress = ProgressDialog.show(this, "Applying changes and generating PDF…", true);

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                try (PDDocument newDoc = new PDDocument()) {
                    for (PageItem item : activePages) {
                        PDPage orig = originalDocument.getPage(item.originalIndex);
                        PDPage imp  = newDoc.importPage(orig);
                        imp.setRotation(item.getNetRotation(orig.getRotation()));
                    }
                    newDoc.save(outFile);
                }
                return null;
            }
            @Override protected void done() {
                progress.dispose();
                try {
                    get();
                    dispose();
                    viewer.openGeneratedPDF(outFile);
                } catch (Exception ex) {
                    Dialogs.showMessageDialog(PageOrganizerDialog.this,
                            "Failed to save PDF: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Refuses to write over the PDF being organized (the source is still being
     * read, so overwriting it can corrupt the result) and confirms before
     * replacing any other existing file.
     */
    private boolean outputAllowed(File out) {
        try {
            if (originalFile != null && out.getCanonicalFile().equals(originalFile.getCanonicalFile())) {
                Dialogs.showMessageDialog(this,
                        "Please choose a different file name.\nThe original PDF is never overwritten directly.",
                        "Choose another name", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        } catch (java.io.IOException ignored) { /* fall through to the overwrite check */ }
        if (out.exists()) {
            return Dialogs.showConfirmDialog(this, out.getName() + " already exists. Replace it?",
                    "Confirm overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    == JOptionPane.YES_OPTION;
        }
        return true;
    }

    private File ensurePdfExtension(File file) {
        return file.getName().toLowerCase().endsWith(".pdf")
                ? file : new File(file.getAbsolutePath() + ".pdf");
    }

    // =========================================================================
    // PAGE CARD COMPONENT
    // =========================================================================

    private class PageCardPanel extends JPanel {
        private final PageItem item;
        private final int displayPageNumber;
        private boolean isHovered = false;

        PageCardPanel(PageItem item, int displayPageNumber) {
            this.item = item;
            this.displayPageNumber = displayPageNumber;

            int cardH = (int) (cardWidth * 1.40);
            setPreferredSize(new Dimension(cardWidth, cardH));
            setLayout(new BorderLayout(0, 6));
            putClientProperty("FlatLaf.style", "arc: 18");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            updateAppearance();

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true;  updateAppearance(); repaint(); }
                @Override public void mouseExited (MouseEvent e) { isHovered = false; updateAppearance(); repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    item.isSelected = !item.isSelected;
                    updateAppearance(); updateStatus(); repaint();
                }
                @Override public void mousePressed (MouseEvent e) { draggedCard = PageCardPanel.this; }
                @Override public void mouseReleased(MouseEvent e) {
                    if (draggedCard != null && draggedCard != PageCardPanel.this) {
                        Point dp = SwingUtilities.convertPoint(PageCardPanel.this, e.getPoint(), gridPanel);
                        Component target = gridPanel.getComponentAt(dp);
                        if (target instanceof PageCardPanel && target != draggedCard) {
                            PageCardPanel dest = (PageCardPanel) target;
                            int srcIdx = pageItems.indexOf(draggedCard.item);
                            int dstIdx = pageItems.indexOf(dest.item);
                            if (srcIdx >= 0 && dstIdx >= 0 && srcIdx != dstIdx) {
                                PageItem moved = pageItems.remove(srcIdx);
                                pageItems.add(dstIdx, moved);
                                rebuildCards();
                            }
                        }
                    }
                    draggedCard = null;
                }
            };
            addMouseListener(mouseHandler);

            // Header
            JPanel header = new JPanel(new BorderLayout(6, 0));
            header.setOpaque(false);
            header.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));

            JCheckBox selectBox = new JCheckBox();
            selectBox.setSelected(item.isSelected);
            selectBox.setOpaque(false);
            selectBox.setFocusable(false);
            selectBox.addActionListener(e -> {
                item.isSelected = selectBox.isSelected();
                updateAppearance(); updateStatus(); repaint();
            });

            JPanel pageTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            pageTitlePanel.setOpaque(false);
            JLabel pageLabel = new JLabel("Page " + displayPageNumber);
            pageLabel.setFont(AppFonts.body(Font.BOLD, 13));
            pageTitlePanel.add(pageLabel);

            if (item.originalIndex != displayPageNumber - 1) {
                JLabel origLabel = new JLabel("(Orig #" + (item.originalIndex + 1) + ")");
                origLabel.setFont(AppFonts.body(Font.PLAIN, 10));
                origLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                pageTitlePanel.add(origLabel);
            }

            header.add(selectBox,      BorderLayout.WEST);
            header.add(pageTitlePanel, BorderLayout.CENTER);

            if (item.isDeleted) {
                JLabel badge = new JLabel("Deleted");
                badge.setFont(AppFonts.body(Font.BOLD, 11));
                badge.setForeground(new Color(220, 53, 69));
                header.add(badge, BorderLayout.EAST);
            } else if (item.rotationDelta != 0) {
                JLabel badge = new JLabel("⟳ " + item.rotationDelta + "°");
                badge.setFont(AppFonts.body(Font.BOLD, 11));
                badge.setForeground(getAccentColor());
                header.add(badge, BorderLayout.EAST);
            }

            add(header, BorderLayout.NORTH);
            add(new ThumbnailCanvas(item), BorderLayout.CENTER);

            // Card bottom mini-buttons
            JPanel cardActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 6));
            cardActions.setOpaque(false);

            JButton btnRotL = makeCardBtn("⟲", "Rotate CCW",  e -> { item.rotateCounterClockwise(); rebuildCards(); });
            JButton btnRotR = makeCardBtn("⟳", "Rotate CW",   e -> { item.rotateClockwise();        rebuildCards(); });
            JButton btnMvL  = makeCardBtn("◀", "Move Left",   e -> {
                int idx = pageItems.indexOf(item);
                if (idx > 0) { pageItems.set(idx, pageItems.get(idx - 1)); pageItems.set(idx - 1, item); rebuildCards(); }
            });
            JButton btnMvR  = makeCardBtn("▶", "Move Right",  e -> {
                int idx = pageItems.indexOf(item);
                if (idx < pageItems.size() - 1) { pageItems.set(idx, pageItems.get(idx + 1)); pageItems.set(idx + 1, item); rebuildCards(); }
            });
            JButton btnDel  = makeCardBtn(item.isDeleted ? "↩" : "🗑",
                    item.isDeleted ? "Restore Page" : "Delete Page", e -> { item.isDeleted = !item.isDeleted; rebuildCards(); });

            int ci = pageItems.indexOf(item);
            if (ci == 0)                     btnMvL.setEnabled(false);
            if (ci == pageItems.size() - 1)  btnMvR.setEnabled(false);

            // Mode hint: dim buttons that are off-focus for non-ORGANIZE modes
            if (mode == Mode.ROTATE) {
                btnMvL.setEnabled(false); btnMvR.setEnabled(false);
            } else if (mode == Mode.REORDER) {
                btnRotL.setEnabled(false); btnRotR.setEnabled(false);
                btnDel.setEnabled(false);
            } else if (mode == Mode.DELETE) {
                btnRotL.setEnabled(false); btnRotR.setEnabled(false);
                if (ci == 0) btnMvL.setEnabled(false);
                if (ci == pageItems.size() - 1) btnMvR.setEnabled(false);
            }

            cardActions.add(btnRotL);
            cardActions.add(btnRotR);
            cardActions.add(btnMvL);
            cardActions.add(btnMvR);
            cardActions.add(btnDel);
            add(cardActions, BorderLayout.SOUTH);
        }

        private JButton makeCardBtn(String text, String tooltip, ActionListener l) {
            JButton btn = new JButton(text);
            btn.setToolTipText(tooltip);
            btn.putClientProperty("FlatLaf.style", "arc: 12");
            btn.setFont(rightpanel.AppFonts.body(Font.PLAIN, 12));
            btn.setPreferredSize(new Dimension(30, 26));
            btn.setMargin(new Insets(1, 1, 1, 1));
            btn.setFocusable(false);
            btn.addActionListener(l);
            return btn;
        }

        private void updateAppearance() {
            Color accent = getAccentColor();
            Color baseBg = UIManager.getColor("Panel.background");
            if (baseBg == null) baseBg = new Color(245, 245, 245);

            Color borderCol;
            int   thickness;
            Color bg;

            if (item.isDeleted) {
                borderCol = new Color(220, 53, 69, 200);
                thickness = 2;
                bg = new Color(220, 53, 69, 24);
            } else if (item.isSelected) {
                borderCol = accent;
                thickness = 2;
                bg = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 28);
            } else {
                borderCol = isHovered
                        ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120)
                        : (UIManager.getColor("Component.borderColor") != null
                               ? UIManager.getColor("Component.borderColor")
                               : new Color(180, 180, 180, 90));
                thickness = isHovered ? 2 : 1;
                bg = isHovered
                        ? new Color(Math.min(255, baseBg.getRed()   + 10),
                                    Math.min(255, baseBg.getGreen() + 10),
                                    Math.min(255, baseBg.getBlue()  + 10))
                        : baseBg;
            }

            Border line    = BorderFactory.createLineBorder(borderCol, thickness, true);
            Border padding = BorderFactory.createEmptyBorder(2, 2, 2, 2);
            setBorder(BorderFactory.createCompoundBorder(line, padding));
            setBackground(bg);
        }
    }

    // =========================================================================
    // THUMBNAIL CANVAS
    // =========================================================================

    private static class ThumbnailCanvas extends JPanel {
        private final PageItem item;
        ThumbnailCanvas(PageItem item) { this.item = item; setOpaque(false); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth(), h = getHeight();

            if (item.thumbnail == null) {
                g2.setColor(new Color(150, 150, 150, 30));
                g2.fill(new RoundRectangle2D.Float(12, 12, w - 24, h - 24, 12, 12));
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.setFont(AppFonts.body(Font.ITALIC, 11));
                String txt = "Rendering…";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(txt, (w - fm.stringWidth(txt)) / 2, h / 2);
            } else {
                BufferedImage img = item.thumbnail;
                int cx = w / 2, cy = h / 2;

                float maxW = w - 20, maxH = h - 16;
                if (item.rotationDelta % 180 != 0) { maxW = h - 20; maxH = w - 16; }

                float scale = Math.min(maxW / img.getWidth(), maxH / img.getHeight());
                int drawW = (int)(img.getWidth() * scale), drawH = (int)(img.getHeight() * scale);

                Graphics2D pg = (Graphics2D) g2.create();
                if (item.rotationDelta != 0) pg.rotate(Math.toRadians(item.rotationDelta), cx, cy);

                int px = cx - drawW / 2, py = cy - drawH / 2;

                // Paper shadow + white background
                pg.setColor(new Color(0, 0, 0, 28));
                pg.fillRoundRect(px + 2, py + 2, drawW, drawH, 4, 4);
                pg.setColor(Color.WHITE);
                pg.fillRect(px, py, drawW, drawH);

                if (item.isDeleted)
                    pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));

                pg.drawImage(img, px, py, drawW, drawH, null);

                pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                pg.setColor(Color.GRAY);
                pg.drawRect(px, py, drawW, drawH);
                pg.dispose();
            }

            // Deleted banner
            if (item.isDeleted) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f));
                g2.setColor(new Color(220, 53, 69));
                int bh = 28;
                g2.fillRect(0, (h - bh) / 2, w, bh);
                g2.setColor(Color.WHITE);
                g2.setFont(AppFonts.body(Font.BOLD, 12));
                String dt = "MARKED FOR REMOVAL";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(dt, (w - fm.stringWidth(dt)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            }

            g2.dispose();
        }
    }

    // =========================================================================
    // WRAP LAYOUT
    // =========================================================================

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container t) { return layoutSize(t, true); }
        @Override public Dimension minimumLayoutSize (Container t) { return layoutSize(t, false); }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth == 0 && target.getParent() != null)
                    targetWidth = target.getParent().getWidth();
                if (targetWidth == 0) targetWidth = 1100;

                int hgap = getHgap(), vgap = getVgap();
                Insets ins = target.getInsets();
                int maxW   = Math.max(200, targetWidth - ins.left - ins.right - hgap * 2);

                Dimension dim = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowW + d.width > maxW && rowW > 0) {
                        dim.width   = Math.max(dim.width, rowW);
                        dim.height += rowH + vgap;
                        rowW = 0; rowH = 0;
                    }
                    if (rowW != 0) rowW += hgap;
                    rowW += d.width;
                    rowH = Math.max(rowH, d.height);
                }
                dim.width   = Math.max(dim.width, rowW);
                dim.height += rowH;
                dim.width  += ins.left + ins.right + hgap * 2;
                dim.height += ins.top  + ins.bottom + vgap * 2;
                return dim;
            }
        }
    }
}
