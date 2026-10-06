package app;

import ui.Svg;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Makes Readora appear in the file manager's "Open with" list for PDFs, for the current user (no
 * administrator rights needed).
 *
 * <p>The installers (.exe, .msi, .deb) register the association themselves. This is for builds that are not
 * installed (the portable zip, or {@code java -jar}). Windows: per-user registry keys under
 * {@code HKCU\Software\Classes}. Linux: a {@code readora.desktop} entry (and icon) in the user's XDG data
 * directory. On macOS the application bundle declares the association, so there is nothing to do.</p>
 */
public final class FileAssociation {

    public enum Os { WINDOWS, LINUX, MAC, OTHER }

    static final String PROG_ID = "Readora.PDF";

    private FileAssociation() { }

    public static Os os() {
        String n = System.getProperty("os.name", "").toLowerCase();
        if (n.contains("win")) return Os.WINDOWS;
        if (n.contains("mac") || n.contains("darwin")) return Os.MAC;
        if (n.contains("nux") || n.contains("nix") || n.contains("bsd")) return Os.LINUX;
        return Os.OTHER;
    }

    /** Whether this class can register on the current system. */
    public static boolean isSupported() {
        Os os = os();
        return (os == Os.WINDOWS || os == Os.LINUX) && launchCommand() != null;
    }

    // ------------------------------------------------------------------
    // How to start Readora with a file
    // ------------------------------------------------------------------

    /**
     * The command that starts this installation (without the file argument): the packaged launcher
     * ({@code Readora.exe}), or {@code java -jar Readora.jar}. Null when running from loose classes (an IDE).
     */
    static List<String> launchCommand() {
        String self = ProcessHandle.current().info().command().orElse(null);
        if (self == null) return null;
        String name = new File(self).getName().toLowerCase();
        if (!name.startsWith("java")) return List.of(self);                    // jpackage launcher
        try {
            File src = new File(FileAssociation.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (src.isFile() && src.getName().toLowerCase().endsWith(".jar")) {
                String java = name.equals("java.exe") ? new File(new File(self).getParentFile(), "javaw.exe").getPath() : self;
                return List.of(java, "-jar", src.getAbsolutePath());
            }
        } catch (URISyntaxException | SecurityException ignored) {
            // fall through
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Public actions
    // ------------------------------------------------------------------

    public static boolean isRegistered() {
        try {
            switch (os()) {
                case WINDOWS: return run(List.of("reg", "query", "HKCU\\Software\\Classes\\" + PROG_ID)) == 0;
                case LINUX:   return Files.isRegularFile(desktopFile());
                default:      return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** @return a sentence describing what was done, for display */
    public static String register() throws IOException {
        return register(launchCommand());
    }

    /** Registers the given launch command (see {@link #launchCommand()}); public entry point for tests. */
    static String register(List<String> cmd) throws IOException {
        if (cmd == null) throw new IOException("Readora can only register itself when run from the installed app or its .jar file.");
        switch (os()) {
            case WINDOWS:
                for (List<String> c : windowsRegisterCommands(cmd)) {
                    int code = run(c);
                    if (code != 0) throw new IOException("The registry command failed (exit code " + code + "): " + String.join(" ", c));
                }
                return "Readora now appears in the \"Open with\" list for PDF files.\nIn Explorer: right-click a PDF \u2192 Open with \u2192 Readora.";
            case LINUX: {
                Path icon = writeLinuxIcon();
                Files.createDirectories(desktopFile().getParent());
                Files.writeString(desktopFile(), desktopEntry(cmd, icon == null ? "readora" : icon.toString()), StandardCharsets.UTF_8);
                try { run(List.of("update-desktop-database", desktopFile().getParent().toString())); } catch (IOException ignored) { /* optional */ }
                return "Readora now appears in the \"Open with\" list for PDF files in your file manager.";
            }
            default:
                throw new IOException("Not supported on this system.");
        }
    }

    public static String unregister() throws IOException {
        switch (os()) {
            case WINDOWS:
                run(List.of("reg", "delete", "HKCU\\Software\\Classes\\" + PROG_ID, "/f"));
                run(List.of("reg", "delete", "HKCU\\Software\\Classes\\.pdf\\OpenWithProgids", "/v", PROG_ID, "/f"));
                return "Readora was removed from the \"Open with\" list.";
            case LINUX:
                Files.deleteIfExists(desktopFile());
                try { run(List.of("update-desktop-database", desktopFile().getParent().toString())); } catch (IOException ignored) { /* optional */ }
                return "Readora was removed from the \"Open with\" list.";
            default:
                throw new IOException("Not supported on this system.");
        }
    }

    // ------------------------------------------------------------------
    // Windows
    // ------------------------------------------------------------------

    /** The {@code reg add} commands that register Readora as an "Open with" choice for .pdf (HKCU, no admin). */
    static List<List<String>> windowsRegisterCommands(List<String> launch) {
        StringBuilder open = new StringBuilder();
        for (String part : launch) open.append('"').append(part).append("\" ");
        open.append("\"%1\"");
        String root = "HKCU\\Software\\Classes\\" + PROG_ID;
        List<List<String>> cmds = new ArrayList<>();
        cmds.add(List.of("reg", "add", root, "/ve", "/d", "PDF Document", "/f"));
        cmds.add(List.of("reg", "add", root + "\\shell\\open\\command", "/ve", "/d", open.toString(), "/f"));
        if (launch.size() == 1) {                                              // a real Readora.exe: it carries the icon
            cmds.add(List.of("reg", "add", root + "\\DefaultIcon", "/ve", "/d", "\"" + launch.get(0) + "\",0", "/f"));
        }
        cmds.add(List.of("reg", "add", "HKCU\\Software\\Classes\\.pdf\\OpenWithProgids", "/v", PROG_ID, "/t", "REG_NONE", "/f"));
        return cmds;
    }

    // ------------------------------------------------------------------
    // Linux
    // ------------------------------------------------------------------

    private static Path dataHome() {
        String override = System.getProperty("readora.xdgDataHome");           // for tests
        if (override != null) return Path.of(override);
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) return Path.of(xdg);
        return Path.of(System.getProperty("user.home"), ".local", "share");
    }

    private static Path desktopFile() { return dataHome().resolve("applications").resolve("readora.desktop"); }

    /** Renders the SVG logo to a PNG in the user's icon theme so file managers can show it. */
    private static Path writeLinuxIcon() {
        try {
            Path dir = dataHome().resolve("icons").resolve("hicolor").resolve("256x256").resolve("apps");
            Files.createDirectories(dir);
            Path png = dir.resolve("readora.png");
            ImageIO.write(Svg.load("/logo.svg").toImage(256, 256, Color.BLACK), "png", png.toFile());
            return png;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** A freedesktop.org desktop entry that offers Readora for PDFs. */
    static String desktopEntry(List<String> launch, String icon) {
        StringBuilder exec = new StringBuilder();
        for (String part : launch) exec.append(execQuote(part)).append(' ');
        exec.append("%f");
        return "[Desktop Entry]\n"
                + "Type=Application\n"
                + "Name=Readora\n"
                + "Comment=Readora: The PDF App\n"
                + "Exec=" + exec + "\n"
                + "Icon=" + icon + "\n"
                + "Terminal=false\n"
                + "Categories=Office;Viewer;\n"
                + "MimeType=application/pdf;\n";
    }

    /** Quotes a command part per the Desktop Entry specification. */
    static String execQuote(String s) {
        if (s.matches("[A-Za-z0-9_./:@%+=,-]+")) return s;
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '`' || c == '$' || c == '\\') b.append('\\');
            b.append(c);
        }
        return b.append('"').toString();
    }

    // ------------------------------------------------------------------

    private static int run(List<String> cmd) throws IOException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        try {
            if (!p.waitFor(15, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("Timed out: " + cmd.get(0)); }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
        return p.exitValue();
    }
}
