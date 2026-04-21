package vid.builder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONArray;

/**
 * Invokes Remotion (via {@code npx remotion render}) to turn the
 * background clip, speech audio, and captions JSON into the final MP4.
 *
 * <p>The output file is named after the first caption line so that the
 * upload step can use it as the video title.</p>
 */
public class Renderer {
  /**
   * Renders the {@code CaptionedShort} composition to an MP4 in
   * {@code outputDirectory}, using the captions at {@code captionsJsonPath}
   * as the Remotion input props.
   *
   * @param captionsJsonPath path to a Remotion captions JSON file
   * @param outputDirectory  directory where the final MP4 will be written
   */
  public static void renderFinalVideo(String captionsJsonPath, String outputDirectory) {
    try {
      //  Read captions, grab first line for filename
      JSONArray captions = new JSONArray(
        Files.readString(Path.of(captionsJsonPath), StandardCharsets.UTF_8)
      );
      String firstCaption = captions.length() > 0
        ? captions.getJSONObject(0).optString("text", "untitled")
        : "untitled";

      // Sanitize and limit length
      String safeTitle = sanitizeForFilename(firstCaption);
      if (safeTitle.isEmpty()) safeTitle = "untitled";
      String outputFilename = safeTitle + ".mp4";

      // Build output and props absolute paths
      Path outputPath = Path.of(outputDirectory, outputFilename);
      String outputPathStr = outputPath.toAbsolutePath().toString();
      String propsPath = Path.of(captionsJsonPath).toAbsolutePath().toString();

      System.out.println("Output: " + outputPathStr);
      System.out.println("Props:  " + propsPath);

      // call Remotion:
      ProcessBuilder pb = new ProcessBuilder(
        "C:\\Program Files\\nodejs\\npx.cmd",
        "remotion",
        "render",
        "CaptionedShort",           // composition ID
        outputPathStr,              // output .mp4
        "--props=" + propsPath      // captions JSON
      );
      pb.directory(new File("D:/autoVideoProducer/vidRenderer"));
      pb.inheritIO();

      // run and check exit code
      int exit = pb.start().waitFor();
      if (exit == 0) {
        System.out.println("✅ Rendered: " + outputFilename);
      } else {
        System.err.println("❌ Remotion failed with exit code: " + exit);
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static String sanitizeForFilename(String input) {
    String cleaned = input
      .replaceAll("[\\\\/:*?\"<>|$]", "")
      .trim()
      .replaceAll("\\s+", "_");
    return cleaned.length() > 50
      ? cleaned.substring(0, 50)
      : cleaned;
  }
}
