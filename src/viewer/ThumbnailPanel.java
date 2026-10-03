package viewer;

import rightpanel.AppFonts;
import ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Left-hand strip of page thumbnails. Backed by a {@link JList} so only
 * the rows in view are painted, and thumbnails are rendered on demand by
 * the {@link RenderService} (never all at once). Selecting a row asks the
 * document view to scroll there; the document view moves the selection as
 * you scroll.
 */
public final class ThumbnailPanel extends Ui.Card {

    private static final int THUMB_W = 124;

    private final RenderService render;
    private final DefaultListModel<Integer> model = new DefaultListModel<>();
    private final JList<Integer> list = new JList<>(model);
    private final JScrollPane scroll = new JScrollPane(list);
    private PageGeometry geo = PageGeometry.empty();
    private IntConsumer onSelect = p -> { };
    private boolean programmatic;

    public ThumbnailPanel(RenderService render) {
        super(new BorderLayout());
        this.render = render;
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(THUMB_W + 52, 0));

        list.setCellRenderer(new Cell());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setFixedCellWidth(THUMB_W + 26);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !programmatic && list.getSelectedIndex() >= 0) {
                onSelect.accept(list.getSelectedIndex());
            }
        });
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> updateWanted());
        scroll.getViewport().addChangeListener(e -> updateWanted());
        add(scroll, BorderLayout.CENTER);
    }

    public void onPageSelected(IntConsumer l) { onSelect = l; }

    public void setDocument(PageGeometry g) {
        this.geo = g;
        programmatic = true;
        model.clear();
        for (int i = 0; i < g.count(); i++) model.addElement(i);
        if (g.count() > 0) list.setSelectedIndex(0);
        programmatic = false;
        scroll.getVerticalScrollBar().setValue(0);
        updateWanted();
    }

    /** Highlights the thumbnail of the page now shown in the document view. */
    public void setCurrentPage(int page) {
        if (page < 0 || page >= model.size() || list.getSelectedIndex() == page) return;
        programmatic = true;
        list.setSelectedIndex(page);
        list.ensureIndexIsVisible(page);
        programmatic = false;
    }

    /** Called by the render service after a bitmap arrives. */
    public void repaintThumbs() { list.repaint(); }

    private void updateWanted() {
        if (model.isEmpty()) { render.setWantedThumbs(Set.of()); return; }
        int first = Math.max(0, list.getFirstVisibleIndex());
        int last = list.getLastVisibleIndex() < 0 ? Math.min(model.size() - 1, 8) : list.getLastVisibleIndex();
        Set<Integer> wanted = new LinkedHashSet<>();
        for (int i = Math.max(0, first - 2); i <= Math.min(model.size() - 1, last + 3); i++) wanted.add(i);
        render.setWantedThumbs(wanted);
        list.repaint();
    }

    private float deviceScale() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) return 1f;
        AffineTransform t = gc.getDefaultTransform();
        return (float) Math.max(1.0, t.getScaleX());
    }

    /** Paints one thumbnail row. */
    private final class Cell extends JComponent implements ListCellRenderer<Integer> {
        private int page;
        private boolean selected;
        private int imgH = 160;

        Cell() { setOpaque(false); }

        @Override
        public Component getListCellRendererComponent(JList<? extends Integer> l, Integer value, int index,
                                                      boolean isSelected, boolean hasFocus) {
            page = value;
            selected = isSelected;
            imgH = geo.count() > page ? Math.max(40, Math.round(THUMB_W * geo.height(page) / geo.width(page))) : 160;
            return this;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(THUMB_W + 26, imgH + 40); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth();
                if (selected) {
                    Color accent = UIManager.getColor("Component.accentColor");
                    if (accent == null) accent = new Color(75, 130, 220);
                    g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
                    g.fillRoundRect(2, 2, w - 4, getHeight() - 4, Ui.CONTROL_ARC, Ui.CONTROL_ARC);
                    g.setColor(accent);
                    g.setStroke(new BasicStroke(2f));
                    g.drawRoundRect(2, 2, w - 5, getHeight() - 5, Ui.CONTROL_ARC, Ui.CONTROL_ARC);
                }
                int x = (w - THUMB_W) / 2, y = 10;
                g.setColor(Color.WHITE);
                g.fillRect(x, y, THUMB_W, imgH);

                float scale = THUMB_W / geo.width(page) * deviceScale();
                BufferedImage img = render.thumb(page, scale);
                if (img == null) img = render.stale(page, true);
                if (img != null) {
                    g.drawImage(img, x, y, THUMB_W, imgH, null);
                } else if (render.failed(page, scale, true)) {
                    g.setColor(Color.GRAY);
                    g.drawString("!", x + THUMB_W / 2 - 2, y + imgH / 2);
                }
                g.setColor(new Color(0, 0, 0, 60));
                g.drawRect(x, y, THUMB_W - 1, imgH - 1);

                g.setColor(getForeground() != null ? getForeground() : Color.LIGHT_GRAY);
                Color fg = UIManager.getColor("Label.foreground");
                if (fg != null) g.setColor(fg);
                g.setFont(AppFonts.body(selected ? Font.BOLD : Font.PLAIN, 12));
                String label = String.valueOf(page + 1);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(label, (w - fm.stringWidth(label)) / 2, y + imgH + 18);
            } finally {
                g.dispose();
            }
        }
    }
}
