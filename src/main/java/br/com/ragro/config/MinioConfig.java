package br.com.ragro.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

  private final MinioProperties properties;

  /** Client usado para upload/delete/list — fala com o storage pelo endpoint interno. */
  @Bean
  public MinioClient minioClient() {
    return MinioClient.builder()
        .endpoint(properties.getEndpoint())
        .region(properties.getRegion())
        .credentials(properties.getAccessKey(), properties.getSecretKey())
        .build();
  }

  /**
   * Client dedicado para gerar presigned URLs. Quando {@code publicUrl} está definido, assina com
   * ele (importante em dev: backend acessa MinIO via {@code minio:9000} dentro do docker, mas a URL
   * embutida nas respostas deve ser {@code localhost:9000} pra browser/app conseguirem alcançar).
   * Em produção, {@code publicUrl} fica vazio e a assinatura cai no {@link #minioClient()}.
   *
   * <p>Setar {@code region} é essencial aqui: sem isso, o SDK tenta uma chamada HTTP de discovery
   * no endpoint antes de assinar, e como o presign endpoint geralmente não é alcançável do backend
   * (ex.: {@code localhost:9000} de dentro do container), a chamada falha com {@code Connection
   * refused}.
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
