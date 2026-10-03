package viewer;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Lets a component accept PDF files dragged in from the file manager. */
public final class PdfDropHandler extends TransferHandler {

    private final Consumer<File> onPdf;

    private PdfDropHandler(Consumer<File> onPdf) { this.onPdf = onPdf; }

    public static void install(JComponent c, Consumer<File> onPdf) {
        c.setTransferHandler(new PdfDropHandler(onPdf));
    }

    @Override public boolean canImport(TransferSupport s) {
        return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override @SuppressWarnings("unchecked")
    public boolean importData(TransferSupport s) {
        if (!canImport(s)) return false;
        try {
            Transferable t = s.getTransferable();
            List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".pdf")) {
                    SwingUtilities.invokeLater(() -> onPdf.accept(f));
                    return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }
}
