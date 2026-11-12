package viewer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the whole Readora test suite. Every area lives in its own class in this
 * package; this runner shares the temporary files, keeps the session alive for
 * the tests that need it, and reports the totals.
 *
 * <p>Run with {@code ./build.sh test} (Linux/macOS) or {@code build.bat test}
 * (Windows). It is headless and starts no window.</p>
 */
public class SmokeTest {

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("readora-smoke");
        File samples = new File(tmp.toFile(), "samples");
        samples.mkdirs();
        T.makeSamples(samples);

        ThemeTests.run();
        StoreTests.run(tmp);
        GeometryTests.run(samples);

        File work = new File(tmp.toFile(), "work.pdf");
        Files.copy(new File(samples, "mixed.pdf").toPath(), work.toPath());
        PdfSession session = new PdfSession();
        try {
            SessionTests.run(session, work, tmp);
            SearchTests.run(samples);
            WatermarkTests.run(session, work, tmp);
        } finally {
            session.close();
        }
        T.check("session: closed", !session.isOpen());

        EditorTests.run(tmp);
        OpenWithTests.run(tmp);
        SecurityTests.run(tmp);
        SvgTests.run();

        System.exit(T.summary());
    }
}
