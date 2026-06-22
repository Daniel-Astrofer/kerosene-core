package source.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.application.usecase.user.GeneratePowChallengeUseCase;
import source.auth.dto.UserDTO;
import source.auth.dto.SignupResponseDTO;
import source.auth.dto.SignupTotpVerifyRequestDTO;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;

import java.util.Map;

/**
 * Controller for user-related operations such as listing, creating, and
 * authenticating users.
 */
@RestController
@RequestMapping("/auth")
public class UserController {
    private final Login login;
    private final Signup signup;
    private final GeneratePowChallengeUseCase generatePowChallengeUseCase;
    private final JwtServicer jwtService;

    public UserController(Login login,
            Signup signup,
            GeneratePowChallengeUseCase generatePowChallengeUseCase,
            JwtServicer jwtService) {
        this.login = login;
        this.signup = signup;
        this.generatePowChallengeUseCase = generatePowChallengeUseCase;
        this.jwtService = jwtService;
    }

    @GetMapping("/pow/challenge")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPowChallenge() {
        return ResponseEntity.ok(
                ApiResponse.success("PoW Challenge generated", generatePowChallengeUseCase.execute()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@Valid @RequestBody UserDTO userDTO) {
        String id = login.loginUser(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Login processed. Proceed with TOTP only when your account has it enabled.", id));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(@Valid @RequestBody UserDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account credentials validated. TOTP is optional, but the setup secret and backup codes are available now if you want to enable it before finishing passkey enrollment.",
                signup.signupUser(dto)));
    }

    @PostMapping("/signup/totp/verify")
    public ResponseEntity<ApiResponse<String>> verifySignupTotpCode(
            @Valid @RequestBody SignupTotpVerifyRequestDTO request) {
        UserDTO userDTO = new UserDTO();
        userDTO.setSessionId(request.getSessionId());
        userDTO.setTotpCode(request.getTotpCode());

        String token = signup.createUser(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse
                .success("Signup security session updated successfully.", token));
    }

    @PostMapping("/login/totp/verify")
    public ResponseEntity<ApiResponse<String>> verifyLoginTotpCode(@Valid @RequestBody UserDTO userDTO) {

        String token = login.loginTotpVerify(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("TOTP verification successful. You have logged in.", token));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication is required to logout.", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        try {
            jwtService.revokeSession(token);
            return ResponseEntity.ok(ApiResponse.success("Session revoked."));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unable to revoke the current session.", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

}
