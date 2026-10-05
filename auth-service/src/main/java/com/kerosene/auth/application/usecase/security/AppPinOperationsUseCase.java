package com.kerosene.auth.application.usecase.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.account.AppPinService;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.dto.AppPinStatusDTO;
import com.kerosene.auth.dto.ConfigureAppPinRequestDTO;
import com.kerosene.auth.dto.VerifyAppPinRequestDTO;
import com.kerosene.auth.model.entity.UserDataBase;

/** Executes authenticated app-PIN status, setup, and verification operations for the current account. */
@Component
public class AppPinOperationsUseCase {

    /** Resolves the authenticated principal to its persisted user entity. */
    private final UserServiceContract userService;
    /** Implements per-device app-PIN status and mutation rules. */
    private final AppPinService appPinService;

    /** Creates app-PIN operations. */
    /** @param userService user lookup service */
    /** @param appPinService per-device PIN service */
    public AppPinOperationsUseCase(UserServiceContract userService, AppPinService appPinService) {
        this.userService = userService;
        this.appPinService = appPinService;
    }

    /** Returns PIN status for the authenticated account and supplied device reference. */
    /** @param deviceHash registered device hash */
    /** @return app-PIN status */
    public AppPinStatusDTO getStatus(String deviceHash) {
        return appPinService.getStatus(getAuthenticatedUser(), deviceHash);
    }

    /** Configures the PIN for the authenticated account and device. */
    /** @param deviceHash registered device hash */
    /** @param request PIN configuration input */
    /** @return updated PIN status */
    public AppPinStatusDTO configure(String deviceHash, ConfigureAppPinRequestDTO request) {
        return appPinService.configure(getAuthenticatedUser(), deviceHash, request);
    }

    /** Verifies the supplied PIN for the authenticated account and device. */
    /** @param deviceHash registered device hash */
    /** @param request verification input */
    /** @return verification status from the PIN service */
    public AppPinStatusDTO verify(String deviceHash, VerifyAppPinRequestDTO request) {
        return appPinService.verify(getAuthenticatedUser(), deviceHash, request.getPin());
    }

    /** Resolves the current security principal to the persisted account required by PIN operations. */
    /** @return authenticated account entity */
    /** @throws AuthExceptions.InvalidCredentials when principal is absent, malformed, or no longer exists */
    private UserDataBase getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthExceptions.InvalidCredentials("Not authenticated.");
        }

        try {
            Long userId = Long.parseLong(auth.getName());
            return userService.buscarPorId(userId)
                    .orElseThrow(() -> new AuthExceptions.InvalidCredentials("Authenticated user not found."));
        } catch (NumberFormatException e) {
            throw new AuthExceptions.InvalidCredentials("Invalid authentication context.");
        }
    }
}
