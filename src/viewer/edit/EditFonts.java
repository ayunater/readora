package viewer.edit;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import rightpanel.FontManager;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Fonts and line wrapping for text boxes. The on-screen preview and the PDF writer both use
 * {@link #layout}, so a box wraps at the same places on screen and in the saved file.
 *
 * <p>The three families (Helvetica, Times, Courier) are PDF standard fonts: nothing is embedded and
 * files stay small, but they only cover Western European text. If a box contains other characters
 * (Cyrillic, Greek, CJK...) an installed TrueType font is embedded instead; if none can be found the
 * unsupported characters become "?" and the writer reports it.</p>
 */
public final class EditFonts {

    /** Line spacing as a multiple of the font size. */
    public static final float LEADING = 1.2f;
    /** Distance from the top of a line box to its baseline, as a multiple of the font size. */
    public static final float BASELINE = 0.95f;

    public enum Mode { STANDARD, FALLBACK, SANITIZED }

    /** The wrapped text of one box, ready to draw. */
    public static final class Layout {
        public final List<String> lines;
        public final Mode mode;
        public final File fallbackFile;
        public final int replaced;
        private final Measure measure;

        Layout(List<String> lines, Mode mode, File fallbackFile, int replaced, Measure measure) {
            this.lines = lines;
            this.mode = mode;
            this.fallbackFile = fallbackFile;
            this.replaced = replaced;
            this.measure = measure;
        }

        public float width(String line) { return measure.width(line); }
        public float height(float size) { return Math.max(1, lines.size()) * size * LEADING; }
    }

    interface Measure { float width(String s); }

    private static final String[] FALLBACKS = {
            "Arial", "Arial Unicode MS", "Segoe UI", "Calibri", "DejaVu Sans", "DejaVuSans",
            "Noto Sans", "NotoSans-Regular", "Liberation Sans", "LiberationSans-Regular", "Helvetica Neue"};
    private static File fallback;
    private static boolean fallbackSearched;

    private EditFonts() { }

    /** The standard-14 font for a text item's family and style. */
    public static PDType1Font standard(EditItem.Text t) {
        boolean b = t.bold, i = t.italic;
        FontName f;
        switch (t.family) {
            case "Times":
                f = b ? (i ? FontName.TIMES_BOLD_ITALIC : FontName.TIMES_BOLD) : (i ? FontName.TIMES_ITALIC : FontName.TIMES_ROMAN);
                break;
            case "Courier":
                f = b ? (i ? FontName.COURIER_BOLD_OBLIQUE : FontName.COURIER_BOLD) : (i ? FontName.COURIER_OBLIQUE : FontName.COURIER);
                break;
            default:
                f = b ? (i ? FontName.HELVETICA_BOLD_OBLIQUE : FontName.HELVETICA_BOLD) : (i ? FontName.HELVETICA_OBLIQUE : FontName.HELVETICA);
        }
        return new PDType1Font(f);
    }

    /** An installed TrueType font with broad Unicode coverage, or null. */
    public static synchronized File fallbackFile() {
        if (!fallbackSearched) {
            fallbackSearched = true;
            for (String name : FALLBACKS) {
                File f = FontManager.getFontFile(name);
                if (f != null && f.getName().toLowerCase().endsWith(".ttf")) { fallback = f; break; }
            }
        }
        return fallback;
    }

    static boolean encodable(PDFont font, String s) {
        try {
            font.encode(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Wraps {@code t.text} to {@code t.bounds.width}. */
    public static Layout layout(EditItem.Text t) {
        PDType1Font std = standard(t);
        String text = t.text == null ? "" : t.text.replace("\r", "");
        Mode mode = Mode.STANDARD;
        File ff = null;
        int replaced = 0;

        for (String line : text.split("\n", -1)) {
            if (!encodable(std, line)) { mode = Mode.FALLBACK; break; }
        }
        Measure m;
        if (mode == Mode.FALLBACK) {
            ff = fallbackFile();
            Font awt = null;
            if (ff != null) {
                try { awt = Font.createFont(Font.TRUETYPE_FONT, ff).deriveFont(t.size); } catch (Exception ignored) { }
            }
            if (awt != null) {
                final Font font = awt;
                final FontRenderContext frc = new FontRenderContext(null, true, true);
                m = s -> (float) font.getStringBounds(s, frc).getWidth();
            } else {
                mode = Mode.SANITIZED;
                ff = null;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '\n' || encodable(std, String.valueOf(c))) sb.append(c);
                    else { sb.append('?'); replaced++; }
                }
                text = sb.toString();
                m = standardMeasure(std, t.size);
            }
        } else {
            m = standardMeasure(std, t.size);
        }

        double maxW = Math.max(t.size, t.bounds.width);
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) wrapParagraph(para, (float) maxW, m, lines);
        return new Layout(lines, mode, ff, replaced, m);
    }

    private static Measure standardMeasure(PDType1Font font, float size) {
        return s -> {
            try {
                return font.getStringWidth(s) / 1000f * size;
            } catch (Exception e) {
                return s.length() * size * 0.6f;
            }
        };
    }

    private static void wrapParagraph(String para, float maxW, Measure m, List<String> out) {
        if (para.isEmpty()) { out.add(""); return; }
        StringBuilder line = new StringBuilder();
        for (String word : para.split(" ", -1)) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (m.width(candidate) <= maxW) { line.setLength(0); line.append(candidate); continue; }
            if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
            // A word wider than the box is broken between characters.
            String rest = word;
            while (m.width(rest) > maxW && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && m.width(rest.substring(0, cut)) > maxW) cut--;
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        out.add(line.toString());
    }

    /** Resizes a text box's height to fit its wrapped text. */
    public static void fitHeight(EditItem.Text t) {
        t.bounds.height = layout(t).height(t.size);
    }
}
