package com.dts.identity.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** BE06-09: owner-scoped, no-store operational evidence. No cross-service success is inferred. */
@RestController
public class AdminOperationalStatusController {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public AdminOperationalStatusController(org.springframework.jdbc.core.JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping("/api/v1/admin/operations/identity-status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('PERM_system-status:read')")
    public ResponseEntity<Map<String,Object>> status() {

        Integer db=jdbc.queryForObject("SELECT 1",Integer.class);
        if(db==null || db!=1)throw new IllegalStateException("Identity database not confirmed");
        var out=new LinkedHashMap<String,Object>();out.put("sourceService","dts-identity");out.put("asOf",Instant.now());
        out.put("dataStatus","PARTIAL");out.put("databaseRead","READY");out.put("authAndTokenIssuance","NOT_CHECKED");
        out.put("warnings",List.of("Database connectivity alone does not establish gateway or token issuance availability"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }
}
