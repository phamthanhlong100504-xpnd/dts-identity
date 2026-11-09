package com.dts.identity.security;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class Admin06RbacPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    static final Set<String> READ=Set.of("dashboard:read","audit:read","integrations:read","system-status:read");
    static final Set<String> CATALOG=Set.of("dashboard:read","audit:read","integrations:read","integrations:retry","system-status:read");
    @Test void v16ToV17AddsOnlyReadGrantsAndNeverGrantsManualRetry() {
        String schema="adm06_"+UUID.randomUUID().toString().replace("-","");
        var datasource=new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),POSTGRES.getPassword());
        var jdbc=new JdbcTemplate(datasource);
        Flyway.configure().dataSource(datasource).locations("classpath:db/migration")
            .schemas(schema).defaultSchema(schema).target("16")
            .baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true).load().migrate();
        long oldRights=jdbc.queryForObject("SELECT count(*) FROM "+schema+".permissions WHERE deleted_at IS NULL",Long.class);
        long oldGrants=jdbc.queryForObject("SELECT count(*) FROM "+schema+".role_permissions",Long.class);
        long oldUsers=jdbc.queryForObject("SELECT count(*) FROM "+schema+".users",Long.class);
        var flyway=Flyway.configure().dataSource(datasource).locations("classpath:db/migration")
            .schemas(schema).defaultSchema(schema).target("17")
            .baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true).load();
        var migrated=flyway.migrate();
        assertEquals(1,migrated.migrationsExecuted);
        flyway.validate();
        assertEquals(oldRights+5,jdbc.queryForObject("SELECT count(*) FROM "+schema+".permissions WHERE deleted_at IS NULL",Long.class));
        assertEquals(oldGrants+4,jdbc.queryForObject("SELECT count(*) FROM "+schema+".role_permissions",Long.class));
        assertEquals(oldUsers,jdbc.queryForObject("SELECT count(*) FROM "+schema+".users",Long.class));
        for(String permission:CATALOG) {
            assertEquals(permission.substring(0,permission.indexOf(':')),jdbc.queryForObject(
                "SELECT resource FROM "+schema+".permissions WHERE name=? AND deleted_at IS NULL",String.class,permission));
            var holders=jdbc.queryForList("SELECT r.name FROM "+schema+".role_permissions rp "
                +"JOIN "+schema+".roles r ON r.id=rp.role_id "
                +"JOIN "+schema+".permissions p ON p.id=rp.permission_id WHERE p.name=?",String.class,permission);
            assertEquals(READ.contains(permission)?List.of("ROLE_ADMIN"):List.of(),holders);
        }
        assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+schema+".permissions WHERE name='reports:read' AND deleted_at IS NULL)",Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+schema+".permissions WHERE name='admin-audit:read' AND deleted_at IS NULL)",Boolean.class));
        assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(oldRights+5,jdbc.queryForObject("SELECT count(*) FROM "+schema+".permissions WHERE deleted_at IS NULL",Long.class));
        assertEquals(oldGrants+4,jdbc.queryForObject("SELECT count(*) FROM "+schema+".role_permissions",Long.class));
    }
}
