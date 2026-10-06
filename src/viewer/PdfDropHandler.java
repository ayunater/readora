package viewer;

import ui.Dialogs;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lets PDF files dragged in from the file manager be dropped on Readora.
 *
 * <p>Swing delivers a drop to the deepest component that has a drop target, and tables, lists and text
 * fields have built-in ones that refuse files, so a drop over (say) the Recent table would be rejected even
 * if the window accepted it. {@link #installEverywhere} therefore gives those components a file-aware drop
 * target too, so a PDF can be dropped on any part of the window.</p>
 *
 * <p>If several files are dropped the first PDF is opened. Anything that is not a PDF gets a polite message
 * instead of silently doing nothing.</p>
 */
public final class PdfDropHandler extends TransferHandler {

    private final Consumer<File> onPdf;

    private PdfDropHandler(Consumer<File> onPdf) { this.onPdf = onPdf; }

    /** Accept drops on one component. */
    public static void install(JComponent c, Consumer<File> onPdf) {
        c.setTransferHandler(new PdfDropHandler(onPdf));
    }

    /**
     * Accept drops on {@code root} and on every descendant whose own handling would otherwise swallow them.
     */
    public static void installEverywhere(Container root, Consumer<File> onPdf) {
        if (root instanceof JComponent jc) install(jc, onPdf);
        if (!java.awt.GraphicsEnvironment.isHeadless()) walk(root, onPdf);    // DropTarget needs a display
    }

    private static void walk(Container c, Consumer<File> onPdf) {
        for (Component k : c.getComponents()) {
            if (k instanceof JComponent jc) {
                boolean hasOwnDrop = jc instanceof JTable || jc instanceof JList || jc instanceof JTree
                        || jc instanceof JTextComponent || jc.getTransferHandler() != null;
                if (hasOwnDrop && !(jc.getTransferHandler() instanceof PdfDropHandler)) {
                    jc.setDropTarget(new FileDropTarget(jc, onPdf));
                }
            }
            if (k instanceof Container cc) walk(cc, onPdf);
        }
    }

    // ------------------------------------------------------------------
    // Reading the dropped files
    // ------------------------------------------------------------------

    /** The dropped files, or an empty list if the transferable does not carry files. */
    @SuppressWarnings("unchecked")
    static List<File> filesIn(Transferable t) {
        try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of();
    }

    static File firstPdf(List<File> files) {
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".pdf")) return f;
        }
        return null;
    }

    /** Opens the first PDF among the files, or explains what Readora can open. */
    static boolean handle(Component where, List<File> files, Consumer<File> onPdf) {
        File pdf = firstPdf(files);
        SwingUtilities.invokeLater(() -> {
            if (pdf != null) {
                onPdf.accept(pdf);
            } else if (!files.isEmpty()) {
                Dialogs.showMessageDialog(where, "Readora opens PDF files.\n\"" + files.get(0).getName()
                        + "\" isn't a PDF.", "Can't open", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        return pdf != null;
    }

    // ------------------------------------------------------------------
    // Swing TransferHandler (components without a handler of their own)
    // ------------------------------------------------------------------

    @Override public boolean canImport(TransferSupport s) {
        return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override public boolean importData(TransferSupport s) {
        if (!canImport(s)) return false;
        return handle(s.getComponent(), filesIn(s.getTransferable()), onPdf);
    }

    // ------------------------------------------------------------------
    // Raw drop target (components whose own handler would refuse files)
    // ------------------------------------------------------------------

    private static final class FileDropTarget extends DropTarget {
        FileDropTarget(JComponent c, Consumer<File> onPdf) {
            super(c, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
                @Override public void dragEnter(DropTargetDragEvent e) { check(e); }
                @Override public void dragOver(DropTargetDragEvent e) { check(e); }

                private void check(DropTargetDragEvent e) {
                    if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) e.acceptDrag(DnDConstants.ACTION_COPY);
                    else e.rejectDrag();
                }

                @Override public void drop(DropTargetDropEvent e) {
                    if (!e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { e.rejectDrop(); return; }
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    boolean ok = handle(c, filesIn(e.getTransferable()), onPdf);
                    e.dropComplete(ok);
                }
            }, true);
        }
    }
}
