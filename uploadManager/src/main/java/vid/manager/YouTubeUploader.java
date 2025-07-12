package vid.manager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;

public class YouTubeUploader {
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final List<String> SCOPES =
      Collections.singletonList(YouTubeScopes.YOUTUBE_UPLOAD);

  /**
   * Uploads a video to YouTube using the provided OAuth credentials and user email.
   *
   * @param videoPath     local path to the .mp4 file
   * @param title         video title
   * @param description   video description
   * @param privacyStatus "public", "unlisted", or "private"
   * @param clientId      OAuth2 client ID
   * @param clientSecret  OAuth2 client secret
   * @param userEmail     the YouTube‐account email (used for login_hint & token store)
   */
  public static boolean uploadVideo(
      String videoPath,
      String title,
      String description,
      String privacyStatus,
      String clientId,
      String clientSecret,
      String userEmail
  ) throws Exception {
    // Build in‐memory client secrets
    GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
    details.setClientId(clientId);
    details.setClientSecret(clientSecret);
    details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
    details.setTokenUri("https://oauth2.googleapis.com/token");
    var clientSecrets = new GoogleClientSecrets().setInstalled(details);

    // Prepare HTTP + Storage
    var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    var dataStoreDir = new File("tokens/" + clientId);
    var flow = new GoogleAuthorizationCodeFlow.Builder(
        httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
      .setDataStoreFactory(new FileDataStoreFactory(dataStoreDir))
      .setAccessType("offline")
      .build();

    // Custom AuthorizationCodeInstalledApp to inject login_hint
    LocalServerReceiver receiver = new LocalServerReceiver.Builder()
        .setPort(8888)
        .build();

    AuthorizationCodeInstalledApp app =
        new AuthorizationCodeInstalledApp(flow, receiver) {
      @Override
      protected void onAuthorization(AuthorizationCodeRequestUrl authUrl) throws IOException {
        // Preselect the correct account
        authUrl.set("login_hint", userEmail);
        super.onAuthorization(authUrl);
      }
    };

    // Authorize, using userEmail as the key for token storage
    Credential credential = app.authorize(userEmail);

    // Build the YouTube service
    YouTube youtube = new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
        .setApplicationName("uploadManager")
        .build();

    // Prepare metadata
    Video video = new Video();
    VideoSnippet snippet = new VideoSnippet()
        .setTitle(title)
        .setDescription(description)
        .setTags(List.of(
    "Shorts",
    "YouTube Shorts",
    "Shorts Video"
))

        .setCategoryId("22");
    video.setSnippet(snippet);

    VideoStatus status = new VideoStatus()
        .setPrivacyStatus(privacyStatus)
        .setSelfDeclaredMadeForKids(false);  // mark as not for kids
    video.setStatus(status);

    // Upload media
    FileContent media = new FileContent("video/mp4", new File(videoPath));
    var insert = youtube.videos().insert("snippet,status", video, media);
    insert.getMediaHttpUploader().setProgressListener(uploader -> {
      switch (uploader.getUploadState()) {
        case INITIATION_STARTED:
          System.out.println(" Init started");
          break;
        case INITIATION_COMPLETE:
          System.out.println(" Init complete");
          break;
        case MEDIA_IN_PROGRESS:
          System.out.printf(" %.1f%%%n", uploader.getProgress() * 100);
          break;
        case MEDIA_COMPLETE:
          System.out.println(" Upload done");
          break;
        default:
          break;
      }
    });

    Video response = insert.execute();
    System.out.println(" Uploaded! Video ID: " + response.getId());
    return true;
  }
}
