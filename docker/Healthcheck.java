import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Healthcheck {

    public static void main(String[] args) throws Exception {
        String target = args.length > 0 ? args[0] : "http://127.0.0.1:8080/health/ready";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300 && response.body().contains("\"status\":\"UP\"")) {
            return;
        }
        System.err.println("Healthcheck failed with HTTP " + status + ": " + response.body());
        System.exit(1);
    }
}
