package br.com.ragro.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ragro.config.ActiveUserFilter;
import br.com.ragro.config.CorsConfig;
import br.com.ragro.config.KeycloakRolesConverter;
import br.com.ragro.config.SecurityConfig;
import br.com.ragro.exception.NotFoundException;
import br.com.ragro.repository.UserRepository;
import br.com.ragro.service.MediaResource;
import br.com.ragro.service.MinioStorageService;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MediaController.class)
@Import({SecurityConfig.class, KeycloakRolesConverter.class, CorsConfig.class, ActiveUserFilter.class})
class MediaControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MinioStorageService storageService;
  @MockBean private UserRepository userRepository;

  // ─── inline-safe content types ──────────────────────────────────────

  @Test
  void getMedia_shouldReturn200WithInlineDisposition_whenContentTypeIsJpeg() throws Exception {
    byte[] bytes = "fake-image-bytes".getBytes();
    when(storageService.download("avatars/user1.jpg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/jpeg", bytes.length));

    mockMvc
        .perform(get("/media/avatars/user1.jpg"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"))
        .andExpect(header().string("Content-Type", "image/jpeg"))
        .andExpect(header().longValue("Content-Length", bytes.length))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    "default-src 'none'; sandbox; frame-ancestors 'none'"))
        .andExpect(content().bytes(bytes));
  }

  @Test
  void getMedia_shouldReturn200WithInlineDisposition_whenContentTypeIsPng() throws Exception {
    byte[] bytes = "png-bytes".getBytes();
    when(storageService.download("covers/cover1.png"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/png", bytes.length));

    mockMvc
        .perform(get("/media/covers/cover1.png"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"))
        .andExpect(header().string("Content-Type", "image/png"));
  }

  @Test
  void getMedia_shouldReturn200WithInlineDisposition_whenContentTypeIsWebp() throws Exception {
    byte[] bytes = "webp-bytes".getBytes();
    when(storageService.download("products/p1.webp"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/webp", bytes.length));

    mockMvc
        .perform(get("/media/products/p1.webp"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"))
        .andExpect(header().string("Content-Type", "image/webp"));
  }

  @Test
  void getMedia_shouldReturn200WithInlineDisposition_whenContentTypeIsGif() throws Exception {
    byte[] bytes = "gif-bytes".getBytes();
    when(storageService.download("products/p1.gif"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/gif", bytes.length));

    mockMvc
        .perform(get("/media/products/p1.gif"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"));
  }

  // ─── content type normalization (case + parameters) ────────────────────

  @Test
  void getMedia_shouldTreatContentTypeCaseInsensitively() throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("avatars/user2.jpg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "IMAGE/JPEG", bytes.length));

    mockMvc
        .perform(get("/media/avatars/user2.jpg"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"));
  }

  @Test
  void getMedia_shouldStripParameters_whenContentTypeHasCharset() throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("avatars/user3.jpg"))
        .thenReturn(
            new MediaResource(
                new ByteArrayInputStream(bytes), "image/jpeg; charset=utf-8", bytes.length));

    mockMvc
        .perform(get("/media/avatars/user3.jpg"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"))
        .andExpect(header().string("Content-Type", "image/jpeg"));
  }

  // ─── non-inline-safe content types (forced download) ───────────────────

  @Test
  void getMedia_shouldReturn200WithAttachmentDisposition_whenContentTypeIsNotSafe()
      throws Exception {
    byte[] bytes = "<svg></svg>".getBytes();
    when(storageService.download("uploads/malicious.svg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/svg+xml", bytes.length));

    mockMvc
        .perform(get("/media/uploads/malicious.svg"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment"))
        .andExpect(header().string("Content-Type", "application/octet-stream"));
  }

  @Test
  void getMedia_shouldReturn200WithAttachmentDisposition_whenContentTypeIsNull() throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("uploads/unknown"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), null, bytes.length));

    mockMvc
        .perform(get("/media/uploads/unknown"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "attachment"))
        .andExpect(header().string("Content-Type", "application/octet-stream"));
  }

  @Test
  void getMedia_shouldReturn200WithAttachmentDisposition_whenContentTypeIsGarbage()
      throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("uploads/garbage"))
        .thenReturn(
            new MediaResource(new ByteArrayInputStream(bytes), "not-a-valid-type", bytes.length));

    mockMvc
        .perform(get("/media/uploads/garbage"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/octet-stream"))
        .andExpect(header().string("Content-Disposition", "attachment"));
  }

  // ─── size handling ───────────────────────────────────────────────────

  @Test
  void getMedia_shouldOmitContentLength_whenSizeIsNegative() throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("avatars/user4.jpg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/jpeg", -1));

    mockMvc
        .perform(get("/media/avatars/user4.jpg"))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("Content-Length"));
  }

  @Test
  void getMedia_shouldSetContentLength_whenSizeIsZero() throws Exception {
    byte[] bytes = new byte[0];
    when(storageService.download("avatars/empty.jpg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/jpeg", 0));

    mockMvc
        .perform(get("/media/avatars/empty.jpg"))
        .andExpect(status().isOk())
        .andExpect(header().longValue("Content-Length", 0));
  }

  // ─── cache-control ──────────────────────────────────────────────────

  @Test
  void getMedia_shouldSetCacheControlHeader() throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("avatars/user5.jpg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/jpeg", bytes.length));

    mockMvc
        .perform(get("/media/avatars/user5.jpg"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=3600, public"));
  }

  // ─── not found / error branches ─────────────────────────────────────

  @Test
  void getMedia_shouldReturn404_whenStorageThrowsNotFound() throws Exception {
    when(storageService.download("missing/key.jpg"))
        .thenThrow(new NotFoundException("Mídia não encontrada"));

    mockMvc.perform(get("/media/missing/key.jpg")).andExpect(status().isNotFound());
  }

  // ─── URL decoding ────────────────────────────────────────────────────

  @Test
  void getMedia_shouldDecodeObjectKey_whenPathContainsEncodedSpaces() throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("products/my product.jpg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/jpeg", bytes.length));

    mockMvc
        .perform(get(java.net.URI.create("/media/products/my%20product.jpg")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", "inline"));

    org.mockito.Mockito.verify(storageService).download(eq("products/my product.jpg"));
  }

  // ─── public access (no auth required) ───────────────────────────────

  @Test
  void getMedia_shouldReturn200_withoutAuthentication() throws Exception {
    byte[] bytes = "bytes".getBytes();
    when(storageService.download("public/file.jpg"))
        .thenReturn(new MediaResource(new ByteArrayInputStream(bytes), "image/jpeg", bytes.length));

    mockMvc.perform(get("/media/public/file.jpg")).andExpect(status().isOk());
  }
}