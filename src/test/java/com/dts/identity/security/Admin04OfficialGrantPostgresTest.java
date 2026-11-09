package com.dts.identity.security;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class Admin04OfficialGrantPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final Set<String> GRANTS = Set.of("exam_session:read", "official-exams:release-result");

    @Test void v15ToV16AddsOnlyAdministrativeRightsAndPreservesExistingMappings() {
        String schema = "adm04_" + UUID.randomUUID().toString().replace("-", "");
        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        var datasource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(datasource);
        var config = Flyway.configure().dataSource(datasource).locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema).validateOnMigrate(true)
                .baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true);
        config.target("15").load().migrate();
        Long prior = jdbc.queryForObject("SELECT count(*) FROM role_permissions", Long.class);
        Long priorRights = jdbc.queryForObject("SELECT count(*) FROM permissions WHERE deleted_at IS NULL", Long.class);
        config.target("16").load().migrate();
        config.target("16").load().validate();
        assertEquals(priorRights + 2, jdbc.queryForObject("SELECT count(*) FROM permissions WHERE deleted_at IS NULL", Long.class));
        assertEquals(prior + 2, jdbc.queryForObject("SELECT count(*) FROM role_permissions", Long.class));
        for(String name : GRANTS) {
            assertTrue(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM role_permissions rp JOIN roles r ON rp.role_id=r.id
                    JOIN permissions p ON rp.permission_id=p.id
                    WHERE r.name='ROLE_ADMIN' AND r.deleted_at IS NULL AND p.name=? AND p.deleted_at IS NULL)
                """, Boolean.class, name));
            assertFalse(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM role_permissions rp JOIN roles r ON rp.role_id=r.id
                    JOIN permissions p ON rp.permission_id=p.id
                    WHERE r.name IN ('ROLE_TEACHER','ROLE_STUDENT') AND r.deleted_at IS NULL
                      AND p.name=? AND p.deleted_at IS NULL)
                """, Boolean.class, name));
        }
        assertTrue(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM permissions WHERE name='official-exams:publish' AND deleted_at IS NULL)
                """, Boolean.class));
        assertEquals(16L, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success",Long.class));
        var count = jdbc.queryForObject("SELECT count(*) FROM role_permissions",Long.class);
        config.target("16").load().migrate();
        assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM role_permissions",Long.class));
    }
}
