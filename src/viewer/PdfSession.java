package viewer;

import ui.Dialogs;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the one PDF that is currently open: the {@link PDDocument}, its
 * file, and its password.
 *
 * <h3>Threading model</h3>
 * <ul>
 *   <li>The live document is used for <b>display only</b> (rendering and
 *       page sizes). Rendering happens on one background thread and always
 *       holds {@link #lock()}.</li>
 *   <li>Anything else that reads or changes the PDF (export, split,
 *       watermark, search, security, metadata) works on its own
 *       {@linkplain #openCopy() copy}, so it can never race the renderer or
 *       leave the live document half-modified.</li>
 *   <li>Invariant: the live document always equals the file on disk. Edits
 *       are written to a temporary file and swapped in with
 *       {@link #rewrite}, then the document is reopened.</li>
 * </ul>
 */
public final class PdfSession implements AutoCloseable {

    /** Something that edits a document and may throw {@link IOException}. */
    @FunctionalInterface
    public interface DocEdit {
        void apply(PDDocument doc) throws IOException;
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Runnable> listeners = new ArrayList<>();

    private PDDocument document;
    private File file;
    private String password;
    private volatile int generation;
    private String notice;

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public ReentrantLock lock()   { return lock; }
    public PDDocument document()  { return document; }
    public File file()            { return file; }
    public String password()      { return password; }
    public boolean isOpen()       { return document != null; }
    /** Bumped every time the document is replaced; render caches key on it. */
    public int generation()       { return generation; }
    public int pageCount()        { return document == null ? 0 : document.getNumberOfPages(); }

    /** Forgets the remembered password (used before opening a different file). */
    public void forgetPassword()  { password = null; }

    /** Called (on the calling thread) after the document is opened, replaced or closed. */
    public void addChangeListener(Runnable r) { listeners.add(r); }

    private void fireChanged() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    /**
     * Opens {@code f}, asking for a password when needed.
     *
     * @return {@code true} if opened; {@code false} if the user cancelled the password prompt
     * @throws IOException if the file is missing or is not a readable PDF
     */
    public boolean open(File f, Component parent) throws IOException {
        if (f == null || !f.isFile()) {
            throw new IOException("File not found: " + f);
        }
        PDDocument doc = null;
        String pw = (file != null && file.equals(f)) ? password : null;
        int attempts = 0;
        while (doc == null) {
            try {
                doc = pw == null ? Loader.loadPDF(f) : Loader.loadPDF(f, pw);
            } catch (InvalidPasswordException ex) {
                if (attempts++ >= 5) throw new IOException("Too many incorrect passwords.");
                pw = askPassword(parent, f, attempts > 1);
                if (pw == null) return false;
            }
        }
        install(doc, f, pw);
        return true;
    }

    /** Reopens the current file (e.g. after it changed on disk). */
    public void reload(Component parent) throws IOException {
        if (file != null) open(file, parent);
    }

    private void install(PDDocument doc, File f, String pw) {
        lock.lock();
        try {
            PDDocument old = document;
            document = doc;
            file = f;
            password = pw;
            generation++;
            closeQuietly(old);
        } finally {
            lock.unlock();
        }
        fireChanged();
    }

    private static String askPassword(Component parent, File f, boolean retry) {
        JPasswordField field = new JPasswordField(20);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel((retry ? "Incorrect password. " : "") + "\"" + f.getName()
                + "\" is password protected."), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        int r = Dialogs.showConfirmDialog(parent, panel, "Password required",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? new String(field.getPassword()) : null;
    }

    /**
     * Loads an independent copy of the current file for background work.
     * The caller must close it (use try-with-resources).
     */
    public PDDocument openCopy() throws IOException {
        if (file == null) throw new IOException("No PDF is open.");
        return password == null ? Loader.loadPDF(file) : Loader.loadPDF(file, password);
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    /**
     * Edits the open PDF in place, safely.
     *
     * <p>The edit runs on a copy and is saved to a temporary file beside
     * the original. Only when that succeeds is the live document closed,
     * the original replaced (atomically where the file system allows) and
     * the result reopened. If anything fails, the original file and the
     * open document are left untouched.</p>
     *
     * @param newPassword the password the result needs to be reopened
     *                    ({@code null} if it will be unencrypted)
     */
    public void rewrite(DocEdit edit, String newPassword) throws IOException {
        if (file == null) throw new IOException("No PDF is open.");
        Path target = file.toPath();
        Path dir = target.toAbsolutePath().getParent();
        Path tmp = Files.createTempFile(dir, ".readora-", ".tmp");
        try {
            try (PDDocument copy = openCopy()) {
                edit.apply(copy);
                keepProtection(copy);
                copy.save(tmp.toFile());
            }
            lock.lock();
            try {
                PDDocument old = document;
                document = null;
                closeQuietly(old);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // Could not replace: put the original back on screen.
                    document = password == null ? Loader.loadPDF(file) : Loader.loadPDF(file, password);
                    generation++;
                    throw e;
                }
            } finally {
                lock.unlock();
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        PDDocument fresh = newPassword == null ? Loader.loadPDF(file) : Loader.loadPDF(file, newPassword);
        install(fresh, file, newPassword);
    }

    /**
     * Writes an edited <i>copy</i> of the open PDF to {@code out}, leaving the open document and its file
     * untouched. Protection is carried over exactly as in {@link #rewrite}.
     */
    public void saveCopyTo(File out, DocEdit edit) throws IOException {
        try (PDDocument copy = openCopy()) {
            edit.apply(copy);
            keepProtection(copy);
            copy.save(out);
        }
    }

    /**
     * PDFBox refuses to save a password-protected document unless a protection policy is supplied, so an
     * edit that does not touch security (metadata, page edits...) would otherwise fail. This re-applies the
     * file's protection: same permission bits, AES-256, protected with the password it was opened with.
     * The original owner and user passwords can't both be recovered from an opened file, so the opening
     * password is used for both; {@link #takeNotice()} tells the user.
     */
    private void keepProtection(PDDocument copy) throws IOException {
        if (!copy.isEncrypted() || copy.isAllSecurityToBeRemoved()) return;
        if (copy.getEncryption().getSecurityHandler().hasProtectionPolicy()) return;     // the edit set its own
        String pw = password == null ? "" : password;
        AccessPermission ap = new AccessPermission(copy.getEncryption().getPermissions());
        StandardProtectionPolicy policy = new StandardProtectionPolicy(pw, pw, ap);
        policy.setEncryptionKeyLength(256);
        copy.protect(policy);
        notice = "This PDF is password protected. It was saved with the same permissions, protected by the "
                + "password you opened it with (used as both the owner and user password).\n\n"
                + "To use different passwords, choose Tools \u2192 Change Password.";
    }

    /** A message the caller should show once after a successful {@link #rewrite}, or null. */
    public String takeNotice() {
        String n = notice;
        notice = null;
        return n;
    }

    // ------------------------------------------------------------------
    // Closing
    // ------------------------------------------------------------------

    @Override
    public void close() {
        lock.lock();
        try {
            closeQuietly(document);
            document = null;
            file = null;
            password = null;
            generation++;
        } finally {
            lock.unlock();
        }
        fireChanged();
    }

    private static void closeQuietly(PDDocument d) {
        if (d != null) {
            try { d.close(); } catch (IOException ignored) { }
        }
    }
}
