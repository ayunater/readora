package rightpanel;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up the installed system font file for a given font family name,
 * so features that need direct access to a .ttf/.otf file (such as
 * PDFBox's {@code PDType0Font.load(document, File)} for embedding a
 * font in a watermark) can resolve a family name picked from a
 * {@code JComboBox} into an actual file on disk.
 *
 * The font list is scanned once and cached in {@link #FONT_MAP};
 * subsequent lookups are just a map read. The scan checks every
 * OS-appropriate font folder for the current platform (Windows, macOS,
 * or Linux) rather than assuming a single hardcoded location, so
 * watermark font selection works the same regardless of platform.
 */
public class FontManager {

    /** Cache of font family name -> the font file it was loaded from. */
    private static final Map<String, File> FONT_MAP = new HashMap<>();

    /** Guards {@link #loadFonts()} so the fonts folders are only scanned once per run. */
    private static boolean loaded = false;

    /**
     * Scans every font folder relevant to the current OS and populates
     * {@link #FONT_MAP} with every distinct font family found, keyed by
     * family name. Safe to call repeatedly — after the first successful
     * call this is a no-op.
     */
    public static void loadFonts() {
        if (loaded) {
            return;
        }

        for (File folder : getFontFolders()) {
            scanFolder(folder);
        }

        loaded = true;
    }

    /**
     * @return every font folder that could plausibly contain user
     *         fonts on the current OS. Folders that don't exist are
     *         included here and simply skipped in {@link #scanFolder};
     *         no need to filter them out first.
     */
    private static List<File> getFontFolders() {

        List<File> folders = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {

            folders.add(new File("C:\\Windows\\Fonts"));

        } else if (os.contains("mac")) {

            folders.add(new File("/Library/Fonts"));
            folders.add(new File("/System/Library/Fonts"));

            if (userHome != null) {
                folders.add(new File(userHome, "Library/Fonts"));
            }

        } else {

            // Linux / other Unix-like systems
            folders.add(new File("/usr/share/fonts"));
            folders.add(new File("/usr/local/share/fonts"));

            if (userHome != null) {
                folders.add(new File(userHome, ".fonts"));
                folders.add(new File(userHome, ".local/share/fonts"));
            }
        }

        return folders;
    }

    /**
     * Recursively scans a single folder for .ttf/.otf files and adds
     * each distinct family found to {@link #FONT_MAP}. Missing folders
     * (common — not every fallback folder above exists on every
     * machine) and unreadable/corrupt font files are silently skipped
     * rather than treated as errors, since this is just a best-effort
     * scan across several possible locations.
     */
    private static void scanFolder(File folder) {

        if (folder == null || !folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            // Linux font folders are commonly nested (e.g.
            // /usr/share/fonts/truetype/dejavu/...), so recurse into
            // subdirectories rather than assuming a flat layout.
            if (file.isDirectory()) {
                scanFolder(file);
                continue;
            }

            String name = file.getName().toLowerCase();

            if (!(name.endsWith(".ttf") || name.endsWith(".otf"))) {
                continue;
            }

            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, file);

                // putIfAbsent: if multiple files declare the same family
                // (e.g. Regular + Bold both reporting "Arial"), keep
                // whichever was found first rather than overwriting it.
                FONT_MAP.putIfAbsent(font.getFamily(), file);
            } catch (Exception ignored) {
                // Not every file in a fonts folder is a valid font PDFBox/AWT
                // can parse (e.g. .ttc collections, corrupt files) — skip it
                // and keep scanning rather than aborting the whole scan.
            }
        }
    }

    /**
     * Resolves a font family name (as shown in a font-picker combo box)
     * to its font file on disk, triggering a one-time font scan on first
     * use if it hasn't run yet.
     *
     * @param family the font family name, e.g. "Arial"
     * @return the matching font file, or {@code null} if no installed
     *         font reports that family name
     */
    public static File getFontFile(String family) {
        loadFonts();
        return FONT_MAP.get(family);
    }
}
