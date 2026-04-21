package vid.builder;

/**
 * Immutable value object returned by {@link AIscraper} representing a
 * generated script. {@link #title} is used to derive the rendered file
 * name, while {@link #text} is the narration body.
 */
public class SearchResult {
    public final String title;
    public final String text;

    public SearchResult(String title, String text) {
        this.title = title;
        this.text = text;
    }
}
