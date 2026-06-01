package br.com.ragro.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

  private final MinioProperties properties;

  /** Client for upload/delete/list, talking to storage over the internal endpoint. */
  @Bean
  public MinioClient minioClient() {
    return MinioClient.builder()
        .endpoint(properties.getEndpoint())
        .region(properties.getRegion())
        .credentials(properties.getAccessKey(), properties.getSecretKey())
        .build();
  }

  /**
   * Dedicated client for presigned URLs. When {@code publicUrl} is set it signs with that host (in
   * dev the backend reaches MinIO via {@code minio:9000}, but embedded URLs must be {@code
   * localhost:9000} for the browser/app); in prod {@code publicUrl} is empty and it matches {@link
   * #minioClient()}. Setting {@code region} is essential: otherwise the SDK issues a discovery HTTP
   * call to the (often unreachable) presign endpoint and fails with Connection refused.
   */
  @Bean
  public MinioClient minioPresignClient() {
    String signEndpoint =
        (properties.getPublicUrl() != null && !properties.getPublicUrl().isBlank())
            ? properties.getPublicUrl()
            : properties.getEndpoint();
    return MinioClient.builder()
        .endpoint(signEndpoint)
        .region(properties.getRegion())
        .credentials(properties.getAccessKey(), properties.getSecretKey())
        .build();
  }
}
