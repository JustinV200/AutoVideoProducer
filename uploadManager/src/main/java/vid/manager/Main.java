package vid.manager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import vid.builder.Env;

/**
 * Entry point for the upload side of AutoVideoProducer.
 *
 * <p>For every configured channel this class:</p>
 * <ul>
 *   <li>Watches the {@code Channels/&lt;name&gt;/pending} folder for finished
 *       MP4s.</li>
 *   <li>Triggers {@link vid.builder.Main} to generate additional videos when
 *       the pending queue drops below five clips.</li>
 *   <li>Delegates actual uploading and archiving to a per-channel
 *       {@link ChannelScheduler}, spacing uploads five hours apart.</li>
 * </ul>
 *
 * <p>Credentials are read through {@link Env} so they may be supplied from a
 * project-root {@code .env} file or from real process environment variables.
 * See {@code .env.example} for the full list of required keys.</p>
 */
public class Main {
  /**
   * Per-channel OAuth credentials. A separate client ID/secret pair is used
   * for each channel so uploads can run in parallel without hitting a single
   * API quota.
   */
  public record ChannelConfig(String clientId,
                              String clientSecret,
                              String email) {}

  private static final Map<String, Instant> nextScheduledMap = new ConcurrentHashMap<>();
  private static final Set<Path> scheduledVideos = ConcurrentHashMap.newKeySet();

  public static void main(String[] args) throws Exception {
    // Root folder that holds one subdirectory per channel.
    Path channelsRoot = Path.of("D:/AutoVideoProducer/Channels");

    // Per-channel credentials. Populate the matching keys in .env.
    Map<String, ChannelConfig> channels = Map.of(
      "Channel_1", new ChannelConfig(
        Env.get("CHANNEL_1_CLIENT_ID"),
        Env.get("CHANNEL_1_CLIENT_SECRET"),
        Env.get("CHANNEL_1_EMAIL")
      ),
      "Channel_2", new ChannelConfig(
        Env.get("CHANNEL_2_CLIENT_ID"),
        Env.get("CHANNEL_2_CLIENT_SECRET"),
        Env.get("CHANNEL_2_EMAIL")
      ),
      "Channel_3", new ChannelConfig(
        Env.get("CHANNEL_3_CLIENT_ID"),
        Env.get("CHANNEL_3_CLIENT_SECRET"),
        Env.get("CHANNEL_3_EMAIL")
      ),
      "Channel_4", new ChannelConfig(
        Env.get("CHANNEL_4_CLIENT_ID"),
        Env.get("CHANNEL_4_CLIENT_SECRET"),
        Env.get("CHANNEL_4_EMAIL")
      ),
      "Channel_5", new ChannelConfig(
        Env.get("CHANNEL_5_CLIENT_ID"),
        Env.get("CHANNEL_5_CLIENT_SECRET"),
        Env.get("CHANNEL_5_EMAIL")
      )
      // Add more channels as needed; remember to mirror the new keys in .env.
    );
   ScheduledExecutorService executor = Executors.newScheduledThreadPool(channels.size()+1);
    AtomicLong timeUntilNextCheck = new AtomicLong(15*60);

    for (var entry : channels.entrySet()) {
      String channelName = entry.getKey();
      ChannelConfig cfg = entry.getValue();
      AtomicBoolean running = new AtomicBoolean(false);

      ChannelScheduler scheduler = new ChannelScheduler(
        channelsRoot, channelName, cfg, executor, nextScheduledMap, scheduledVideos
      );

      executor.scheduleWithFixedDelay(() -> {
        if (!running.compareAndSet(false, true)) {
          System.out.println("⏳ Still processing " + channelName + ", skipping.");
          return;
        }
        try {
          // ——— PRE-FILL if pending < 5 ———
          Path pendingDir = channelsRoot.resolve(channelName).resolve("pending");
          long pendingCount = Files.list(pendingDir)
                                   .filter(p -> p.toString().endsWith(".mp4"))
                                   .count();

          if (pendingCount < 5) {
            System.out.printf(" [%s] only %d pending videos → generating 3 more…%n",
                              channelName, pendingCount);
            // launch generation in its own task so we don't block scheduling
            executor.submit(() -> {
              try {
                vid.builder.Main.main(new String[]{ channelName, "3" });
              } catch (Exception e) {
                System.err.printf(" vidBuilder generation failed for %s: %s%n",
                                  channelName, e.getMessage());
                e.printStackTrace();
              }
            });
          }

          // ——— then schedule uploads ———
          System.out.println(" Checking for new videos in " + channelName);
          scheduler.scheduleAll();

        } catch (IOException e) {
          System.err.println("🚨 Failed for " + channelName + ": " + e.getMessage());
        } finally {
          running.set(false);
          timeUntilNextCheck.set(15*60);
        }
      }, 0, 15, TimeUnit.MINUTES);
    }

    // Countdown & status (unchanged)
    executor.scheduleAtFixedRate(() -> {
      long secs = timeUntilNextCheck.getAndAdd(-60);
      if (secs > 0) {
        System.out.println("⏳ Time till next check: " + (secs/60) + " min");
      } else {
        timeUntilNextCheck.set(0);
      }
        for (var entry : channels.entrySet()) {
        String channelName = entry.getKey();
        Path pendingDir = Path.of("D:/AutoVideoProducer/Channels").resolve(channelName).resolve("pending");

        try {
          List<Path> videos = Files.list(pendingDir)
            .filter(p -> p.toString().endsWith(".mp4"))
            .sorted()
            .collect(Collectors.toList());

          Optional<Path> scheduledNext = videos.stream()
            .map(p -> p.toAbsolutePath().normalize())
            .filter(scheduledVideos::contains)
            .findFirst();

          if (scheduledNext.isPresent()) {
            Path nextVideo = scheduledNext.get();
            Instant nextUploadTime = nextScheduledMap.getOrDefault(channelName, Instant.now());
            long minutesUntilUpload = Math.max(Duration.between(Instant.now(), nextUploadTime).toMinutes(), 0);

            System.out.printf(
              "📺 [%s] Next upload in %d min at %s: %s%n",
              channelName,
              minutesUntilUpload,
              nextUploadTime.atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0),
              nextVideo.getFileName()
            );
          } else {
            Instant nextUploadTime = nextScheduledMap.get(channelName);
            if (nextUploadTime != null) {
              long minutes = Math.max(Duration.between(Instant.now(), nextUploadTime).toMinutes(), 0);
              System.out.printf(" [%s] Scheduled upload in %d min at %s (video not yet assigned)%n",
                channelName,
                minutes,
                nextUploadTime.atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0));
            }
          }

        } catch (IOException e) {
          System.err.printf("Error checking [%s]: %s%n", channelName, e.getMessage());
        }
      }
    }, 1, 1, TimeUnit.MINUTES);

    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
  }
}