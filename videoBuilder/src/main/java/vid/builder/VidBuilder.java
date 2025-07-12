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



public class VidBuilder {
    static private final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String prompt = "Search Social Media platforms for an interesting story/post, that would take under 45 seconds but over 20 seconds for a very slow text to speech bot to read, and restate it verbatim, from the perspective of the author, do this immediately, do not start with anything besides the title of the post, and end with the phrase: rememebr to like and subscribe!";
    private final HttpClient httpClient;

    public VidBuilder() {
        this.httpClient = HttpClient.newHttpClient();
    }

  public SearchResult scriptWriter() {
        return generateResponse(prompt);
    }

    // Overloaded method: script writer with customizable scripts
    public SearchResult scriptWriter(String theme) {
        return generateResponse(theme);
    }

    //  common logic for overloaded methods
    private SearchResult generateResponse(String userPrompt) {
        AIscraper searcher = new AIscraper();
        SearchResult result = searcher.search(userPrompt);
        return result;
    }

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

    public void generate_captions(){
        saveRemotionCaptions(WhisperTranscriber.transcribe("vidRenderer\\public\\speech.mp3", API_KEY), "vidRenderer\\src\\remotion-captions.json");
    }

   
    @SuppressWarnings("UseSpecificCatch")
    //generates speech.mp3
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


private String toJsonString(String text) {
    return "\"" + text.replace("\"", "\\\"") + "\"";
}

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