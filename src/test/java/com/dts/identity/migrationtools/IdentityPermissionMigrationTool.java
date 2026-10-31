package com.dts.identity.migrationtools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;

/** Explicit V10-to-V11 upgrade tool. No Spring context, ORM, workers or repair. */
public class IdentityPermissionMigrationTool {
    static final List<String> PERSONAL=List.of("progress:read","progress:update","practice:read","practice:learn","practice:exam");
    static final List<String> ALL=List.of("progress:read","progress:update","practice:read","practice:learn","practice:exam","practice:import");
    static final String NAMES="'progress:read','progress:update','practice:read','practice:learn','practice:exam','practice:import'";
    record State(int version,List<Integer> versions){}

    static String quote(String name) {
        if(!name.matches("[A-Za-z_][A-Za-z0-9_]{0,62}"))throw new IllegalArgumentException("Invalid schema identifier");
        return "\""+name+"\"";
    }
    static PGSimpleDataSource source() {
        String url=System.getenv("DTS_IDENTITY_JDBC_URL"),user=System.getenv("DTS_IDENTITY_DB_USER"),password=System.getenv("DTS_IDENTITY_DB_PASSWORD");
        if(url==null || !url.startsWith("jdbc:postgresql://") || url.chars().anyMatch(c->c<32))
            throw new IllegalArgumentException("Use a PostgreSQL JDBC URL with explicit host/database and no embedded credentials");
        URI uri=URI.create(url.substring("jdbc:".length()));
        if(uri.getRawUserInfo()!=null)throw new IllegalArgumentException("Keep credentials out of the JDBC URL");
        if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")) {
            String key=URLDecoder.decode(part.split("=",2)[0],StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if(Set.of("user","password","sslpassword").contains(key))throw new IllegalArgumentException("Keep credentials out of the JDBC URL");
        }
        if(user==null || user.isBlank() || password==null)throw new IllegalArgumentException("Database credentials are missing");
        PGSimpleDataSource result=new PGSimpleDataSource();result.setURL(url);result.setUser(user);result.setPassword(password);
        result.setConnectTimeout(15);result.setSocketTimeout(60);return result;
    }
    /** Server-side read-only default covers implicit transactions as well. */
    static class ReadOnlySource implements DataSource {
        final DataSource delegate;
        ReadOnlySource(DataSource delegate){this.delegate=delegate;}
        Connection prepare(Connection connection)throws SQLException {
            try {
                connection.setReadOnly(true);
                try(Statement statement=connection.createStatement()){statement.execute("SET default_transaction_read_only = on");}
                return connection;
            }catch(SQLException error){connection.close();throw error;}
        }
        public Connection getConnection()throws SQLException{return prepare(delegate.getConnection());}
        public Connection getConnection(String user,String password)throws SQLException{return prepare(delegate.getConnection(user,password));}
        public PrintWriter getLogWriter()throws SQLException{return delegate.getLogWriter();}
        public void setLogWriter(PrintWriter writer)throws SQLException{delegate.setLogWriter(writer);}
        public void setLoginTimeout(int seconds)throws SQLException{delegate.setLoginTimeout(seconds);}
        public int getLoginTimeout()throws SQLException{return delegate.getLoginTimeout();}
        public Logger getParentLogger(){return Logger.getLogger("identity.permissions.migration");}
        public <T>T unwrap(Class<T> type)throws SQLException{return delegate.unwrap(type);}
        public boolean isWrapperFor(Class<?> type)throws SQLException{return delegate.isWrapperFor(type);}
    }
    static SortedMap<Integer,String> sourceFiles(Path directory)throws Exception {
        SortedMap<Integer,String> files=new TreeMap<>();
        try(var entries=Files.list(directory)) {
            for(Path path:entries.filter(Files::isRegularFile).toList()) {
                Matcher match=Pattern.compile("V([0-9]+)__.+\\.sql").matcher(path.getFileName().toString());
                if(!match.matches())throw new IllegalStateException("Unreviewed migration file in snapshot");
                int version=Integer.parseInt(match.group(1));
                if(files.put(version,path.getFileName().toString())!=null)throw new IllegalStateException("Duplicate migration version");
            }
        }
        if(files.size()!=11 || files.firstKey()!=1 || files.lastKey()!=11)
            throw new IllegalStateException("Expected the reviewed V1-V11 migration files");
        return files;
    }
    static State history(DataSource readonly,String expectedDatabase,String schema,SortedMap<Integer,String> files)throws Exception {
        try(Connection connection=readonly.getConnection()) {
            try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery("SELECT current_database()")) {
                result.next();if(!expectedDatabase.equals(result.getString(1)))throw new IllegalStateException("Connected database does not match --expected-database");
            }
            String table=quote(schema)+".\"flyway_schema_history\"";
            try(PreparedStatement statement=connection.prepareStatement("SELECT to_regclass(?)")) {
                statement.setString(1,table);try(ResultSet result=statement.executeQuery()) {
                    result.next();if(result.getString(1)==null)throw new IllegalStateException("No Flyway history in the selected schema; initialization/baseline requires separate review");
                }
            }
            SortedSet<Integer> versions=new TreeSet<>();
            try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery("SELECT version,type,script,success FROM "+table+" ORDER BY installed_rank")) {
                while(result.next()) {
                    String version=result.getString("version"),type=result.getString("type");
                    if(!result.getBoolean("success"))throw new IllegalStateException("Flyway history contains a failed migration");
                    if(version==null && "SCHEMA".equals(type))continue;
                    if(version==null || !version.matches("[0-9]+") || !"SQL".equals(type))
                        throw new IllegalStateException("Unexpected baseline/repeatable/non-SQL migration; review history before this upgrade");
                    int number=Integer.parseInt(version);
                    if(number<1 || number>11 || !versions.add(number) || !Objects.equals(files.get(number),result.getString("script")))
                        throw new IllegalStateException("Database migration version/script differs from the reviewed Identity branch");
                }
            }
            if(versions.isEmpty() || (versions.last()!=10 && versions.last()!=11) || versions.size()!=versions.last())
                throw new IllegalStateException("This upgrade requires complete SQL history V1-V10, or the same V11 already applied");
            return new State(versions.last(),List.copyOf(versions));
        }
    }
    static Map<String,Object> rights(DataSource readonly,String schema,boolean requireAll)throws Exception {
        String prefix=quote(schema)+".";Set<String> catalog=new TreeSet<>();Map<String,List<String>> grants=new TreeMap<>();
        try(Connection connection=readonly.getConnection()) {
            try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery("SELECT name,resource FROM "+prefix+"permissions WHERE deleted_at IS NULL AND name IN ("+NAMES+")")) {
                while(result.next()) {
                    String name=result.getString("name");
                    if(!name.substring(0,name.indexOf(':')).equals(result.getString("resource")))
                        throw new IllegalStateException("Existing permission has an unexpected resource: "+name);
                    catalog.add(name);
                }
            }
            if(requireAll && !catalog.equals(new TreeSet<>(ALL)))throw new IllegalStateException("V11 history exists but the six active permissions are incomplete; no manual SQL repair was performed");
            try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery("SELECT name FROM "+prefix+"roles WHERE deleted_at IS NULL AND name IN ('ROLE_ADMIN','ROLE_TEACHER','ROLE_STUDENT') ORDER BY name")) {
                while(result.next())grants.put(result.getString(1),new ArrayList<>());
            }
            try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery("SELECT r.name AS role_name,p.name AS permission_name FROM "+prefix+"roles r JOIN "+prefix+"role_permissions rp ON rp.role_id=r.id JOIN "+prefix+"permissions p ON p.id=rp.permission_id WHERE r.deleted_at IS NULL AND p.deleted_at IS NULL AND r.name IN ('ROLE_ADMIN','ROLE_TEACHER','ROLE_STUDENT') AND p.name IN ("+NAMES+") ORDER BY r.name,p.name")) {
                while(result.next())grants.get(result.getString("role_name")).add(result.getString("permission_name"));
            }
            if(requireAll)for(var role:grants.entrySet()) {
                List<String> needed=role.getKey().equals("ROLE_ADMIN")?ALL:PERSONAL;
                if(!role.getValue().containsAll(needed))throw new IllegalStateException("An active role lacks required V11 grants: "+role.getKey()+"; existing administrative changes were not overwritten");
            }
        }
        Map<String,Object> output=new LinkedHashMap<>();output.put("ActivePermissionNames",catalog);output.put("ActiveBuiltInRoleGrants",grants);
        output.put("RequiredGrantsVerified",requireAll);return output;
    }
    static Flyway flyway(DataSource source,Path directory,String schema) {
        return Flyway.configure().dataSource(source).locations("filesystem:"+directory.toAbsolutePath())
                .schemas(schema).defaultSchema(schema).createSchemas(false).baselineOnMigrate(false)
                .validateOnMigrate(true).outOfOrder(false).cleanDisabled(true).target("11")
                .ignoreMigrationPatterns("*:pending").load();
    }
    static Map<String,Object> execute(String mode,String expectedDatabase,String schema,Path directory,PGSimpleDataSource writable)throws Exception {
            if(!Set.of("check","migrate").contains(mode))throw new IllegalArgumentException("Expected check or migrate");
            quote(schema);
            SortedMap<Integer,String> files=sourceFiles(directory);
            DataSource readonly=new ReadOnlySource(writable);
            State before=history(readonly,expectedDatabase,schema,files);
            Map<String,Object> beforeRights=rights(readonly,schema,before.version()==11);
            // Read-only datasource enforces no schema/data writes during validation.
            flyway(readonly,directory,schema).validate();
            if(mode.equals("migrate"))flyway(writable,directory,schema).migrate();
            State after=history(readonly,expectedDatabase,schema,files);
            Map<String,Object> afterRights=mode.equals("migrate")?rights(readonly,schema,true):beforeRights;
            Map<String,Object> output=new LinkedHashMap<>();output.put("Mode",mode);output.put("Passed",true);
            output.put("DatabaseName",expectedDatabase);output.put("Schema",schema);output.put("HistoryVersionsBefore",before.versions());
            output.put("HistoryVersionsAfter",after.versions());output.put("SourceChecksumsValidated",true);
            output.put("Version11AppliedThisRun",mode.equals("migrate") && before.version()==10 && after.version()==11);
            output.put("DatabaseMigrationRequested",mode.equals("migrate"));output.put("CheckConnectionReadOnly",true);
            output.put("PermissionContract",afterRights);output.put("SpringApplicationStarted",false);
            output.put("RuntimeJwtAcceptanceCompleted",false);output.put("ServicesRestarted",false);
            return output;
    }
    public static void main(String[] args) {
        String mode=args.length>0?args[0]:"unknown";
        try {
            if(args.length!=4)throw new IllegalArgumentException("Expected mode, database name, schema and reviewed migration snapshot directory");
            Map<String,Object> output=execute(mode,args[1],args[2],Path.of(args[3]),source());
            System.out.println("IDENTITY_PERMISSION_RESULT:"+new ObjectMapper().writeValueAsString(output));
        }catch(Exception error) {
            System.err.println("IDENTITY_PERMISSION_ERROR: "+error.getClass().getSimpleName()+": "+error.getMessage());
            System.exit(1);
        }
    }
}
