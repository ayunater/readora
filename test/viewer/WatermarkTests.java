package viewer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import viewer.ops.WatermarkOps;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;

/** Watermarking works on a copy and never touches the live document or its file. */
final class WatermarkTests {

    private WatermarkTests() { }

    static void run(PdfSession s, File work, java.nio.file.Path tmp) throws Exception {
        File wmOut = new File(tmp.toFile(), "wm.pdf");
        WatermarkOps ops = new WatermarkOps(s, null, f -> { });
        Class<?> setCls = Class.forName("viewer.ops.WatermarkOps$WatermarkSettings");
        Constructor<?> ctor = setCls.getDeclaredConstructor(); ctor.setAccessible(true);
        Object ws = ctor.newInstance();
        T.set(ws, "type", "TEXT"); T.set(ws, "text", "CONFIDENTIAL");
        File font = T.findAnyTrueTypeFont();
        if (font == null) {
            T.skip("watermark tests (no .ttf font found on this machine)");
            return;
        }
        T.set(ws, "fontFile", font); T.set(ws, "fontSize", 48); T.set(ws, "color", java.awt.Color.RED);
        T.set(ws, "opacity", 0.4f); T.set(ws, "rotation", 45f); T.set(ws, "position", "CENTER");
        T.set(ws, "startPage", 1); T.set(ws, "endPage", 3); T.set(ws, "horizontalMargin", 20); T.set(ws, "verticalMargin", 20);
        T.set(ws, "fitToPage", false); T.set(ws, "scale", 1f);
        Method apply = WatermarkOps.class.getDeclaredMethod("applyWatermark", PDDocument.class, setCls);
        apply.setAccessible(true);
        byte[] liveBefore = Files.readAllBytes(work.toPath());
        try (PDDocument copy = s.openCopy()) {
            apply.invoke(ops, copy, ws);
            copy.save(wmOut);
        }
        T.check("watermark: output written", wmOut.length() > 0);
        T.check("watermark: source file unchanged", Arrays.equals(liveBefore, Files.readAllBytes(work.toPath())));
        try (PDDocument onDisk = Loader.loadPDF(work)) {
            T.check("watermark: live doc not modified in memory",
                  s.document().getPage(0).getContents() != null && T.contentLen(s.document(), 0) == T.contentLen(onDisk, 0));
        }
        try (PDDocument d = Loader.loadPDF(wmOut)) {
            T.check("watermark: page 1 has extra content", T.contentLen(d, 0) > T.contentLen(s.document(), 0));
            T.check("watermark: page 5 untouched", T.contentLen(d, 4) == T.contentLen(s.document(), 4));
            new PDFRenderer(d).renderImage(0, 1f);
            T.check("watermark: result renders", true);
        }
    }
}
