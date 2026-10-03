package viewer.ops;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import rightpanel.AppFonts;
import rightpanel.FontManager;
import viewer.PdfSession;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Text and image watermarks. The dialog collects a {@link WatermarkSettings};
 * {@link #applyWatermark} then draws it onto a <b>copy</b> of the document
 * and saves that copy to a new file, which is opened afterwards. (The old
 * code drew onto the live document, leaving it modified in memory.)
 */
public final class WatermarkOps extends Ops {

    /** Every option from the watermark dialog. Only the fields matching {@link #type} are used. */
    static final class WatermarkSettings {
        String type;          // "TEXT" or "IMAGE"
        String text;
        File fontFile;
        int fontSize;
        Color color;
        File imageFile;
        float opacity;
        float rotation;
        String position;      // CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TILE
        int startPage;
        int endPage;
        int horizontalMargin;
        int verticalMargin;
        boolean fitToPage;
        float scale;
    }

    private final Consumer<File> open;

    public WatermarkOps(PdfSession session, Component parent, Consumer<File> open) {
        super(session, parent);
        this.open = open;
    }

    public void addTextWatermark() throws IOException { addWatermarkDialog("TEXT"); }

    public void addImageWatermark() throws IOException { addWatermarkDialog("IMAGE"); }

    private void addWatermarkDialog(String initialType) throws IOException {

        if (!session.isOpen()) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Open a PDF first."
            );

            return;
        }

        // ---------------- Watermark type toggle ----------------

        JRadioButton textTypeRB = new JRadioButton(
                "Text Watermark", "TEXT".equals(initialType));

        JRadioButton imageTypeRB = new JRadioButton(
                "Image Watermark", "IMAGE".equals(initialType));

        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(textTypeRB);
        typeGroup.add(imageTypeRB);

        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(textTypeRB);
        typePanel.add(imageTypeRB);

        JLabel watermarkLabel = new JLabel("Enter Watermark:");
        JTextArea watermarkArea = new JTextArea("Confidential", 3, 20);

        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();

        String[] fonts =
                ge.getAvailableFontFamilyNames();
        JLabel fontFamilyLabel = new JLabel("Font:");
        JComboBox<String> fontFamilyComboBox = new JComboBox<>(fonts);

        JLabel fontSizeLabel = new JLabel("Font Size:");
        JSpinner fontSizeSpinner = new JSpinner(new SpinnerNumberModel(48, 1, 200, 1));

        JButton colorButton = new JButton("Color");

        final Color[] selectedColor = { Color.LIGHT_GRAY };
        colorButton.setBackground(selectedColor[0]);

        colorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(
                        parent,
                        "Choose watermark color",
                        selectedColor[0]
                );
                if (c != null) {
                    selectedColor[0] = c;
                    colorButton.setBackground(c);
                }
            }
        });

        JPanel textSpecificPanel = new JPanel(new GridBagLayout());

        GridBagConstraints tgbc = new GridBagConstraints();
        tgbc.insets = new Insets(5, 5, 5, 5);
        tgbc.anchor = GridBagConstraints.WEST;
        tgbc.fill = GridBagConstraints.HORIZONTAL;
        tgbc.weightx = 1;

        int trow = 0;

        tgbc.gridx = 0; tgbc.gridy = trow;
        textSpecificPanel.add(watermarkLabel, tgbc);
        tgbc.gridx = 1;
        textSpecificPanel.add(new JScrollPane(watermarkArea), tgbc);

        trow++;
        tgbc.gridx = 0; tgbc.gridy = trow;
        textSpecificPanel.add(fontFamilyLabel, tgbc);
        tgbc.gridx = 1;
        textSpecificPanel.add(fontFamilyComboBox, tgbc);

        trow++;
        tgbc.gridx = 0; tgbc.gridy = trow;
        textSpecificPanel.add(fontSizeLabel, tgbc);
        tgbc.gridx = 1;
        textSpecificPanel.add(fontSizeSpinner, tgbc);

        trow++;
        tgbc.gridx = 0; tgbc.gridy = trow;
        textSpecificPanel.add(new JLabel("Color:"), tgbc);
        tgbc.gridx = 1;
        textSpecificPanel.add(colorButton, tgbc);

        JLabel imagePathLabel = new JLabel("No image selected");
        JButton chooseImageButton = new JButton("Choose Image...");

        final File[] selectedImageFile = { null };

        chooseImageButton.addActionListener(e -> {

            JFileChooser imageChooser = new JFileChooser();

            imageChooser.setFileFilter(
                    new FileNameExtensionFilter(
                            "Image Files",
                            "png", "jpg", "jpeg", "bmp", "gif"
                    )
            );

            int r = imageChooser.showOpenDialog(parent);

            if (r == JFileChooser.APPROVE_OPTION) {
                selectedImageFile[0] = imageChooser.getSelectedFile();
                imagePathLabel.setText(selectedImageFile[0].getName());
            }
        });

        JPanel imageSpecificPanel = new JPanel(new GridBagLayout());

        GridBagConstraints igbc = new GridBagConstraints();
        igbc.insets = new Insets(5, 5, 5, 5);
        igbc.anchor = GridBagConstraints.WEST;
        igbc.fill = GridBagConstraints.HORIZONTAL;
        igbc.weightx = 1;

        igbc.gridx = 0; igbc.gridy = 0;
        imageSpecificPanel.add(chooseImageButton, igbc);
        igbc.gridx = 1;
        imageSpecificPanel.add(imagePathLabel, igbc);

        CardLayout specificCardLayout = new CardLayout();
        JPanel specificPanel = new JPanel(specificCardLayout);

        specificPanel.add(textSpecificPanel, "TEXT");
        specificPanel.add(imageSpecificPanel, "IMAGE");

        specificCardLayout.show(specificPanel, initialType);

        textTypeRB.addActionListener(e ->
                specificCardLayout.show(specificPanel, "TEXT"));

        imageTypeRB.addActionListener(e ->
                specificCardLayout.show(specificPanel, "IMAGE"));

        JLabel opacityLabel = new JLabel("Opacity:");
        JSlider opacitySlider = new JSlider(0, 100, 50);
        opacitySlider.setMajorTickSpacing(10);
        opacitySlider.setPaintTicks(true);
        opacitySlider.setPaintLabels(true);

        JLabel rotationLabel = new JLabel("Rotation:");
        JSpinner rotationSpinner = new JSpinner(new SpinnerNumberModel(45, -180, 180, 5));

        JPanel sharedPanel = new JPanel(new GridBagLayout());

        GridBagConstraints shgbc = new GridBagConstraints();
        shgbc.insets = new Insets(5, 5, 5, 5);
        shgbc.anchor = GridBagConstraints.WEST;
        shgbc.fill = GridBagConstraints.HORIZONTAL;
        shgbc.weightx = 1;

        shgbc.gridx = 0; shgbc.gridy = 0;
        sharedPanel.add(opacityLabel, shgbc);
        shgbc.gridx = 1;
        sharedPanel.add(opacitySlider, shgbc);

        shgbc.gridx = 0; shgbc.gridy = 1;
        sharedPanel.add(rotationLabel, shgbc);
        shgbc.gridx = 1;
        sharedPanel.add(rotationSpinner, shgbc);

        JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
        leftPanel.add(typePanel, BorderLayout.NORTH);
        leftPanel.add(specificPanel, BorderLayout.CENTER);
        leftPanel.add(sharedPanel, BorderLayout.SOUTH);

        JPanel positionPanel = new JPanel();
        positionPanel.setBorder(
                BorderFactory.createTitledBorder("Position")
        );
        positionPanel.setLayout(
                new GridLayout(0, 2, 5, 5)
        );

        JRadioButton centerRB = new JRadioButton("Center", true);
        JRadioButton topLeftRB = new JRadioButton("Top Left");
        JRadioButton topRightRB = new JRadioButton("Top Right");
        JRadioButton bottomLeftRB = new JRadioButton("Bottom Left");
        JRadioButton bottomRightRB = new JRadioButton("Bottom Right");
        JRadioButton tileRB = new JRadioButton("Tile");

        ButtonGroup positionGroup = new ButtonGroup();

        positionGroup.add(centerRB);
        positionGroup.add(topLeftRB);
        positionGroup.add(topRightRB);
        positionGroup.add(bottomLeftRB);
        positionGroup.add(bottomRightRB);
        positionGroup.add(tileRB);

        positionPanel.add(centerRB);
        positionPanel.add(topLeftRB);
        positionPanel.add(topRightRB);
        positionPanel.add(bottomLeftRB);
        positionPanel.add(bottomRightRB);
        positionPanel.add(tileRB);

        JPanel pagesPanel = new JPanel(new GridBagLayout());

        pagesPanel.setBorder(
                BorderFactory.createTitledBorder("Pages")
        );

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        JRadioButton allPagesRB =
                new JRadioButton("All Pages", true);

        JRadioButton customPagesRB =
                new JRadioButton("Custom Range");

        ButtonGroup pageGroup =
                new ButtonGroup();

        pageGroup.add(allPagesRB);
        pageGroup.add(customPagesRB);

        JSpinner startPageSpinner =
                new JSpinner(
                        new SpinnerNumberModel(1,1,9999,1)
                );

        JSpinner endPageSpinner =
                new JSpinner(
                        new SpinnerNumberModel(1,1,9999,1)
                );

        startPageSpinner.setEnabled(false);
        endPageSpinner.setEnabled(false);

        customPagesRB.addActionListener(e -> {

            boolean enabled =
                    customPagesRB.isSelected();

            startPageSpinner.setEnabled(enabled);
            endPageSpinner.setEnabled(enabled);
        });

        allPagesRB.addActionListener(e -> {

            startPageSpinner.setEnabled(false);
            endPageSpinner.setEnabled(false);
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        pagesPanel.add(allPagesRB, gbc);

        gbc.gridy++;
        pagesPanel.add(customPagesRB, gbc);

        gbc.gridx = 1;
        pagesPanel.add(startPageSpinner, gbc);

        gbc.gridx = 2;
        pagesPanel.add(new JLabel("-"), gbc);

        gbc.gridx = 3;
        pagesPanel.add(endPageSpinner, gbc);

        JPanel marginPanel =
                new JPanel(new GridLayout(2,2,10,10));

        marginPanel.setBorder(
                BorderFactory.createTitledBorder("Margin")
        );

        JSpinner horizontalMargin =
                new JSpinner(
                        new SpinnerNumberModel(20,0,500,5)
                );

        JSpinner verticalMargin =
                new JSpinner(
                        new SpinnerNumberModel(20,0,500,5)
                );

        marginPanel.add(new JLabel("Horizontal"));
        marginPanel.add(horizontalMargin);

        marginPanel.add(new JLabel("Vertical"));
        marginPanel.add(verticalMargin);

        JPanel scalePanel = new JPanel(new GridBagLayout());

        scalePanel.setBorder(
                BorderFactory.createTitledBorder("Scale")
        );

        GridBagConstraints sg = new GridBagConstraints();
        sg.insets = new Insets(3,3,3,3);
        sg.anchor = GridBagConstraints.WEST;

        JRadioButton fixedSizeRB =
                new JRadioButton("Fixed Size", true);

        JRadioButton fitPageRB =
                new JRadioButton("Fit to Page");

        ButtonGroup scaleGroup =
                new ButtonGroup();

        scaleGroup.add(fixedSizeRB);
        scaleGroup.add(fitPageRB);

        JSpinner scaleSpinner =
                new JSpinner(
                        new SpinnerNumberModel(
                                100,   // default
                                10,    // min
                                500,   // max
                                10     // step
                        )
                );

        JLabel percentLabel =
                new JLabel("%");

        sg.gridx = 0;
        sg.gridy = 0;
        scalePanel.add(fixedSizeRB, sg);

        sg.gridx = 0;
        sg.gridy = 1;
        scalePanel.add(new JLabel("Scale"), sg);

        sg.gridx = 1;
        scalePanel.add(scaleSpinner, sg);

        sg.gridx = 2;
        scalePanel.add(percentLabel, sg);

        sg.gridx = 0;
        sg.gridy = 2;
        sg.gridwidth = 3;
        scalePanel.add(fitPageRB, sg);

        fixedSizeRB.addActionListener(e ->
                scaleSpinner.setEnabled(true)
        );

        fitPageRB.addActionListener(e ->
                scaleSpinner.setEnabled(false)
        );

        scaleSpinner.setEnabled(true);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));

        rightPanel.add(positionPanel);
        rightPanel.add(Box.createVerticalStrut(10));

        rightPanel.add(pagesPanel);
        rightPanel.add(Box.createVerticalStrut(10));

        rightPanel.add(marginPanel);
        rightPanel.add(Box.createVerticalStrut(10));

        rightPanel.add(scalePanel);

        stretch(positionPanel);
        stretch(pagesPanel);
        stretch(marginPanel);
        stretch(scalePanel);



        JPanel mainPanel = new JPanel(new BorderLayout(15,0));

        mainPanel.add(leftPanel, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        int result = JOptionPane.showConfirmDialog(
                parent,
                mainPanel,
                "Add Watermark",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        boolean isText = textTypeRB.isSelected();

        WatermarkSettings settings = new WatermarkSettings();
        settings.type = isText ? "TEXT" : "IMAGE";

        if (isText) {

            settings.text = watermarkArea.getText();

            if (settings.text == null || settings.text.trim().isEmpty()) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Enter watermark text."
                );
                return;
            }

            String family =
                    (String) fontFamilyComboBox.getSelectedItem();

            File fontFile =
                    FontManager.getFontFile(family);

            if (fontFile == null) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Font file not found."
                );
                return;
            }

            settings.fontFile = fontFile;
            settings.fontSize = (Integer) fontSizeSpinner.getValue();
            settings.color = selectedColor[0];

        } else {

            if (selectedImageFile[0] == null) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Please choose an image."
                );
                return;
            }

            settings.imageFile = selectedImageFile[0];
        }

        settings.opacity = opacitySlider.getValue() / 100f;

        settings.rotation = ((Integer) rotationSpinner.getValue()).floatValue();

        if (centerRB.isSelected())
            settings.position = "CENTER";

        else if (topLeftRB.isSelected())
            settings.position = "TOP_LEFT";

        else if (topRightRB.isSelected())
            settings.position = "TOP_RIGHT";

        else if (bottomLeftRB.isSelected())
            settings.position = "BOTTOM_LEFT";

        else if (bottomRightRB.isSelected())
            settings.position = "BOTTOM_RIGHT";

        else if (tileRB.isSelected())
            settings.position = "TILE";

        settings.horizontalMargin =
                (Integer) horizontalMargin.getValue();

        settings.verticalMargin =
                (Integer) verticalMargin.getValue();

        settings.fitToPage =
                fitPageRB.isSelected();

        settings.scale =
                ((Integer) scaleSpinner.getValue()) / 100f;

        if (allPagesRB.isSelected()) {
            settings.startPage = 1;
            settings.endPage = session.pageCount();
        } else {

            settings.startPage =
                    Math.max(1, (Integer) startPageSpinner.getValue());

            settings.endPage =
                    Math.min(
                            session.pageCount(),
                            (Integer) endPageSpinner.getValue()
                    );

            if (settings.startPage > settings.endPage) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Invalid page range."
                );
                return;
            }
        }

        JFileChooser saveChooser = new JFileChooser();
        saveChooser.setDialogTitle("Save Watermarked PDF");

        saveChooser.setFileFilter(
                new FileNameExtensionFilter(
                        "PDF Files",
                        "pdf"
                )
        );

        String baseName =
                (session.file() != null) ? session.file().getName() : "watermarked.pdf";

        int dot = baseName.lastIndexOf(".");

        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }

        saveChooser.setSelectedFile(
                new File(baseName + "_watermarked.pdf")
        );

        if (saveChooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File output = saveChooser.getSelectedFile();

        if (!output.getName().toLowerCase().endsWith(".pdf")) {
            output = new File(output.getAbsolutePath() + ".pdf");
        }

        if (output.exists()) {

            int confirm = JOptionPane.showConfirmDialog(
                    parent,
                    "File already exists. Replace it?",
                    "Confirm",
                    JOptionPane.YES_NO_OPTION
            );

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (output.equals(session.file())) {
            JOptionPane.showMessageDialog(
                    parent,
                    "Choose a different file name; the original is not overwritten."
            );
            return;
        }
        File finalOutput = output;
        background("Applying watermark...", () -> {
            // Work on a private copy so the open document is never modified.
            try (PDDocument copy = session.openCopy()) {
                applyWatermark(copy, settings);
                copy.save(finalOutput);
            }
            return finalOutput;
        }, f -> {
            JOptionPane.showMessageDialog(parent, "Watermark added successfully.");
            open.accept(f);
        }, "Error applying watermark");
    }

    private void applyWatermark(PDDocument document, WatermarkSettings settings) throws IOException {
        PDFont font = "TEXT".equals(settings.type) ? PDType0Font.load(document, settings.fontFile) : null;

        boolean isText = "TEXT".equals(settings.type);

        PDImageXObject pdImage = null;
        float imgWidth = 0;
        float imgHeight = 0;

        if (!isText) {
            pdImage = PDImageXObject.createFromFile(
                    settings.imageFile.getAbsolutePath(),
                    document
            );

            imgWidth = pdImage.getWidth();
            imgHeight = pdImage.getHeight();
        }

        for (int i = settings.startPage - 1; i <= settings.endPage - 1; i++) {

            PDPage page = document.getPage(i);
            PDRectangle box = page.getMediaBox();

            float pageWidth = box.getWidth();
            float pageHeight = box.getHeight();

            float contentWidth;
            float contentHeight;
            float fontSize = settings.fontSize;

            if (isText) {

                float rawWidth = font.getStringWidth(settings.text)
                        / 1000f * settings.fontSize;

                if (settings.fitToPage) {

                    float availableWidth =
                            Math.max(1f, pageWidth - 2 * settings.horizontalMargin);

                    float factor = (rawWidth > 0) ? availableWidth / rawWidth : 1f;
                    fontSize = settings.fontSize * factor;

                } else {
                    fontSize = settings.fontSize * settings.scale;
                }

                contentWidth = font.getStringWidth(settings.text)
                        / 1000f * fontSize;

                contentHeight = fontSize;

            } else {

                float scaleFactor;

                if (settings.fitToPage) {

                    float availableWidth =
                            Math.max(1f, pageWidth - 2 * settings.horizontalMargin);

                    float availableHeight =
                            Math.max(1f, pageHeight - 2 * settings.verticalMargin);

                    scaleFactor = Math.min(
                            availableWidth / imgWidth,
                            availableHeight / imgHeight
                    );

                } else {
                    // Default watermark width at 100% scale: 30% of the page width.
                    float baseWidth = pageWidth * 0.3f;
                    scaleFactor = (baseWidth / imgWidth) * settings.scale;
                }

                contentWidth = imgWidth * scaleFactor;
                contentHeight = imgHeight * scaleFactor;
            }

            List<float[]> centers = computeWatermarkCenters(
                    settings.position,
                    pageWidth, pageHeight,
                    contentWidth, contentHeight,
                    settings.horizontalMargin, settings.verticalMargin
            );

            try (PDPageContentStream stream = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
            )) {

                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(settings.opacity);
                stream.setGraphicsStateParameters(gs);

                if (isText) {
                    stream.setNonStrokingColor(settings.color);
                }

                for (float[] center : centers) {

                    stream.saveGraphicsState();

                    Matrix m = Matrix.getTranslateInstance(center[0], center[1]);
                    m.concatenate(Matrix.getRotateInstance(
                            Math.toRadians(settings.rotation), 0, 0));

                    stream.transform(m);

                    if (isText) {

                        stream.beginText();
                        stream.setFont(font, fontSize);
                        stream.newLineAtOffset(
                                -contentWidth / 2f,
                                -contentHeight / 3f
                        );
                        stream.showText(settings.text);
                        stream.endText();

                    } else {

                        stream.drawImage(
                                pdImage,
                                -contentWidth / 2f,
                                -contentHeight / 2f,
                                contentWidth,
                                contentHeight
                        );
                    }

                    stream.restoreGraphicsState();
                }
            }
        }
    }

    /**
     * Computes the center point(s) at which a watermark of the given
     * content size should be drawn on a page of the given size, for
     * each supported position keyword. "TILE" returns a full grid of
     * centers.
     */
    private List<float[]> computeWatermarkCenters(
            String position,
            float pageWidth, float pageHeight,
            float contentWidth, float contentHeight,
            float horizontalMargin, float verticalMargin
    ) {

        List<float[]> centers = new ArrayList<>();

        switch (position) {

            case "TOP_LEFT":
                centers.add(new float[]{
                        horizontalMargin + contentWidth / 2f,
                        pageHeight - verticalMargin - contentHeight / 2f
                });
                break;

            case "TOP_RIGHT":
                centers.add(new float[]{
                        pageWidth - horizontalMargin - contentWidth / 2f,
                        pageHeight - verticalMargin - contentHeight / 2f
                });
                break;

            case "BOTTOM_LEFT":
                centers.add(new float[]{
                        horizontalMargin + contentWidth / 2f,
                        verticalMargin + contentHeight / 2f
                });
                break;

            case "BOTTOM_RIGHT":
                centers.add(new float[]{
                        pageWidth - horizontalMargin - contentWidth / 2f,
                        verticalMargin + contentHeight / 2f
                });
                break;

            case "TILE": {

                float stepX = contentWidth + horizontalMargin;
                float stepY = contentHeight + verticalMargin;

                if (stepX <= 0) stepX = pageWidth;
                if (stepY <= 0) stepY = pageHeight;

                for (float y = contentHeight / 2f + verticalMargin;
                     y < pageHeight;
                     y += stepY) {

                    for (float x = contentWidth / 2f + horizontalMargin;
                         x < pageWidth;
                         x += stepX) {

                        centers.add(new float[]{x, y});
                    }
                }

                if (centers.isEmpty()) {
                    centers.add(new float[]{pageWidth / 2f, pageHeight / 2f});
                }

                break;
            }

            case "CENTER":
            default:
                centers.add(new float[]{pageWidth / 2f, pageHeight / 2f});
                break;
        }

        return centers;
    }

    private void stretch(JComponent c) {

        c.setAlignmentX(Component.LEFT_ALIGNMENT);

        c.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        c.getPreferredSize().height
                )
        );
    }
}
