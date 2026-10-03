package store;

import rightpanel.AppPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Reads and writes {@code settings.properties}. The only place that touches that file. */
public final class AppSettings {

    public static final String DEFAULT_THEME = "Gruvbox Dark Hard";

    private AppSettings() { }

    private static Properties load() {
        Properties p = new Properties();
        File f = new File(AppPaths.settingsFile());
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (IOException ignored) { }
        }
        return p;
    }

    public static synchronized String get(String key, String def) {
        return load().getProperty(key, def);
    }

    public static boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }

    public static synchronized void set(String key, String value) {
        Properties p = load();
        p.setProperty(key, value);
        try (OutputStream out = new FileOutputStream(AppPaths.settingsFile())) {
            p.store(out, "Readora Settings");
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }

    public static String theme()          { return get("theme", DEFAULT_THEME); }
    public static boolean rememberRecent() { return getBool("rememberRecent", true); }
    public static boolean confirmExit()    { return getBool("confirmExit", true); }

    /** Reserved for tests: reads UTF-8 text safely. */
    static String read(File f) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
