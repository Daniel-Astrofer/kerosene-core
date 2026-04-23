package source.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.dto.UserDTO;
import source.auth.dto.SignupResponseDTO;
import source.auth.application.service.pow.PowService;
import source.common.dto.ApiResponse;

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
    private final PowService powService;

    public UserController(Login login,
            Signup signup,
            PowService powService) {
        this.login = login;
        this.signup = signup;
        this.powService = powService;
    }

    @GetMapping("/pow/challenge")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPowChallenge() {
        String challenge = powService.generateChallenge();
        return ResponseEntity.ok(ApiResponse.success("PoW Challenge generated", Map.of("challenge", challenge)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@RequestBody UserDTO userDTO) {
        String id = login.loginUser(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Login processed. Proceed with TOTP only when your account has it enabled.", id));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(@RequestBody UserDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account credentials validated. TOTP is optional, but the setup secret and backup codes are available now if you want to enable it before finishing passkey enrollment.",
                signup.signupUser(dto)));
    }

    @PostMapping("/signup/totp/verify")
    public ResponseEntity<ApiResponse<String>> totpCodeVerify(@RequestBody UserDTO userDTO) {

        String token = signup.createUser(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse
                .success("Signup security session updated successfully.", token));
    }

    @PostMapping("/login/totp/verify")
    public ResponseEntity<ApiResponse<String>> loginTotpVerify(@RequestBody UserDTO userDTO) {

        String token = login.loginTotpVerify(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("TOTP verification successful. You have logged in.", token));
    }

}
