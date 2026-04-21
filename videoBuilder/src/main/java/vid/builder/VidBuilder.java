package vid.builder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 *       the audio to {@code vidRenderer/public/speech.mp3}.</li>
 *   <li>{@link #generate_background(String)} — pick a random gameplay clip
 *       and trim it to match the audio length via {@link background_generator}.</li>
 *   <li>{@link #generate_captions()} — transcribe the speech with
 *       {@link WhisperTranscriber} and emit a Remotion-friendly JSON file.</li>
 * </ol>
 *
 * <p>The {@code OPENAI_API_KEY} is read through {@link Env} so it can be
 * provided either via a {@code .env} file or a real environment variable.</p>
 */
public class VidBuilder {
    private static final String API_KEY = Env.get("OPENAI_API_KEY");
    /** Default prompt used when {@link #scriptWriter()} is called with no theme. */
    private static final String prompt = "Search Social Media platforms for an interesting story/post, that would take under 45 seconds but over 20 seconds for a very slow text to speech bot to read, and restate it verbatim, from the perspective of the author, do this immediately, do not start with anything besides the title of the post, and end with the phrase: rememebr to like and subscribe!";
    private final HttpClient httpClient;

    public VidBuilder() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /** Generates a script using the built-in default prompt. */
    public SearchResult scriptWriter() {
        return generateResponse(prompt);
    }

    /**
     * Generates a script using a caller-supplied prompt or theme.
     *
     * @param theme the complete prompt to send to GPT-4o
     */
    public SearchResult scriptWriter(String theme) {
        return generateResponse(theme);
    }

    /** Shared implementation used by both {@code scriptWriter} overloads. */
    private SearchResult generateResponse(String userPrompt) {
        AIscraper searcher = new AIscraper();
        return searcher.search(userPrompt);
    }

    /**
     * Picks a random background gameplay clip and trims it to match the
     * current narration length, writing the result to
     * {@code vidRenderer/public/backgroundclip.mp4}.
     *
     * @param Channel name of the channel being rendered (reserved for future
     *                per-channel clip libraries; currently unused)
     */
    public void generate_background(String Channel) {
        String chosenVideo = null;
        String[] videos = {
            "back_1.mp4", "back_2.mp4", "back_3.mp4",
            "back_4.mp4", "back_5.mp4", "back_6.mp4",
            "back_7.mp4", "back_8.mp4", "back_9.mp4", "back_10.mp4",
        };

        Random rand = new Random();
        int index = rand.nextInt(videos.length);

        chosenVideo = "D:\\autoVideoProducer\\videoBuilder\\src\\main\\resources\\Gameplay_stores\\" + videos[index];

        background_generator.clipVideoToAudioRandomStart(chosenVideo, "vidRenderer\\public\\speech.mp3", "D:\\autoVideoProducer\\vidRenderer\\public\\backgroundclip.mp4");


    }

    /**
     * Transcribes {@code speech.mp3} with Whisper and writes a Remotion-
     * compatible captions JSON to {@code vidRenderer/src/remotion-captions.json}.
     */
    public void generate_captions() {
        saveRemotionCaptions(
            WhisperTranscriber.transcribe("vidRenderer\\public\\speech.mp3", API_KEY),
            "vidRenderer\\src\\remotion-captions.json"
        );
    }

    /**
     * Synthesises narration audio for the given script using the OpenAI TTS
     * endpoint and writes the MP3 to {@code vidRenderer/public/speech.mp3}.
     * Scripts longer than 3000 characters are truncated to stay within the
     * API limit.
     *
     * @param script narration text to be spoken aloud
     */
    @SuppressWarnings("UseSpecificCatch")
    public void voiceAct(String script) {
    try {
        if (script.length() > 3000) {
            script = script.substring(0, 3000); // avoid API limits
        }

        JSONObject json = new JSONObject()
            .put("model", "tts-1")
            .put("input", script)
            .put("voice", "shimmer")
            .put("speed", 1.3);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/audio/speech"))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(Path.of("vidRenderer\\public\\speech.mp3"), response.body());

    } catch (Exception e) {
        e.printStackTrace();
    }
}


/**
 * Converts the verbose-JSON response from Whisper into the compact
 * {@code [{start,end,text}]} array format consumed by the Remotion
 * composition, and writes it to {@code outputPath}.
 *
 * @param jsonResponse raw Whisper verbose-JSON response body
 * @param outputPath   destination path for the generated captions file
 */
public static void saveRemotionCaptions(String jsonResponse, String outputPath) {
    try {
        JSONObject root = new JSONObject(jsonResponse);
        JSONArray segments = root.getJSONArray("segments");

        JSONArray outputCaptions = new JSONArray();

        for (int i = 0; i < segments.length(); i++) {
            JSONObject seg = segments.getJSONObject(i);

            JSONObject caption = new JSONObject();
            caption.put("start", seg.getDouble("start"));
            caption.put("end", seg.getDouble("end"));
            caption.put("text", seg.getString("text"));

            outputCaptions.put(caption);
        }

        // Write to file
        Files.writeString(Path.of(outputPath), outputCaptions.toString(2), StandardCharsets.UTF_8);
        System.out.println("✅ remotion-captions.json created: " + outputPath);

    } catch (Exception e) {
        e.printStackTrace();
    }
}

}