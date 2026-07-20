# Minimum Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimum observability layer with Prometheus metrics and a protected ERP business-health summary.

**Architecture:** Use Spring Boot Actuator and Micrometer for standard Prometheus metrics. Add a small `system/observability` module that aggregates tenant-scoped counts from existing tables and exposes them through a secured read-only API. Keep monitoring infrastructure outside this app.

**Tech Stack:** Spring Boot 3.5.14, Actuator, Micrometer Prometheus registry, Spring Security method security, MyBatis-Plus, Flyway, MockMvc, JUnit 5.

---

## File Map

- Modify `pom.xml`: add Prometheus registry dependency.
- Modify `src/main/resources/application-prod.yml`: expose `prometheus` along with `health,info`.
- Modify `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`: add `system:observability:view`.
- Modify `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`: add `sys_observability` only if a table is introduced; this plan does not introduce one, so do not modify tenant tables for observability.
- Create `src/main/java/com/tuowei/erp/system/observability/controller/ObservabilityController.java`: API entry.
- Create `src/main/java/com/tuowei/erp/system/observability/service/ObservabilityBusinessHealthService.java`: aggregation logic.
- Create `src/main/java/com/tuowei/erp/system/observability/web/BusinessHealthCheckResponse.java`: check DTO.
- Create `src/main/java/com/tuowei/erp/system/observability/web/BusinessHealthResponse.java`: response DTO.
- Create `src/main/resources/db/migration/V60__system_observability_menu_seed.sql`: permission menu seed only.
- Modify `scripts/preprod-acceptance.ps1`: authenticated `/actuator/prometheus` and business-health evidence.
- Modify `scripts/business-smoke.ps1`: add business-health read-only check.
- Modify `docs/production-deployment.md`: document metrics and business-health check.
- Modify `docs/business-readiness-checklist.md`: include observability in Go / No-Go evidence.
- Add tests under `src/test/java/com/tuowei/erp/common/config` and `src/test/java/com/tuowei/erp/system/observability`.

---

### Task 1: Prometheus Metrics Configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application-prod.yml`
- Test: `src/test/java/com/tuowei/erp/common/config/ObservabilityConfigurationTest.java`

- [ ] **Step 1: Write failing configuration tests**

Create `src/test/java/com/tuowei/erp/common/config/ObservabilityConfigurationTest.java`:

```java
package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {

    @Test
    void pomIncludesPrometheusRegistry() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);

        assertThat(pom)
                .contains("<artifactId>micrometer-registry-prometheus</artifactId>");
    }

    @Test
    void productionProfileExposesPrometheusActuatorEndpoint() throws IOException {
        String prodConfig = Files.readString(Path.of("src", "main", "resources", "application-prod.yml"),
                StandardCharsets.UTF_8);

        assertThat(prodConfig)
                .contains("include: health,info,prometheus");
    }

    @Test
    void prometheusEndpointIsNotAnonymousInSecurityConfig() throws IOException {
        String securityConfig = Files.readString(Path.of("src", "main", "java", "com", "tuowei", "erp",
                        "common", "security", "SecurityConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(securityConfig)
                .contains("\"/actuator/health\"")
                .doesNotContain("\"/actuator/prometheus\"");
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityConfigurationTest" test
```

Expected: FAIL because `micrometer-registry-prometheus` and `health,info,prometheus` are not present.

- [ ] **Step 3: Add Prometheus dependency**

In `pom.xml`, add this dependency near `spring-boot-starter-actuator`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- [ ] **Step 4: Expose Prometheus in prod**

In `src/main/resources/application-prod.yml`, change:

```yaml
include: health,info
```

to:

```yaml
include: health,info,prometheus
```

- [ ] **Step 5: Run tests and verify they pass**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityConfigurationTest" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add pom.xml src/main/resources/application-prod.yml src/test/java/com/tuowei/erp/common/config/ObservabilityConfigurationTest.java
git commit -m "feat: expose prometheus metrics"
```

---

### Task 2: Business Health Service

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/observability/web/BusinessHealthCheckResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/observability/web/BusinessHealthResponse.java`
- Create: `src/main/java/com/tuowei/erp/system/observability/service/ObservabilityBusinessHealthService.java`
- Test: `src/test/java/com/tuowei/erp/system/observability/ObservabilityBusinessHealthServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `src/test/java/com/tuowei/erp/system/observability/ObservabilityBusinessHealthServiceTest.java`:

```java
package com.tuowei.erp.system.observability;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.system.observability.service.ObservabilityBusinessHealthService;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservabilityBusinessHealthServiceTest {

    private final ReadinessItemMapper readinessItemMapper = mock(ReadinessItemMapper.class);
    private final ImportJobMapper importJobMapper = mock(ImportJobMapper.class);
    private final InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
    private final AccountPeriodMapper accountPeriodMapper = mock(AccountPeriodMapper.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);

    @Test
    void returnsUpWhenAllCountsAreHealthy() {
        stubAudit();
        stubCounts(0L, 0L, 0L, 1L);

        BusinessHealthResponse response = service().current();

        assertThat(response.overallStatus()).isEqualTo("UP");
        assertThat(response.checks()).hasSize(4);
        assertThat(response.checks()).allMatch(check -> "UP".equals(check.status()));
    }

    @Test
    void returnsWarnWhenAnyCheckIsUnhealthy() {
        stubAudit();
        stubCounts(2L, 1L, 3L, 0L);

        BusinessHealthResponse response = service().current();

        assertThat(response.overallStatus()).isEqualTo("WARN");
        assertThat(response.checks())
                .extracting("code")
                .containsExactly(
                        "READINESS_UNPASSED_P0_P1",
                        "IMPORT_FAILED_RECENT",
                        "NEGATIVE_INVENTORY_BALANCE",
                        "OPEN_PERIOD_COUNT"
                );
        assertThat(response.checks())
                .filteredOn(check -> "WARN".equals(check.status()))
                .hasSize(4);
    }

    private ObservabilityBusinessHealthService service() {
        return new ObservabilityBusinessHealthService(
                readinessItemMapper,
                importJobMapper,
                inventoryBalanceMapper,
                accountPeriodMapper,
                auditMetadataFactory
        );
    }

    private void stubAudit() {
        when(auditMetadataFactory.current())
                .thenReturn(new AuditMetadata(1001L, 1L, 1L, LocalDateTime.parse("2026-06-05T08:00:00")));
    }

    private void stubCounts(long readiness, long imports, long negativeInventory, long openPeriods) {
        when(readinessItemMapper.selectCount(any())).thenReturn(readiness);
        when(importJobMapper.selectCount(any())).thenReturn(imports);
        when(inventoryBalanceMapper.selectCount(any())).thenReturn(negativeInventory);
        when(accountPeriodMapper.selectCount(any())).thenReturn(openPeriods);
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityBusinessHealthServiceTest" test
```

Expected: FAIL because observability classes do not exist.

- [ ] **Step 3: Create response DTOs**

Create `src/main/java/com/tuowei/erp/system/observability/web/BusinessHealthCheckResponse.java`:

```java
package com.tuowei.erp.system.observability.web;

public record BusinessHealthCheckResponse(
        String code,
        String name,
        String status,
        long count,
        long threshold,
        String summary
) {
}
```

Create `src/main/java/com/tuowei/erp/system/observability/web/BusinessHealthResponse.java`:

```java
package com.tuowei.erp.system.observability.web;

import java.time.LocalDateTime;
import java.util.List;

public record BusinessHealthResponse(
        String overallStatus,
        LocalDateTime generatedAt,
        List<BusinessHealthCheckResponse> checks
) {
}
```

- [ ] **Step 4: Implement service**

Create `src/main/java/com/tuowei/erp/system/observability/service/ObservabilityBusinessHealthService.java`:

```java
package com.tuowei.erp.system.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.imports.mapper.ImportJobMapper;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.system.observability.web.BusinessHealthCheckResponse;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.model.ReadinessItemEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObservabilityBusinessHealthService {

    private static final String STATUS_UP = "UP";
    private static final String STATUS_WARN = "WARN";

    private final ReadinessItemMapper readinessItemMapper;
    private final ImportJobMapper importJobMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ObservabilityBusinessHealthService(
            ReadinessItemMapper readinessItemMapper,
            ImportJobMapper importJobMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.readinessItemMapper = readinessItemMapper;
        this.importJobMapper = importJobMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    public BusinessHealthResponse current() {
        AuditMetadata audit = auditMetadataFactory.current();
        List<BusinessHealthCheckResponse> checks = new ArrayList<>();
        checks.add(readinessCheck(audit));
        checks.add(importCheck(audit));
        checks.add(negativeInventoryCheck(audit));
        checks.add(openPeriodCheck(audit));
        String overallStatus = checks.stream().anyMatch(check -> STATUS_WARN.equals(check.status()))
                ? STATUS_WARN
                : STATUS_UP;
        return new BusinessHealthResponse(overallStatus, audit.now(), List.copyOf(checks));
    }

    private BusinessHealthCheckResponse readinessCheck(AuditMetadata audit) {
        long count = readinessItemMapper.selectCount(new LambdaQueryWrapper<ReadinessItemEntity>()
                .eq(ReadinessItemEntity::getCompanyId, audit.companyId())
                .eq(ReadinessItemEntity::getAccountBookId, audit.accountBookId())
                .eq(ReadinessItemEntity::getDeletedFlag, 0)
                .in(ReadinessItemEntity::getPriority, List.of("P0", "P1"))
                .ne(ReadinessItemEntity::getStatus, "PASSED"));
        return thresholdZero("READINESS_UNPASSED_P0_P1", "未通过 P0/P1 验收项", count,
                "存在未通过或未执行的 P0/P1 readiness 项");
    }

    private BusinessHealthCheckResponse importCheck(AuditMetadata audit) {
        long count = importJobMapper.selectCount(new LambdaQueryWrapper<ImportJobEntity>()
                .eq(ImportJobEntity::getCompanyId, audit.companyId())
                .eq(ImportJobEntity::getAccountBookId, audit.accountBookId())
                .eq(ImportJobEntity::getStatus, "FAILED")
                .ge(ImportJobEntity::getCreatedTime, audit.now().minusHours(24)));
        return thresholdZero("IMPORT_FAILED_RECENT", "最近 24 小时失败导入任务", count,
                "最近 24 小时存在失败导入任务");
    }

    private BusinessHealthCheckResponse negativeInventoryCheck(AuditMetadata audit) {
        long count = inventoryBalanceMapper.selectCount(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, audit.companyId())
                .eq(InventoryBalanceEntity::getAccountBookId, audit.accountBookId())
                .lt(InventoryBalanceEntity::getQtyOnHand, BigDecimal.ZERO));
        return thresholdZero("NEGATIVE_INVENTORY_BALANCE", "负库存余额", count,
                "存在负库存余额");
    }

    private BusinessHealthCheckResponse openPeriodCheck(AuditMetadata audit) {
        long count = accountPeriodMapper.selectCount(new LambdaQueryWrapper<AccountPeriodEntity>()
                .eq(AccountPeriodEntity::getCompanyId, audit.companyId())
                .eq(AccountPeriodEntity::getAccountBookId, audit.accountBookId())
                .eq(AccountPeriodEntity::getStatus, "OPEN"));
        String status = count == 0 ? STATUS_WARN : STATUS_UP;
        String summary = count == 0 ? "当前账套没有开放会计期间" : "存在开放会计期间";
        return new BusinessHealthCheckResponse("OPEN_PERIOD_COUNT", "开放会计期间数量", status, count, 1, summary);
    }

    private BusinessHealthCheckResponse thresholdZero(String code, String name, long count, String warnSummary) {
        String status = count > 0 ? STATUS_WARN : STATUS_UP;
        String summary = count > 0 ? warnSummary : "未发现异常";
        return new BusinessHealthCheckResponse(code, name, status, count, 0, summary);
    }
}
```

- [ ] **Step 5: Run tests and verify they pass**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityBusinessHealthServiceTest" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/tuowei/erp/system/observability src/test/java/com/tuowei/erp/system/observability/ObservabilityBusinessHealthServiceTest.java
git commit -m "feat: add business health summary service"
```

---

### Task 3: Business Health API, Permission, and Menu Seed

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Create: `src/main/java/com/tuowei/erp/system/observability/controller/ObservabilityController.java`
- Create: `src/main/resources/db/migration/V60__system_observability_menu_seed.sql`
- Test: `src/test/java/com/tuowei/erp/system/observability/ObservabilityControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Create `src/test/java/com/tuowei/erp/system/observability/ObservabilityControllerTest.java`:

```java
package com.tuowei.erp.system.observability;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessHealthRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/system/observability/business-health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithErpUser(authorities = "system:profile:view")
    void businessHealthRequiresObservabilityPermission() throws Exception {
        mockMvc.perform(get("/api/system/observability/business-health"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities = "system:observability:view")
    void businessHealthReturnsSummaryForAuthorizedUser() throws Exception {
        mockMvc.perform(get("/api/system/observability/business-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallStatus").exists())
                .andExpect(jsonPath("$.data.generatedAt").exists())
                .andExpect(jsonPath("$.data.checks.length()").value(4))
                .andExpect(jsonPath("$.data.checks[0].code").value("READINESS_UNPASSED_P0_P1"));
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityControllerTest" test
```

Expected: FAIL because controller and permission do not exist.

- [ ] **Step 3: Add permission constants**

In `PermissionCodes.java`, add near system permissions:

```java
public static final String SYSTEM_OBSERVABILITY_VIEW = "system:observability:view";
```

And near `HAS_SYSTEM_*` constants:

```java
public static final String HAS_SYSTEM_OBSERVABILITY_VIEW = "hasAuthority('" + SYSTEM_OBSERVABILITY_VIEW + "')";
```

- [ ] **Step 4: Add controller**

Create `src/main/java/com/tuowei/erp/system/observability/controller/ObservabilityController.java`:

```java
package com.tuowei.erp.system.observability.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.system.observability.service.ObservabilityBusinessHealthService;
import com.tuowei.erp.system.observability.web.BusinessHealthResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/observability")
public class ObservabilityController {

    private final ObservabilityBusinessHealthService businessHealthService;

    public ObservabilityController(ObservabilityBusinessHealthService businessHealthService) {
        this.businessHealthService = businessHealthService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_OBSERVABILITY_VIEW)
    @GetMapping("/business-health")
    public ApiResponse<BusinessHealthResponse> businessHealth() {
        return ApiResponse.success(businessHealthService.current());
    }
}
```

- [ ] **Step 5: Add menu seed migration**

Create `src/main/resources/db/migration/V60__system_observability_menu_seed.sql`:

```sql
INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag,
 status, deleted_flag, created_by, updated_by, version)
VALUES
    (5095, 5000, 'BUTTON', 'SYSTEM_OBSERVABILITY_VIEW', '可观测性查看', NULL, NULL,
     'system:observability:view', 95, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag),
    updated_by = VALUES(updated_by);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7133, 3002, 5095, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
```

- [ ] **Step 6: Run tests and verify they pass**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityControllerTest,FlywayMigrationSmokeTest" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/security/PermissionCodes.java src/main/java/com/tuowei/erp/system/observability/controller src/main/resources/db/migration/V60__system_observability_menu_seed.sql src/test/java/com/tuowei/erp/system/observability/ObservabilityControllerTest.java
git commit -m "feat: expose business health endpoint"
```

---

### Task 4: Acceptance Scripts and Documentation

**Files:**
- Modify: `scripts/preprod-acceptance.ps1`
- Modify: `scripts/business-smoke.ps1`
- Modify: `docs/production-deployment.md`
- Modify: `docs/business-readiness-checklist.md`
- Test: `src/test/java/com/tuowei/erp/common/config/ObservabilityAcceptanceScriptConfigurationTest.java`

- [ ] **Step 1: Write failing script and documentation tests**

Create `src/test/java/com/tuowei/erp/common/config/ObservabilityAcceptanceScriptConfigurationTest.java`:

```java
package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAcceptanceScriptConfigurationTest {

    @Test
    void preproductionAcceptanceChecksPrometheusAndBusinessHealth() throws IOException {
        String script = Files.readString(Path.of("scripts", "preprod-acceptance.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("/actuator/prometheus")
                .contains("/api/system/observability/business-health");
    }

    @Test
    void businessSmokeChecksBusinessHealthEndpoint() throws IOException {
        String script = Files.readString(Path.of("scripts", "business-smoke.ps1"), StandardCharsets.UTF_8);

        assertThat(script)
                .contains("/api/system/observability/business-health");
    }

    @Test
    void productionDocsMentionObservabilityEndpoints() throws IOException {
        String deployment = Files.readString(Path.of("docs", "production-deployment.md"), StandardCharsets.UTF_8);
        String checklist = Files.readString(Path.of("docs", "business-readiness-checklist.md"), StandardCharsets.UTF_8);

        assertThat(deployment)
                .contains("/actuator/prometheus")
                .contains("/api/system/observability/business-health");
        assertThat(checklist)
                .contains("/actuator/prometheus")
                .contains("/api/system/observability/business-health");
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityAcceptanceScriptConfigurationTest" test
```

Expected: FAIL because scripts/docs do not mention the new endpoints.

- [ ] **Step 3: Update preproduction script**

In `scripts/preprod-acceptance.ps1`, after the existing authenticated profile check, add:

```powershell
Add-WebSection "Prometheus metrics" "/actuator/prometheus" $authHeaders
Add-WebSection "Business health summary" "/api/system/observability/business-health" $authHeaders
```

Use the same `$authHeaders` variable already used for `/api/system/profile`.

- [ ] **Step 4: Update business smoke script**

In `scripts/business-smoke.ps1`, add this read-only endpoint to the protected GET checks:

```powershell
@{ Title = "Business health summary"; Path = "/api/system/observability/business-health" }
```

- [ ] **Step 5: Update production deployment docs**

In `docs/production-deployment.md`, add a short section near health checks:

```markdown
## 可观测性检查

生产 profile 暴露 `/actuator/prometheus`，用于 Prometheus 以认证方式抓取 JVM、HTTP、Tomcat、Hikari 和 Redis 等基础指标。该端点不得匿名公开；生产环境应通过内网、网关认证或抓取侧凭证控制访问。

上线前还要用已登录账号访问 `/api/system/observability/business-health`，确认 readiness、导入失败、负库存和开放会计期间摘要能返回。该接口只返回聚合数量和状态，不替代具体业务验收。
```

- [ ] **Step 6: Update business readiness checklist**

In `docs/business-readiness-checklist.md`, add these checks under Go / No-Go or core smoke:

```markdown
- 指标出口 `/actuator/prometheus` 已通过认证访问，响应包含 Prometheus 文本指标。
- 业务健康摘要 `/api/system/observability/business-health` 已通过认证访问，返回 readiness、导入、库存和期间检查项。
```

- [ ] **Step 7: Run tests and verify they pass**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityAcceptanceScriptConfigurationTest" test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add scripts/preprod-acceptance.ps1 scripts/business-smoke.ps1 docs/production-deployment.md docs/business-readiness-checklist.md src/test/java/com/tuowei/erp/common/config/ObservabilityAcceptanceScriptConfigurationTest.java
git commit -m "docs: add observability acceptance checks"
```

---

### Task 5: Final Verification

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run focused observability tests**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityConfigurationTest,ObservabilityBusinessHealthServiceTest,ObservabilityControllerTest,ObservabilityAcceptanceScriptConfigurationTest,FlywayMigrationSmokeTest" test
```

Expected: PASS with 0 failures.

- [ ] **Step 2: Run default test suite**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test
```

Expected: PASS with 0 failures.

- [ ] **Step 3: Run release gate**

Run:

```powershell
.\scripts\release-check.ps1
```

Expected: PASS. Report should show `status=PASSED`, `allowDirtyWorktree=false`, and `includeTestcontainers=false`.

- [ ] **Step 4: Confirm clean worktree**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## master
```

- [ ] **Step 5: Report Docker/Testcontainers boundary**

If Docker is unavailable on this machine, do not run `-IncludeTestcontainers`. Report that final preproduction verification still requires:

```powershell
.\scripts\release-check.ps1 -IncludeTestcontainers
.\scripts\preprod-full-acceptance.ps1 -EnvFile .env.prod -BaseUrl http://127.0.0.1:8080 -Username admin -Password "<预生产密码>" -WarehouseId <活跃仓库ID> -MaterialWarehouseId <活跃材料仓库ID> -FinishedWarehouseId <活跃成品仓库ID> -BusinessDate "<开放期间日期>"
```

---

## Self-Review

- Spec coverage: Prometheus dependency/config, protected business health endpoint, tenant-scoped aggregation, scripts, docs, and verification are covered.
- Placeholder scan: no placeholder patterns remain.
- Type consistency: DTO names, service name, endpoint path, permission code, and check codes match across tasks.
