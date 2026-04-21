package vid.builder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * High-level façade that stitches together the individual steps required to
 * produce a single short-form video:
 *
 * <ol>
 *   <li>{@link #scriptWriter(String)} — ask GPT-4o (via {@link AIscraper})
 *       to write the narration script.</li>
 *   <li>{@link #voiceAct(String)} — call the OpenAI TTS endpoint and save
 *       the audio to {@link AppPaths#SPEECH_MP3}.</li>
 *   <li>{@link #generateBackground()} — pick a random gameplay clip and
 *       trim it to match the audio length via {@link BackgroundGenerator}.</li>
 *   <li>{@link #generateCaptions()} — transcribe the speech with
 *       {@link WhisperTranscriber} and emit a Remotion-friendly JSON file.</li>
 * </ol>
 *
 * <p>The {@code OPENAI_API_KEY} is read through {@link Env}.</p>
 */
public class VidBuilder {

    private static final String API_KEY = Env.get("OPENAI_API_KEY");
    private static final String TTS_ENDPOINT = "https://api.openai.com/v1/audio/speech";
    private static final int MAX_TTS_CHARS = 3000;

    /** Default prompt used when {@link #scriptWriter()} is called with no theme. */
    private static final String DEFAULT_PROMPT =
        "Search Social Media platforms for an interesting story/post, that would take " +
        "under 45 seconds but over 20 seconds for a very slow text to speech bot to read, " +
        "and restate it verbatim, from the perspective of the author, do this immediately, " +
        "do not start with anything besides the title of the post, and end with the phrase: " +
        "remember to like and subscribe!";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** Generates a script using the built-in default prompt. */
    public SearchResult scriptWriter() {
        return scriptWriter(DEFAULT_PROMPT);
    }

    /** Generates a script using a caller-supplied prompt or theme. */
    public SearchResult scriptWriter(String theme) {
        return new AIscraper().search(theme);
    }

    /**
     * Picks a random background gameplay clip and trims it to match the
     * current narration length, writing the result to
     * {@link AppPaths#BACKGROUND_CLIP}.
     */
    public void generateBackground() {
        String[] clips = {
            "back_1.mp4", "back_2.mp4", "back_3.mp4", "back_4.mp4", "back_5.mp4",
            "back_6.mp4", "back_7.mp4", "back_8.mp4", "back_9.mp4", "back_10.mp4"
        };
        String chosen = AppPaths.GAMEPLAY_DIR
            .resolve(clips[new Random().nextInt(clips.length)])
            .toString();

        BackgroundGenerator.clipVideoToAudioRandomStart(
            chosen,
            AppPaths.SPEECH_MP3.toString(),
            AppPaths.BACKGROUND_CLIP.toString()
        );
    }

    /**
     * Transcribes {@link AppPaths#SPEECH_MP3} with Whisper and writes a
     * Remotion-compatible captions JSON to {@link AppPaths#CAPTIONS_JSON}.
     */
    public void generateCaptions() {
        String whisperJson = WhisperTranscriber.transcribe(AppPaths.SPEECH_MP3.toString(), API_KEY);
        saveRemotionCaptions(whisperJson, AppPaths.CAPTIONS_JSON.toString());
    }

    /**
     * Synthesises narration audio for {@code script} using the OpenAI TTS
     * endpoint and writes the MP3 to {@link AppPaths#SPEECH_MP3}. Scripts
     * longer than {@value #MAX_TTS_CHARS} characters are truncated.
     */
    public void voiceAct(String script) {
        try {
            if (script.length() > MAX_TTS_CHARS) {
                script = script.substring(0, MAX_TTS_CHARS);
            }

            JSONObject json = new JSONObject()
                .put("model", "tts-1")
                .put("input", script)
                .put("voice", "shimmer")
                .put("speed", 1.3);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TTS_ENDPOINT))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

            HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            Files.createDirectories(AppPaths.SPEECH_MP3.getParent());
            Files.write(AppPaths.SPEECH_MP3, response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Converts the verbose-JSON response from Whisper into the compact
     * {@code [{start,end,text}]} format consumed by the Remotion
     * composition, and writes it to {@code outputPath}.
     */
    public static void saveRemotionCaptions(String jsonResponse, String outputPath) {
        try {
            JSONObject root = new JSONObject(jsonResponse);
            JSONArray segments = root.getJSONArray("segments");
            JSONArray output = new JSONArray();

            for (int i = 0; i < segments.length(); i++) {
                JSONObject seg = segments.getJSONObject(i);
                output.put(new JSONObject()
                    .put("start", seg.getDouble("start"))
                    .put("end", seg.getDouble("end"))
                    .put("text", seg.getString("text")));
            }

            Files.writeString(AppPaths.CAPTIONS_JSON, output.toString(2), StandardCharsets.UTF_8);
            System.out.println("✅ remotion-captions.json created: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
