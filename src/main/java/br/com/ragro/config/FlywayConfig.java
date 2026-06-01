package br.com.ragro.config;

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
   */
  @Bean
  public FlywayMigrationStrategy repairAndMigrateStrategy() {
    return flyway -> {
      flyway.repair();
      flyway.migrate();
    };
  }
}
