package rightpanel;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Readora's two themes. Both are custom FlatLaf themes built from the app
 * icon's palette (near-black navy, with a blue -> violet -> magenta
 * accent); the colour values live in {@code resources/theme/*.properties}.
 *
 * <p>This replaces the 17 third-party IntelliJ themes: one identity, in a
 * dark and a light variant, so every screen and dialog reads as Readora.</p>
 */
public final class ThemeManager {

    public static final String DARK = "Readora Dark";
    public static final String LIGHT = "Readora Light";
    public static final String[] THEMES = {DARK, LIGHT};

    private static boolean registered;

    private ThemeManager() { }

    public static boolean isKnown(String name) {
        return DARK.equals(name) || LIGHT.equals(name);
    }

    public static boolean isDark(String name) {
        return !LIGHT.equals(name);
    }

    /**
     * Installs the named theme (anything unknown falls back to dark).
     * Call before creating windows at startup; at runtime follow it with
     * {@code Main.refreshTheme()} to repaint what is already on screen.
     */
    public static void applyTheme(String themeName) {
        try {
            if (!registered) {
                // Loads theme/FlatDarkLaf.properties and theme/FlatLightLaf.properties from the classpath.
                FlatLaf.registerCustomDefaultsSource("theme");
                registered = true;
            }
            if (isDark(themeName)) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            System.err.println("Could not apply theme '" + themeName + "': " + e.getMessage());
        }
    }
}
