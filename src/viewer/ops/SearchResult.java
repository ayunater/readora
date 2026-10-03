package viewer.ops;

/**
 * A single match produced by an in-document text search.
 *
 * Bundles the page it was found on, a short snippet of surrounding text
 * (shown to the user in the search results list), and the
 * {@link HighlightArea} describing where on the page to draw the
 * highlight when the user jumps to this result.
 *
 * Instances are created by {@link SearchStripper} while scanning the
 * document, and consumed by the search results list in
 * {@code pdfViewer}, which relies on {@link #toString()} for its
 * default list-cell display text.
 */
public class SearchResult {

    /** 1-based page number the match was found on (see HighlightArea for indexing details). */
    private int page;

    /** Short excerpt of text surrounding the match, for display in the results list. */
    private String snippet;

    /** Location of the match on the page, used to draw a highlight when jumped to. */
    private HighlightArea highlight;

    /**
     * Creates a search result.
     *
     * @param page     1-based page number the match occurred on
     * @param snippet  short excerpt of text around the match
     * @param highlight location of the match on the page
     */
    public SearchResult(int page,
                        String snippet,
                        HighlightArea highlight) {

        this.page = page;
        this.snippet = snippet;
        this.highlight = highlight;
    }

    /** @return the 1-based page number this match was found on */
    public int getPage() {
        return page;
    }

    /** @return a short excerpt of text surrounding the match */
    public String getSnippet() {
        return snippet;
    }

    /** @return the location on the page to highlight for this match */
    public HighlightArea getHighlight() {
        return highlight;
    }

    /**
     * Used directly as the display text when this result is rendered in
     * a {@code JList} of search results.
     */
    @Override
    public String toString(){
        return "Page " + page + "  —  " + snippet;
    }
}
