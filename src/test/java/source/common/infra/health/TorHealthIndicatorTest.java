package source.common.infra.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Status;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TorHealthIndicatorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReportUpWhenTorSocketAndVanguardsStateExist() throws Exception {
        Path socks = Files.createFile(tempDir.resolve("tor.sock"));
        Path state = Files.createFile(tempDir.resolve("vanguards.state"));
        Files.writeString(state, "layer2=abc\nlayer3=def\n");

        TorHealthIndicator indicator = new TorHealthIndicator(socks.toString(), state.toString());

        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void shouldReportDownWhenVanguardsStateIsMissing() throws Exception {
        Path socks = Files.createFile(tempDir.resolve("tor.sock"));

        TorHealthIndicator indicator = new TorHealthIndicator(
                socks.toString(),
                tempDir.resolve("missing.state").toString());

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }
}
