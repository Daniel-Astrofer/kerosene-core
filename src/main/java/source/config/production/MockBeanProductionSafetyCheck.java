package source.config.production;

import java.util.Set;
import org.springframework.util.ClassUtils;

public class MockBeanProductionSafetyCheck extends AbstractProductionSafetyCheck {

    private static final Set<String> ALLOWED_PRODUCTION_MOCK_CLASSES = Set.of();

    public MockBeanProductionSafetyCheck(ProductionSafetyCheck next) {
        super(next);
    }

    @Override
    protected void inspect(ProductionSafetyContext context) {
        for (String beanName : context.beanFactory().getBeanDefinitionNames()) {
            Class<?> beanType;
            try {
                beanType = context.beanFactory().getType(beanName, false);
            } catch (RuntimeException ignored) {
                continue;
            }

            if (beanType == null) {
                continue;
            }

            Class<?> userClass = ClassUtils.getUserClass(beanType);
            Package beanPackage = userClass.getPackage();
            String packageName = beanPackage != null ? beanPackage.getName() : "";
            if (!packageName.startsWith("source.")) {
                continue;
            }

            String simpleName = userClass.getSimpleName().toLowerCase(java.util.Locale.ROOT);
            String className = userClass.getName();
            if ((simpleName.contains("mock") || simpleName.contains("stub"))
                    && !ALLOWED_PRODUCTION_MOCK_CLASSES.contains(className)) {
                context.addViolation("bean " + className + " is not allowed in prod");
            }
        }
    }
}
