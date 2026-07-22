package source.content.dto;

public record HomeRestingHeaderDTO(Greeting greeting, Actions actions) {

    public record Greeting(String template, Boolean includeName) {}

    public record Actions(
            String placement,
            Boolean balanceVisibility,
            Boolean notifications,
            Boolean settings) {}

    public static HomeRestingHeaderDTO defaults() {
        return new HomeRestingHeaderDTO(
                new Greeting("TIME_OF_DAY", true),
                new Actions("TRAILING", true, true, true));
    }
}
