package com.dts.identity.migrationtools;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class IdentityPermissionMigrationToolPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @TempDir Path directory;
    String schema;
    PGSimpleDataSource source;
    JdbcTemplate jdbc;
    @BeforeEach void existingV10DatabaseAndSourceSnapshot() throws Exception {
        Path resources=new ClassPathResource("db/migration").getFile().toPath();
        try(var paths=Files.list(resources)) {
            // The original tool intentionally supports only V1-V11. Test that contract
            // against its historical source snapshot, including future-version refusal.
            for(Path file:paths.filter(p->p.getFileName().toString().matches("V(?:[1-9]|10|11)__.*\\.sql")).toList())
                Files.copy(file,directory.resolve(file.getFileName()));
        }
        schema="t_"+UUID.randomUUID().toString().replace("-","");
        source=new PGSimpleDataSource();source.setURL(postgres.getJdbcUrl()+(postgres.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema);
        source.setUser(postgres.getUsername());source.setPassword(postgres.getPassword());
        Flyway.configure().dataSource(source).locations("filesystem:"+directory).schemas(schema).defaultSchema(schema).target("10").load().migrate();
        jdbc=new JdbcTemplate(source);
    }
    Map<String,List<String>> snapshot() {
        Map<String,List<String>> result=new TreeMap<>();
        for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname=?",String.class,schema)) {
            if(!table.matches("[a-z_]+"))throw new IllegalStateException("Unexpected test table");
            result.put(table,jdbc.queryForList("SELECT to_jsonb(t)::text FROM "+table+" t ORDER BY to_jsonb(t)::text",String.class));
        }
        return result;
    }
    Map<String,Object> run(String mode)throws Exception {
        return IdentityPermissionMigrationTool.execute(mode,postgres.getDatabaseName(),schema,directory,source);
    }
    @Test void checkIsReadOnlyAndAllowsOnlyTheExpectedPendingV11() throws Exception {
        Map<String,List<String>> before=snapshot();Map<String,Object> result=run("check");
        assertEquals(true,result.get("Passed"));assertEquals(false,result.get("DatabaseMigrationRequested"));
        assertEquals(false,result.get("Version11AppliedThisRun"));assertEquals(true,result.get("SourceChecksumsValidated"));
        assertEquals(before,snapshot());assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='11'",Long.class));
    }
    @Test void migrateAppliesV11AndASecondCallMakesNoDataOrHistoryChanges() throws Exception {
        Map<String,Object> first=run("migrate");assertEquals(true,first.get("Version11AppliedThisRun"));
        assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='11' AND success",Long.class));
        assertEquals(6L,jdbc.queryForObject("SELECT count(*) FROM permissions WHERE resource IN ('progress','practice') AND deleted_at IS NULL",Long.class));
        Map<String,List<String>> before=snapshot();Map<String,Object> second=run("migrate");
        assertEquals(false,second.get("Version11AppliedThisRun"));assertEquals(before,snapshot());
        assertEquals(false,second.get("SpringApplicationStarted"));
    }
    @Test void readOnlyDatasourceRejectsWritesAtThePostgreSqlServer() throws Exception {
        Map<String,List<String>> before=snapshot();
        try(Connection connection=new IdentityPermissionMigrationTool.ReadOnlySource(source).getConnection();Statement statement=connection.createStatement()) {
            SQLException failure=assertThrows(SQLException.class,()->statement.execute("CREATE TABLE must_not_be_created(id integer)"));
            assertEquals("25006",failure.getSQLState());
        }
        assertEquals(before,snapshot());assertNull(jdbc.queryForObject("SELECT to_regclass(?)::text",String.class,schema+".must_not_be_created"));
    }
    @Test void wrongDatabaseNameIsRejectedBeforeMigration() {
        Map<String,List<String>> before=snapshot();
        assertThrows(IllegalStateException.class,()->IdentityPermissionMigrationTool.execute("migrate","wrong_database_fixture",schema,directory,source));
        assertEquals(before,snapshot());
    }
    @Test void unknownFutureHistoryVersionIsRejectedWithoutRepairingOrDeletingIt() {
        jdbc.update("INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) VALUES(999,'12','fixture future','SQL','V12__fixture.sql',0,'fixture',0,TRUE)");
        Map<String,List<String>> before=snapshot();assertThrows(IllegalStateException.class,()->run("migrate"));assertEquals(before,snapshot());
    }
    @Test void oldSourceChecksumMismatchFailsReadOnlyValidationWithoutDatabaseChanges() throws Exception {
        Path old=directory.resolve("V8__add_chapter_permissions.sql");String text=Files.readString(old);
        assertTrue(text.contains("Xem chương học"));Files.writeString(old,text.replace("Xem chương học","Changed fixture display"));
        Map<String,List<String>> before=snapshot();assertThrows(Exception.class,()->run("migrate"));assertEquals(before,snapshot());
    }
    @Test void existingV11WithAnAdministrativeRevocationIsNotSilentlyReseeded() throws Exception {
        run("migrate");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE name='ROLE_STUDENT') AND permission_id=(SELECT id FROM permissions WHERE name='practice:learn' AND deleted_at IS NULL)");
        Map<String,List<String>> before=snapshot();assertThrows(IllegalStateException.class,()->run("migrate"));assertEquals(before,snapshot());
    }
    @Test void missingSchemaHistoryIsRejectedWithoutCreatingOrBaseliningIt() {
        String missing="missing_"+UUID.randomUUID().toString().replace("-","");
        Map<String,List<String>> before=snapshot();
        assertThrows(IllegalStateException.class,()->IdentityPermissionMigrationTool.execute("migrate",postgres.getDatabaseName(),missing,directory,source));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?",Long.class,missing));assertEquals(before,snapshot());
    }
}
