# ERP Production Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the ERP backend deployable as a production service with explicit production configuration, guarded bootstrap data, container deployment assets and smoke verification.

**Architecture:** Keep the Spring Boot application as a single deployable jar. Production configuration is activated by the `prod` profile and supplied through environment variables; startup fails fast when required production secrets or services are missing. Docker Compose provides MySQL, Redis and the application service for a repeatable first deployment, while Flyway owns schema and bootstrap data.

**Tech Stack:** Spring Boot 3.3.5, Spring Security, MyBatis-Plus, Flyway, MySQL 8, Redis 7, Docker Compose, PowerShell smoke check

---

### Task 1: Production Configuration And Guards

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-prod.yml`
- Modify: `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java`
- Modify: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/tuowei/erp/common/config/ProductionStartupValidator.java`

- [x] Remove the default `dev` active profile from base configuration so production cannot accidentally start with local database defaults.
- [x] Move local JWT defaults and public Swagger access into the `dev` profile.
- [x] Add `prod` profile configuration that reads datasource, Redis and JWT values from environment variables.
- [x] Permit Swagger endpoints only when `erp.security.public-api-docs-enabled=true`.
- [x] Hide unexpected exception messages by default and only expose them when `erp.error.expose-unexpected-message=true`.
- [x] Add a production startup validator that fails fast if required production values are blank or Swagger is enabled.

### Task 2: Bootstrap Data And Super Admin Access

**Files:**
- Modify: `src/main/java/com/tuowei/erp/common/security/PermissionCodes.java`
- Modify: `src/main/java/com/tuowei/erp/common/security/UserPermissionService.java`
- Create: `src/main/java/com/tuowei/erp/system/bootstrap/ProductionBootstrapService.java`
- Create: `src/main/resources/db/migration/V27__production_bootstrap_seed.sql`

- [x] Give active `SUPER_ADMIN` roles all backend permission codes without depending on menu rows being complete.
- [x] Seed minimum production bootstrap data through Flyway: config, sequence rules, root department, admin post, admin user, roles, default warehouse and data scope.
- [x] Require `ERP_BOOTSTRAP_ADMIN_PASSWORD` on the first production boot and store a marker after the admin password is initialized.
- [x] Avoid resetting the admin password on later restarts after the bootstrap marker is set.

### Task 3: Deployment Assets

**Files:**
- Create: `Dockerfile`
- Create: `docker-compose.yml`
- Create: `.env.prod.example`
- Modify: `.gitignore`
- Create: smoke check script (later removed by request)

- [x] Add a runtime Docker image that runs the built Spring Boot jar with `SPRING_PROFILES_ACTIVE=prod`.
- [x] Add Docker Compose services for MySQL, Redis and the ERP server.
- [x] Add a tracked production environment template and ignore real production env files.
- [x] Add a smoke check script that verifies health, login and an authenticated protected endpoint.
- [x] 2026-05-18: Smoke check script later removed by request; pre-production validation is now manual.

### Task 4: Deployment Documentation

**Files:**
- Create: `docs/production-deployment.md`

- [x] Document environment variables, build commands, Compose startup, first-login handling and smoke verification.
- [x] Include an explicit go-live checklist and rollback notes.
- [x] State what remains environment-dependent and cannot be verified without real production hosts.

### Task 5: Verification

**Files:**
- No code changes.

- [x] Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean package -DskipTests`.
- [x] 2026-05-15: Run `mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" clean test`; expected result after temporary test removal was `No tests to run` and `BUILD SUCCESS`.
- [x] 2026-05-15: Verify no tracked test source remains under `src/test` at that point.
- [x] 2026-05-19: Restore a minimal `src/test` regression set and run `scripts/release-check.ps1`; expected result is `BUILD SUCCESS`, 13 tests passing, release jar generated, and both SBOM files present.
- [x] Verify `docker-compose.yml`, `.env.prod.example` and `Dockerfile` exist; smoke script was later removed by request.
