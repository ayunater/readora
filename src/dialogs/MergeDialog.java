package dialogs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Modal confirmation dialog shown before merging PDFs, letting the user
 * review the exact order the selected files will be merged in before
 * committing. Used by {@code tools.java}'s "Merge PDF" action.
 *
 * <p>Note: despite the dialog title ("Check the order"), the list here
 * is read-only review, not drag-to-reorder — the files merge in
 * whatever order they were selected in the file chooser. If drag
 * reordering is ever wanted, it would need a {@code TransferHandler} /
 * {@code setDragEnabled(true)} wired up on {@link #list}.
 *
 * Call {@link #setVisible(boolean)} with {@code true} to show it (it's
 * modal, so this blocks until the user picks Merge or Cancel), then
 * check {@link #isApproved()} to see which one they picked.
 */
public class MergeDialog extends JDialog {

    private static final Log log = LogFactory.getLog(MergeDialog.class);

    /** True if the user clicked "Merge"; false if they cancelled or closed the dialog. */
    private boolean approved = false;

    /** Backing list model holding the PDFs in merge order. */
    DefaultListModel<File> model =
            new DefaultListModel<>();

    /** Displays {@link #model}, rendered via {@link PdfCellRenderer}. */
    JList<File> list =
            new JList<>(model);

    JButton mergeButton =
            new JButton("Merge");

    JButton cancelButton =
            new JButton("Cancel");

    /**
     * Builds the dialog pre-populated with the files to merge, in the
     * order given.
     *
     * @param owner parent frame to center the dialog over (may be
     *              {@code null})
     * @param pdfs  the PDF files to merge, in merge order
     */
    public MergeDialog(
            Frame owner,
            File[] pdfs
    ) {

        super(owner, "Check the order", true);

        setSize(650, 500);
        setLocationRelativeTo(owner);

        for (File pdf : pdfs)
            model.addElement(pdf);

        list.setCellRenderer(
                new PdfCellRenderer()
        );

        // Suppress the default blue selection highlight — this list is
        // for reviewing order, not for making a selection.
        list.setSelectionBackground(list.getBackground());
        list.setSelectionForeground(list.getForeground());

        JScrollPane scrollPane =
                new JScrollPane(list);

        JPanel bottom =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT
                        )
                );

        mergeButton.addActionListener(e -> {
            approved = true;
            dispose();
        });

        cancelButton.addActionListener(e -> {
            approved = false;
            dispose();
        });

        bottom.add(cancelButton);
        bottom.add(mergeButton);

        add(scrollPane, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Note: a second, redundant Cancel listener — harmless since
        // `approved` already defaults to false and is set false by the
        // listener above, but this one only disposes without touching
        // `approved` again.
        cancelButton.addActionListener(e ->
                dispose()
        );
    }

    /** @return true if the user confirmed the merge; false if they cancelled */
    public boolean isApproved() {
        return approved;
    }
}
