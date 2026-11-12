package rightpanel;

import app.Main;
import store.FileListStore;

/** "Recent Files" screen: the most recently opened PDFs. */
public class RecentScreen extends FileListScreen {

    /** One store instance per list, shared with the viewer. */
    public static final FileListStore STORE =
            new FileListStore(AppPaths.recentFile(), AppPaths.legacyRecentFile(), 50);

    public RecentScreen(Main main) {
        super(main, STORE, "Recent Files", "Recently opened PDFs",
                "Last Opened", "Remove", true, "No recent files yet — open a PDF and it will show up here.");
    }
}
