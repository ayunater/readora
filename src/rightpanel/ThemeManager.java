package rightpanel;

import com.formdev.flatlaf.intellijthemes.*;

/**
 * Thin wrapper around FlatLaf's IntelliJ theme pack that maps a
 * human-readable theme name (as shown in the Settings screen's theme
 * dropdown) to the matching {@code Flat*IJTheme.setup()} call.
 *
 * {@code setup()} installs the theme as the active Swing LookAndFeel; it
 * does not retroactively re-skin already-visible components. Callers
 * that apply a theme to a running UI (rather than at startup, before
 * any components exist) also need to call
 * {@code SwingUtilities.updateComponentTreeUI(...)} afterwards — see
 * {@code Main.refreshTheme()} for how this app does that.
 *
 * <p><b>Caveat:</b> {@link #applyTheme(String)} silently does nothing if
 * {@code themeName} doesn't exactly match one of the {@link #THEMES}
 * entries (no {@code default} case in the switch, and the surrounding
 * try/catch only guards against exceptions, not unmatched names). Only
 * pass values that came from {@link #THEMES} or were previously saved
 * from it.
 */
public class ThemeManager {

    /**
     * Every theme name this app supports, in the exact form expected by
     * {@link #applyTheme(String)} and used to populate the theme
     * dropdown in the Settings screen. Grouped into light themes then
     * dark themes for the dropdown's sake; the split is cosmetic only.
     */
    public static final String[] THEMES = {
            //light
            "Arc",
            "Cyan Light",
            "Light Flat",
            "Solarized Light",
            //dark
            "Arc Dark",
            "Carbon",
            "Cobalt 2",
            "Dracula",
            "Gradianto Deep Ocean",
            "Gradianto Midnight Blue",
            "Gruvbox Dark Hard",
            "Material Design Dark",
            "Monokai Pro",
            "Nord",
            "One Dark",
            "Solarized Dark",
            "Xcode Dark"
    };


    /**
     * Installs the named FlatLaf theme as the application's active
     * LookAndFeel. Must be called before any Swing components are
     * created for it to take effect on them automatically; for an
     * already-running UI, follow this with
     * {@code SwingUtilities.updateComponentTreeUI(rootComponent)} to
     * re-skin existing components.
     *
     * @param themeName one of the exact strings in {@link #THEMES};
     *                  any other value is silently ignored (see class
     *                  javadoc caveat)
     */
    public static void applyTheme(String themeName) {

        try {

            switch(themeName) {

                //light

                case "Arc":
                    FlatArcIJTheme.setup();
                    break;

                case "Cyan Light":
                    FlatCyanLightIJTheme.setup();
                    break;

                case "Light Flat":
                    FlatLightFlatIJTheme.setup();
                    break;

                case "Solarized Light":
                    FlatSolarizedLightIJTheme.setup();
                    break;

                //dark

                case "Arc Dark":
                    FlatArcDarkIJTheme.setup();
                    break;

                case "Carbon":
                    FlatCarbonIJTheme.setup();
                    break;

                case "Cobalt 2":
                    FlatCobalt2IJTheme.setup();
                    break;

                case "Dracula":
                    FlatDraculaIJTheme.setup();
                    break;

                case "Gradianto Deep Ocean":
                    FlatGradiantoDeepOceanIJTheme.setup();
                    break;

                case "Gradianto Midnight Blue":
                    FlatGradiantoMidnightBlueIJTheme.setup();
                    break;

                case "Gruvbox Dark Hard":
                    FlatGruvboxDarkHardIJTheme.setup();
                    break;

                case "Material Design Dark":
                    FlatMaterialDesignDarkIJTheme.setup();
                    break;

                case "Monokai Pro":
                    FlatMonokaiProIJTheme.setup();
                    break;

                case "Nord":
                    FlatNordIJTheme.setup();
                    break;

                case "One Dark":
                    FlatOneDarkIJTheme.setup();
                    break;

                case "Solarized Dark":
                    FlatSolarizedDarkIJTheme.setup();
                    break;

                case "Xcode Dark":
                    FlatXcodeDarkIJTheme.setup();
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}