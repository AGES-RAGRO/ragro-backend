package br.com.ragro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ragro.config.MinioProperties;
import br.com.ragro.exception.BusinessException;
import br.com.ragro.exception.InternalServerException;
import br.com.ragro.exception.NotFoundException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import okhttp3.Headers;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

  @Mock private MinioClient minioClient;

  private MinioProperties properties() {
    MinioProperties properties = new MinioProperties();
    properties.setEndpoint("http://localhost:9000");
    properties.setMediaPublicUrl("http://localhost:8080");
    properties.setRegion("us-east-1");
    properties.setAccessKey("access");
    properties.setSecretKey("secret");
    properties.setBucket("ragro-media");
    return properties;
  }

  private MinioStorageService newService() {
    return new MinioStorageService(minioClient, properties());
  }

  // ─── upload ──────────────────────────────────────────────────────────

  @Test
  void upload_shouldReturnObjectKey_whenFileIsValidJpeg() throws Exception {
    MinioStorageService service = newService();
    MultipartFile file =
        new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-bytes".getBytes());

    String objectKey = service.upload(file, "products");

    assertThat(objectKey).startsWith("products/");
    assertThat(objectKey).endsWith(".jpg");
    verify(minioClient).putObject(any(PutObjectArgs.class));
  }

  @Test
  void upload_shouldReturnPngExtension_whenContentTypeIsPng() throws Exception {
    MinioStorageService service = newService();
    MultipartFile file =
        new MockMultipartFile("file", "photo.png", "image/png", "fake-bytes".getBytes());

    String objectKey = service.upload(file, "avatars");

    assertThat(objectKey).endsWith(".png");
  }

  @Test
  void upload_shouldReturnWebpExtension_whenContentTypeIsWebp() throws Exception {
    MinioStorageService service = newService();
    MultipartFile file =
        new MockMultipartFile("file", "photo.webp", "image/webp", "fake-bytes".getBytes());

    String objectKey = service.upload(file, "covers");

    assertThat(objectKey).endsWith(".webp");
  }

  @Test
  void upload_shouldThrowBusinessException_whenFileIsNull() {
    MinioStorageService service = newService();

    assertThatThrownBy(() -> service.upload(null, "products"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Arquivo enviado está vazio");
  }

  @Test
  void upload_shouldThrowBusinessException_whenFileIsEmpty() {
    MinioStorageService service = newService();
    MultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

    assertThatThrownBy(() -> service.upload(file, "products"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Arquivo enviado está vazio");
  }

  @Test
  void upload_shouldThrowBusinessException_whenContentTypeIsNull() {
    MinioStorageService service = newService();
    MultipartFile file = new MockMultipartFile("file", "photo.jpg", null, "bytes".getBytes());

    assertThatThrownBy(() -> service.upload(file, "products")).isInstanceOf(BusinessException.class);
  }

  @Test
  void upload_shouldThrowBusinessException_whenContentTypeIsNotAllowed() {
    MinioStorageService service = newService();
    MultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());

    assertThatThrownBy(() -> service.upload(file, "products"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Tipo de arquivo inválido");
  }

  @Test
  void upload_shouldThrowInternalServerException_whenPutObjectFails() throws Exception {
    MinioStorageService service = newService();
    MultipartFile file =
        new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
    when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.upload(file, "products"))
        .isInstanceOf(InternalServerException.class)
        .hasMessage("Falha ao enviar arquivo para o storage");
  }

  // ─── delete ──────────────────────────────────────────────────────────

  @Test
  void delete_shouldCallRemoveObject_whenObjectKeyIsValid() throws Exception {
    MinioStorageService service = newService();

    service.delete("products/abc.jpg");

    verify(minioClient).removeObject(any(RemoveObjectArgs.class));
  }

  @Test
  void delete_shouldDoNothing_whenObjectKeyIsNull() throws Exception {
    MinioStorageService service = newService();

    service.delete(null);

    verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
  }

  @Test
  void delete_shouldDoNothing_whenObjectKeyIsBlank() throws Exception {
    MinioStorageService service = newService();

    service.delete("   ");

    verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
  }

  @Test
  void delete_shouldNotThrow_whenRemoveObjectFails() throws Exception {
    MinioStorageService service = newService();
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(minioClient)
        .removeObject(any(RemoveObjectArgs.class));

    service.delete("products/abc.jpg");

    verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
  }

  // ─── composePublicUrl ────────────────────────────────────────────────

  @Test
  void composePublicUrl_shouldReturnNull_whenObjectKeyIsNull() {
    MinioStorageService service = newService();

    assertThat(service.composePublicUrl(null)).isNull();
  }

  @Test
  void composePublicUrl_shouldReturnNull_whenObjectKeyIsBlank() {
    MinioStorageService service = newService();

    assertThat(service.composePublicUrl("  ")).isNull();
  }

  @Test
  void composePublicUrl_shouldReturnUnchanged_whenAlreadyHttpUrl() {
    MinioStorageService service = newService();

    String url = service.composePublicUrl("http://legacy.example.com/old.jpg");

    assertThat(url).isEqualTo("http://legacy.example.com/old.jpg");
  }

  @Test
  void composePublicUrl_shouldReturnUnchanged_whenAlreadyHttpsUrl() {
    MinioStorageService service = newService();

    String url = service.composePublicUrl("https://legacy.example.com/old.jpg");

    assertThat(url).isEqualTo("https://legacy.example.com/old.jpg");
  }

  @Test
  void composePublicUrl_shouldBuildMediaProxyUrl_whenObjectKeyIsPlain() {
    MinioStorageService service = newService();

    String url = service.composePublicUrl("products/photo.jpg");

    assertThat(url).isEqualTo("http://localhost:8080/media/products/photo.jpg");
  }

  @Test
  void composePublicUrl_shouldStripTrailingSlash_fromBaseUrl() {
    MinioProperties properties = properties();
    properties.setMediaPublicUrl("http://localhost:8080/");
    MinioStorageService service = new MinioStorageService(minioClient, properties);

    String url = service.composePublicUrl("products/photo.jpg");

    assertThat(url).isEqualTo("http://localhost:8080/media/products/photo.jpg");
  }

  @Test
  void composePublicUrl_shouldEncodeSpacesInSegments() {
    MinioStorageService service = newService();

    String url = service.composePublicUrl("products/my photo.jpg");

    assertThat(url).isEqualTo("http://localhost:8080/media/products/my%20photo.jpg");
  }

  @Test
  void composePublicUrl_shouldPreserveSlashesAsPathSeparators() {
    MinioStorageService service = newService();

    String url = service.composePublicUrl("a/b/c/photo.jpg");

    assertThat(url).isEqualTo("http://localhost:8080/media/a/b/c/photo.jpg");
  }

  // ─── download ────────────────────────────────────────────────────────

  @Test
  void download_shouldThrowNotFoundException_whenObjectKeyIsNull() {
    MinioStorageService service = newService();

    assertThatThrownBy(() -> service.download(null))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Mídia não encontrada");
  }

  @Test
  void download_shouldThrowNotFoundException_whenObjectKeyIsBlank() {
    MinioStorageService service = newService();

    assertThatThrownBy(() -> service.download("  "))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Mídia não encontrada");
  }

  @Test
  void download_shouldReturnMediaResource_withContentTypeAndSize() throws Exception {
    MinioStorageService service = newService();
    byte[] bytes = "fake-image".getBytes();
    GetObjectResponse response =
        buildGetObjectResponse(bytes, "image/jpeg", String.valueOf(bytes.length));
    when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

    var media = service.download("products/photo.jpg");

    assertThat(media.contentType()).isEqualTo("image/jpeg");
    assertThat(media.size()).isEqualTo(bytes.length);
  }

  @Test
  void download_shouldDefaultToOctetStream_whenContentTypeHeaderIsMissing() throws Exception {
    MinioStorageService service = newService();
    byte[] bytes = "bytes".getBytes();
    GetObjectResponse response = buildGetObjectResponse(bytes, null, null);
    when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

    var media = service.download("products/photo.jpg");

    assertThat(media.contentType()).isEqualTo("application/octet-stream");
    assertThat(media.size()).isEqualTo(-1);
  }

  @Test
  void download_shouldDefaultToOctetStream_whenContentTypeHeaderIsBlank() throws Exception {
    MinioStorageService service = newService();
    byte[] bytes = "bytes".getBytes();
    GetObjectResponse response = buildGetObjectResponse(bytes, "  ", "5");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

    var media = service.download("products/photo.jpg");

    assertThat(media.contentType()).isEqualTo("application/octet-stream");
  }

  @Test
  void download_shouldDefaultSizeToNegativeOne_whenContentLengthHeaderIsBlank() throws Exception {
    MinioStorageService service = newService();
    byte[] bytes = "bytes".getBytes();
    GetObjectResponse response = buildGetObjectResponse(bytes, "image/png", "  ");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

    var media = service.download("products/photo.jpg");

    assertThat(media.size()).isEqualTo(-1);
  }

  @Test
  void download_shouldDefaultSizeToNegativeOne_whenContentLengthHeaderIsNotANumber()
      throws Exception {
    MinioStorageService service = newService();
    byte[] bytes = "bytes".getBytes();
    GetObjectResponse response = buildGetObjectResponse(bytes, "image/png", "not-a-number");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

    var media = service.download("products/photo.jpg");

    assertThat(media.size()).isEqualTo(-1);
  }

  @Test
  void download_shouldThrowNotFoundException_whenMinioReturnsNoSuchKey() throws Exception {
    MinioStorageService service = newService();
    ErrorResponseException exception = buildErrorResponseException("NoSuchKey");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(exception);

    assertThatThrownBy(() -> service.download("missing/key.jpg"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Mídia não encontrada");
  }

  @Test
  void download_shouldThrowNotFoundException_whenMinioReturnsNoSuchObject() throws Exception {
    MinioStorageService service = newService();
    ErrorResponseException exception = buildErrorResponseException("NoSuchObject");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(exception);

    assertThatThrownBy(() -> service.download("missing/key.jpg"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void download_shouldThrowNotFoundException_whenMinioReturnsResourceNotFound() throws Exception {
    MinioStorageService service = newService();
    ErrorResponseException exception = buildErrorResponseException("ResourceNotFound");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(exception);

    assertThatThrownBy(() -> service.download("missing/key.jpg"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void download_shouldThrowInternalServerException_whenErrorCodeIsUnrecognized() throws Exception {
    MinioStorageService service = newService();
    ErrorResponseException exception = buildErrorResponseException("InternalError");
    when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(exception);

    assertThatThrownBy(() -> service.download("products/photo.jpg"))
        .isInstanceOf(InternalServerException.class)
        .hasMessage("Falha ao ler mídia do storage");
  }

  @Test
  void download_shouldThrowInternalServerException_whenUnexpectedErrorOccurs() throws Exception {
    MinioStorageService service = newService();
    when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> service.download("products/photo.jpg"))
        .isInstanceOf(InternalServerException.class)
        .hasMessage("Falha ao ler mídia do storage");
  }

  // ─── bootstrapBucket ─────────────────────────────────────────────────

  @Test
  void bootstrapBucket_shouldCreateBucket_whenItDoesNotExist() throws Exception {
    MinioStorageService service = newService();
    when(minioClient.bucketExists(any())).thenReturn(false);

    service.bootstrapBucket();

    verify(minioClient).makeBucket(any());
  }

  @Test
  void bootstrapBucket_shouldNotCreateBucket_whenItAlreadyExists() throws Exception {
    MinioStorageService service = newService();
    when(minioClient.bucketExists(any())).thenReturn(true);

    service.bootstrapBucket();

    verify(minioClient, never()).makeBucket(any());
  }

@Test
  void bootstrapBucket_shouldNotThrow_whenMinioClientFails() throws Exception {
    MinioStorageService service = newService();
    when(minioClient.bucketExists(any())).thenThrow(new RuntimeException("connection refused"));

    assertThatCode(service::bootstrapBucket).doesNotThrowAnyException();
  }

  // ─── helpers ─────────────────────────────────────────────────────────

  private GetObjectResponse buildGetObjectResponse(
      byte[] bytes, String contentType, String contentLength) {
    Headers.Builder headersBuilder = new Headers.Builder();
    if (contentType != null) {
      headersBuilder.add("Content-Type", contentType);
    }
    if (contentLength != null) {
      headersBuilder.add("Content-Length", contentLength);
    }
    Headers headers = headersBuilder.build();
    InputStream stream = new ByteArrayInputStream(bytes);

    return new GetObjectResponse(headers, "ragro-media", "us-east-1", "object", stream);
  }

  private ErrorResponseException buildErrorResponseException(String code) {
    ErrorResponse errorResponse =
        new ErrorResponse(code, "error message", "ragro-media", "object", "resource", "req-id", "host-id");
    Response okHttpResponse =
        new Response.Builder()
            .request(new okhttp3.Request.Builder().url("http://localhost:9000/bucket/object").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build();
    return new ErrorResponseException(errorResponse, okHttpResponse, "request-id");
  }
}