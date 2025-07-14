package vid.builder;
// This class uses OpenAI's GPT-4o to scrape web search results and extract titles and text. Creating the video script. Prompts can be found in Main.java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

public class AIscraper {
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";

    private final HttpClient httpClient;

    public AIscraper() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Sends a prompt to GPT-4o using the web_search_preview tool.
     * Returns the extracted title and text.
     */
    public SearchResult search(String prompt) {
        try {
            JSONObject payload = new JSONObject()
                .put("model", "gpt-4o")
                .put("input", prompt)
                .put("tools", new JSONArray()
                    .put(new JSONObject().put("type", "web_search_preview"))
                );

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(BodyPublishers.ofString(payload.toString()))
                .build();

            HttpResponse<String> response = httpClient.send(
                req,
                HttpResponse.BodyHandlers.ofString()
            );

            return extractTitleAndText(response.body());

        } catch (Exception e) {
            e.printStackTrace();
            return new SearchResult("Error", "Error occurred: " + e.getMessage());
        }
    }

    /**
     * Parses the JSON and returns a SearchResult with title and text.
     */
    private SearchResult extractTitleAndText(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray outputArray = root.getJSONArray("output");
            for (int i = 0; i < outputArray.length(); i++) {
                JSONObject outputItem = outputArray.getJSONObject(i);
                if ("message".equals(outputItem.optString("type"))) {
                    JSONArray contentArray = outputItem.getJSONArray("content");
                    for (int j = 0; j < contentArray.length(); j++) {
                        JSONObject contentItem = contentArray.getJSONObject(j);
                        if ("output_text".equals(contentItem.optString("type"))) {
                            String text = contentItem.optString("text");
                            JSONArray annotations = contentItem.optJSONArray("annotations");
                            String title = (annotations != null && annotations.length() > 0)
                                ? annotations.getJSONObject(0).optString("title")
                                : "No Title";
                            return new SearchResult(title, text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            return new SearchResult("Error", "Error parsing JSON: " + e.getMessage());
        }
        return new SearchResult("Not found", "No valid output found in response.");
    }
}
