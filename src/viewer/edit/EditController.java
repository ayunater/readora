package viewer.edit;

import ui.AppFileChooser;
import ui.Dialogs;
import ui.Ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Turns mouse and keyboard input on the page view into edits: drawing new items with the current tool,
 * and selecting, moving, resizing and deleting existing ones. Every finished gesture is one undo step.
 */
public final class EditController extends MouseAdapter implements KeyListener {

    public enum Tool { SELECT, TEXT, IMAGE, RECT, ELLIPSE, LINE, HIGHLIGHT, PEN, REDACT }

    private enum Mode { NONE, MOVE, RESIZE, CREATE, DRAW }

    private final PageHost host;
    private final EditModel model;
    private final EditOverlay overlay;
    private final EditProps props;

    private Tool tool = Tool.SELECT;
    private boolean attached;
    private Mode mode = Mode.NONE;
    private int pressPage, handle;
    private Point2D.Double pressPt = new Point2D.Double(), lastPt = new Point2D.Double();
    private EditItem before;
    private EditItem.Ink liveInk;
    private Consumer<Tool> toolListener = t -> { };
    private Runnable saveAction = () -> { };

    public EditController(PageHost host, EditModel model, EditOverlay overlay, EditProps props) {
        this.host = host;
        this.model = model;
        this.overlay = overlay;
        this.props = props;
        model.addListener(() -> host.component().repaint());
    }

    public void onToolChanged(Consumer<Tool> l) { toolListener = l; }
    public void onSave(Runnable r) { saveAction = r; }
    public Tool tool() { return tool; }

    public void attach() {
        if (attached) return;
        attached = true;
        JComponent c = host.component();
        c.addMouseListener(this);
        c.addMouseMotionListener(this);
        c.addKeyListener(this);
        updateCursor(null);
    }

    public void detach() {
        if (!attached) return;
        attached = false;
        cancelGesture();
        JComponent c = host.component();
        c.removeMouseListener(this);
        c.removeMouseMotionListener(this);
        c.removeKeyListener(this);
        c.setCursor(Cursor.getDefaultCursor());
    }

    public void setTool(Tool t) {
        cancelGesture();
        tool = t;
        updateCursor(null);
        toolListener.accept(t);
    }

    // ------------------------------------------------------------------
    // Coordinates
    // ------------------------------------------------------------------

    private Point2D.Double toPage(int page, Point p) {
        Rectangle b = host.pageBounds(page);
        double k = host.pxPerPt();
        double x = (p.x - b.x) / k, y = (p.y - b.y) / k;
        return new Point2D.Double(Math.max(0, Math.min(host.pageWidthPt(page), x)), Math.max(0, Math.min(host.pageHeightPt(page), y)));
    }

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    @Override
    public void mousePressed(MouseEvent e) {
        host.component().requestFocusInWindow();
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        int page = host.pageAt(e.getPoint());
        Point2D.Double pt = toPage(page, e.getPoint());
        double k = host.pxPerPt();
        pressPage = page;
        pressPt = pt;
        lastPt = pt;

        switch (tool) {
            case SELECT: {
                EditItem sel = model.selected();
                if (sel != null && sel.page == page) {
                    int h = overlay.handleAt(sel, pt, k);
                    if (h >= 0) { mode = Mode.RESIZE; handle = h; before = sel.copy(); return; }
                }
                EditItem hit = overlay.hit(model.items(), page, pt, k);
                model.select(hit);
                if (hit != null) {
                    if (e.getClickCount() == 2 && hit instanceof EditItem.Text) { editText((EditItem.Text) hit); mode = Mode.NONE; return; }
                    mode = Mode.MOVE;
                    before = hit.copy();
                } else {
                    mode = Mode.NONE;
                }
                return;
            }
            case IMAGE:
                placePicture(page, pt);
                return;
            case PEN:
                liveInk = new EditItem.Ink();
                liveInk.page = page;
                liveInk.color = props.color;
                liveInk.width = props.width;
                liveInk.points.add(new Point2D.Double(pt.x, pt.y));
                liveInk.recomputeBounds();
                overlay.setLive(liveInk);
                mode = Mode.DRAW;
                host.component().repaint();
                return;
            default:
                model.select(null);
                mode = Mode.CREATE;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (mode == Mode.NONE) return;
        Point2D.Double pt = toPage(pressPage, e.getPoint());
        EditItem sel = model.selected();
        switch (mode) {
            case MOVE: {
                double dx = pt.x - lastPt.x, dy = pt.y - lastPt.y;
                sel.translate(dx, dy);
                lastPt = pt;
                model.touch();
                break;
            }
            case RESIZE: {
                Rectangle2D.Double nb = EditOverlay.resized(before.bounds, handle, pt, 4);
                sel.setBounds(nb);
                if (sel instanceof EditItem.Text) EditFonts.fitHeight((EditItem.Text) sel);
                model.touch();
                break;
            }
            case CREATE:
                updateLive(pt);
                host.component().repaint();
                break;
            case DRAW: {
                Point2D.Double last = liveInk.points.get(liveInk.points.size() - 1);
                if (last.distance(pt) >= 0.6) {
                    liveInk.points.add(new Point2D.Double(pt.x, pt.y));
                    liveInk.recomputeBounds();
                    host.component().repaint();
                }
                break;
            }
            default:
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        Mode m = mode;
        mode = Mode.NONE;
        if (m == Mode.NONE) return;
        Point2D.Double pt = toPage(pressPage, e.getPoint());
        EditItem sel = model.selected();
        switch (m) {
            case MOVE:
            case RESIZE:
                if (sel != null && before != null && !sel.bounds.equals(before.bounds)) model.changed(sel, before);
                before = null;
                break;
            case DRAW:
                overlay.setLive(null);
                liveInk.recomputeBounds();
                model.add(liveInk);
                liveInk = null;
                break;
            case CREATE:
                finishCreate(pt);
                break;
            default:
        }
        host.component().repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) { updateCursor(e.getPoint()); }

    private void updateCursor(Point p) {
        JComponent c = host.component();
        if (tool != Tool.SELECT) {
            c.setCursor(Cursor.getPredefinedCursor(tool == Tool.TEXT ? Cursor.TEXT_CURSOR : Cursor.CROSSHAIR_CURSOR));
            return;
        }
        if (p == null) { c.setCursor(Cursor.getDefaultCursor()); return; }
        int page = host.pageAt(p);
        Point2D.Double pt = toPage(page, p);
        EditItem sel = model.selected();
        if (sel != null && sel.page == page) {
            int h = overlay.handleAt(sel, pt, host.pxPerPt());
            if (h >= 0) { c.setCursor(Cursor.getPredefinedCursor(EditOverlay.cursorFor(h))); return; }
        }
        boolean over = overlay.hit(model.items(), page, pt, host.pxPerPt()) != null;
        c.setCursor(Cursor.getPredefinedCursor(over ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    // ------------------------------------------------------------------
    // Creating items
    // ------------------------------------------------------------------

    private Rectangle2D.Double dragBox(Point2D.Double pt) {
        return new Rectangle2D.Double(Math.min(pressPt.x, pt.x), Math.min(pressPt.y, pt.y),
                Math.abs(pt.x - pressPt.x), Math.abs(pt.y - pressPt.y));
    }

    private void updateLive(Point2D.Double pt) {
        Rectangle2D.Double box = dragBox(pt);
        if (tool == Tool.TEXT) {
            overlay.setBand(pressPage, box);
            return;
        }
        EditItem.Shape s = newShape(box, pt);
        overlay.setLive(s);
    }

    private EditItem.Shape newShape(Rectangle2D.Double box, Point2D.Double pt) {
        EditItem.Shape s = new EditItem.Shape();
        s.page = pressPage;
        s.bounds.setRect(box);
        s.width = props.width;
        switch (tool) {
            case ELLIPSE: s.kind = EditItem.Shape.Kind.ELLIPSE; s.stroke = props.color; s.fill = props.fill; break;
            case LINE:
                s.kind = EditItem.Shape.Kind.LINE; s.stroke = props.color;
                s.rising = (pt.x - pressPt.x) * (pt.y - pressPt.y) < 0;
                break;
            case HIGHLIGHT:
                s.kind = EditItem.Shape.Kind.HIGHLIGHT; s.fill = EditProps.HIGHLIGHT; s.stroke = null;
                break;
            case REDACT:
                s.kind = EditItem.Shape.Kind.REDACT; s.fill = Color.BLACK; s.stroke = null;
                break;
            default: s.kind = EditItem.Shape.Kind.RECT; s.stroke = props.color; s.fill = props.fill;
        }
        return s;
    }

    private void finishCreate(Point2D.Double pt) {
        overlay.setLive(null);
        overlay.setBand(-1, null);
        Rectangle2D.Double box = dragBox(pt);
        if (tool == Tool.TEXT) {
            double w = box.width >= 24 ? box.width : Math.min(200, host.pageWidthPt(pressPage) - pressPt.x);
            EditItem.Text t = new EditItem.Text();
            t.page = pressPage;
            t.family = props.family; t.bold = props.bold; t.italic = props.italic;
            t.size = props.size; t.color = props.textColor;
            t.text = "";
            t.bounds.setRect(box.width >= 24 ? box.x : pressPt.x, box.width >= 24 ? box.y : pressPt.y, Math.max(40, w), 0);
            String text = askText(t.text);
            if (text != null && !text.isBlank()) {
                t.text = text;
                EditFonts.fitHeight(t);
                model.add(t);
                setTool(Tool.SELECT);
            }
            return;
        }
        boolean big = box.width >= 3 || box.height >= 3;
        if (tool == Tool.LINE ? (box.width + box.height) >= 4 : (box.width >= 4 && box.height >= 4)) {
            model.add(newShape(box, pt));
            if (tool != Tool.HIGHLIGHT && tool != Tool.REDACT) setTool(Tool.SELECT);
        } else if (!big) {
            host.component().repaint();                       // a click without a drag draws nothing
        }
    }

    private void placePicture(int page, Point2D.Double pt) {
        AppFileChooser fc = new AppFileChooser();
        fc.setDialogTitle("Choose a picture");
        fc.setFileFilter(new FileNameExtensionFilter("Pictures (png, jpg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(host.component()) != JFileChooser.APPROVE_OPTION) { setTool(Tool.SELECT); return; }
        File f = fc.getSelectedFile();
        BufferedImage bi;
        try {
            bi = ImageIO.read(f);
        } catch (IOException ex) {
            bi = null;
        }
        if (bi == null) {
            Dialogs.showMessageDialog(host.component(), "That file isn't a picture Readora can read.\nUse PNG, JPEG, GIF or BMP.",
                    "Picture", JOptionPane.WARNING_MESSAGE);
            setTool(Tool.SELECT);
            return;
        }
        double pw = host.pageWidthPt(page), ph = host.pageHeightPt(page);
        double w = Math.min(bi.getWidth() * 0.75, Math.min(240, pw - 20));
        double h = w * bi.getHeight() / bi.getWidth();
        if (h > ph - 20) { h = ph - 20; w = h * bi.getWidth() / bi.getHeight(); }
        EditItem.Pic p = new EditItem.Pic();
        p.page = page;
        p.file = f;
        p.preview = bi;
        p.bounds.setRect(Math.max(0, Math.min(pw - w, pt.x - w / 2)), Math.max(0, Math.min(ph - h, pt.y - h / 2)), w, h);
        model.add(p);
        setTool(Tool.SELECT);
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    private String askText(String initial) {
        JTextArea area = new JTextArea(initial, 7, 38);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(rightpanel.AppFonts.body(Font.PLAIN, 14));
        area.setMargin(new Insets(8, 10, 8, 10));
        JScrollPane sp = new JScrollPane(area);
        Ui.styleScroll(sp);
        SwingUtilities.invokeLater(() -> { area.requestFocusInWindow(); area.selectAll(); });
        int r = Dialogs.showConfirmDialog(host.component(), sp, "Text", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? area.getText() : null;
    }

    /** Opens the text dialog for an existing box and records the change. */
    public void editText(EditItem.Text t) {
        String text = askText(t.text);
        if (text == null || text.equals(t.text)) return;
        EditItem before = t.copy();
        t.text = text;
        EditFonts.fitHeight(t);
        model.changed(t, before);
    }

    // ------------------------------------------------------------------
    // Changes to the selection (from the toolbar and the keyboard)
    // ------------------------------------------------------------------

    /** Applies {@code change} to the selected item as one undo step. */
    public void applyToSelected(Consumer<EditItem> change) {
        EditItem sel = model.selected();
        if (sel == null) return;
        EditItem snapshot = sel.copy();
        change.accept(sel);
        if (sel instanceof EditItem.Text) EditFonts.fitHeight((EditItem.Text) sel);
        model.changed(sel, snapshot);
    }

    public void deleteSelected() {
        EditItem sel = model.selected();
        if (sel != null) model.remove(sel);
    }

    public void duplicateSelected() {
        EditItem sel = model.selected();
        if (sel == null) return;
        EditItem c = sel.copy();
        c.translate(10, 10);
        model.add(c);
    }

    private void cancelGesture() {
        mode = Mode.NONE;
        liveInk = null;
        before = null;
        overlay.setLive(null);
        overlay.setBand(-1, null);
        host.component().repaint();
    }

    // ------------------------------------------------------------------
    // Keyboard
    // ------------------------------------------------------------------

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ctrl = e.isControlDown() || e.isMetaDown();
        EditItem sel = model.selected();
        if (ctrl) {
            switch (code) {
                case KeyEvent.VK_Z: if (e.isShiftDown()) model.redo(); else model.undo(); e.consume(); return;
                case KeyEvent.VK_Y: model.redo(); e.consume(); return;
                case KeyEvent.VK_S: saveAction.run(); e.consume(); return;
                case KeyEvent.VK_D: duplicateSelected(); e.consume(); return;
                default: return;
            }
        }
        switch (code) {
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_BACK_SPACE:
                if (sel != null) { deleteSelected(); e.consume(); }
                return;
            case KeyEvent.VK_ESCAPE:
                if (mode != Mode.NONE) cancelGesture(); else model.select(null);
                e.consume();
                return;
            case KeyEvent.VK_LEFT: case KeyEvent.VK_RIGHT: case KeyEvent.VK_UP: case KeyEvent.VK_DOWN:
                if (sel == null) return;                           // nothing selected: let the page scroll
                double d = e.isShiftDown() ? 10 : 1;
                double dx = code == KeyEvent.VK_LEFT ? -d : code == KeyEvent.VK_RIGHT ? d : 0;
                double dy = code == KeyEvent.VK_UP ? -d : code == KeyEvent.VK_DOWN ? d : 0;
                applyToSelected(it -> it.translate(dx, dy));
                e.consume();
                return;
            case KeyEvent.VK_V: setTool(Tool.SELECT); return;
            case KeyEvent.VK_T: setTool(Tool.TEXT); return;
            case KeyEvent.VK_I: setTool(Tool.IMAGE); return;
            case KeyEvent.VK_R: setTool(Tool.RECT); return;
            case KeyEvent.VK_O: setTool(Tool.ELLIPSE); return;
            case KeyEvent.VK_L: setTool(Tool.LINE); return;
            case KeyEvent.VK_H: setTool(Tool.HIGHLIGHT); return;
            case KeyEvent.VK_P: setTool(Tool.PEN); return;
            case KeyEvent.VK_X: setTool(Tool.REDACT); return;
            default:
        }
    }

    @Override public void keyTyped(KeyEvent e) { }
    @Override public void keyReleased(KeyEvent e) { }
}
