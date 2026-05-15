package source.auth.application.service.validation.jwt.contracts;

public interface JwtServicer {
    String generateToken(long id);

    Long extractId(String token);

}
