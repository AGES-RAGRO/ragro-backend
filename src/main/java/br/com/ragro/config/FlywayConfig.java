package br.com.ragro.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

  /**
   * Roda {@code repair} antes de {@code migrate} a cada boot. Necessário quando o histórico do
   * Flyway diverge dos arquivos locais — tipicamente porque uma migração foi editada depois de
   * aplicada ou porque uma migração já aplicada foi removida do código.
   *
   * <p>{@code repair} corrige o {@code flyway_schema_history}: atualiza checksums pra bater com os
   * arquivos locais e marca como deletadas as migrações que sumiram. Não re-executa SQL nem
   * desfaz mudanças no schema.
   *
   * <p>Idempotente: em um schema saudável é no-op.
   */
  @Bean
  public FlywayMigrationStrategy repairAndMigrateStrategy() {
    return flyway -> {
      flyway.repair();
      flyway.migrate();
    };
  }
}
