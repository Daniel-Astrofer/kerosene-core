package source.auth.application.usecase.passkey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;

@Component
public class GetPasskeyInventoryUseCase {

    private final UserRepository userRepository;
    private final PasskeyInventoryService passkeyInventoryService;

    public GetPasskeyInventoryUseCase(
            UserRepository userRepository,
            PasskeyInventoryService passkeyInventoryService) {
        this.userRepository = userRepository;
        this.passkeyInventoryService = passkeyInventoryService;
    }

    @Transactional(readOnly = true)
    public Result execute(Long userId) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        return Result.found(passkeyInventoryService.inventoryFor(user));
    }

    public record Result(Status status, String message, PasskeyInventoryDTO inventory) {

        private static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, "User not found", null);
        }

        private static Result found(PasskeyInventoryDTO inventory) {
            return new Result(Status.FOUND, null, inventory);
        }
    }

    public enum Status {
        FOUND,
        USER_NOT_FOUND
    }
}
