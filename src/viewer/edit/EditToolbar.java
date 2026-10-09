package viewer.edit;

import rightpanel.AppFonts;
import ui.AppColorChooser;
import ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The editing toolbar: tool palette, colours, stroke and font controls, undo/redo, and the save actions.
 * It mirrors the selected item (choose a text box and the font controls show its font) and applies changes
 * made here to it.
 */
public final class EditToolbar extends Ui.Card {

    /** What the surrounding screen does for the toolbar's buttons. */
    public interface Actions {
        void addPage();
        void saveCopy();
        void save();
        void done();
    }

    private final EditController ctl;
    private final EditModel model;
    private final EditProps props;

    private final Map<EditController.Tool, JToggleButton> toolButtons = new EnumMap<>(EditController.Tool.class);
    private final JButton colorBtn = Ui.button("Color");
    private final JButton fillBtn = Ui.button("Fill");
    private final JCheckBox fillOn = new JCheckBox("Fill");
    private final JSpinner width = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 40.0, 0.5));
    private final JComboBox<String> family = new JComboBox<>(new String[]{"Helvetica", "Times", "Courier"});
    private final JSpinner size = new JSpinner(new SpinnerNumberModel(14, 6, 200, 1));
    private final JToggleButton bold = new JToggleButton(Ui.icon("bold", 16));
    private final JToggleButton italic = new JToggleButton(Ui.icon("italic", 16));
    private final JButton undo = Ui.iconButton("undo-2", "Undo (Ctrl+Z)");
    private final JButton redo = Ui.iconButton("redo-2", "Redo (Ctrl+Y)");
    private final JLabel status = new JLabel(" ");
    private boolean syncing;

    public EditToolbar(EditController ctl, EditModel model, EditProps props, Actions actions) {
        super(new BorderLayout());
        this.ctl = ctl;
        this.model = model;
        this.props = props;
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 6));
        rows.setOpaque(false);
        rows.add(row1());
        rows.add(row2(actions));
        add(rows, BorderLayout.CENTER);

        model.addListener(this::sync);
        ctl.onToolChanged(t -> { if (toolButtons.get(t) != null) toolButtons.get(t).setSelected(true); });
        sync();
    }

    // ------------------------------------------------------------------

    private JPanel flow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        return p;
    }

    private JPanel row1() {
        JPanel p = flow();
        ButtonGroup group = new ButtonGroup();
        tool(p, group, EditController.Tool.SELECT, "mouse-pointer-2", "Select and move  (V)");
        tool(p, group, EditController.Tool.TEXT, "type", "Text box  (T) \u2013 click or drag on the page");
        tool(p, group, EditController.Tool.IMAGE, "image", "Picture  (I)");
        tool(p, group, EditController.Tool.RECT, "square", "Rectangle  (R)");
        tool(p, group, EditController.Tool.ELLIPSE, "circle", "Ellipse  (O)");
        tool(p, group, EditController.Tool.LINE, "slash", "Line  (L)");
        tool(p, group, EditController.Tool.HIGHLIGHT, "highlighter", "Highlighter  (H)");
        tool(p, group, EditController.Tool.PEN, "pen-line", "Freehand pen  (P)");
        tool(p, group, EditController.Tool.REDACT, "eraser", "Redact  (X) \u2013 permanently removes what is underneath when you save");
        toolButtons.get(EditController.Tool.SELECT).setSelected(true);
        p.add(Box.createHorizontalStrut(10));

        colorBtn.setIcon(swatch(this::currentColor));
        colorBtn.setToolTipText("Colour of text, lines and drawings");
        colorBtn.addActionListener(e -> pickColor());
        p.add(colorBtn);

        fillOn.setOpaque(false);
        fillOn.setFont(AppFonts.body(Font.PLAIN, 14));
        fillOn.setToolTipText("Fill rectangles and ellipses");
        fillOn.addActionListener(e -> {
            if (syncing) return;
            props.fill = fillOn.isSelected() ? (props.fill != null ? props.fill : new Color(0xFFF59D)) : null;
            ctl.applyToSelected(it -> { if (it instanceof EditItem.Shape s && (s.kind == EditItem.Shape.Kind.RECT || s.kind == EditItem.Shape.Kind.ELLIPSE)) s.fill = props.fill; });
            sync();
        });
        p.add(fillOn);
        fillBtn.setText("");
        fillBtn.setIcon(swatch(() -> props.fill));
        fillBtn.setPreferredSize(new Dimension(Ui.CONTROL_H, Ui.CONTROL_H));
        fillBtn.setToolTipText("Fill colour");
        fillBtn.addActionListener(e -> {
            Color c = AppColorChooser.pick(this, "Fill colour", props.fill != null ? props.fill : new Color(0xFFF59D));
            if (c == null) return;
            props.fill = c;
            ctl.applyToSelected(it -> { if (it instanceof EditItem.Shape s && (s.kind == EditItem.Shape.Kind.RECT || s.kind == EditItem.Shape.Kind.ELLIPSE)) s.fill = c; });
            sync();
        });
        p.add(fillBtn);

        p.add(new JLabel("Width"));
        width.setPreferredSize(new Dimension(70, Ui.CONTROL_H));
        width.setToolTipText("Line width in points");
        width.addChangeListener(e -> {
            if (syncing) return;
            props.width = ((Number) width.getValue()).floatValue();
            ctl.applyToSelected(it -> {
                if (it instanceof EditItem.Shape s && s.kind != EditItem.Shape.Kind.HIGHLIGHT && s.kind != EditItem.Shape.Kind.REDACT) s.width = props.width;
                else if (it instanceof EditItem.Ink i) i.width = props.width;
            });
        });
        p.add(width);
        p.add(Box.createHorizontalStrut(10));
        undo.addActionListener(e -> model.undo());
        redo.addActionListener(e -> model.redo());
        p.add(undo);
        p.add(redo);
        return p;
    }

    private JPanel row2(Actions a) {
        JPanel p = flow();
        family.setPreferredSize(new Dimension(130, Ui.CONTROL_H));
        family.setToolTipText("Font");
        family.addActionListener(e -> {
            if (syncing) return;
            props.family = (String) family.getSelectedItem();
            ctl.applyToSelected(it -> { if (it instanceof EditItem.Text t) t.family = props.family; });
        });
        p.add(family);
        size.setPreferredSize(new Dimension(70, Ui.CONTROL_H));
        size.setToolTipText("Font size in points");
        size.addChangeListener(e -> {
            if (syncing) return;
            props.size = ((Number) size.getValue()).floatValue();
            ctl.applyToSelected(it -> { if (it instanceof EditItem.Text t) t.size = props.size; });
        });
        p.add(size);
        for (JToggleButton b : new JToggleButton[]{bold, italic}) {
            b.setFocusPainted(false);
            b.putClientProperty("FlatLaf.style", "arc:" + Ui.CONTROL_ARC);
            b.setPreferredSize(new Dimension(Ui.CONTROL_H, Ui.CONTROL_H));
            p.add(b);
        }
        bold.setToolTipText("Bold");
        italic.setToolTipText("Italic");
        bold.addActionListener(e -> {
            if (syncing) return;
            props.bold = bold.isSelected();
            ctl.applyToSelected(it -> { if (it instanceof EditItem.Text t) t.bold = props.bold; });
        });
        italic.addActionListener(e -> {
            if (syncing) return;
            props.italic = italic.isSelected();
            ctl.applyToSelected(it -> { if (it instanceof EditItem.Text t) t.italic = props.italic; });
        });
        p.add(Box.createHorizontalStrut(14));

        JButton addPage = Ui.button("Add page");
        addPage.setIcon(Ui.icon("file-plus", 16));
        addPage.setIconTextGap(6);
        addPage.setToolTipText("Insert a blank page after the current one");
        addPage.addActionListener(e -> a.addPage());
        p.add(addPage);
        JButton copy = Ui.button("Save a copy\u2026");
        copy.addActionListener(e -> a.saveCopy());
        p.add(copy);
        JButton save = Ui.primaryButton("Save");
        save.setToolTipText("Write the edits into this PDF  (Ctrl+S)");
        save.addActionListener(e -> a.save());
        p.add(save);
        JButton done = Ui.button("Done");
        done.setToolTipText("Leave edit mode");
        done.addActionListener(e -> a.done());
        p.add(done);

        status.setFont(AppFonts.body(Font.BOLD, 13));
        p.add(Box.createHorizontalStrut(6));
        p.add(status);
        return p;
    }

    private void tool(JPanel p, ButtonGroup g, EditController.Tool t, String icon, String tip) {
        JToggleButton b = new JToggleButton(Ui.icon(icon, 18));
        b.setToolTipText(tip);
        b.setFocusPainted(false);
        b.putClientProperty("FlatLaf.style", "arc:" + Ui.CONTROL_ARC);
        b.setPreferredSize(new Dimension(Ui.CONTROL_H, Ui.CONTROL_H));
        b.addActionListener(e -> ctl.setTool(t));
        g.add(b);
        toolButtons.put(t, b);
        p.add(b);
    }

    // ------------------------------------------------------------------
    // Colours
    // ------------------------------------------------------------------

    /** Text colour when a text box is selected or the text tool is active, else the line colour. */
    private Color currentColor() {
        EditItem sel = model.selected();
        boolean text = sel != null ? sel instanceof EditItem.Text : ctl.tool() == EditController.Tool.TEXT;
        return text ? props.textColor : props.color;
    }

    private void pickColor() {
        Color c = AppColorChooser.pick(this, "Colour", currentColor());
        if (c == null) return;
        EditItem sel = model.selected();
        boolean text = sel != null ? sel instanceof EditItem.Text : ctl.tool() == EditController.Tool.TEXT;
        if (text) props.textColor = c; else props.color = c;
        ctl.applyToSelected(it -> {
            if (it instanceof EditItem.Text t) t.color = c;
            else if (it instanceof EditItem.Shape s && s.kind != EditItem.Shape.Kind.HIGHLIGHT && s.kind != EditItem.Shape.Kind.REDACT) s.stroke = c;
            else if (it instanceof EditItem.Ink i) i.color = c;
        });
        sync();
    }

    private static Icon swatch(Supplier<Color> color) {
        return new Icon() {
            @Override public int getIconWidth() { return 18; }
            @Override public int getIconHeight() { return 18; }
            @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color col = color.get();
                    g.setColor(col != null ? col : Color.WHITE);
                    g.fillRoundRect(x, y, 18, 18, 6, 6);
                    g.setColor(Ui.line());
                    g.drawRoundRect(x, y, 17, 17, 6, 6);
                    if (col == null) {                                  // "no fill": a red slash
                        g.setColor(new Color(0xE0334F));
                        g.setStroke(new BasicStroke(1.6f));
                        g.drawLine(x + 3, y + 15, x + 15, y + 3);
                    }
                } finally {
                    g.dispose();
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // Mirror the model / selection
    // ------------------------------------------------------------------

    private void sync() {
        syncing = true;
        try {
            undo.setEnabled(model.canUndo());
            redo.setEnabled(model.canRedo());
            EditItem sel = model.selected();
            if (sel instanceof EditItem.Text t) {
                family.setSelectedItem(t.family);
                size.setValue((int) t.size);
                bold.setSelected(t.bold);
                italic.setSelected(t.italic);
                props.textColor = t.color;
            } else if (sel instanceof EditItem.Shape s) {
                if (s.kind != EditItem.Shape.Kind.HIGHLIGHT && s.kind != EditItem.Shape.Kind.REDACT) {
                    if (s.stroke != null) props.color = s.stroke;
                    width.setValue((double) s.width);
                }
                if (s.kind == EditItem.Shape.Kind.RECT || s.kind == EditItem.Shape.Kind.ELLIPSE) props.fill = s.fill;
            } else if (sel instanceof EditItem.Ink i) {
                props.color = i.color;
                width.setValue((double) i.width);
            } else {
                family.setSelectedItem(props.family);
                size.setValue((int) props.size);
                bold.setSelected(props.bold);
                italic.setSelected(props.italic);
                width.setValue((double) props.width);
            }
            fillOn.setSelected(props.fill != null);
            colorBtn.repaint();
            fillBtn.repaint();
            status.setText(model.isDirty() ? "\u25CF Unsaved changes" : " ");
            status.setForeground(Ui.accent());
        } finally {
            syncing = false;
        }
    }
}
