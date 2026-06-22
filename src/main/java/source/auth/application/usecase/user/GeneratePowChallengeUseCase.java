package source.auth.application.usecase.user;

import org.springframework.stereotype.Component;
import source.auth.application.service.pow.PowService;

import java.util.Map;

@Component
public class GeneratePowChallengeUseCase {

    private final PowService powService;

    public GeneratePowChallengeUseCase(PowService powService) {
        this.powService = powService;
    }

    public Map<String, String> execute() {
        return Map.of("challenge", powService.generateChallenge());
    }
}
