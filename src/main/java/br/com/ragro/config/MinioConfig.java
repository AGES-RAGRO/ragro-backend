package br.com.ragro.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

  private final MinioProperties properties;

  /** Client for upload/delete/list/read, talking to storage over the internal endpoint. */
  @Bean
  public MinioClient minioClient() {
    return MinioClient.builder()
        .endpoint(properties.getEndpoint())
        .region(properties.getRegion())
        .credentials(properties.getAccessKey(), properties.getSecretKey())
        .build();
  }
}
