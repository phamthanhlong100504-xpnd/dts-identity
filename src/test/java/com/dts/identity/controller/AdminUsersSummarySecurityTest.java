package com.dts.identity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.junit.jupiter.api.Assertions.*;

class AdminUsersSummarySecurityTest {
    @Test void noGenericAdminRoleCanBypassReportingGrant() throws Exception {
        var method=java.util.Arrays.stream(AdminUsersSummaryController.class.getDeclaredMethods())
                .filter(m->m.getName().equals("get")).findFirst().orElseThrow();
        var policy=method.getAnnotation(PreAuthorize.class);
        assertNotNull(policy);assertTrue(policy.value().contains("ROLE_ADMIN"));
        assertTrue(policy.value().contains("PERM_reports:read"));
    }
}
