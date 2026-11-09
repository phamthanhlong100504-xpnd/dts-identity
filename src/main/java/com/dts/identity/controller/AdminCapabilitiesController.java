package com.dts.identity.controller;

import com.dts.identity.dto.response.ApiResponse;
import com.dts.identity.dto.response.RoleResponse;
import com.dts.identity.entity.User;
import com.dts.identity.repository.UserRepository;
import com.dts.identity.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Independent of legacy AdminController (which remains ROLE_ADMIN-only).
 * Effective permissions are the INTERSECTION of live DB grants and current JWT claims,
 * preventing stale tokens from elevating access after a role assignment.
 */
@RestController
@RequestMapping("/api/v1/admin/me")
@RequiredArgsConstructor
public class AdminCapabilitiesController {
    private static final Set<String> RESOURCES = Set.of(
        "questions", "chapters", "learning-programs", "traffic-signs", "memory-tips",
        "practice-exam-sets", "official-exams", "exam_session", "simulation-scenarios", "simulation-exam-sets",
        "simulation-guide", "media", "content-releases", "reports", "users", "roles", "permissions",
        "exams", "exam", "exam_version", "exam_structure", "exam_rule", "exam_criteria", "admin-audit", "admin-portal", "practice-policy-summary",
        "dashboard", "audit", "integrations", "system-status");
    private static final Set<String> ACTIONS = Set.of(
        "read", "create", "update", "delete", "submit", "approve", "publish", "archive", "audit", "assign", "revoke", "manage", "clone", "enter", "retry", "release-result");
    private final UserService userService;
    private final UserRepository userRepository;

    public record ResourceGrant(String resource, List<String> actions, String scopeLabel) { }
    public record Grant(String actorId, boolean canEnterAdmin, String verifiedAt, String expiresAt,
                        Map<String, Boolean> pages, List<ResourceGrant> resources) { }

    @GetMapping("/capabilities")
    public ResponseEntity<ApiResponse<Grant>> current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID actor)) {
            throw new AccessDeniedException("Authenticated actor required");
        }
        User user = userRepository.findById(actor)
            .orElseThrow(() -> new AccessDeniedException("Account not available"));
        if (user.getStatus() != User.UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new AccessDeniedException("Active account required");
        }
        List<RoleResponse> live = userService.getUserRoles(actor);
        Set<String> jwtRoles = authentication.getAuthorities().stream()
            .map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_")).collect(Collectors.toSet());
        Set<String> jwtPerms = authentication.getAuthorities().stream()
            .map(a -> a.getAuthority()).filter(a -> a.startsWith("PERM_"))
            .map(a -> a.substring(5)).collect(Collectors.toSet());
        Set<String> activeRoles = live.stream().map(RoleResponse::name)
            .filter(jwtRoles::contains).collect(Collectors.toSet());
        Set<String> dbPerms = live.stream().filter(r -> activeRoles.contains(r.name()))
            .flatMap(r -> r.permissions().stream()).collect(Collectors.toSet());
        dbPerms.retainAll(jwtPerms);
        boolean admin = activeRoles.contains("ROLE_ADMIN");
        // questions:read is deliberately granted to students in V1. It must not open Admin.
        boolean enter = admin || dbPerms.contains("admin-portal:enter");
        List<ResourceGrant> resources = RESOURCES.stream().sorted().map(resource -> {
            List<String> verbs = ACTIONS.stream().sorted().filter(action -> {
                if ((Set.of("dashboard", "audit", "integrations", "system-status", "exam_session").contains(resource)
                        || ("official-exams".equals(resource) && "release-result".equals(action))) && !admin) return false;
                if (dbPerms.contains(resource + ":" + action)) return true;
                if (!admin) return false;
                // Legacy Identity AdminController is ROLE_ADMIN-guarded for CRUD and must not disappear.
                if (Set.of("users","roles","permissions").contains(resource) &&
                    Set.of("read","create","update","delete").contains(action)) return true;
                if (resource.equals("practice-policy-summary")) return action.equals("read") && dbPerms.contains("exams:read");
                if (Set.of("exam","exam_version","exam_structure","exam_rule","exam_criteria").contains(resource)) {
                    String legacyAction = action.equals("clone") ? "create"
                        : action.equals("publish") || action.equals("archive") ? "update" : action;
                    return dbPerms.contains("exams:" + legacyAction);
                }
                return false;
            }).toList();
            return new ResourceGrant(resource, verbs, "GLOBAL");
        }).toList();
        Map<String,Boolean> pages = new LinkedHashMap<>();
        pages.put("dashboard", enter && (dbPerms.contains("dashboard:read") || dbPerms.contains("reports:read")));
        pages.put("questions", dbPerms.contains("questions:read"));
        pages.put("chapters", dbPerms.contains("chapters:read") && dbPerms.contains("questions:read"));
        pages.put("programs", dbPerms.contains("learning-programs:read"));
        pages.put("traffic-signs", dbPerms.contains("traffic-signs:read"));
        pages.put("memory-tips", dbPerms.contains("memory-tips:read"));
        pages.put("practice-exam-sets", dbPerms.contains("practice-exam-sets:read") || (admin && dbPerms.contains("exams:read")));
        boolean examRead = dbPerms.contains("official-exams:read") || dbPerms.contains("exams:read");
        for (String page : List.of("exams", "exam-structures", "exam-criteria", "exam-rules")) pages.put(page, examRead);
        // Monitoring other users' attempts requires its independent BE04 permission.
        // Both DB role grant and JWT claim must agree; generic exams:read is insufficient.
        pages.put("exam-sessions", admin && dbPerms.contains("exam_session:read"));
        pages.put("simulation-scenarios", dbPerms.contains("simulation-scenarios:read"));
        pages.put("simulation-exam-sets", dbPerms.contains("simulation-exam-sets:read"));
        pages.put("simulation-settings", dbPerms.contains("simulation-scenarios:read"));
        pages.put("simulation-guide", dbPerms.contains("simulation-guide:read") || dbPerms.contains("simulation-guides:read"));
        pages.put("media", dbPerms.contains("media:read"));
        pages.put("content-releases", dbPerms.contains("content-releases:read"));
        pages.put("reports", dbPerms.contains("reports:read") || (admin && dbPerms.contains("analytics:read")));
        // Sensitive Admin operations now need independent live DB + JWT grants.
        // Historical admin-audit and reports permissions remain available for their
        // existing legacy endpoints, but do not open new BE06 monitoring/audit screens.
        pages.put("system-monitoring", admin && dbPerms.contains("system-status:read"));
        pages.put("integrations", admin && dbPerms.contains("integrations:read"));
        pages.put("audit-logs", admin && dbPerms.contains("audit:read"));
        pages.put("users", admin && dbPerms.contains("users:read"));
        pages.put("roles", admin && dbPerms.contains("roles:read"));
        pages.put("settings", admin);
        Instant now = Instant.now();
        var grant = new Grant(actor.toString(), enter, now.toString(), now.plus(90, ChronoUnit.SECONDS).toString(),
            Collections.unmodifiableMap(pages), resources);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(grant));
    }
}
