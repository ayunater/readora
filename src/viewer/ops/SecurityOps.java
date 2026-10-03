package viewer.ops;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import viewer.PdfSession;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * Passwords and permissions. Every change is written through
 * {@link PdfSession#rewrite}, which saves to a temporary file first and
 * only then replaces the original. The old code deleted the original
 * <i>before</i> renaming, so a failed rename lost the user's PDF.
 */
public final class SecurityOps extends Ops {

    private static final String[] PERMISSIONS = {
            "Allow Printing", "Allow Modify PDF", "Allow Extract Text", "Allow Accessibility Extraction",
            "Allow Fill Forms", "Allow Modify Annotations", "Allow Assemble Document"
    };

    public SecurityOps(PdfSession session, Component parent) { super(session, parent); }

    // ------------------------------------------------------------------
    // Add / change / remove password
    // ------------------------------------------------------------------

    public void addPassword() {
        if (!requireDocument()) return;
        protectWithDialog("Set PDF Password", "User Password:", "Owner Password:", "Password added successfully.");
    }

    public void changePassword() {
        if (!requireDocument()) return;
        protectWithDialog("Change PDF Password", "New Password:", "Owner Password:", "Password changed successfully.");
    }

    private void protectWithDialog(String title, String userLabel, String ownerLabel, String successMessage) {
        JPasswordField user = new JPasswordField(), owner = new JPasswordField();
        JCheckBox[] boxes = permissionBoxes(null);
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 8));
        panel.add(new JLabel(userLabel));
        panel.add(user);
        panel.add(new JLabel(ownerLabel));
        panel.add(owner);
        panel.add(new JLabel("Permissions (User Password):"));
        panel.add(new JLabel());
        for (JCheckBox b : boxes) { panel.add(b); panel.add(new JLabel()); }

        if (JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String u = new String(user.getPassword()), o = new String(owner.getPassword());
        if (u.isEmpty() || o.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Password cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (u.equals(o) && JOptionPane.showConfirmDialog(parent, "User and Owner passwords are the same.\nContinue?",
                "Warning", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        applyProtection(u, o, boxes, successMessage);
    }

    private void applyProtection(String userPw, String ownerPw, JCheckBox[] boxes, String success) {
        try {
            // Reopen with the owner password so the user keeps full control of the file they just secured.
            session.rewrite(doc -> protect(doc, userPw, ownerPw, boxes), ownerPw);
            info(success, "Security");
        } catch (IOException ex) {
            error("Could not update the PDF's security. Your original file is unchanged", ex);
        }
    }

    private static void protect(PDDocument doc, String userPw, String ownerPw, JCheckBox[] b) throws IOException {
        AccessPermission ap = new AccessPermission();
        ap.setCanPrint(b[0].isSelected());
        ap.setCanModify(b[1].isSelected());
        ap.setCanExtractContent(b[2].isSelected());
        ap.setCanExtractForAccessibility(b[3].isSelected());
        ap.setCanFillInForm(b[4].isSelected());
        ap.setCanModifyAnnotations(b[5].isSelected());
        ap.setCanAssembleDocument(b[6].isSelected());
        StandardProtectionPolicy policy = new StandardProtectionPolicy(ownerPw, userPw, ap);
        policy.setEncryptionKeyLength(256);
        doc.protect(policy);
    }

    public void removePassword() {
        if (!requireDocument()) return;
        if (JOptionPane.showConfirmDialog(parent, "Remove password protection from this PDF?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            session.rewrite(doc -> doc.setAllSecurityToBeRemoved(true), null);
            info("Password removed successfully.", "Security");
        } catch (IOException ex) {
            error("Could not remove the password. Your original file is unchanged", ex);
        }
    }

    // ------------------------------------------------------------------
    // Inspect / edit permissions
    // ------------------------------------------------------------------

    public void securityInformation() {
        if (!requireDocument()) return;
        PDDocument d = session.document();
        AccessPermission p = d.getCurrentAccessPermission();
        info("""
                Encrypted: %s
                Permissions
                -------------------------
                Print: %s
                Modify: %s
                Extract Content: %s
                Accessibility Extract: %s
                Fill Forms: %s
                Modify Annotations: %s
                Assemble Document: %s
                Owner Access: %s
                """.formatted(d.isEncrypted() ? "Yes" : "No", yn(p.canPrint()), yn(p.canModify()),
                yn(p.canExtractContent()), yn(p.canExtractForAccessibility()), yn(p.canFillInForm()),
                yn(p.canModifyAnnotations()), yn(p.canAssembleDocument()), p.isOwnerPermission() ? "Yes" : "No"),
                "Security Information");
    }

    private static String yn(boolean b) { return b ? "Allowed" : "Not Allowed"; }

    public void editPermissions() {
        if (!requireDocument()) return;
        PDDocument d = session.document();
        AccessPermission current = d.getCurrentAccessPermission();
        if (!current.isOwnerPermission()) {
            JOptionPane.showMessageDialog(parent, """
                    This PDF was opened using the user password.
                    Reopen the PDF using the owner password
                    to edit its security permissions.
                    """, "Owner Permission Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JPasswordField user = new JPasswordField(), owner = new JPasswordField(
                d.isEncrypted() && session.password() != null ? session.password() : "");
        JCheckBox[] boxes = permissionBoxes(current);
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 8));
        panel.add(new JLabel("User Password:"));
        panel.add(user);
        panel.add(new JLabel("Owner Password:"));
        panel.add(owner);
        for (JCheckBox b : boxes) { panel.add(b); panel.add(new JLabel()); }

        if (JOptionPane.showConfirmDialog(parent, panel, "Edit PDF Permissions", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String u = new String(user.getPassword()), o = new String(owner.getPassword());
        if (u.isEmpty() || o.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Passwords cannot be empty.");
            return;
        }
        if (u.equals(o) && JOptionPane.showConfirmDialog(parent, "User and Owner passwords are the same.\nContinue?",
                "Warning", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        applyProtection(u, o, boxes, "Permissions updated successfully.");
    }

    private static JCheckBox[] permissionBoxes(AccessPermission initial) {
        boolean[] on = initial == null ? new boolean[]{true, true, true, true, true, true, true}
                : new boolean[]{initial.canPrint(), initial.canModify(), initial.canExtractContent(),
                initial.canExtractForAccessibility(), initial.canFillInForm(), initial.canModifyAnnotations(),
                initial.canAssembleDocument()};
        JCheckBox[] boxes = new JCheckBox[PERMISSIONS.length];
        for (int i = 0; i < boxes.length; i++) boxes[i] = new JCheckBox(PERMISSIONS[i], on[i]);
        return boxes;
    }
}
