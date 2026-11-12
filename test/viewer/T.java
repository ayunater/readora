package viewer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared support for the test suite: assertions, the pass/fail counters, sample
 * PDFs and small utilities. The checks themselves live in one class per area,
 * all driven from {@link SmokeTest}.
 */
final class T {

    private static int pass, fail, skipped;

    private T() { }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (ok) pass++; else fail++;
    }

    static void skip(String name) {
        System.out.println("SKIP  " + name);
        skipped++;
    }

    static int summary() {
        System.out.println("\n" + pass + " passed, " + fail + " failed"
                + (skipped > 0 ? ", " + skipped + " skipped" : ""));
        return fail == 0 ? 0 : 1;
    }

    /** Builds the PDFs the tests need, so nothing depends on files outside the repo. */
    static void makeSamples(File dir) throws Exception {
        try (PDDocument d = new PDDocument()) {
            org.apache.pdfbox.pdmodel.common.PDRectangle[] sizes = {
                org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER, org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER,
                org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER, org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER,
                org.apache.pdfbox.pdmodel.common.PDRectangle.A3,
                new org.apache.pdfbox.pdmodel.common.PDRectangle(842, 595),
                org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER,
                org.apache.pdfbox.pdmodel.common.PDRectangle.A5};
            for (int i = 0; i < sizes.length; i++) {
                PDPage p = new PDPage(sizes[i]);
                if (i == 6) p.setRotation(90);
                d.addPage(p);
                try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(d, p)) {
                    var font = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
                    for (int line = 0; line < 20; line++) {
                        cs.beginText();
                        cs.setFont(font, 11);
                        cs.newLineAtOffset(40, sizes[i].getHeight() - 60 - line * 14);
                        cs.showText("The quick brown fox jumps over the lazy dog, page " + (i + 1));
                        cs.endText();
                    }
                }
            }
            d.save(new File(dir, "mixed.pdf"));
        }
    }

    static File findAnyTrueTypeFont() {
        String[] roots = {"/usr/share/fonts", "/Library/Fonts", "/System/Library/Fonts", "C:\\Windows\\Fonts"};
        for (String r : roots) {
            File f = firstTtf(new File(r), 0);
            if (f != null) return f;
        }
        return null;
    }

    private static File firstTtf(File dir, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null || depth > 4) return null;
        for (File k : kids) if (k.isFile() && k.getName().toLowerCase().endsWith(".ttf")) return k;
        for (File k : kids) if (k.isDirectory()) { File f = firstTtf(k, depth + 1); if (f != null) return f; }
        return null;
    }

    static boolean containsBytes(File f, String needle) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1).contains(needle);
    }

    static boolean near(int rgb, int r, int g, int b, int tol) {
        return Math.abs(((rgb >> 16) & 255) - r) <= tol && Math.abs(((rgb >> 8) & 255) - g) <= tol && Math.abs((rgb & 255) - b) <= tol;
    }

    static viewer.edit.EditItem.Shape shape(int page, viewer.edit.EditItem.Shape.Kind k,
                                            double x, double y, double w, double h, java.awt.Color fill) {
        var s = new viewer.edit.EditItem.Shape();
        s.page = page; s.kind = k; s.fill = fill; s.stroke = null; s.bounds.setRect(x, y, w, h);
        return s;
    }

    static java.awt.datatransfer.Transferable filesFlavor(List<File> files) {
        return new java.awt.datatransfer.Transferable() {
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() { return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor}; }
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f) { return f.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor); }
            public Object getTransferData(java.awt.datatransfer.DataFlavor f) { return files; }
        };
    }

    static void set(Object o, String f, Object v) throws Exception {
        Field fld = o.getClass().getDeclaredField(f); fld.setAccessible(true); fld.set(o, v);
    }

    static String titleOf(File f) throws Exception {
        try (PDDocument d = org.apache.pdfbox.Loader.loadPDF(f)) { return d.getDocumentInformation().getTitle(); }
    }

    static boolean noTemp(Path dir) throws Exception {
        try (var st = Files.list(dir)) { return st.noneMatch(p -> p.getFileName().toString().startsWith(".readora-")); }
    }

    static long contentLen(PDDocument d, int p) throws Exception {
        PDPage pg = d.getPage(p);
        try (var in = pg.getContents()) { return in.readAllBytes().length; }
    }
}
