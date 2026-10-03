package store;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A persistent, most-recent-first list of files ("Recent" and "Starred").
 *
 * <p>Stored as {@code path<TAB>epochMillis} per line in UTF-8, so file
 * names containing commas, quotes or non-ASCII characters round-trip
 * correctly. Older Readora versions wrote {@code name,path,date} CSV;
 * if that file exists and the new one doesn't, it is migrated once.</p>
 */
public final class FileListStore {

    /** One row in the list. */
    public static final class Entry {
        public final File file;
        public final long timeMillis;

        Entry(File file, long timeMillis) {
            this.file = file;
            this.timeMillis = timeMillis;
        }

        public String name() { return file.getName(); }
        public String path() { return file.getAbsolutePath(); }
        public boolean exists() { return file.isFile(); }

        public String when() {
            return new SimpleDateFormat("dd MMM yyyy, HH:mm").format(new Date(timeMillis));
        }
    }

    private final File file;
    private final File legacyCsv;
    private final int maxEntries;

    public FileListStore(String tsvPath, String legacyCsvPath, int maxEntries) {
        this.file = new File(tsvPath);
        this.legacyCsv = legacyCsvPath == null ? null : new File(legacyCsvPath);
        this.maxEntries = maxEntries;
    }

    public synchronized List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        if (!file.isFile() && legacyCsv != null && legacyCsv.isFile()) {
            out = migrateLegacy();
            save(out);
        } else if (file.isFile()) {
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    int tab = line.lastIndexOf('\t');
                    if (tab <= 0) continue;
                    try {
                        out.add(new Entry(new File(line.substring(0, tab)),
                                Long.parseLong(line.substring(tab + 1).trim())));
                    } catch (NumberFormatException ignored) { }
                }
            } catch (IOException e) {
                System.err.println("Could not read " + file + ": " + e.getMessage());
            }
        }
        return out;
    }

    private synchronized void save(List<Entry> entries) {
        List<String> lines = new ArrayList<>();
        for (Entry e : entries) lines.add(e.path() + "\t" + e.timeMillis);
        try {
            Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not write " + file + ": " + e.getMessage());
        }
    }

    /** Adds (or moves to the top) a file. */
    public synchronized void add(File f) {
        List<Entry> all = load();
        String p = f.getAbsolutePath();
        all.removeIf(e -> e.path().equals(p));
        all.add(0, new Entry(f.getAbsoluteFile(), System.currentTimeMillis()));
        while (all.size() > maxEntries) all.remove(all.size() - 1);
        save(all);
    }

    public synchronized void remove(File f) {
        List<Entry> all = load();
        String p = f.getAbsolutePath();
        all.removeIf(e -> e.path().equals(p));
        save(all);
    }

    public synchronized void clear() {
        save(new ArrayList<>());
    }

    public synchronized boolean contains(File f) {
        String p = f.getAbsolutePath();
        for (Entry e : load()) if (e.path().equals(p)) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Legacy "name,path,dd MMM yyyy, HH:mm" migration
    // ------------------------------------------------------------------

    private static final Pattern LEGACY_TAIL =
            Pattern.compile("^(.*),(\\d{2} \\w{3} \\d{4}, \\d{2}:\\d{2})$");

    private List<Entry> migrateLegacy() {
        List<Entry> out = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH);
        try {
            for (String line : Files.readAllLines(legacyCsv.toPath(), StandardCharsets.UTF_8)) {
                Matcher m = LEGACY_TAIL.matcher(line.trim());
                if (!m.matches()) continue;
                String namePath = m.group(1);
                String path = null;
                // "name,path": the name is the last segment of the path, which
                // tells us which comma is the separator even if the name has commas.
                for (int i = namePath.indexOf(','); i >= 0; i = namePath.indexOf(',', i + 1)) {
                    String candidate = namePath.substring(i + 1);
                    if (new File(candidate).getName().equals(namePath.substring(0, i))) {
                        path = candidate;
                        break;
                    }
                }
                if (path == null) continue;
                long t;
                try { t = fmt.parse(m.group(2)).getTime(); } catch (Exception e) { t = System.currentTimeMillis(); }
                out.add(new Entry(new File(path), t));
            }
        } catch (IOException e) {
            System.err.println("Legacy migration failed: " + e.getMessage());
        }
        return out;
    }
}
