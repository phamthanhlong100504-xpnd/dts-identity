package com.dts.identity.controller;

import com.dts.identity.dto.response.RoleResponse;
import com.dts.identity.entity.User;
import com.dts.identity.repository.UserRepository;
import com.dts.identity.service.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Admin06CapabilitiesTest {
    final UUID actor=UUID.randomUUID();
    final UserService users=mock(UserService.class);
    final UserRepository repository=mock(UserRepository.class);
    final AdminCapabilitiesController controller=new AdminCapabilitiesController(users,repository);

    UsernamePasswordAuthenticationToken token(String... permissions) {
        return new UsernamePasswordAuthenticationToken(actor,null,
            java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
    void live(String role,List<String> grants) {
        when(repository.findById(actor)).thenReturn(Optional.of(User.builder()
            .id(actor).status(User.UserStatus.ACTIVE).build()));
        when(users.getUserRoles(actor)).thenReturn(List.of(new RoleResponse(UUID.randomUUID(),role,grants)));
    }
    boolean can(String resource,String action,String... jwt) {
        return controller.current(token(jwt)).getBody().data().resources().stream()
            .filter(r -> resource.equals(r.resource())).findFirst().orElseThrow().actions().contains(action);
    }
    @Test void newlySeededReadGrantsAppearOnlyWhenDbAndJwtAgree() {
        live("ROLE_ADMIN", List.of("dashboard:read","audit:read","integrations:read","system-status:read"));
        var response=controller.current(token("ROLE_ADMIN","PERM_dashboard:read","PERM_audit:read",
            "PERM_integrations:read","PERM_system-status:read")).getBody().data();
        assertTrue(response.pages().get("dashboard"));
        assertTrue(response.pages().get("audit-logs"));
        assertTrue(response.pages().get("integrations"));
        assertTrue(response.pages().get("system-monitoring"));
        assertTrue(response.resources().stream().filter(r->r.resource().equals("audit"))
            .findFirst().orElseThrow().actions().contains("read"));
        assertTrue(response.resources().stream().filter(r->r.resource().equals("integrations"))
            .findFirst().orElseThrow().actions().contains("read"));
        assertFalse(can("integrations","retry","ROLE_ADMIN","PERM_integrations:read"));
    }
    @Test void staleJwtAndRevokedDatabaseGrantNeverRevealNewCapability() {
        live("ROLE_ADMIN", List.of("dashboard:read"));
        var response=controller.current(token("ROLE_ADMIN","PERM_dashboard:read","PERM_integrations:read",
            "PERM_audit:read","PERM_system-status:read")).getBody().data();
        assertFalse(response.pages().get("integrations"));
        assertFalse(response.pages().get("audit-logs"));
        assertFalse(response.pages().get("system-monitoring"));
        assertFalse(response.resources().stream().filter(r->r.resource().equals("integrations"))
            .findFirst().orElseThrow().actions().contains("read"));
        var stale=controller.current(token("ROLE_ADMIN","PERM_reports:read")).getBody().data();
        assertFalse(stale.resources().stream().filter(r->r.resource().equals("dashboard"))
            .findFirst().orElseThrow().actions().contains("read"));
    }
    @Test void legacyRightsPreserveExistingPagesDuringTokenTransition() {
        live("ROLE_ADMIN", List.of("reports:read","admin-audit:read"));
        var data=controller.current(token("ROLE_ADMIN","PERM_reports:read","PERM_admin-audit:read"))
            .getBody().data();
        assertTrue(data.pages().get("dashboard"));
        assertFalse(data.pages().get("integrations"));
        assertFalse(data.pages().get("system-monitoring"));
        assertFalse(data.pages().get("audit-logs"));
        assertFalse(data.resources().stream().filter(r->r.resource().equals("integrations"))
            .findFirst().orElseThrow().actions().contains("read"));
    }
    @Test void studentWithRawSensitivePermissionCannotOpenAdminPagesOrResources() {
        live("ROLE_STUDENT",List.of("audit:read","integrations:read","system-status:read"));
        var data=controller.current(token("ROLE_STUDENT","PERM_audit:read",
            "PERM_integrations:read","PERM_system-status:read")).getBody().data();
        assertFalse(data.canEnterAdmin());
        assertFalse(data.pages().get("audit-logs"));
        assertFalse(data.pages().get("integrations"));
        assertFalse(data.pages().get("system-monitoring"));
        assertTrue(data.resources().stream().filter(r->r.resource().equals("audit"))
            .findFirst().orElseThrow().actions().isEmpty());
    }
}
