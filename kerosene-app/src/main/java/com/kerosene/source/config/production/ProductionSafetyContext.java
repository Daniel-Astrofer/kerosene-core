package source.config.production;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.env.Environment;

public class ProductionSafetyContext {

    private final Environment environment;
    private final ListableBeanFactory beanFactory;
    private final List<String> violations = new ArrayList<>();

    public ProductionSafetyContext(Environment environment, ListableBeanFactory beanFactory) {
        this.environment = environment;
        this.beanFactory = beanFactory;
    }

    public Environment environment() {
        return environment;
    }

    public ListableBeanFactory beanFactory() {
        return beanFactory;
    }

    public void addViolation(String violation) {
        violations.add(violation);
    }

    public List<String> violations() {
        return List.copyOf(violations);
    }
}
