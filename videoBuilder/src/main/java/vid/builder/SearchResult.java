package vid.builder;

/**
 * Immutable value object returned by {@link AIscraper} representing a
 * generated script. {@code title} is used to derive the rendered file
 * name, while {@code text} is the narration body.
 */
public record SearchResult(String title, String text) {
}
