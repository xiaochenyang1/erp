# Observability Alerts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose ERP business-health Prometheus metrics and provide minimum alert rule templates.

**Architecture:** Register a focused Micrometer `MeterBinder` that maps the existing business-health service response into gauges. Add repository-owned Prometheus alert rule templates and extend script/documentation checks so the operational evidence includes metric names and alert rules.

**Tech Stack:** Spring Boot Actuator, Micrometer, SimpleMeterRegistry, Prometheus rule YAML, JUnit 5, AssertJ.

---

## File Map

- Create `src/main/java/com/tuowei/erp/system/observability/metrics/BusinessHealthMetricsBinder.java`: Micrometer gauge registration.
- Test `src/test/java/com/tuowei/erp/system/observability/BusinessHealthMetricsBinderTest.java`: metric values and tags.
- Create `docs/monitoring/prometheus-alert-rules.yml`: minimum Prometheus alert rules.
- Test `src/test/java/com/tuowei/erp/common/config/ObservabilityAlertRulesConfigurationTest.java`: rule template and docs coverage.
- Modify `docs/production-deployment.md`: document business metrics and alert template.
- Modify `docs/business-readiness-checklist.md`: add Go / No-Go evidence for metrics and rules.
- Modify `src/test/java/com/tuowei/erp/common/config/ObservabilityAcceptanceScriptConfigurationTest.java`: assert docs mention metrics and alert template.

---

### Task 1: Business Health Metrics Binder

**Files:**
- Create: `src/main/java/com/tuowei/erp/system/observability/metrics/BusinessHealthMetricsBinder.java`
- Test: `src/test/java/com/tuowei/erp/system/observability/BusinessHealthMetricsBinderTest.java`

- [ ] **Step 1: Write failing metrics test**

Create `src/test/java/com/tuowei/erp/system/observability/BusinessHealthMetricsBinderTest.java` with a mocked `ObservabilityBusinessHealthService`. Verify:

- `erp_business_health_overall_status` returns `1` when overall status is `WARN`.
- `erp_business_health_check_count{check="NEGATIVE_INVENTORY_BALANCE"}` returns the count.
- `erp_business_health_check_status{check="OPEN_PERIOD_COUNT"}` returns `1` when that check is `WARN`.

- [ ] **Step 2: Run test and verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=BusinessHealthMetricsBinderTest" test
```

Expected: compilation failure because `BusinessHealthMetricsBinder` does not exist.

- [ ] **Step 3: Implement binder**

Register gauges using `Gauge.builder(...)` and a private `current()` helper. Use tag key `check` for per-check gauges. Do not add company or account-book labels.

- [ ] **Step 4: Run test and verify it passes**

Run the same Maven command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/tuowei/erp/system/observability/metrics src/test/java/com/tuowei/erp/system/observability/BusinessHealthMetricsBinderTest.java
git commit -m "feat: expose business health metrics"
```

---

### Task 2: Alert Rule Template and Docs

**Files:**
- Create: `docs/monitoring/prometheus-alert-rules.yml`
- Modify: `docs/production-deployment.md`
- Modify: `docs/business-readiness-checklist.md`
- Test: `src/test/java/com/tuowei/erp/common/config/ObservabilityAlertRulesConfigurationTest.java`
- Modify: `src/test/java/com/tuowei/erp/common/config/ObservabilityAcceptanceScriptConfigurationTest.java`

- [ ] **Step 1: Write failing configuration tests**

Create a test that checks the alert rule template contains:

- `ErpReadinessP0P1Unpassed`
- `ErpRecentImportFailures`
- `ErpNegativeInventoryBalance`
- `ErpNoOpenAccountingPeriod`
- `ErpBusinessHealthWarn`
- `erp_business_health_overall_status`
- `erp_business_health_check_count`

Extend the existing acceptance configuration test so deployment docs and readiness checklist mention both metric names and `docs/monitoring/prometheus-alert-rules.yml`.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=ObservabilityAlertRulesConfigurationTest,ObservabilityAcceptanceScriptConfigurationTest" test
```

Expected: FAIL because the rule template and doc mentions do not exist yet.

- [ ] **Step 3: Add alert rules and docs**

Add `docs/monitoring/prometheus-alert-rules.yml` with the five rules. Update production deployment docs and business readiness checklist to require metric and rule evidence.

- [ ] **Step 4: Run tests and verify they pass**

Run the same Maven command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add docs/monitoring/prometheus-alert-rules.yml docs/production-deployment.md docs/business-readiness-checklist.md src/test/java/com/tuowei/erp/common/config/ObservabilityAlertRulesConfigurationTest.java src/test/java/com/tuowei/erp/common/config/ObservabilityAcceptanceScriptConfigurationTest.java
git commit -m "docs: add business health alert rules"
```

---

### Task 3: Final Verification

- [ ] **Step 1: Run focused tests**

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" "-Dtest=BusinessHealthMetricsBinderTest,ObservabilityAlertRulesConfigurationTest,ObservabilityAcceptanceScriptConfigurationTest,ObservabilityConfigurationTest,ObservabilityControllerTest" test
```

Expected: PASS.

- [ ] **Step 2: Run full test suite**

```powershell
.\mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test
```

Expected: PASS.

- [ ] **Step 3: Run release gate**

```powershell
.\scripts\release-check.ps1
```

Expected: PASS.

- [ ] **Step 4: Merge back to master**

Fast-forward merge `codex/observability-alerts` into `master`, rerun full tests on `master`, then clean up the worktree and branch.

---

## Self-Review

- Spec coverage: metrics, alert template, docs, tests, and release verification are covered.
- Placeholder scan: no placeholder patterns remain.
- Type consistency: metric names, alert names, and file paths match the design.
