package source.auth.application.usecase.user;

import org.junit.jupiter.api.Test;
import source.auth.application.service.pow.PowService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratePowChallengeUseCaseTest {

    private final PowService powService = mock(PowService.class);
    private final GeneratePowChallengeUseCase useCase = new GeneratePowChallengeUseCase(powService);

    @Test
    void executeGeneratesChallengePayload() {
        when(powService.generateChallenge()).thenReturn("challenge-1");

        Map<String, String> result = useCase.execute();

        assertEquals(Map.of("challenge", "challenge-1"), result);
        verify(powService).generateChallenge();
    }

    @Test
    void executeReturnsImmutablePayload() {
        when(powService.generateChallenge()).thenReturn("challenge-1");

        Map<String, String> result = useCase.execute();

        assertThrows(UnsupportedOperationException.class, () -> result.put("other", "value"));
    }
}
