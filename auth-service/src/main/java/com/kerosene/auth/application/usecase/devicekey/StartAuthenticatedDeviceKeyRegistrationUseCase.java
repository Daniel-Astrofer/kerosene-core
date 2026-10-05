package com.kerosene.auth.application.usecase.devicekey;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.application.infra.persistence.jpa.UserRepository;
import com.kerosene.auth.application.service.devicekey.DeviceKeyService;
import com.kerosene.auth.dto.devicekey.DeviceKeyChallengeResponse;
import com.kerosene.auth.model.entity.UserDataBase;

/** Starts device-key enrollment for an existing authenticated account. */
@Component
public class StartAuthenticatedDeviceKeyRegistrationUseCase {

    /** User lookup used to bind the challenge to the requesting account. */
    private final UserRepository userRepository;
    /** Device-key protocol service that creates an account-bound challenge. */
    private final DeviceKeyService deviceKeyService;

    /** Creates the authenticated registration starter. */
    /** @param userRepository account lookup repository */
    /** @param deviceKeyService challenge generator and registration verifier */
    public StartAuthenticatedDeviceKeyRegistrationUseCase(
            UserRepository userRepository,
            DeviceKeyService deviceKeyService) {
        this.userRepository = userRepository;
        this.deviceKeyService = deviceKeyService;
    }

    /** Loads the owner and returns either a challenge bound to that user or a not-found result. */
    /** @param userId authenticated principal identifier */
    /** @return challenge result or user-not-found result */
    @Transactional(readOnly = true)
    public Result execute(Long userId) {
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Result.userNotFound();
        }

        return Result.generated(deviceKeyService.startAuthenticatedRegistrationChallenge(user));
    }

    /**
     * Result of starting authenticated device-key registration.
     * @param status whether the owner existed and challenge generation succeeded
     * @param challenge generated account-bound challenge, present only for {@link Status#GENERATED}
     */
    public record Result(Status status, DeviceKeyChallengeResponse challenge) {

        /** Builds the missing-user result. */
        /** @return not-found result without a challenge */
        public static Result userNotFound() {
            return new Result(Status.USER_NOT_FOUND, null);
        }

        /** Builds the successful challenge result. */
        /** @param challenge generated registration challenge */
        /** @return generated result */
        public static Result generated(DeviceKeyChallengeResponse challenge) {
            return new Result(Status.GENERATED, challenge);
        }
    }

    /** Outcomes of starting authenticated device-key registration. */
    public enum Status {
        /** Challenge was generated for the existing account. */
        GENERATED,
        /** No account matched the authenticated principal identifier. */
        USER_NOT_FOUND
    }
}
