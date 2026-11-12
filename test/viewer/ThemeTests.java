package viewer;

/** Both custom themes load, report the right darkness and produce valid colours. */
final class ThemeTests {

    private ThemeTests() { }

    static void run() {
        for (String theme : rightpanel.ThemeManager.THEMES) {
            rightpanel.ThemeManager.applyTheme(theme);
            boolean wantDark = rightpanel.ThemeManager.isDark(theme);
            T.check("theme '" + theme + "': detected as " + (wantDark ? "dark" : "light"), ui.Ui.isDark() == wantDark);
            T.check("theme '" + theme + "': accent is the icon violet",
                  java.awt.Color.decode("#6D5CFF").equals(javax.swing.UIManager.getColor("Component.accentColor")));
            java.awt.Color r = ui.Ui.raised(), sk = ui.Ui.sunken();     // used to throw on light themes
            T.check("theme '" + theme + "': surfaces are valid colours", r != null && sk != null);
        }
        T.check("theme: old IntelliJ theme names are not accepted", !rightpanel.ThemeManager.isKnown("Gruvbox Dark Hard"));
    }
}
