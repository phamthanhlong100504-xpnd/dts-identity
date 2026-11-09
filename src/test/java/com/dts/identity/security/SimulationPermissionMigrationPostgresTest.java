package com.dts.identity.security;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SimulationPermissionMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Set<String> PERSONAL = Set.of(
            "simulations:read", "simulations:learn", "media:read", "media:upload",
            "traffic-signs:read", "memory-tips:read");
    static final Set<String> ALL = Set.of(
            "simulations:read", "simulations:learn", "media:read", "media:upload",
            "media:update", "media:delete", "media:manage",
            "simulation-scenarios:read", "simulation-scenarios:create", "simulation-scenarios:publish",
            "traffic-signs:read", "traffic-signs:create", "memory-tips:read", "memory-tips:create",
            "learning-programs:read", "learning-programs:create", "learning-programs:update",
            "learning-programs:delete", "chapters:delete");
    static final String MIGRATION = "db/migration/V12__add_simulation_and_media_permissions.sql";

    record Database(String schema, String url, JdbcTemplate jdbc) {
        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").schemas(schema).defaultSchema(schema)
                    .target(target).baselineOnMigrate(false).validateOnMigrate(true)
                    .outOfOrder(false).cleanDisabled(true).load();
        }
        void upgrade() { flyway("12").migrate(); flyway("12").validate(); }
    }
    Database database(String target) {
        String schema = "sim_" + UUID.randomUUID().toString().replace("-", "");
        String url = postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        Database db = new Database(schema, url, new JdbcTemplate(
                new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword())));
        db.flyway(target).migrate();
        return db;
    }
    Set<String> grants(Database db, String role) {
        Set<String> result = new HashSet<>(db.jdbc().queryForList(
                "SELECT p.name FROM role_permissions rp JOIN roles r ON r.id=rp.role_id " +
                "JOIN permissions p ON p.id=rp.permission_id WHERE r.name=? AND p.deleted_at IS NULL", String.class, role));
        result.retainAll(ALL);
        return result;
    }
    void assertMatrix(Database db) {
        assertEquals(ALL, grants(db, "ROLE_ADMIN"));
        assertEquals(PERSONAL, grants(db, "ROLE_STUDENT"));
        assertEquals(PERSONAL, grants(db, "ROLE_TEACHER"));
        assertEquals(Set.of(), grants(db, "ROLE_GUEST"));
        assertEquals(19, db.jdbc().queryForList("SELECT name FROM permissions WHERE deleted_at IS NULL", String.class)
                .stream().filter(ALL::contains).count());
    }
    Map<String, List<String>> snapshot(Database db) {
        Map<String, List<String>> result = new TreeMap<>();
        for (String table : db.jdbc().queryForList("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename", String.class, db.schema())) {
            if (!table.matches("[a-z_]+")) throw new IllegalStateException("Unexpected fixture table");
            result.put(table, db.jdbc().queryForList("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY to_jsonb(t)::text", String.class));
        }
        return result;
    }
    void assertPreserved(Database db, Map<String, List<String>> before) {
        Map<String, List<String>> after = snapshot(db);
        assertEquals(before.keySet(), after.keySet());
        before.forEach((table, rows) -> {
            if (Set.of("permissions", "role_permissions", "flyway_schema_history").contains(table))
                assertTrue(after.get(table).containsAll(rows), "Historical rows changed: " + table);
            else assertEquals(rows, after.get(table), "Unrelated table changed: " + table);
        });
    }
    @Test void upgradeV11AddsNineteenPermissionsAndThirtyOneMappingsAndPreservesUserData() {
        Database db = database("11");
        UUID user = UUID.randomUUID();
        db.jdbc().update("INSERT INTO users(id,username,email,password,full_name,birth_of_date,phone_number,status,selected_license_class) " +
                "VALUES(?,?,'simulation-fixture@example.invalid','fixture-hash','Migration fixture','2000-01-01','0900000000','ACTIVE','B')", user, "sim_" + user.toString().substring(0,8));
        db.jdbc().update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_STUDENT'", user);
        db.jdbc().update("INSERT INTO refresh_tokens(user_id,token_hash,device_info,expires_at) VALUES(?,'fixture-only','fixture device',now()+interval '1 day')", user);
        db.jdbc().update("INSERT INTO verification_codes(user_id,code_hash,type,expires_at) VALUES(?,'fixture-code','REGISTER',now()+interval '1 hour')", user);
        db.jdbc().update("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,topic,payload) VALUES('USER',?,'FIXTURE','fixture-only','{}'::jsonb)", user.toString());
        Map<String, List<String>> before = snapshot(db);
        long permissions = db.jdbc().queryForObject("SELECT count(*) FROM permissions", Long.class);
        long mappings = db.jdbc().queryForObject("SELECT count(*) FROM role_permissions", Long.class);
        db.upgrade(); assertMatrix(db); assertPreserved(db, before);
        assertEquals(permissions + 19, db.jdbc().queryForObject("SELECT count(*) FROM permissions", Long.class));
        assertEquals(mappings + 31, db.jdbc().queryForObject("SELECT count(*) FROM role_permissions", Long.class));
        assertEquals(1L, db.jdbc().queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='12' AND success", Long.class));
    }
    @Test void freshDatabaseRunsWholeChainThroughV12() {
        Database db = database("12"); assertMatrix(db);
        assertEquals(12L, db.jdbc().queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success", Long.class));
    }
    @Test void repeatFlywayDoesNotChangeRowsOrHistory() {
        Database db = database("12"); Map<String, List<String>> before = snapshot(db);
        db.upgrade(); assertEquals(before, snapshot(db));
    }
    @Test void conflictHandlingDoesNotDuplicateRowsWhenSqlIsReplayedInAnIsolatedDatabase() throws Exception {
        Database db = database("12"); Map<String, List<String>> before = snapshot(db);
        try (Connection connection = new DriverManagerDataSource(db.url(), postgres.getUsername(), postgres.getPassword()).getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
        }
        assertEquals(before, snapshot(db));
    }
    @Test void activePermissionMetadataAndManualCustomGrantArePreserved() {
        Database db = database("11"); UUID id = UUID.randomUUID(), role = UUID.randomUUID();
        db.jdbc().update("INSERT INTO permissions(id,name,display_name,resource,metadata) VALUES(?,'simulations:read','Existing display','simulations','{\"keep\":true}'::jsonb)", id);
        db.jdbc().update("INSERT INTO roles(id,name) VALUES(?,'ROLE_SIM_CUSTOM')", role);
        db.jdbc().update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)", role, id);
        Map<String, List<String>> before = snapshot(db); db.upgrade(); assertMatrix(db); assertPreserved(db, before);
        assertEquals(id, db.jdbc().queryForObject("SELECT id FROM permissions WHERE name='simulations:read' AND deleted_at IS NULL", UUID.class));
        assertEquals(Set.of("simulations:read"), grants(db, "ROLE_SIM_CUSTOM"));
    }
    @Test void softDeletedPermissionGetsSeparateActiveRowWithoutResurrection() {
        Database db = database("11"); UUID id = UUID.randomUUID();
        db.jdbc().update("INSERT INTO permissions(id,name,display_name,resource,deleted_at) VALUES(?,'media:read','Historical','media',now())", id);
        Map<String, List<String>> before = snapshot(db); db.upgrade(); assertMatrix(db); assertPreserved(db, before);
        assertNotEquals(id, db.jdbc().queryForObject("SELECT id FROM permissions WHERE name='media:read' AND deleted_at IS NULL", UUID.class));
    }
    @Test void inactiveRoleReceivesNoNewGrantAndIsNotReactivated() {
        Database db = database("11"); db.jdbc().update("UPDATE roles SET deleted_at=now() WHERE name='ROLE_TEACHER'");
        Map<String, List<String>> before = snapshot(db); db.upgrade(); assertPreserved(db, before);
        assertEquals(Set.of(), grants(db, "ROLE_TEACHER"));
        assertEquals(PERSONAL, grants(db, "ROLE_STUDENT")); assertEquals(ALL, grants(db, "ROLE_ADMIN"));
        assertEquals(1L, db.jdbc().queryForObject("SELECT count(*) FROM roles WHERE name='ROLE_TEACHER' AND deleted_at IS NOT NULL", Long.class));
    }
    @Test void guestAndCustomRoleReceiveNoAutomaticRights() {
        Database db = database("11"); db.jdbc().update("INSERT INTO roles(name) VALUES('ROLE_SIM_CUSTOM')");
        db.upgrade(); assertMatrix(db); assertEquals(Set.of(), grants(db, "ROLE_SIM_CUSTOM"));
        assertFalse(grants(db, "ROLE_STUDENT").contains("simulation-scenarios:read"));
        assertFalse(grants(db, "ROLE_TEACHER").contains("media:manage"));
    }
    @Test void repeatFlywayDoesNotReseedAnAdministrativelyRevokedGrant() {
        Database db = database("12");
        db.jdbc().update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE name='ROLE_STUDENT') " +
                "AND permission_id=(SELECT id FROM permissions WHERE name='simulations:learn' AND deleted_at IS NULL)");
        Map<String, List<String>> before = snapshot(db); db.upgrade(); assertEquals(before, snapshot(db));
        assertFalse(grants(db, "ROLE_STUDENT").contains("simulations:learn"));
    }
}
