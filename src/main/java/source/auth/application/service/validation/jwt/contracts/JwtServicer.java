package source.auth.application.service.validation.jwt.contracts;

public interface JwtServicer {
    String generateToken(long id, String devicehash);

    String extractDevice(String token);

    Long extractId(String token);

}
