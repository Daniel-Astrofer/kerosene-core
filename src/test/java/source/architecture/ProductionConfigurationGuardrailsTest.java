package source.architecture;

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
    private static final Path PROD_K8S_DEPLOYMENT = PROJECT_ROOT.resolve("../../infra/kubernetes/base/server/deployment.yaml").normalize();
    private static final Path PROD_K8S_CONFIG = PROJECT_ROOT.resolve("../../infra/kubernetes/base/server/configmap.yaml").normalize();
    private static final Path DESTRUCTIVE_RESET_MIGRATION = PROJECT_ROOT.resolve("src/main/resources/db/migration/V23__drop_legacy_financial_tables.sql");

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
    void productionKubernetesUsesEffectiveSpringPropertyEnvironmentNames() throws IOException {
        String manifest = Files.readString(PROD_K8S_DEPLOYMENT) + "\n" + Files.readString(PROD_K8S_CONFIG);

        assertTrue(manifest.contains("SPRING_PROFILES_ACTIVE: \"prod\""));
        assertFalse(manifest.contains("SPRING_PROFILES_ACTIVE: \"production\""));

        assertTrue(manifest.contains("name: SPRING_DATASOURCE_URL"));
        assertTrue(manifest.contains("name: SPRING_DATASOURCE_USERNAME"));
        assertTrue(manifest.contains("name: SPRING_DATASOURCE_PASSWORD"));
        assertTrue(manifest.contains("SPRING_DATA_REDIS_HOST"));
        assertTrue(manifest.contains("name: SPRING_DATA_REDIS_PASSWORD"));
        assertTrue(manifest.contains("LIGHTNING_LND_HOST"));
        assertTrue(manifest.contains("name: LIGHTNING_LND_MACAROON"));
        assertTrue(manifest.contains("VAULT_RAFT_URL"));
        assertTrue(manifest.contains("MPC_SIDECAR_HOST"));

        assertFalse(manifest.contains("name: POSTGRES_URL"));
        assertFalse(manifest.contains("name: REDIS_HOST"));
        assertFalse(manifest.contains("name: LND_HOST"));
        assertFalse(manifest.contains("name: LND_MACAROON_HEX"));
    }

    @Test
    void destructiveLegacyFinancialDropIsMarkedAsDevTestResetOnly() throws IOException {
        String migration = Files.readString(DESTRUCTIVE_RESET_MIGRATION);

        assertTrue(migration.startsWith("-- KEROSENE DEV/TEST RESET MIGRATION"));
        assertTrue(migration.contains("dev/test only"));
        assertTrue(migration.contains("Do not run against production data"));
    }
}
