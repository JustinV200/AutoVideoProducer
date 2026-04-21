package vid.builder;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Thin wrapper around {@link Dotenv} that loads a project-root {@code .env}
 * file once and exposes a simple {@link #get(String)} API.
 *
 * <p>{@code dotenv-java} is configured to search upward from the working
 * directory so it works regardless of which module is executed. Missing
 * files and malformed lines are tolerated, and real process environment
 * variables take precedence over values in the file — matching the
 * behaviour most developers expect from a {@code .env} workflow.</p>
 *
 * <p>See {@code .env.example} at the repo root for the full list of keys
 * the application consumes.</p>
 */
public final class Env {
    private static final Dotenv DOTENV = Dotenv.configure()
        .ignoreIfMissing()
        .ignoreIfMalformed()
        .load();

    private Env() {
        // Utility class — no instances.
    }

    /**
     * Returns the value associated with {@code key}. Real process
     * environment variables win over values stored in {@code .env}; if
     * neither is set, {@code null} is returned.
     */
    public static String get(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        return DOTENV.get(key);
    }

    /**
     * Returns the value for {@code key} or {@code defaultValue} when no
     * value is configured.
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
