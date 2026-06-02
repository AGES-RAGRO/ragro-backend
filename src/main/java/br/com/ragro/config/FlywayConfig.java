package br.com.ragro.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

  /**
   * Runs {@code repair} before {@code migrate} on each boot to realign {@code flyway_schema_history}
   * when it diverges from local files (e.g. a migration was edited after being applied, or an
   * applied migration was removed). {@code repair} fixes checksums and deletion markers only; it
   * does not re-run SQL or revert schema changes, and is a no-op on a healthy schema.
   *
   * <p>Gated on {@code ragro.flyway.repair-on-boot} (default true for local/dev). Set it to {@code
   * false} in production so a real checksum/history divergence fails the deploy loudly instead of
   * being silently repaired. When disabled, no custom strategy bean is registered and Spring Boot's
   * default {@code migrate}-only behaviour applies.
   */
  @Bean
  @ConditionalOnProperty(
      name = "ragro.flyway.repair-on-boot",
      havingValue = "true",
      matchIfMissing = true)
  public FlywayMigrationStrategy repairAndMigrateStrategy() {
    return flyway -> {
      flyway.repair();
      flyway.migrate();
    };
  }
}
