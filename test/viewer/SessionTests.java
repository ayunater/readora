package viewer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** PdfSession: open, independent copies, safe rewrite and failure handling. */
final class SessionTests {

    private SessionTests() { }

    static void run(PdfSession s, File work, Path tmp) throws Exception {
        int[] changes = {0};
        s.addChangeListener(() -> changes[0]++);
        T.check("session: opens", s.open(work, null));
        T.check("session: page count", s.pageCount() == 8);
        try (PDDocument c = s.openCopy()) { T.check("session: independent copy", c != s.document() && c.getNumberOfPages() == 8); }

        s.rewrite(d -> d.getDocumentInformation().setTitle("Edited"), null);
        T.check("rewrite: metadata persisted to disk", T.titleOf(work).equals("Edited"));
        T.check("rewrite: live doc reloaded", s.document().getDocumentInformation().getTitle().equals("Edited"));
        T.check("rewrite: no temp files left", T.noTemp(tmp));

        byte[] snapshot = Files.readAllBytes(work.toPath());
        try {
            s.rewrite(d -> { throw new java.io.IOException("boom"); }, null);
            T.check("rewrite failure: throws", false);
        } catch (java.io.IOException e) {
            T.check("rewrite failure: throws", true);
        }
        T.check("rewrite failure: original file untouched", Arrays.equals(snapshot, Files.readAllBytes(work.toPath())));
        T.check("rewrite failure: document still open", s.isOpen() && s.pageCount() == 8);
        T.check("rewrite failure: no temp files left", T.noTemp(tmp));

        // encrypt, then decrypt
        s.rewrite(d -> {
            var ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            var pol = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("own", "usr", ap);
            pol.setEncryptionKeyLength(256);
            d.protect(pol);
        }, "own");
        boolean enc;
        try { Loader.loadPDF(work).close(); enc = false; } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) { enc = true; }
        T.check("security: file on disk now needs a password", enc);
        T.check("security: session still open after encrypt", s.isOpen() && s.document().isEncrypted());
        T.check("security: owner permission retained", s.document().getCurrentAccessPermission().isOwnerPermission());
        s.rewrite(d -> d.setAllSecurityToBeRemoved(true), null);
        boolean open2; try { Loader.loadPDF(work).close(); open2 = true; } catch (Exception e) { open2 = false; }
        T.check("security: password removed on disk", open2);
        T.check("session: change listener fired for each edit", changes[0] >= 4);
    }
}
