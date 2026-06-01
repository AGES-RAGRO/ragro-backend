package br.com.ragro.service;

import br.com.ragro.config.MinioProperties;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.InternalServerException;
import br.com.ragro.exception.NotFoundException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MinioStorageService {

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private final MinioClient minioClient;
  private final MinioClient minioPresignClient;
  private final MinioProperties properties;

  public MinioStorageService(
      MinioClient minioClient,
      @Qualifier("minioPresignClient") MinioClient minioPresignClient,
      MinioProperties properties) {
    this.minioClient = minioClient;
    this.minioPresignClient = minioPresignClient;
    this.properties = properties;
  }

  @PostConstruct
  public void bootstrapBucket() {
    String bucket = properties.getBucket();
    try {
      boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        log.info("Storage bucket '{}' created", bucket);
      }
      log.info("Storage bucket '{}' ready (private; reads via /media/** proxy)", bucket);
    } catch (Exception e) {
      log.error("Failed to bootstrap storage bucket '{}': {}", bucket, e.getMessage(), e);
    }
  }

  public String upload(MultipartFile file, String folder) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException("Arquivo enviado está vazio");
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(
          "Tipo de arquivo inválido. Permitidos: " + String.join(", ", ALLOWED_CONTENT_TYPES));
    }

    String objectKey = folder + "/" + UUID.randomUUID() + extensionFor(contentType);

    try (InputStream stream = file.getInputStream()) {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).stream(
                  stream, file.getSize(), -1)
              .contentType(contentType)
              .build());
    } catch (IOException e) {
      throw new InternalServerException("Falha ao ler arquivo para upload", e);
    } catch (Exception e) {
      throw new InternalServerException("Falha ao enviar arquivo para o storage", e);
    }

    return objectKey;
  }

  public void delete(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return;
    }
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
    } catch (Exception e) {
      log.warn("Falha ao remover objeto '{}' do MinIO: {}", objectKey, e.getMessage());
    }
  }

  /**
   * Composes a stable public URL for an object, served by the backend media proxy ({@code GET
   * /media/**}). Unlike presigned URLs, it never expires and is not tied to the signing host, so it
   * survives the {@code localhost -> 10.0.2.2} rewrite the app does on the Android emulator and
   * works the same on web/iOS/prod. Passes the value through unchanged when it is already an
   * absolute http(s) URL (legacy data) or null/blank.
   */
  public String composePublicUrl(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return null;
    }
    if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
      return objectKey;
    }
    String base = properties.getMediaPublicUrl();
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return normalizedBase + "/media/" + encodeObjectKey(objectKey);
  }

  /** URL-encodes each segment of the object key, keeping slashes as path separators. */
  private String encodeObjectKey(String objectKey) {
    String[] segments = objectKey.split("/", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        sb.append('/');
      }
      sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
    }
    return sb.toString();
  }

  /**
   * Generates a temporary presigned URL for an object (signed with {@code minioPresignClient}). Kept
   * for cases needing direct, temporary storage access; the default media read path is now the proxy
   * via {@link #composePublicUrl(String)}.
   */
  public String composePresignedUrl(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return null;
    }
    if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
      return objectKey;
    }
    try {
      return minioPresignClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(properties.getBucket())
              .object(objectKey)
              .expiry(properties.getPresignedExpirySeconds())
              .build());
    } catch (Exception e) {
      log.error(
          "Failed to generate presigned URL for object '{}' in bucket '{}': {}",
          objectKey,
          properties.getBucket(),
          e.getMessage(),
          e);
      throw new InternalServerException("Falha ao gerar URL presigned", e);
    }
  }

  /**
   * Downloads an object from storage to be served by the media proxy. Uses the internal client and
   * streams the bytes via {@code getObject}. Content-Type and Content-Length are read from the
   * response headers, avoiding an extra {@code statObject} (less latency and no TOCTOU race between
   * stat and get). Throws {@link NotFoundException} when the object does not exist.
   */
  public MediaResource download(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new NotFoundException("Mídia não encontrada");
    }
    String bucket = properties.getBucket();
    try {
      GetObjectResponse stream =
          minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
      String contentTypeHeader = stream.headers().get("Content-Type");
      String contentLengthHeader = stream.headers().get("Content-Length");
      String contentType =
          (contentTypeHeader != null && !contentTypeHeader.isBlank())
              ? contentTypeHeader
              : "application/octet-stream";
      long size = -1;
      if (contentLengthHeader != null && !contentLengthHeader.isBlank()) {
        try {
          size = Long.parseLong(contentLengthHeader.trim());
        } catch (NumberFormatException ignored) {
          size = -1;
        }
      }
      return new MediaResource(stream, contentType, size);
    } catch (ErrorResponseException e) {
      String code = e.errorResponse() != null ? e.errorResponse().code() : null;
      if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "ResourceNotFound".equals(code)) {
        throw new NotFoundException("Mídia não encontrada");
      }
      throw new InternalServerException("Falha ao ler mídia do storage", e);
    } catch (Exception e) {
      throw new InternalServerException("Falha ao ler mídia do storage", e);
    }
  }

  private String extensionFor(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      default -> "";
    };
  }

}
