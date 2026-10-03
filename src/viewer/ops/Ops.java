package viewer.ops;

import rightpanel.ProgressDialog;
import viewer.PdfSession;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Base class for the operation groups (export, watermark, security, ...)
 * that used to live inside the 5,000-line viewer. Each subclass handles
 * one concern and shares these helpers for dialogs and background work.
 *
 * <p>Operations that take time run on a background thread against their
 * own {@linkplain PdfSession#openCopy() copy} of the document, so they
 * never touch the document the renderer is using.</p>
 */
public abstract class Ops {

    protected final PdfSession session;
    /** Dialog parent (the viewer panel). */
    protected final Component parent;

    protected Ops(PdfSession session, Component parent) {
        this.session = session;
        this.parent = parent;
    }

    /** Shows "Open a PDF first." and returns false when nothing is open. */
    protected boolean requireDocument() {
        if (!session.isOpen()) {
            JOptionPane.showMessageDialog(parent, "Open a PDF first.", "No PDF Open",
                    JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    protected void info(String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    protected void error(String message, Throwable t) {
        if (t != null) t.printStackTrace();
        String detail = t == null ? "" : ": " + rootMessage(t);
        JOptionPane.showMessageDialog(parent, message + detail, "Error", JOptionPane.ERROR_MESSAGE);
    }

    protected static String rootMessage(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }

    protected static String baseName(File f) {
        return f.getName().replaceFirst("[.][^.]+$", "");
    }

    /**
     * Runs {@code work} off the UI thread with a progress dialog, then
     * calls {@code done} (on the UI thread) with the result, or shows the
     * error if it failed.
     */
    protected <T> void background(String message, Callable<T> work, Consumer<T> done, String failure) {
        ProgressDialog progress = ProgressDialog.show(parent, message, true);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }

            @Override protected void done() {
                progress.dispose();
                try {
                    done.accept(get());
                } catch (Exception ex) {
                    error(failure, ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        }.execute();
    }

    /** Ask where to save a file; adds the extension if missing; confirms overwrite. Null if cancelled. */
    protected File chooseSave(String title, String suggestedName, String description, String extension) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new File(suggestedName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(description, extension));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith("." + extension)) {
            f = new File(f.getParentFile(), f.getName() + "." + extension);
        }
        return confirmOverwrite(f) ? f : null;
    }

    protected boolean confirmOverwrite(File f) {
        if (!f.exists()) return true;
        return JOptionPane.showConfirmDialog(parent, f.getName() + " already exists. Replace it?",
                "Confirm overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    protected File chooseFolder(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }
}
