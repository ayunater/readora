package viewer.edit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Draws {@link EditItem}s into a PDF as real page content (vector shapes, selectable text, embedded
 * pictures). Content is appended after the existing page content, so nothing underneath is modified.
 *
 * <p>Drawing happens in visual space with the page's {@link PageSpace} applied as the base transform, so
 * rotated pages and non-zero crop boxes are handled uniformly.</p>
 */
public final class EditWriter {

    /** What happened while writing. */
    public static final class Result {
        public final List<String> warnings = new ArrayList<>();
        public int itemsWritten;
        /** Pages that were flattened to images to redact them. */
        public int redactedPages;
    }

    private EditWriter() { }

    public static Result apply(PDDocument doc, Collection<EditItem> items) throws IOException {
        Result result = new Result();
        Map<Integer, List<EditItem>> byPage = new TreeMap<>();
        Map<Integer, List<java.awt.geom.Rectangle2D>> redactions = new TreeMap<>();
        for (EditItem it : items) {
            if (it.page < 0 || it.page >= doc.getNumberOfPages()) {
                result.warnings.add("An item on page " + (it.page + 1) + " was skipped: the page no longer exists.");
                continue;
            }
            if (it instanceof EditItem.Shape rs && rs.kind == EditItem.Shape.Kind.REDACT) {
                redactions.computeIfAbsent(it.page, k -> new ArrayList<>()).add(new Rectangle2D.Double(it.bounds.x, it.bounds.y, it.bounds.width, it.bounds.height));
                continue;                                  // handled below, after everything else is on the page
            }
            byPage.computeIfAbsent(it.page, k -> new ArrayList<>()).add(it);
        }
        Map<String, PDFont> embedded = new HashMap<>();
        Map<String, PDImageXObject> images = new HashMap<>();

        for (Map.Entry<Integer, List<EditItem>> e : byPage.entrySet()) {
            PDPage page = doc.getPage(e.getKey());
            try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.transform(new Matrix(PageSpace.of(page).visualToPdf()));
                for (EditItem it : e.getValue()) {
                    cs.saveGraphicsState();
                    try {
                        if (it instanceof EditItem.Text t) text(doc, cs, t, embedded, result);
                        else if (it instanceof EditItem.Shape s) shape(cs, s);
                        else if (it instanceof EditItem.Ink i) ink(cs, i);
                        else if (it instanceof EditItem.Pic p) picture(doc, cs, p, images);
                        result.itemsWritten++;
                    } finally {
                        cs.restoreGraphicsState();
                    }
                }
            }
        }
        // Redaction last, so annotations and added items on the same page are part of what gets flattened.
        for (Map.Entry<Integer, List<java.awt.geom.Rectangle2D>> e : redactions.entrySet()) {
            Redactor.flatten(doc, e.getKey(), e.getValue());
            result.redactedPages++;
            result.itemsWritten += e.getValue().size();
        }
        if (result.redactedPages > 0) {
            result.warnings.add(result.redactedPages + " page(s) with redactions were converted to images, so the covered content is "
                    + "permanently gone, but their text can no longer be selected or searched. Bookmarks, metadata and other pages "
                    + "were not changed; use Tools \u2192 Remove Hidden Data to clean those too.");
        }
        return result;
    }

    // ------------------------------------------------------------------

    private static void text(PDDocument doc, PDPageContentStream cs, EditItem.Text t,
                             Map<String, PDFont> embedded, Result result) throws IOException {
        EditFonts.Layout layout = EditFonts.layout(t);
        PDFont font;
        if (layout.mode == EditFonts.Mode.FALLBACK) {
            String key = layout.fallbackFile.getAbsolutePath();
            PDFont f = embedded.get(key);
            if (f == null) { f = PDType0Font.load(doc, layout.fallbackFile); embedded.put(key, f); }
            font = f;
        } else {
            font = EditFonts.standard(t);
        }
        if (layout.mode == EditFonts.Mode.SANITIZED) {
            result.warnings.add(layout.replaced + " character(s) can't be shown in the standard fonts and no "
                    + "Unicode font was found on this computer, so they were replaced with '?'.");
        }
        cs.beginText();
        cs.setFont(font, t.size);
        cs.setNonStrokingColor(t.color);
        for (int i = 0; i < layout.lines.size(); i++) {
            String line = layout.lines.get(i);
            if (line.isEmpty()) continue;
            double x = t.bounds.x;
            float w = layout.width(line);
            if (t.align == 1) x += (t.bounds.width - w) / 2;
            else if (t.align == 2) x += t.bounds.width - w;
            double baseline = t.bounds.y + i * t.size * EditFonts.LEADING + t.size * EditFonts.BASELINE;
            // y is flipped: the base transform is y-down, text is y-up.
            cs.setTextMatrix(new Matrix(1, 0, 0, -1, (float) x, (float) baseline));
            cs.showText(line);
        }
        cs.endText();
    }

    private static void shape(PDPageContentStream cs, EditItem.Shape s) throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setStrokingAlphaConstant(s.opacity);
        gs.setNonStrokingAlphaConstant(s.opacity);
        if (s.kind == EditItem.Shape.Kind.HIGHLIGHT) gs.setBlendMode(BlendMode.MULTIPLY);   // text stays readable underneath
        cs.setGraphicsStateParameters(gs);

        Rectangle2D.Double b = s.bounds;
        boolean fill = s.fill != null && s.kind != EditItem.Shape.Kind.LINE;
        boolean stroke = s.stroke != null && s.width > 0 && s.kind != EditItem.Shape.Kind.HIGHLIGHT;
        if (!fill && !stroke) return;                       // nothing visible: don't leave a dangling path
        if (fill) cs.setNonStrokingColor(s.fill);
        if (stroke) { cs.setStrokingColor(s.stroke); cs.setLineWidth(s.width); }
        cs.setLineCapStyle(1);
        cs.setLineJoinStyle(1);

        switch (s.kind) {
            case LINE:
                if (!stroke) return;
                if (s.rising) { cs.moveTo((float) b.x, (float) (b.y + b.height)); cs.lineTo((float) (b.x + b.width), (float) b.y); }
                else { cs.moveTo((float) b.x, (float) b.y); cs.lineTo((float) (b.x + b.width), (float) (b.y + b.height)); }
                cs.stroke();
                return;
            case ELLIPSE: {
                final double k = 0.5522847498;
                double rx = b.width / 2, ry = b.height / 2, cx = b.x + rx, cy = b.y + ry;
                cs.moveTo((float) (cx + rx), (float) cy);
                cs.curveTo((float) (cx + rx), (float) (cy + k * ry), (float) (cx + k * rx), (float) (cy + ry), (float) cx, (float) (cy + ry));
                cs.curveTo((float) (cx - k * rx), (float) (cy + ry), (float) (cx - rx), (float) (cy + k * ry), (float) (cx - rx), (float) cy);
                cs.curveTo((float) (cx - rx), (float) (cy - k * ry), (float) (cx - k * rx), (float) (cy - ry), (float) cx, (float) (cy - ry));
                cs.curveTo((float) (cx + k * rx), (float) (cy - ry), (float) (cx + rx), (float) (cy - k * ry), (float) (cx + rx), (float) cy);
                cs.closePath();
                break;
            }
            default:
                cs.addRect((float) b.x, (float) b.y, (float) b.width, (float) b.height);
        }
        paint(cs, fill, stroke);
    }

    private static void paint(PDPageContentStream cs, boolean fill, boolean stroke) throws IOException {
        if (fill && stroke) cs.fillAndStroke();
        else if (fill) cs.fill();
        else cs.stroke();
    }

    private static void ink(PDPageContentStream cs, EditItem.Ink ink) throws IOException {
        if (ink.points.isEmpty()) return;
        cs.setStrokingColor(ink.color);
        cs.setLineWidth(ink.width);
        cs.setLineCapStyle(1);
        cs.setLineJoinStyle(1);
        Point2D.Double first = ink.points.get(0);
        cs.moveTo((float) first.x, (float) first.y);
        if (ink.points.size() == 1) cs.lineTo((float) first.x, (float) first.y);   // a dot
        for (int i = 1; i < ink.points.size(); i++) cs.lineTo((float) ink.points.get(i).x, (float) ink.points.get(i).y);
        cs.stroke();
    }

    private static void picture(PDDocument doc, PDPageContentStream cs, EditItem.Pic p,
                                Map<String, PDImageXObject> cache) throws IOException {
        if (p.file == null) return;
        String key = p.file.getAbsolutePath();
        PDImageXObject img = cache.get(key);
        if (img == null) { img = PDImageXObject.createFromFileByContent(p.file, doc); cache.put(key, img); }
        Rectangle2D.Double b = p.bounds;
        // The base transform is y-down; an image is y-up, so flip it back: top edge of the image at b.y.
        cs.drawImage(img, new Matrix((float) b.width, 0, 0, (float) -b.height, (float) b.x, (float) (b.y + b.height)));
    }
}
