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
public class UsuarioController {
    private final Login login;
    private final Signup signup;
    private final PowService powService;

    public UsuarioController(Login login,
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
                .body(ApiResponse.success("Login request received. Please proceed with TOTP verification.", id));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(@RequestBody UserDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account credentials validated. Please configure your authenticator app using the provided setup key and safely store your backup codes.",
                signup.signupUser(dto)));
    }

    @PostMapping("/signup/totp/verify")
    public ResponseEntity<ApiResponse<String>> totpCodeVerify(@RequestBody UserDTO userDTO) {

        String token = signup.createUser(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse
                .success("Device verified and account successfully created. You are now fully authenticated.", token));
    }

    @PostMapping("/login/totp/verify")
    public ResponseEntity<ApiResponse<String>> loginTotpVerify(@RequestBody UserDTO userDTO) {

        String token = login.loginTotpVerify(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("TOTP verification successful. You have logged in.", token));
    }

}
