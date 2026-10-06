package viewer;

import ui.Dialogs;
import ui.AppFileChooser;
import dialogs.PageOrganizerDialog;
import rightpanel.recent;
import rightpanel.starred;
import store.AppSettings;
import ui.Ui;
import viewer.edit.*;
import viewer.ops.*;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import ui.AppFileChooser;
import ui.Dialogs;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * The PDF viewer screen.
 *
 * <p>This class only <b>assembles</b> the pieces and exposes the public
 * entry points the rest of the app calls (Tools, Recent, Main). The work
 * lives in focused classes:</p>
 * <ul>
 *   <li>{@link PdfSession} &ndash; the open document, password and safe file rewrite</li>
 *   <li>{@link RenderService} &ndash; background, cached, bounded page rendering</li>
 *   <li>{@link DocumentView} / {@link PageCanvas} &ndash; zoom, fit, scrolling, page drawing</li>
 *   <li>{@link ThumbnailPanel} &ndash; page thumbnails</li>
 *   <li>{@link ViewerHeader} / {@link ViewerControls} &ndash; the top and bottom bars</li>
 *   <li>{@code viewer.ops.*} &ndash; export, watermark, security, search, print, ...</li>
 * </ul>
 */
public class PdfViewer extends JPanel {

    private static File lastDirectory;

    private final PdfSession session = new PdfSession();
    private final RenderService render;
    private final DocumentView docView;
    private final ThumbnailPanel thumbs;
    private final ViewerHeader header = new ViewerHeader();
    private final ViewerControls controls = new ViewerControls();

    private final InfoOps info;
    private final ExportOps export;
    private final WatermarkOps watermark;
    private final PageOps pages;
    private final SecurityOps security;
    private final SearchOps search;
    private final PrintOps print;

    // ---- editing ----
    private final EditModel editModel = new EditModel();
    private final EditProps editProps = new EditProps();
    private final EditOverlay editOverlay = new EditOverlay(editModel);
    private final EditController editCtl;
    private final EditToolbar editBar;
    private boolean editing;
    private boolean savingEdits;

    private File shownFile;

    public PdfViewer() {
        setLayout(new BorderLayout(0, Ui.GAP));
        setOpaque(false);
        Ui.padScreen(this);

        RenderService.warmUp();
        render = new RenderService(session, this::onRendered);
        docView = new DocumentView(render);
        thumbs = new ThumbnailPanel(render);

        info = new InfoOps(session, this);
        export = new ExportOps(session, this);
        watermark = new WatermarkOps(session, this, this::openGeneratedPDF);
        pages = new PageOps(session, this);
        security = new SecurityOps(session, this);
        search = new SearchOps(session, this, docView::setHighlights, docView::showHighlight);
        print = new PrintOps(session, this);

        editCtl = new EditController(docView.pageHost(), editModel, editOverlay, editProps);
        editCtl.onSave(this::saveEdits);
        editBar = new EditToolbar(editCtl, editModel, editProps, new EditToolbar.Actions() {
            @Override public void addPage() { addBlankPage(); }
            @Override public void saveCopy() { saveEditedCopy(); }
            @Override public void save() { saveEdits(); }
            @Override public void done() { setEditing(false); }
        });
        editBar.setVisible(false);
        docView.setOverlay(editOverlay);

        // ---- layout ----
        JPanel north = new JPanel(new BorderLayout(0, Ui.GAP));
        north.setOpaque(false);
        north.add(header, BorderLayout.NORTH);
        north.add(editBar, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(Ui.GAP, 0));
        body.setOpaque(false);
        body.add(thumbs, BorderLayout.WEST);
        body.add(docView, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        wire();
        session.addChangeListener(this::onDocumentChanged);
        PdfDropHandler.install(this, this::safeOpen);
        PdfDropHandler.install(docView.canvas(), this::safeOpen);
    }

    /** A background render finished: repaint whatever may be showing it. */
    private void onRendered() {
        docView.repaintPages();
        thumbs.repaintThumbs();
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private void wire() {
        header.open.addActionListener(e -> choosePdf());
        header.organize.addActionListener(e -> openPageOrganizer());
        header.edit.addActionListener(e -> setEditing(header.edit.isSelected()));
        header.star.addActionListener(e -> toggleStar());
        header.reload.addActionListener(e -> reloadCurrent());

        controls.pages.addActionListener(e -> {
            thumbs.setVisible(controls.pages.isSelected());
            revalidate();
        });
        controls.prev.addActionListener(e -> docView.prevPage());
        controls.next.addActionListener(e -> docView.nextPage());
        controls.onGoto(docView::goToPage);
        controls.zoomIn.addActionListener(e -> docView.zoomIn());
        controls.zoomOut.addActionListener(e -> docView.zoomOut());
        controls.fitWidth.addActionListener(e -> docView.fitWidth());
        controls.fitPage.addActionListener(e -> docView.fitPage());
        controls.zoomField.addActionListener(e -> {
            try {
                String t = controls.zoomField.getText().replace("%", "").trim();
                docView.setZoom(Double.parseDouble(t) / 100.0);
            } catch (NumberFormatException ex) {
                controls.showZoom(docView.zoom());
            }
        });

        docView.onPageChanged(p -> {
            controls.showPage(p, docView.pageCount());
            thumbs.setCurrentPage(p);
        });
        docView.onZoomChanged(controls::showZoom);
        thumbs.onPageSelected(docView::goToPage);
    }

    /** Called whenever the session's document is opened, replaced (e.g. after an edit) or closed. */
    private void onDocumentChanged() {
        if (!session.isOpen()) {
            editModel.clear();
            if (editing) leaveEditMode();
            render.clear();
            docView.clear();
            thumbs.setDocument(PageGeometry.empty());
            header.showNothing();
            controls.setEnabledAll(false);
            controls.showPage(0, 0);
            shownFile = null;
            return;
        }
        PageGeometry geo;
        session.lock().lock();
        try {
            geo = PageGeometry.of(session.document());
        } finally {
            session.lock().unlock();
        }
        boolean sameFile = session.file().equals(shownFile);
        if (!sameFile || savingEdits) editModel.clear();         // edits belong to one document; after a save they are in the file
        render.clear();
        docView.setHighlights(java.util.List.of(), null);
        docView.setDocument(geo, sameFile);
        thumbs.setDocument(geo);
        if (sameFile) thumbs.setCurrentPage(docView.currentPage());
        header.showFile(session.file(), geo.count());
        header.setStarred(starred.STORE.contains(session.file()));
        controls.setEnabledAll(true);
        controls.showPage(Math.min(docView.currentPage(), Math.max(0, geo.count() - 1)), geo.count());
        shownFile = session.file();
    }

    // ------------------------------------------------------------------
    // Opening files
    // ------------------------------------------------------------------

    /** Opens a PDF, asking for a password if needed. Adds it to Recent unless disabled in Settings. */
    public void openPdf(File file) throws IOException {
        if (!confirmEditsHandled()) return;
        boolean opened = session.open(file, this);
        if (opened && AppSettings.rememberRecent()) recent.STORE.add(file);
    }

    /** Opens a file the app just produced (split/extract/watermark output). */
    public void openGeneratedPDF(File file) {
        safeOpen(file);
    }

    private void safeOpen(File f) {
        try {
            session.forgetPassword();
            openPdf(f);
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not open \"" + f.getName() + "\":\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Forgets the remembered password before a different file is opened. */
    public void resetPdfPassword() { session.forgetPassword(); }

    private void choosePdf() {
        JFileChooser chooser = new AppFileChooser(lastDirectory);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        lastDirectory = f.getParentFile();
        safeOpen(f);
    }

    private void reloadCurrent() {
        if (!session.isOpen() || !confirmEditsHandled()) return;
        try {
            session.reload(this);
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not reload the file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleStar() {
        if (!session.isOpen()) return;
        File f = session.file();
        if (starred.STORE.contains(f)) starred.STORE.remove(f); else starred.STORE.add(f);
        header.setStarred(starred.STORE.contains(f));
    }

    /** Stops background rendering and closes the document (app shutdown). */
    public void shutdown() {
        render.shutdown();
        session.close();
    }

    // ------------------------------------------------------------------
    // Editing: add text, pictures, shapes and drawings; create new PDFs
    // ------------------------------------------------------------------

    /** True while there are edits that are not saved yet. */
    public boolean hasUnsavedEdits() { return editModel.isDirty(); }

    /** Turns edit mode on or off (off asks what to do with unsaved edits). */
    public void setEditing(boolean on) {
        if (on == editing) return;
        if (on) {
            if (!session.isOpen()) {
                Dialogs.showMessageDialog(this, "Open a PDF first, or create one with New PDF.", "Edit",
                        JOptionPane.INFORMATION_MESSAGE);
                header.edit.setSelected(false);
                return;
            }
            editing = true;
            docView.setPanEnabled(false);
            docView.setHighlights(java.util.List.of(), null);
            editCtl.attach();
            editBar.setVisible(true);
            editCtl.setTool(EditController.Tool.SELECT);
            docView.canvas().requestFocusInWindow();
        } else {
            if (!confirmEditsHandled()) {
                header.edit.setSelected(true);
                return;
            }
            leaveEditMode();
        }
        header.edit.setSelected(editing);
        revalidate();
        repaint();
    }

    private void leaveEditMode() {
        editing = false;
        editCtl.detach();
        docView.setPanEnabled(true);
        editBar.setVisible(false);
        header.edit.setSelected(false);
        revalidate();
    }

    /**
     * If there are unsaved edits, asks to save or discard them.
     *
     * @return false if the caller should stop (the user cancelled, or saving failed)
     */
    public boolean confirmEditsHandled() {
        if (!editModel.isDirty()) return true;
        String name = session.file() == null ? "this PDF" : session.file().getName();
        int r = Dialogs.showConfirmDialog(this, "You have unsaved edits to " + name + ".\nSave them before continuing?",
                "Unsaved edits", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) return saveEdits();
        if (r == JOptionPane.NO_OPTION) { editModel.clear(); return true; }
        return false;
    }

    /** Writes the pending edits into the open PDF (safely: the original is replaced only on success). */
    public boolean saveEdits() {
        if (!editModel.isDirty()) return true;
        if (!session.isOpen()) return false;
        EditWriter.Result[] result = new EditWriter.Result[1];
        java.util.List<EditItem> items = new java.util.ArrayList<>(editModel.items());
        savingEdits = true;
        try {
            session.rewrite(doc -> result[0] = EditWriter.apply(doc, items), session.password());
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not save your edits. The file is unchanged and your edits are still here.\n\n"
                    + ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            savingEdits = false;
        }
        editModel.clear();
        reportSave(result[0], "Your edits were saved.", false);
        return true;
    }

    /** Shows warnings / notices from a save; with {@code always} also confirms plain success. */
    private void reportSave(EditWriter.Result r, String headline, boolean always) {
        StringBuilder msg = new StringBuilder();
        if (r != null) for (String w : r.warnings) msg.append("\u2022 ").append(w).append('\n');
        String notice = session.takeNotice();
        if (notice != null) msg.append(msg.length() > 0 ? "\n" : "").append(notice);
        if (msg.length() > 0 || always) {
            Dialogs.showMessageDialog(this, msg.length() > 0 ? headline + "\n\n" + msg.toString().strip() : headline, "Saved",
                    r != null && !r.warnings.isEmpty() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** "Save a copy": the edits go into a new file; this PDF and its pending edits are left as they are. */
    private void saveEditedCopy() {
        if (!session.isOpen() || !editModel.isDirty()) {
            Dialogs.showMessageDialog(this, "There are no unsaved edits to put in a copy.", "Save a copy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        AppFileChooser chooser = new AppFileChooser(session.file().getParentFile());
        chooser.setDialogTitle("Save a copy as");
        chooser.setSelectedFile(new File(session.file().getParentFile(),
                session.file().getName().replaceFirst("[.][^.]+$", "") + "-edited.pdf"));
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files", "pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = chooser.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".pdf")) out = new File(out.getParentFile(), out.getName() + ".pdf");
        if (out.getAbsoluteFile().equals(session.file().getAbsoluteFile())) {
            Dialogs.showMessageDialog(this, "Choose a different name, or use Save to write into this file.", "Save a copy", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (out.exists() && Dialogs.showConfirmDialog(this, out.getName() + " already exists. Replace it?", "Confirm overwrite",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        EditWriter.Result[] result = new EditWriter.Result[1];
        java.util.List<EditItem> items = new java.util.ArrayList<>(editModel.items());
        try {
            session.saveCopyTo(out, doc -> result[0] = EditWriter.apply(doc, items));
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not save the copy: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        reportSave(result[0], "Saved a copy as " + out.getName() + ".\nThis PDF still has your unsaved edits.", true);
    }

    /** Inserts a blank page after the current one, the same size as it. */
    public void addBlankPage() {
        if (!session.isOpen()) {
            Dialogs.showMessageDialog(this, "Open a PDF first.", "Add page", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!saveEdits()) return;                       // page numbers shift, so settle pending edits first
        final int at = Math.max(0, docView.currentPage());
        try {
            session.rewrite(doc -> {
                PDPage current = doc.getPage(at);
                float[] wh = PageGeometry.sizeOf(current);
                doc.getPages().insertAfter(new PDPage(new PDRectangle(wh[0], wh[1])), current);
            }, session.password());
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not add a page: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> docView.goToPage(at + 1));
    }

    /** Creates a blank PDF (asking for size and location) and opens it ready to edit. */
    public void newPdf() {
        if (!confirmEditsHandled()) return;
        File created = CreateOps.createBlankPdf(this);
        if (created == null) return;
        try {
            session.forgetPassword();
            openPdf(created);
            setEditing(true);
        } catch (IOException ex) {
            Dialogs.showMessageDialog(this, "Could not open the new PDF: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Page organizer entry points
    // ------------------------------------------------------------------

    public void openPageOrganizer()          { pages.openOrganizer(PageOrganizerDialog.Mode.ORGANIZE); }
    public void openPageOrganizerForReorder() { pages.openOrganizer(PageOrganizerDialog.Mode.REORDER); }
    public void openPageOrganizerForRotate()  { pages.openOrganizer(PageOrganizerDialog.Mode.ROTATE); }
    public void openPageOrganizerForDelete()  { pages.openOrganizer(PageOrganizerDialog.Mode.DELETE); }
    public void openPageOrganizerForExtract() { pages.openOrganizer(PageOrganizerDialog.Mode.EXTRACT); }
    public void removePages()   { openPageOrganizerForDelete(); }
    public void reorderPages()  { openPageOrganizerForReorder(); }
    public void rotatePages()   { openPageOrganizerForRotate(); }

    // ------------------------------------------------------------------
    // Operations (thin delegates; signatures kept for the Tools screen)
    // ------------------------------------------------------------------

    public void splitPDF() throws IOException           { pages.splitPdf(); }
    public void showMetadata()                          { info.showMetadata(); }
    public void editMetadata() throws IOException       { info.editMetadata(); }
    public void showPdfInfo()                           { info.showPdfInfo(); }
    public void showPageCount()                         { info.showPageCount(); }
    public void listFonts()                             { info.listFonts(); }
    public void pdfToPng() throws IOException           { export.pdfToPng(); }
    public void pdfToJpg() throws IOException           { export.pdfToJpg(); }
    public void exportText() throws IOException         { export.exportText(); }
    public void extractImages() throws IOException     { export.extractImages(); }
    public void addTextWatermark() throws IOException   { watermark.addTextWatermark(); }
    public void addImageWatermark() throws IOException  { watermark.addImageWatermark(); }
    public void addPassword()                           { security.addPassword(); }
    public void changePassword()                        { security.changePassword(); }
    public void removePassword()                        { security.removePassword(); }
    public void securityInformation()                   { security.securityInformation(); }
    public void editPermissions()                       { security.editPermissions(); }
    public void searchTextThroughPdf() throws IOException { search.search(); }
    public void printPdf()                              { print.printPdf(); }

    /** Test/automation hook: the currently open file, or null. */
    public File currentFile() { return session.file(); }
}
