package ui;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small, dependency-free SVG renderer for Readora's own icons and logo.
 *
 * <p><b>Why not a library?</b> The standard Swing SVG support ({@code flatlaf-extras} + {@code jsvg}) is a
 * Maven download; this keeps the project at two jars, and gives icons that recolour with the theme.</p>
 *
 * <p><b>Supported:</b> {@code svg, g, path, rect, circle, ellipse, line, polyline, polygon}; every path
 * command (M L H V C S Q T A Z, absolute and relative, including elliptical arcs); {@code transform}
 * (translate, scale, rotate, matrix, skew); fill, stroke, stroke-width/linecap/linejoin/miterlimit,
 * opacity/fill-opacity/stroke-opacity, fill-rule; colours as hex, {@code rgb()}, a few names and
 * {@code currentColor}; {@code linearGradient} and {@code radialGradient} (userSpaceOnUse or
 * objectBoundingBox, gradientTransform).</p>
 *
 * <p><b>Not supported</b> (ignored, never an error): filters/blur, masks, clip paths, {@code use},
 * text, images, CSS stylesheets. Author icons within the supported subset.</p>
 */
public final class Svg {

    private Svg() { }

    private static final Map<String, Doc> CACHE = new HashMap<>();

    /** Loads (and caches) an SVG from the classpath, e.g. {@code "/icons/home.svg"}. */
    public static synchronized Doc load(String resource) {
        return CACHE.computeIfAbsent(resource, r -> {
            try (InputStream in = Svg.class.getResourceAsStream(r)) {
                if (in == null) throw new IllegalArgumentException("SVG resource not found: " + r);
                return parse(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static Doc parse(InputStream in) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // no DTD/entity tricks
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            Element root = f.newDocumentBuilder().parse(in).getDocumentElement();
            return new Parser().parse(root);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid SVG: " + e.getMessage(), e);
        }
    }

    // ======================================================================
    // Model
    // ======================================================================

    private enum Kind { NONE, COLOR, CURRENT, URL }

    private record PaintSpec(Kind kind, Color color, String url) {
        static final PaintSpec NONE = new PaintSpec(Kind.NONE, null, null);
        static final PaintSpec CURRENT = new PaintSpec(Kind.CURRENT, null, null);
        static PaintSpec of(Color c) { return new PaintSpec(Kind.COLOR, c, null); }
    }

    private static final class Style implements Cloneable {
        PaintSpec fill = PaintSpec.of(Color.BLACK);
        PaintSpec stroke = PaintSpec.NONE;
        double strokeWidth = 1, opacity = 1, fillOpacity = 1, strokeOpacity = 1, miter = 4;
        int cap = BasicStroke.CAP_BUTT, join = BasicStroke.JOIN_MITER;
        int windRule = Path2D.WIND_NON_ZERO;

        Style copy() {
            try { return (Style) clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); }
        }
    }

    private static final class Grad {
        boolean radial, bbox = true;
        double x1 = 0, y1 = 0, x2 = 1, y2 = 0, cx = .5, cy = .5, r = .5, fx = Double.NaN, fy = Double.NaN;
        AffineTransform gt = new AffineTransform();
        List<Float> offsets = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        String href;
    }

    private record Item(Shape shape, AffineTransform tx, Style style) { }

    /** A parsed, immutable SVG. */
    public static final class Doc {
        private final double vbX, vbY, vbW, vbH;
        private final List<Item> items;
        private final Map<String, Grad> grads;

        Doc(double vbX, double vbY, double vbW, double vbH, List<Item> items, Map<String, Grad> grads) {
            this.vbX = vbX; this.vbY = vbY; this.vbW = vbW; this.vbH = vbH;
            this.items = items; this.grads = grads;
        }

        public double viewBoxX()      { return vbX; }
        public double viewBoxY()      { return vbY; }
        public double viewBoxWidth()  { return vbW; }
        public double viewBoxHeight() { return vbH; }
        public int shapeCount()       { return items.size(); }

        /**
         * Paints into the box (x, y, w, h), preserving aspect ratio and centring.
         *
         * @param current what {@code currentColor} resolves to; coordinates of a gradient
         *                {@code Paint} are in the SVG's own viewBox space. May be null (black).
         */
        public void paint(Graphics2D g0, double x, double y, double w, double h, Paint current) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                double s = Math.min(w / vbW, h / vbH);
                g.translate(x + (w - vbW * s) / 2, y + (h - vbH * s) / 2);
                g.scale(s, s);
                g.translate(-vbX, -vbY);
                for (Item it : items) draw(g, it, current == null ? Color.BLACK : current);
            } finally {
                g.dispose();
            }
        }

        /** Renders to a transparent ARGB bitmap. */
        public BufferedImage toImage(int w, int h, Paint current) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                paint(g, 0, 0, w, h, current);
            } finally {
                g.dispose();
            }
            return img;
        }

        private void draw(Graphics2D base, Item it, Paint current) {
            Graphics2D g = (Graphics2D) base.create();
            try {
                g.transform(it.tx);
                Style st = it.style;
                Rectangle2D bounds = it.shape.getBounds2D();
                Paint fill = resolve(st.fill, bounds, current);
                if (fill != null) {
                    g.setComposite(AlphaComposite.SrcOver.derive((float) clamp01(st.opacity * st.fillOpacity)));
                    g.setPaint(fill);
                    g.fill(it.shape);
                }
                Paint stroke = st.strokeWidth > 0 ? resolve(st.stroke, bounds, current) : null;
                if (stroke != null) {
                    g.setComposite(AlphaComposite.SrcOver.derive((float) clamp01(st.opacity * st.strokeOpacity)));
                    g.setPaint(stroke);
                    g.setStroke(new BasicStroke((float) st.strokeWidth, st.cap, st.join, (float) Math.max(1, st.miter)));
                    g.draw(it.shape);
                }
            } finally {
                g.dispose();
            }
        }

        private Paint resolve(PaintSpec ps, Rectangle2D b, Paint current) {
            switch (ps.kind) {
                case COLOR:   return ps.color;
                case CURRENT: return current;
                case URL: {
                    Grad gr = findGrad(ps.url);
                    return gr == null ? null : gradientPaint(gr, b);
                }
                default:      return null;
            }
        }

        private Grad findGrad(String id) {
            Grad g = grads.get(id);
            int guard = 0;
            while (g != null && g.offsets.isEmpty() && g.href != null && guard++ < 8) {
                Grad parent = grads.get(g.href);
                if (parent == null) break;
                g.offsets = parent.offsets;
                g.colors = parent.colors;
            }
            return g;
        }

        private Paint gradientPaint(Grad g, Rectangle2D b) {
            if (g.offsets.isEmpty()) return null;
            if (g.offsets.size() == 1) return g.colors.get(0);
            float[] fr = new float[g.offsets.size()];
            Color[] co = g.colors.toArray(new Color[0]);
            for (int i = 0; i < fr.length; i++) {
                float v = Math.max(0f, Math.min(1f, g.offsets.get(i)));
                if (i > 0 && v <= fr[i - 1]) v = Math.min(1f, fr[i - 1] + 0.0001f);   // must strictly increase
                fr[i] = v;
            }
            AffineTransform t = new AffineTransform();
            if (g.bbox) {
                if (b.getWidth() <= 0 || b.getHeight() <= 0) return co[0];
                t.translate(b.getX(), b.getY());
                t.scale(b.getWidth(), b.getHeight());
            }
            t.concatenate(g.gt);
            try {
                if (g.radial) {
                    if (g.r <= 0) return co[co.length - 1];
                    Point2D c = new Point2D.Double(g.cx, g.cy);
                    Point2D f = Double.isNaN(g.fx) ? c : new Point2D.Double(g.fx, g.fy);
                    return new RadialGradientPaint(c, (float) g.r, f, fr, co,
                            MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, t);
                }
                Point2D p1 = new Point2D.Double(g.x1, g.y1), p2 = new Point2D.Double(g.x2, g.y2);
                if (p1.equals(p2)) return co[co.length - 1];
                return new LinearGradientPaint(p1, p2, fr, co,
                        MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, t);
            } catch (IllegalArgumentException e) {
                return co[0];
            }
        }
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    // ======================================================================
    // Parser
    // ======================================================================

    private static final class Parser {
        private final List<Item> items = new ArrayList<>();
        private final Map<String, Grad> grads = new HashMap<>();

        Doc parse(Element root) {
            collectGradients(root);
            double x = 0, y = 0, w = number(root.getAttribute("width"), 100), h = number(root.getAttribute("height"), 100);
            String vb = root.getAttribute("viewBox");
            if (!vb.isBlank()) {
                double[] v = numbers(vb);
                if (v.length == 4) { x = v[0]; y = v[1]; w = v[2]; h = v[3]; }
            }
            Style base = new Style();
            applyStyle(base, root);
            walk(root, base, new AffineTransform());
            return new Doc(x, y, w, h, List.copyOf(items), Map.copyOf(grads));
        }

        private void collectGradients(Element e) {
            String tag = e.getTagName();
            if (tag.equals("linearGradient") || tag.equals("radialGradient")) {
                String id = e.getAttribute("id");
                if (!id.isEmpty()) grads.put(id, readGradient(e));
            }
            for (Element c : children(e)) collectGradients(c);
        }

        private Grad readGradient(Element e) {
            Grad g = new Grad();
            g.radial = e.getTagName().equals("radialGradient");
            g.bbox = !"userSpaceOnUse".equals(e.getAttribute("gradientUnits"));
            if (g.radial) {
                g.cx = frac(e, "cx", .5); g.cy = frac(e, "cy", .5); g.r = frac(e, "r", .5);
                if (e.hasAttribute("fx")) g.fx = frac(e, "fx", g.cx);
                if (e.hasAttribute("fy")) g.fy = frac(e, "fy", g.cy);
            } else {
                g.x1 = frac(e, "x1", 0); g.y1 = frac(e, "y1", 0); g.x2 = frac(e, "x2", 1); g.y2 = frac(e, "y2", 0);
            }
            if (e.hasAttribute("gradientTransform")) g.gt = transform(e.getAttribute("gradientTransform"));
            String href = e.getAttribute("href").isEmpty() ? e.getAttribute("xlink:href") : e.getAttribute("href");
            if (href.startsWith("#")) g.href = href.substring(1);
            for (Element s : children(e)) {
                if (!s.getTagName().equals("stop")) continue;
                Map<String, String> css = css(s);
                String off = prop(s, css, "offset");
                float o = off == null ? 0f : (float) (off.trim().endsWith("%") ? number(off, 0) / 100 : number(off, 0));
                String sc = prop(s, css, "stop-color");
                Color c = sc == null ? Color.BLACK : color(sc, Color.BLACK);
                String so = prop(s, css, "stop-opacity");
                if (so != null) c = withAlpha(c, number(so, 1));
                g.offsets.add(o);
                g.colors.add(c);
            }
            return g;
        }

        private double frac(Element e, String attr, double def) {
            String v = e.getAttribute(attr);
            if (v.isEmpty()) return def;
            return v.trim().endsWith("%") ? number(v, 0) / 100 : number(v, def);
        }

        private void walk(Element e, Style inherited, AffineTransform ctm) {
            for (Element c : children(e)) {
                String tag = c.getTagName();
                switch (tag) {
                    case "g", "svg", "a" -> {
                        Style s = inherited.copy();
                        applyStyle(s, c);
                        walk(c, s, concat(ctm, c));
                    }
                    case "path", "rect", "circle", "ellipse", "line", "polyline", "polygon" -> {
                        Style s = inherited.copy();
                        applyStyle(s, c);
                        Shape shape = shapeOf(c, s);
                        if (shape != null) items.add(new Item(shape, concat(ctm, c), s));
                    }
                    default -> { /* defs, gradients, filters, text, ... : not rendered */ }
                }
            }
        }

        private AffineTransform concat(AffineTransform parent, Element e) {
            AffineTransform t = new AffineTransform(parent);
            if (e.hasAttribute("transform")) t.concatenate(transform(e.getAttribute("transform")));
            return t;
        }

        // ---- shapes -------------------------------------------------------

        private Shape shapeOf(Element e, Style s) {
            switch (e.getTagName()) {
                case "path": {
                    Path2D p = PathData.parse(e.getAttribute("d"));
                    p.setWindingRule(s.windRule);
                    return p;
                }
                case "rect": {
                    double x = number(e.getAttribute("x"), 0), y = number(e.getAttribute("y"), 0);
                    double w = number(e.getAttribute("width"), 0), h = number(e.getAttribute("height"), 0);
                    if (w <= 0 || h <= 0) return null;
                    double rx = number(e.getAttribute("rx"), -1), ry = number(e.getAttribute("ry"), -1);
                    if (rx < 0 && ry < 0) return new Rectangle2D.Double(x, y, w, h);
                    if (rx < 0) rx = ry;
                    if (ry < 0) ry = rx;
                    return new RoundRectangle2D.Double(x, y, w, h, 2 * Math.min(rx, w / 2), 2 * Math.min(ry, h / 2));
                }
                case "circle": {
                    double cx = number(e.getAttribute("cx"), 0), cy = number(e.getAttribute("cy"), 0), r = number(e.getAttribute("r"), 0);
                    return r <= 0 ? null : new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
                }
                case "ellipse": {
                    double cx = number(e.getAttribute("cx"), 0), cy = number(e.getAttribute("cy"), 0);
                    double rx = number(e.getAttribute("rx"), 0), ry = number(e.getAttribute("ry"), 0);
                    return rx <= 0 || ry <= 0 ? null : new Ellipse2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry);
                }
                case "line":
                    return new Line2D.Double(number(e.getAttribute("x1"), 0), number(e.getAttribute("y1"), 0),
                            number(e.getAttribute("x2"), 0), number(e.getAttribute("y2"), 0));
                case "polyline":
                case "polygon": {
                    double[] v = numbers(e.getAttribute("points"));
                    if (v.length < 4) return null;
                    Path2D p = new Path2D.Double(s.windRule);
                    p.moveTo(v[0], v[1]);
                    for (int i = 2; i + 1 < v.length; i += 2) p.lineTo(v[i], v[i + 1]);
                    if (e.getTagName().equals("polygon")) p.closePath();
                    return p;
                }
                default:
                    return null;
            }
        }

        // ---- styling ------------------------------------------------------

        private void applyStyle(Style s, Element e) {
            Map<String, String> css = css(e);
            String v;
            if ((v = prop(e, css, "fill")) != null) s.fill = paint(v, s.fill);
            if ((v = prop(e, css, "stroke")) != null) s.stroke = paint(v, s.stroke);
            if ((v = prop(e, css, "stroke-width")) != null) s.strokeWidth = number(v, s.strokeWidth);
            if ((v = prop(e, css, "stroke-miterlimit")) != null) s.miter = number(v, s.miter);
            if ((v = prop(e, css, "opacity")) != null) s.opacity *= clamp01(number(v, 1));
            if ((v = prop(e, css, "fill-opacity")) != null) s.fillOpacity = clamp01(number(v, 1));
            if ((v = prop(e, css, "stroke-opacity")) != null) s.strokeOpacity = clamp01(number(v, 1));
            if ((v = prop(e, css, "fill-rule")) != null) s.windRule = v.trim().equals("evenodd") ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
            if ((v = prop(e, css, "stroke-linecap")) != null) {
                s.cap = switch (v.trim()) { case "round" -> BasicStroke.CAP_ROUND; case "square" -> BasicStroke.CAP_SQUARE; default -> BasicStroke.CAP_BUTT; };
            }
            if ((v = prop(e, css, "stroke-linejoin")) != null) {
                s.join = switch (v.trim()) { case "round" -> BasicStroke.JOIN_ROUND; case "bevel" -> BasicStroke.JOIN_BEVEL; default -> BasicStroke.JOIN_MITER; };
            }
        }

        private PaintSpec paint(String v, PaintSpec fallback) {
            v = v.trim();
            if (v.equals("none") || v.equals("transparent")) return PaintSpec.NONE;
            if (v.equalsIgnoreCase("currentColor")) return PaintSpec.CURRENT;
            if (v.startsWith("url(")) {
                int a = v.indexOf('#'), b = v.indexOf(')');
                if (a >= 0 && b > a) return new PaintSpec(Kind.URL, null, v.substring(a + 1, b).replace("'", "").replace("\"", "").trim());
                return fallback;
            }
            Color c = color(v, null);
            return c == null ? fallback : PaintSpec.of(c);
        }

        private Map<String, String> css(Element e) {
            Map<String, String> m = new HashMap<>();
            String st = e.getAttribute("style");
            if (!st.isBlank()) {
                for (String decl : st.split(";")) {
                    int i = decl.indexOf(':');
                    if (i > 0) m.put(decl.substring(0, i).trim(), decl.substring(i + 1).trim());
                }
            }
            return m;
        }

        private String prop(Element e, Map<String, String> css, String name) {
            String v = css.get(name);
            if (v != null) return v;
            return e.hasAttribute(name) ? e.getAttribute(name) : null;
        }

        private List<Element> children(Element e) {
            List<Element> out = new ArrayList<>();
            NodeList nl = e.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n instanceof Element el) out.add(el);
            }
            return out;
        }
    }

    // ======================================================================
    // Values
    // ======================================================================

    private static final Pattern NUM = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    /** Leading number of a length such as "24", "1.5px" or "50%"; {@code def} if absent. */
    static double number(String s, double def) {
        if (s == null) return def;
        Matcher m = NUM.matcher(s);
        return m.find() ? Double.parseDouble(m.group()) : def;
    }

    static double[] numbers(String s) {
        List<Double> out = new ArrayList<>();
        Matcher m = NUM.matcher(s == null ? "" : s);
        while (m.find()) out.add(Double.parseDouble(m.group()));
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private static Color withAlpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * clamp01(a) * (c.getAlpha() / 255.0)));
    }

    private static final Map<String, Color> NAMED = Map.of(
            "black", Color.BLACK, "white", Color.WHITE, "red", Color.RED, "green", new Color(0, 128, 0),
            "blue", Color.BLUE, "gray", Color.GRAY, "grey", Color.GRAY, "orange", Color.ORANGE,
            "yellow", Color.YELLOW, "magenta", Color.MAGENTA);

    static Color color(String v, Color def) {
        v = v.trim();
        if (v.startsWith("#")) {
            try {
                String h = v.substring(1);
                if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                if (h.length() == 6) return new Color(Integer.parseInt(h, 16));
                if (h.length() == 8) {
                    long argb = Long.parseLong(h.substring(6) + h.substring(0, 6), 16);   // #rrggbbaa
                    return new Color((int) argb, true);
                }
            } catch (NumberFormatException ignored) { return def; }
            return def;
        }
        if (v.startsWith("rgb")) {
            double[] n = numbers(v.substring(v.indexOf('(')));
            if (n.length >= 3) {
                boolean pct = v.contains("%");
                int r = (int) Math.round(pct ? n[0] * 2.55 : n[0]), g = (int) Math.round(pct ? n[1] * 2.55 : n[1]), b = (int) Math.round(pct ? n[2] * 2.55 : n[2]);
                Color c = new Color(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
                return n.length >= 4 ? withAlpha(c, n[3]) : c;
            }
            return def;
        }
        Color named = NAMED.get(v.toLowerCase(Locale.ROOT));
        return named != null ? named : def;
    }

    private static final Pattern TX = Pattern.compile("([a-zA-Z]+)\\s*\\(([^)]*)\\)");

    static AffineTransform transform(String s) {
        AffineTransform t = new AffineTransform();
        Matcher m = TX.matcher(s == null ? "" : s);
        while (m.find()) {
            double[] a = numbers(m.group(2));
            switch (m.group(1)) {
                case "translate" -> t.translate(a.length > 0 ? a[0] : 0, a.length > 1 ? a[1] : 0);
                case "scale" -> t.scale(a.length > 0 ? a[0] : 1, a.length > 1 ? a[1] : (a.length > 0 ? a[0] : 1));
                case "rotate" -> {
                    if (a.length >= 3) t.rotate(Math.toRadians(a[0]), a[1], a[2]);
                    else if (a.length >= 1) t.rotate(Math.toRadians(a[0]));
                }
                case "matrix" -> { if (a.length >= 6) t.concatenate(new AffineTransform(a[0], a[1], a[2], a[3], a[4], a[5])); }
                case "skewX" -> { if (a.length >= 1) t.concatenate(AffineTransform.getShearInstance(Math.tan(Math.toRadians(a[0])), 0)); }
                case "skewY" -> { if (a.length >= 1) t.concatenate(AffineTransform.getShearInstance(0, Math.tan(Math.toRadians(a[0])))); }
                default -> { }
            }
        }
        return t;
    }

    // ======================================================================
    // Path data
    // ======================================================================

    /** Parser for the {@code d} attribute of a {@code <path>}. */
    static final class PathData {
        private final String s;
        private int pos;

        private PathData(String s) { this.s = s == null ? "" : s; }

        static Path2D parse(String d) {
            return new PathData(d).run();
        }

        private void skipSep() {
            while (pos < s.length() && (Character.isWhitespace(s.charAt(pos)) || s.charAt(pos) == ',')) pos++;
        }

        private boolean hasNumber() {
            skipSep();
            if (pos >= s.length()) return false;
            char c = s.charAt(pos);
            return c == '-' || c == '+' || c == '.' || Character.isDigit(c);
        }

        private double num() {
            skipSep();
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
            boolean dot = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isDigit(c)) pos++;
                else if (c == '.' && !dot) { dot = true; pos++; }
                else if ((c == 'e' || c == 'E') && pos + 1 < s.length()
                        && (Character.isDigit(s.charAt(pos + 1)) || s.charAt(pos + 1) == '-' || s.charAt(pos + 1) == '+')) {
                    pos += 2;
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                    break;
                } else break;
            }
            if (start == pos) throw new IllegalArgumentException("number expected at " + pos + " in path data");
            return Double.parseDouble(s.substring(start, pos));
        }

        /** Arc flags are single characters and may be written without separators ("a1 1 0 01.5.5"). */
        private boolean flag() {
            skipSep();
            char c = s.charAt(pos++);
            return c == '1';
        }

        private Path2D run() {
            Path2D.Double p = new Path2D.Double();
            double cx = 0, cy = 0, sx = 0, sy = 0, lcx = 0, lcy = 0;    // current, subpath start, last control
            char cmd = 0, prev = 0;
            try {
                while (true) {
                    skipSep();
                    if (pos >= s.length()) break;
                    char c = s.charAt(pos);
                    if (Character.isLetter(c)) { cmd = c; pos++; }
                    else if (cmd == 0) break;                                // data before any command
                    boolean rel = Character.isLowerCase(cmd);
                    switch (Character.toUpperCase(cmd)) {
                        case 'M' -> {
                            double x = num(), y = num();
                            if (rel) { x += cx; y += cy; }
                            p.moveTo(x, y); cx = sx = x; cy = sy = y;
                            cmd = rel ? 'l' : 'L';                           // extra pairs are implicit lineto
                        }
                        case 'L' -> { double x = num(), y = num(); if (rel) { x += cx; y += cy; } p.lineTo(x, y); cx = x; cy = y; }
                        case 'H' -> { double x = num(); if (rel) x += cx; p.lineTo(x, cy); cx = x; }
                        case 'V' -> { double y = num(); if (rel) y += cy; p.lineTo(cx, y); cy = y; }
                        case 'C' -> {
                            double x1 = num(), y1 = num(), x2 = num(), y2 = num(), x = num(), y = num();
                            if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy; }
                            p.curveTo(x1, y1, x2, y2, x, y); lcx = x2; lcy = y2; cx = x; cy = y;
                        }
                        case 'S' -> {
                            double x2 = num(), y2 = num(), x = num(), y = num();
                            if (rel) { x2 += cx; y2 += cy; x += cx; y += cy; }
                            boolean after = "CcSs".indexOf(prev) >= 0;
                            double x1 = after ? 2 * cx - lcx : cx, y1 = after ? 2 * cy - lcy : cy;
                            p.curveTo(x1, y1, x2, y2, x, y); lcx = x2; lcy = y2; cx = x; cy = y;
                        }
                        case 'Q' -> {
                            double x1 = num(), y1 = num(), x = num(), y = num();
                            if (rel) { x1 += cx; y1 += cy; x += cx; y += cy; }
                            p.quadTo(x1, y1, x, y); lcx = x1; lcy = y1; cx = x; cy = y;
                        }
                        case 'T' -> {
                            double x = num(), y = num();
                            if (rel) { x += cx; y += cy; }
                            boolean after = "QqTt".indexOf(prev) >= 0;
                            double x1 = after ? 2 * cx - lcx : cx, y1 = after ? 2 * cy - lcy : cy;
                            p.quadTo(x1, y1, x, y); lcx = x1; lcy = y1; cx = x; cy = y;
                        }
                        case 'A' -> {
                            double rx = num(), ry = num(), rot = num();
                            boolean large = flag(), sweep = flag();
                            double x = num(), y = num();
                            if (rel) { x += cx; y += cy; }
                            arc(p, cx, cy, rx, ry, rot, large, sweep, x, y);
                            cx = x; cy = y;
                        }
                        case 'Z' -> { p.closePath(); cx = sx; cy = sy; }
                        default -> throw new IllegalArgumentException("unknown path command '" + cmd + "'");
                    }
                    prev = cmd;
                    if (Character.toUpperCase(cmd) == 'Z') cmd = 0;          // Z takes no arguments; next token must be a command
                }
            } catch (RuntimeException ex) {
                // Malformed data: keep what was parsed so far (browsers do the same).
            }
            return p;
        }

        /** SVG elliptical arc -> cubic Beziers (W3C implementation notes F.6.5). */
        private static void arc(Path2D p, double x0, double y0, double rx, double ry, double deg,
                                boolean large, boolean sweep, double x, double y) {
            if (x0 == x && y0 == y) return;
            rx = Math.abs(rx); ry = Math.abs(ry);
            if (rx == 0 || ry == 0) { p.lineTo(x, y); return; }
            double phi = Math.toRadians(deg % 360), cos = Math.cos(phi), sin = Math.sin(phi);
            double dx2 = (x0 - x) / 2, dy2 = (y0 - y) / 2;
            double x1p = cos * dx2 + sin * dy2, y1p = -sin * dx2 + cos * dy2;
            double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
            if (lambda > 1) { double sq = Math.sqrt(lambda); rx *= sq; ry *= sq; }
            double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
            double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
            double coef = (large == sweep ? -1 : 1) * Math.sqrt(Math.max(0, num / den));
            double cxp = coef * (rx * y1p / ry), cyp = coef * (-ry * x1p / rx);
            double cx = cos * cxp - sin * cyp + (x0 + x) / 2, cy = sin * cxp + cos * cyp + (y0 + y) / 2;
            double th1 = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
            double th2 = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
            double dth = th2 - th1;
            if (!sweep && dth > 0) dth -= 2 * Math.PI;
            else if (sweep && dth < 0) dth += 2 * Math.PI;
            int segs = (int) Math.ceil(Math.abs(dth) / (Math.PI / 2) - 1e-9);
            double delta = dth / segs, t = 4.0 / 3.0 * Math.tan(delta / 4);
            double a = th1;
            for (int i = 0; i < segs; i++) {
                double cosA = Math.cos(a), sinA = Math.sin(a), cosB = Math.cos(a + delta), sinB = Math.sin(a + delta);
                double[] e1 = {cosA - t * sinA, sinA + t * cosA};
                double[] e2 = {cosB + t * sinB, sinB - t * cosB};
                double[] e3 = {cosB, sinB};
                double[][] pts = {e1, e2, e3};
                double[] out = new double[6];
                for (int k = 0; k < 3; k++) {
                    double ux = pts[k][0] * rx, uy = pts[k][1] * ry;
                    out[2 * k] = cos * ux - sin * uy + cx;
                    out[2 * k + 1] = sin * ux + cos * uy + cy;
                }
                p.curveTo(out[0], out[1], out[2], out[3], out[4], out[5]);
                a += delta;
            }
        }
    }
}
