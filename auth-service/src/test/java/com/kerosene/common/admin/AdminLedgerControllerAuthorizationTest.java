package com.kerosene.common.admin;

import com.kerosene.common.security.AdminRoles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminLedgerControllerAuthorizationTest {

    @Test
    void classLevelRequiresAdminRole() {
        PreAuthorize preAuthorize = AdminLedgerController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals(AdminRoles.HAS_ANY_ADMIN_ROLE, preAuthorize.value());
    }
}
