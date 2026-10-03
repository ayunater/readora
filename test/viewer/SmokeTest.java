package viewer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import store.FileListStore;
import viewer.ops.*;

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class SmokeTest {
    static int pass, fail;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (ok) pass++; else fail++;
    }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("readora-smoke");
        File samples = new File(tmp.toFile(), "samples");
        samples.mkdirs();
        makeSamples(samples);

        // ---------- 1. FileListStore: commas, unicode, legacy migration ----------
        File tsv = new File(tmp.toFile(), "recent.tsv"), csv = new File(tmp.toFile(), "recent.csv");
        // Use a non-ASCII character only if this machine's file-name encoding can represent it.
        boolean unicodeNames = java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"))
                .newEncoder().canEncode('\u00e9');
        File odd = new File(tmp.toFile(), "Report, final (v2)" + (unicodeNames ? " \u00e9" : "") + ".pdf");
        Files.write(odd.toPath(), new byte[]{1});
        FileListStore st = new FileListStore(tsv.getPath(), csv.getPath(), 3);
        st.add(odd);
        st.add(new File(tmp.toFile(), "b.pdf"));
        st.add(new File(tmp.toFile(), "c.pdf"));
        st.add(new File(tmp.toFile(), "d.pdf"));
        List<FileListStore.Entry> es = st.load();
        check("store: capped at max entries", es.size() == 3);
        check("store: newest first", es.get(0).name().equals("d.pdf"));
        st.add(odd);
        check("store: comma" + (unicodeNames ? "/unicode" : "") + " name round-trips", st.load().get(0).file.equals(odd.getAbsoluteFile()));
        check("store: contains()", st.contains(odd));
        st.remove(odd);
        check("store: remove()", !st.contains(odd));

        File tsv2 = new File(tmp.toFile(), "r2.tsv"), csv2 = new File(tmp.toFile(), "r2.csv");
        Files.writeString(csv2.toPath(),
            "Report, final.pdf," + tmp + "/Report, final.pdf,02 Oct 2026, 08:31\n" +
            "plain.pdf," + tmp + "/plain.pdf,01 Jan 2026, 09:05\n");
        List<FileListStore.Entry> mig = new FileListStore(tsv2.getPath(), csv2.getPath(), 50).load();
        check("migration: both legacy rows read", mig.size() == 2);
        check("migration: comma in file name recovered", mig.get(0).name().equals("Report, final.pdf"));
        check("migration: time kept", mig.get(0).when().equals("02 Oct 2026, 08:31"));
        check("migration: writes new file", tsv2.exists());

        // ---------- 2. PageGeometry: mixed sizes + rotation ----------
        try (PDDocument d = Loader.loadPDF(new File(samples, "mixed.pdf"))) {
            PageGeometry g = PageGeometry.of(d);
            check("geometry: 8 pages", g.count() == 8);
            check("geometry: A3 page larger than Letter", g.width(4) > g.width(0));
            check("geometry: landscape wider than tall", g.width(5) > g.height(5));
            check("geometry: rotated-90 Letter swaps w/h", Math.abs(g.width(6) - 792) < 1 && Math.abs(g.height(6) - 612) < 1);
        }

        // ---------- 3. PdfSession: open, copy, rewrite ----------
        File work = new File(tmp.toFile(), "work.pdf");
        Files.copy(new File(samples, "mixed.pdf").toPath(), work.toPath());
        PdfSession s = new PdfSession();
        int[] changes = {0};
        s.addChangeListener(() -> changes[0]++);
        check("session: opens", s.open(work, null));
        check("session: page count", s.pageCount() == 8);
        try (PDDocument c = s.openCopy()) { check("session: independent copy", c != s.document() && c.getNumberOfPages() == 8); }

        s.rewrite(d -> d.getDocumentInformation().setTitle("Edited"), null);
        check("rewrite: metadata persisted to disk", titleOf(work).equals("Edited"));
        check("rewrite: live doc reloaded", s.document().getDocumentInformation().getTitle().equals("Edited"));
        check("rewrite: no temp files left", noTemp(tmp));

        long before = work.length(); byte[] snapshot = Files.readAllBytes(work.toPath());
        try {
            s.rewrite(d -> { throw new java.io.IOException("boom"); }, null);
            check("rewrite failure: throws", false);
        } catch (java.io.IOException e) {
            check("rewrite failure: throws", true);
        }
        check("rewrite failure: original file untouched", Arrays.equals(snapshot, Files.readAllBytes(work.toPath())));
        check("rewrite failure: document still open", s.isOpen() && s.pageCount() == 8);
        check("rewrite failure: no temp files left", noTemp(tmp));

        // encrypt, then decrypt
        s.rewrite(d -> {
            var ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            var pol = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("own", "usr", ap);
            pol.setEncryptionKeyLength(256);
            d.protect(pol);
        }, "own");
        boolean enc;
        try { Loader.loadPDF(work).close(); enc = false; } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) { enc = true; }
        check("security: file on disk now needs a password", enc);
        check("security: session still open after encrypt", s.isOpen() && s.document().isEncrypted());
        check("security: owner permission retained", s.document().getCurrentAccessPermission().isOwnerPermission());
        s.rewrite(d -> d.setAllSecurityToBeRemoved(true), null);
        boolean open2; try { Loader.loadPDF(work).close(); open2 = true; } catch (Exception e) { open2 = false; }
        check("security: password removed on disk", open2);
        check("session: change listener fired for each edit", changes[0] >= 4);

        // ---------- 4. SearchStripper ----------
        try (PDDocument d = Loader.loadPDF(new File(samples, "mixed.pdf"))) {
            SearchStripper ss = new SearchStripper("QUICK brown");
            ss.getText(d);
            String all = new org.apache.pdfbox.text.PDFTextStripper().getText(d).toLowerCase();
            int expect = 0; for (int i = 0; (i = all.indexOf("quick brown", i)) >= 0; i += 11) expect++;
            check("search: hit count equals independent text count (" + expect + ")", expect > 0 && ss.getResults().size() == expect);
            HighlightArea h = ss.getResults().get(0).getHighlight();
            check("search: highlight geometry sane", h.getWidth() > 10 && h.getHeight() > 3 && h.getPage() == 1);
            SearchStripper none = new SearchStripper("zzzqqq"); none.getText(d);
            check("search: no hits is empty", none.getResults().isEmpty());
        }

        // ---------- 5. Watermark on a COPY (live doc untouched) ----------
        File wmOut = new File(tmp.toFile(), "wm.pdf");
        WatermarkOps ops = new WatermarkOps(s, null, f -> { });
        Class<?> setCls = Class.forName("viewer.ops.WatermarkOps$WatermarkSettings");
        Constructor<?> ctor = setCls.getDeclaredConstructor(); ctor.setAccessible(true);
        Object ws = ctor.newInstance();
        set(ws, "type", "TEXT"); set(ws, "text", "CONFIDENTIAL");
        File font = findAnyTrueTypeFont();
        if (font == null) {
            System.out.println("SKIP  watermark tests (no .ttf font found on this machine)");
            s.close();
            System.out.println("\n" + pass + " passed, " + fail + " failed");
            System.exit(fail == 0 ? 0 : 1);
        }
        set(ws, "fontFile", font); set(ws, "fontSize", 48); set(ws, "color", java.awt.Color.RED);
        set(ws, "opacity", 0.4f); set(ws, "rotation", 45f); set(ws, "position", "CENTER");
        set(ws, "startPage", 1); set(ws, "endPage", 3); set(ws, "horizontalMargin", 20); set(ws, "verticalMargin", 20);
        set(ws, "fitToPage", false); set(ws, "scale", 1f);
        Method apply = WatermarkOps.class.getDeclaredMethod("applyWatermark", PDDocument.class, setCls);
        apply.setAccessible(true);
        byte[] liveBefore = Files.readAllBytes(work.toPath());
        try (PDDocument copy = s.openCopy()) {
            apply.invoke(ops, copy, ws);
            copy.save(wmOut);
        }
        check("watermark: output written", wmOut.length() > 0);
        check("watermark: source file unchanged", Arrays.equals(liveBefore, Files.readAllBytes(work.toPath())));
        check("watermark: live doc not modified in memory",
              s.document().getPage(0).getContents() != null && contentLen(s.document(), 0) == contentLen(Loader.loadPDF(work), 0));
        try (PDDocument d = Loader.loadPDF(wmOut)) {
            check("watermark: page 1 has extra content", contentLen(d, 0) > contentLen(s.document(), 0));
            check("watermark: page 5 untouched", contentLen(d, 4) == contentLen(s.document(), 4));
            new PDFRenderer(d).renderImage(0, 1f);
            check("watermark: result renders", true);
        }

        s.close();
        check("session: closed", !s.isOpen());
        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
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

    static void set(Object o, String f, Object v) throws Exception {
        Field fld = o.getClass().getDeclaredField(f); fld.setAccessible(true); fld.set(o, v);
    }
    static String titleOf(File f) throws Exception {
        try (PDDocument d = Loader.loadPDF(f)) { return d.getDocumentInformation().getTitle(); }
    }
    static boolean noTemp(Path dir) throws Exception {
        try (var st = Files.list(dir)) { return st.noneMatch(p -> p.getFileName().toString().startsWith(".readora-")); }
    }
    static long contentLen(PDDocument d, int p) throws Exception {
        PDPage pg = d.getPage(p);
        try (var in = pg.getContents()) { return in.readAllBytes().length; }
    }
}
