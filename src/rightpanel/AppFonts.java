package rightpanel;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralizes the two custom fonts this app's UI is built around
 * ("Bernard MT Condensed" for large titles, "Segoe UI" for body text),
 * with a graceful fallback when a font isn't installed.
 *
 * Both are Windows system fonts. On Windows they're virtually always
 * present, so {@link #title(int, int)} / {@link #body(int, int)}
 * behave exactly like calling {@code new Font(...)} directly. On
 * macOS/Linux they're typically missing; passing an unavailable family
 * name to {@code new Font(...)} doesn't throw, but it silently falls
 * back to whatever logical font the platform picks, which can look
 * inconsistent. This class checks availability once (cached — a
 * {@code GraphicsEnvironment} font-name scan isn't free) and
 * substitutes a reasonable cross-platform equivalent instead, so the
 * UI degrades predictably rather than however each OS happens to
 * default.
 *
 * <p>Use {@link #title(int, int)} anywhere the code previously did
 * {@code new Font("Bernard MT Condensed", style, size)}, and
 * {@link #body(int, int)} anywhere it did
 * {@code new Font("Segoe UI", style, size)}.
 */
public class AppFonts {

    private static final String TITLE_FAMILY = "Bernard MT Condensed";
    private static final String BODY_FAMILY = "Segoe UI";

    /** Reasonable stand-ins if the preferred family isn't installed, checked in order. */
    private static final String[] TITLE_FALLBACKS = { "Copperplate", "Rockwell", "Serif" };
    private static final String[] BODY_FALLBACKS = { "SF Pro Text", "Helvetica Neue", "Noto Sans", "SansSerif" };

    /** Lazily-populated set of every font family name installed on this machine. */
    private static volatile Set<String> installedFamilies;

    /**
     * @return the app's title font at the given style/size, using
     *         "Bernard MT Condensed" if installed, otherwise the first
     *         available fallback.
     */
    public static Font title(int style, int size) {
        return resolve(TITLE_FAMILY, TITLE_FALLBACKS, style, size);
    }

    /**
     * @return the app's body font at the given style/size, using
     *         "Segoe UI" if installed, otherwise the first available
     *         fallback.
     */
    public static Font body(int style, int size) {
        return resolve(BODY_FAMILY, BODY_FALLBACKS, style, size);
    }

    private static Font resolve(String preferred, String[] fallbacks, int style, int size) {

        Set<String> families = getInstalledFamilies();

        if (families.contains(preferred)) {
            return new Font(preferred, style, size);
        }

        for (String fallback : fallbacks) {
            if (families.contains(fallback)) {
                return new Font(fallback, style, size);
            }
        }

        // Last resort: Java's built-in logical font, guaranteed to
        // exist on every platform.
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static Set<String> getInstalledFamilies() {

        if (installedFamilies == null) {

            synchronized (AppFonts.class) {

                if (installedFamilies == null) {

                    String[] names = GraphicsEnvironment
                            .getLocalGraphicsEnvironment()
                            .getAvailableFontFamilyNames();

                    installedFamilies = new HashSet<>(Arrays.asList(names));
                }
            }
        }

        return installedFamilies;
    }
}
