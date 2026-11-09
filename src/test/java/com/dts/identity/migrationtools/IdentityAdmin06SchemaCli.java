package com.dts.identity.migrationtools;

import java.io.PrintWriter;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.postgresql.ds.PGSimpleDataSource;

/** Explicit, guarded, no-Spring Identity V17 migration. Never repairs, baselines or cleans. */
public final class IdentityAdmin06SchemaCli {
    private static final List<String> READ=List.of("dashboard:read","audit:read","integrations:read","system-status:read");
    private static final List<String> CATALOG=List.of("dashboard:read","audit:read","integrations:read","integrations:retry","system-status:read");
    private static final Pattern FILE=Pattern.compile("V([0-9]+)__[A-Za-z0-9_]+\\.sql");
    private static String env(String key){String s=System.getenv(key);if(s==null||s.isBlank())throw new IllegalStateException("Missing "+key);return s;}
    private record Record(int version,String type,boolean ok,String script){}
    private record Plan(int current,boolean baseline){}
    private static class ReadOnlySource implements DataSource {
        private final DataSource source;
        ReadOnlySource(DataSource src){source=src;}
        private Connection readonly(Connection conn)throws SQLException {
            try {conn.setReadOnly(true);
                try(Statement statement=conn.createStatement()){statement.execute("SET default_transaction_read_only = on");}
                return conn;
            }catch(SQLException e){conn.close();throw e;}
        }
        public Connection getConnection()throws SQLException{return readonly(source.getConnection());}
        public Connection getConnection(String u,String p)throws SQLException{return readonly(source.getConnection(u,p));}
        public PrintWriter getLogWriter()throws SQLException{return source.getLogWriter();}
        public void setLogWriter(PrintWriter out)throws SQLException{source.setLogWriter(out);}
        public void setLoginTimeout(int sec)throws SQLException{source.setLoginTimeout(sec);}
        public int getLoginTimeout()throws SQLException{return source.getLoginTimeout();}
        public Logger getParentLogger(){return Logger.getLogger("dts.identity.adm06");}
        public <T>T unwrap(Class<T> type)throws SQLException{return source.unwrap(type);}
        public boolean isWrapperFor(Class<?> type)throws SQLException{return source.isWrapperFor(type);}
    }
    private static Plan history(DataSource source,String expectedDatabase,SortedMap<Integer,String> files)throws Exception {
        List<Record> rows=new ArrayList<>();
        try(Connection conn=source.getConnection()) {
            try(Statement stmt=conn.createStatement();ResultSet rs=stmt.executeQuery("SELECT current_database()")) {
                if(!rs.next() || !expectedDatabase.equals(rs.getString(1)))
                    throw new IllegalStateException("Wrong Identity database; migration refused");
            }
            try(Statement stmt=conn.createStatement();ResultSet rs=stmt.executeQuery(
                "SELECT version,type,success,script FROM public.flyway_schema_history ORDER BY installed_rank")) {
                while(rs.next()) {
                    String version=rs.getString(1);
                    if(version==null || !version.matches("[0-9]{1,2}"))
                        throw new IllegalStateException("Non-versioned or unexpected Flyway row");
                    rows.add(new Record(Integer.parseInt(version),rs.getString(2),rs.getBoolean(3),rs.getString(4)));
                }
            }
        }
        if(rows.size()<16 || rows.size()>17)throw new IllegalStateException("Expected Identity migration history V1-V16 or V17");
        boolean baseline=false;int current=rows.get(rows.size()-1).version();
        if(current!=16 && current!=17)throw new IllegalStateException("Identity schema must be V16 or already V17");
        for(int i=0;i<rows.size();i++) {
            Record r=rows.get(i);int expected=i+1;
            if(r.version()!=expected || !r.ok())throw new IllegalStateException("Incorrect Flyway order or failed migration at V"+expected);
            if(i==0 && "BASELINE".equals(r.type())){baseline=true;continue;}
            if(!"SQL".equals(r.type()) || !Objects.equals(files.get(expected),r.script()))
                throw new IllegalStateException("Unexpected installed migration V"+expected+"; no repair attempted");
        }
        return new Plan(current,baseline);
    }
    private static SortedMap<Integer,String> sources(Path folder)throws Exception {
        if(!Files.isDirectory(folder))throw new IllegalStateException("Reviewed migration directory missing");
        var found=new TreeMap<Integer,String>();
        try(var stream=Files.list(folder)) {
            for(Path p:stream.filter(Files::isRegularFile).toList()) {
                var match=FILE.matcher(p.getFileName().toString());
                if(!match.matches())throw new IllegalStateException("Unreviewed migration filename: "+p.getFileName());
                int number=Integer.parseInt(match.group(1));
                if(number<1 || number>17 || found.putIfAbsent(number,p.getFileName().toString())!=null)
                    throw new IllegalStateException("Duplicate or future migration V"+number);
            }
        }
        if(found.size()!=17 || !found.keySet().equals(new TreeSet<>(java.util.stream.IntStream.rangeClosed(1,17).boxed().toList())))
            throw new IllegalStateException("Complete reviewed V1-V17 migration files required");
        return found;
    }
    private static Flyway flyway(DataSource source,Path dir,boolean allowPending) {
        var setup=Flyway.configure().dataSource(source).locations("filesystem:"+dir.toAbsolutePath().toString().replace('\\','/'))
            .schemas("public").defaultSchema("public").createSchemas(false).baselineOnMigrate(false)
            .outOfOrder(false).cleanDisabled(true).validateOnMigrate(true).target("17");
        if(allowPending)setup.ignoreMigrationPatterns("*:pending");
        return setup.load();
    }
    private static void pending(Flyway flyway,int current) {
        MigrationInfo[] pending=flyway.info().pending();
        if(pending.length != (current==16?1:0))throw new IllegalStateException("Unexpected pending Flyway count: "+pending.length);
        if(current==16 && (pending[0].getVersion()==null || !"17".equals(pending[0].getVersion().getVersion())))
            throw new IllegalStateException("Only V17 may be pending");
    }
    private static long count(Connection connection,String sql)throws SQLException {
        try(Statement s=connection.createStatement();ResultSet r=s.executeQuery(sql)){if(!r.next())throw new SQLException("Missing count");return r.getLong(1);}
    }
    private static void permissionContract(DataSource ds,boolean required)throws Exception {
        try(Connection conn=ds.getConnection()) {
            for(String p:CATALOG) {
                try(PreparedStatement ps=conn.prepareStatement("SELECT resource FROM permissions WHERE deleted_at IS NULL AND name=?")){
                    ps.setString(1,p);
                    try(ResultSet r=ps.executeQuery()){
                        if(!r.next()){if(required)throw new IllegalStateException("Missing V17 right: "+p);continue;}
                        if(!required)throw new IllegalStateException("Untracked V17 grant already exists before migration: "+p);
                        if(!p.substring(0,p.indexOf(':')).equals(r.getString(1)) || r.next())
                            throw new IllegalStateException("Permission resource/conflict: "+p);
                    }
                }
                try(PreparedStatement ps=conn.prepareStatement("SELECT r.name FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions x ON x.id=rp.permission_id WHERE x.deleted_at IS NULL AND x.name=?")){
                    ps.setString(1,p);
                    try(ResultSet r=ps.executeQuery()){
                        while(r.next()) {
                            String role=r.getString(1);
                            if(!READ.contains(p) || !"ROLE_ADMIN".equals(role))
                                throw new IllegalStateException("Unexpected privileged role grant for "+p);
                        }
                    }
                }
            }
            if(required)for(String p:READ){
                try(PreparedStatement ps=conn.prepareStatement("SELECT COUNT(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions x ON x.id=rp.permission_id WHERE r.name='ROLE_ADMIN' AND r.deleted_at IS NULL AND x.deleted_at IS NULL AND x.name=?")){
                    ps.setString(1,p);try(ResultSet r=ps.executeQuery()){r.next();if(r.getLong(1)!=1)throw new IllegalStateException("Missing admin read grant: "+p);}
                }
            }
            if(count(conn,"SELECT COUNT(*) FROM permissions WHERE deleted_at IS NULL AND name IN ('reports:read','admin-audit:read')")!=2)
                throw new IllegalStateException("Legacy report/audit rights not present");
            if(count(conn,"SELECT COUNT(*) FROM roles WHERE deleted_at IS NULL AND name='ROLE_ADMIN'")!=1)
                throw new IllegalStateException("Active administrator role missing");
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1 || !Set.of("check","migrate","verify").contains(args[0]))
            throw new IllegalArgumentException("Use check, migrate or verify");
        String expected=env("DTS_IDENTITY_EXPECTED_DB");
        if(!expected.matches("[A-Za-z][A-Za-z0-9_-]{0,62}"))throw new IllegalStateException("Invalid database name");
        String url=env("DTS_IDENTITY_JDBC_URL");
        if(!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/"+Pattern.quote(expected)))
            throw new IllegalStateException("Only a validated localhost Identity database is permitted");
        Path directory=Path.of(env("DTS_IDENTITY_MIGRATION_DIR"));
        SortedMap<Integer,String> files=sources(directory);
        var db=new PGSimpleDataSource();db.setURL(url);
        db.setUser(env("DTS_IDENTITY_DB_USER"));db.setPassword(env("DTS_IDENTITY_DB_PASSWORD"));
        db.setConnectTimeout(15);db.setSocketTimeout(60);
        DataSource ro=new ReadOnlySource(db);
        Plan before=history(ro,expected,files);
        permissionContract(ro,before.current()==17);
        Flyway pre=flyway(ro,directory,true);
        pending(pre,before.current());pre.validate();
        if(args[0].equals("check")) {
            System.out.println("IDENTITY_ADM06_SCHEMA_PRECHECK=V"+before.current()+"; ONLY_PENDING_V17_ALLOWED=True; READ_ONLY=True");return;
        }
        if(args[0].equals("verify") && before.current()!=17)
            throw new IllegalStateException("V17 not applied");
        if(args[0].equals("migrate")) {
            var migrated=flyway(db,directory,true).migrate();
            if(migrated.migrationsExecuted!=(before.current()==16?1:0))
                throw new IllegalStateException("Unexpected migration count: "+migrated.migrationsExecuted);
        }
        Plan after=history(ro,expected,files);
        if(after.current()!=17)throw new IllegalStateException("V17 not installed");
        flyway(ro,directory,false).validate();
        permissionContract(ro,true);
        System.out.println("IDENTITY_ADM06_SCHEMA=V17; STRICT_VALIDATE=PASS; RETRY_NOT_GRANTED=True");
    }
}
