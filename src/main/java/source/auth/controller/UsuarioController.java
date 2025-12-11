package source.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.dto.UserDTO;

/**
 * Controller for user-related operations such as listing, creating, and authenticating users.
 */
@RestController
@RequestMapping("/auth")
public class UsuarioController {
    private final Login login;
    private final Signup signup;


    public UsuarioController(Login login,
                             Signup signup

    ) {
        this.login = login;
        this.signup = signup;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserDTO userDTO, HttpServletRequest request) {
        String id = login.loginUser(userDTO, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(id);
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody UserDTO userDTO) {
        String key = signup.signupUser(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(key);
    }

    @PostMapping("/signup/totp/verify")
    public ResponseEntity<String> totpCodeVerify(@RequestBody UserDTO userDTO,
                                                 @RequestHeader("X-Device-Hash") String deviceHash,
                                                 HttpServletRequest request) {

        String token = signup.createUser(userDTO, deviceHash,request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(token);
    }

    @PostMapping("/login/totp/verify")
    public ResponseEntity<String> loginTotpVerify(@RequestBody UserDTO userDTO,
                                                   @RequestHeader("X-Device-Hash") String deviceHash,
                                                   HttpServletRequest request) {

        String token = login.loginTotpVerify(userDTO, deviceHash, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(token);
    }

}
