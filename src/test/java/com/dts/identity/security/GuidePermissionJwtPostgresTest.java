package com.dts.identity.security;

import com.dts.identity.config.JwtProperties;
import com.dts.identity.config.SecurityProperties;
import com.dts.identity.dto.request.LoginRequest;
import com.dts.identity.dto.request.RefreshTokenRequest;
import com.dts.identity.dto.response.AuthResponse;
import com.dts.identity.entity.*;
import com.dts.identity.exception.BusinessException;
import com.dts.identity.repository.*;
import com.dts.identity.service.*;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=GuidePermissionJwtPostgresTest.Config.class)
@Import({AuthService.class,UserService.class,JwtProvider.class})
@Testcontainers
class GuidePermissionJwtPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    static final AtomicInteger NEXT=new AtomicInteger(10000000);
    static final String PASSWORD="Fixture-password-only-123";
    static final Set<String> PERSONAL=Set.of();
    static final Set<String> ALL=GuidePermissionMigrationPostgresTest.ALL;
    @Configuration(proxyBeanMethods=false)
    @EntityScan(basePackageClasses=User.class)
    @EnableJpaRepositories(basePackageClasses=UserRepository.class)
    static class Config {
        @Bean JwtProperties jwtProperties() {
            String access=Base64.getEncoder().encodeToString("identity-permission-access-fixture-only-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
            String refresh=Base64.getEncoder().encodeToString("identity-permission-refresh-fixture-only-012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
            return new JwtProperties(new JwtProperties.TokenProperties(access,300000),
                    new JwtProperties.TokenProperties(refresh,600000),"identity-permission-fixture");
        }
        @Bean SecurityProperties securityProperties() {
            return new SecurityProperties(new SecurityProperties.BcryptProperties(4),
                    new SecurityProperties.BruteForceProperties(5,15),new SecurityProperties.VerificationCodeProperties(10,5));
        }
        @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(4);}
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        registry.add("spring.jpa.properties.hibernate.default_schema",()->"public");
        registry.add("spring.flyway.enabled",()->"true");
        registry.add("spring.flyway.url",postgres::getJdbcUrl);
        registry.add("spring.flyway.user",postgres::getUsername);
        registry.add("spring.flyway.password",postgres::getPassword);
        registry.add("spring.flyway.schemas",()->"public");
        registry.add("spring.flyway.default-schema",()->"public");
        registry.add("spring.flyway.target",()->"13");
        registry.add("spring.flyway.baseline-on-migrate",()->"false");
        registry.add("spring.flyway.validate-on-migrate",()->"true");
        registry.add("spring.flyway.out-of-order",()->"false");
        registry.add("spring.flyway.locations",()->"classpath:db/migration");
    }
    @Autowired AuthService auth;
    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired UserRoleRepository memberships;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtProvider jwt;
    @Autowired EntityManager entities;
    @MockBean EmailService email;
    @MockBean OutboxPublisher outbox;

    User fixture(String...roleNames) {
        int sequence=NEXT.incrementAndGet();
        User user=userRepository.saveAndFlush(User.builder().username("jwt_fixture_"+sequence)
                .email("jwt"+sequence+"@example.invalid").password(encoder.encode(PASSWORD))
                .fullName("JWT fixture").birthOfDate(LocalDate.of(2000,1,1)).phoneNumber("09"+sequence)
                .status(User.UserStatus.ACTIVE).emailVerifiedAt(Instant.now()).selectedLicenseClass("B").build());
        for(String name:roleNames) {
            Role role=roles.findByName(name).orElseThrow();
            memberships.saveAndFlush(UserRole.builder().userId(user.getId()).roleId(role.getId()).user(user).role(role).build());
        }
        entities.clear();return user;
    }
    AuthResponse login(User user){return auth.login(new LoginRequest(user.getUsername(),PASSWORD,"permission-fixture-only"));}
    Set<String> newRights(AuthResponse response){return response.user().permissions().stream().filter(ALL::contains).collect(Collectors.toSet());}
    void assertContext(User user,AuthResponse response) {
        Claims claims=jwt.validateAccessToken(response.accessToken());
        assertEquals(user.getId(),jwt.getUserId(claims));
        assertEquals(new HashSet<>(response.user().roles()),new HashSet<>(jwt.getRoles(claims)));
        assertEquals(new HashSet<>(response.user().permissions()),new HashSet<>(jwt.getPermissions(claims)));
        assertEquals(response.user().permissions().size(),new HashSet<>(response.user().permissions()).size());
        assertTrue(response.user().permissions().stream().noneMatch(name->name.startsWith("PERM_")));
        assertEquals("B",response.user().selectedLicenseClass());assertNull(claims.get("selectedLicenseClass"));
        Set<String> databaseRights=users.getUserRoles(user.getId()).stream().flatMap(role->role.permissions().stream()).collect(Collectors.toSet());
        assertEquals(databaseRights,new HashSet<>(jwt.getPermissions(claims)));
    }
    @AfterEach void noExternalMailOrEvents(){verifyNoInteractions(email,outbox);}

    @ParameterizedTest @ValueSource(strings={"ROLE_STUDENT","ROLE_TEACHER","ROLE_ADMIN"})
    void loginUsesMigratedDatabaseGrantsInRealSignedJwtAndUserContext(String role) {
        User user=fixture(role);AuthResponse response=login(user);assertContext(user,response);
        assertEquals(role.equals("ROLE_ADMIN")?ALL:PERSONAL,newRights(response));
        assertTrue(response.user().permissions().containsAll(List.of("questions:read","exams:read","progress:read","progress:update","practice:read","practice:learn","practice:exam")));
        if(role.equals("ROLE_ADMIN"))assertTrue(response.user().permissions().containsAll(List.of("chapters:read","chapters:create","chapters:update","exams:update","practice:import")));
    }
    @Test void guestKeepsLegacyRightsWithoutGuideAdministration() {
        User user=fixture("ROLE_GUEST");AuthResponse response=login(user);assertContext(user,response);
        assertTrue(response.user().permissions().contains("questions:read"));assertEquals(Set.of(),newRights(response));
    }
    @Test void multipleRolesDoNotDuplicatePermissionClaimsOrEscalateToContentAdministration() {
        User user=fixture("ROLE_STUDENT","ROLE_TEACHER");AuthResponse response=login(user);assertContext(user,response);
        assertEquals(PERSONAL,newRights(response));assertFalse(response.user().permissions().contains("simulation-scenarios:publish"));
    }
    @Test void customRoleOnlyReceivesNewRightWhenExplicitlyAssignedThroughExistingService() {
        Role custom=roles.saveAndFlush(Role.builder().name("ROLE_JWT_FIXTURE").build());
        User user=fixture(custom.getName());AuthResponse before=login(user);assertEquals(Set.of(),newRights(before));
        Permission guideUpdate=permissions.findByName("simulation-guides:update").orElseThrow();
        users.assignPermissionToRole(custom.getId(),guideUpdate.getId());entities.flush();entities.clear();
        AuthResponse after=login(user);assertContext(user,after);assertEquals(Set.of("simulation-guides:update"),newRights(after));
    }
    @Test void refreshLoadsCurrentGrantsAndRotatesRefreshTokenWhileOldAccessTokenKeepsItsSnapshot() {
        Role admin=roles.findByName("ROLE_ADMIN").orElseThrow();
        List<UUID> rights=ALL.stream().map(name->permissions.findByName(name).orElseThrow().getId()).toList();
        rights.forEach(permission->users.revokePermissionFromRole(admin.getId(),permission));entities.flush();entities.clear();
        User user=fixture("ROLE_ADMIN");AuthResponse before=login(user);entities.flush();
        assertEquals(Set.of(),newRights(before));
        rights.forEach(permission->users.assignPermissionToRole(admin.getId(),permission));entities.flush();entities.clear();
        AuthResponse refreshed=auth.refreshToken(new RefreshTokenRequest(before.refreshToken()));
        assertContext(user,refreshed);assertEquals(ALL,newRights(refreshed));
        assertNotEquals(before.refreshToken(),refreshed.refreshToken());
        assertTrue(Collections.disjoint(ALL,jwt.getPermissions(jwt.validateAccessToken(before.accessToken()))));
        BusinessException failure=assertThrows(BusinessException.class,()->auth.refreshToken(new RefreshTokenRequest(before.refreshToken())));
        assertEquals(HttpStatus.UNAUTHORIZED,failure.getStatus());
    }
}
