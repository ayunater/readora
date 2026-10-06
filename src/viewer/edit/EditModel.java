package viewer.edit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * The pending edits of one document: items in paint order, the selection, and undo/redo.
 * Nothing touches the PDF until {@link EditWriter} applies the items on save.
 */
public final class EditModel {

    private interface Cmd {
        void apply();
        void revert();
    }

    private final List<EditItem> items = new ArrayList<>();
    private final Deque<Cmd> undo = new ArrayDeque<>();
    private final Deque<Cmd> redo = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private EditItem selected;

    public List<EditItem> items() { return Collections.unmodifiableList(items); }

    public EditItem selected() { return selected; }

    public void select(EditItem it) {
        if (selected != it) {
            selected = it;
            fire();
        }
    }

    /** True if there is anything unsaved. */
    public boolean isDirty() { return !undo.isEmpty(); }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public void addListener(Runnable r) { listeners.add(r); }

    private void fire() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    // ------------------------------------------------------------------

    public void add(EditItem it) {
        Cmd c = new Cmd() {
            public void apply() { items.add(it); }
            public void revert() { items.remove(it); if (selected == it) selected = null; }
        };
        c.apply();
        push(c);
        selected = it;
        fire();
    }

    public void remove(EditItem it) {
        int index = items.indexOf(it);
        if (index < 0) return;
        Cmd c = new Cmd() {
            public void apply() { items.remove(it); if (selected == it) selected = null; }
            public void revert() { items.add(Math.min(index, items.size()), it); }
        };
        c.apply();
        push(c);
        fire();
    }

    /**
     * Records a change that has <i>already been applied</i> to {@code it}.
     *
     * @param before a {@link EditItem#copy() copy} taken before the change
     */
    public void changed(EditItem it, EditItem before) {
        EditItem after = it.copy();
        push(new Cmd() {
            public void apply() { it.copyFrom(after); }
            public void revert() { it.copyFrom(before); }
        });
        fire();
    }

    public void undo() {
        if (undo.isEmpty()) return;
        Cmd c = undo.pop();
        c.revert();
        redo.push(c);
        fire();
    }

    public void redo() {
        if (redo.isEmpty()) return;
        Cmd c = redo.pop();
        c.apply();
        undo.push(c);
        fire();
    }

    /** Forgets everything (after saving, or when switching documents). */
    public void clear() {
        items.clear();
        undo.clear();
        redo.clear();
        selected = null;
        fire();
    }

    /** Notifies listeners without recording history (live dragging). */
    public void touch() { fire(); }

    private void push(Cmd c) {
        undo.push(c);
        redo.clear();
    }
}
