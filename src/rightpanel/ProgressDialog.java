package rightpanel;

import javax.swing.*;
import java.awt.*;

/**
 * A small modeless "please wait" dialog with a progress bar, shown while
 * a background {@link SwingWorker} task runs so long PDF operations
 * (merge, split, watermarking, page-by-page conversions, etc.) give
 * visible feedback instead of appearing frozen.
 *
 * Originally written inside {@code tools.java} for just the Merge and
 * IMG-to-PDF workers; pulled out here so {@link viewer.PdfViewer}'s own
 * longer-running operations (split, extract, rotate, watermark, export,
 * search, ...) can show the same feedback internally instead of
 * appearing to hang while they process every page of a large document.
 *
 * The caller is responsible for calling {@link #dispose()} once their
 * background task's {@code done()} callback runs.
 */
public class ProgressDialog {

    private final JDialog dialog;
    private final JProgressBar bar;

    private ProgressDialog(JDialog dialog, JProgressBar bar) {
        this.dialog = dialog;
        this.bar = bar;
    }

    /**
     * Builds and shows the dialog.
     *
     * @param parent any component inside the window the dialog should
     *               be centered over and owned by
     * @param message text shown above the progress bar
     * @param indeterminate true for tasks with no known completion
     *                      percentage (e.g. merge, or an operation whose
     *                      page count isn't known up front), false for
     *                      tasks that report progress via {@link
     *                      #setProgress(int)}
     */
    public static ProgressDialog show(Component parent, String message, boolean indeterminate) {

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setIndeterminate(indeterminate);
        bar.setStringPainted(!indeterminate);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        panel.add(new JLabel(message), BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);

        JDialog dialog = new JDialog(
                (Frame) SwingUtilities.getWindowAncestor(parent),
                "Please wait",
                false
        );

        dialog.getContentPane().add(panel);
        dialog.setSize(300, 110);
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setVisible(true);

        return new ProgressDialog(dialog, bar);
    }

    /** Updates the bar for determinate tasks. Must be called on the EDT. */
    public void setProgress(int percent) {
        bar.setValue(percent);
    }

    /** Dismisses the dialog once the background task is done. Must be called on the EDT. */
    public void dispose() {
        dialog.dispose();
    }
}
