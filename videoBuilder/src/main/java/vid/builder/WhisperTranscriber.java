package vid.builder;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thin client for OpenAI's Whisper speech-to-text endpoint. Produces a
 * verbose JSON response containing timestamped segments that are later
 * consumed by {@link VidBuilder#saveRemotionCaptions(String, String)} to
 * drive on-screen captions in the rendered short.
 */
public class WhisperTranscriber {
    /**
     * Transcribes the audio file at {@code audioFilePath} using the
     * {@code whisper-1} model in verbose-JSON mode so segment-level start /
     * end timestamps are included in the response.
     *
     * @param audioFilePath path to the audio file (MP3) to transcribe
     * @param API_KEY       OpenAI API key used for authentication
     * @return the raw verbose-JSON response body, or {@code null} on failure
     */
    public static String transcribe(String audioFilePath, String API_KEY) {
        try {
            var boundary = "----Boundary" + System.currentTimeMillis();

            var fileBytes = Files.readAllBytes(Path.of(audioFilePath));
            var multipart = new StringBuilder();

            // Prepare multipart request
            multipart.append("--").append(boundary).append("\r\n");
            multipart.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            multipart.append("whisper-1\r\n");
            //get timestamps
            multipart.append("--").append(boundary).append("\r\n");
            multipart.append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
            multipart.append("verbose_json\r\n");

            multipart.append("--").append(boundary).append("\r\n");
            multipart.append("Content-Disposition: form-data; name=\"file\"; filename=\"speech.mp3\"\r\n");
            multipart.append("Content-Type: audio/mpeg\r\n\r\n");

            var byteStream = new ByteArrayOutputStream();
            byteStream.write(multipart.toString().getBytes(StandardCharsets.UTF_8));
            byteStream.write(fileBytes);
            byteStream.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            // Build request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(byteStream.toByteArray()))
                .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());



            return response.body();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
