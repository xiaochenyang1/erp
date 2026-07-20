# Quality Gates And Initial Import Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a practical verification baseline and close the backend initial-data import workflow so the ERP server can be regression-tested before pre-production acceptance.

**Architecture:** Keep the existing Spring Boot + MockMvc test style. Add focused integration tests around the current public APIs and create a small PowerShell release gate that runs Maven verification and checks generated release artifacts. Avoid broad feature work in this pass.

**Tech Stack:** Spring Boot 3.5, JUnit 5, MockMvc, H2 + Flyway, MyBatis-Plus, Maven Wrapper, PowerShell.

---

## File Map

- Create: `src/test/java/com/tuowei/erp/imports/InitialImportControllerTest.java`
- Create: `src/test/java/com/tuowei/erp/system/auth/AuthControllerSmokeTest.java`
- Create: `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`
- Create: `scripts/release-check.ps1`
- Modify: `docs/business-readiness-checklist.md`
- Modify: `src/main/java/com/tuowei/erp/imports/service/ImportJobService.java`

## Task 1: Initial Import API Regression

**Files:**
- Create: `src/test/java/com/tuowei/erp/imports/InitialImportControllerTest.java`

- [x] Write MockMvc tests for template download, CSV preview validation, commit success, duplicate commit rejection, and failed commit status reporting.
- [x] Seed only the minimal reference data needed for import rows and clean ids in a reserved test range.
- [x] Run `./mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=InitialImportControllerTest test` and require `BUILD SUCCESS`.

## Task 2: Authentication And Migration Smoke Tests

**Files:**
- Create: `src/test/java/com/tuowei/erp/system/auth/AuthControllerSmokeTest.java`
- Create: `src/test/java/com/tuowei/erp/db/FlywayMigrationSmokeTest.java`

- [x] Add an unauthenticated auth smoke test proving protected APIs return `401` and login returns a token.
- [x] Add a Flyway smoke test proving late migrations created import and finance cancel metadata tables/columns.
- [x] Run the two smoke tests with Maven and require `BUILD SUCCESS`.

## Task 3: Release Gate Script

**Files:**
- Create: `scripts/release-check.ps1`
- Modify: `docs/business-readiness-checklist.md`

- [x] Add a PowerShell script that runs Maven `clean package`, verifies SBOM output, verifies the application jar exists, and prints the remaining manual acceptance checklist location.
- [x] Document the release gate command in the business readiness checklist.
- [x] Run the script locally and require `BUILD SUCCESS` plus artifact checks.

## Task 4: Final Verification

**Files:**
- Existing test and script files from Tasks 1-3

- [x] Run `./mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" test`.
- [x] Run `./mvnw.cmd "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" package` or `scripts/release-check.ps1`.
- [x] Fix the initial import failure path in `ImportJobService` after the regression test exposed a transaction self-lock during failed commit status reporting.

## Self-Review

- Spec coverage: Covers the selected path of quality gates plus initial import closure.
- Placeholder scan: No open-ended implementation placeholders remain; each task names concrete files and verification commands.
- Scope check: Frontend, approval-center refactor, advanced inventory, and advanced finance remain out of scope for this pass.
