# Frontend/Backend Hardening Backlog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the highest-risk cross-repo gaps between `E:\tuowei\python\erp-frontend` and `E:\tuowei\python\erpServer`, then sequence medium-term maintainability work with clear priorities.

**Architecture:** Treat this file as a master backlog, not a one-shot mega refactor. Execute P0 runtime-governance items first to close permission, navigation, test, and data-loading gaps; then ship missing frontend entrypoints and clean stale documentation; finally move into shared-component adoption, contract generation, and large-file refactors with smaller dedicated execution plans.

**Tech Stack:** Vue 3, TypeScript, Vite, Element Plus, Pinia, Vitest, GitHub Actions, Spring Boot 3.5, MyBatis-Plus, Maven Wrapper, springdoc OpenAPI.

---

## Baseline

- Frontend baseline already verified on 2026-07-08:
  - `npm run lint`
  - `npm run type-check`
  - `npm run build`
  - `npm run check:contracts`
- Backend baseline already verified on 2026-07-08:
  - `.\mvnw.cmd -B test`
  - Result: `884` tests, `0` failures.
- Do not repeat full-suite verification until a backlog item enters implementation. For doc-only work, run only link/path/content consistency checks.

## Scope Guardrails

- This backlog spans two repositories:
  - Frontend: `E:\tuowei\python\erp-frontend`
  - Backend: `E:\tuowei\python\erpServer`
- This file is for prioritization and execution sequencing.
- Any backlog item that touches one mega-file or more than three functional areas should be split into its own dedicated implementation plan before coding.
- Do not spend time on false gaps that are already closed:
  - Sequence rules are already wired in `..\erp-frontend\src\views\system\configs\index.vue`.
  - Inventory reservations are already exposed in `..\erp-frontend\src\views\inventory\stocks\index.vue`.
  - Business timeline is already surfaced in `..\erp-frontend\src\views\reports\traces\index.vue`.
  - `ProfileController` is low-value and should not enter the queue ahead of runtime governance work.

## File Structure

- Runtime authorization and navigation:
  - Modify `..\erp-frontend\src\views\**\index.vue`
  - Modify `..\erp-frontend\src\layout\index.vue`
  - Review `..\erp-frontend\src\directives\permission.ts`
  - Review `..\erp-frontend\src\store\modules\user.ts`
  - Review `..\erp-frontend\src\router\index.ts`
  - Review backend menu and role sources:
    - `src\main\java\com\tuowei\erp\system\menu\controller\MenuController.java`
    - `src\main\java\com\tuowei\erp\system\menu\service\MenuService.java`
    - `src\main\java\com\tuowei\erp\system\role\controller\RoleController.java`
- Frontend quality gates:
  - Modify `..\erp-frontend\package.json`
  - Modify `..\erp-frontend\vite.config.ts`
  - Create `..\erp-frontend\src\test\**`
  - Create `..\erp-frontend\.github\workflows\frontend-verify.yml`
- Lookup and option loading:
  - Modify `..\erp-frontend\src\api\system.ts`
  - Modify `..\erp-frontend\src\api\workflow.ts`
  - Modify high-risk pages:
    - `..\erp-frontend\src\views\finance\payments\index.vue`
    - `..\erp-frontend\src\views\sales\orders\index.vue`
    - `..\erp-frontend\src\views\sales\deliveries\index.vue`
    - `..\erp-frontend\src\views\purchase\orders\index.vue`
    - `..\erp-frontend\src\views\purchase\receipts\index.vue`
    - `..\erp-frontend\src\views\inventory\alerts\index.vue`
    - `..\erp-frontend\src\views\inventory\checks\index.vue`
    - `..\erp-frontend\src\views\inventory\replenishment-suggestions\index.vue`
- Missing frontend entrypoints:
  - Modify `..\erp-frontend\src\api\system.ts`
  - Modify `..\erp-frontend\src\router\index.ts`
  - Create or extend `..\erp-frontend\src\views\system\**`
  - Review backend source:
    - `src\main\java\com\tuowei\erp\common\document\DocumentStateRuleController.java`
    - `src\main\java\com\tuowei\erp\common\document\DocumentStateRuleService.java`
- Documentation cleanup:
  - Modify or archive stale frontend docs:
    - `..\erp-frontend\docs\frontend-backend-integration-report.md`
    - `..\erp-frontend\docs\frontend-pages-supplement.md`
  - Modify or archive stale backend docs:
    - `README.md`
    - `测试报告.md`
    - `test_report_20260616_101054.md`
    - `docs\frontend-progress-update.md`
    - `docs\system-analysis-and-optimization.md`
    - `docs\BACKEND_FRONTEND_GAP_ANALYSIS.md`
- Medium-term refactors:
  - Frontend hot files:
    - `..\erp-frontend\src\views\inventory\stocks\index.vue`
    - `..\erp-frontend\src\views\production\orders\index.vue`
    - `..\erp-frontend\src\views\system\readiness\index.vue`
    - `..\erp-frontend\src\api\inventory.ts`
    - `..\erp-frontend\src\api\finance.ts`
  - Backend hot files:
    - `src\main\java\com\tuowei\erp\purchase\order\service\PurchaseOrderService.java`
    - `src\main\java\com\tuowei\erp\report\service\ReportQueryService.java`
    - `src\main\java\com\tuowei\erp\system\readiness\service\ReadinessService.java`

---

### Task 1: Runtime Permission Closure

**Priority:** P0

**Files:**
- Audit: `..\erp-frontend\src\views\**\index.vue`
- Modify first-wave pages:
  - `..\erp-frontend\src\views\system\users\index.vue`
  - `..\erp-frontend\src\views\system\roles\index.vue`
  - `..\erp-frontend\src\views\finance\payments\index.vue`
  - `..\erp-frontend\src\views\production\orders\index.vue`
- Review supporting files:
  - `..\erp-frontend\src\directives\permission.ts`
  - `..\erp-frontend\src\store\modules\user.ts`
  - `..\erp-frontend\src\router\index.ts`

**Why now:** There are `52` business `index.vue` pages, but only `11` currently use `v-permission`. Runtime write actions are still visible on many pages that already have backend action-level permissions.

**Risk:** If permission keys are mapped carelessly, buttons may disappear for legitimate users or remain exposed for unauthorized users.

**Expected Benefit:** The UI stops advertising actions users cannot perform, reducing authorization confusion and accidental 403-heavy flows.

- [ ] Build a page-action-permission matrix for all `52` business pages, starting with the four first-wave high-risk pages above.
- [ ] For each write action button, align the frontend permission key with the backend controller action instead of reusing route-level `view` permissions.
- [ ] Add `v-permission` to mutation actions before touching cosmetics or page refactors.
- [ ] Re-scan the remaining `41` pages without `v-permission` and classify them into:
  - write-capable pages that must be fixed now
  - view-only pages that can stay as-is
  - pages that need a dedicated follow-up plan
- [ ] Verify admin and restricted-role behavior manually before closing the item.
- [ ] Re-run `npm run lint`, `npm run type-check`, and `npm run build`.

**Acceptance:**
- No write-capable page exposes create/update/delete/approve/cancel/enable/disable actions without a permission guard.
- Route-level `meta.permission` remains the view gate; button-level permissions cover action granularity.
- The page-action-permission matrix is saved alongside the implementation notes.

### Task 2: Dynamic Menu Runtime Closure

**Priority:** P0

**Files:**
- Modify `..\erp-frontend\src\layout\index.vue`
- Modify `..\erp-frontend\src\router\index.ts`
- Modify or extend `..\erp-frontend\src\api\system.ts`
- Optional create: `..\erp-frontend\src\store\modules\menu.ts`
- Review backend sources:
  - `src\main\java\com\tuowei\erp\system\menu\controller\MenuController.java`
  - `src\main\java\com\tuowei\erp\system\menu\service\MenuService.java`
  - `src\main\java\com\tuowei\erp\system\role\controller\RoleController.java`

**Why now:** The backend already supports menu tree maintenance and role-menu assignment, but the frontend sidebar still renders from static `router.options.routes`.

**Risk:** Runtime menu data may not match frontend route/component definitions. A poor fallback design can create blank pages or inaccessible routes.

**Expected Benefit:** Role-menu assignments become real runtime behavior instead of admin-only decoration.

- [ ] Define the runtime source of truth: backend menu tree controls sidebar visibility; frontend router remains the component registry and last-line guard.
- [ ] Normalize backend menu nodes to frontend navigation data without breaking existing route guards.
- [ ] Replace static sidebar generation in `layout/index.vue` with server-driven menu data.
- [ ] Decide fallback behavior for malformed menu records:
  - missing component mapping
  - hidden menu records
  - menu items pointing to routes the frontend no longer ships
- [ ] Ensure role-menu changes become visible after re-login or refresh.
- [ ] Re-run `npm run lint`, `npm run type-check`, and `npm run build`.

**Acceptance:**
- Sidebar content reflects backend menu assignments instead of static route enumeration.
- Removing a menu from a role removes it from runtime navigation after session refresh.
- Unknown or malformed menu records fail safely and do not break the layout shell.

### Task 3: Frontend Tests and CI Gate

**Priority:** P0

**Files:**
- Modify `..\erp-frontend\package.json`
- Modify `..\erp-frontend\vite.config.ts`
- Create `..\erp-frontend\src\test\setup.ts`
- Create `..\erp-frontend\src\**\*.test.ts`
- Create `..\erp-frontend\.github\workflows\frontend-verify.yml`

**Why now:** The backend already has a CI gate and a large automated suite; the frontend currently has neither a test script nor a GitHub Actions workflow.

**Risk:** If tests target unstable UI details first, the initial suite will be noisy and ignored. Start with pure logic, normalization, and store behavior.

**Expected Benefit:** Frontend regressions stop landing silently, especially around auth, request normalization, pagination, and permission-sensitive behavior.

- [ ] Add a minimal unit-test runner and scripts for the frontend repository.
- [ ] Cover first-stage low-risk/high-value targets:
  - auth response normalization
  - request and page normalization helpers
  - user store login/logout and permission state
  - selected shared formatters
- [ ] Add a frontend GitHub Actions workflow that runs:
  - `npm ci`
  - `npm run lint`
  - `npm run type-check`
  - `npm run build`
  - frontend unit tests
  - `npm run check:contracts`
- [ ] Document local verification commands in the frontend repo once the scripts exist.
- [ ] Keep browser E2E optional until seeded data and environment stability improve.

**Acceptance:**
- Frontend repository exposes a runnable test script.
- A failing frontend test or broken build blocks CI.
- First-stage tests focus on deterministic logic rather than brittle page snapshots.

### Task 4: Replace `pageSize: 1000` Lookup Loading

**Priority:** P0

**Files:**
- Modify API callers:
  - `..\erp-frontend\src\api\system.ts`
  - `..\erp-frontend\src\api\workflow.ts`
- Modify first-wave pages:
  - `..\erp-frontend\src\views\finance\payments\index.vue`
  - `..\erp-frontend\src\views\sales\orders\index.vue`
  - `..\erp-frontend\src\views\sales\deliveries\index.vue`
  - `..\erp-frontend\src\views\purchase\orders\index.vue`
  - `..\erp-frontend\src\views\purchase\receipts\index.vue`
  - `..\erp-frontend\src\views\inventory\alerts\index.vue`
  - `..\erp-frontend\src\views\inventory\checks\index.vue`
  - `..\erp-frontend\src\views\inventory\replenishment-suggestions\index.vue`

**Why now:** Current lookup behavior assumes datasets stay small forever. Once customers, suppliers, products, or warehouses grow, these pages will get slower and noisier.

**Risk:** Some existing backend list endpoints may not yet expose the search fields the UI needs. Extending them carelessly can destabilize list queries.

**Expected Benefit:** Lookup dialogs and selects stay responsive under real data volumes.

- [ ] Inventory every current `pageSize: 1000` usage and classify it as:
  - lookup-select shortcut
  - table/list query
  - temporary compatibility hack
- [ ] For lookup-select cases, move to remote search, small page size, debounce, and explicit loading states.
- [ ] Reuse existing list endpoints where possible; only extend backend query parameters when the current filter surface is genuinely insufficient.
- [ ] Preserve current happy-path UX for small datasets while removing full-table fetch assumptions.
- [ ] Re-run `npm run lint`, `npm run type-check`, `npm run build`, and targeted backend tests only if backend query contracts change.

**Acceptance:**
- No lookup-select flow depends on `pageSize: 1000`.
- Large-tenant option lists remain usable without full preload.
- Empty-state, loading-state, and no-match behavior are explicit in the UI.

### Task 5: Add a Frontend Entry Point for `document-state-rules`

**Priority:** P1

**Files:**
- Modify `..\erp-frontend\src\api\system.ts`
- Modify `..\erp-frontend\src\router\index.ts`
- Create or extend `..\erp-frontend\src\views\system\**`
- Review backend source:
  - `src\main\java\com\tuowei\erp\common\document\DocumentStateRuleController.java`
  - `src\main\java\com\tuowei\erp\common\document\DocumentStateRuleService.java`
  - `src\test\java\com\tuowei\erp\common\document\DocumentStateRuleControllerTest.java`

**Why now:** The backend already exposes a stable read API with tests, but the frontend currently offers no entry point for business users or admins to inspect the rule matrix.

**Risk:** Choosing the wrong IA location will bury the feature or duplicate existing system-config surfaces.

**Expected Benefit:** Rule visibility improves supportability and reduces “why can’t this document move state” confusion.

- [ ] Decide the frontend placement before coding:
  - recommended: a read-only system-management entry
  - acceptable: a tab under an existing system configuration screen
- [ ] Add frontend types and API wiring for the rule list response.
- [ ] Render the matrix with clear columns for document type, action, allowed source states, target state, and notes.
- [ ] Gate the page with a view permission aligned to system/admin visibility.
- [ ] Re-run `npm run lint`, `npm run type-check`, and `npm run build`.

**Acceptance:**
- Users can view the document state rule matrix from the frontend.
- No backend code changes are required unless permission naming or response shape gaps are discovered.
- The entry point is discoverable from the system area instead of hiding behind a raw URL.

### Task 6: Clean Stale Reports and Documentation Debt

**Priority:** P1

**Files:**
- Frontend docs:
  - `..\erp-frontend\docs\frontend-backend-integration-report.md`
  - `..\erp-frontend\docs\frontend-pages-supplement.md`
- Backend docs and reports:
  - `README.md`
  - `测试报告.md`
  - `test_report_20260616_101054.md`
  - `docs\frontend-progress-update.md`
  - `docs\system-analysis-and-optimization.md`
  - `docs\BACKEND_FRONTEND_GAP_ANALYSIS.md`

**Why now:** Several current-facing docs still claim implemented features are missing and even disagree on the local admin password. That wastes engineering time and sends people chasing ghosts.

**Risk:** Removing historical files without labeling may erase useful audit history. Archive or relabel them instead of blind deletion.

**Expected Benefit:** Future work starts from a trustworthy source of truth instead of stale status reports.

- [ ] Create a source-of-truth matrix for:
  - implemented frontend features
  - live backend endpoints
  - local default credentials and override env vars
  - evidence date for each statement
- [ ] Fix or archive docs that still claim sequence rules, inventory reservations, finance periods, or finance funds are missing when they already exist.
- [ ] Remove or relabel outdated credential references that still say `admin123` instead of `LocalAdmin123`.
- [ ] Mark failed historical test reports as historical snapshots, not current delivery status.
- [ ] Add a short “last verified” stamp to high-traffic status docs.

**Acceptance:**
- No current-facing doc still claims sequence rules or inventory reservations are missing.
- Local default admin credentials are described consistently.
- Historical failure reports are clearly labeled as historical, archived, or replaced.

### Task 7: Expand Common Table and Preference Infrastructure

**Priority:** P2

**Files:**
- Review shared frontend infrastructure:
  - `..\erp-frontend\src\components\common\PageTable.vue`
  - `..\erp-frontend\src\composables\useTablePreference.ts`
- First rollout candidates:
  - `..\erp-frontend\src\views\system\users\index.vue`
  - `..\erp-frontend\src\views\system\roles\index.vue`
  - `..\erp-frontend\src\views\finance\payments\index.vue`
  - `..\erp-frontend\src\views\production\orders\index.vue`

**Why now:** Only `7` business pages currently use the shared common layer, so the team keeps rewriting the same table, filter, and pagination glue.

**Risk:** Forcing giant pages into the shared abstraction too early will create a worse abstraction and more churn.

**Expected Benefit:** Faster page delivery, more consistent filters/pagination, and less copy-paste drift.

- [ ] Define a rollout batch of low-to-medium complexity pages before touching the biggest composite screens.
- [ ] Standardize which concerns belong in the common layer:
  - toolbar
  - query reset
  - pagination binding
  - column preference persistence
- [ ] Migrate one batch, measure friction, then refine the shared layer before the next batch.
- [ ] Keep page-specific business dialogs and workflows outside the common table abstraction.

**Acceptance:**
- The first rollout batch uses the shared layer without increasing page complexity.
- New list pages default to the common table path unless there is a documented exception.
- Shared preference behavior is consistent across adopted pages.

### Task 8: Replace Heavy Contract Checking with OpenAPI-Driven Generation

**Current status (2026-07-22):** 产品主数据试点已完成。后端版本化契约为
`docs/openapi/product-api.json`，前端生成类型为 `src/api/generated/product.ts`；
`npm run check:contracts` 已包含生成漂移检查。其余模块按需渐进迁移，不阻塞本任务验收。

**Priority:** P2

**Files:**
- Review backend OpenAPI surface:
  - `pom.xml`
  - `src\main\resources\application-local.yml`
  - `src\main\resources\application-dev.yml`
- Modify frontend build tooling:
  - `..\erp-frontend\package.json`
  - `..\erp-frontend\scripts\**`
  - optional generated client directory under `..\erp-frontend\src\api\generated\**`
- Review current heavy script:
  - `..\erp-frontend\scripts\check-production-order-warehouse-contract.mjs`

**Why now:** The backend already exposes springdoc OpenAPI, but the frontend still carries a `2833`-line string-heavy contract check script.

**Risk:** Full generation across the whole API in one pass may create noisy diffs and low trust. Start with one or two modules first.

**Expected Benefit:** Contract drift becomes schema-driven, reproducible, and less dependent on brittle hand-maintained string checks.

- [x] Choose the generation approach before implementation:
  - recommended: generated types plus a thin request wrapper
  - acceptable: generated full client for a narrow module subset first
- [x] Export and version a stable OpenAPI artifact from the backend.
- [x] Generate frontend client/types for a pilot surface before broad rollout.
- [x] Replace or shrink the monolithic manual contract script once the pilot proves reliable.
- [x] Add CI verification so schema drift is visible in pull requests.

**Acceptance:**
- At least one frontend API surface is generated from backend OpenAPI instead of hand-maintained contracts.
- The current monolithic contract script is reduced, replaced, or explicitly scoped down.
- Contract validation becomes reproducible in CI.

### Task 9: Split Hot Files and Narrow `keep-alive`

**Priority:** P2

**Files:**
- Frontend hot spots:
  - `..\erp-frontend\src\views\inventory\stocks\index.vue`
  - `..\erp-frontend\src\views\production\orders\index.vue`
  - `..\erp-frontend\src\views\system\readiness\index.vue`
  - `..\erp-frontend\src\api\inventory.ts`
  - `..\erp-frontend\src\layout\index.vue`
- Backend hot spots:
  - `src\main\java\com\tuowei\erp\purchase\order\service\PurchaseOrderService.java`
  - `src\main\java\com\tuowei\erp\report\service\ReportQueryService.java`
  - `src\main\java\com\tuowei\erp\system\readiness\service\ReadinessService.java`

**Why now:** Several front and back files are already in the “anything you touch wakes up three unrelated behaviors” zone. The current global `keep-alive` also risks stale state accumulation.

**Risk:** A large refactor without clear boundaries will burn time and destabilize unrelated features.

**Expected Benefit:** Smaller blast radius, clearer ownership, and less stale-state debugging.

- [ ] Rank hot files by churn, bug frequency, and upcoming feature pressure before refactoring.
- [ ] Narrow `keep-alive` from global-on to explicit route-level opt-in.
- [ ] Split frontend mega-pages by responsibility:
  - query/table shell
  - dialogs
  - derived business calculations
  - reusable composables
- [ ] Split backend services by command/query or domain-helper boundaries instead of arbitrary “utils” dumping.
- [ ] Write a dedicated execution plan before touching any file in this task that is above `500` lines.

**Acceptance:**
- `keep-alive` is explicit instead of blanket-applied.
- Any hot-file refactor enters implementation only with a dedicated sub-plan.
- Newly touched modules move toward smaller, responsibility-focused units.

---

## Recommended Execution Order

1. Task 1 `Runtime Permission Closure`
2. Task 2 `Dynamic Menu Runtime Closure`
3. Task 3 `Frontend Tests and CI Gate`
4. Task 4 `Replace pageSize: 1000 Lookup Loading`
5. Task 5 `Add a Frontend Entry Point for document-state-rules`
6. Task 6 `Clean Stale Reports and Documentation Debt`
7. Task 7 `Expand Common Table and Preference Infrastructure`
8. Task 8 `Replace Heavy Contract Checking with OpenAPI-Driven Generation`
9. Task 9 `Split Hot Files and Narrow keep-alive`

## Parallelism Guidance

- Tasks `1` and `6` can run in parallel if one person owns runtime behavior and another owns documentation cleanup.
- Task `3` can start once Task `1` identifies the highest-value frontend logic to lock with tests.
- Task `4` should start after Task `3` establishes a basic frontend safety net.
- Tasks `7`, `8`, and `9` should wait until P0 and P1 items stop moving the ground under them.

## Pull Rules

- Do not start Task `7`, `8`, or `9` before Tasks `1` to `4` are stable.
- For any cross-repo item, update frontend and backend verification notes together.
- For any doc-only item, prefer archive labels and timestamps over silent deletion.
- When a backlog item is pulled into active execution, write a smaller dedicated implementation plan if the change surface is not obvious from this file.
