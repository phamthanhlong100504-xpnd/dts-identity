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
class ExamPermissionMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    static final Set<String> ALL=Set.of("simulations:exam");
    static final String MIGRATION="db/migration/V14__add_simulation_exam_permission.sql";
    record Database(String schema,String url,JdbcTemplate jdbc) {
        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url,postgres.getUsername(),postgres.getPassword())
                    .locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target(target)
                    .baselineOnMigrate(false).validateOnMigrate(true).outOfOrder(false).cleanDisabled(true).load();
        }
        void upgrade(){flyway("14").migrate();flyway("14").validate();}
    }
    Database database(String target) {
        String schema="simexam_"+UUID.randomUUID().toString().replace("-","");
        String url=postgres.getJdbcUrl()+(postgres.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema;
        var d=new Database(schema,url,new JdbcTemplate(new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword())));
        d.flyway(target).migrate();return d;
    }
    Set<String> grants(Database d,String role) {
        var result=new HashSet<>(d.jdbc().queryForList("""
                SELECT p.name FROM role_permissions rp JOIN roles r ON r.id=rp.role_id
                JOIN permissions p ON p.id=rp.permission_id
                WHERE r.name=? AND r.deleted_at IS NULL AND p.deleted_at IS NULL
                """,String.class,role));result.retainAll(ALL);return result;
    }
    void matrix(Database d) {
        for(String role:List.of("ROLE_ADMIN","ROLE_STUDENT","ROLE_TEACHER"))assertEquals(ALL,grants(d,role),role);
        assertEquals(Set.of(),grants(d,"ROLE_GUEST"));
        assertEquals(1,d.jdbc().queryForList("SELECT name FROM permissions WHERE deleted_at IS NULL",String.class).stream().filter(ALL::contains).count());
    }
    Map<String,List<String>> snapshot(Database d) {
        var rows=new TreeMap<String,List<String>>();
        for(String table:d.jdbc().queryForList("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename",String.class,d.schema())) {
            if(!table.matches("[a-z_]+"))throw new IllegalStateException("Unexpected fixture table");
            rows.put(table,d.jdbc().queryForList("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text",String.class));
        }
        return rows;
    }
    void preserved(Database d,Map<String,List<String>> before) {
        var after=snapshot(d);assertEquals(before.keySet(),after.keySet());
        before.forEach((table,rows)->{
            if(Set.of("permissions","role_permissions","flyway_schema_history").contains(table))assertTrue(after.get(table).containsAll(rows),table);
            else assertEquals(rows,after.get(table),table);
        });
    }
    @Test void upgradeV13AddsOneRightAndThreeGrantsWithoutChangingExistingData() {
        var d=database("13");UUID user=UUID.randomUUID();
        d.jdbc().update("INSERT INTO users(id,username,email,password,full_name,birth_of_date,phone_number,status,selected_license_class) VALUES(?,?,'simexam-fixture@example.invalid','fixture-hash','Exam fixture','2000-01-01','0900000000','ACTIVE','B')",user,"exam_"+user.toString().substring(0,8));
        d.jdbc().update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_STUDENT'",user);
        d.jdbc().update("INSERT INTO refresh_tokens(user_id,token_hash,device_info,expires_at) VALUES(?,'simexam-fixture','fixture',now()+interval '1 day')",user);
        var before=snapshot(d);long rights=d.jdbc().queryForObject("SELECT count(*) FROM permissions",Long.class),maps=d.jdbc().queryForObject("SELECT count(*) FROM role_permissions",Long.class);
        d.upgrade();matrix(d);preserved(d,before);
        assertEquals(rights+1,d.jdbc().queryForObject("SELECT count(*) FROM permissions",Long.class));
        assertEquals(maps+3,d.jdbc().queryForObject("SELECT count(*) FROM role_permissions",Long.class));
    }
    @Test void freshChainReachesV14WithIndependentLearningTheoryAndGuideGrants() {
        var d=database("14");matrix(d);
        assertEquals(14L,d.jdbc().queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success",Long.class));
        for(String role:List.of("ROLE_ADMIN","ROLE_TEACHER","ROLE_STUDENT"))
            for(String name:List.of("simulations:read","simulations:learn","media:read","practice:exam"))
                assertTrue(d.jdbc().queryForObject("SELECT EXISTS(SELECT 1 FROM roles r JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id WHERE r.name=? AND p.name=? AND p.deleted_at IS NULL)",Boolean.class,role,name));
        assertEquals(3L,d.jdbc().queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.name='ROLE_ADMIN' AND p.name LIKE 'simulation-guides:%' AND p.deleted_at IS NULL",Long.class));
        assertEquals(0L,d.jdbc().queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.name='ROLE_STUDENT' AND p.name LIKE 'simulation-guides:%'",Long.class));
    }
    @Test void repeatedFlywayUpgradePreservesRowsAndHistory() {
        var d=database("14");var before=snapshot(d);d.upgrade();assertEquals(before,snapshot(d));
    }
    @Test void replayingSqlInOwnedDatabaseIsIdempotent()throws Exception {
        var d=database("14");var before=snapshot(d);
        try(Connection c=new DriverManagerDataSource(d.url(),postgres.getUsername(),postgres.getPassword()).getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource(MIGRATION));}
        assertEquals(before,snapshot(d));
    }
    @Test void existingActivePermissionIdMetadataAndManualCustomGrantArePreserved() {
        var d=database("13");UUID permission=UUID.randomUUID(),role=UUID.randomUUID();
        d.jdbc().update("INSERT INTO permissions(id,name,display_name,resource,metadata) VALUES(?,'simulations:exam','Existing exam right','simulations','{\"keep\":true}'::jsonb)",permission);
        d.jdbc().update("INSERT INTO roles(id,name) VALUES(?,'ROLE_EXAM_CUSTOM')",role);
        d.jdbc().update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)",role,permission);
        var before=snapshot(d);d.upgrade();matrix(d);preserved(d,before);
        assertEquals(permission,d.jdbc().queryForObject("SELECT id FROM permissions WHERE name='simulations:exam' AND deleted_at IS NULL",UUID.class));
        assertEquals(ALL,grants(d,"ROLE_EXAM_CUSTOM"));
    }
    @Test void softDeletedPermissionGetsNewActiveRowWithoutResurrectingHistory() {
        var d=database("13");UUID old=UUID.randomUUID();
        d.jdbc().update("INSERT INTO permissions(id,name,display_name,resource,deleted_at) VALUES(?,'simulations:exam','Historical exam right','simulations',now())",old);
        var before=snapshot(d);d.upgrade();matrix(d);preserved(d,before);
        assertNotEquals(old,d.jdbc().queryForObject("SELECT id FROM permissions WHERE name='simulations:exam' AND deleted_at IS NULL",UUID.class));
    }
    @Test void inactiveAdminIsNotReactivatedOrGranted() {
        var d=database("13");d.jdbc().update("UPDATE roles SET deleted_at=now() WHERE name='ROLE_ADMIN'");
        var before=snapshot(d);d.upgrade();preserved(d,before);
        assertEquals(Set.of(),grants(d,"ROLE_ADMIN"));assertEquals(ALL,grants(d,"ROLE_STUDENT"));assertEquals(ALL,grants(d,"ROLE_TEACHER"));
        assertEquals(1L,d.jdbc().queryForObject("SELECT count(*) FROM roles WHERE name='ROLE_ADMIN' AND deleted_at IS NOT NULL",Long.class));
    }
    @Test void inactiveStudentAndTeacherAreNotReactivatedOrGranted() {
        var d=database("13");d.jdbc().update("UPDATE roles SET deleted_at=now() WHERE name IN ('ROLE_STUDENT','ROLE_TEACHER')");
        var before=snapshot(d);d.upgrade();preserved(d,before);
        assertEquals(Set.of(),grants(d,"ROLE_STUDENT"));assertEquals(Set.of(),grants(d,"ROLE_TEACHER"));assertEquals(ALL,grants(d,"ROLE_ADMIN"));
    }
    @Test void guestAndUnassignedCustomRolesReceiveNoAutomaticExamGrant() {
        var d=database("13");d.jdbc().update("INSERT INTO roles(name) VALUES('ROLE_EXAM_CUSTOM')");d.upgrade();matrix(d);
        assertEquals(Set.of(),grants(d,"ROLE_EXAM_CUSTOM"));
    }
    @Test void repeatMigrationDoesNotRestoreAdministrativelyRevokedGrant() {
        var d=database("14");d.jdbc().update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE name='ROLE_STUDENT') AND permission_id=(SELECT id FROM permissions WHERE name='simulations:exam' AND deleted_at IS NULL)");
        var before=snapshot(d);d.upgrade();assertEquals(before,snapshot(d));assertEquals(Set.of(),grants(d,"ROLE_STUDENT"));
    }
}
