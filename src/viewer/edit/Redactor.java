package viewer.edit;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Permanent redaction.
 *
 * <p>Drawing a black box over text does <b>not</b> remove it: the words are still in the file and can be
 * selected, copied and searched. Real redaction has to destroy the content underneath. The robust way to do
 * that without parsing every content-stream operator is to <b>flatten the page</b>: render it, paint the
 * boxes black in the picture, and replace the whole page with that picture. The original text, vectors, fonts
 * and images of the page are then no longer referenced and are not written when the file is saved.</p>
 *
 * <p>The trade-off is honest and shown to the user: flattened pages are images, so their text can no longer be
 * selected or searched. Other pages are untouched.</p>
 */
public final class Redactor {

    /** Resolution of flattened pages. */
    public static final int DPI = 200;
    private static final long MAX_PIXELS = 36_000_000L;

    private Redactor() { }

    /**
     * Flattens page {@code pageIndex}, blacking out {@code boxes} (visual page space, points).
     */
    public static void flatten(PDDocument doc, int pageIndex, List<Rectangle2D> boxes) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PageSpace space = PageSpace.of(page);
        double w = space.width(), h = space.height();

        float scale = DPI / 72f;
        double px = w * scale * h * scale;
        if (px > MAX_PIXELS) scale *= (float) Math.sqrt(MAX_PIXELS / px);       // keep huge pages within memory

        BufferedImage img = new PDFRenderer(doc).renderImage(pageIndex, scale, ImageType.RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLACK);
            double sx = img.getWidth() / w, sy = img.getHeight() / h;
            for (Rectangle2D b : boxes) {
                int x = (int) Math.floor(b.getX() * sx) - 1, y = (int) Math.floor(b.getY() * sy) - 1;
                int bw = (int) Math.ceil(b.getWidth() * sx) + 2, bh = (int) Math.ceil(b.getHeight() * sy) + 2;
                g.fillRect(x, y, bw, bh);                                       // 1px margin: no antialiased sliver survives
            }
        } finally {
            g.dispose();
        }
        PDImageXObject picture = LosslessFactory.createFromImage(doc, img);

        // Drop everything the page referenced, then draw the picture in its place.
        page.setResources(new PDResources());
        page.setAnnotations(new ArrayList<>());
        var cos = page.getCOSObject();
        for (String key : new String[]{"Metadata", "PieceInfo", "Thumb", "AA", "StructParents", "ID"}) cos.removeItem(COSName.getPDFName(key));
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
            cs.transform(new Matrix(space.visualToPdf()));
            cs.drawImage(picture, new Matrix((float) w, 0, 0, (float) -h, 0, (float) h));
        }
    }
}
