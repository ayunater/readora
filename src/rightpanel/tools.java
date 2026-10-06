package rightpanel;

import ui.Dialogs;
import ui.AppFileChooser;
import javax.imageio.ImageIO;
import javax.swing.*;
import viewer.PdfViewer;
import ui.SvgIcon;
import ui.Ui;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;

import dialogs.MergeDialog;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * The "Tools" sidebar screen: a scrollable panel of buttons for every
 * PDF operation that isn't part of normal viewing (merge, split,
 * convert, password/security, metadata, watermarking, printing, etc.),
 * grouped into labeled sections.
 *
 * This screen doesn't hold its own PDF state — every operation acts on
 * whatever document is currently open in the {@link viewer.PdfViewer} it was
 * constructed with. Most buttons simply delegate to a matching public
 * method on {@code PdfViewer} (via {@link #reopenViewer} to switch back
 * to the viewer card first); a handful of longer-running operations
 * (merge, image-to-PDF) are implemented directly here as background
 * {@link SwingWorker} tasks so the UI doesn't freeze while PDFBox
 * processes potentially large files.
 *
 * Buttons are dispatched through two switches in sequence within
 * {@link #actionPerformed(ActionEvent)}: an initial switch that
 * collects any file(s) the operation needs via a {@link JFileChooser}
 * (currently just "Merge PDF" and "IMG to PDF"), followed by the main
 * switch that actually performs each operation.
 */
public class tools extends JPanel implements ActionListener {

    /** The currently-open PDF viewer; every tool operation ultimately acts on its open document. */
    private PdfViewer viewer;

    /** Switches the main window back to the Viewer card before an operation shows a dialog on top of it. */
    private Runnable reopenViewer;

    /** Output path for the currently-running merge, set by the "Merge PDF" file-selection case. */
    File outputFile;

    /** Set by the "IMG to PDF" file-selection case; currently unused elsewhere (informational). */
    private File selectedFile;

    /** The set of images selected for "IMG to PDF" conversion, in the order chosen. */
    private File[] selectedFiles;

    JScrollPane pdfToolsScroll;

    JPanel ToolsPanel;

    JPanel createEditPanel;
    JButton newPdfButton, editPdfButton, addBlankPageButton;
    JPanel pdfToolsPanel;

    JButton organizePagesButton;
    JButton mergePdfButton;
    JButton splitPdfButton;
    JButton extractPagesButton;
    JButton removePagesButton;
    JButton reorderPagesButton;
    JButton rotatePagesButton;

    JPanel pdfConvertPanel;

    JButton pdfToPng;
    JButton pdfToJpg;
    JButton imgToPdf;
    JButton pdfToTxt;

    JPanel pdfSecurityPanel;

    JButton addPasswdButton;
    JButton changePasswdButton;
    JButton removePasswdButton;
    JButton securityInformationButton;
    JButton editSecurityInformationButton;

    JPanel pdfMetadataPanel;

    JButton viewMetadataButton;
    JButton editMetadataButton;

    JPanel pdfContentPanel;

    JButton extractImageButton;
    JButton searchTextButton;

    JPanel pdfInfoPanel;

    JButton pdfInfoButton;
    JButton pageCountButton;
    JButton listFontsButton;

    JPanel pdfWatermarkPanel;

    JButton addTextWatermarkButton;
    JButton addImageWatermarkButton;

    JPanel pdfPrintPanel;
    JButton printPdf;

    /**
     * Builds the Tools screen's UI — every section and button — but
     * performs no PDF operations itself until a button is clicked.
     *
     * @param viewer       the PDF viewer whose currently-open document
     *                     every tool operation acts on
     * @param reopenViewer switches the main window back to the Viewer
     *                     card; called before operations that need to
     *                     show a dialog over the viewer
     */
    public tools(
            PdfViewer viewer,
            Runnable reopenViewer
    ) {
        this.viewer = viewer;
        this.reopenViewer = reopenViewer;
        putClientProperty("FlatLaf.style", "arc:20");
        setOpaque(false);

        setLayout(new BorderLayout());


        ToolsPanel = new JPanel();
        ToolsPanel.setOpaque(false);
        ToolsPanel.setLayout(new BoxLayout(ToolsPanel, BoxLayout.Y_AXIS));

        Ui.padView(ToolsPanel);
        pdfToolsScroll = new JScrollPane(ToolsPanel);
        Ui.styleScroll(pdfToolsScroll);               // same rounded frame as the Recent table

        pdfToolsScroll.getVerticalScrollBar()
                .setUnitIncrement(16);

        pdfToolsScroll.getViewport()
                .setOpaque(false);

        pdfToolsScroll.setOpaque(false);
        add(pdfToolsScroll, BorderLayout.CENTER);

        // Header stays put above the scrolling card, like every other screen.
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(BorderFactory.createEmptyBorder(0, 0, Ui.GAP, 0));
        headerRow.add(Ui.header("PDF Tools", "Convert, secure, analyze and manage PDFs"), BorderLayout.CENTER);
        add(headerRow, BorderLayout.NORTH);

        createEditPanel = createSectionPanel();
        newPdfButton = createToolButton("New PDF");
        editPdfButton = createToolButton("Edit PDF");
        addBlankPageButton = createToolButton("Add Blank Page");
        newPdfButton.setIcon(Ui.icon("file-plus", 20, SvgIcon.Tint.BRAND));
        editPdfButton.setIcon(Ui.icon("file-pen", 20, SvgIcon.Tint.BRAND));
        addBlankPageButton.setIcon(Ui.icon("plus", 20, SvgIcon.Tint.BRAND));
        for (JButton b : new JButton[]{newPdfButton, editPdfButton, addBlankPageButton}) b.setIconTextGap(10);
        createEditPanel.add(newPdfButton);
        createEditPanel.add(editPdfButton);
        createEditPanel.add(addBlankPageButton);

        pdfToolsPanel = createSectionPanel();
        organizePagesButton = createToolButton("Organize Pages");
        mergePdfButton = createToolButton("Merge PDF");
        splitPdfButton = createToolButton("Split PDF");
        extractPagesButton = createToolButton("Extract Pages");
        removePagesButton = createToolButton("Remove Pages");
        reorderPagesButton = createToolButton("Reorder Pages");
        rotatePagesButton = createToolButton("Rotate Pages");
        pdfToolsPanel.add(organizePagesButton);
        pdfToolsPanel.add(mergePdfButton);
        pdfToolsPanel.add(splitPdfButton);
        pdfToolsPanel.add(extractPagesButton);
        pdfToolsPanel.add(removePagesButton);
        pdfToolsPanel.add(reorderPagesButton);
        pdfToolsPanel.add(rotatePagesButton);

        ToolsPanel.add(createSectionTitle("Create & Edit"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(createEditPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        ToolsPanel.add(createSectionTitle("Tools"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfToolsPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        pdfConvertPanel = createSectionPanel();
        pdfToPng = createToolButton("PDF to PNG");
        pdfToJpg = createToolButton("PDF to JPG");
        pdfToTxt = createToolButton("Export Text");
        imgToPdf = createToolButton("IMG to PDF");
        pdfConvertPanel.add(pdfToPng);
        pdfConvertPanel.add(pdfToJpg);
        pdfConvertPanel.add(pdfToTxt);
        pdfConvertPanel.add(imgToPdf);

        ToolsPanel.add(createSectionTitle("Conversion"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfConvertPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        pdfSecurityPanel = createSectionPanel();
        addPasswdButton = createToolButton("Add Password");
        changePasswdButton = createToolButton("Change Password");
        removePasswdButton = createToolButton("Remove Password");
        securityInformationButton = createToolButton("Security Information");
        editSecurityInformationButton = createToolButton("Edit Security Information");

        pdfSecurityPanel.add(addPasswdButton);
        pdfSecurityPanel.add(changePasswdButton);
        pdfSecurityPanel.add(removePasswdButton);
        pdfSecurityPanel.add(securityInformationButton);
        pdfSecurityPanel.add(editSecurityInformationButton);

        ToolsPanel.add(createSectionTitle("Security"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfSecurityPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        pdfMetadataPanel = createSectionPanel();
        viewMetadataButton = createToolButton("View Metadata");
        editMetadataButton = createToolButton("Edit Metadata");
        pdfMetadataPanel.add(viewMetadataButton);
        pdfMetadataPanel.add(editMetadataButton);

        ToolsPanel.add(createSectionTitle("Metadata"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfMetadataPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        pdfContentPanel = createSectionPanel();
        extractImageButton = createToolButton("Extract Image");
        searchTextButton = createToolButton("Search Text");
        pdfContentPanel.add(extractImageButton);
        pdfContentPanel.add(searchTextButton);

        ToolsPanel.add(createSectionTitle("Content"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfContentPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        pdfInfoPanel = createSectionPanel();
        pdfInfoButton = createToolButton("PDF Info");
        pageCountButton = createToolButton("Page Count");
        listFontsButton = createToolButton("List Fonts");
        pdfInfoPanel.add(pdfInfoButton);
        pdfInfoPanel.add(pageCountButton);
        pdfInfoPanel.add(listFontsButton);

        ToolsPanel.add(createSectionTitle("Info"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfInfoPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        pdfWatermarkPanel = createSectionPanel();
        addTextWatermarkButton = createToolButton("Add Text Watermark");
        addImageWatermarkButton = createToolButton("Add Image Watermark");
        pdfWatermarkPanel.add(addTextWatermarkButton);
        pdfWatermarkPanel.add(addImageWatermarkButton);

        ToolsPanel.add(createSectionTitle("Watermark"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfWatermarkPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));

        pdfPrintPanel = createSectionPanel();
        printPdf = createToolButton("Print PDF");
        pdfPrintPanel.add(printPdf);

        ToolsPanel.add(createSectionTitle("Print"));
        ToolsPanel.add(Box.createVerticalStrut(8));
        ToolsPanel.add(pdfPrintPanel);
        ToolsPanel.add(Box.createVerticalStrut(20));
    }

    /** Builds a bold section heading label (e.g. "PDF Operations", "Security"). */
    /** Asks before replacing an existing file. */
    private boolean confirmReplace(File f) {
        return !f.exists() || Dialogs.showConfirmDialog(this, f.getName() + " already exists. Replace it?",
                "Confirm overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private JLabel createSectionTitle(String text) {
        JLabel label = Ui.section(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton createToolButton(String text) {
        JButton btn = Ui.tile(text);
        btn.setPreferredSize(new Dimension(0, 64));
        btn.addActionListener(this);
        return btn;
    }

    private JPanel createSectionPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 4, 10, 10));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /**
     * Single dispatch point for every tool button, routed by action
     * command through two sequential switches:
     * <ol>
     *   <li>An initial switch that opens a {@link JFileChooser} for
     *   operations needing file input up front ("Merge PDF" collects
     *   multiple PDFs into {@link #selectedFiles}; "IMG to PDF"
     *   collects images the same way). Returns early if the user
     *   cancels the chooser.</li>
     *   <li>The main switch that performs the operation — either by
     *   calling {@code reopenViewer.run()} followed by the matching
     *   method on {@link #viewer} (the pattern used by nearly every
     *   case), or, for merge and image-to-PDF, by running the
     *   operation directly here in a background {@link SwingWorker}.</li>
     * </ol>
     */
    @Override
    public void actionPerformed(ActionEvent e) {

        String command = e.getActionCommand();

        JFileChooser chooser = new AppFileChooser();

        switch (command) {

            // PDF operations
            case "Merge PDF":
                chooser.setFileFilter(
                        new FileNameExtensionFilter(
                                "PDF Files",
                                "pdf"
                        )
                );

                chooser.setMultiSelectionEnabled(true);

                chooser.setDialogTitle("Select multiple PDF Files");

                if (chooser.showOpenDialog(this)
                        != JFileChooser.APPROVE_OPTION)
                    return;

                selectedFiles = chooser.getSelectedFiles();
                break;

            case "IMG to PDF":

                chooser.setFileFilter(
                        new javax.swing.filechooser.FileNameExtensionFilter(
                                "Images",
                                "png",
                                "jpg",
                                "jpeg",
                                "bmp",
                                "gif",
                                "webp"
                        )
                );

                chooser.setMultiSelectionEnabled(true);

                if (chooser.showOpenDialog(this)
                        != JFileChooser.APPROVE_OPTION) {
                }
                selectedFiles = chooser.getSelectedFiles();
                break;
                }


                switch (command) {
                    // selectedFiles was already collected by the
                    // file-selection switch above. Shows a review
                    // dialog, then a save-location chooser, then merges
                    // in the background so the UI doesn't freeze on
                    // large files.
                    case "Merge PDF":
                        if (selectedFiles.length < 2) {
                            Dialogs.showMessageDialog(
                                    this,
                                    "Select at least two PDFs."
                            );
                            return;
                        }

                        MergeDialog dialog =
                                new MergeDialog(
                                        null,
                                        selectedFiles
                                );

                        dialog.setVisible(true);

                        if (!dialog.isApproved()) {
                            return;
                        }

                        JFileChooser saveChoose = new AppFileChooser();

                        saveChoose.setSelectedFile(
                                new File("Merged.pdf")
                        );

                        if (saveChoose.showSaveDialog(this)
                                != JFileChooser.APPROVE_OPTION){
                            return;
                        }

                        outputFile = saveChoose.getSelectedFile();

                        if (!outputFile.getName().toLowerCase().endsWith(".pdf")) {
                            outputFile = new File(outputFile.getAbsolutePath() + ".pdf");
                        }
                        // Never write over one of the files being merged (that would corrupt the result).
                        for (File source : selectedFiles) {
                            if (source.getAbsoluteFile().equals(outputFile.getAbsoluteFile())) {
                                Dialogs.showMessageDialog(this,
                                        "The merged file can't have the same name as one of the PDFs being merged.\nPlease choose a different name.",
                                        "Choose another name", JOptionPane.WARNING_MESSAGE);
                                return;
                            }
                        }
                        if (!confirmReplace(outputFile)) {
                            return;
                        }

                        PDFMergerUtility merger = new PDFMergerUtility();

                        for (File pdf : selectedFiles) {
                            try {
                                merger.addSource(pdf);
                            } catch (FileNotFoundException ex) {
                                throw new RuntimeException(ex);
                            }
                        }

                        merger.setDestinationFileName(
                                outputFile.getAbsolutePath()
                        );

                        ProgressDialog mergeProgress =
                                ProgressDialog.show(this, "Merging PDF files...", true);

                        new SwingWorker<Void, Void>() {

                            @Override
                            protected Void doInBackground() throws Exception {

                                merger.mergeDocuments(null);

                                return null;
                            }

                            @Override
                            protected void done() {

                                mergeProgress.dispose();

                                try {
                                    // get() re-throws whatever doInBackground()
                                    // threw, wrapped in ExecutionException — this
                                    // is what actually surfaces a merge failure;
                                    // without it, an exception here would be
                                    // swallowed silently (SwingWorker only
                                    // surfaces it via get()) and the success
                                    // dialog below would show regardless.
                                    get();

                                    Dialogs.showMessageDialog(
                                            tools.this,
                                            "Merged successfully."
                                    );
                                    reopenViewer.run();
                                    viewer.openGeneratedPDF(outputFile);

                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    Dialogs.showMessageDialog(
                                            tools.this,
                                            "Merge failed: " + ex.getMessage(),
                                            "Error",
                                            JOptionPane.ERROR_MESSAGE
                                    );
                                }
                            }
                        }.execute();
                        break;
                    case "New PDF":
                        reopenViewer.run();
                        viewer.newPdf();
                        break;
                    case "Edit PDF":
                        reopenViewer.run();
                        viewer.setEditing(true);
                        break;
                    case "Add Blank Page":
                        reopenViewer.run();
                        viewer.addBlankPage();
                        break;
                    case "Organize Pages":
                        reopenViewer.run();
                        viewer.openPageOrganizer();
                        break;
                    case "Split PDF":
                        reopenViewer.run();
                        try {
                            viewer.splitPDF();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                        break;
                    case "Extract Pages":
                        reopenViewer.run();
                        viewer.openPageOrganizerForExtract();
                        break;
                    case "Remove Pages":
                        reopenViewer.run();
                        viewer.removePages();
                        break;
                    case "Reorder Pages":
                        reopenViewer.run();
                        viewer.reorderPages();
                        break;
                    case "Rotate Pages":
                        reopenViewer.run();
                        viewer.rotatePages();
                        break;
                    case "Add Password":
                        reopenViewer.run();
                        viewer.addPassword();
                        break;
                    case "Change Password":
                        reopenViewer.run();
                        viewer.changePassword();
                        break;
                    case "Remove Password":
                        reopenViewer.run();
                        viewer.removePassword();
                        break;
                    case "Security Information":
                        reopenViewer.run();
                        viewer.securityInformation();
                        break;
                    case "Edit Security Information":
                        reopenViewer.run();
                        viewer.editPermissions();
                        break;
                    case "View Metadata":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            viewer.showMetadata();
                        });
                        break;
                    case "Edit Metadata":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.editMetadata();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    case "Extract Image":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.extractImages();
                            }catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    case "Search Text":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.searchTextThroughPdf();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    case "PDF Info":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            viewer.showPdfInfo();
                        });
                        break;
                    case "Page Count":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            viewer.showPageCount();
                        });
                        break;
                    case "List Fonts":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            viewer.listFonts();
                        });
                        break;
                    case "Add Text Watermark":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.addTextWatermark();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    case "Add Image Watermark":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.addImageWatermark();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    case "Print PDF":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            viewer.printPdf();
                        });
                        break;
                    case "PDF to PNG":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.pdfToPng();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    case "PDF to JPG":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.pdfToJpg();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    case "Export Text":
                        reopenViewer.run();
                        SwingUtilities.invokeLater(() -> {
                            try {
                                viewer.exportText();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        break;
                    // selectedFiles was already collected by the
                    // file-selection switch above. Builds one PDF page
                    // per image (page sized to match each image's
                    // aspect ratio) in a background SwingWorker; see
                    // its done() for the exception-surfacing fix noted
                    // in the class javadoc.
                    case "IMG to PDF":

                        if (selectedFiles == null || selectedFiles.length == 0) {
                            Dialogs.showMessageDialog(
                                    this,
                                    "Select one or more images first."
                            );
                            break;
                        }

                        JFileChooser saveChooser = new AppFileChooser();

                        saveChooser.setSelectedFile(
                                new File("output.pdf")
                        );

                        saveChooser.setFileFilter(
                                new FileNameExtensionFilter(
                                        "PDF Files",
                                        "pdf"
                                )
                        );

                        if (saveChooser.showSaveDialog(this)
                                != JFileChooser.APPROVE_OPTION) {
                            break;
                        }

                        outputFile =
                                saveChooser.getSelectedFile();

                        if (!outputFile.getName()
                                .toLowerCase()
                                .endsWith(".pdf")) {

                            outputFile = new File(
                                    outputFile.getAbsolutePath() + ".pdf"
                            );
                        }
                        if (!confirmReplace(outputFile)) {
                            break;
                        }

                        ProgressDialog convertProgress =
                                ProgressDialog.show(this, "Converting images to PDF...", false);

                        SwingWorker<Void, Void> worker = new SwingWorker<>() {

                            @Override
                            protected Void doInBackground() throws Exception {

                                try (PDDocument document = new PDDocument()) {

                                    int total = selectedFiles.length;
                                    int completed = 0;

                                    for (File imageFile : selectedFiles) {

                                        BufferedImage image =
                                                ImageIO.read(imageFile);

                                        if (image == null)
                                            continue;

                                        PDRectangle pageSize = new  PDRectangle(
                                                image.getWidth(),
                                                image.getHeight()
                                        );

                                        PDPage page =
                                                new PDPage(pageSize);

                                        document.addPage(page);

                                        PDImageXObject pdImage =
                                                LosslessFactory.createFromImage(
                                                        document,
                                                        image
                                                );

                                        float pageWidth =
                                                pageSize.getWidth();

                                        float pageHeight =
                                                pageSize.getHeight();

                                        float imageWidth =
                                                image.getWidth();

                                        float imageHeight =
                                                image.getHeight();

                                        float scale =
                                                Math.min(
                                                        pageWidth / imageWidth,
                                                        pageHeight / imageHeight
                                                );

                                        float drawWidth =
                                                imageWidth * scale;

                                        float drawHeight =
                                                imageHeight * scale;

                                        float x =
                                                (pageWidth - drawWidth) / 2;

                                        float y =
                                                (pageHeight - drawHeight) / 2;

                                        try (PDPageContentStream stream =
                                                     new PDPageContentStream(
                                                             document,
                                                             page
                                                     )) {

                                            // Draw at the fitted size computed above, not the raw pixel size.
                                            stream.drawImage(
                                                    pdImage,
                                                    x,
                                                    y,
                                                    drawWidth,
                                                    drawHeight
                                            );
                                        }

                                        completed++;
                                        setProgress((int) (completed * 100.0 / total));
                                    }

                                    document.save(outputFile);

                                } catch (IOException ex) {
                                    // Previously swallowed silently, which meant
                                    // done() below would report "PDF created
                                    // successfully." even when the save failed.
                                    // Log it and let it propagate so done()'s
                                    // get() surfaces it and shows a real error.
                                    ex.printStackTrace();
                                    throw ex;
                                }

                                return null;
                            }

                            @Override
                            protected void done() {

                                convertProgress.dispose();

                                try {
                                    get();

                                    Dialogs.showMessageDialog(
                                            tools.this,
                                            "PDF created successfully."
                                    );

                                    reopenViewer.run();
                                    viewer.openGeneratedPDF(outputFile);

                                } catch (Exception ex) {

                                    Dialogs.showMessageDialog(
                                            tools.this,
                                            ex.getMessage(),
                                            "Error",
                                            JOptionPane.ERROR_MESSAGE
                                    );
                                }
                            }
                        };

                        worker.addPropertyChangeListener(evt -> {
                            if ("progress".equals(evt.getPropertyName())) {
                                convertProgress.setProgress((Integer) evt.getNewValue());
                            }
                        });

                        worker.execute();

                        break;
                }
        }
    }