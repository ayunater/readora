package viewer.ops;

import ui.Dialogs;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import ui.Ui;
import viewer.PdfSession;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Read-only document information plus metadata editing: info, page count, fonts, metadata. */
public final class InfoOps extends Ops {

    public InfoOps(PdfSession session, Component parent) { super(session, parent); }

    private static String safe(String s) { return s == null || s.isBlank() ? "Not Available" : s; }

    public void showMetadata() {
        if (!requireDocument()) return;
        PDDocumentInformation i = session.document().getDocumentInformation();
        info("""
                Title: %s
                Author: %s
                Subject: %s
                Creator: %s
                Producer: %s
                Keywords: %s
                Pages: %d
                """.formatted(safe(i.getTitle()), safe(i.getAuthor()), safe(i.getSubject()),
                safe(i.getCreator()), safe(i.getProducer()), safe(i.getKeywords()), session.pageCount()),
                "PDF Metadata");
    }

    public void showPdfInfo() {
        if (!requireDocument()) return;
        PDDocumentInformation i = session.document().getDocumentInformation();
        info("""
                File Name: %s
                File Size: %.2f MB
                Total Pages: %d
                Title: %s
                Author: %s
                Subject: %s
                Creator: %s
                Producer: %s
                Created: %s
                Modified: %s
                """.formatted(session.file().getName(), session.file().length() / (1024.0 * 1024.0),
                session.pageCount(), safe(i.getTitle()), safe(i.getAuthor()), safe(i.getSubject()),
                safe(i.getCreator()), safe(i.getProducer()),
                i.getCreationDate() != null ? i.getCreationDate().getTime() : "Unknown",
                i.getModificationDate() != null ? i.getModificationDate().getTime() : "Unknown"),
                "PDF Info");
    }

    public void showPageCount() {
        if (!requireDocument()) return;
        info("Total Pages: " + session.pageCount(), "Page Count");
    }

    public void listFonts() {
        if (!requireDocument()) return;
        background("Reading fonts...", () -> {
            Set<String> fonts = new LinkedHashSet<>();
            try (PDDocument doc = session.openCopy()) {
                for (PDPage page : doc.getPages()) {
                    PDResources res = page.getResources();
                    if (res == null) continue;
                    for (COSName name : res.getFontNames()) {
                        try {
                            PDFont f = res.getFont(name);
                            if (f != null) fonts.add(f.getName());
                        } catch (IOException ignored) { /* unreadable font entry: skip it */ }
                    }
                }
            }
            return fonts;
        }, fonts -> {
            JTextArea area = new JTextArea(fonts.isEmpty() ? "No fonts found." : String.join("\n", fonts), 20, 50);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setCaretPosition(0);
            JScrollPane sp = new JScrollPane(area);
            sp.setPreferredSize(new Dimension(500, 350));
            Dialogs.showMessageDialog(parent, sp, "Fonts Used", JOptionPane.INFORMATION_MESSAGE);
        }, "Could not read fonts");
    }

    /** Edits the standard metadata fields and writes them back safely. */
    public void editMetadata() {
        if (!requireDocument()) return;
        PDDocumentInformation i = session.document().getDocumentInformation();
        JTextField title = field(i.getTitle()), author = field(i.getAuthor()), subject = field(i.getSubject()),
                creator = field(i.getCreator()), producer = field(i.getProducer()), keywords = field(i.getKeywords());
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        addRow(panel, "Title:", title);
        addRow(panel, "Author:", author);
        addRow(panel, "Subject:", subject);
        addRow(panel, "Creator:", creator);
        addRow(panel, "Producer:", producer);
        addRow(panel, "Keywords:", keywords);
        if (Dialogs.showConfirmDialog(parent, panel, "Edit Metadata", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            session.rewrite(doc -> {
                PDDocumentInformation d = doc.getDocumentInformation();
                d.setTitle(title.getText());
                d.setAuthor(author.getText());
                d.setSubject(subject.getText());
                d.setCreator(creator.getText());
                d.setProducer(producer.getText());
                d.setKeywords(keywords.getText());
            }, session.password());
            String notice = session.takeNotice();
            info("Metadata saved." + (notice == null ? "" : "\n\n" + notice), "Edit Metadata");
        } catch (IOException ex) {
            error("Could not save metadata", ex);
        }
    }

    private static JTextField field(String v) {
        JTextField f = new JTextField(v == null ? "" : v, 24);
        Ui.styleField(f);
        return f;
    }

    private static void addRow(JPanel p, String label, JComponent c) {
        p.add(new JLabel(label));
        p.add(c);
    }
}
