package com.dts.identity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import static org.junit.jupiter.api.Assertions.*;

class AdminOperationalStatusSecurityTest {
    @Test void ownerStatusDoesNotInventUnavailableDependenciesOrLeakyCaching() {
        var db=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(db.queryForObject(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Integer.class))).thenReturn(1);
        var reply=new AdminOperationalStatusController(db).status();
        assertEquals("dts-identity",reply.getBody().get("sourceService"));
        assertEquals("PARTIAL",reply.getBody().get("dataStatus"));
        assertEquals("READY",reply.getBody().get("databaseRead"));
        assertEquals("NOT_CHECKED",reply.getBody().get("authAndTokenIssuance"));
        assertEquals("no-store",reply.getHeaders().getFirst("Cache-Control"));
    }
    @Test void operationHasIndependentSystemStatusGrant() throws Exception {
        var method=AdminOperationalStatusController.class.getMethod("status");
        var policy=method.getAnnotation(PreAuthorize.class);
        assertNotNull(policy);
        assertTrue(policy.value().contains("ROLE_ADMIN"));
        assertTrue(policy.value().contains("PERM_system-status:read"));
        assertFalse(policy.value().contains("PERM_reports:read"));
        assertFalse(policy.value().contains("PERM_integrations:read"));
        assertEquals("/api/v1/admin/operations/identity-status",method.getAnnotation(GetMapping.class).value()[0]);
        assertEquals(ResponseEntity.class,method.getReturnType());
    }
}
