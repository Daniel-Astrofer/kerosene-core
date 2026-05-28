package source.auth.application.service.security.profile;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class AdvancedAccountSecurityProfileHandler extends AbstractAccountSecurityProfileHandler {

    private final AdvancedAccountSecurityAvailability availability;

    public AdvancedAccountSecurityProfileHandler(AdvancedAccountSecurityAvailability availability) {
        this.availability = availability;
    }

    @Override
    public void handle(AccountSecurityProfileContext context) {
        availability.assertSupported(context.getSecurityType());
        handleNext(context);
    }
}
