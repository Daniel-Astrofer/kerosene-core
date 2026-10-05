package com.kerosene.admin.controller;

import com.kerosene.platform.security.AdminRoles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminOperationsControllerAuthorizationTest {

    @Test
    void operationsEndpointsRequireAdminOrOperatorRole() {
        PreAuthorize preAuthorize = AdminOperationsController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(AdminRoles.HAS_ADMIN_OR_OPERATOR, preAuthorize.value());
    }
}
