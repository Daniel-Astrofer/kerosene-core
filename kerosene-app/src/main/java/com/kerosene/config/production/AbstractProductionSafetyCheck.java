package source.config.production;

public abstract class AbstractProductionSafetyCheck implements ProductionSafetyCheck {

    private final ProductionSafetyCheck next;

    protected AbstractProductionSafetyCheck(ProductionSafetyCheck next) {
        this.next = next;
    }

    @Override
    public final void handle(ProductionSafetyContext context) {
        inspect(context);
        if (next != null) {
            next.handle(context);
        }
    }

    protected abstract void inspect(ProductionSafetyContext context);
}
