package viewer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Editor: item placement on every rotation, text layout, Unicode, encryption and undo/redo. */
final class EditorTests {

    private EditorTests() { }

    static void run(Path tmp) throws Exception {
        geometryOnEveryRotation(tmp);
        textLayout(tmp);
        unicode(tmp);
        encryptedDocument(tmp);
        undoRedo();
    }

    /** 1. geometry on all four rotations: render at 1 px = 1 pt and probe pixels. */
    private static void geometryOnEveryRotation(Path tmp) throws Exception {
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
            items.add(T.shape(pg, viewer.edit.EditItem.Shape.Kind.RECT, 100, 50, 40, 30, java.awt.Color.RED));          // centre (120,65)
            items.add(T.shape(pg, viewer.edit.EditItem.Shape.Kind.ELLIPSE, 20, 120, 60, 40, new java.awt.Color(0, 160, 0))); // centre (50,140)
            var pic = new viewer.edit.EditItem.Pic(); pic.page = pg; pic.file = blue; pic.bounds.setRect(150, 200, 30, 30); items.add(pic);   // centre (165,215)
            var hl = T.shape(pg, viewer.edit.EditItem.Shape.Kind.HIGHLIGHT, 20, 250, 80, 20, new java.awt.Color(255, 235, 59)); items.add(hl);   // centre (60,260)
            var line = new viewer.edit.EditItem.Shape(); line.page = pg; line.kind = viewer.edit.EditItem.Shape.Kind.LINE;
            line.stroke = java.awt.Color.BLACK; line.width = 6; line.bounds.setRect(150, 100, 60, 60); items.add(line);   // (150,100)->(210,160), mid (180,130)
            var ink = new viewer.edit.EditItem.Ink(); ink.page = pg; ink.color = new java.awt.Color(255, 0, 255); ink.width = 6;
            ink.points.add(new java.awt.geom.Point2D.Double(30, 280)); ink.points.add(new java.awt.geom.Point2D.Double(90, 280)); ink.recomputeBounds(); items.add(ink);   // mid (60,280)
        }
        File edited = new File(tmp.toFile(), "rot-edited.pdf");
        try (PDDocument d = Loader.loadPDF(base)) {
            viewer.edit.EditWriter.Result r = viewer.edit.EditWriter.apply(d, items);
            T.check("editor: all items written (" + r.itemsWritten + "/" + items.size() + ")", r.itemsWritten == items.size() && r.warnings.isEmpty());
            d.save(edited);
        }
        try (PDDocument d = Loader.loadPDF(edited)) {
            var renderer = new PDFRenderer(d);
            for (int pg = 0; pg < 4; pg++) {
                int rot = pg * 90;
                var img = renderer.renderImage(pg, 1f);
                boolean tall = rot == 0 || rot == 180;
                T.check("editor/rot " + rot + ": page renders as " + (tall ? "300x400" : "400x300"),
                      img.getWidth() == (tall ? 300 : 400) && img.getHeight() == (tall ? 400 : 300));
                T.check("editor/rot " + rot + ": red rectangle at its visual position", T.near(img.getRGB(120, 65), 255, 0, 0, 12));
                T.check("editor/rot " + rot + ": rectangle does not leak elsewhere", T.near(img.getRGB(120, 100), 255, 255, 255, 3));
                T.check("editor/rot " + rot + ": ellipse centre green, bbox corner empty (true ellipse)",
                      T.near(img.getRGB(50, 140), 0, 160, 0, 12) && T.near(img.getRGB(22, 122), 255, 255, 255, 3));
                T.check("editor/rot " + rot + ": picture embedded, correct place", T.near(img.getRGB(165, 215), 0, 0, 255, 12));
                T.check("editor/rot " + rot + ": highlighter yellow", T.near(img.getRGB(60, 260), 255, 235, 59, 14));
                T.check("editor/rot " + rot + ": line passes through its midpoint", img.getRGB(180, 130) != 0xFFFFFFFF && ((img.getRGB(180, 130) >> 16) & 255) < 90);
                T.check("editor/rot " + rot + ": freehand stroke magenta", T.near(img.getRGB(60, 280), 255, 0, 255, 12));
            }
        }
    }

    /** 2. text: real, selectable text in the right place, wrapped within its box. */
    private static void textLayout(Path tmp) throws Exception {
        File tf = new File(tmp.toFile(), "text.pdf");
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(300, 400)));
            var t = new viewer.edit.EditItem.Text(); t.page = 0; t.size = 20; t.text = "Hello Readora, this sentence must wrap across several lines.";
            t.bounds.setRect(40, 60, 140, 0);
            viewer.edit.EditFonts.fitHeight(t);
            var lay = viewer.edit.EditFonts.layout(t);
            boolean fits = true; for (String l : lay.lines) fits &= lay.width(l) <= 140.5f;
            T.check("text: wraps into multiple lines (" + lay.lines.size() + ") each within the box width", lay.lines.size() >= 3 && fits);
            T.check("text: box height follows the wrapped lines", Math.abs(t.bounds.height - lay.lines.size() * 20 * 1.2f) < 0.01);
            viewer.edit.EditWriter.apply(d, List.of(t));
            d.save(tf);
        }
        try (PDDocument d = Loader.loadPDF(tf)) {
            String txt = new org.apache.pdfbox.text.PDFTextStripper().getText(d).replace("\n", " ").replaceAll("\\s+", " ").trim();
            T.check("text: extractable from the saved PDF", txt.contains("Hello Readora") && txt.contains("several lines."));
            var img = new PDFRenderer(d).renderImage(0, 1f);
            boolean ink = false;
            for (int y = 60; y < 90 && !ink; y++) for (int x = 40; x < 180 && !ink; x++) ink |= ((img.getRGB(x, y) >> 8) & 255) < 100;
            T.check("text: dark pixels appear in the first line's box", ink);
            boolean outside = false;
            for (int y = 300; y < 390; y++) for (int x = 0; x < 300; x++) outside |= ((img.getRGB(x, y) >> 8) & 255) < 200;
            T.check("text: nothing drawn far from the box", !outside);
        }
    }

    /** 3. unicode never crashes the writer. */
    private static void unicode(Path tmp) throws Exception {
        File uf = new File(tmp.toFile(), "uni.pdf");
        try (PDDocument d = new PDDocument()) {
            d.addPage(new PDPage());
            var t = new viewer.edit.EditItem.Text(); t.page = 0; t.text = "Caf\u00e9 \u20ac and \u041f\u0440\u0438\u0432\u0435\u0442"; t.bounds.setRect(50, 50, 400, 30);
            var res = viewer.edit.EditWriter.apply(d, List.of(t));
            d.save(uf);
            boolean haveFallback = viewer.edit.EditFonts.fallbackFile() != null;
            T.check("unicode: " + (haveFallback ? "embedded fallback font used, no warning" : "no Unicode font installed -> '?' substitution warned"),
                  haveFallback ? res.warnings.isEmpty() : !res.warnings.isEmpty());
            T.check("unicode: file is valid and re-openable", Loader.loadPDF(uf).getNumberOfPages() == 1);
        }
    }

    /** 4. editing an ENCRYPTED pdf keeps it encrypted and applies the edit. */
    private static void encryptedDocument(Path tmp) throws Exception {
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
        var rect = T.shape(0, viewer.edit.EditItem.Shape.Kind.RECT, 50, 50, 60, 60, java.awt.Color.RED);
        es2.rewrite(d -> viewer.edit.EditWriter.apply(d, List.of(rect)), "own");
        boolean stillEncrypted; try { Loader.loadPDF(encFile).close(); stillEncrypted = false; } catch (Exception e) { stillEncrypted = true; }
        T.check("encrypted: file is still password protected after editing", stillEncrypted);
        try (PDDocument d = Loader.loadPDF(encFile, "own")) {
            T.check("encrypted: edit is present after reopening with the password", T.near(new PDFRenderer(d).renderImage(0, 1f).getRGB(80, 80), 255, 0, 0, 12));
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
        T.check("encrypted: a metadata-only edit saves (previously threw IllegalStateException)", es3.takeNotice() != null);
        try (PDDocument d = Loader.loadPDF(restricted, "own")) {
            T.check("encrypted: title saved", "Encrypted title".equals(d.getDocumentInformation().getTitle()));
            T.check("encrypted: the 'no printing' restriction survived", !d.getCurrentAccessPermission().canPrint() || d.getCurrentAccessPermission().isOwnerPermission());
            T.check("encrypted: restriction is recorded in the file's permission bits", !new org.apache.pdfbox.pdmodel.encryption.AccessPermission(d.getEncryption().getPermissions()).canPrint());
        }
        es3.close();
        es2.close();
    }

    /** 5. model: undo / redo / dirty. */
    private static void undoRedo() {
        var m = new viewer.edit.EditModel();
        var a = T.shape(0, viewer.edit.EditItem.Shape.Kind.RECT, 0, 0, 10, 10, null);
        T.check("model: starts clean", !m.isDirty() && !m.canUndo());
        m.add(a);
        T.check("model: add makes it dirty and selects the item", m.isDirty() && m.selected() == a && m.items().size() == 1);
        var before = a.copy(); a.bounds.x = 55; m.changed(a, before);
        m.undo();
        T.check("model: undo reverts a move", a.bounds.x == 0);
        m.redo();
        T.check("model: redo re-applies it", a.bounds.x == 55);
        m.undo(); m.undo();
        T.check("model: undo of the add removes the item", m.items().isEmpty() && m.selected() == null);
        m.redo();
        m.remove(a);
        T.check("model: remove then undo restores the item", m.items().isEmpty());
        m.undo();
        T.check("model: ...restored", m.items().size() == 1);
        m.clear();
        T.check("model: clear (after save) resets dirty state", !m.isDirty() && m.items().isEmpty());
    }
}
