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
   * URL pública que clientes (browser/app) usam pra alcançar o storage. Diferente de {@link
   * #endpoint} apenas quando o backend acessa o storage por um hostname interno (ex.: docker-compose
   * usa {@code minio:9000}, mas o browser do dev precisa de {@code localhost:9000}). Quando vazio,
   * presigned URLs são geradas com o {@link #endpoint}.
   */
  private String publicUrl;

  /**
   * Região do storage usada na assinatura AWS Signature V4. Setar explicitamente faz o SDK pular a
   * discovery automática (que tenta uma chamada HTTP) e deixa o signing 100% local. Em S3 real
   * precisa bater com a região do bucket; em MinIO local qualquer valor serve.
   */
  @NotBlank private String region = "us-east-1";

  @NotBlank private String accessKey;

  @NotBlank private String secretKey;

  @NotBlank private String bucket;

  /**
   * Time-to-live (em segundos) para presigned URLs geradas para leitura de objetos. Default: 3600
   * (1 hora). Mínimo: 60s. Máximo: 604800s (7 dias, limite do AWS Signature V4).
   */
  @Min(60)
  @Max(604800)
  private int presignedExpirySeconds = 3600;
}
