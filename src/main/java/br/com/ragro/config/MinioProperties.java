package br.com.ragro.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

  @NotBlank private String endpoint;

  /**
   * Public URL clients (browser/app) use to reach storage. Differs from {@link #endpoint} only when
   * the backend reaches storage via an internal hostname (e.g. docker-compose uses {@code
   * minio:9000} but the dev browser needs {@code localhost:9000}). When empty, presigned URLs use
   * {@link #endpoint}.
   */
  private String publicUrl;

  /**
   * Public base URL used to build media URLs served by the backend proxy ({@code GET /media/**}).
   * In dev it is the API itself ({@code http://localhost:8080}); in prod the public API domain or
   * CDN. Unlike {@link #publicUrl} (used for presigned storage URLs), this points at the backend.
   */
  @NotBlank private String mediaPublicUrl = "http://localhost:8080";

  /**
   * Storage region for AWS Signature V4. Setting it explicitly makes the SDK skip auto-discovery
   * (which issues an HTTP call) and keeps signing fully local. On real S3 it must match the bucket
   * region; on local MinIO any value works.
   */
  @NotBlank private String region = "us-east-1";

  @NotBlank private String accessKey;

  @NotBlank private String secretKey;

  @NotBlank private String bucket;

  /**
   * Time-to-live (seconds) for presigned read URLs. Max 604800s is the AWS Signature V4 limit.
   */
  @Min(60)
  @Max(604800)
  private int presignedExpirySeconds = 3600;
}
