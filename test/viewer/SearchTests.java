package viewer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import viewer.ops.HighlightArea;
import viewer.ops.SearchStripper;

import java.io.File;

/** Search: hit counting and highlight geometry. */
final class SearchTests {

    private SearchTests() { }

    static void run(File samples) throws Exception {
        try (PDDocument d = Loader.loadPDF(new File(samples, "mixed.pdf"))) {
            SearchStripper ss = new SearchStripper("QUICK brown");
            ss.getText(d);
            String all = new org.apache.pdfbox.text.PDFTextStripper().getText(d).toLowerCase();
            int expect = 0; for (int i = 0; (i = all.indexOf("quick brown", i)) >= 0; i += 11) expect++;
            T.check("search: hit count equals independent text count (" + expect + ")", expect > 0 && ss.getResults().size() == expect);
            HighlightArea h = ss.getResults().get(0).getHighlight();
            T.check("search: highlight geometry sane", h.getWidth() > 10 && h.getHeight() > 3 && h.getPage() == 1);
            SearchStripper none = new SearchStripper("zzzqqq"); none.getText(d);
            T.check("search: no hits is empty", none.getResults().isEmpty());
        }
    }
}
