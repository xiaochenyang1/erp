# Frontend Quality Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-stage automated quality net for `E:\tuowei\python\erp-frontend` without changing business behavior.

**Architecture:** Add Vitest unit tests around the lowest-risk, highest-impact frontend contracts: auth normalization, request normalization, user store behavior, and common formatting helpers. Keep browser E2E as a runnable scaffold first, because stable E2E depends on a running backend and seeded login data. Avoid large page refactors in this phase.

**Tech Stack:** Vue 3, TypeScript, Vite, Vitest, jsdom, @vue/test-utils, Pinia, Playwright optional scaffold, npm scripts.

---

## File Structure

- Modify `E:\tuowei\python\erp-frontend\package.json`
  - Add unit-test scripts.
  - Add dev dependencies for Vitest and Vue test utilities.
- Modify `E:\tuowei\python\erp-frontend\vite.config.ts`
  - Add Vitest `test` configuration while preserving the current Vite build config.
- Create `E:\tuowei\python\erp-frontend\src\test\setup.ts`
  - Provide test setup for jsdom and localStorage cleanup.
- Modify `E:\tuowei\python\erp-frontend\src\utils\request.ts`
  - Export pure normalization helpers for direct unit tests.
  - Keep runtime request behavior unchanged.
- Create `E:\tuowei\python\erp-frontend\src\api\auth.test.ts`
  - Verify login/user-info normalization preserves Long IDs as strings.
- Create `E:\tuowei\python\erp-frontend\src\utils\request.test.ts`
  - Verify pagination parameter and page response normalization.
- Create `E:\tuowei\python\erp-frontend\src\store\modules\user.test.ts`
  - Verify login/logout store behavior with mocked API/router/message.
- Create `E:\tuowei\python\erp-frontend\src\utils\common.test.ts`
  - Verify money/date/file-size/status helpers.
- Optionally create `E:\tuowei\python\erp-frontend\playwright.config.ts`
  - Only if adding E2E scaffold in this phase.
- Optionally create `E:\tuowei\python\erp-frontend\e2e\auth-routing.spec.ts`
  - Smoke-test login routing against a running local app.

---

### Task 1: Add Vitest Tooling

**Files:**
- Modify: `E:\tuowei\python\erp-frontend\package.json`
- Modify: `E:\tuowei\python\erp-frontend\vite.config.ts`
- Create: `E:\tuowei\python\erp-frontend\src\test\setup.ts`

- [ ] **Step 1: Add test dependencies and scripts**

In `package.json`, add these scripts:

```json
{
  "scripts": {
    "test:unit": "vitest run",
    "test:unit:watch": "vitest"
  }
}
```

Add these dev dependencies:

```json
{
  "devDependencies": {
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^27.2.0",
    "vitest": "^4.0.0"
  }
}
```

- [ ] **Step 2: Install dependencies**

Run:

```powershell
npm install
```

Expected: `package-lock.json` updates and npm exits with code `0`.

- [ ] **Step 3: Configure Vitest in Vite**

In `vite.config.ts`, add a triple-slash reference at the top:

```ts
/// <reference types="vitest" />
```

Add this `test` block inside `defineConfig({ ... })`:

```ts
test: {
  environment: 'jsdom',
  setupFiles: ['src/test/setup.ts'],
  globals: true,
  clearMocks: true,
  restoreMocks: true
},
```

- [ ] **Step 4: Add test setup**

Create `src/test/setup.ts`:

```ts
import { afterEach, vi } from 'vitest'

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  vi.clearAllMocks()
})
```

- [ ] **Step 5: Run the empty unit test command**

Run:

```powershell
npm run test:unit -- --passWithNoTests
```

Expected: Vitest starts successfully and exits with code `0`.

---

### Task 2: Export and Test Request Normalizers

**Files:**
- Modify: `E:\tuowei\python\erp-frontend\src\utils\request.ts`
- Create: `E:\tuowei\python\erp-frontend\src\utils\request.test.ts`

- [ ] **Step 1: Write failing tests**

Create `src/utils/request.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import {
  normalizeRequestParams,
  normalizeResponsePayload
} from './request'

describe('request normalizers', () => {
  it('maps page and size to backend pageNo and pageSize without mutating the input', () => {
    const input = { page: 2, size: 50, keyword: 'bolt' }

    const result = normalizeRequestParams(input)

    expect(result).toEqual({
      pageNo: 2,
      pageSize: 50,
      keyword: 'bolt'
    })
    expect(input).toEqual({ page: 2, size: 50, keyword: 'bolt' })
  })

  it('does not override explicit backend pagination names', () => {
    const result = normalizeRequestParams({
      page: 2,
      size: 50,
      pageNo: 3,
      pageSize: 100
    })

    expect(result).toEqual({
      pageNo: 3,
      pageSize: 100
    })
  })

  it('normalizes numeric page metadata returned as strings', () => {
    const payload = {
      records: [{ id: '9007199254740993' }],
      pageNo: '1',
      pageSize: '20',
      total: '42'
    }

    const result = normalizeResponsePayload(payload)

    expect(result).toEqual({
      records: [{ id: '9007199254740993' }],
      pageNo: 1,
      pageSize: 20,
      total: 42
    })
  })

  it('returns primitive payloads unchanged', () => {
    expect(normalizeResponsePayload('ok')).toBe('ok')
    expect(normalizeRequestParams(undefined)).toBeUndefined()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
npm run test:unit -- src/utils/request.test.ts
```

Expected: FAIL because `normalizeRequestParams` and `normalizeResponsePayload` are not exported yet.

- [ ] **Step 3: Export the pure helper functions**

In `src/utils/request.ts`, replace:

```ts
const normalizeParams = (params: unknown) => {
```

with:

```ts
export const normalizeRequestParams = (params: unknown) => {
```

Replace:

```ts
const normalizeResponseData = <T>(data: T): T => {
```

with:

```ts
export const normalizeResponsePayload = <T>(data: T): T => {
```

Replace request interceptor usage:

```ts
config.params = normalizeParams(config.params)
```

with:

```ts
config.params = normalizeRequestParams(config.params)
```

Replace response interceptor usage:

```ts
return normalizeResponseData(res.data)
```

with:

```ts
return normalizeResponsePayload(res.data)
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
npm run test:unit -- src/utils/request.test.ts
```

Expected: PASS.

---

### Task 3: Test Auth Long-ID Normalization

**Files:**
- Create: `E:\tuowei\python\erp-frontend\src\api\auth.test.ts`

- [ ] **Step 1: Write failing auth normalization tests**

Create `src/api/auth.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = {
  get: vi.fn(),
  post: vi.fn()
}

vi.mock('@/utils/request', () => ({
  request: requestMock
}))

describe('auth api normalization', () => {
  beforeEach(() => {
    requestMock.get.mockReset()
    requestMock.post.mockReset()
  })

  it('keeps login user id and warehouse ids as strings', async () => {
    requestMock.post.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 7200,
      refreshExpiresIn: 1209600,
      user: {
        id: 9007199254740993,
        username: 'admin',
        dataScope: {
          hasAllScope: false,
          deptScoped: false,
          postScoped: false,
          selfScoped: false,
          warehouseIds: [9007199254740995, '9007199254740997']
        }
      },
      permissions: ['system:user:view']
    })

    const { login } = await import('./auth')
    const result = await login({ username: 'admin', password: 'LocalAdmin123' })

    expect(result.user.id).toBe('9007199254740992')
    expect(result.user.dataScope?.warehouseIds).toEqual([
      '9007199254740996',
      '9007199254740997'
    ])
  })

  it('keeps user-info id and permissions stable', async () => {
    requestMock.get.mockResolvedValue({
      id: '9007199254740993',
      username: 'runtime_smoke',
      permissions: ['dashboard:view']
    })

    const { getUserInfo } = await import('./auth')
    const result = await getUserInfo()

    expect(result.id).toBe('9007199254740993')
    expect(result.permissions).toEqual(['dashboard:view'])
  })
})
```

Note: the first expectation intentionally captures JavaScript precision loss for unsafe numeric literals. If the backend returns a JSON number larger than `Number.MAX_SAFE_INTEGER`, the frontend cannot recover it. This test documents why the backend contract must return Long IDs as strings and why frontend code must not convert them back to numbers.

- [ ] **Step 2: Run test to verify current behavior**

Run:

```powershell
npm run test:unit -- src/api/auth.test.ts
```

Expected: PASS after Task 1. If the unsafe-number expectation surprises the implementer, keep it; it is the point of the test.

- [ ] **Step 3: Add a safe-string regression test**

Append this test to `src/api/auth.test.ts`:

```ts
it('preserves backend string Long ids exactly', async () => {
  requestMock.post.mockResolvedValue({
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    expiresIn: 7200,
    refreshExpiresIn: 1209600,
    user: {
      id: '9007199254740993',
      username: 'admin',
      dataScope: {
        hasAllScope: false,
        deptScoped: false,
        postScoped: false,
        selfScoped: false,
        warehouseIds: ['9007199254740995']
      }
    },
    permissions: []
  })

  const { login } = await import('./auth')
  const result = await login({ username: 'admin', password: 'LocalAdmin123' })

  expect(result.user.id).toBe('9007199254740993')
  expect(result.user.dataScope?.warehouseIds).toEqual(['9007199254740995'])
})
```

- [ ] **Step 4: Run auth tests**

Run:

```powershell
npm run test:unit -- src/api/auth.test.ts
```

Expected: PASS.

---

### Task 4: Test User Store Login, Logout, and Permissions

**Files:**
- Create: `E:\tuowei\python\erp-frontend\src\store\modules\user.test.ts`

- [ ] **Step 1: Write store tests**

Create `src/store/modules/user.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const routerPush = vi.fn()
const messageSuccess = vi.fn()
const loginMock = vi.fn()
const logoutMock = vi.fn()
const getUserInfoMock = vi.fn()

vi.mock('@/router', () => ({
  default: {
    push: routerPush
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: messageSuccess
  }
}))

vi.mock('@/api/auth', () => ({
  login: loginMock,
  logout: logoutMock,
  getUserInfo: getUserInfoMock
}))

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    routerPush.mockReset()
    messageSuccess.mockReset()
    loginMock.mockReset()
    logoutMock.mockReset()
    getUserInfoMock.mockReset()
  })

  it('stores token, refresh token, user info, and permissions after login', async () => {
    loginMock.mockResolvedValue({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      user: {
        id: '1',
        username: 'admin'
      },
      permissions: ['system:user:view']
    })

    const { useUserStore } = await import('./user')
    const store = useUserStore()

    await store.doLogin({ username: 'admin', password: 'LocalAdmin123' })

    expect(store.token).toBe('access-token')
    expect(localStorage.getItem('token')).toBe('access-token')
    expect(localStorage.getItem('refreshToken')).toBe('refresh-token')
    expect(store.userInfo?.username).toBe('admin')
    expect(store.permissions).toEqual(['system:user:view'])
    expect(store.hasPermission('system:user:view')).toBe(true)
    expect(store.hasPermission('system:role:view')).toBe(false)
    expect(routerPush).toHaveBeenCalledWith('/')
    expect(messageSuccess).toHaveBeenCalledWith('登录成功')
  })

  it('loads user info and permissions after page refresh', async () => {
    getUserInfoMock.mockResolvedValue({
      id: '1',
      username: 'admin',
      permissions: ['dashboard:view']
    })

    const { useUserStore } = await import('./user')
    const store = useUserStore()

    const result = await store.getUserInfo()

    expect(result.username).toBe('admin')
    expect(store.permissions).toEqual(['dashboard:view'])
    expect(store.hasPermission('dashboard:view')).toBe(true)
  })

  it('clears local auth state and redirects on logout', async () => {
    localStorage.setItem('token', 'access-token')
    localStorage.setItem('refreshToken', 'refresh-token')
    logoutMock.mockResolvedValue(undefined)

    const { useUserStore } = await import('./user')
    const store = useUserStore()

    await store.doLogout()

    expect(logoutMock).toHaveBeenCalledWith('refresh-token')
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(routerPush).toHaveBeenCalledWith('/login')
    expect(messageSuccess).toHaveBeenCalledWith('已退出登录')
  })
})
```

- [ ] **Step 2: Run store tests**

Run:

```powershell
npm run test:unit -- src/store/modules/user.test.ts
```

Expected: PASS.

---

### Task 5: Test Common Formatting Helpers

**Files:**
- Create: `E:\tuowei\python\erp-frontend\src\utils\common.test.ts`

- [ ] **Step 1: Write common helper tests**

Create `src/utils/common.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatDateTime,
  formatFileSize,
  formatMoney,
  getStatusText,
  getStatusType
} from './common'

describe('common formatting helpers', () => {
  it('formats money with thousands separators and decimals', () => {
    expect(formatMoney(1234567.5)).toBe('1,234,567.50')
    expect(formatMoney('88.8', 3)).toBe('88.800')
    expect(formatMoney('not-a-number')).toBe('0.00')
  })

  it('formats date and date time strings', () => {
    const date = new Date(2026, 6, 7, 17, 30, 5)

    expect(formatDate(date)).toBe('2026-07-07')
    expect(formatDateTime(date)).toBe('2026-07-07 17:30:05')
    expect(formatDate('bad-date')).toBe('')
  })

  it('formats file sizes', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(1024)).toBe('1 KB')
    expect(formatFileSize(1536)).toBe('1.5 KB')
  })

  it('maps status type and text', () => {
    expect(getStatusType('APPROVED')).toBe('success')
    expect(getStatusType('PENDING')).toBe('warning')
    expect(getStatusType('UNKNOWN')).toBe('')
    expect(getStatusText('POSTED')).toBe('已过账')
    expect(getStatusText('UNKNOWN')).toBe('UNKNOWN')
  })
})
```

- [ ] **Step 2: Run common helper tests**

Run:

```powershell
npm run test:unit -- src/utils/common.test.ts
```

Expected: PASS.

---

### Task 6: Add Optional E2E Scaffold

**Files:**
- Modify: `E:\tuowei\python\erp-frontend\package.json`
- Create: `E:\tuowei\python\erp-frontend\playwright.config.ts`
- Create: `E:\tuowei\python\erp-frontend\e2e\auth-routing.spec.ts`

- [ ] **Step 1: Add Playwright dependency and scripts**

If this phase includes E2E, add this dev dependency:

```json
{
  "devDependencies": {
    "@playwright/test": "^1.57.0"
  }
}
```

Add these scripts:

```json
{
  "scripts": {
    "test:e2e": "playwright test",
    "test:e2e:ui": "playwright test --ui"
  }
}
```

- [ ] **Step 2: Create Playwright config**

Create `playwright.config.ts`:

```ts
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: {
    timeout: 5_000
  },
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173',
    trace: 'retain-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 60_000
  }
})
```

- [ ] **Step 3: Create routing smoke test**

Create `e2e/auth-routing.spec.ts`:

```ts
import { expect, test } from '@playwright/test'

test('redirects anonymous users to login', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.locator('body')).toContainText('登录')
})
```

- [ ] **Step 4: Run E2E smoke**

Run:

```powershell
npm run test:e2e
```

Expected: PASS if Playwright browsers are installed. If browsers are missing, run `npx playwright install chromium` and rerun.

---

### Task 7: Final Verification

**Files:**
- Verify: `E:\tuowei\python\erp-frontend`

- [ ] **Step 1: Run unit tests**

Run:

```powershell
npm run test:unit
```

Expected: PASS.

- [ ] **Step 2: Run type check**

Run:

```powershell
npm run type-check
```

Expected: PASS.

- [ ] **Step 3: Run lint**

Run:

```powershell
npm run lint
```

Expected: PASS.

- [ ] **Step 4: Run production build**

Run:

```powershell
npm run build
```

Expected: PASS.

- [ ] **Step 5: Run audit with official npm registry**

Run:

```powershell
npm audit --registry=https://registry.npmjs.org
```

Expected: `found 0 vulnerabilities`.

- [ ] **Step 6: Check worktree**

Run:

```powershell
git status --short
```

Expected: only intentional files are modified or created.

---

## Self-Review

- Spec coverage: The plan covers unit-test tooling, request/auth/store/common-helper tests, Long ID protection, and final verification. Large page refactors and global `strict` mode are intentionally excluded from this phase.
- Placeholder scan: No `TBD`, deferred implementation placeholder, or unnamed file path remains.
- Type consistency: Test names and exported helper names are consistent: `normalizeRequestParams` and `normalizeResponsePayload`.
