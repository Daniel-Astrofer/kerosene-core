package com.kerosene.platform.config.production;

public interface ProductionSafetyCheck {

    void handle(ProductionSafetyContext context);
}
