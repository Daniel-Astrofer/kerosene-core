package com.kerosene.config.production;

public interface ProductionSafetyCheck {

    void handle(ProductionSafetyContext context);
}
