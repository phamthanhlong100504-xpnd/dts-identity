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
class IdentityPermissionMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    static final List<String> PERSONAL=List.of("progress:read","progress:update","practice:read","practice:learn","practice:exam");
    static final List<String> ALL=List.of("progress:read","progress:update","practice:read","practice:learn","practice:exam","practice:import");
    static final String NAMES="'progress:read','progress:update','practice:read','practice:learn','practice:exam','practice:import'";
    static final String MIGRATION="db/migration/V11__add_progress_practice_permissions.sql";

    record Database(String schema,String url,JdbcTemplate jdbc) {
        Flyway flyway(String target) {
            var builder=Flyway.configure().dataSource(url,postgres.getUsername(),postgres.getPassword())
                    .locations("classpath:db/migration").schemas(schema).defaultSchema(schema)
                    .baselineOnMigrate(false).validateOnMigrate(true).outOfOrder(false).cleanDisabled(true);
            // Historical V11 regression; newer migrations have their own upgrade tests.
            builder.target(target == null ? "11" : target);
            return builder.load();
        }
        void upgrade(){flyway(null).migrate();flyway(null).validate();}
    }
    Database database(String target) {
        String schema="m_"+UUID.randomUUID().toString().replace("-","");
        String url=postgres.getJdbcUrl()+(postgres.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema;
        Database db=new Database(schema,url,new JdbcTemplate(new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword())));
        db.flyway(target).migrate();return db;
    }
    List<String> grants(Database db,String role) {
        return db.jdbc().queryForList("SELECT p.name FROM role_permissions rp JOIN roles r ON r.id=rp.role_id "+
                "JOIN permissions p ON p.id=rp.permission_id WHERE r.name=? AND p.deleted_at IS NULL AND p.name IN ("+NAMES+") ORDER BY p.name",String.class,role);
    }
    void assertMatrix(Database db) {
        for(String role:List.of("ROLE_ADMIN","ROLE_TEACHER","ROLE_STUDENT")) {
            List<String> expected=new ArrayList<>(role.equals("ROLE_ADMIN")?ALL:PERSONAL);Collections.sort(expected);
            assertEquals(expected,grants(db,role),role);
        }
        assertEquals(List.of(),grants(db,"ROLE_GUEST"));
        assertEquals(6L,db.jdbc().queryForObject("SELECT count(*) FROM permissions WHERE deleted_at IS NULL AND name IN ("+NAMES+")",Long.class));
    }
    Map<String,List<String>> snapshot(Database db) {
        Map<String,List<String>> result=new TreeMap<>();
        for(String table:db.jdbc().queryForList("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename",String.class,db.schema())) {
            if(!table.matches("[a-z_]+"))throw new IllegalStateException("Unexpected fixture table");
            result.put(table,db.jdbc().queryForList("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text",String.class));
        }
        return result;
    }
    void assertPreserved(Database db,Map<String,List<String>> before) {
        Map<String,List<String>> after=snapshot(db);assertEquals(before.keySet(),after.keySet());
        before.forEach((table,rows)->{
            if(Set.of("permissions","role_permissions","flyway_schema_history").contains(table))
                assertTrue(after.get(table).containsAll(rows),"Old rows changed or disappeared: "+table);
            else assertEquals(rows,after.get(table),"Unrelated table changed: "+table);
        });
    }
    void userFixture(Database db) {
        UUID user=UUID.randomUUID();
        db.jdbc().update("INSERT INTO users(id,username,email,password,full_name,birth_of_date,phone_number,status,selected_license_class) "+
                "VALUES(?,?,'fixture@example.invalid','fixture-hash','Migration fixture','2000-01-01','0900000000','ACTIVE','B')",user,"fixture_"+user.toString().substring(0,8));
        db.jdbc().update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_STUDENT'",user);
        db.jdbc().update("INSERT INTO refresh_tokens(user_id,token_hash,device_info,expires_at) VALUES(?,'fixture-only','fixture device',now()+interval '1 day')",user);
        db.jdbc().update("INSERT INTO verification_codes(user_id,code_hash,type,expires_at) VALUES(?,'fixture-code','REGISTER',now()+interval '1 hour')",user);
        db.jdbc().update("INSERT INTO outbox_events(aggregate_type,aggregate_id,event_type,topic,payload) VALUES('USER',?,'FIXTURE','fixture-only','{}'::jsonb)",user.toString());
    }
    @Test void upgradeV10AddsSixPermissionsAndSixteenGrantsWithoutRewritingExistingRows() {
        Database db=database("10");userFixture(db);Map<String,List<String>> before=snapshot(db);
        long permissions=db.jdbc().queryForObject("SELECT count(*) FROM permissions",Long.class);
        long mappings=db.jdbc().queryForObject("SELECT count(*) FROM role_permissions",Long.class);
        db.upgrade();assertMatrix(db);assertPreserved(db,before);
        assertEquals(permissions+6,db.jdbc().queryForObject("SELECT count(*) FROM permissions",Long.class));
        assertEquals(mappings+16,db.jdbc().queryForObject("SELECT count(*) FROM role_permissions",Long.class));
        assertTrue(db.jdbc().queryForList("SELECT p.name FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id JOIN roles r ON r.id=rp.role_id WHERE r.name='ROLE_ADMIN'",String.class)
                .containsAll(List.of("chapters:read","chapters:create","chapters:update","exams:update")));
    }
    @Test void emptyDatabaseRunsWholeChainThroughV11() {
        Database db=database(null);assertMatrix(db);
        assertEquals(11L,db.jdbc().queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success",Long.class));
    }
    @Test void repeatedFlywayMigrateLeavesEveryRowAndHistoryIdentical() {
        Database db=database(null);Map<String,List<String>> before=snapshot(db);db.upgrade();assertEquals(before,snapshot(db));
        assertEquals(1L,db.jdbc().queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='11' AND success",Long.class));
    }
    @Test void insertConflictHandlingIsIdempotentInAnOwnedTestDatabase() throws Exception {
        Database db=database(null);Map<String,List<String>> before=snapshot(db);
        try(Connection connection=new DriverManagerDataSource(db.url(),postgres.getUsername(),postgres.getPassword()).getConnection()) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource(MIGRATION));
        }
        assertEquals(before,snapshot(db));
    }
    @Test void preexistingActivePermissionAndManualCustomRoleGrantKeepTheirIdsAndMetadata() {
        Database db=database("10");UUID permission=UUID.randomUUID(),role=UUID.randomUUID();
        db.jdbc().update("INSERT INTO permissions(id,name,display_name,resource,metadata) VALUES(?,'progress:read','Existing display','progress','{\"keep\":true}'::jsonb)",permission);
        db.jdbc().update("INSERT INTO roles(id,name) VALUES(?,'ROLE_CUSTOM_FIXTURE')",role);
        db.jdbc().update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)",role,permission);
        Map<String,List<String>> before=snapshot(db);db.upgrade();assertPreserved(db,before);assertMatrix(db);
        assertEquals(permission,db.jdbc().queryForObject("SELECT id FROM permissions WHERE name='progress:read' AND deleted_at IS NULL",UUID.class));
        assertEquals(List.of("progress:read"),grants(db,"ROLE_CUSTOM_FIXTURE"));
    }
    @Test void softDeletedPermissionStaysHistoricalAndGetsASeparateActiveRow() {
        Database db=database("10");UUID historical=UUID.randomUUID();
        db.jdbc().update("INSERT INTO permissions(id,name,display_name,resource,deleted_at) VALUES(?,'practice:read','Historical display','practice',now())",historical);
        Map<String,List<String>> before=snapshot(db);db.upgrade();assertPreserved(db,before);assertMatrix(db);
        assertNotEquals(historical,db.jdbc().queryForObject("SELECT id FROM permissions WHERE name='practice:read' AND deleted_at IS NULL",UUID.class));
        assertEquals(1L,db.jdbc().queryForObject("SELECT count(*) FROM permissions WHERE id=? AND deleted_at IS NOT NULL",Long.class,historical));
    }
    @Test void inactiveBuiltInRoleReceivesNoNewGrantAndIsNotReactivated() {
        Database db=database("10");db.jdbc().update("UPDATE roles SET deleted_at=now() WHERE name='ROLE_TEACHER'");
        Map<String,List<String>> before=snapshot(db);db.upgrade();assertPreserved(db,before);
        assertEquals(List.of(),grants(db,"ROLE_TEACHER"));
        assertEquals(1L,db.jdbc().queryForObject("SELECT count(*) FROM roles WHERE name='ROLE_TEACHER' AND deleted_at IS NOT NULL",Long.class));
        assertEquals(5,grants(db,"ROLE_STUDENT").size());assertEquals(6,grants(db,"ROLE_ADMIN").size());
    }
    @Test void guestAndNewCustomRoleDoNotReceiveAutomaticLearningOrImportRights() {
        Database db=database("10");db.jdbc().update("INSERT INTO roles(name) VALUES('ROLE_CUSTOM_FIXTURE')");db.upgrade();assertMatrix(db);
        assertEquals(List.of(),grants(db,"ROLE_CUSTOM_FIXTURE"));
        assertFalse(grants(db,"ROLE_TEACHER").contains("practice:import"));assertFalse(grants(db,"ROLE_STUDENT").contains("practice:import"));
    }
}
