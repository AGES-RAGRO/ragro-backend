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
 * Proxy público de leitura de mídia. Faz streaming dos objetos do storage (MinIO/S3) pela mesma
 * porta da API, evitando expor o storage diretamente e o acoplamento host/assinatura das presigned
 * URLs (que quebrava no emulador Android, onde o app reescreve {@code localhost -> 10.0.2.2}).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Media", description = "Leitura pública de mídia (avatares, capas, fotos de produtos)")
public class MediaController {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  /**
   * Tipos seguros para servir inline (o browser renderiza como imagem, nunca como documento ativo).
   * SVG fica DE FORA de propósito: SVG pode conter scripts e, servido same-origin, vira vetor de XSS
   * stored. Qualquer Content-Type fora desta lista é forçado a download (attachment + octet-stream).
   */
  private static final Set<String> INLINE_SAFE_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  // CSP defensiva: mesmo que algo ative o parser do browser, nada de script/objeto/frame roda.
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
      // Normaliza removendo parâmetros (ex.: "image/jpeg; charset=utf-8" -> "image/jpeg").
      String baseType = rawType.contains(";") ? rawType.substring(0, rawType.indexOf(';')).trim() : rawType.trim();
      boolean inlineSafe = INLINE_SAFE_CONTENT_TYPES.contains(baseType);

      MediaType mediaType =
          inlineSafe ? parseContentType(baseType) : MediaType.APPLICATION_OCTET_STREAM;
      String disposition = inlineSafe ? "inline" : "attachment";

      ResponseEntity.BodyBuilder builder =
          ResponseEntity.ok()
              .contentType(mediaType)
              .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
              // Anti-XSS: não deixa o browser "adivinhar" o tipo nem executar conteúdo ativo.
              .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
              .header("X-Content-Type-Options", "nosniff")
              .header("Content-Security-Policy", MEDIA_CSP);
      if (media.size() >= 0) {
        builder.contentLength(media.size());
      }
      return builder.body(new InputStreamResource(media.stream()));
    } catch (RuntimeException e) {
      // Garante que o stream (conexão com o storage) não vaze se a montagem da resposta falhar.
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

  /** Extrai o objectKey (o que vem depois de {@code /media/}), decodificando o URL-encoding. */
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
