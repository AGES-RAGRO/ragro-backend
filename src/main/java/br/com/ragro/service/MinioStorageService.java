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
   * Compõe a URL pública e estável de um objeto, servida pelo proxy de mídia do backend (endpoint
   * {@code GET /media/**}). Diferente das presigned URLs, esta URL não expira e não está atrelada ao
   * host que a assinou — então sobrevive à reescrita {@code localhost -> 10.0.2.2} feita pelo app no
   * emulador Android, e funciona igual em web/iOS/prod. Mantém comportamento de passagem quando o
   * valor já é uma URL http(s) absoluta (dados legados) ou nulo/vazio.
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

  /** URL-encoda cada segmento do objectKey preservando as barras como separadores de path. */
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
   * Gera uma presigned URL temporária para um objeto (assina com o {@code minioPresignClient}).
   * Preservado para casos que precisem de acesso direto e temporário ao storage; o caminho padrão de
   * leitura de mídia agora é o proxy via {@link #composePublicUrl(String)}.
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
   * Baixa um objeto do storage para ser servido pelo proxy de mídia. Usa o client interno (que fala
   * com o storage pelo endpoint interno) e abre o stream dos bytes via {@code getObject}. O
   * Content-Type e o Content-Length são lidos dos headers da própria resposta (evita um
   * {@code statObject} extra — menos latência e sem race TOCTOU entre stat e get). Lança {@link
   * NotFoundException} quando o objeto não existe.
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
