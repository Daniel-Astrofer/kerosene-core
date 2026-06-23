package source.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalComposeFlywayGuardrailsTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final Path LOCAL_COMPOSE = PROJECT_ROOT.resolve("../kerosene-infrastructure/docker-compose.local.yml").normalize();

    @Test
    void localShardAppsEnableFlywayByDefault() throws IOException {
        String compose = Files.readString(LOCAL_COMPOSE);

        assertEquals(3, countOccurrences(compose, "FLYWAY_ENABLED=${FLYWAY_ENABLED:-true}"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
