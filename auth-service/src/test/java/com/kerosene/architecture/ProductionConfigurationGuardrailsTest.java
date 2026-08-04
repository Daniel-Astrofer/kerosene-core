package com.kerosene.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigurationGuardrailsTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final Path PROD_PROPERTIES = PROJECT_ROOT.resolve("src/main/resources/application-prod.properties");
    private static final Path DOCKER_PROPERTIES = PROJECT_ROOT.resolve("src/main/resources/application-docker.properties");
    private static final Path DESTRUCTIVE_RESET_MIGRATION = PROJECT_ROOT.resolveSibling("kfe-service/src/main/resources/db/migration/V23__drop_legacy_financial_tables.sql");

    @Test
    void productionAndDockerFlywayBaselineDefaultsAreFailClosed() throws IOException {
        String prod = Files.readString(PROD_PROPERTIES);
        String docker = Files.readString(DOCKER_PROPERTIES);

        assertTrue(prod.contains("spring.flyway.baseline-on-migrate=${FLYWAY_BASELINE_ON_MIGRATE:false}"));
        assertTrue(docker.contains("spring.flyway.baseline-on-migrate=${FLYWAY_BASELINE_ON_MIGRATE:false}"));
        assertFalse(prod.contains("spring.flyway.baseline-on-migrate=${FLYWAY_BASELINE_ON_MIGRATE:true}"));
        assertFalse(docker.contains("spring.flyway.baseline-on-migrate=${FLYWAY_BASELINE_ON_MIGRATE:true}"));
    }

    @Test
    void destructiveLegacyFinancialDropIsMarkedAsDevTestResetOnly() throws IOException {
        String migration = Files.readString(DESTRUCTIVE_RESET_MIGRATION);

        assertTrue(migration.startsWith("-- KEROSENE DEV/TEST RESET MIGRATION"));
        assertTrue(migration.contains("dev/test only"));
        assertTrue(migration.contains("Do not run against production data"));
    }
}
