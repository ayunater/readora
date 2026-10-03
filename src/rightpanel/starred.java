package rightpanel;

import app.Main;
import store.FileListStore;

/** "Starred" screen: PDFs the user has marked as favourites. */
public class starred extends FileListScreen {

    public static final FileListStore STORE =
            new FileListStore(AppPaths.starredFile(), AppPaths.legacyStarredFile(), 200);

    public starred(Main main) {
        super(main, STORE, "Starred", "Your favourite PDFs",
                "Starred On", "Unstar", false, "Nothing starred yet — press Star while viewing a PDF.");
    }
}
