package br.com.ragro.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase Cloud Messaging bootstrap.
 *
 * <p>Beans are only created when {@code FIREBASE_SERVICE_ACCOUNT_JSON} is set (see {@link
 * FirebaseEnabledCondition}). With the variable absent the application boots normally and push
 * notifications are silently disabled (see {@link br.com.ragro.service.FcmService}), so
 * local/dev/test environments don't need a Firebase project.
 */
@Slf4j
@Configuration
@Conditional(FirebaseEnabledCondition.class)
public class FirebaseConfig {

  @Value("${firebase.service-account-json}")
  private String serviceAccountJson;

  @Bean
  public FirebaseApp firebaseApp() throws IOException {
    if (!FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.getInstance();
    }

    GoogleCredentials credentials =
        GoogleCredentials.fromStream(
            new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
    FirebaseOptions options =
        FirebaseOptions.builder()
            .setCredentials(credentials)
            .setConnectTimeout(10_000)
            .setReadTimeout(30_000)
            .build();

    FirebaseApp app = FirebaseApp.initializeApp(options);
    log.info("[FCM] Firebase inicializado; push notifications habilitado");
    return app;
  }

  @Bean
  public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }
}
