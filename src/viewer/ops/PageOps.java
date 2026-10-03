package viewer.ops;

import dialogs.PageOrganizerDialog;
import org.apache.pdfbox.pdmodel.PDDocument;
import ui.Ui;
import viewer.PdfSession;
import viewer.PdfViewer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Page-level editing: the Page Organizer in its five modes, and splitting a PDF. */
public final class PageOps extends Ops {

    private final PdfViewer viewer;

    public PageOps(PdfSession session, PdfViewer viewer) {
        super(session, viewer);
        this.viewer = viewer;
    }

    // ------------------------------------------------------------------
    // Page Organizer (organize / reorder / rotate / delete / extract)
    // ------------------------------------------------------------------

    public void openOrganizer(PageOrganizerDialog.Mode mode) {
        if (!requireDocument()) return;
        Window w = SwingUtilities.getWindowAncestor(parent);
        Frame owner = w instanceof Frame f ? f : null;
        // The organizer gets its own copy of the document so its thumbnail
        // worker can never collide with the main renderer.
        try (PDDocument copy = session.openCopy()) {
            new PageOrganizerDialog(owner, copy, session.file(), viewer, mode).setVisible(true);
        } catch (IOException e) {
            error("Could not open the page organizer", e);
        }
    }

    // ------------------------------------------------------------------
    // Split
    // ------------------------------------------------------------------

    public void splitPdf() {
        if (!requireDocument()) return;
        final int total = session.pageCount();
        if (total < 2) {
            JOptionPane.showMessageDialog(parent, "This PDF has only one page, so there is nothing to split.");
            return;
        }
        JTextField points = new JTextField(String.valueOf(total / 2), 20);
        Ui.styleField(points);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.add(new JLabel("<html>Split <b>after</b> these pages (comma-separated).<br>"
                + "Example: <b>3, 7</b> makes pages 1-3, 4-7 and 8-" + total + ".</html>"), BorderLayout.NORTH);
        p.add(points, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(parent, p, "Split PDF", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        TreeSet<Integer> cuts = new TreeSet<>();
        try {
            for (String part : points.getText().split("[,;\\s]+")) {
                if (part.isBlank()) continue;
                int n = Integer.parseInt(part.trim());
                if (n < 1 || n >= total) throw new NumberFormatException();
                cuts.add(n);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parent, "Enter page numbers between 1 and " + (total - 1) + ".");
            return;
        }
        if (cuts.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Enter at least one split point.");
            return;
        }
        File folder = chooseFolder("Choose output folder");
        if (folder == null) return;

        final String name = baseName(session.file());
        background("Splitting PDF...", () -> {
            List<String> made = new ArrayList<>();
            try (PDDocument src = session.openCopy()) {
                int start = 1;
                List<Integer> ends = new ArrayList<>(cuts);
                ends.add(total);
                for (int end : ends) {
                    File out = new File(folder, name + "_" + start + "-" + end + ".pdf");
                    try (PDDocument part = new PDDocument()) {
                        for (int i = start - 1; i < end; i++) part.importPage(src.getPage(i));
                        part.save(out);
                    }
                    made.add(out.getName());
                    start = end + 1;
                }
            }
            return made;
        }, made -> info("Split successful!\n\nCreated files:\n" + String.join("\n", made), "Split PDF"),
                "Split failed");
    }
}
