package source.config.production;

import java.util.List;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionSafetyCheckChain {

    private final Environment environment;
    private final ListableBeanFactory beanFactory;
    private final ProductionSafetyCheck chain;

    public ProductionSafetyCheckChain(Environment environment, ListableBeanFactory beanFactory) {
        this.environment = environment;
        this.beanFactory = beanFactory;
        this.chain = new MockBeanProductionSafetyCheck(
                new BooleanPropertyProductionSafetyCheck(
                        new TextPropertyProductionSafetyCheck(null)));
    }

    public List<String> collectViolations() {
        ProductionSafetyContext context = new ProductionSafetyContext(environment, beanFactory);
        chain.handle(context);
        return context.violations();
    }
}
