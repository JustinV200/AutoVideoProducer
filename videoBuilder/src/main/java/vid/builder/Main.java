package vid.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Main {
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: java vid.builder.Main <channelName|all> <repeatCount>");
      System.exit(1);
    }

    // ▶︎ 1) Read args
    String channelArg = args[0].trim();
    int repeatCount;
    try {
      repeatCount = Integer.parseInt(args[1].trim());
    } catch (NumberFormatException e) {
      System.err.println("Invalid number of videos—defaulting to 3.");
      repeatCount = 3;
    }

    // ▶︎ Your configured channels & prompts
    Map<String, String> channelPrompts = Map.of(
      "Channel_1", """
          Search Reddit and select a real and tending post that fits these criteria, then repeat the story verbatum, as the author wrote it.

          - The story should be short: readable aloud in 45-80 seconds with a  text-to-speech voice.
          - Use proper grammar, punctuation, and capitalization. Avoid slang, abbreviations, or informal tone.
          - Do not include links, usernames, subreddits, or external references.
          - The post must be in English and appropriate for a wide audience — no personal info or explicit content.
          - Choose a post with high engagement (many comments or upvotes) from a popular subreddit known for storytelling (e.g., AITA, r/confession, r/tifu, r/pettyrevenge).
          - Prefer stories with negative or controversial themes (e.g., social conflict, betrayal, drama) — these perform better in short-form video.
          - Start only with the title of the post.
          -try to avoid the topic of engagnments or weddings, as these are overused. family drama, relationship drama, or personal conflicts are better.
          - try to find posts where one person is very cleary in the wrong, or where there is a clear victim and antagonist. we want to viewer to feel outraged
          - End with the phrase: “Remember to like and subscribe!”
  
      """,
      "Channel_2", """
     Find a verified breaking news story from the past 24–48 hours that is unusual, inspiring, emotional, or shocking. Write a short script in under 75 words, formatted for a YouTube Shorts video with a fast, punchy tone.

Structure it like this:

Hook (1 sentence to grab attention)

Key facts (3–5  sentences with real info)

Punchline or twist (1 sentence to leave viewers thinking or reacting)

Avoid fictionalizing or embellishing. Just rephrase the real story in a snappy way. Do not include any links, usernames, or external references.
      """,
      
      "Channel_3", """
        Find an interesting short story that is popular online and tell it in an engaging way that feels like a human would have wrote it
        , dont use any weird intro sentences like "Did you know that..." or "Today I learned that...", just get right into it. This will
        be used in an automated youtube video, so dont include any text that would not make sense to be read aloud by a text-to-speech voice.
       """,
      "Channel_4",
      
      """

Search Reddit for a real, highly upvoted post from the past 7 days that shares a fascinating, surprising, or weird fact — the kind that makes people say, “No way that’s real” or “I never knew that.”

Use posts from subreddits like:
- r/interestingasfuck
- r/todayilearned
- r/Damnthatsinteresting
- r/coolguides
- r/mildlyinteresting
- r/UnbelievableFacts

Repeat the original poster’s words *as closely as possible*. You may:
- Trim parts of the post to stay under the word/time limit.
- Lightly paraphrase awkward grammar.
- Remove any text that is too long, off-topic, or visually focused (like image descriptions).
- remove anty links, usernames, or subreddit names.

**Absolutely DO NOT include**:
- Any links, usernames, subreddit names, or source citations.
- Phrases like “according to...” or “source says...”
- Boring intros like “Did you know that...”, or "Today I learned that...", ect, just get right to the point.

**Format**:
- Speak in natural, casual English as if you're the original poster.
- Length: **120-160 words max**
- Read time: **no more than 45 seconds** with a slow text-to-speech voice.
- End with: “Which one blew your mind? Like and subscribe if you want more.”

Only return the final video script — no extra commentary or explanations.
      """, 
      "Channel_5", 
      
      """
Search for a real internet moment, meme, controversy, or viral digital trend that made a lasting impression on online culture — something that defined a specific moment in internet history.

It must be real, notable, and verifiable — but avoid the most overused stories.

 Do NOT use the following topics (these are too common):
- The Dress (blue/black)
- Keyboard Cat, Rickroll, Charlie Bit Me
- Harambe, Gangnam Style, Ice Bucket Challenge
- Logan Paul’s forest video, TanaCon

Instead, think of:
- Forgotten viral events
- Creator meltdowns
- Subreddit shutdowns
- Strange trends or community-wide pranks
- Meme origins that aren’t in the top 5 ever

Step 1: Briefly consider 3 candidate events from different years and internet spaces.

Step 2: Choose the most interesting one and retell it like a mini docudrama, under 130 words total.

do NOT include any reference to the three events you considered, just the final story.

Requirements:
- No fiction. No usernames, hashtags, links, or subreddit names.
- Use short, vivid sentences in spoken, natural English.
- Start with a strong hook, then build tension with 3–5 lines of context.
- End with a punchline or twist, followed by this exact line:
  “Like and sub for more digital lore.”

      """
    );

    // ▶︎ Determine which channels to process
    List<String> channelsToRun = new ArrayList<>();
    if (channelArg.equalsIgnoreCase("all")) {
      channelsToRun.addAll(channelPrompts.keySet());
    } else if (channelPrompts.containsKey(channelArg)) {
      channelsToRun.add(channelArg);
    } else {
      System.err.println("Unknown channel: " + channelArg);
      System.err.println("Valid channels: " + channelPrompts.keySet());
      return;
    }

    //  Loop and generate
    for (String channelName : channelsToRun) {
      String basePrompt = channelPrompts.get(channelName);

      // read history so you don’t repeat stories
      String history = readUploadHistory(
        Path.of("D:/AutoVideoProducer/Channels", channelName, "upload_history.txt")
      );

      for (int i = 0; i < repeatCount; i++) {
        String fullPrompt = buildPrompt(basePrompt, history);
        generateVid(fullPrompt, channelName);
      }
    }

  }

  private static String readUploadHistory(Path historyFile) {
    try {
      if (Files.exists(historyFile)) {
        List<String> lines = Files.readAllLines(historyFile);
        return String.join("\n", lines);
      }
    } catch (IOException e) {
      System.err.println("⚠️ Could not read history for " + historyFile + ": " + e);
    }
    return "";
  }

  private static String buildPrompt(String basePrompt, String history) {
    if (history.isBlank()) return basePrompt;
    return """
      Also, remember not to include any links or usernames. This will be 
      read aloud by a text-to-speech voice, so use natural, spoken English.

      Also, viewer retention is key, so keep it engaging and concise.

      Reminder: ****** NO links, usernames, or external references. ******
      think everything is going to be read aloud, so links would not make sense. DO NOT INCLUDE SOURCES, LINKS, OR USERNAMES.
      Do not include any personal information, usernames, or links to external sites. doing so could get you shut down by YouTube.
      Use proper grammar, punctuation, and capitalization.

      Previously uploaded videos (timestamp, filename):
      %s


      Now generate a *new* story—do NOT repeat any of the above.
      %s
      """.formatted(history, basePrompt);
  }

  public static void generateVid(String prompt, String channelName) {
    System.out.println(" vidbuilder starting for channel " + channelName + "…");
    VidBuilder builder = new VidBuilder();

    System.out.println("  • Generating script…");
    SearchResult script = builder.scriptWriter(prompt);
    System.out.println("    title: " + script.title);
    System.out.println("    text : " + script.text);

    System.out.println("  • Generating audio…");
    builder.voiceAct(script.text);

    System.out.println("  • Generating background clip…");
    builder.generate_background(channelName);

    System.out.println("  • Generating captions…");
    builder.generate_captions();

    System.out.println("  • Rendering final video…");
    Path captionsJson = Path.of("D:/autoVideoProducer/vidRenderer/src/remotion-captions.json");
    Path pendingDir   = Path.of("D:/autoVideoProducer/Channels", channelName, "pending");
    Renderer.renderFinalVideo(captionsJson.toString(), pendingDir.toString());

    System.out.println("✅ Video rendered → “" + pendingDir + "”\n");
  }
}
