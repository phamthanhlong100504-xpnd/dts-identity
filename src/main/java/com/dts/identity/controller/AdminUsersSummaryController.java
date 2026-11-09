package com.dts.identity.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Aggregation within Identity only; login activity must not be mistaken for studying. */
@RestController
@RequestMapping("/api/v1/admin/analytics/users-summary")
public class AdminUsersSummaryController {
    private final JdbcTemplate jdbc;
    public AdminUsersSummaryController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('PERM_reports:read')")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @GetMapping
    public ResponseEntity<Map<String,Object>> get(@RequestParam(required=false) Instant from,
            @RequestParam(required=false) Instant to){
        Instant now=Instant.now(),end=to==null?now:to,start=from==null?end.minus(Duration.ofDays(30)):from;
        if(!start.isBefore(end)||end.isAfter(now.plusSeconds(60))||Duration.between(start,end).compareTo(Duration.ofDays(366))>0)
            throw new IllegalArgumentException("Invalid reporting interval");
        var statuses=jdbc.queryForList("SELECT status,COUNT(*)::bigint total FROM users WHERE deleted_at IS NULL GROUP BY status ORDER BY status");
        var roles=jdbc.queryForList("""
            SELECT r.name role,COUNT(DISTINCT ur.user_id)::bigint users
            FROM user_roles ur JOIN roles r ON r.id=ur.role_id AND r.deleted_at IS NULL
              JOIN users u ON u.id=ur.user_id AND u.deleted_at IS NULL
            GROUP BY r.name ORDER BY r.name
            """);
        Long loginUsers=jdbc.queryForObject("SELECT COUNT(DISTINCT id) FROM users WHERE deleted_at IS NULL AND last_login_at>=? AND last_login_at<?",
                Long.class,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end));
        var response=new LinkedHashMap<String,Object>();response.put("from",start);response.put("to",end);
        response.put("asOf",now);response.put("timezone","Asia/Ho_Chi_Minh");response.put("sourceService","dts-identity");
        response.put("dataStatus","READY");response.put("accountsByStatus",statuses);response.put("accountsByRole",roles);
        response.put("uniqueSignedInAccounts",loginUsers);response.put("activeLearners",null);
        response.put("warnings",List.of("Login does not prove learning activity; obtain activeLearners from dts-result"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
