package viewer.ops;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.PDFText2HTML;
import org.apache.pdfbox.tools.PDFText2Markdown;
import rightpanel.ProgressDialog;
import viewer.PdfSession;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Conversions out of PDF: pages to PNG/JPG, text (TXT/HTML/Markdown), embedded images. */
public final class ExportOps extends Ops {

    public ExportOps(PdfSession session, Component parent) { super(session, parent); }

    public void pdfToPng() { exportImages("png"); }
    public void pdfToJpg() { exportImages("jpg"); }

    // ------------------------------------------------------------------
    // Pages -> images
    // ------------------------------------------------------------------

    private void exportImages(String format) {
        if (!requireDocument()) return;
        File folder = chooseFolder("Choose output folder");
        if (folder == null) return;
        int[] range = askPageRange();
        if (range == null) return;
        int dpi = askDpi();
        if (dpi < 0) return;

        final int start = range[0], end = range[1];
        final String name = baseName(session.file());
        ProgressDialog progress = ProgressDialog.show(parent, "Exporting pages...", false);
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override protected Integer doInBackground() throws Exception {
                try (PDDocument doc = session.openCopy()) {
                    PDFRenderer renderer = new PDFRenderer(doc);
                    int total = end - start + 1, done = 0;
                    for (int i = start - 1; i < end; i++) {
                        BufferedImage img = renderer.renderImageWithDPI(i, dpi);
                        String file = doc.getNumberOfPages() == 1 ? name + "." + format
                                : name + "_page_" + (i + 1) + "." + format;
                        if (!ImageIO.write(img, format, new File(folder, file))) {
                            throw new IOException("No encoder available for " + format);
                        }
                        setProgress((int) (++done * 100.0 / total));
                    }
                    return total;
                }
            }

            @Override protected void done() {
                progress.dispose();
                try {
                    info(get() + " page(s) exported to\n" + folder.getAbsolutePath(), "Export completed");
                } catch (Exception ex) {
                    error("Export failed", ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        };
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) progress.setProgress((Integer) evt.getNewValue());
        });
        worker.execute();
    }

    private int askDpi() {
        JTextField f = new JTextField("200");
        if (JOptionPane.showConfirmDialog(parent, f, "Enter DPI (50-600)", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return -1;
        try {
            int dpi = Integer.parseInt(f.getText().trim());
            if (dpi < 50 || dpi > 600) throw new NumberFormatException();
            return dpi;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parent, "DPI must be a number between 50 and 600.");
            return -1;
        }
    }

    private int[] askPageRange() {
        JTextField s = new JTextField("1"), e = new JTextField(String.valueOf(session.pageCount()));
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.add(new JLabel("Start page"));
        p.add(s);
        p.add(new JLabel("End page"));
        p.add(e);
        if (JOptionPane.showConfirmDialog(parent, p, "Page range", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return null;
        try {
            int a = Integer.parseInt(s.getText().trim()), b = Integer.parseInt(e.getText().trim());
            if (a < 1 || b > session.pageCount() || a > b) throw new NumberFormatException();
            return new int[]{a, b};
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(parent, "Enter a valid page range (1-" + session.pageCount() + ").");
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    public void exportText() {
        if (!requireDocument()) return;
        JComboBox<String> format = new JComboBox<>(new String[]{"TXT", "HTML", "Markdown"});
        JTextField s = new JTextField("1"), e = new JTextField(String.valueOf(session.pageCount()));
        JPanel p = new JPanel(new GridLayout(0, 2, 10, 10));
        p.add(new JLabel("Format:"));
        p.add(format);
        p.add(new JLabel("Start page:"));
        p.add(s);
        p.add(new JLabel("End page:"));
        p.add(e);
        if (JOptionPane.showConfirmDialog(parent, p, "Export text", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        int start, end;
        try {
            start = Integer.parseInt(s.getText().trim());
            end = Integer.parseInt(e.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(parent, "Enter valid page numbers.");
            return;
        }
        if (start < 1 || end > session.pageCount() || start > end) {
            JOptionPane.showMessageDialog(parent, "Invalid page range.");
            return;
        }
        String kind = (String) format.getSelectedItem();
        String ext = switch (kind) { case "HTML" -> "html"; case "Markdown" -> "md"; default -> "txt"; };
        File out = chooseSave("Save " + kind + " file", baseName(session.file()) + "." + ext, kind + " files", ext);
        if (out == null) return;

        final int a = start, b = end;
        background("Exporting text...", () -> {
            try (PDDocument doc = session.openCopy()) {
                PDFTextStripper st = switch (kind) {
                    case "HTML" -> new PDFText2HTML();
                    case "Markdown" -> new PDFText2Markdown();
                    default -> new PDFTextStripper();
                };
                st.setStartPage(a);
                st.setEndPage(b);
                st.setAddMoreFormatting(true);
                // UTF-8 always, so non-Latin text survives on every platform.
                Files.writeString(out.toPath(), st.getText(doc), StandardCharsets.UTF_8);
            }
            return out;
        }, f -> info("Saved " + f.getName(), "Export text"), "Export failed");
    }

    // ------------------------------------------------------------------
    // Embedded images
    // ------------------------------------------------------------------

    public void extractImages() {
        if (!requireDocument()) return;
        File folder = chooseFolder("Choose folder for extracted images");
        if (folder == null) return;
        background("Extracting images...", () -> {
            int count = 0;
            try (PDDocument doc = session.openCopy()) {
                for (PDPage page : doc.getPages()) {
                    count += extractFrom(page.getResources(), folder, count, 0);
                }
            }
            return count;
        }, n -> info(n == 0 ? "No images found in this PDF." : n + " image(s) extracted to\n" + folder.getAbsolutePath(),
                "Extract images"), "Image extraction failed");
    }

    /** Extracts images from a resource dictionary, descending into form XObjects (up to a depth limit). */
    private int extractFrom(PDResources res, File folder, int startIndex, int depth) throws IOException {
        if (res == null || depth > 4) return 0;
        int n = 0;
        for (COSName name : res.getXObjectNames()) {
            PDXObject obj = res.getXObject(name);
            if (obj instanceof PDImageXObject img) {
                String ext = img.getSuffix() == null ? "png" : img.getSuffix();
                BufferedImage bi = img.getImage();
                File out = new File(folder, "Image_" + (startIndex + n) + "." + ext);
                if (!ImageIO.write(bi, ext, out)) {            // e.g. jpx/jb2 have no encoder
                    out = new File(folder, "Image_" + (startIndex + n) + ".png");
                    ImageIO.write(bi, "png", out);
                }
                n++;
            } else if (obj instanceof PDFormXObject form) {
                n += extractFrom(form.getResources(), folder, startIndex + n, depth + 1);
            }
        }
        return n;
    }
}
