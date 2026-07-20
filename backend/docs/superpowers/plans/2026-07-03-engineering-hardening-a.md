# Engineering Hardening A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the exception-rule scheduler tenant-context runtime issue and add a real frontend lint gate.

**Architecture:** Backend scheduled automation must run without relying on an HTTP `ErpPrincipal`; scheduled scans should use explicit tenant/account-book scope from due rules. Frontend lint should use a checked ESLint configuration and separate read-only lint from auto-fix.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, JUnit 5, Mockito, Vue 3, TypeScript, ESLint 8.

---

### Task 1: Backend Scheduler Tenant Context

**Files:**
- Modify: `src/main/java/com/tuowei/erp/issue/rule/service/ExceptionRuleService.java`
- Test: `src/test/java/com/tuowei/erp/issue/rule/ExceptionRuleServiceTest.java`

- [ ] Write a failing test proving `scanDueRules()` can select due rules without `SecurityContextHolder` authentication.
- [ ] Run the targeted test and confirm it fails with `租户表查询缺少当前登录用户`.
- [ ] Change `scanDueRules()` so the first rule query bypasses the tenant interceptor safely while later work still uses `schedulerAudit(rule, now)`.
- [ ] Run the targeted test and confirm it passes.

### Task 2: Frontend Lint Gate

**Files:**
- Create: `erp-frontend/.eslintrc.cjs`
- Modify: `erp-frontend/package.json`

- [ ] Add ESLint configuration for Vue 3 + TypeScript using existing installed dependencies.
- [ ] Split scripts into read-only `lint` and auto-fix `lint:fix`.
- [ ] Run `npm run lint` and address only blocking configuration issues in this pass.

### Task 3: Verification

- [ ] Run backend targeted test for exception rules.
- [ ] Run backend full `.\mvnw.cmd -B test`.
- [ ] Run frontend `npm run type-check`.
- [ ] Run frontend `npm run lint`.
- [ ] Run frontend `npm run build`.
