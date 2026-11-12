package viewer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** "Open with" registration and drag-and-drop. */
final class OpenWithTests {

    private OpenWithTests() { }

    static void run(Path tmp) throws Exception {
        // command-line argument: first existing PDF wins, junk and non-PDFs are ignored
        File pdf = new File(tmp.toFile(), "arg one.pdf"); Files.write(pdf.toPath(), new byte[]{1});
        File txt = new File(tmp.toFile(), "notes.txt"); Files.write(txt.toPath(), new byte[]{1});
        var first = Class.forName("app.Main").getDeclaredMethod("firstPdfArgument", String[].class);
        first.setAccessible(true);
        T.check("args: picks the PDF (path with a space), skipping other files",
              pdf.getAbsoluteFile().equals(first.invoke(null, (Object) new String[]{"--flag", txt.getPath(), "missing.pdf", pdf.getPath()})));
        T.check("args: no PDF -> nothing to open", first.invoke(null, (Object) new String[]{txt.getPath(), "nope.pdf"}) == null
              && first.invoke(null, (Object) new String[0]) == null && first.invoke(null, (Object) null) == null);

        // Linux desktop entry: the file placeholder, mime type and correct quoting
        var entryM = Class.forName("app.FileAssociation").getDeclaredMethod("desktopEntry", List.class, String.class);
        entryM.setAccessible(true);
        String entry = (String) entryM.invoke(null, List.of("/opt/Read ora/bin/Readora"), "readora");
        T.check("desktop entry: passes the file (%f), declares application/pdf", entry.contains(" %f\n") && entry.contains("MimeType=application/pdf;"));
        T.check("desktop entry: a launcher path with a space is quoted", entry.contains("Exec=\"/opt/Read ora/bin/Readora\" %f"));
        String jarEntry = (String) entryM.invoke(null, List.of("/usr/bin/java", "-jar", "/home/u/My Apps/Readora.jar"), "readora");
        T.check("desktop entry: java -jar form keeps the jar path as one argument", jarEntry.contains("Exec=/usr/bin/java -jar \"/home/u/My Apps/Readora.jar\" %f"));
        var qm = Class.forName("app.FileAssociation").getDeclaredMethod("execQuote", String.class); qm.setAccessible(true);
        T.check("desktop entry: shell characters are escaped", "\"a\\$b\\`c\"".equals(qm.invoke(null, "a$b`c")));

        // Windows registry commands (generated only; never executed here)
        var winM = Class.forName("app.FileAssociation").getDeclaredMethod("windowsRegisterCommands", List.class);
        winM.setAccessible(true);
        @SuppressWarnings("unchecked") List<List<String>> reg = (List<List<String>>) winM.invoke(null, List.of("C:\\Program Files\\Readora\\Readora.exe"));
        String all = reg.toString();
        T.check("windows: registers under HKCU only (no admin needed)", all.contains("HKCU\\Software\\Classes\\Readora.PDF") && !all.contains("HKLM") && !all.contains("HKEY_LOCAL"));
        T.check("windows: open command quotes the exe and the %1 file argument",
              all.contains("\"C:\\Program Files\\Readora\\Readora.exe\" \"%1\""));
        T.check("windows: listed under .pdf OpenWithProgids", all.contains("Classes\\.pdf\\OpenWithProgids"));
        @SuppressWarnings("unchecked") List<List<String>> regJar = (List<List<String>>) winM.invoke(null, List.of("C:\\Java\\javaw.exe", "-jar", "C:\\Apps\\Readora.jar"));
        T.check("windows: java -jar form has no bogus icon entry", !regJar.toString().contains("DefaultIcon"));

        // real round trip on Linux against a scratch XDG directory
        if (app.FileAssociation.os() == app.FileAssociation.Os.LINUX) {
            File xdg = new File(tmp.toFile(), "xdg"); System.setProperty("readora.xdgDataHome", xdg.getPath());
            var regM = Class.forName("app.FileAssociation").getDeclaredMethod("register", List.class); regM.setAccessible(true);
            T.check("linux: not registered at first", !app.FileAssociation.isRegistered());
            regM.invoke(null, List.of("/usr/bin/java", "-jar", "/tmp/x/Readora.jar"));
            File desktop = new File(xdg, "applications/readora.desktop");
            T.check("linux: register writes the desktop entry", desktop.isFile() && Files.readString(desktop.toPath()).contains("MimeType=application/pdf"));
            T.check("linux: register writes a PNG icon rendered from the SVG logo", new File(xdg, "icons/hicolor/256x256/apps/readora.png").length() > 1000);
            T.check("linux: isRegistered() sees it", app.FileAssociation.isRegistered());
            app.FileAssociation.unregister();
            T.check("linux: unregister removes it", !desktop.exists() && !app.FileAssociation.isRegistered());
            System.clearProperty("readora.xdgDataHome");
        }

        // drag and drop: files dropped on a table (which has its own handler that refuses files) still open
        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        javax.swing.JTable table = new javax.swing.JTable(2, 2);
        javax.swing.JTextField field = new javax.swing.JTextField();
        root.add(new javax.swing.JScrollPane(table), java.awt.BorderLayout.CENTER);
        root.add(field, java.awt.BorderLayout.SOUTH);
        java.util.concurrent.CopyOnWriteArrayList<File> opened = new java.util.concurrent.CopyOnWriteArrayList<>();
        viewer.PdfDropHandler.installEverywhere(root, opened::add);
        T.check("drop: the window itself accepts files",
              root.getTransferHandler().canImport(new javax.swing.TransferHandler.TransferSupport(root, new java.awt.datatransfer.StringSelection("x"))) == false
              && root.getTransferHandler().canImport(new javax.swing.TransferHandler.TransferSupport(root, T.filesFlavor(List.of(pdf)))));
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            T.check("drop: tables and text fields get a file-aware drop target",
                  table.getDropTarget() != null && table.getDropTarget().getClass().getSimpleName().equals("FileDropTarget")
                  && field.getDropTarget() != null && field.getDropTarget().getClass().getSimpleName().equals("FileDropTarget"));
        } else {
            T.skip("drop-target wiring (needs a display; covered when run with one)");
        }
        root.getTransferHandler().importData(new javax.swing.TransferHandler.TransferSupport(root, T.filesFlavor(List.of(txt, pdf))));
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        T.check("drop: with several files, the first PDF is opened", opened.size() == 1 && opened.get(0).equals(pdf));
    }
}
