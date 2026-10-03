package viewer.ops;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds every case-insensitive occurrence of a query and records where it
 * sits on the page (for highlighting) plus a text snippet.
 *
 * <p>Hardened versus the original: a line's text can be shorter or longer
 * than its glyph list (ligatures such as "fi" are one glyph, accented
 * characters can expand), which used to throw
 * {@code IndexOutOfBoundsException}. Hits whose glyphs can't be mapped
 * reliably are located by proportional position instead of crashing.</p>
 */
public class SearchStripper extends PDFTextStripper {

    private final String query;
    private final List<SearchResult> results = new ArrayList<>();
    private int currentPage = 0;

    public SearchStripper(String query) throws IOException {
        this.query = query.toLowerCase(Locale.ROOT);
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        currentPage++;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
        if (!positions.isEmpty() && !query.isEmpty()) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.length() == text.length()) {
                int index = 0;
                while ((index = lower.indexOf(query, index)) != -1) {
                    addHit(text, positions, index);
                    index += query.length();
                }
            }
        }
        super.writeString(text, positions);
    }

    private void addHit(String text, List<TextPosition> positions, int index) {
        int n = positions.size();
        // Map a character offset to a glyph index (exact when counts match).
        int firstIdx = map(index, text.length(), n);
        int lastIdx = map(index + query.length() - 1, text.length(), n);
        if (lastIdx < firstIdx) lastIdx = firstIdx;
        TextPosition first = positions.get(firstIdx);
        TextPosition last = positions.get(lastIdx);

        int start = Math.max(0, index - 40);
        int end = Math.min(text.length(), index + query.length() + 40);
        String snippet = text.substring(start, end).replace('\n', ' ').trim();

        float width = last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj();
        HighlightArea area = new HighlightArea(currentPage, first.getXDirAdj(), first.getYDirAdj(),
                Math.max(width, 2f), first.getHeightDir());
        results.add(new SearchResult(currentPage, snippet, area));
    }

    private static int map(int charIndex, int chars, int glyphs) {
        if (chars == glyphs) return Math.min(charIndex, glyphs - 1);
        return Math.max(0, Math.min(glyphs - 1, (int) Math.round(charIndex * (glyphs / (double) Math.max(1, chars)))));
    }

    public List<SearchResult> getResults() { return results; }
}
