# Auth Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a complete ERP authentication and authorization loop with DB-backed login, short-lived JWT access tokens, Flyway-seeded administrator data, and `sys_menu.permission` based endpoint authorization.

**Architecture:** Spring Security remains the single security boundary. `UserDetailsService` loads active users and permissions from existing system tables, a stateless JWT filter restores authenticated principals for Bearer requests, and method security enforces permission codes on controllers. Existing MockMvc tests keep working through a test-only `@WithMockErpAdmin` annotation that grants the permissions production `SUPER_ADMIN` receives through seed data.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring Security 6, MyBatis-Plus, Flyway, H2/MySQL, JUnit 5, MockMvc, Jackson, JDK `javax.crypto.Mac` for HMAC-SHA256 JWT signing.

---

## File Structure

Create:

- `src/main/java/com/tuowei/erp/common/config/SecurityProperties.java` for JWT configuration.
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java` for permission code and expression constants.
- `src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java` for authenticated user details.
- `src/main/java/com/tuowei/erp/common/security/CurrentUser.java` for immutable current-user snapshot.
- `src/main/java/com/tuowei/erp/common/security/CurrentUserContext.java` for reading the current user from `SecurityContext`.
- `src/main/java/com/tuowei/erp/common/security/UserPermissionService.java` for loading authorities from `sys_user_role`, `sys_role_menu`, and `sys_menu`.
- `src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java` for Spring Security DB authentication.
- `src/main/java/com/tuowei/erp/common/security/JwtTokenService.java` for JWT creation and validation.
- `src/main/java/com/tuowei/erp/common/security/JwtAuthenticationFilter.java` for Bearer token authentication.
- `src/main/java/com/tuowei/erp/system/auth/controller/AuthController.java` for `POST /api/auth/login`.
- `src/main/java/com/tuowei/erp/system/auth/service/AuthService.java` for login orchestration.
- `src/main/java/com/tuowei/erp/system/auth/web/LoginRequest.java`.
- `src/main/java/com/tuowei/erp/system/auth/web/LoginResponse.java`.
- `src/main/java/com/tuowei/erp/system/auth/web/LoginUserResponse.java`.
- `src/main/resources/db/migration/V16__auth_seed_data.sql` for admin, role, permissions, and bindings.
- `src/test/java/com/tuowei/erp/testsupport/WithMockErpAdmin.java` for existing controller tests.
- `src/test/java/com/tuowei/erp/common/security/JwtTokenServiceTest.java`.
- `src/test/java/com/tuowei/erp/common/security/UserPermissionServiceTest.java`.
- `src/test/java/com/tuowei/erp/system/auth/AuthControllerLoginTest.java`.
- `src/test/java/com/tuowei/erp/system/auth/AuthAuthorizationIntegrationTest.java`.
- `src/test/java/com/tuowei/erp/system/auth/AuthSeedMigrationTest.java`.

Modify:

- `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java` to enable stateless JWT security and method security.
- `src/main/resources/application.yml` to add default JWT config.
- Existing controllers to add `@PreAuthorize(...)` where endpoint permissions are defined.
- Existing tests using `@WithMockUser(username = "admin")` to use `@WithMockErpAdmin`.
- `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java` to assert Bearer-token behavior instead of HTTP Basic.

---

### Task 1: JWT Configuration And Token Service

**Files:**

- Create: `src/main/java/com/tuowei/erp/common/config/SecurityProperties.java`
- Create: `src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java`
- Create: `src/main/java/com/tuowei/erp/common/security/JwtTokenService.java`
- Test: `src/test/java/com/tuowei/erp/common/security/JwtTokenServiceTest.java`
- Modify: `src/main/resources/application.yml`

- [x] **Step 1: Write the failing JWT service tests**

Create `src/test/java/com/tuowei/erp/common/security/JwtTokenServiceTest.java`:

```java
package com.tuowei.erp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-29T08:00:00Z"), ZoneOffset.UTC);
    private final SecurityProperties properties = new SecurityProperties(
            new SecurityProperties.Jwt("local-test-secret-value-with-32-bytes-min", 7200)
    );
    private final JwtTokenService jwtTokenService = new JwtTokenService(new ObjectMapper(), properties, clock);

    @Test
    void createsAndParsesAccessToken() {
        ErpPrincipal principal = new ErpPrincipal(
                4001L,
                1L,
                1L,
                "admin",
                "系统管理员",
                "$2a$10$hash",
                Set.of("system:user:list")
        );

        String token = jwtTokenService.createAccessToken(principal);
        JwtTokenService.JwtClaims claims = jwtTokenService.parse(token);

        assertThat(claims.userId()).isEqualTo(4001L);
        assertThat(claims.username()).isEqualTo("admin");
        assertThat(claims.expiresAt()).isEqualTo(Instant.parse("2026-04-29T10:00:00Z"));
    }

    @Test
    void rejectsTamperedToken() {
        ErpPrincipal principal = new ErpPrincipal(
                4001L,
                1L,
                1L,
                "admin",
                "系统管理员",
                "$2a$10$hash",
                Set.of("system:user:list")
        );
        String token = jwtTokenService.createAccessToken(principal);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtTokenService.parse(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT签名无效");
    }

    @Test
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt("too-short", 7200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ERP_JWT_SECRET长度不能小于32字节");
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=JwtTokenServiceTest test
```

Expected: compilation fails because `SecurityProperties`, `ErpPrincipal`, and `JwtTokenService` do not exist.

- [x] **Step 3: Add JWT properties**

Create `src/main/java/com/tuowei/erp/common/config/SecurityProperties.java`:

```java
package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.security")
public record SecurityProperties(Jwt jwt) {

    public record Jwt(String secret, long accessTokenTtlSeconds) {

        public Jwt {
            if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("ERP_JWT_SECRET长度不能小于32字节");
            }
            if (accessTokenTtlSeconds < 60) {
                throw new IllegalArgumentException("access-token-ttl-seconds不能小于60");
            }
        }
    }
}
```

Modify `src/main/resources/application.yml`:

```yaml
erp:
  app:
    code: erp-server
    name: ERP Server
    timezone: Asia/Shanghai
  security:
    jwt:
      secret: ${ERP_JWT_SECRET:local-dev-secret-change-me-32bytes-minimum}
      access-token-ttl-seconds: 7200
```

- [x] **Step 4: Add authenticated principal**

Create `src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java`:

```java
package com.tuowei.erp.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public record ErpPrincipal(
        Long userId,
        Long companyId,
        Long accountBookId,
        String username,
        String realName,
        String password,
        Set<String> permissions
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
```

- [x] **Step 5: Add JWT service**

Create `src/main/java/com/tuowei/erp/common/security/JwtTokenService.java`:

```java
package com.tuowei.erp.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.config.SecurityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class JwtTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(ObjectMapper objectMapper, SecurityProperties properties, Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(ErpPrincipal principal) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(properties.jwt().accessTokenTtlSeconds());
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "sub", principal.username(),
                "uid", principal.userId(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()
        );
        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return unsigned + "." + sign(unsigned);
    }

    public JwtClaims parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT格式无效");
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(unsigned).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("JWT签名无效");
        }
        Map<String, Object> payload = decodeJson(parts[1]);
        Instant expiresAt = Instant.ofEpochSecond(((Number) payload.get("exp")).longValue());
        if (!expiresAt.isAfter(Instant.now(clock))) {
            throw new IllegalArgumentException("JWT已过期");
        }
        return new JwtClaims(
                ((Number) payload.get("uid")).longValue(),
                String.valueOf(payload.get("sub")),
                expiresAt
        );
    }

    public long accessTokenTtlSeconds() {
        return properties.jwt().accessTokenTtlSeconds();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT编码失败", ex);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return objectMapper.readValue(URL_DECODER.decode(value), MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT载荷无效", ex);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT签名失败", ex);
        }
    }

    public record JwtClaims(Long userId, String username, Instant expiresAt) {
    }
}
```

- [x] **Step 6: Add a Clock bean**

Modify `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java` in Task 4. For this task, the unit test can instantiate `JwtTokenService` directly.

- [x] **Step 7: Run test to verify it passes**

Run:

```powershell
mvn -Dtest=JwtTokenServiceTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [x] **Step 8: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/config/SecurityProperties.java src/main/java/com/tuowei/erp/common/security/ErpPrincipal.java src/main/java/com/tuowei/erp/common/security/JwtTokenService.java src/main/resources/application.yml src/test/java/com/tuowei/erp/common/security/JwtTokenServiceTest.java
git commit -m "feat: add jwt token service"
```

---

### Task 2: Database User And Permission Loading

**Files:**

- Create: `src/main/java/com/tuowei/erp/common/security/UserPermissionService.java`
- Create: `src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java`
- Create: `src/main/java/com/tuowei/erp/common/security/CurrentUser.java`
- Create: `src/main/java/com/tuowei/erp/common/security/CurrentUserContext.java`
- Test: `src/test/java/com/tuowei/erp/common/security/UserPermissionServiceTest.java`

- [x] **Step 1: Write failing permission loading test**

Create `src/test/java/com/tuowei/erp/common/security/UserPermissionServiceTest.java`:

```java
package com.tuowei.erp.common.security;

import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserPermissionServiceTest {

    @Autowired
    private UserPermissionService userPermissionService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void loadsOnlyActiveMenuPermissionsFromActiveRoles() {
        long userId = System.nanoTime();
        long activeRoleId = userId + 1;
        long disabledRoleId = userId + 2;
        long activeMenuId = userId + 3;
        long blankPermissionMenuId = userId + 4;
        long disabledMenuId = userId + 5;
        LocalDateTime now = LocalDateTime.now();

        insertRole(activeRoleId, "ACTIVE_ROLE_" + userId, "ACTIVE", now);
        insertRole(disabledRoleId, "DISABLED_ROLE_" + userId, "DISABLED", now);
        insertUserRole(userId, activeRoleId, now);
        insertUserRole(userId, disabledRoleId, now);

        insertMenu(activeMenuId, "ACTIVE_MENU_" + userId, "system:user:list", "ACTIVE", now);
        insertMenu(blankPermissionMenuId, "BLANK_MENU_" + userId, "", "ACTIVE", now);
        insertMenu(disabledMenuId, "DISABLED_MENU_" + userId, "system:user:disable", "DISABLED", now);

        insertRoleMenu(activeRoleId, activeMenuId, now);
        insertRoleMenu(activeRoleId, blankPermissionMenuId, now);
        insertRoleMenu(activeRoleId, disabledMenuId, now);

        Set<String> permissions = userPermissionService.loadPermissions(userId);

        assertThat(permissions).containsExactly("system:user:list");
    }

    private void insertRole(long id, String code, String status, LocalDateTime now) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setStatus(status);
        role.setDeletedFlag(0);
        role.setCreatedBy(0L);
        role.setCreatedTime(now);
        role.setUpdatedBy(0L);
        role.setUpdatedTime(now);
        role.setVersion(0);
        roleMapper.insert(role);
    }

    private void insertUserRole(long userId, long roleId, LocalDateTime now) {
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setCreatedBy(0L);
        userRole.setCreatedTime(now);
        userRoleMapper.insert(userRole);
    }

    private void insertMenu(long id, String code, String permission, String status, LocalDateTime now) {
        MenuEntity menu = new MenuEntity();
        menu.setId(id);
        menu.setParentId(0L);
        menu.setMenuType("BUTTON");
        menu.setMenuCode(code);
        menu.setMenuName(code);
        menu.setPermission(permission);
        menu.setSortNo(1);
        menu.setVisibleFlag(1);
        menu.setStatus(status);
        menu.setDeletedFlag(0);
        menu.setCreatedBy(0L);
        menu.setCreatedTime(now);
        menu.setUpdatedBy(0L);
        menu.setUpdatedTime(now);
        menu.setVersion(0);
        menuMapper.insert(menu);
    }

    private void insertRoleMenu(long roleId, long menuId, LocalDateTime now) {
        RoleMenuEntity roleMenu = new RoleMenuEntity();
        roleMenu.setRoleId(roleId);
        roleMenu.setMenuId(menuId);
        roleMenu.setCreatedBy(0L);
        roleMenu.setCreatedTime(now);
        roleMenuMapper.insert(roleMenu);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=UserPermissionServiceTest test
```

Expected: compilation fails because `UserPermissionService` does not exist.

- [x] **Step 3: Implement permission loading service**

Create `src/main/java/com/tuowei/erp/common/security/UserPermissionService.java`:

```java
package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.system.menu.mapper.MenuMapper;
import com.tuowei.erp.system.menu.mapper.RoleMenuMapper;
import com.tuowei.erp.system.menu.model.MenuEntity;
import com.tuowei.erp.system.menu.model.RoleMenuEntity;
import com.tuowei.erp.system.role.mapper.RoleMapper;
import com.tuowei.erp.system.role.model.RoleEntity;
import com.tuowei.erp.system.user.mapper.UserRoleMapper;
import com.tuowei.erp.system.user.model.UserRoleEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserPermissionService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    public UserPermissionService(UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                                 RoleMenuMapper roleMenuMapper, MenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    public Set<String> loadPermissions(Long userId) {
        List<Long> assignedRoleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .distinct()
                .toList();
        if (assignedRoleIds.isEmpty()) {
            return Set.of();
        }

        List<Long> activeRoleIds = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .in(RoleEntity::getId, assignedRoleIds)
                        .eq(RoleEntity::getDeletedFlag, 0)
                        .eq(RoleEntity::getStatus, "ACTIVE"))
                .stream()
                .map(RoleEntity::getId)
                .toList();
        if (activeRoleIds.isEmpty()) {
            return Set.of();
        }

        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenuEntity>()
                        .in(RoleMenuEntity::getRoleId, activeRoleIds))
                .stream()
                .map(RoleMenuEntity::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        menuMapper.selectList(new LambdaQueryWrapper<MenuEntity>()
                        .in(MenuEntity::getId, menuIds)
                        .eq(MenuEntity::getDeletedFlag, 0)
                        .eq(MenuEntity::getStatus, "ACTIVE")
                        .orderByAsc(MenuEntity::getSortNo)
                        .orderByAsc(MenuEntity::getMenuCode))
                .stream()
                .map(MenuEntity::getPermission)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(permissions::add);
        return permissions;
    }
}
```

- [x] **Step 4: Implement DB-backed UserDetailsService**

Create `src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java`:

```java
package com.tuowei.erp.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserPermissionService userPermissionService;

    public DatabaseUserDetailsService(UserMapper userMapper, UserPermissionService userPermissionService) {
        this.userMapper = userMapper;
        this.userPermissionService = userPermissionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)
                .eq(UserEntity::getDeletedFlag, 0)
                .eq(UserEntity::getStatus, "ACTIVE"));
        if (entity == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        return new ErpPrincipal(
                entity.getId(),
                entity.getCompanyId(),
                entity.getAccountBookId(),
                entity.getUsername(),
                entity.getRealName(),
                entity.getPassword(),
                userPermissionService.loadPermissions(entity.getId())
        );
    }
}
```

- [x] **Step 5: Implement current user context**

Create `src/main/java/com/tuowei/erp/common/security/CurrentUser.java`:

```java
package com.tuowei.erp.common.security;

public record CurrentUser(
        Long userId,
        Long companyId,
        Long accountBookId,
        String username,
        String realName
) {
}
```

Create `src/main/java/com/tuowei/erp/common/security/CurrentUserContext.java`:

```java
package com.tuowei.erp.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserContext {

    public CurrentUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ErpPrincipal principal)) {
            throw new IllegalStateException("当前用户未登录");
        }
        return new CurrentUser(
                principal.userId(),
                principal.companyId(),
                principal.accountBookId(),
                principal.username(),
                principal.realName()
        );
    }
}
```

- [x] **Step 6: Run permission test**

Run:

```powershell
mvn -Dtest=UserPermissionServiceTest test
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [x] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/security/UserPermissionService.java src/main/java/com/tuowei/erp/common/security/DatabaseUserDetailsService.java src/main/java/com/tuowei/erp/common/security/CurrentUser.java src/main/java/com/tuowei/erp/common/security/CurrentUserContext.java src/test/java/com/tuowei/erp/common/security/UserPermissionServiceTest.java
git commit -m "feat: load user permissions from database"
```

---

### Task 3: Login API

**Files:**

- Create: `src/main/java/com/tuowei/erp/system/auth/controller/AuthController.java`
- Create: `src/main/java/com/tuowei/erp/system/auth/service/AuthService.java`
- Create: `src/main/java/com/tuowei/erp/system/auth/web/LoginRequest.java`
- Create: `src/main/java/com/tuowei/erp/system/auth/web/LoginResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/auth/web/LoginUserResponse.java`
- Test: `src/test/java/com/tuowei/erp/system/auth/AuthControllerLoginTest.java`

- [x] **Step 1: Write failing login API tests**

Create `src/test/java/com/tuowei/erp/system/auth/AuthControllerLoginTest.java`:

```java
package com.tuowei.erp.system.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void logsInWithSeededAdminUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.permissions.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.permissions", hasItem("system:profile:view")));
    }

    @Test
    void rejectsBadPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=AuthControllerLoginTest test
```

Expected: `404` or compilation failure because auth endpoint and seed data do not exist yet.

- [x] **Step 3: Add login request and response records**

Create `src/main/java/com/tuowei/erp/system/auth/web/LoginRequest.java`:

```java
package com.tuowei.erp.system.auth.web;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "username不能为空") String username,
        @NotBlank(message = "password不能为空") String password
) {
}
```

Create `src/main/java/com/tuowei/erp/system/auth/web/LoginUserResponse.java`:

```java
package com.tuowei.erp.system.auth.web;

public record LoginUserResponse(
        Long id,
        String username,
        String realName
) {
}
```

Create `src/main/java/com/tuowei/erp/system/auth/web/LoginResponse.java`:

```java
package com.tuowei.erp.system.auth.web;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        LoginUserResponse user,
        List<String> permissions
) {
}
```

- [x] **Step 4: Add auth service**

Create `src/main/java/com/tuowei/erp/system/auth/service/AuthService.java`:

```java
package com.tuowei.erp.system.auth.service;

import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.JwtTokenService;
import com.tuowei.erp.system.auth.web.LoginRequest;
import com.tuowei.erp.system.auth.web.LoginResponse;
import com.tuowei.erp.system.auth.web.LoginUserResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        ErpPrincipal principal = (ErpPrincipal) authentication.getPrincipal();
        String accessToken = jwtTokenService.createAccessToken(principal);
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtTokenService.accessTokenTtlSeconds(),
                new LoginUserResponse(principal.userId(), principal.username(), principal.realName()),
                principal.permissions().stream().toList()
        );
    }
}
```

- [x] **Step 5: Add auth controller**

Create `src/main/java/com/tuowei/erp/system/auth/controller/AuthController.java`:

```java
package com.tuowei.erp.system.auth.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.system.auth.service.AuthService;
import com.tuowei.erp.system.auth.web.LoginRequest;
import com.tuowei.erp.system.auth.web.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
```

- [x] **Step 6: Stop here if test still fails because seed data is missing**

If `AuthControllerLoginTest` now fails because admin data does not exist, do not fake data in the test. Continue to Task 5 and rerun this test after seed migration exists.

- [x] **Step 7: Commit after Task 5 makes this test pass**

```powershell
git add src/main/java/com/tuowei/erp/system/auth src/test/java/com/tuowei/erp/system/auth/AuthControllerLoginTest.java
git commit -m "feat: add login endpoint"
```

---

### Task 4: Stateless Security Filter Chain

**Files:**

- Create: `src/main/java/com/tuowei/erp/common/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java`
- Test: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java`

- [x] **Step 1: Update failing security config test**

Modify `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java` test method:

```java
@Test
void permitsHealthAndLoginButProtectsProfileEndpoint() throws Exception {
    mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"username":"admin","password":"wrong-password"}
                            """))
            .andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/system/profile"))
            .andExpect(status().isUnauthorized());
}
```

Add imports:

```java
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=SecurityConfigTest test
```

Expected: failure because `/api/auth/login` is not yet permitted or security config still uses HTTP Basic.

- [x] **Step 3: Add JWT authentication filter**

Create `src/main/java/com/tuowei/erp/common/security/JwtAuthenticationFilter.java`:

```java
package com.tuowei.erp.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final DatabaseUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, DatabaseUserDetailsService userDetailsService) {
        this.jwtTokenService = jwtTokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authenticate(request, authorization.substring(7));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            JwtTokenService.JwtClaims claims = jwtTokenService.parse(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(claims.username());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
        }
    }
}
```

- [x] **Step 4: Replace security filter chain**

Modify `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java`:

```java
package com.tuowei.erp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.web.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), new ApiResponse<>("401", "未登录或登录已过期", null));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), new ApiResponse<>("403", "无权访问", null));
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/api/auth/login",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(DatabaseUserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [x] **Step 5: Run security config test**

Run:

```powershell
mvn -Dtest=SecurityConfigTest test
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [x] **Step 6: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/security/JwtAuthenticationFilter.java src/main/java/com/tuowei/erp/common/security/SecurityConfig.java src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java
git commit -m "feat: switch security to bearer token"
```

---

### Task 5: Flyway Seed Data

**Files:**

- Create: `src/main/resources/db/migration/V16__auth_seed_data.sql`
- Test: `src/test/java/com/tuowei/erp/system/auth/AuthSeedMigrationTest.java`
- Modify: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`

- [x] **Step 1: Write failing seed migration test**

Create `src/test/java/com/tuowei/erp/system/auth/AuthSeedMigrationTest.java`:

```java
package com.tuowei.erp.system.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuthSeedMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywaySeedsAdminRoleUserAndPermissions() {
        Integer adminCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_user where username = 'admin' and status = 'ACTIVE' and deleted_flag = 0",
                Integer.class
        );
        Integer roleCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_role where role_code = 'SUPER_ADMIN' and status = 'ACTIVE' and deleted_flag = 0",
                Integer.class
        );
        Integer profilePermissionCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_menu where permission = 'system:profile:view' and status = 'ACTIVE' and deleted_flag = 0",
                Integer.class
        );
        Integer adminRoleCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from sys_user_role ur
                join sys_user u on u.id = ur.user_id
                join sys_role r on r.id = ur.role_id
                where u.username = 'admin' and r.role_code = 'SUPER_ADMIN'
                """,
                Integer.class
        );
        Integer adminProfilePermissionCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from sys_user u
                join sys_user_role ur on ur.user_id = u.id
                join sys_role_menu rm on rm.role_id = ur.role_id
                join sys_menu m on m.id = rm.menu_id
                where u.username = 'admin' and m.permission = 'system:profile:view'
                """,
                Integer.class
        );

        assertThat(adminCount).isEqualTo(1);
        assertThat(roleCount).isEqualTo(1);
        assertThat(profilePermissionCount).isEqualTo(1);
        assertThat(adminRoleCount).isEqualTo(1);
        assertThat(adminProfilePermissionCount).isEqualTo(1);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=AuthSeedMigrationTest test
```

Expected: assertion failure because admin seed data is absent.

- [x] **Step 3: Add auth seed migration**

Create `src/main/resources/db/migration/V16__auth_seed_data.sql`:

```sql
INSERT INTO sys_role (id, role_code, role_name, status, deleted_flag, created_by, updated_by, version)
SELECT 3001, 'SUPER_ADMIN', '超级管理员', 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN');

INSERT INTO sys_user (id, company_id, account_book_id, username, password, employee_no, real_name, status, deleted_flag, created_by, updated_by, version)
SELECT 4001, 1, 1, 'admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiA2Rv4RANQ0H3TnYx1p8g4a9e6fW7K', 'EMP_ADMIN', '系统管理员', 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

INSERT INTO sys_user_role (id, user_id, role_id, created_by)
SELECT 4101, 4001, 3001, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 4001 AND role_id = 3001);

INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
SELECT 50001, 0, 'BUTTON', 'SYSTEM_PROFILE_VIEW', '查看当前用户', 'system:profile:view', 1, 0, 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'SYSTEM_PROFILE_VIEW');

INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
SELECT 50002, 0, 'BUTTON', 'SYSTEM_USER_LIST', '用户列表', 'system:user:list', 2, 0, 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'SYSTEM_USER_LIST');

INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
SELECT 50003, 0, 'BUTTON', 'SYSTEM_USER_CREATE', '创建用户', 'system:user:create', 3, 0, 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'SYSTEM_USER_CREATE');

INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
SELECT 50004, 0, 'BUTTON', 'SYSTEM_ROLE_ASSIGN_MENU', '分配角色菜单', 'system:role:assign-menu', 4, 0, 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'SYSTEM_ROLE_ASSIGN_MENU');

INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
SELECT 50005, 0, 'BUTTON', 'PURCHASE_ORDER_APPROVE', '采购订单审批', 'purchase:order:approve', 5, 0, 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'PURCHASE_ORDER_APPROVE');

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT 60001, 3001, 50001, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3001 AND menu_id = 50001);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT 60002, 3001, 50002, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3001 AND menu_id = 50002);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT 60003, 3001, 50003, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3001 AND menu_id = 50003);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT 60004, 3001, 50004, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3001 AND menu_id = 50004);

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT 60005, 3001, 50005, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3001 AND menu_id = 50005);
```

- [x] **Step 4: Keep seed permissions intentionally small first**

Do not seed every future ERP permission in this task. Seed only permissions that tests and first annotations need. Add more permission rows in Task 6 when endpoints are annotated, so failing tests drive the actual permission set.

- [x] **Step 5: Update DB script layout test**

Modify `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java` to add:

```java
assertThat(Files.exists(Path.of("src/main/resources/db/migration/V16__auth_seed_data.sql"))).isTrue();
```

- [x] **Step 6: Run seed migration tests**

Run:

```powershell
mvn -Dtest=AuthSeedMigrationTest,DbScriptLayoutTest test
```

Expected: both tests pass.

- [x] **Step 7: Rerun login test**

Run:

```powershell
mvn -Dtest=AuthControllerLoginTest test
```

Expected: login success and bad-password tests pass after Task 3 and Task 4 are also complete.

- [x] **Step 8: Commit**

```powershell
git add src/main/resources/db/migration/V16__auth_seed_data.sql src/test/java/com/tuowei/erp/system/auth/AuthSeedMigrationTest.java src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java
git commit -m "feat: seed admin auth data"
```

---

### Task 6: Permission Constants, Controller Annotations, And Test Admin Annotation

**Files:**

- Create: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Create: `src/test/java/com/tuowei/erp/testsupport/WithMockErpAdmin.java`
- Modify: `src/main/java/com/tuowei/erp/system/controller/ProfileController.java`
- Modify: `src/main/java/com/tuowei/erp/system/user/controller/UserController.java`
- Modify: `src/main/java/com/tuowei/erp/system/role/controller/RoleController.java`
- Modify: existing tests that use `@WithMockUser(username = "admin")`

- [x] **Step 1: Write failing authorization integration test**

Create `src/test/java/com/tuowei/erp/system/auth/AuthAuthorizationIntegrationTest.java`:

```java
package com.tuowei.erp.system.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void bearerTokenCanAccessAuthorizedEndpoint() throws Exception {
        String token = login("admin", "password");

        mockMvc.perform(get("/api/system/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("protected"));
    }

    @Test
    void bearerTokenWithoutPermissionReceivesForbidden() throws Exception {
        String username = "limited_" + System.nanoTime();
        jdbcTemplate.update("""
                insert into sys_user (id, company_id, account_book_id, username, password, real_name, status, deleted_flag, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, '受限用户', 'ACTIVE', 0, 0, 0, 0)
                """, System.nanoTime(), username, passwordEncoder.encode("password"));

        String token = login(username, "password");

        mockMvc.perform(get("/api/system/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=AuthAuthorizationIntegrationTest test
```

Expected: limited user can access `/api/system/profile`, so the forbidden test fails.

- [x] **Step 3: Add permission constants**

Create `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`:

```java
package com.tuowei.erp.common.security;

public final class PermissionCodes {

    public static final String SYSTEM_PROFILE_VIEW = "system:profile:view";
    public static final String SYSTEM_USER_LIST = "system:user:list";
    public static final String SYSTEM_USER_CREATE = "system:user:create";
    public static final String SYSTEM_USER_UPDATE = "system:user:update";
    public static final String SYSTEM_USER_ENABLE = "system:user:enable";
    public static final String SYSTEM_USER_DISABLE = "system:user:disable";
    public static final String SYSTEM_USER_ASSIGN_ROLE = "system:user:assign-role";
    public static final String SYSTEM_ROLE_LIST = "system:role:list";
    public static final String SYSTEM_ROLE_CREATE = "system:role:create";
    public static final String SYSTEM_ROLE_UPDATE = "system:role:update";
    public static final String SYSTEM_ROLE_ENABLE = "system:role:enable";
    public static final String SYSTEM_ROLE_DISABLE = "system:role:disable";
    public static final String SYSTEM_ROLE_ASSIGN_MENU = "system:role:assign-menu";

    public static final String HAS_SYSTEM_PROFILE_VIEW = "hasAuthority('" + SYSTEM_PROFILE_VIEW + "')";
    public static final String HAS_SYSTEM_USER_LIST = "hasAuthority('" + SYSTEM_USER_LIST + "')";
    public static final String HAS_SYSTEM_USER_CREATE = "hasAuthority('" + SYSTEM_USER_CREATE + "')";
    public static final String HAS_SYSTEM_USER_UPDATE = "hasAuthority('" + SYSTEM_USER_UPDATE + "')";
    public static final String HAS_SYSTEM_USER_ENABLE = "hasAuthority('" + SYSTEM_USER_ENABLE + "')";
    public static final String HAS_SYSTEM_USER_DISABLE = "hasAuthority('" + SYSTEM_USER_DISABLE + "')";
    public static final String HAS_SYSTEM_USER_ASSIGN_ROLE = "hasAuthority('" + SYSTEM_USER_ASSIGN_ROLE + "')";
    public static final String HAS_SYSTEM_ROLE_LIST = "hasAuthority('" + SYSTEM_ROLE_LIST + "')";
    public static final String HAS_SYSTEM_ROLE_CREATE = "hasAuthority('" + SYSTEM_ROLE_CREATE + "')";
    public static final String HAS_SYSTEM_ROLE_UPDATE = "hasAuthority('" + SYSTEM_ROLE_UPDATE + "')";
    public static final String HAS_SYSTEM_ROLE_ENABLE = "hasAuthority('" + SYSTEM_ROLE_ENABLE + "')";
    public static final String HAS_SYSTEM_ROLE_DISABLE = "hasAuthority('" + SYSTEM_ROLE_DISABLE + "')";
    public static final String HAS_SYSTEM_ROLE_ASSIGN_MENU = "hasAuthority('" + SYSTEM_ROLE_ASSIGN_MENU + "')";

    private PermissionCodes() {
    }
}
```

- [x] **Step 4: Annotate profile endpoint**

Modify `src/main/java/com/tuowei/erp/system/controller/ProfileController.java`:

```java
import com.tuowei.erp.common.security.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
```

Add to `profile()`:

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_PROFILE_VIEW)
@GetMapping("/profile")
public ApiResponse<Map<String, String>> profile() {
    return ApiResponse.success(Map.of("scope", "protected"));
}
```

- [x] **Step 5: Add test admin annotation**

Create `src/test/java/com/tuowei/erp/testsupport/WithMockErpAdmin.java`:

```java
package com.tuowei.erp.testsupport;

import org.springframework.security.test.context.support.WithMockUser;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({METHOD, TYPE})
@WithMockUser(
        username = "admin",
        authorities = {
                "system:profile:view",
                "system:user:list",
                "system:user:create",
                "system:user:update",
                "system:user:enable",
                "system:user:disable",
                "system:user:assign-role",
                "system:role:list",
                "system:role:create",
                "system:role:update",
                "system:role:enable",
                "system:role:disable",
                "system:role:assign-menu"
        }
)
public @interface WithMockErpAdmin {
}
```

- [x] **Step 6: Replace existing admin mock annotations**

For each test file that currently imports:

```java
import org.springframework.security.test.context.support.WithMockUser;
```

and uses:

```java
@WithMockUser(username = "admin")
```

replace with:

```java
import com.tuowei.erp.testsupport.WithMockErpAdmin;
```

and:

```java
@WithMockErpAdmin
```

Run this search to find remaining cases:

```powershell
Get-ChildItem -Recurse -File 'src\test\java' -Filter '*.java' | Select-String -Pattern '@WithMockUser\(username = "admin"\)'
```

Expected after replacement: no results.

- [x] **Step 7: Annotate user and role controllers**

Modify `src/main/java/com/tuowei/erp/system/user/controller/UserController.java`:

```java
import com.tuowei.erp.common.security.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
```

Add annotations:

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_CREATE)
@PostMapping
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_LIST)
@GetMapping
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_LIST)
@GetMapping("/{id}")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_UPDATE)
@PutMapping("/{id}")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_ENABLE)
@PostMapping("/{id}/enable")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_DISABLE)
@PostMapping("/{id}/disable")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_ASSIGN_ROLE)
@PutMapping("/{id}/roles")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_USER_ASSIGN_ROLE)
@GetMapping("/{id}/roles")
```

Modify `src/main/java/com/tuowei/erp/system/role/controller/RoleController.java`:

```java
import com.tuowei.erp.common.security.PermissionCodes;
import org.springframework.security.access.prepost.PreAuthorize;
```

Add annotations:

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_CREATE)
@PostMapping
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_LIST)
@GetMapping
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_LIST)
@GetMapping("/{id}")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_UPDATE)
@PutMapping("/{id}")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_ENABLE)
@PostMapping("/{id}/enable")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_DISABLE)
@PostMapping("/{id}/disable")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_ASSIGN_MENU)
@PutMapping("/{id}/menus")
```

```java
@PreAuthorize(PermissionCodes.HAS_SYSTEM_ROLE_ASSIGN_MENU)
@GetMapping("/{id}/menus")
```

- [x] **Step 8: Extend seed data for user and role permissions**

Extend `src/main/resources/db/migration/V16__auth_seed_data.sql` with rows for this explicit permission set, using menu IDs `50006` through `50013` and role-menu IDs `60006` through `60013`:

```text
50006 / 60006 SYSTEM_USER_UPDATE       system:user:update
50007 / 60007 SYSTEM_USER_ENABLE       system:user:enable
50008 / 60008 SYSTEM_USER_DISABLE      system:user:disable
50009 / 60009 SYSTEM_USER_ASSIGN_ROLE  system:user:assign-role
50010 / 60010 SYSTEM_ROLE_LIST         system:role:list
50011 / 60011 SYSTEM_ROLE_UPDATE       system:role:update
50012 / 60012 SYSTEM_ROLE_ENABLE       system:role:enable
50013 / 60013 SYSTEM_ROLE_DISABLE      system:role:disable
```

Use this exact pattern for each permission:

```sql
INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
SELECT 50006, 0, 'BUTTON', 'SYSTEM_USER_UPDATE', '更新用户', 'system:user:update', 6, 0, 'ACTIVE', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'SYSTEM_USER_UPDATE');

INSERT INTO sys_role_menu (id, role_id, menu_id, created_by)
SELECT 60006, 3001, 50006, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 3001 AND menu_id = 50006);
```

Do not reuse IDs. Do not update existing user-defined permissions.

- [x] **Step 9: Run authorization tests**

Run:

```powershell
mvn -Dtest=AuthAuthorizationIntegrationTest,UserControllerCreateTest,RoleMenuAuthorizationTest test
```

Expected: all selected tests pass.

- [x] **Step 10: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/security/PermissionCodes.java src/test/java/com/tuowei/erp/testsupport/WithMockErpAdmin.java src/main/java/com/tuowei/erp/system/controller/ProfileController.java src/main/java/com/tuowei/erp/system/user/controller/UserController.java src/main/java/com/tuowei/erp/system/role/controller/RoleController.java src/main/resources/db/migration/V16__auth_seed_data.sql src/test/java src/test/java/com/tuowei/erp/system/auth/AuthAuthorizationIntegrationTest.java
git commit -m "feat: enforce system permissions"
```

---

### Task 7: Expand Permission Coverage To Remaining Controllers

**Files:**

- Modify: `src/main/java/com/tuowei/erp/system/config/controller/SystemConfigController.java`
- Modify: `src/main/java/com/tuowei/erp/system/config/controller/SequenceRuleController.java`
- Modify: `src/main/java/com/tuowei/erp/system/dept/controller/DeptController.java`
- Modify: `src/main/java/com/tuowei/erp/system/menu/controller/MenuController.java`
- Modify: `src/main/java/com/tuowei/erp/system/post/controller/PostController.java`
- Modify: `src/main/java/com/tuowei/erp/masterdata/*/controller/*.java`
- Modify: `src/main/java/com/tuowei/erp/purchase/*/controller/*.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/main/resources/db/migration/V16__auth_seed_data.sql`
- Modify: `src/test/java/com/tuowei/erp/testsupport/WithMockErpAdmin.java`

- [x] **Step 1: Add one failing permission test for a purchase endpoint**

Extend `AuthAuthorizationIntegrationTest`:

```java
@Test
void limitedUserCannotAccessPurchaseOrderList() throws Exception {
    String username = "limited_purchase_" + System.nanoTime();
    jdbcTemplate.update("""
            insert into sys_user (id, company_id, account_book_id, username, password, real_name, status, deleted_flag, created_by, updated_by, version)
            values (?, 1, 1, ?, ?, '受限采购用户', 'ACTIVE', 0, 0, 0, 0)
            """, System.nanoTime(), username, passwordEncoder.encode("password"));

    String token = login(username, "password");

    mockMvc.perform(get("/api/purchase/orders")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("403"));
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=AuthAuthorizationIntegrationTest test
```

Expected: `limitedUserCannotAccessPurchaseOrderList` fails because purchase order list is not annotated yet.

- [x] **Step 3: Add remaining permission constants**

Add constants in `PermissionCodes` for each controller action. Use the same naming convention:

```java
public static final String PURCHASE_ORDER_LIST = "purchase:order:list";
public static final String PURCHASE_ORDER_CREATE = "purchase:order:create";
public static final String PURCHASE_ORDER_UPDATE = "purchase:order:update";
public static final String PURCHASE_ORDER_SUBMIT = "purchase:order:submit";
public static final String PURCHASE_ORDER_APPROVE = "purchase:order:approve";
public static final String PURCHASE_ORDER_REJECT = "purchase:order:reject";
public static final String PURCHASE_ORDER_CANCEL = "purchase:order:cancel";
public static final String HAS_PURCHASE_ORDER_LIST = "hasAuthority('" + PURCHASE_ORDER_LIST + "')";
public static final String HAS_PURCHASE_ORDER_CREATE = "hasAuthority('" + PURCHASE_ORDER_CREATE + "')";
public static final String HAS_PURCHASE_ORDER_UPDATE = "hasAuthority('" + PURCHASE_ORDER_UPDATE + "')";
public static final String HAS_PURCHASE_ORDER_SUBMIT = "hasAuthority('" + PURCHASE_ORDER_SUBMIT + "')";
public static final String HAS_PURCHASE_ORDER_APPROVE = "hasAuthority('" + PURCHASE_ORDER_APPROVE + "')";
public static final String HAS_PURCHASE_ORDER_REJECT = "hasAuthority('" + PURCHASE_ORDER_REJECT + "')";
public static final String HAS_PURCHASE_ORDER_CANCEL = "hasAuthority('" + PURCHASE_ORDER_CANCEL + "')";
```

Add constants for this explicit permission set:

```text
purchase:receipt:list
purchase:receipt:create
purchase:receipt:update
purchase:receipt:cancel
purchase:receipt:post
purchase:return:list
purchase:return:create
purchase:return:update
purchase:return:cancel
purchase:return:post
masterdata:product:list/create/update/enable/disable
masterdata:customer:list/create/update/enable/disable
masterdata:supplier:list/create/update/enable/disable
masterdata:warehouse:list/create/update/enable/disable
system:config:list/create/update/enable/disable
system:sequence-rule:list/create/update/enable/disable
system:dept:list/create/update/enable/disable
system:menu:list/create/update/enable/disable
system:post:list/create/update/enable/disable
```

- [x] **Step 4: Annotate remaining controllers**

Annotate controller methods according to this map. Put `@PreAuthorize(PermissionCodes.HAS_...)` above the existing mapping annotation. Use list permission for both page list and detail endpoints. Use action-specific permissions for create, update, enable, disable, submit, approve, reject, cancel, and post endpoints.

Example for `src/main/java/com/tuowei/erp/purchase/order/controller/PurchaseOrderController.java`:

```java
@PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_LIST)
@GetMapping
```

```java
@PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_CREATE)
@PostMapping
```

```java
@PreAuthorize(PermissionCodes.HAS_PURCHASE_ORDER_APPROVE)
@PostMapping("/{id}/approve")
```

- [x] **Step 5: Extend seed SQL and test admin annotation**

For each item in the explicit permission set above:

- Add a `sys_menu` seed row to `V16__auth_seed_data.sql`.
- Add a matching `sys_role_menu` seed row binding role `3001`.
- Add the permission string to `WithMockErpAdmin.authorities`.

Use monotonically increasing IDs after the last ID introduced in Task 6.

- [x] **Step 6: Run affected controller suites**

Run:

```powershell
mvn -Dtest=AuthAuthorizationIntegrationTest,*Controller*Test test
```

Expected: controller tests pass. If a test fails with `403`, add the missing permission to `WithMockErpAdmin` or correct the endpoint annotation.

- [x] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/security/PermissionCodes.java src/main/java/com/tuowei/erp src/main/resources/db/migration/V16__auth_seed_data.sql src/test/java/com/tuowei/erp/testsupport/WithMockErpAdmin.java src/test/java/com/tuowei/erp/system/auth/AuthAuthorizationIntegrationTest.java
git commit -m "feat: enforce permissions across controllers"
```

---

### Task 8: Full Regression And Cleanup

**Files:**

- Modify only files that fail verification.

- [x] **Step 1: Search for old security patterns**

Run:

```powershell
Get-ChildItem -Recurse -File 'src\main\java','src\test\java' -Filter '*.java' | Select-String -Pattern 'httpBasic|WithMockUser\(username = "admin"\)|permitAll\(\).*anyRequest|T[O]DO|T[B]D'
```

Expected:

- No `httpBasic` usage in production security config.
- No remaining `@WithMockUser(username = "admin")` in controller tests.
- No marker strings introduced by this work.

- [x] **Step 2: Run targeted auth tests**

Run:

```powershell
mvn -Dtest=JwtTokenServiceTest,UserPermissionServiceTest,AuthSeedMigrationTest,AuthControllerLoginTest,AuthAuthorizationIntegrationTest,SecurityConfigTest test
```

Expected: all targeted auth tests pass.

- [x] **Step 3: Run full test suite**

Run:

```powershell
mvn test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [x] **Step 4: Review git diff**

Run:

```powershell
git diff --stat
git diff -- src/main/java/com/tuowei/erp/common/security/SecurityConfig.java
git diff -- src/main/resources/db/migration/V16__auth_seed_data.sql
```

Expected: changes are limited to auth/security, permission annotations, seed migration, config, and tests.

- [x] **Step 5: Commit verification cleanup**

If Step 1 through Step 4 required cleanup changes, commit them:

```powershell
git add src/main src/test
git commit -m "test: verify auth authorization flow"
```

If no cleanup changes were needed, do not create an empty commit.

---

## Self-Review Checklist

- Spec coverage: login, DB auth, short-lived JWT, `sys_menu.permission`, Flyway admin seed, 401/403 behavior, and tests are covered.
- Scope control: refresh token, login logs, forced password reset, MFA, token blacklist, and audit-field replacement remain out of scope.
- TDD compliance: every behavior-changing task starts with a failing test before production code.
- Existing tests: `@WithMockErpAdmin` prevents method-security annotations from breaking unrelated controller tests.
- Security risk: `SUPER_ADMIN` has no production code bypass; permissions come from seed data.
- Dependency control: JWT uses JDK crypto and Jackson, so no new external dependency is required.

