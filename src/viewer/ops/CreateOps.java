package viewer.ops;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import ui.AppFileChooser;
import ui.Dialogs;
import ui.Ui;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/** Creating a new, blank PDF to build up in the editor. */
public final class CreateOps {

    private CreateOps() { }

    private record Size(String label, PDRectangle rect) {
        @Override public String toString() { return label; }
    }

    private static final Size[] SIZES = {
            new Size("A4  (210 \u00D7 297 mm)", PDRectangle.A4),
            new Size("Letter  (8.5 \u00D7 11 in)", PDRectangle.LETTER),
            new Size("Legal  (8.5 \u00D7 14 in)", PDRectangle.LEGAL),
            new Size("A3  (297 \u00D7 420 mm)", PDRectangle.A3),
            new Size("A5  (148 \u00D7 210 mm)", PDRectangle.A5)};

    /**
     * Asks for page size, orientation, page count and a save location, then writes the blank PDF.
     *
     * @return the new file, or null if cancelled or it could not be written
     */
    public static File createBlankPdf(Component parent) {
        JComboBox<Size> size = new JComboBox<>(SIZES);
        JComboBox<String> orientation = new JComboBox<>(new String[]{"Portrait", "Landscape"});
        JSpinner pages = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
        Ui.styleCombo(size);
        Ui.styleCombo(orientation);
        pages.setPreferredSize(new Dimension(120, Ui.CONTROL_H));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 0, 5, 14);
        c.anchor = GridBagConstraints.WEST;
        String[] labels = {"Page size", "Orientation", "Number of pages"};
        JComponent[] fields = {size, orientation, pages};
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0; c.gridy = i; c.fill = GridBagConstraints.NONE; c.weightx = 0;
            form.add(new JLabel(labels[i]), c);
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(5, 0, 5, 0);
            form.add(fields[i], c);
            c.insets = new Insets(5, 0, 5, 14);
        }
        if (Dialogs.showConfirmDialog(parent, form, "New PDF", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return null;

        AppFileChooser chooser = new AppFileChooser();
        chooser.setDialogTitle("Save the new PDF as");
        chooser.setSelectedFile(new File("Untitled.pdf"));
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files", "pdf"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File out = chooser.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".pdf")) out = new File(out.getParentFile(), out.getName() + ".pdf");
        if (out.exists() && Dialogs.showConfirmDialog(parent, out.getName() + " already exists. Replace it?",
                "Confirm overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return null;

        PDRectangle base = ((Size) size.getSelectedItem()).rect();
        PDRectangle rect = "Landscape".equals(orientation.getSelectedItem())
                ? new PDRectangle(base.getHeight(), base.getWidth()) : base;
        try (PDDocument doc = new PDDocument()) {
            int n = ((Number) pages.getValue()).intValue();
            for (int i = 0; i < n; i++) doc.addPage(new PDPage(rect));
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(out.getName().replaceFirst("[.][^.]+$", ""));
            info.setCreator("Readora");
            doc.save(out);
            return out;
        } catch (IOException e) {
            Dialogs.showMessageDialog(parent, "Could not create the PDF: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}
