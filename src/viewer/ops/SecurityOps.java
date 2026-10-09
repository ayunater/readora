package viewer.ops;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import rightpanel.AppFonts;
import ui.Dialogs;
import ui.SvgIcon;
import ui.Ui;
import viewer.PdfSession;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Everything about protecting a PDF: passwords, restrictions, inspecting what protection a file has, and
 * removing hidden data.
 *
 * <p>All changes go through {@link PdfSession#rewrite}, which saves to a temporary file first and replaces the
 * original only after a successful write. Changing or removing protection requires the file's <b>permissions
 * (owner) password</b>: someone who only knows the open password must not be able to strip the restrictions
 * the owner set.</p>
 */
public final class SecurityOps extends Ops {

    public SecurityOps(PdfSession session, Component parent) { super(session, parent); }

    // ------------------------------------------------------------------
    // Protect / change / permissions
    // ------------------------------------------------------------------

    public void addPassword()     { protect(SecurityDialog.Mode.ADD); }
    public void changePassword()  { protect(SecurityDialog.Mode.CHANGE); }
    public void editPermissions() { protect(SecurityDialog.Mode.PERMISSIONS); }

    private void protect(SecurityDialog.Mode mode) {
        if (!requireDocument()) return;
        boolean encrypted = session.document().isEncrypted();
        if (encrypted && !session.unlockOwner(parent)) return;
        AccessPermission now = session.document().getCurrentAccessPermission();
        ProtectionOptions initial = encrypted ? ProtectionOptions.from(now) : new ProtectionOptions();

        ProtectionOptions o = SecurityDialog.ask(parent, mode, initial);
        if (o == null) return;
        try {
            session.rewrite(doc -> doc.protect(o.toPolicy()), o.reopenPassword());
            info(summary(o), "Protection saved");
        } catch (IOException ex) {
            error("Could not protect the PDF. Your original file is unchanged", ex);
        } finally {
            o.wipe();
        }
    }

    private static String summary(ProtectionOptions o) {
        StringBuilder sb = new StringBuilder();
        sb.append(o.hasOpenPassword() ? "A password is now needed to open this file." : "Anyone can open this file.").append('\n');
        if (o.restrictsAnything()) {
            sb.append("Not allowed without the permissions password:\n");
            for (int i = 0; i < o.allowed.length; i++) if (!o.allowed[i]) sb.append("   \u2022 ").append(ProtectionOptions.PERMISSIONS[i]).append('\n');
        } else {
            sb.append("Nothing is restricted.\n");
        }
        sb.append("Encryption: AES-").append(o.keyBits).append('.');
        return sb.toString();
    }

    public void removePassword() {
        if (!requireDocument()) return;
        if (!session.document().isEncrypted()) {
            info("This PDF is not password protected.", "Remove password");
            return;
        }
        if (!session.unlockOwner(parent)) return;
        if (Dialogs.showConfirmDialog(parent,
                "Remove all password protection and restrictions from this PDF?\n\nAnyone will be able to open, copy and change it.",
                "Remove password", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            session.rewrite(doc -> doc.setAllSecurityToBeRemoved(true), null);
            info("The file is no longer protected.", "Remove password");
        } catch (IOException ex) {
            error("Could not remove the protection. Your original file is unchanged", ex);
        }
    }

    // ------------------------------------------------------------------
    // Security information
    // ------------------------------------------------------------------

    public void securityInformation() {
        if (!requireDocument()) return;
        try {
            JComponent panel = infoPanel();
            Dialogs.showMessageDialog(parent, panel, "Security information", JOptionPane.PLAIN_MESSAGE);
        } catch (IOException ex) {
            error("Could not read the security information", ex);
        }
    }

    private JComponent infoPanel() throws IOException {
        PDDocument d = session.document();
        boolean enc = d.isEncrypted();
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        int y = 0;

        // headline
        String headline, sub;
        boolean weak = false;
        String encText = "Not encrypted";
        if (enc) {
            PDEncryption e = d.getEncryption();
            String[] desc = describe(e);
            encText = desc[0];
            weak = "weak".equals(desc[1]);
            AccessPermission file = new AccessPermission(e.getPermissions());
            boolean restricted = !(file.canPrint() && file.canModify() && file.canExtractContent() && file.canAssembleDocument());
            headline = restricted ? "Protected and restricted" : "Password protected";
            sub = weak ? "The encryption used is outdated; re-protect it with AES-256." : "The contents are encrypted.";
        } else {
            headline = "Not protected";
            sub = "Anyone can open, copy and change this file.";
        }
        JLabel title = new JLabel(headline, Ui.icon(enc && !weak ? "shield-check" : "shield-alert", 34, SvgIcon.Tint.BRAND), SwingConstants.LEADING);
        title.setIconTextGap(12);
        title.setFont(AppFonts.body(Font.BOLD, 20));
        c.gridx = 0; c.gridy = y++; c.gridwidth = 2; c.insets = new Insets(0, 0, 2, 0);
        p.add(title, c);
        JLabel subL = new JLabel(sub);
        subL.setForeground(Ui.muted());
        subL.setFont(AppFonts.body(Font.PLAIN, 13));
        c.gridy = y++; c.insets = new Insets(0, 46, 14, 0);
        p.add(subL, c);

        y = row(p, y, "lock", "Encryption", encText, weak ? "bad" : enc ? "ok" : "neutral");
        if (enc) {
            AccessPermission mine = d.getCurrentAccessPermission();
            y = row(p, y, "key-round", "Your access",
                    mine.isOwnerPermission() ? "Full: nothing is restricted for you" : "Limited: only what the owner permits", "neutral");
            AccessPermission file = new AccessPermission(d.getEncryption().getPermissions());
            boolean[] allowed = {file.canPrint(), file.canPrintFaithful(), file.canModify(), file.canExtractContent(),
                    file.canExtractForAccessibility(), file.canFillInForm(), file.canModifyAnnotations(), file.canAssembleDocument()};
            JLabel h = new JLabel("What the file allows");
            h.setFont(AppFonts.body(Font.BOLD, 13));
            c.gridx = 0; c.gridy = y++; c.gridwidth = 2; c.insets = new Insets(12, 0, 4, 0);
            p.add(h, c);
            for (int i = 0; i < allowed.length; i++) y = row(p, y, allowed[i] ? "check" : "x", ProtectionOptions.PERMISSIONS[i], allowed[i] ? "Allowed" : "Blocked", allowed[i] ? "ok" : "bad");
        }

        // what else is inside
        JLabel h2 = new JLabel("Hidden data in this file");
        h2.setFont(AppFonts.body(Font.BOLD, 13));
        c.gridx = 0; c.gridy = y++; c.gridwidth = 2; c.insets = new Insets(14, 0, 4, 0);
        p.add(h2, c);
        try (PDDocument copy = session.openCopy()) {
            Sanitizer.Report r = Sanitizer.scan(copy);
            y = row(p, y, r.hasMetadata() ? "triangle-alert" : "check", "Metadata",
                    r.hasMetadata() ? String.join(", ", r.infoFields) + (r.xmp ? (r.infoFields.isEmpty() ? "" : ", ") + "XMP" : "") : "None", r.hasMetadata() ? "warn" : "ok");
            y = row(p, y, r.attachments > 0 ? "triangle-alert" : "check", "Attached files", r.attachments > 0 ? String.valueOf(r.attachments) : "None", r.attachments > 0 ? "warn" : "ok");
            y = row(p, y, r.scripts > 0 ? "triangle-alert" : "check", "Scripts and automatic actions", r.scripts > 0 ? String.valueOf(r.scripts) : "None", r.scripts > 0 ? "warn" : "ok");
            y = row(p, y, r.comments > 0 ? "triangle-alert" : "check", "Comments and markup", r.comments > 0 ? String.valueOf(r.comments) : "None", r.comments > 0 ? "warn" : "ok");
            y = row(p, y, "file-text", "Bookmarks", r.bookmarks ? "Yes" : "None", "neutral");
            int sigs = copy.getSignatureDictionaries().size();
            y = row(p, y, sigs > 0 ? "shield-check" : "file-text", "Digital signatures", sigs > 0 ? String.valueOf(sigs) : "None", "neutral");
        }
        JLabel tip = new JLabel("<html><div style='width:380px'>Use <b>Tools \u2192 Remove Hidden Data</b> to clean a copy of this file before sharing it.</div></html>");
        tip.setForeground(Ui.muted());
        tip.setFont(AppFonts.body(Font.PLAIN, 12));
        c.gridx = 0; c.gridy = y; c.gridwidth = 2; c.insets = new Insets(12, 0, 0, 0);
        p.add(tip, c);
        return p;
    }

    private static int row(JPanel p, int y, String icon, String label, String value, String tone) {
        SvgIcon ic = switch (tone) {
            case "ok" -> Ui.icon(icon, 16, SvgIcon.Tint.ACCENT);
            case "bad", "warn" -> Ui.icon(icon, 16, SvgIcon.Tint.BRAND);
            default -> Ui.icon(icon, 16, SvgIcon.Tint.MUTED);
        };
        JLabel l = new JLabel(label, ic, SwingConstants.LEADING);
        l.setIconTextGap(10);
        l.setFont(AppFonts.body(Font.PLAIN, 13));
        JLabel v = new JLabel("<html><div style='width:230px'>" + value.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
        v.setFont(AppFonts.body(tone.equals("bad") ? Font.BOLD : Font.PLAIN, 13));
        if (tone.equals("bad")) v.setForeground(new Color(0xE0334F));
        else if (tone.equals("ok")) v.setForeground(new Color(0x2EC27E));
        else if (tone.equals("warn")) v.setForeground(new Color(0xFF9F1C));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = y; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(2, 0, 2, 24);
        p.add(l, c);
        c.gridx = 1; c.weightx = 1; c.insets = new Insets(2, 0, 2, 0);
        p.add(v, c);
        return y + 1;
    }

    /** {encryption description, "weak" or "ok"} */
    static String[] describe(PDEncryption e) {
        int version = e.getVersion(), revision = e.getRevision(), bits = e.getLength();
        if (version >= 5 || revision >= 5) return new String[]{"AES-256", "ok"};
        if (version == 4) {
            try {
                var f = e.getStdCryptFilterDictionary();
                COSName m = f == null ? null : f.getCryptFilterMethod();
                if (m != null && "AESV2".equals(m.getName())) return new String[]{"AES-128", "ok"};
                if (m != null && "AESV3".equals(m.getName())) return new String[]{"AES-256", "ok"};
            } catch (RuntimeException ignored) { /* fall through */ }
            return new String[]{"RC4 128-bit (outdated)", "weak"};
        }
        return new String[]{"RC4 " + (bits > 0 ? bits : 40) + "-bit " + (bits >= 128 ? "(outdated)" : "(very weak)"), "weak"};
    }

    // ------------------------------------------------------------------
    // Remove hidden data
    // ------------------------------------------------------------------

    public void removeHiddenData() {
        if (!requireDocument()) return;
        if (session.document().isEncrypted() && !session.unlockOwner(parent)) return;
        Sanitizer.Report rep;
        try (PDDocument copy = session.openCopy()) {
            rep = Sanitizer.scan(copy);
        } catch (IOException ex) {
            error("Could not inspect the PDF", ex);
            return;
        }
        if (rep.isClean()) {
            info("No hidden data was found: this file has no metadata, attachments, scripts, comments, bookmarks or thumbnails.", "Remove hidden data");
            return;
        }

        JCheckBox meta = box("Metadata \u2013 author, title, software and dates"
                + (rep.hasMetadata() ? "  (" + String.join(", ", rep.infoFields) + (rep.xmp ? ", XMP" : "") + ")" : ""), rep.hasMetadata() || rep.thumbnails > 0);
        JCheckBox att = box("Attached files  (" + rep.attachments + ")", rep.attachments > 0);
        JCheckBox scr = box("Scripts and automatic actions  (" + rep.scripts + ")", rep.scripts > 0);
        JCheckBox com = box("Comments and markup \u2013 notes, highlights, stamps  (" + rep.comments + ")", rep.comments > 0);
        JCheckBox bkm = box("Bookmarks", rep.bookmarks);
        JComboBox<String> where = new JComboBox<>(new String[]{"Save a cleaned copy  (recommended)", "Clean this file in place"});
        Ui.styleCombo(where);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.setOpaque(false);
        JLabel intro = new JLabel("<html><div style='width:430px'>This file carries information that is not visible on the page. "
                + "Choose what to remove before you share it. Links and form fields are always kept.</div></html>");
        intro.setForeground(Ui.muted());
        intro.setFont(AppFonts.body(Font.PLAIN, 13));
        form.add(intro);
        form.add(Box.createVerticalStrut(6));
        for (JCheckBox b : new JCheckBox[]{meta, att, scr, com, bkm}) form.add(b);
        form.add(Box.createVerticalStrut(8));
        form.add(where);

        if (Dialogs.showConfirmDialog(parent, form, "Remove hidden data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        Sanitizer.Options opt = new Sanitizer.Options(meta.isSelected(), att.isSelected(), scr.isSelected(), com.isSelected(), bkm.isSelected());
        if (!(opt.metadata() || opt.attachments() || opt.scripts() || opt.comments() || opt.bookmarks())) return;

        try {
            if (where.getSelectedIndex() == 0) {
                File out = chooseSave("Save the cleaned copy as", baseName(session.file()) + "-clean.pdf", "PDF files", "pdf");
                if (out == null) return;
                if (out.getAbsoluteFile().equals(session.file().getAbsoluteFile())) {
                    info("Choose a different name, or pick \"Clean this file in place\".", "Remove hidden data");
                    return;
                }
                session.saveCopyTo(out, doc -> Sanitizer.apply(doc, opt));
                info("Saved a cleaned copy:\n" + out.getName(), "Remove hidden data");
            } else {
                if (Dialogs.showConfirmDialog(parent, "This replaces the file on disk with the cleaned version.\nThe removed data cannot be recovered.\n\nContinue?",
                        "Remove hidden data", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
                session.rewrite(doc -> Sanitizer.apply(doc, opt), session.password());
                String notice = session.takeNotice();
                info("Hidden data was removed from this file." + (notice == null ? "" : "\n\n" + notice), "Remove hidden data");
            }
        } catch (IOException ex) {
            error("Could not clean the PDF. Your original file is unchanged", ex);
        }
    }

    private static JCheckBox box(String text, boolean found) {
        JCheckBox b = new JCheckBox(text, found);
        b.setEnabled(found);
        b.setOpaque(false);
        b.setFont(AppFonts.body(Font.PLAIN, 14));
        return b;
    }
}
