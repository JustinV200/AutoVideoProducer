//This code breaks down long background videos into small clips equal in length to the audio duration.
//It uses ffmpeg to clip the video and re-encode it without audio.

package vid.builder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class background_generator {

    public static double getAudioDurationSec(String audioPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            audioPath
        );
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        p.waitFor();
        return Double.parseDouble(new String(out).trim());
    }

    public static double getVideoDurationSec(String videoPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            videoPath
        );
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        p.waitFor();
        return Double.parseDouble(new String(out).trim());
    }

    /**
     * Clips only the video to match the audio duration, re-encodes it, and strips out all audio.
     */
    public static void clipVideoToAudio(
        String videoPath,
        String audioPath,
        String outputPath,
        String startOffset  // seconds as string, or null for start
    ) {
        try {
            // 1) Measure how long the speech is:
            double duration = getAudioDurationSec(audioPath);
            String durationStr = String.format(Locale.US, "%.3f", duration);

            // 2) Build ffmpeg command
            var cmd = new java.util.ArrayList<String>();
            cmd.add("ffmpeg");
            cmd.add("-y");  // overwrite existing
            if (startOffset != null && !startOffset.isBlank()) {
                cmd.add("-ss");
                cmd.add(startOffset);
            }
            cmd.add("-i");
            cmd.add(videoPath);
            cmd.add("-t");
            cmd.add(durationStr);

            // 3) Re-encode video + strip audio:
            cmd.addAll(List.of(
                "-c:v", "libx264",
                "-preset", "fast",
                "-profile:v", "baseline",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                "-an",               //  drop audio track entirely
                outputPath
            ));

            // 4) Run it
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader r = new BufferedReader(
                     new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                r.lines().forEach(System.out::println);
            }
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new RuntimeException("FFmpeg failed with exit code " + exit);
            }
            System.out.println("Background clip generated" + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Picks a random segment from the video and calls the above method.
     */
    public static void clipVideoToAudioRandomStart(
        String videoPath,
        String audioPath,
        String outputPath
    ) {
        try {
            double videoDur = getVideoDurationSec(videoPath);
            double audioDur = getAudioDurationSec(audioPath);
            double maxStart = Math.max(0, videoDur - audioDur);
            double startSec = new Random().nextDouble() * maxStart;
            String startOffset = String.format(Locale.US, "%.2f", startSec);

            System.out.println("🎯 Random start time: " + startOffset + "s");
            clipVideoToAudio(videoPath, audioPath, outputPath, startOffset);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
