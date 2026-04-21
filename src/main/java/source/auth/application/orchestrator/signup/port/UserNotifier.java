package source.auth.application.orchestrator.signup.port;

public interface UserNotifier {

    void notify(Long userId, String title, String body);
}
