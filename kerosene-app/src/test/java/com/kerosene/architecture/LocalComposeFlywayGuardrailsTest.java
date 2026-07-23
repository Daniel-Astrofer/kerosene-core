package source.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy local.compose.yaml (mpc-sidecar shards) was removed. Guard that the
 * deploy settlement compose path stays vault-mesh and mpc-free.
 */
class LocalComposeFlywayGuardrailsTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final Path VAULT_MESH_LAB = PROJECT_ROOT
            .resolve("../../../infra/docker/compose/vault-mesh-lab.compose.yaml")
            .normalize();
    private static final Path IMAGES_YAML = PROJECT_ROOT
            .resolve("../../../infra/docker/images.yaml")
            .normalize();

    @Test
    void vaultMeshLabComposeExistsAndDoesNotReferenceMpcSidecar() throws IOException {
        String compose = Files.readString(VAULT_MESH_LAB);
        assertTrue(compose.contains("kerosene-vault") || compose.contains("vault-1"));
        assertFalse(compose.contains("mpc-sidecar"));
    }

    @Test
    void imageInventoryDoesNotListMpcSidecar() throws IOException {
        String images = Files.readString(IMAGES_YAML);
        assertFalse(images.contains("mpc-sidecar:"));
        assertFalse(images.contains("backend/mpc-sidecar"));
    }
}
