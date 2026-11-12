package viewer;

import store.FileListStore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** FileListStore: TSV escaping, the entry cap and migration from the legacy CSV. */
final class StoreTests {

    private StoreTests() { }

    static void run(Path tmp) throws Exception {
        File tsv = new File(tmp.toFile(), "recent.tsv"), csv = new File(tmp.toFile(), "recent.csv");
        // Use a non-ASCII character only if this machine's file-name encoding can represent it.
        boolean unicodeNames = java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"))
                .newEncoder().canEncode('\u00e9');
        File odd = new File(tmp.toFile(), "Report, final (v2)" + (unicodeNames ? " \u00e9" : "") + ".pdf");
        Files.write(odd.toPath(), new byte[]{1});
        FileListStore st = new FileListStore(tsv.getPath(), csv.getPath(), 3);
        st.add(odd);
        st.add(new File(tmp.toFile(), "b.pdf"));
        st.add(new File(tmp.toFile(), "c.pdf"));
        st.add(new File(tmp.toFile(), "d.pdf"));
        List<FileListStore.Entry> es = st.load();
        T.check("store: capped at max entries", es.size() == 3);
        T.check("store: newest first", es.get(0).name().equals("d.pdf"));
        st.add(odd);
        T.check("store: comma" + (unicodeNames ? "/unicode" : "") + " name round-trips", st.load().get(0).file.equals(odd.getAbsoluteFile()));
        T.check("store: contains()", st.contains(odd));
        st.remove(odd);
        T.check("store: remove()", !st.contains(odd));

        File tsv2 = new File(tmp.toFile(), "r2.tsv"), csv2 = new File(tmp.toFile(), "r2.csv");
        Files.writeString(csv2.toPath(),
            "Report, final.pdf," + tmp + "/Report, final.pdf,02 Oct 2026, 08:31\n" +
            "plain.pdf," + tmp + "/plain.pdf,01 Jan 2026, 09:05\n");
        List<FileListStore.Entry> mig = new FileListStore(tsv2.getPath(), csv2.getPath(), 50).load();
        T.check("migration: both legacy rows read", mig.size() == 2);
        T.check("migration: comma in file name recovered", mig.get(0).name().equals("Report, final.pdf"));
        T.check("migration: time kept", mig.get(0).when().equals("02 Oct 2026, 08:31"));
        T.check("migration: writes new file", tsv2.exists());
    }
}
