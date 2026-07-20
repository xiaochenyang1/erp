# Readiness Acceptance Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a backend-only readiness acceptance center for release validation runs, items, evidence, and Go / No-Go decisions.

**Architecture:** Add a focused `system/readiness` module with three tables: run, item, and evidence. Keep release governance separate from business posting services; the module stores validation facts and references, enforces tenant isolation, and blocks Go decisions when P0/P1 items are failed or blocked.

**Tech Stack:** Spring Boot, Spring Security `@PreAuthorize`, MyBatis-Plus, Flyway SQL migrations, H2/MySQL-compatible schema, MockMvc, JUnit 5.

---

## File Map

**Create**

- `src/main/resources/db/migration/V52__system_readiness_schema.sql`：readiness 表结构、索引、菜单权限初始化。
- `src/main/java/com/tuowei/erp/system/readiness/controller/ReadinessController.java`：REST API。
- `src/main/java/com/tuowei/erp/system/readiness/service/ReadinessService.java`：租户过滤、状态流转、决策规则。
- `src/main/java/com/tuowei/erp/system/readiness/mapper/ReadinessRunMapper.java`
- `src/main/java/com/tuowei/erp/system/readiness/mapper/ReadinessItemMapper.java`
- `src/main/java/com/tuowei/erp/system/readiness/mapper/ReadinessEvidenceMapper.java`
- `src/main/java/com/tuowei/erp/system/readiness/model/ReadinessRunEntity.java`
- `src/main/java/com/tuowei/erp/system/readiness/model/ReadinessItemEntity.java`
- `src/main/java/com/tuowei/erp/system/readiness/model/ReadinessEvidenceEntity.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessRunCreateRequest.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessRunPageQuery.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessRunResponse.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessRunDetailResponse.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessItemCreateRequest.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessItemResultRequest.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessItemResponse.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessEvidenceCreateRequest.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessEvidenceResponse.java`
- `src/main/java/com/tuowei/erp/system/readiness/web/ReadinessDecisionRequest.java`
- `src/test/java/com/tuowei/erp/system/readiness/ReadinessControllerTest.java`

**Modify**

- `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`：租户插件纳入三张 readiness 表。
- `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`：新增 readiness 权限码。

## Task 1: Schema, Permissions, And Tenant Registration

**Files:**

- Create: `src/main/resources/db/migration/V52__system_readiness_schema.sql`
- Modify: `src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Test: existing `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`

- [ ] **Step 1: Add failing permission expectations mentally and update permission constants first**

Modify `PermissionCodes.java` near existing system permissions:

```java
public static final String SYSTEM_READINESS_VIEW = "system:readiness:view";
public static final String SYSTEM_READINESS_MANAGE = "system:readiness:manage";
public static final String SYSTEM_READINESS_DECIDE = "system:readiness:decide";
```

Modify the `HAS_` section near `HAS_SYSTEM_NOTIFICATION_VIEW`:

```java
public static final String HAS_SYSTEM_READINESS_VIEW = "hasAuthority('" + SYSTEM_READINESS_VIEW + "')";
public static final String HAS_SYSTEM_READINESS_MANAGE = "hasAuthority('" + SYSTEM_READINESS_MANAGE + "')";
public static final String HAS_SYSTEM_READINESS_DECIDE = "hasAuthority('" + SYSTEM_READINESS_DECIDE + "')";
```

- [ ] **Step 2: Register readiness tables in tenant interceptor**

Modify `MybatisPlusConfig.java` `TENANT_TABLES`:

```java
"sys_readiness_run",
"sys_readiness_item",
"sys_readiness_evidence",
```

Place them near other `sys_*` tables.

- [ ] **Step 3: Create migration**

Create `V52__system_readiness_schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS sys_readiness_run (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_no VARCHAR(64) NOT NULL,
    release_commit VARCHAR(128) NOT NULL,
    release_version VARCHAR(128),
    environment VARCHAR(64) NOT NULL,
    database_instance VARCHAR(256),
    redis_instance VARCHAR(256),
    docker_profile VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    decision VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decision_comment VARCHAR(512),
    remark VARCHAR(512),
    started_by BIGINT NOT NULL,
    started_time TIMESTAMP NOT NULL,
    decided_by BIGINT,
    decided_time TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sys_readiness_run_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'PASSED', 'FAILED', 'BLOCKED', 'NO_GO')),
    CONSTRAINT chk_sys_readiness_run_decision CHECK (decision IN ('PENDING', 'GO', 'NO_GO'))
);

CREATE TABLE IF NOT EXISTS sys_readiness_item (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    expected_result VARCHAR(512),
    actual_result VARCHAR(512),
    failure_reason VARCHAR(512),
    executed_by BIGINT,
    executed_time TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sys_readiness_item_priority CHECK (priority IN ('P0', 'P1', 'P2')),
    CONSTRAINT chk_sys_readiness_item_status CHECK (status IN ('PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'))
);

CREATE TABLE IF NOT EXISTS sys_readiness_evidence (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    account_book_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    request_method VARCHAR(16),
    request_uri VARCHAR(512),
    http_status INT,
    business_type VARCHAR(64),
    business_id BIGINT,
    business_no VARCHAR(128),
    summary VARCHAR(256) NOT NULL,
    detail VARCHAR(2048),
    attachment_business_type VARCHAR(64),
    attachment_business_id BIGINT,
    recorded_by BIGINT NOT NULL,
    recorded_time TIMESTAMP NOT NULL,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sys_readiness_evidence_type CHECK (evidence_type IN ('API', 'BUSINESS_NO', 'LOG', 'SCREENSHOT', 'NOTE', 'ATTACHMENT'))
);

CREATE UNIQUE INDEX uk_sys_readiness_run_no
    ON sys_readiness_run (company_id, account_book_id, run_no);
CREATE INDEX idx_sys_readiness_run_commit
    ON sys_readiness_run (company_id, account_book_id, release_commit);
CREATE INDEX idx_sys_readiness_run_status_time
    ON sys_readiness_run (company_id, account_book_id, status, created_time);

CREATE INDEX idx_sys_readiness_item_run_status
    ON sys_readiness_item (company_id, account_book_id, run_id, priority, status);
CREATE INDEX idx_sys_readiness_item_code_time
    ON sys_readiness_item (company_id, account_book_id, item_code, created_time);

CREATE INDEX idx_sys_readiness_evidence_item_time
    ON sys_readiness_evidence (company_id, account_book_id, run_id, item_id, recorded_time);
CREATE INDEX idx_sys_readiness_evidence_business
    ON sys_readiness_evidence (company_id, account_book_id, business_type, business_id);

INSERT INTO sys_menu
(id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no, visible_flag, status, deleted_flag, created_by, updated_by, version)
VALUES
    (5064, 5001, 'MENU', 'SYSTEM_READINESS', '预生产验收', '/system/readiness', 'system/readiness/index', 'system:readiness:view', 11, 1, 'ACTIVE', 0, 0, 0, 0),
    (5065, 5064, 'BUTTON', 'SYSTEM_READINESS_MANAGE', '维护验收记录', '', '', 'system:readiness:manage', 1, 1, 'ACTIVE', 0, 0, 0, 0),
    (5066, 5064, 'BUTTON', 'SYSTEM_READINESS_DECIDE', '发布决策', '', '', 'system:readiness:decide', 2, 1, 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    visible_flag = VALUES(visible_flag),
    status = VALUES(status),
    deleted_flag = VALUES(deleted_flag);

INSERT INTO sys_role_menu
(id, role_id, menu_id, created_by)
VALUES
    (7124, 3002, 5064, 0),
    (7125, 3002, 5065, 0),
    (7126, 3002, 5066, 0)
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    menu_id = VALUES(menu_id);
```

- [ ] **Step 4: Run migration smoke test**

Run:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest" test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [ ] **Step 5: Commit schema and permissions**

Run:

```powershell
git add src/main/resources/db/migration/V52__system_readiness_schema.sql src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java src/main/java/com/tuowei/erp/common/security/PermissionCodes.java
git commit -m "feat: add readiness acceptance schema"
```

## Task 2: Entities, Mappers, And DTOs

**Files:**

- Create all `model`, `mapper`, and `web` files listed in File Map.
- Test: compilation via `.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest" test`

- [ ] **Step 1: Create entity classes**

Create `ReadinessRunEntity.java` with fields matching `sys_readiness_run`. Use:

```java
@TableName("sys_readiness_run")
public class ReadinessRunEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long companyId;
    private Long accountBookId;
    private String runNo;
    private String releaseCommit;
    private String releaseVersion;
    private String environment;
    private String databaseInstance;
    private String redisInstance;
    private String dockerProfile;
    private String status;
    private String decision;
    private String decisionComment;
    private String remark;
    private Long startedBy;
    private LocalDateTime startedTime;
    private Long decidedBy;
    private LocalDateTime decidedTime;
    private Integer deletedFlag;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer version;
}
```

Add explicit getters and setters like existing entities; do not use Lombok.

Create `ReadinessItemEntity.java` and `ReadinessEvidenceEntity.java` the same way, matching migration fields and using `@TableName`, `@TableId(type = IdType.ASSIGN_ID)`, and `@Version`.

- [ ] **Step 2: Create mapper classes**

Create:

```java
@Mapper
public interface ReadinessRunMapper extends BaseMapper<ReadinessRunEntity> {
}
```

Repeat for item and evidence mappers.

- [ ] **Step 3: Create request DTO records**

Create request records:

```java
public record ReadinessRunCreateRequest(
        String releaseCommit,
        String releaseVersion,
        String environment,
        String databaseInstance,
        String redisInstance,
        String dockerProfile,
        String remark
) {
}
```

```java
public record ReadinessItemCreateRequest(
        String itemCode,
        String itemName,
        String category,
        String priority,
        String expectedResult
) {
}
```

```java
public record ReadinessEvidenceCreateRequest(
        String evidenceType,
        String requestMethod,
        String requestUri,
        Integer httpStatus,
        String businessType,
        Long businessId,
        String businessNo,
        String summary,
        String detail,
        String attachmentBusinessType,
        Long attachmentBusinessId
) {
}
```

```java
public record ReadinessItemResultRequest(
        String status,
        String actualResult,
        String failureReason
) {
}
```

```java
public record ReadinessDecisionRequest(
        String decision,
        String status,
        String decisionComment
) {
}
```

- [ ] **Step 4: Create query and response DTOs**

Create `ReadinessRunPageQuery` as a JavaBean with `releaseCommit`, `environment`, `status`, `decision`, `createdTimeFrom`, `createdTimeTo`, `pageNo`, `pageSize`.

Create response records:

```java
public record ReadinessRunResponse(
        Long id,
        String runNo,
        String releaseCommit,
        String releaseVersion,
        String environment,
        String databaseInstance,
        String redisInstance,
        String dockerProfile,
        String status,
        String decision,
        String decisionComment,
        String remark,
        Long startedBy,
        LocalDateTime startedTime,
        Long decidedBy,
        LocalDateTime decidedTime,
        LocalDateTime createdTime
) {
}
```

```java
public record ReadinessRunDetailResponse(
        ReadinessRunResponse run,
        List<ReadinessItemResponse> items
) {
}
```

```java
public record ReadinessItemResponse(
        Long id,
        Long runId,
        String itemCode,
        String itemName,
        String category,
        String priority,
        String status,
        String expectedResult,
        String actualResult,
        String failureReason,
        Long executedBy,
        LocalDateTime executedTime,
        LocalDateTime createdTime,
        List<ReadinessEvidenceResponse> evidence
) {
}
```

```java
public record ReadinessEvidenceResponse(
        Long id,
        Long runId,
        Long itemId,
        String evidenceType,
        String requestMethod,
        String requestUri,
        Integer httpStatus,
        String businessType,
        Long businessId,
        String businessNo,
        String summary,
        String detail,
        String attachmentBusinessType,
        Long attachmentBusinessId,
        Long recordedBy,
        LocalDateTime recordedTime
) {
}
```

- [ ] **Step 5: Compile**

Run:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest" test
```

Expected: compile succeeds and smoke test passes.

- [ ] **Step 6: Commit entities and DTOs**

Run:

```powershell
git add src/main/java/com/tuowei/erp/system/readiness
git commit -m "feat: add readiness acceptance models"
```

## Task 3: Service Rules

**Files:**

- Create: `src/main/java/com/tuowei/erp/system/readiness/service/ReadinessService.java`
- Test: `src/test/java/com/tuowei/erp/system/readiness/ReadinessControllerTest.java` in later task

- [ ] **Step 1: Implement service skeleton**

Create constructor-injected service:

```java
@Service
public class ReadinessService {
    private static final String RUN_DRAFT = "DRAFT";
    private static final String RUN_IN_PROGRESS = "IN_PROGRESS";
    private static final String RUN_PASSED = "PASSED";
    private static final String RUN_FAILED = "FAILED";
    private static final String RUN_BLOCKED = "BLOCKED";
    private static final String RUN_NO_GO = "NO_GO";
    private static final String DECISION_PENDING = "PENDING";
    private static final String DECISION_GO = "GO";
    private static final String DECISION_NO_GO = "NO_GO";

    private final ReadinessRunMapper runMapper;
    private final ReadinessItemMapper itemMapper;
    private final ReadinessEvidenceMapper evidenceMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ReadinessService(
            ReadinessRunMapper runMapper,
            ReadinessItemMapper itemMapper,
            ReadinessEvidenceMapper evidenceMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.runMapper = runMapper;
        this.itemMapper = itemMapper;
        this.evidenceMapper = evidenceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }
}
```

- [ ] **Step 2: Implement create/list/detail**

Add methods:

```java
@Transactional
public ReadinessRunResponse createRun(ReadinessRunCreateRequest request)

public PageResponse<ReadinessRunResponse> listRuns(ReadinessRunPageQuery query)

public ReadinessRunDetailResponse detail(Long id)
```

Rules:

- `releaseCommit` and `environment` are required.
- Normalize status values with `trim().toUpperCase(Locale.ROOT)`.
- Generate `runNo` as `"RDY" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(now)`.
- All queries filter `companyId`, `accountBookId`, and `deletedFlag = 0`.
- `detail` loads items by run id and evidence by item ids.

- [ ] **Step 3: Implement item and evidence writes**

Add:

```java
@Transactional
public ReadinessItemResponse addItem(Long runId, ReadinessItemCreateRequest request)

@Transactional
public ReadinessEvidenceResponse addEvidence(Long itemId, ReadinessEvidenceCreateRequest request)
```

Rules:

- Run must exist in current tenant.
- Run must be `DRAFT` or `IN_PROGRESS`.
- Adding the first item to a `DRAFT` run should move run to `IN_PROGRESS`.
- `itemCode`, `itemName`, `category`, `priority` are required.
- `priority` must be `P0`, `P1`, or `P2`.
- Evidence `evidenceType` and `summary` are required.
- Evidence type must be one of `API`, `BUSINESS_NO`, `LOG`, `SCREENSHOT`, `NOTE`, `ATTACHMENT`.

- [ ] **Step 4: Implement item result**

Add:

```java
@Transactional
public ReadinessItemResponse markItemResult(Long itemId, ReadinessItemResultRequest request)
```

Rules:

- Item and run must exist in current tenant.
- Run must be open.
- Status must be `PASSED`, `FAILED`, `BLOCKED`, or `SKIPPED`.
- `P0` cannot be `SKIPPED`.
- `P1` skipped must have `failureReason`.
- Update `actualResult`, `failureReason`, `executedBy`, `executedTime`.

- [ ] **Step 5: Implement overall decision**

Add:

```java
@Transactional
public ReadinessRunResponse decide(Long runId, ReadinessDecisionRequest request)
```

Rules:

- `decision` must be `GO` or `NO_GO`.
- `NO_GO` requires `decisionComment`.
- `GO` requires request status `PASSED`.
- `GO` is blocked when any current-tenant item under run has priority `P0` or `P1` and status `FAILED` or `BLOCKED`.
- Closed statuses are `PASSED`, `FAILED`, `BLOCKED`, `NO_GO`; once closed, no further item/evidence writes.

- [ ] **Step 6: Add mapper-to-response helpers and validation helpers**

Add private helpers:

```java
private ReadinessRunEntity requireRun(Long id, AuditMetadata audit)
private ReadinessItemEntity requireItem(Long id, AuditMetadata audit)
private void assertRunOpen(ReadinessRunEntity run)
private String normalizeRequired(String value, String message)
private String normalizeCode(String value, String message)
private boolean hasBlockingP0P1Items(ReadinessRunEntity run)
private ReadinessRunResponse toRunResponse(ReadinessRunEntity entity)
private ReadinessItemResponse toItemResponse(ReadinessItemEntity entity, List<ReadinessEvidenceResponse> evidence)
private ReadinessEvidenceResponse toEvidenceResponse(ReadinessEvidenceEntity entity)
```

- [ ] **Step 7: Compile service**

Run:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest" test
```

Expected: compile succeeds.

- [ ] **Step 8: Commit service**

Run:

```powershell
git add src/main/java/com/tuowei/erp/system/readiness/service/ReadinessService.java
git commit -m "feat: add readiness acceptance service"
```

## Task 4: REST Controller

**Files:**

- Create: `src/main/java/com/tuowei/erp/system/readiness/controller/ReadinessController.java`
- Test: controller tests in Task 5

- [ ] **Step 1: Create controller**

Implement:

```java
@RestController
@RequestMapping("/api/system/readiness")
public class ReadinessController {
    private final ReadinessService readinessService;

    public ReadinessController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/runs")
    public ApiResponse<ReadinessRunResponse> createRun(@RequestBody ReadinessRunCreateRequest request) {
        return ApiResponse.success(readinessService.createRun(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_VIEW)
    @GetMapping("/runs")
    public ApiResponse<PageResponse<ReadinessRunResponse>> listRuns(ReadinessRunPageQuery query) {
        return ApiResponse.success(readinessService.listRuns(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_VIEW)
    @GetMapping("/runs/{id}")
    public ApiResponse<ReadinessRunDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(readinessService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/runs/{id}/items")
    public ApiResponse<ReadinessItemResponse> addItem(@PathVariable Long id, @RequestBody ReadinessItemCreateRequest request) {
        return ApiResponse.success(readinessService.addItem(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/items/{itemId}/evidence")
    public ApiResponse<ReadinessEvidenceResponse> addEvidence(@PathVariable Long itemId, @RequestBody ReadinessEvidenceCreateRequest request) {
        return ApiResponse.success(readinessService.addEvidence(itemId, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_MANAGE)
    @PostMapping("/items/{itemId}/result")
    public ApiResponse<ReadinessItemResponse> markItemResult(@PathVariable Long itemId, @RequestBody ReadinessItemResultRequest request) {
        return ApiResponse.success(readinessService.markItemResult(itemId, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_READINESS_DECIDE)
    @PostMapping("/runs/{id}/decision")
    public ApiResponse<ReadinessRunResponse> decide(@PathVariable Long id, @RequestBody ReadinessDecisionRequest request) {
        return ApiResponse.success(readinessService.decide(id, request));
    }
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationSmokeTest" test
```

Expected: compile succeeds.

- [ ] **Step 3: Commit controller**

Run:

```powershell
git add src/main/java/com/tuowei/erp/system/readiness/controller/ReadinessController.java
git commit -m "feat: expose readiness acceptance APIs"
```

## Task 5: Controller Tests

**Files:**

- Create: `src/test/java/com/tuowei/erp/system/readiness/ReadinessControllerTest.java`

- [ ] **Step 1: Write tests**

Create tests with `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`.

Required test methods:

```java
@Test
void createRunAddItemEvidenceAndQueryDetail() throws Exception
```

Checks:

- Login as user with role `3002`.
- `POST /api/system/readiness/runs` returns status `DRAFT`.
- `POST /api/system/readiness/runs/{id}/items` returns status `PENDING`.
- `POST /api/system/readiness/items/{itemId}/evidence` returns evidence summary.
- `GET /api/system/readiness/runs/{id}` returns one item and one evidence.

```java
@Test
void blockingP0FailurePreventsGoDecision() throws Exception
```

Checks:

- Create run.
- Add `P0` item.
- Mark item `FAILED`.
- `POST /api/system/readiness/runs/{id}/decision` with `GO/PASSED` returns business error containing `存在未通过的 P0/P1 验收项`.

```java
@Test
void noGoRequiresDecisionCommentAndClosedRunRejectsEvidence() throws Exception
```

Checks:

- `NO_GO` without comment returns business error containing `No-Go 决策说明不能为空`.
- `NO_GO` with comment succeeds.
- Adding evidence to the closed run item returns business error containing `已关闭的验收运行单不能修改`.

```java
@Test
void tenantIsolationPreventsCrossCompanyAccess() throws Exception
```

Checks:

- User A company `1` creates run.
- User B company `2` tries `GET /api/system/readiness/runs/{id}`.
- Response is business error containing `验收运行单不存在`.

```java
@Test
void userWithoutReadinessPermissionGetsForbidden() throws Exception
```

Checks:

- Seed active user without role menu permission.
- `GET /api/system/readiness/runs` returns `403`.

- [ ] **Step 2: Use deterministic seed helpers**

Follow the style of `NotificationControllerTest`:

```java
private void seedUser(long id, String username, long companyId, long accountBookId) {
    jdbcTemplate.update("""
            insert into sys_user
            (id, company_id, account_book_id, username, password, employee_no, real_name, dept_id, post_id,
             status, deleted_flag, remark, created_by, updated_by, version)
            values (?, ?, ?, ?, ?, ?, ?, 3501, 3601,
                    'ACTIVE', 0, 'readiness controller test', 0, 0, 0)
            """, id, companyId, accountBookId, username, passwordEncoder.encode(PASSWORD), "EMP_" + id, username);
}
```

Grant admin role:

```java
jdbcTemplate.update("insert into sys_user_role (id, user_id, role_id, created_by) values (?, ?, 3002, 0)", roleLinkId, userId);
```

Cleanup order:

```sql
delete from sys_readiness_evidence where company_id in (1, 2)
delete from sys_readiness_item where company_id in (1, 2)
delete from sys_readiness_run where company_id in (1, 2)
delete from sys_refresh_token where user_id in (...)
delete from sys_login_log where username in (...)
delete from sys_user_role where user_id in (...)
delete from sys_user where id in (...)
```

- [ ] **Step 3: Run focused test and confirm failures if implementation incomplete**

Run:

```powershell
.\mvnw.cmd "-Dtest=ReadinessControllerTest" test
```

Expected after implementation: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 4: Fix implementation until focused test passes**

Use only targeted edits in `ReadinessService`, DTOs, or controller. Do not loosen tests to fit broken behavior.

- [ ] **Step 5: Commit tests and fixes**

Run:

```powershell
git add src/test/java/com/tuowei/erp/system/readiness/ReadinessControllerTest.java src/main/java/com/tuowei/erp/system/readiness src/main/java/com/tuowei/erp/common/config/MybatisPlusConfig.java src/main/java/com/tuowei/erp/common/security/PermissionCodes.java src/main/resources/db/migration/V52__system_readiness_schema.sql
git commit -m "test: cover readiness acceptance center"
```

## Task 6: Full Verification And Cleanup

**Files:**

- Verify whole repository.

- [ ] **Step 1: Run focused readiness and migration tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=ReadinessControllerTest,FlywayMigrationSmokeTest" test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [ ] **Step 2: Run full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected:

```text
BUILD SUCCESS
Failures: 0, Errors: 0
```

- [ ] **Step 3: Check worktree**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## master
```

- [ ] **Step 4: If worktree is dirty, commit only readiness-related files**

Expected commit message if needed:

```powershell
git add <readiness-related-files>
git commit -m "feat: add readiness acceptance center"
```

## Self-Review

- Spec coverage: schema, permissions, tenant isolation, API surface, state transitions, decision rules, evidence records, and tests are covered by Tasks 1-6.
- Placeholder scan: no placeholder markers or vague edge-case instructions remain; every task lists concrete files and commands.
- Type consistency: API names and DTO names match the design document and remain under `system/readiness`.
