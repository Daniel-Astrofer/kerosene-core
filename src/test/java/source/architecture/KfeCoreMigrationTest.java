package source.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KfeCoreMigrationTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final Path V12_MIGRATION = PROJECT_ROOT.resolve("src/main/resources/db/migration/V12__kfe_core.sql");

    @Test
    void appendOnlyAuditTriggerIsReRunnable() throws IOException {
        String migration = Files.readString(V12_MIGRATION);

        assertTrue(migration.contains("DROP TRIGGER IF EXISTS enforce_append_only_audit_log"));
        assertAppearsBefore(migration,
                "DROP TRIGGER IF EXISTS enforce_append_only_audit_log",
                "CREATE TRIGGER enforce_append_only_audit_log");
    }

    private static void assertAppearsBefore(String text, String earlier, String later) {
        int earlierIndex = text.indexOf(earlier);
        int laterIndex = text.indexOf(later);

        assertTrue(earlierIndex >= 0, () -> "Missing expected SQL: " + earlier);
        assertTrue(laterIndex >= 0, () -> "Missing expected SQL: " + later);
        assertTrue(earlierIndex < laterIndex, () -> earlier + " must appear before " + later);
    }
}
