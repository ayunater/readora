package viewer.ops;

import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import java.util.Arrays;

/**
 * What the user chose when protecting a PDF, and how to turn it into a PDFBox policy.
 *
 * <p>A PDF has two independent passwords. The <b>open password</b> (a.k.a. user password) is needed to read
 * the file at all. The <b>permissions password</b> (a.k.a. owner password) lets you change security and
 * overrides the restrictions. A file may have only a permissions password: anyone can open it, but printing,
 * copying and so on are blocked unless the permissions password is given.</p>
 */
public final class ProtectionOptions {

    public static final String[] PERMISSIONS = {
            "Printing", "High-quality printing", "Changing the document", "Copying text and images",
            "Screen readers (accessibility)", "Filling in forms", "Adding comments", "Inserting, rotating or deleting pages"};

    public char[] openPassword = new char[0];
    public char[] permissionsPassword = new char[0];
    /** 256 (AES-256, recommended) or 128 (AES-128, for very old readers). */
    public int keyBits = 256;
    public final boolean[] allowed = new boolean[PERMISSIONS.length];

    public ProtectionOptions() { Arrays.fill(allowed, true); }

    /** Starts from an existing file's permission bits (for "Change permissions"). */
    public static ProtectionOptions from(AccessPermission p) {
        ProtectionOptions o = new ProtectionOptions();
        boolean[] a = {p.canPrint(), p.canPrintFaithful(), p.canModify(), p.canExtractContent(),
                p.canExtractForAccessibility(), p.canFillInForm(), p.canModifyAnnotations(), p.canAssembleDocument()};
        System.arraycopy(a, 0, o.allowed, 0, a.length);
        return o;
    }

    public boolean restrictsAnything() {
        for (boolean b : allowed) if (!b) return true;
        return false;
    }

    public boolean hasOpenPassword()        { return openPassword.length > 0; }
    public boolean hasPermissionsPassword() { return permissionsPassword.length > 0; }

    /** @return a message for the user if these options can't be applied, else null */
    public String validate() {
        if (!hasOpenPassword() && !restrictsAnything()) {
            return "There is nothing to protect yet.\nSet a password to open the file, or restrict at least one action.";
        }
        if (restrictsAnything() && !hasPermissionsPassword()) {
            return "Restrictions need a permissions password.\nWithout one, anyone could simply remove them.";
        }
        if (keyBits != 256 && (nonLatin1(openPassword) || nonLatin1(permissionsPassword))) {
            return "AES-128 only supports Western (Latin-1) characters in passwords.\nChoose AES-256, or use a simpler password.";
        }
        return null;
    }

    /** True when both passwords are the same, which makes the restrictions pointless for anyone who can open the file. */
    public boolean passwordsMatch() {
        return hasOpenPassword() && Arrays.equals(openPassword, permissionsPassword);
    }

    private static boolean nonLatin1(char[] pw) {
        for (char c : pw) if (c > 0xFF) return true;
        return false;
    }

    public AccessPermission toPermission() {
        AccessPermission ap = new AccessPermission();
        ap.setCanPrint(allowed[0]);
        ap.setCanPrintFaithful(allowed[1]);
        ap.setCanModify(allowed[2]);
        ap.setCanExtractContent(allowed[3]);
        ap.setCanExtractForAccessibility(allowed[4]);
        ap.setCanFillInForm(allowed[5]);
        ap.setCanModifyAnnotations(allowed[6]);
        ap.setCanAssembleDocument(allowed[7]);
        return ap;
    }

    public StandardProtectionPolicy toPolicy() {
        // PDFBox uses the open password for the permissions password when none is given.
        StandardProtectionPolicy policy = new StandardProtectionPolicy(
                new String(permissionsPassword), new String(openPassword), toPermission());
        policy.setEncryptionKeyLength(keyBits);
        policy.setPreferAES(true);
        return policy;
    }

    /** The password to reopen the saved file with: the permissions password (full control) if there is one. */
    public String reopenPassword() {
        if (hasPermissionsPassword()) return new String(permissionsPassword);
        return hasOpenPassword() ? new String(openPassword) : null;
    }

    /** Overwrites the password arrays (best effort: Strings made from them can't be wiped). */
    public void wipe() {
        Arrays.fill(openPassword, '\0');
        Arrays.fill(permissionsPassword, '\0');
    }
}
