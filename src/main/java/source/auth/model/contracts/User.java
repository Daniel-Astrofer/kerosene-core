package source.auth.model.contracts;

public interface User {

    String getUsername();

    String getPasswordHash();

    Long getId();

    void setUsername(String username);

    void setPasswordHash(String passwordHash);

    default String getPassphrase() {
        return getPasswordHash();
    }

    default void setPassphrase(String passphrase) {
        setPasswordHash(passphrase);
    }

}
