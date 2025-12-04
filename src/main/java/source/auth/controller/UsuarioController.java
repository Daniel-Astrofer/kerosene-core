package source.auth.controller;

import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<String> signup(@RequestBody UserDTO userDTO, HttpServletRequest request) {
        String key = signup.signupUser(userDTO);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(key);
    }

    @PostMapping("/signup/totp/verify")
    public ResponseEntity<String> totpCodeVerify(@RequestBody UserDTO userDTO, HttpServletRequest request) {
        String token = signup.createUser(userDTO, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(token);
    }

}
