package viewer;

import dialogs.PageOrganizerDialog;
import rightpanel.recent;
import rightpanel.starred;
import store.AppSettings;
import ui.Ui;
import viewer.ops.*;

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

        // ---- layout ----
        add(header, BorderLayout.NORTH);
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
            JOptionPane.showMessageDialog(this, "Could not open \"" + f.getName() + "\":\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Forgets the remembered password before a different file is opened. */
    public void resetPdfPassword() { session.forgetPassword(); }

    private void choosePdf() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        lastDirectory = f.getParentFile();
        safeOpen(f);
    }

    private void reloadCurrent() {
        if (!session.isOpen()) return;
        try {
            session.reload(this);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not reload the file:\n" + ex.getMessage(),
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
