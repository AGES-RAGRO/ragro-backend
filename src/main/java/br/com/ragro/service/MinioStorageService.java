package br.com.ragro.service;

import br.com.ragro.config.MinioProperties;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.InternalServerException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private final MinioClient minioClient;
  private final MinioProperties properties;

  @PostConstruct
  public void bootstrapBucket() {
    String bucket = properties.getBucket();
    try {
      boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        log.info("MinIO bucket '{}' created", bucket);
      }
      minioClient.setBucketPolicy(
          SetBucketPolicyArgs.builder().bucket(bucket).config(publicReadPolicy(bucket)).build());
      log.info("MinIO bucket '{}' ready (anonymous read-only)", bucket);
    } catch (Exception e) {
      log.error("Failed to bootstrap MinIO bucket '{}': {}", bucket, e.getMessage(), e);
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
          PutObjectArgs.builder()
              .bucket(properties.getBucket())
              .object(objectKey)
              .stream(stream, file.getSize(), -1)
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

  public String composePublicUrl(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return null;
    }
    if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
      return objectKey;
    }
    return properties.getPublicUrl() + "/" + properties.getBucket() + "/" + objectKey;
  }

  private String extensionFor(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      default -> "";
    };
  }

  private String publicReadPolicy(String bucket) {
    return "{"
        + "\"Version\":\"2012-10-17\","
        + "\"Statement\":[{"
        + "\"Effect\":\"Allow\","
        + "\"Principal\":{\"AWS\":[\"*\"]},"
        + "\"Action\":[\"s3:GetObject\"],"
        + "\"Resource\":[\"arn:aws:s3:::"
        + bucket
        + "/*\"]"
        + "}]}";
  }
}
