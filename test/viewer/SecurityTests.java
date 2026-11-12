package viewer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import viewer.ops.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Security: password strength, encryption, hidden-data cleaning and true redaction. */
final class SecurityTests {

    private SecurityTests() { }

    static void run(Path tmp) throws Exception {
        passwordOptions(tmp);
        hiddenData(tmp);
        redaction(tmp);
    }

    /** Password strength, generator and protection options. */
    private static void passwordOptions(Path tmp) throws Exception {
        // -- password strength + generator --
        T.check("strength: empty is EMPTY", PasswordStrength.evaluate("".toCharArray()).level() == PasswordStrength.Level.EMPTY);
        T.check("strength: '123456', 'password', 'abcdefgh' and 'aaaaaaaaaa' are all WEAK",
              java.util.stream.Stream.of("123456", "password", "abcdefgh", "aaaaaaaaaa", "Password1").allMatch(x ->
                  PasswordStrength.evaluate(x.toCharArray()).level() == PasswordStrength.Level.WEAK));
        var okMid = PasswordStrength.evaluate("Tr0ub4dor&3".toCharArray());
        T.check("strength: a decent 11-char mixed password is GOOD, not STRONG", okMid.level() == PasswordStrength.Level.GOOD);
        T.check("strength: a long passphrase is STRONG", PasswordStrength.evaluate("correct-Horse-battery-9-staple".toCharArray()).level() == PasswordStrength.Level.STRONG);
        char[] g1 = PasswordStrength.generate(16), g2 = PasswordStrength.generate(16);
        String gs = new String(g1);
        T.check("generator: 16 chars with lower, upper, digit and symbol; two calls differ; result rated STRONG",
              g1.length == 16 && gs.matches(".*[a-z].*") && gs.matches(".*[A-Z].*") && gs.matches(".*[0-9].*") && gs.matches(".*[^A-Za-z0-9].*")
              && !gs.equals(new String(g2)) && PasswordStrength.evaluate(g1).level() == PasswordStrength.Level.STRONG);

        // -- protection options: validation --
        var o = new ProtectionOptions();
        T.check("options: nothing chosen is rejected", o.validate() != null);
        o.openPassword = "open-pass".toCharArray();
        T.check("options: an open password alone is valid", o.validate() == null);
        o.allowed[0] = false;
        T.check("options: restrictions without a permissions password are rejected", o.validate() != null);
        o.permissionsPassword = "own-pass".toCharArray();
        T.check("options: restrictions + permissions password are valid", o.validate() == null);
        o.permissionsPassword = "open-pass".toCharArray();
        T.check("options: identical passwords are detected", o.passwordsMatch());
        o.permissionsPassword = "own-pass".toCharArray(); o.keyBits = 128; o.openPassword = "p\u0430ss\u0436".toCharArray();
        T.check("options: AES-128 refuses non-Latin-1 passwords (readers disagree on the encoding)", o.validate() != null);
        o.keyBits = 256;
        T.check("options: AES-256 accepts them", o.validate() == null);
        T.check("options: reopen with the permissions password when there is one", "own-pass".equals(o.reopenPassword()));

        // -- protection options: the real encryption they produce --
        File plain = new File(tmp.toFile(), "sec-plain.pdf");
        try (PDDocument d = new PDDocument()) { d.addPage(new PDPage()); d.save(plain); }
        for (int bits : new int[]{256, 128}) {
            var po = new ProtectionOptions();
            po.keyBits = bits; po.openPassword = "open-123-XY".toCharArray(); po.permissionsPassword = "owner-456-XY".toCharArray();
            po.allowed[0] = false; po.allowed[3] = false;                                   // no printing, no copying
            File prot = new File(tmp.toFile(), "sec-" + bits + ".pdf");
            try (PDDocument d = Loader.loadPDF(plain)) { d.protect(po.toPolicy()); d.save(prot); }
            boolean wrong; try { Loader.loadPDF(prot, "nope").close(); wrong = false; } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) { wrong = true; }
            boolean none; try { Loader.loadPDF(prot).close(); none = false; } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) { none = true; }
            T.check("AES-" + bits + ": wrong password and no password are both refused", wrong && none);
            try (PDDocument d = Loader.loadPDF(prot, "open-123-XY")) {
                var ap = d.getCurrentAccessPermission();
                T.check("AES-" + bits + ": opening with the open password honours the restrictions (no print, no copy, still can fill forms)",
                      !ap.canPrint() && !ap.canExtractContent() && ap.canFillInForm() && !ap.isOwnerPermission());
                T.check("AES-" + bits + ": key length recorded as " + bits + " bits", d.getEncryption().getLength() == bits);
            }
            try (PDDocument d = Loader.loadPDF(prot, "owner-456-XY")) {
                T.check("AES-" + bits + ": the permissions password gives full control", d.getCurrentAccessPermission().isOwnerPermission());
            }
        }
        // permissions password only: anyone can open it, but the restrictions bind
        var ro = new ProtectionOptions();
        ro.permissionsPassword = "owner-only-77".toCharArray(); ro.allowed[2] = false; ro.allowed[3] = false;
        File restrictedOnly = new File(tmp.toFile(), "sec-restricted.pdf");
        try (PDDocument d = Loader.loadPDF(plain)) { d.protect(ro.toPolicy()); d.save(restrictedOnly); }
        try (PDDocument d = Loader.loadPDF(restrictedOnly)) {
            T.check("restrict-only file: opens with no password, but modifying and copying are blocked",
                  !d.getCurrentAccessPermission().canModify() && !d.getCurrentAccessPermission().canExtractContent()
                  && d.getCurrentAccessPermission().canPrint());
        }
    }

    /** Hidden data: scan, then clean partially and fully. */
    private static void hiddenData(Path tmp) throws Exception {
        File dirty = new File(tmp.toFile(), "dirty.pdf");
        try (PDDocument d = new PDDocument()) {
            var page = new PDPage(); d.addPage(page);
            var info = d.getDocumentInformation(); info.setTitle("Merger plan"); info.setAuthor("Jane Doe"); info.setCustomMetadataValue("Project", "Falcon");
            var cat = d.getDocumentCatalog();
            var md = new org.apache.pdfbox.pdmodel.common.PDMetadata(d); md.importXMPMetadata("<x:xmpmeta xmlns:x='adobe:ns:meta/'/>".getBytes()); cat.setMetadata(md);
            var names = new org.apache.pdfbox.pdmodel.PDDocumentNameDictionary(cat);
            var ef = new org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile(d, new java.io.ByteArrayInputStream("hidden".getBytes()));
            var fs = new org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification(); fs.setFile("secret.txt"); fs.setEmbeddedFile(ef);
            var embedded = new org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode(); embedded.setNames(java.util.Map.of("secret.txt", fs)); names.setEmbeddedFiles(embedded);
            var js = new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("app.alert('hi')");
            var jt = new org.apache.pdfbox.pdmodel.PDJavascriptNameTreeNode(); jt.setNames(java.util.Map.of("init", js)); names.setJavascript(jt);
            cat.setNames(names);
            cat.setOpenAction(new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("this.print()"));
            var link = new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
            link.setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(10, 10, 50, 20));
            link.setAction(new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("evil()"));
            var note = new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText(); note.setContents("call him before Friday");
            note.setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(80, 80, 20, 20));
            page.setAnnotations(new ArrayList<>(List.of(link, note)));
            var outline = new org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline();
            var item = new org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem(); item.setTitle("Layoffs chapter"); outline.addLast(item);
            cat.setDocumentOutline(outline);
            d.save(dirty);
        }
        try (PDDocument d = Loader.loadPDF(dirty)) {
            var rep = Sanitizer.scan(d);
            T.check("scan: finds metadata (Title, Author and a custom field), XMP, the attachment, 3 scripts, the comment and the bookmarks",
                  rep.infoFields.containsAll(List.of("Title", "Author", "Project")) && rep.xmp && rep.attachments == 1
                  && rep.scripts == 3 && rep.comments == 1 && rep.bookmarks && !rep.isClean());
            Sanitizer.apply(d, new Sanitizer.Options(false, false, false, true, false));
            var partial = Sanitizer.scan(d);
            T.check("clean (comments only): the comment goes, metadata and the rest stay", partial.comments == 0 && partial.hasMetadata() && partial.attachments == 1 && partial.bookmarks);
        }
        try (PDDocument d = Loader.loadPDF(dirty)) {
            Sanitizer.apply(d, Sanitizer.Options.all());
            File cleaned = new File(tmp.toFile(), "cleaned.pdf"); d.save(cleaned);
            try (PDDocument c = Loader.loadPDF(cleaned)) {
                var rep = Sanitizer.scan(c);
                T.check("clean (everything): a re-opened file reports no metadata, attachments, scripts, comments or bookmarks", rep.isClean());
                T.check("clean: the link survives (the document still works), only its script is gone", c.getPage(0).getAnnotations().size() == 1);
                T.check("clean: the author name is gone from the document info", c.getDocumentInformation().getAuthor() == null);
            }
        }
    }

    /** TRUE redaction: the secret must be gone from the FILE, not just hidden. */
    private static void redaction(Path tmp) throws Exception {
        File secret = new File(tmp.toFile(), "secret.pdf");
        try (PDDocument d = new PDDocument()) {
            for (String txt : new String[]{"TOP-SECRET-4711 salary 90000", "KEEP-ME-999 public"}) {
                var pg = new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4); d.addPage(pg);
                try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(d, pg, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE, false)) {
                    cs.beginText(); cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 18);
                    cs.newLineAtOffset(50, 700); cs.showText(txt); cs.endText();
                }
            }
            d.save(secret, org.apache.pdfbox.pdfwriter.compress.CompressParameters.NO_COMPRESSION);
        }
        T.check("redact setup: the secret text is visible in the raw bytes of the original file", T.containsBytes(secret, "TOP-SECRET-4711"));
        File redacted = new File(tmp.toFile(), "redacted.pdf");
        try (PDDocument d = Loader.loadPDF(secret)) {
            var box = T.shape(0, viewer.edit.EditItem.Shape.Kind.REDACT, 40, 120, 330, 36, java.awt.Color.BLACK);   // visual y = 842 - 700 = 142 baseline
            var res = viewer.edit.EditWriter.apply(d, List.of(box));
            T.check("redact: one page flattened and the user is told", res.redactedPages == 1 && !res.warnings.isEmpty());
            d.save(redacted, org.apache.pdfbox.pdfwriter.compress.CompressParameters.NO_COMPRESSION);
        }
        T.check("redact: the secret is NOT in the raw bytes of the saved file", !T.containsBytes(redacted, "TOP-SECRET-4711") && !T.containsBytes(redacted, "salary"));
        try (PDDocument d = Loader.loadPDF(redacted)) {
            String all = new org.apache.pdfbox.text.PDFTextStripper().getText(d);
            T.check("redact: text extraction no longer finds it, and the other page's text is untouched", !all.contains("SECRET") && all.contains("KEEP-ME-999"));
            T.check("redact: the flattened page has no fonts left (nothing to recover)", d.getPage(0).getResources().getFontNames().iterator().hasNext() == false);
            var img = new PDFRenderer(d).renderImage(0, 1f);
            T.check("redact: the box is solid black on the page", T.near(img.getRGB(100, 140), 0, 0, 0, 6) && T.near(img.getRGB(300, 130), 0, 0, 0, 6));
            T.check("redact: the rest of the page is still white and rendered", T.near(img.getRGB(100, 400), 255, 255, 255, 6));
            T.check("redact: the other page stays a normal text page (still has its font)", d.getPage(1).getResources().getFontNames().iterator().hasNext());
        }
        // redaction on a rotated page: the box must land on the same visual spot
        File rot = new File(tmp.toFile(), "rot-secret.pdf");
        try (PDDocument d = new PDDocument()) {
            var pg = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(300, 400)); pg.setRotation(90); d.addPage(pg);
            try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(d, pg, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE, false)) {
                cs.setNonStrokingColor(java.awt.Color.RED); cs.addRect(0, 0, 300, 400); cs.fill();
            }
            var box = T.shape(0, viewer.edit.EditItem.Shape.Kind.REDACT, 100, 50, 40, 30, java.awt.Color.BLACK);
            viewer.edit.EditWriter.apply(d, List.of(box)); d.save(rot);
        }
        try (PDDocument d = Loader.loadPDF(rot)) {
            var img = new PDFRenderer(d).renderImage(0, 1f);
            T.check("redact on a 90-degree page: black exactly at the chosen visual spot, red elsewhere",
                  T.near(img.getRGB(120, 65), 0, 0, 0, 6) && T.near(img.getRGB(250, 200), 255, 0, 0, 14));
        }
    }
}
