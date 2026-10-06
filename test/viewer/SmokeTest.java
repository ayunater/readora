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

        // ---------- 0. Themes: both custom themes load and produce valid surfaces ----------
        for (String theme : rightpanel.ThemeManager.THEMES) {
            rightpanel.ThemeManager.applyTheme(theme);
            boolean wantDark = rightpanel.ThemeManager.isDark(theme);
            check("theme '" + theme + "': detected as " + (wantDark ? "dark" : "light"), ui.Ui.isDark() == wantDark);
            check("theme '" + theme + "': accent is the icon violet",
                  java.awt.Color.decode("#6D5CFF").equals(javax.swing.UIManager.getColor("Component.accentColor")));
            java.awt.Color r = ui.Ui.raised(), sk = ui.Ui.sunken();     // used to throw on light themes
            check("theme '" + theme + "': surfaces are valid colours", r != null && sk != null);
        }
        check("theme: old IntelliJ theme names are not accepted", !rightpanel.ThemeManager.isKnown("Gruvbox Dark Hard"));

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

        // ---------- E. Editor: items must land where they were placed, on every page rotation ----------
        editorTests(tmp);
        openWithTests(tmp);

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

    // ======================================================================
    // "Open with" and drag-and-drop
    // ======================================================================

    static void openWithTests(Path tmp) throws Exception {
        // command-line argument: first existing PDF wins, junk and non-PDFs are ignored
        File pdf = new File(tmp.toFile(), "arg one.pdf"); Files.write(pdf.toPath(), new byte[]{1});
        File txt = new File(tmp.toFile(), "notes.txt"); Files.write(txt.toPath(), new byte[]{1});
        var first = Class.forName("app.Main").getDeclaredMethod("firstPdfArgument", String[].class);
        first.setAccessible(true);
        check("args: picks the PDF (path with a space), skipping other files",
              pdf.getAbsoluteFile().equals(first.invoke(null, (Object) new String[]{"--flag", txt.getPath(), "missing.pdf", pdf.getPath()})));
        check("args: no PDF -> nothing to open", first.invoke(null, (Object) new String[]{txt.getPath(), "nope.pdf"}) == null
              && first.invoke(null, (Object) new String[0]) == null && first.invoke(null, (Object) null) == null);

        // Linux desktop entry: the file placeholder, mime type and correct quoting
        var entryM = Class.forName("app.FileAssociation").getDeclaredMethod("desktopEntry", List.class, String.class);
        entryM.setAccessible(true);
        String entry = (String) entryM.invoke(null, List.of("/opt/Read ora/bin/Readora"), "readora");
        check("desktop entry: passes the file (%f), declares application/pdf", entry.contains(" %f\n") && entry.contains("MimeType=application/pdf;"));
        check("desktop entry: a launcher path with a space is quoted", entry.contains("Exec=\"/opt/Read ora/bin/Readora\" %f"));
        String jarEntry = (String) entryM.invoke(null, List.of("/usr/bin/java", "-jar", "/home/u/My Apps/Readora.jar"), "readora");
        check("desktop entry: java -jar form keeps the jar path as one argument", jarEntry.contains("Exec=/usr/bin/java -jar \"/home/u/My Apps/Readora.jar\" %f"));
        var qm = Class.forName("app.FileAssociation").getDeclaredMethod("execQuote", String.class); qm.setAccessible(true);
        check("desktop entry: shell characters are escaped", "\"a\\$b\\`c\"".equals(qm.invoke(null, "a$b`c")));

        // Windows registry commands (generated only; never executed here)
        var winM = Class.forName("app.FileAssociation").getDeclaredMethod("windowsRegisterCommands", List.class);
        winM.setAccessible(true);
        @SuppressWarnings("unchecked") List<List<String>> reg = (List<List<String>>) winM.invoke(null, List.of("C:\\Program Files\\Readora\\Readora.exe"));
        String all = reg.toString();
        check("windows: registers under HKCU only (no admin needed)", all.contains("HKCU\\Software\\Classes\\Readora.PDF") && !all.contains("HKLM") && !all.contains("HKEY_LOCAL"));
        check("windows: open command quotes the exe and the %1 file argument",
              all.contains("\"C:\\Program Files\\Readora\\Readora.exe\" \"%1\""));
        check("windows: listed under .pdf OpenWithProgids", all.contains("Classes\\.pdf\\OpenWithProgids"));
        @SuppressWarnings("unchecked") List<List<String>> regJar = (List<List<String>>) winM.invoke(null, List.of("C:\\Java\\javaw.exe", "-jar", "C:\\Apps\\Readora.jar"));
        check("windows: java -jar form has no bogus icon entry", !regJar.toString().contains("DefaultIcon"));

        // real round trip on Linux against a scratch XDG directory
        if (app.FileAssociation.os() == app.FileAssociation.Os.LINUX) {
            File xdg = new File(tmp.toFile(), "xdg"); System.setProperty("readora.xdgDataHome", xdg.getPath());
            var regM = Class.forName("app.FileAssociation").getDeclaredMethod("register", List.class); regM.setAccessible(true);
            check("linux: not registered at first", !app.FileAssociation.isRegistered());
            regM.invoke(null, List.of("/usr/bin/java", "-jar", "/tmp/x/Readora.jar"));
            File desktop = new File(xdg, "applications/readora.desktop");
            check("linux: register writes the desktop entry", desktop.isFile() && Files.readString(desktop.toPath()).contains("MimeType=application/pdf"));
            check("linux: register writes a PNG icon rendered from the SVG logo", new File(xdg, "icons/hicolor/256x256/apps/readora.png").length() > 1000);
            check("linux: isRegistered() sees it", app.FileAssociation.isRegistered());
            app.FileAssociation.unregister();
            check("linux: unregister removes it", !desktop.exists() && !app.FileAssociation.isRegistered());
            System.clearProperty("readora.xdgDataHome");
        }

        // drag and drop: files dropped on a table (which has its own handler that refuses files) still open
        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        javax.swing.JTable table = new javax.swing.JTable(2, 2);
        javax.swing.JTextField field = new javax.swing.JTextField();
        root.add(new javax.swing.JScrollPane(table), java.awt.BorderLayout.CENTER);
        root.add(field, java.awt.BorderLayout.SOUTH);
        java.util.concurrent.CopyOnWriteArrayList<File> opened = new java.util.concurrent.CopyOnWriteArrayList<>();
        viewer.PdfDropHandler.installEverywhere(root, opened::add);
        check("drop: the window itself accepts files",
              root.getTransferHandler().canImport(new javax.swing.TransferHandler.TransferSupport(root, new java.awt.datatransfer.StringSelection("x"))) == false
              && root.getTransferHandler().canImport(new javax.swing.TransferHandler.TransferSupport(root, filesFlavor(List.of(pdf)))));
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            check("drop: tables and text fields get a file-aware drop target",
                  table.getDropTarget() != null && table.getDropTarget().getClass().getSimpleName().equals("FileDropTarget")
                  && field.getDropTarget() != null && field.getDropTarget().getClass().getSimpleName().equals("FileDropTarget"));
        } else {
            System.out.println("SKIP  drop-target wiring (needs a display; covered when run with one)");
        }
        root.getTransferHandler().importData(new javax.swing.TransferHandler.TransferSupport(root, filesFlavor(List.of(txt, pdf))));
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        check("drop: with several files, the first PDF is opened", opened.size() == 1 && opened.get(0).equals(pdf));
    }

    static java.awt.datatransfer.Transferable filesFlavor(List<File> files) {
        return new java.awt.datatransfer.Transferable() {
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() { return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor}; }
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f) { return f.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor); }
            public Object getTransferData(java.awt.datatransfer.DataFlavor f) { return files; }
        };
    }

    // ======================================================================
    // Editor tests
    // ======================================================================

    static boolean near(int rgb, int r, int g, int b, int tol) {
        return Math.abs(((rgb >> 16) & 255) - r) <= tol && Math.abs(((rgb >> 8) & 255) - g) <= tol && Math.abs((rgb & 255) - b) <= tol;
    }

    static viewer.edit.EditItem.Shape shape(int page, viewer.edit.EditItem.Shape.Kind k, double x, double y, double w, double h, java.awt.Color fill) {
        var s = new viewer.edit.EditItem.Shape();
        s.page = page; s.kind = k; s.fill = fill; s.stroke = null; s.bounds.setRect(x, y, w, h);
        return s;
    }

    static void editorTests(Path tmp) throws Exception {
        // -- 1. geometry on all four rotations: render at 1 px = 1 pt and probe pixels --
        File base = new File(tmp.toFile(), "rot.pdf");
        try (PDDocument d = new PDDocument()) {
            for (int rot : new int[]{0, 90, 180, 270}) {
                PDPage p = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(300, 400));   // non-square on purpose
                p.setRotation(rot);
                d.addPage(p);
            }
            d.save(base);
        }
        File blue = new File(tmp.toFile(), "blue.png");
        var bi = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g2 = bi.createGraphics(); g2.setColor(new java.awt.Color(0, 0, 255)); g2.fillRect(0, 0, 20, 20); g2.dispose();
        javax.imageio.ImageIO.write(bi, "png", blue);

        List<viewer.edit.EditItem> items = new ArrayList<>();
        for (int pg = 0; pg < 4; pg++) {
            items.add(shape(pg, viewer.edit.EditItem.Shape.Kind.RECT, 100, 50, 40, 30, java.awt.Color.RED));          // centre (120,65)
            items.add(shape(pg, viewer.edit.EditItem.Shape.Kind.ELLIPSE, 20, 120, 60, 40, new java.awt.Color(0, 160, 0))); // centre (50,140)
            var pic = new viewer.edit.EditItem.Pic(); pic.page = pg; pic.file = blue; pic.bounds.setRect(150, 200, 30, 30); items.add(pic);   // centre (165,215)
            var hl = shape(pg, viewer.edit.EditItem.Shape.Kind.HIGHLIGHT, 20, 250, 80, 20, new java.awt.Color(255, 235, 59)); items.add(hl);   // centre (60,260)
            var line = new viewer.edit.EditItem.Shape(); line.page = pg; line.kind = viewer.edit.EditItem.Shape.Kind.LINE;
            line.stroke = java.awt.Color.BLACK; line.width = 6; line.bounds.setRect(150, 100, 60, 60); items.add(line);   // (150,100)->(210,160), mid (180,130)
            var ink = new viewer.edit.EditItem.Ink(); ink.page = pg; ink.color = new java.awt.Color(255, 0, 255); ink.width = 6;
            ink.points.add(new java.awt.geom.Point2D.Double(30, 280)); ink.points.add(new java.awt.geom.Point2D.Double(90, 280)); ink.recomputeBounds(); items.add(ink);   // mid (60,280)
        }
        File edited = new File(tmp.toFile(), "rot-edited.pdf");
        try (PDDocument d = Loader.loadPDF(base)) {
            viewer.edit.EditWriter.Result r = viewer.edit.EditWriter.apply(d, items);
            check("editor: all items written (" + r.itemsWritten + "/" + items.size() + ")", r.itemsWritten == items.size() && r.warnings.isEmpty());
            d.save(edited);
        }
        try (PDDocument d = Loader.loadPDF(edited)) {
            var renderer = new PDFRenderer(d);
            for (int pg = 0; pg < 4; pg++) {
                int rot = pg * 90;
                var img = renderer.renderImage(pg, 1f);
                boolean tall = rot == 0 || rot == 180;
                check("editor/rot " + rot + ": page renders as " + (tall ? "300x400" : "400x300"),
                      img.getWidth() == (tall ? 300 : 400) && img.getHeight() == (tall ? 400 : 300));
                check("editor/rot " + rot + ": red rectangle at its visual position", near(img.getRGB(120, 65), 255, 0, 0, 12));
                check("editor/rot " + rot + ": rectangle does not leak elsewhere", near(img.getRGB(120, 100), 255, 255, 255, 3));
                check("editor/rot " + rot + ": ellipse centre green, bbox corner empty (true ellipse)",
                      near(img.getRGB(50, 140), 0, 160, 0, 12) && near(img.getRGB(22, 122), 255, 255, 255, 3));
                check("editor/rot " + rot + ": picture embedded, correct place", near(img.getRGB(165, 215), 0, 0, 255, 12));
                check("editor/rot " + rot + ": highlighter yellow", near(img.getRGB(60, 260), 255, 235, 59, 14));
                check("editor/rot " + rot + ": line passes through its midpoint", img.getRGB(180, 130) != 0xFFFFFFFF && ((img.getRGB(180, 130) >> 16) & 255) < 90);
                check("editor/rot " + rot + ": freehand stroke magenta", near(img.getRGB(60, 280), 255, 0, 255, 12));
            }
        }

        // -- 2. text: real, selectable text in the right place, wrapped within its box --
        File tf = new File(tmp.toFile(), "text.pdf");
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(300, 400)));
            var t = new viewer.edit.EditItem.Text(); t.page = 0; t.size = 20; t.text = "Hello Readora, this sentence must wrap across several lines.";
            t.bounds.setRect(40, 60, 140, 0);
            viewer.edit.EditFonts.fitHeight(t);
            var lay = viewer.edit.EditFonts.layout(t);
            boolean fits = true; for (String l : lay.lines) fits &= lay.width(l) <= 140.5f;
            check("text: wraps into multiple lines (" + lay.lines.size() + ") each within the box width", lay.lines.size() >= 3 && fits);
            check("text: box height follows the wrapped lines", Math.abs(t.bounds.height - lay.lines.size() * 20 * 1.2f) < 0.01);
            viewer.edit.EditWriter.apply(d, List.of(t));
            d.save(tf);
        }
        try (PDDocument d = Loader.loadPDF(tf)) {
            String txt = new org.apache.pdfbox.text.PDFTextStripper().getText(d).replace("\n", " ").replaceAll("\\s+", " ").trim();
            check("text: extractable from the saved PDF", txt.contains("Hello Readora") && txt.contains("several lines."));
            var img = new PDFRenderer(d).renderImage(0, 1f);
            boolean ink = false;
            for (int y = 60; y < 90 && !ink; y++) for (int x = 40; x < 180 && !ink; x++) ink |= ((img.getRGB(x, y) >> 8) & 255) < 100;
            check("text: dark pixels appear in the first line's box", ink);
            boolean outside = false;
            for (int y = 300; y < 390; y++) for (int x = 0; x < 300; x++) outside |= ((img.getRGB(x, y) >> 8) & 255) < 200;
            check("text: nothing drawn far from the box", !outside);
        }

        // -- 3. unicode never crashes the writer --
        File uf = new File(tmp.toFile(), "uni.pdf");
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage());
            var t = new viewer.edit.EditItem.Text(); t.page = 0; t.text = "Caf\u00e9 \u20ac and \u041f\u0440\u0438\u0432\u0435\u0442"; t.bounds.setRect(50, 50, 400, 30);
            var res = viewer.edit.EditWriter.apply(d, List.of(t));
            d.save(uf);
            boolean haveFallback = viewer.edit.EditFonts.fallbackFile() != null;
            check("unicode: " + (haveFallback ? "embedded fallback font used, no warning" : "no Unicode font installed -> '?' substitution warned"),
                  haveFallback ? res.warnings.isEmpty() : !res.warnings.isEmpty());
            check("unicode: file is valid and re-openable", Loader.loadPDF(uf).getNumberOfPages() == 1);
        }

        // -- 4. editing an ENCRYPTED pdf keeps it encrypted and applies the edit --
        File encFile = new File(tmp.toFile(), "enc-edit.pdf");
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage());
            var ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            var pol = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("own", "usr", ap);
            pol.setEncryptionKeyLength(256);
            d.protect(pol);
            d.save(encFile);
        }
        PdfSession es2 = new PdfSession();
        Loader.loadPDF(encFile, "own").close();     // sanity: the password works
        // open through the session's copy API using the password the same way the UI does
        java.lang.reflect.Field pf = PdfSession.class.getDeclaredField("password"); pf.setAccessible(true);
        java.lang.reflect.Field ff = PdfSession.class.getDeclaredField("file"); ff.setAccessible(true);
        java.lang.reflect.Field df = PdfSession.class.getDeclaredField("document"); df.setAccessible(true);
        pf.set(es2, "own"); ff.set(es2, encFile); df.set(es2, Loader.loadPDF(encFile, "own"));
        var rect = shape(0, viewer.edit.EditItem.Shape.Kind.RECT, 50, 50, 60, 60, java.awt.Color.RED);
        es2.rewrite(d -> viewer.edit.EditWriter.apply(d, List.of(rect)), "own");
        boolean stillEncrypted; try { Loader.loadPDF(encFile).close(); stillEncrypted = false; } catch (Exception e) { stillEncrypted = true; }
        check("encrypted: file is still password protected after editing", stillEncrypted);
        try (PDDocument d = Loader.loadPDF(encFile, "own")) {
            check("encrypted: edit is present after reopening with the password", near(new PDFRenderer(d).renderImage(0, 1f).getRGB(80, 80), 255, 0, 0, 12));
        }
        // metadata edit on the same encrypted file (the path Edit Metadata uses) must work and keep restrictions
        File restricted = new File(tmp.toFile(), "restricted.pdf");
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage());
            var ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            ap.setCanPrint(false);
            var pol = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("own", "usr", ap);
            pol.setEncryptionKeyLength(256);
            d.protect(pol);
            d.save(restricted);
        }
        PdfSession es3 = new PdfSession();
        pf.set(es3, "own"); ff.set(es3, restricted); df.set(es3, Loader.loadPDF(restricted, "own"));
        es3.rewrite(d -> d.getDocumentInformation().setTitle("Encrypted title"), "own");
        check("encrypted: a metadata-only edit saves (previously threw IllegalStateException)", es3.takeNotice() != null);
        try (PDDocument d = Loader.loadPDF(restricted, "own")) {
            check("encrypted: title saved", "Encrypted title".equals(d.getDocumentInformation().getTitle()));
            check("encrypted: the 'no printing' restriction survived", !d.getCurrentAccessPermission().canPrint() || d.getCurrentAccessPermission().isOwnerPermission());
            check("encrypted: restriction is recorded in the file's permission bits", !new org.apache.pdfbox.pdmodel.encryption.AccessPermission(d.getEncryption().getPermissions()).canPrint());
        }
        es3.close();
        es2.close();

        // -- 5. model: undo / redo / dirty --
        var m = new viewer.edit.EditModel();
        var a = shape(0, viewer.edit.EditItem.Shape.Kind.RECT, 0, 0, 10, 10, null);
        check("model: starts clean", !m.isDirty() && !m.canUndo());
        m.add(a);
        check("model: add makes it dirty and selects the item", m.isDirty() && m.selected() == a && m.items().size() == 1);
        var before = a.copy(); a.bounds.x = 55; m.changed(a, before);
        m.undo();
        check("model: undo reverts a move", a.bounds.x == 0);
        m.redo();
        check("model: redo re-applies it", a.bounds.x == 55);
        m.undo(); m.undo();
        check("model: undo of the add removes the item", m.items().isEmpty() && m.selected() == null);
        m.redo();
        m.remove(a);
        check("model: remove then undo restores the item", m.items().isEmpty());
        m.undo();
        check("model: ...restored", m.items().size() == 1);
        m.clear();
        check("model: clear (after save) resets dirty state", !m.isDirty() && m.items().isEmpty());

        // -- 6. SVG engine --
        var doc = ui.Svg.parse(new java.io.ByteArrayInputStream(
            "<svg viewBox='0 0 10 10'><rect x='0' y='0' width='10' height='10' fill='#ff0000'/><circle cx='5' cy='5' r='3' fill='#0000ff'/></svg>".getBytes()));
        var simg = doc.toImage(10, 10, java.awt.Color.BLACK);
        check("svg: shapes and colours render", near(simg.getRGB(1, 1), 255, 0, 0, 3) && near(simg.getRGB(5, 5), 0, 0, 255, 3));
        var arc = ui.Svg.parse(new java.io.ByteArrayInputStream(
            "<svg viewBox='0 0 20 20'><path d='M2 10 a8 8 0 0 1 16 0 a8 8 0 0 1 -16 0z' fill='#00ff00'/></svg>".getBytes()));
        var aimg = arc.toImage(20, 20, java.awt.Color.BLACK);
        check("svg: elliptical arcs form a filled circle (centre filled, corner empty)", near(aimg.getRGB(10, 10), 0, 255, 0, 3) && (aimg.getRGB(0, 0) >>> 24) == 0);
        var cc = ui.Svg.parse(new java.io.ByteArrayInputStream(
            "<svg viewBox='0 0 4 4'><rect width='4' height='4' fill='currentColor'/></svg>".getBytes()));
        check("svg: currentColor takes the tint", near(cc.toImage(4, 4, new java.awt.Color(10, 200, 30)).getRGB(2, 2), 10, 200, 30, 2));
        var gr = ui.Svg.parse(new java.io.ByteArrayInputStream(
            ("<svg viewBox='0 0 100 10'><defs><linearGradient id='g' gradientUnits='userSpaceOnUse' x1='0' y1='0' x2='100' y2='0'>"
            + "<stop offset='0' stop-color='#000000'/><stop offset='1' stop-color='#ffffff'/></linearGradient></defs><rect width='100' height='10' fill='url(#g)'/></svg>").getBytes()));
        var gimg = gr.toImage(100, 10, java.awt.Color.BLACK);
        check("svg: linear gradient runs dark to light", ((gimg.getRGB(5, 5) >> 8) & 255) < 40 && ((gimg.getRGB(95, 5) >> 8) & 255) > 215);
        boolean allIcons = true; int iconCount = 0;
        File iconDir = new File("resources/icons");
        File[] icons = iconDir.listFiles((dd, n) -> n.endsWith(".svg"));
        if (icons != null) for (File f : icons) {
            iconCount++;
            try (var in = new java.io.FileInputStream(f)) {
                var dd = ui.Svg.parse(in);
                var im = dd.toImage(24, 24, java.awt.Color.BLACK);
                boolean any = false;
                for (int y = 0; y < 24 && !any; y++) for (int x = 0; x < 24 && !any; x++) any = (im.getRGB(x, y) >>> 24) > 0;
                if (!any) { allIcons = false; System.out.println("   blank icon: " + f.getName()); }
            }
        }
        check("svg: all " + iconCount + " bundled icons parse and draw something", iconCount > 30 && allIcons);
        // every icon NAME used anywhere in the source must exist (a typo here crashed a window once)
        java.util.regex.Pattern use = java.util.regex.Pattern.compile("(?:icon|iconButton|loadIcon|makeCardBtn|createToolButton|tool)\\(\\s*(?:[^\"()]*,\\s*)?\"([a-z0-9]+(?:-[a-z0-9]+)*)\"");
        java.util.regex.Pattern modes = java.util.regex.Pattern.compile("^\\s+[A-Z_]+\\(\"[^\"]*\",\\s*\"[^\"]*\",\\s*\"([^\"]*)\"\\)", java.util.regex.Pattern.MULTILINE);
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        try (var walk = Files.walk(Path.of("src"))) {
            for (Path src : (Iterable<Path>) walk.filter(q -> q.toString().endsWith(".java"))::iterator) {
                String code = Files.readString(src);
                var m1 = use.matcher(code); while (m1.find()) names.add(m1.group(1));
                var m2 = modes.matcher(code); while (m2.find()) names.add(m2.group(1));
            }
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String n : names) if (!n.matches("[a-z0-9]+(-[a-z0-9]+)*") || !new File("resources/icons/" + n + ".svg").isFile()) missing.add(n);
        check("svg: all " + names.size() + " icon names used in the code exist" + (missing.isEmpty() ? "" : "  MISSING " + missing), missing.isEmpty());
        var logo = ui.Svg.parse(new java.io.FileInputStream("resources/logo.svg")).toImage(64, 64, java.awt.Color.BLACK);
        check("svg: logo renders (rounded square with transparent corners)", (logo.getRGB(0, 0) >>> 24) == 0 && (logo.getRGB(32, 8) >>> 24) > 200);
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
