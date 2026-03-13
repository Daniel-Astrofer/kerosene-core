package source.auth.application.service.cripto.contracts;

public interface Hasher {

    String hash(char[] input);

    Boolean verify(char[] input, String hash);
}
