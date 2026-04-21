package vid.builder;

import java.nio.file.Path;

/**
 * Centralised filesystem paths used across the pipeline.
 *
 * <p>Each path can be overridden in {@code .env}; reasonable relative
 * defaults are used when an override is not supplied, so the project can
 * run from a fresh clone without machine-specific configuration.</p>
 *
 * <p>Environment keys:</p>
 * <ul>
 *   <li>{@code CHANNELS_ROOT} — root directory holding one subfolder per
 *       channel (each with {@code pending/}, {@code archive/},
 *       {@code upload_history.txt})</li>
 *   <li>{@code VIDRENDERER_DIR} — the Remotion project directory</li>
 *   <li>{@code GAMEPLAY_DIR} — folder containing long background gameplay
 *       clips ({@code back_1.mp4 … back_N.mp4})</li>
 *   <li>{@code NPX_PATH} — full path to the {@code npx} executable used to
 *       invoke Remotion (defaults to just {@code npx}, relying on
 *       {@code PATH})</li>
 * </ul>
 */
public final class AppPaths {
    /** Root folder containing per-channel output directories. */
    public static final Path CHANNELS_ROOT =
        Path.of(Env.get("CHANNELS_ROOT", "Channels"));

    /** Remotion project directory. */
    public static final Path VIDRENDERER_DIR =
        Path.of(Env.get("VIDRENDERER_DIR", "vidRenderer"));

    /** Directory of raw background gameplay source clips. */
    public static final Path GAMEPLAY_DIR = Path.of(Env.get(
        "GAMEPLAY_DIR",
        "videoBuilder/src/main/resources/Gameplay_stores"
    ));

    /** Executable used to run Remotion; defaults to {@code npx} on PATH. */
    public static final String NPX = Env.get("NPX_PATH", "npx");

    // Derived paths --------------------------------------------------------

    /** MP3 produced by {@code VidBuilder.voiceAct} and consumed by Whisper + Remotion. */
    public static final Path SPEECH_MP3 =
        VIDRENDERER_DIR.resolve("public").resolve("speech.mp3");

    /** Trimmed background clip Remotion layers beneath the narration. */
    public static final Path BACKGROUND_CLIP =
        VIDRENDERER_DIR.resolve("public").resolve("backgroundclip.mp4");

    /** Remotion captions JSON written by {@code VidBuilder.generateCaptions}. */
    public static final Path CAPTIONS_JSON =
        VIDRENDERER_DIR.resolve("src").resolve("remotion-captions.json");

    private AppPaths() {
        // Utility class — no instances.
    }

    /** Returns the {@code pending/} directory for the given channel. */
    public static Path pendingDir(String channel) {
        return CHANNELS_ROOT.resolve(channel).resolve("pending");
    }

    /** Returns the {@code archive/} directory for the given channel. */
    public static Path archiveDir(String channel) {
        return CHANNELS_ROOT.resolve(channel).resolve("archive");
    }

    /** Returns the upload-history file path for the given channel. */
    public static Path historyFile(String channel) {
        return CHANNELS_ROOT.resolve(channel).resolve("upload_history.txt");
    }
}
