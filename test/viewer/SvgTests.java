package viewer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** SVG engine: shapes, arcs, currentColor, gradients, and every bundled icon. */
final class SvgTests {

    private SvgTests() { }

    static void run() throws Exception {
        var doc = ui.Svg.parse(new java.io.ByteArrayInputStream(
            "<svg viewBox='0 0 10 10'><rect x='0' y='0' width='10' height='10' fill='#ff0000'/><circle cx='5' cy='5' r='3' fill='#0000ff'/></svg>".getBytes()));
        var simg = doc.toImage(10, 10, java.awt.Color.BLACK);
        T.check("svg: shapes and colours render", T.near(simg.getRGB(1, 1), 255, 0, 0, 3) && T.near(simg.getRGB(5, 5), 0, 0, 255, 3));
        var arc = ui.Svg.parse(new java.io.ByteArrayInputStream(
            "<svg viewBox='0 0 20 20'><path d='M2 10 a8 8 0 0 1 16 0 a8 8 0 0 1 -16 0z' fill='#00ff00'/></svg>".getBytes()));
        var aimg = arc.toImage(20, 20, java.awt.Color.BLACK);
        T.check("svg: elliptical arcs form a filled circle (centre filled, corner empty)", T.near(aimg.getRGB(10, 10), 0, 255, 0, 3) && (aimg.getRGB(0, 0) >>> 24) == 0);
        var cc = ui.Svg.parse(new java.io.ByteArrayInputStream(
            "<svg viewBox='0 0 4 4'><rect width='4' height='4' fill='currentColor'/></svg>".getBytes()));
        T.check("svg: currentColor takes the tint", T.near(cc.toImage(4, 4, new java.awt.Color(10, 200, 30)).getRGB(2, 2), 10, 200, 30, 2));
        var gr = ui.Svg.parse(new java.io.ByteArrayInputStream(
            ("<svg viewBox='0 0 100 10'><defs><linearGradient id='g' gradientUnits='userSpaceOnUse' x1='0' y1='0' x2='100' y2='0'>"
            + "<stop offset='0' stop-color='#000000'/><stop offset='1' stop-color='#ffffff'/></linearGradient></defs><rect width='100' height='10' fill='url(#g)'/></svg>").getBytes()));
        var gimg = gr.toImage(100, 10, java.awt.Color.BLACK);
        T.check("svg: linear gradient runs dark to light", ((gimg.getRGB(5, 5) >> 8) & 255) < 40 && ((gimg.getRGB(95, 5) >> 8) & 255) > 215);
        boolean allIcons = true; int iconCount = 0;
        File iconDir = new File("resources/icons");
        File[] icons = iconDir.listFiles((dd, n) -> n.endsWith(".svg"));
        if (icons != null) for (File f : icons) {
            iconCount++;
            try (var in = new java.io.FileInputStream(f)) {
                var dd = ui.Svg.parse(in);
                var im = dd.toImage(24, 24, java.awt.Color.BLACK);
                boolean any = false;
                for (int y = 0; y < 24 && !any; y++) for (int x = 0; x < 24 && !any; x++) any = (im.getRGB(x, y) >>> 24) > 0;
                if (!any) { allIcons = false; System.out.println("   blank icon: " + f.getName()); }
            }
        }
        T.check("svg: all " + iconCount + " bundled icons parse and draw something", iconCount > 30 && allIcons);
        // every icon NAME used anywhere in the source must exist (a typo here crashed a window once)
        java.util.regex.Pattern use = java.util.regex.Pattern.compile("(?:icon|iconButton|loadIcon|makeCardBtn|createToolButton|tool)\\(\\s*(?:[^\"()]*,\\s*)?\"([a-z0-9]+(?:-[a-z0-9]+)*)\"");
        java.util.regex.Pattern modes = java.util.regex.Pattern.compile("^\\s+[A-Z_]+\\(\"[^\"]*\",\\s*\"[^\"]*\",\\s*\"([^\"]*)\"\\)", java.util.regex.Pattern.MULTILINE);
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        try (var walk = Files.walk(Path.of("src"))) {
            for (Path src : (Iterable<Path>) walk.filter(q -> q.toString().endsWith(".java"))::iterator) {
                String code = Files.readString(src);
                var m1 = use.matcher(code); while (m1.find()) names.add(m1.group(1));
                var m2 = modes.matcher(code); while (m2.find()) names.add(m2.group(1));
            }
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String n : names) if (!n.matches("[a-z0-9]+(-[a-z0-9]+)*") || !new File("resources/icons/" + n + ".svg").isFile()) missing.add(n);
        T.check("svg: all " + names.size() + " icon names used in the code exist" + (missing.isEmpty() ? "" : "  MISSING " + missing), missing.isEmpty());
        var logo = ui.Svg.parse(new java.io.FileInputStream("resources/logo.svg")).toImage(64, 64, java.awt.Color.BLACK);
        T.check("svg: logo renders (rounded square with transparent corners)", (logo.getRGB(0, 0) >>> 24) == 0 && (logo.getRGB(32, 8) >>> 24) > 200);
    }
}
