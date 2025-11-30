package source.auth.model.contracts;

public interface User {

    String getUsername();

    String getPassphrase();

    Long getId();

    void setUsername(String username);

    void setPassphrase(String passphrase);


}
