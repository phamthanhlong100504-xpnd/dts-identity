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

class AdminCapabilitiesControllerTest {
    final UUID actor=UUID.randomUUID();
    final UserService users=mock(UserService.class);
    final UserRepository repo=mock(UserRepository.class);
    final AdminCapabilitiesController controller=new AdminCapabilitiesController(users,repo);
    UsernamePasswordAuthenticationToken auth(String... values) {
        return new UsernamePasswordAuthenticationToken(actor, null,
            List.of(values).stream().map(SimpleGrantedAuthority::new).toList());
    }
    void active() {
        when(repo.findById(actor)).thenReturn(Optional.of(User.builder()
            .id(actor).status(User.UserStatus.ACTIVE).build()));
    }
    @Test void legacyAdminKeepsReadOnlyRightsAndNoAssumedPublish() {
        active();
        when(users.getUserRoles(actor)).thenReturn(List.of(new RoleResponse(UUID.randomUUID(),"ROLE_ADMIN",
            List.of("questions:read","questions:publish","users:read"))));
        var g=controller.current(auth("ROLE_ADMIN","PERM_questions:read","PERM_users:read")).getBody().data();
        assertTrue(g.canEnterAdmin());assertTrue(g.pages().get("questions"));
        assertTrue(g.resources().stream().filter(r->r.resource().equals("questions")).findFirst().orElseThrow().actions().contains("read"));
        assertFalse(g.resources().stream().filter(r->r.resource().equals("questions")).findFirst().orElseThrow().actions().contains("publish"));
    }
    @Test void permissionInJwtButRevokedInDbIsNotGranted() {
        active();when(users.getUserRoles(actor)).thenReturn(List.of(new RoleResponse(UUID.randomUUID(),"ROLE_ADMIN",List.of("users:read"))));
        var grant=controller.current(auth("ROLE_ADMIN","PERM_questions:read","PERM_users:read")).getBody().data();
        assertFalse(grant.pages().get("questions"));
    }
    @Test void userWithoutAdminRightsCannotEnter() {
        active();when(users.getUserRoles(actor)).thenReturn(List.of(new RoleResponse(UUID.randomUUID(),"ROLE_STUDENT",List.of("simulations:read","questions:read"))));
        var grant=controller.current(auth("ROLE_STUDENT","PERM_simulations:read","PERM_questions:read")).getBody().data();
        assertFalse(grant.canEnterAdmin());
    }

    @Test void officialSessionAndManualReleaseRequireExactDbAndJwtGrants() {
        active();
        when(users.getUserRoles(actor)).thenReturn(List.of(new RoleResponse(UUID.randomUUID(), "ROLE_ADMIN",
            List.of("exams:read", "exam_session:read", "official-exams:release-result"))));
        var full = controller.current(auth("ROLE_ADMIN", "PERM_exams:read", "PERM_exam_session:read",
            "PERM_official-exams:release-result")).getBody().data();
        assertTrue(full.pages().get("exam-sessions"));
        assertTrue(full.resources().stream().filter(r -> r.resource().equals("exam_session"))
            .findFirst().orElseThrow().actions().contains("read"));
        assertTrue(full.resources().stream().filter(r -> r.resource().equals("official-exams"))
            .findFirst().orElseThrow().actions().contains("release-result"));

        var jwtStale = controller.current(auth("ROLE_ADMIN", "PERM_exams:read",
            "PERM_official-exams:release-result")).getBody().data();
        assertFalse(jwtStale.pages().get("exam-sessions"));
        assertFalse(jwtStale.resources().stream().filter(r -> r.resource().equals("exam_session"))
            .findFirst().orElseThrow().actions().contains("read"));
    }
    @Test void legacyExamsReadNeverAuthorizesOtherUsersSessionMonitoring() {
        active();
        when(users.getUserRoles(actor)).thenReturn(List.of(new RoleResponse(UUID.randomUUID(), "ROLE_ADMIN",
            List.of("exams:read"))));
        var grant = controller.current(auth("ROLE_ADMIN", "PERM_exams:read", "PERM_exam_session:read",
            "PERM_official-exams:release-result")).getBody().data();
        assertTrue(grant.pages().get("exams"));
        assertFalse(grant.pages().get("exam-sessions"));
        assertTrue(grant.resources().stream().filter(r -> r.resource().equals("exam_session"))
            .findFirst().orElseThrow().actions().isEmpty());
        assertFalse(grant.resources().stream().filter(r -> r.resource().equals("official-exams"))
            .findFirst().orElseThrow().actions().contains("release-result"));
    }
    @Test void nonAdminCannotReceiveSensitiveExamSessionCapabilities() {
        active();
        when(users.getUserRoles(actor)).thenReturn(List.of(new RoleResponse(UUID.randomUUID(), "ROLE_STUDENT",
            List.of("exam_session:read", "official-exams:release-result"))));
        var grant = controller.current(auth("ROLE_STUDENT", "PERM_exam_session:read",
            "PERM_official-exams:release-result")).getBody().data();
        assertFalse(grant.canEnterAdmin());
        assertFalse(grant.pages().get("exam-sessions"));
        assertTrue(grant.resources().stream().filter(r -> r.resource().equals("exam_session"))
            .findFirst().orElseThrow().actions().isEmpty());
        assertFalse(grant.resources().stream().filter(r -> r.resource().equals("official-exams"))
            .findFirst().orElseThrow().actions().contains("release-result"));
    }
}
