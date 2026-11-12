package viewer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;

/** PageGeometry: mixed page sizes and rotated pages. */
final class GeometryTests {

    private GeometryTests() { }

    static void run(File samples) throws Exception {
        try (PDDocument d = Loader.loadPDF(new File(samples, "mixed.pdf"))) {
            PageGeometry g = PageGeometry.of(d);
            T.check("geometry: 8 pages", g.count() == 8);
            T.check("geometry: A3 page larger than Letter", g.width(4) > g.width(0));
            T.check("geometry: landscape wider than tall", g.width(5) > g.height(5));
            T.check("geometry: rotated-90 Letter swaps w/h", Math.abs(g.width(6) - 792) < 1 && Math.abs(g.height(6) - 612) < 1);
        }
    }
}
