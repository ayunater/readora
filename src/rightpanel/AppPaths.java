package rightpanel;

import java.io.File;

/**
 * Resolves a writable, per-user directory for Readora's cache files
 * (settings, recent, starred), instead of relying on a "cache/" folder
 * relative to wherever the app happens to be installed.
 *
 * This matters because installers commonly place the app under
 * C:\Program Files\..., which normal Windows user accounts cannot write
 * to. Writing there silently fails, so settings/recent/starred never
 * persist. Using the OS's standard per-user app-data location avoids
 * that entirely, regardless of how the app was installed or launched.
 */
public class AppPaths {

    private static final String APP_FOLDER_NAME = "Readora";

    private static volatile File cacheDir;

    /**
     * Returns the absolute path to the cache directory, ending in a
     * path separator, creating it if necessary. e.g.
     * "C:\Users\<user>\AppData\Roaming\Readora\cache\" on Windows.
     */
    public static String getCacheDir() {

        if (cacheDir == null) {

            synchronized (AppPaths.class) {

                if (cacheDir == null) {

                    File base = resolveAppDataBase();

                    File resolved = (base != null)
                            ? new File(base, "cache")
                            : new File("cache"); // fallback: old relative behavior

                    resolved.mkdirs();

                    cacheDir = resolved;
                }
            }
        }

        return cacheDir.getAbsolutePath() + File.separator;
    }

    public static String settingsFile() {
        return getCacheDir() + "settings.properties";
    }

    /** Pre-1.0 comma-separated list, migrated automatically. */
    public static String legacyRecentFile() {
        return getCacheDir() + "recent.csv";
    }

    /** Pre-1.0 comma-separated list, migrated automatically. */
    public static String legacyStarredFile() {
        return getCacheDir() + "starred.csv";
    }

    public static String recentFile() {
        return getCacheDir() + "recent.tsv";
    }

    public static String starredFile() {
        return getCacheDir() + "starred.tsv";
    }

    private static File resolveAppDataBase() {

        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home");

        try {

            if (os.contains("win")) {

                String appData = System.getenv("APPDATA");

                if (appData != null && !appData.isBlank()) {
                    return new File(appData, APP_FOLDER_NAME);
                }

            } else if (os.contains("mac")) {

                if (userHome != null) {
                    return new File(
                            userHome,
                            "Library/Application Support/" + APP_FOLDER_NAME
                    );
                }

            } else {

                // Linux / other Unix-like systems
                if (userHome != null) {
                    return new File(
                            userHome,
                            "." + APP_FOLDER_NAME.toLowerCase()
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
