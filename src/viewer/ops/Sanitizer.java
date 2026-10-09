package viewer.ops;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds and removes data that travels invisibly inside a PDF and often leaks more than people expect:
 * author and software names, XMP metadata, embedded files, scripts, comments, bookmarks and page thumbnails.
 *
 * <p>Links and form fields are always kept, so the document still works. Removing "comments and markup"
 * removes notes, highlights, stamps and attachments, but not links or fields.</p>
 */
public final class Sanitizer {

    private Sanitizer() { }

    /** What a document contains. */
    public static final class Report {
        public final List<String> infoFields = new ArrayList<>();
        public boolean xmp;
        public int attachments, scripts, comments, thumbnails;
        public boolean bookmarks;

        public boolean hasMetadata()   { return !infoFields.isEmpty() || xmp; }
        public boolean isClean()       { return !hasMetadata() && attachments == 0 && scripts == 0 && comments == 0 && !bookmarks && thumbnails == 0; }
    }

    /** What to remove. */
    public record Options(boolean metadata, boolean attachments, boolean scripts, boolean comments, boolean bookmarks) {
        public static Options all() { return new Options(true, true, true, true, true); }
    }

    // ------------------------------------------------------------------
    // Scan
    // ------------------------------------------------------------------

    public static Report scan(PDDocument doc) throws IOException {
        Report r = new Report();
        PDDocumentInformation info = doc.getDocumentInformation();
        String[][] fields = {{"Title", info.getTitle()}, {"Author", info.getAuthor()}, {"Subject", info.getSubject()},
                {"Keywords", info.getKeywords()}, {"Creator", info.getCreator()}, {"Producer", info.getProducer()}};
        for (String[] f : fields) if (f[1] != null && !f[1].isBlank()) r.infoFields.add(f[0]);
        for (String key : info.getMetadataKeys()) {
            if (!List.of("Title", "Author", "Subject", "Keywords", "Creator", "Producer", "CreationDate", "ModDate", "Trapped").contains(key)) r.infoFields.add(key);
        }
        PDDocumentCatalog catalog = doc.getDocumentCatalog();
        r.xmp = catalog.getMetadata() != null;
        r.bookmarks = catalog.getDocumentOutline() != null;

        PDDocumentNameDictionary names = catalog.getNames();
        if (names != null) {
            if (names.getEmbeddedFiles() != null) {
                var map = names.getEmbeddedFiles().getNames();
                r.attachments += map != null ? map.size() : 1;
            }
            if (names.getJavaScript() != null) {
                var map = names.getJavaScript().getNames();
                r.scripts += map != null ? map.size() : 1;
            }
        }
        if (catalog.getCOSObject().containsKey(COSName.OPEN_ACTION) && isJavaScript(catalog.getOpenAction())) r.scripts++;
        if (catalog.getCOSObject().containsKey(COSName.AA)) r.scripts++;

        for (PDPage page : doc.getPages()) {
            if (page.getCOSObject().containsKey(COSName.THUMB)) r.thumbnails++;
            for (PDAnnotation a : page.getAnnotations()) {
                if (a instanceof PDAnnotationLink link) { if (isJavaScript(link.getAction())) r.scripts++; }
                else if (a instanceof PDAnnotationWidget w) { if (isJavaScript(w.getAction()) || w.getCOSObject().containsKey(COSName.AA)) r.scripts++; }
                else if ("FileAttachment".equals(a.getSubtype())) r.attachments++;
                else if (!"Popup".equals(a.getSubtype())) r.comments++;
            }
        }
        return r;
    }

    private static boolean isJavaScript(Object action) {
        return action instanceof PDActionJavaScript;
    }

    // ------------------------------------------------------------------
    // Clean
    // ------------------------------------------------------------------

    public static void apply(PDDocument doc, Options o) throws IOException {
        PDDocumentCatalog catalog = doc.getDocumentCatalog();
        if (o.metadata()) {
            doc.setDocumentInformation(new PDDocumentInformation());
            catalog.setMetadata(null);
            catalog.getCOSObject().removeItem(COSName.getPDFName("PieceInfo"));
        }
        if (o.bookmarks()) catalog.setDocumentOutline(null);

        PDDocumentNameDictionary names = catalog.getNames();
        if (names != null) {
            if (o.attachments()) names.setEmbeddedFiles(null);
            if (o.scripts()) names.setJavascript(null);
        }
        if (o.scripts()) {
            catalog.getCOSObject().removeItem(COSName.OPEN_ACTION);
            catalog.getCOSObject().removeItem(COSName.AA);
        }

        for (PDPage page : doc.getPages()) {
            var cos = page.getCOSObject();
            if (o.metadata()) { cos.removeItem(COSName.METADATA); cos.removeItem(COSName.getPDFName("PieceInfo")); cos.removeItem(COSName.THUMB); }
            if (o.scripts()) cos.removeItem(COSName.AA);

            List<PDAnnotation> keep = new ArrayList<>();
            for (PDAnnotation a : page.getAnnotations()) {
                if (a instanceof PDAnnotationLink link) {
                    if (o.scripts() && isJavaScript(link.getAction())) link.setAction((PDAction) null);
                    keep.add(a);
                } else if (a instanceof PDAnnotationWidget w) {
                    if (o.scripts()) { if (isJavaScript(w.getAction())) w.setAction(null); w.getCOSObject().removeItem(COSName.AA); }
                    keep.add(a);
                } else if ("FileAttachment".equals(a.getSubtype())) {
                    if (!o.attachments()) keep.add(a);
                } else if (!o.comments()) {
                    keep.add(a);                                          // comments kept; popups go with their note
                }
            }
            page.setAnnotations(keep);
        }
    }
}
