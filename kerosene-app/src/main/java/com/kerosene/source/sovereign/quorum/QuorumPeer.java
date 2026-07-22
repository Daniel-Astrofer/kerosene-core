package source.sovereign.quorum;

public record QuorumPeer(String baseUrl) {

    public QuorumPeer {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Quorum peer URL must not be blank");
        }
        baseUrl = trimTrailingSlash(baseUrl.trim());
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("Quorum peer URL must not be blank");
        }
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Quorum peers must use explicit https:// URLs.");
        }
    }

    public String endpoint(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Quorum endpoint path must not be blank");
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
