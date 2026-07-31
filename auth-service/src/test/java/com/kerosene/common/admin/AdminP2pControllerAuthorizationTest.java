package com.kerosene.common.admin;

import com.kerosene.common.security.AdminRoles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminP2pControllerAuthorizationTest {

    @Test
    void classLevelRequiresAdminRole() {
        PreAuthorize preAuthorize = AdminP2pController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(AdminRoles.HAS_ANY_ADMIN_ROLE, preAuthorize.value());
    }
}
