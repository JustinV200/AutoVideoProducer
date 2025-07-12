package vid.manager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChannelScheduler {
  private final String channelName;
  private final Path pendingDir;
  private final Path archiveDir;
  private final Path historyFile;
  private final String clientId;
  private final String clientSecret;
  private final String userEmail;
  private final ScheduledExecutorService executor;
  private final Map<String, Instant> nextScheduledMap;
  private final Set<Path> scheduledVideos;

  public ChannelScheduler(Path baseChannelsDir,
                          String channelName,
                          Main.ChannelConfig cfg,
                          ScheduledExecutorService executor,
                          Map<String, Instant> nextScheduledMap,
                          Set<Path> scheduledVideos) throws IOException {
    this.channelName      = channelName;
    this.pendingDir       = baseChannelsDir.resolve(channelName).resolve("pending");
    this.archiveDir       = baseChannelsDir.resolve(channelName).resolve("archive");
    this.historyFile      = baseChannelsDir.resolve(channelName).resolve("upload_history.txt");
    this.clientId         = cfg.clientId();
    this.clientSecret     = cfg.clientSecret();
    this.userEmail        = cfg.email();
    this.executor         = executor;
    this.nextScheduledMap = nextScheduledMap;
    this.scheduledVideos  = scheduledVideos;

    Files.createDirectories(pendingDir);
    Files.createDirectories(archiveDir);
    Files.createDirectories(historyFile.getParent());
  }

  public void scheduleAll() throws IOException {
    List<Path> videos = Files.list(pendingDir)
      .filter(p -> p.toString().endsWith(".mp4"))
      .map(Path::toAbsolutePath)
      .map(Path::normalize)
      .sorted(Comparator.naturalOrder())
      .collect(Collectors.toList());

    if (videos.isEmpty()) {
      System.out.println("No pending videos for channel “" + channelName + "”");
      return;
    }

    Instant now = Instant.now();
    Instant firstScheduled;

    if (!Files.exists(historyFile) || Files.readAllLines(historyFile, StandardCharsets.UTF_8).isEmpty()) {
      firstScheduled = now;
    } else {
      Instant lastUpload  = readLastUploadTime();
      Instant nextAllowed = lastUpload.plus(Duration.ofHours(5));
      firstScheduled      = nextAllowed.isAfter(now) ? nextAllowed : now;
    }

    // if first video already scheduled in the future, skip entire batch
    Instant existing = nextScheduledMap.get(channelName);
    if (existing != null && existing.isAfter(now)) {
      System.out.printf("⚠️ [%s] batch already scheduled at %s, skipping%n",
        channelName,
        existing.atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0));
      return;
    }

    for (int i = 0; i < videos.size(); i++) {
      Path video = videos.get(i);

      // skip if already in-flight
      if (!scheduledVideos.add(video)) {
        System.out.printf("⚠️ Already scheduled: %s%n", video.getFileName());
        continue;
      }

      Instant scheduledTime = firstScheduled.plus(Duration.ofHours(5L * i));
      long delayMs = Math.max(Duration.between(now, scheduledTime).toMillis(), 0);

      // schedule upload
      executor.schedule(() -> uploadAndArchive(video, scheduledTime),
                        delayMs, TimeUnit.MILLISECONDS);

      // record next-upload = first video's time
      if (i == 0) {
        nextScheduledMap.put(channelName, scheduledTime);
      }

      System.out.printf("  • Scheduled %s at %s%n",
        video.getFileName(),
        scheduledTime.atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0));
    }
  }

  private Instant readLastUploadTime() throws IOException {
    List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
    String lastLine = lines.get(lines.size() - 1);
    String timestamp = lastLine.split(",", 2)[0];
    return Instant.parse(timestamp);
  }

  private void uploadAndArchive(Path video, Instant scheduledTime) {
    String filename = video.getFileName().toString();
    String title    = filename.substring(0, filename.length() - 4).replace('_', ' ');

    try {
      System.out.printf("🚀 Uploading %s%n", filename);

      YouTubeUploader.uploadVideo(
        video.toString(),
        title,
        "",
        "public",
        clientId,
        clientSecret,
        userEmail
      );

      // move file to archive (retry if locked)
      Path moved = archiveDir.resolve(filename);
      for (int retry = 0; retry < 5; retry++) {
        try {
          Files.move(video, moved, StandardCopyOption.REPLACE_EXISTING);
          break;
        } catch (IOException e) {
          if (retry == 4) throw e;
          Thread.sleep(200);
        }
      }

      // append timestamp to history
      String record = scheduledTime + "," + filename + "\n";
      Files.write(historyFile,
                  record.getBytes(StandardCharsets.UTF_8),
                  StandardOpenOption.CREATE,
                  StandardOpenOption.APPEND);

      System.out.printf("✔ Uploaded & archived %s%n", filename);

      // schedule next-upload map to the *next* video time
      Instant nextUpload = scheduledTime.plus(Duration.ofHours(5));
      // only if there are still pending videos
      boolean hasMore = Files.list(pendingDir).anyMatch(p -> p.toString().endsWith(".mp4"));
      if (hasMore) {
        nextScheduledMap.put(channelName, nextUpload);
      } else {
        nextScheduledMap.remove(channelName);
      }

    } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
      boolean quotaHit = e.getDetails() != null &&
        e.getDetails().getErrors().stream()
          .anyMatch(err -> "uploadLimitExceeded".equals(err.getReason()));
      if (quotaHit) {
        System.err.printf("🚫 Quota hit on %s — rescheduling batch for tomorrow%n", filename);
        rescheduleBatchForTomorrow(video);
        return;
      }
      e.printStackTrace();
      System.err.printf("‼ API error on %s: %s%n", filename, e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      System.err.printf("‼ Unexpected error on %s: %s%n", filename, e.getMessage());
    } finally {
      // remove from in-flight
      scheduledVideos.remove(video);
    }
  }

  private void rescheduleBatchForTomorrow(Path failedVideo) {
    try {
      List<Path> all = Files.list(pendingDir)
        .filter(p -> p.toString().endsWith(".mp4"))
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toList());

      int idx = all.indexOf(failedVideo);
      if (idx < 0) return;

      Instant base = Instant.now().plus(Duration.ofDays(1));
      for (int i = idx; i < all.size(); i++) {
        Path vid = all.get(i);
        Instant when = base.plus(Duration.ofHours(5L * (i - idx)));
        long delay = Math.max(Duration.between(Instant.now(), when).toMillis(), 0);

        executor.schedule(() -> uploadAndArchive(vid, when),
                          delay, TimeUnit.MILLISECONDS);

        scheduledVideos.add(vid);  // mark in-flight

        // update only first rescheduled
        if (i == idx) {
          nextScheduledMap.put(channelName, when);
        }

        System.out.printf("🔁 Rescheduled %s for %s%n",
          vid.getFileName(),
          when.atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0));
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public void uploadAllNow() throws IOException {
    List<Path> videos = Files.list(pendingDir)
      .filter(p -> p.toString().endsWith(".mp4"))
      .map(Path::toAbsolutePath)
      .map(Path::normalize)
      .sorted(Comparator.naturalOrder())
      .collect(Collectors.toList());

    for (Path video : videos) {
      uploadAndArchive(video, Instant.now());
    }
  }
}
