package source.config.production;

public interface ProductionSafetyCheck {

    void handle(ProductionSafetyContext context);
}
