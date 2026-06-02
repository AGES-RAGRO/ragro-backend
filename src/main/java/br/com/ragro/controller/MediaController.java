package br.com.ragro.controller;

import br.com.ragro.exception.NotFoundException;
import br.com.ragro.service.MediaResource;
import br.com.ragro.service.MinioStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriUtils;

/**
 * Public read-only media proxy. Streams storage objects (MinIO/S3) through the API port instead of
 * exposing the storage directly. Avoids the host/signature coupling of presigned URLs, which broke
 * on the Android emulator where the app rewrites {@code localhost -> 10.0.2.2}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Media", description = "Leitura pública de mídia (avatares, capas, fotos de produtos)")
public class MediaController {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  /**
   * Content types safe to serve inline (rendered as an image, never as an active document). SVG is
   * deliberately excluded: it can contain scripts and, served same-origin, becomes a stored XSS
   * vector. Anything outside this list is forced to download (attachment + octet-stream).
   */
  private static final Set<String> INLINE_SAFE_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  // Defensive CSP: even if something triggers the browser parser, no script/object/frame can run.
  private static final String MEDIA_CSP = "default-src 'none'; sandbox; frame-ancestors 'none'";

  private final MinioStorageService storageService;

  @Operation(summary = "Serve um objeto de mídia do storage")
  @GetMapping("/media/**")
  public ResponseEntity<InputStreamResource> getMedia(HttpServletRequest request) {
    String objectKey = extractObjectKey(request);
    if (objectKey == null || objectKey.isBlank()) {
      throw new NotFoundException("Mídia não encontrada");
    }

    MediaResource media = storageService.download(objectKey);
    try {
      String rawType = media.contentType() == null ? "" : media.contentType().toLowerCase();
      // Strip parameters (e.g. "image/jpeg; charset=utf-8" -> "image/jpeg").
      String baseType = rawType.contains(";") ? rawType.substring(0, rawType.indexOf(';')).trim() : rawType.trim();
      boolean inlineSafe = INLINE_SAFE_CONTENT_TYPES.contains(baseType);

      MediaType mediaType =
          inlineSafe ? parseContentType(baseType) : MediaType.APPLICATION_OCTET_STREAM;
      String disposition = inlineSafe ? "inline" : "attachment";

      ResponseEntity.BodyBuilder builder =
          ResponseEntity.ok()
              .contentType(mediaType)
              .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
              // Anti-XSS: stop the browser from sniffing the type or running active content.
              .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
              .header("X-Content-Type-Options", "nosniff")
              .header("Content-Security-Policy", MEDIA_CSP);
      if (media.size() >= 0) {
        builder.contentLength(media.size());
      }
      return builder.body(new InputStreamResource(media.stream()));
    } catch (RuntimeException e) {
      // Ensure the stream (storage connection) does not leak if building the response fails.
      closeQuietly(media);
      throw e;
    }
  }

  private MediaType parseContentType(String contentType) {
    try {
      return MediaType.parseMediaType(contentType);
    } catch (RuntimeException e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private void closeQuietly(MediaResource media) {
    try {
      media.stream().close();
    } catch (IOException ignored) {
      // best-effort
    }
  }

  /** Extracts the object key (the part after {@code /media/}), URL-decoded. */
  private String extractObjectKey(HttpServletRequest request) {
    String path =
        (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    String pattern =
        (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (path == null || pattern == null) {
      return null;
    }
    String remaining = PATH_MATCHER.extractPathWithinPattern(pattern, path);
    return UriUtils.decode(remaining, StandardCharsets.UTF_8);
  }
}
