const assert = require('node:assert/strict')
const { test } = require('node:test')
const { runPeriodLockCheck } = require('./fq-period-lock-check.cjs')

const ok = data => ({ http: 200, body: { code: '0', data } })
const conflict = message => ({ http: 409, body: { code: '409', message } })
const missingFx = { type: 'FX_REVALUATION', message: '尚未执行期末调汇', amount: 265.83 }

function fixture(options = {}) {
  const calls = []
  let status = options.periodStatus || 'OPEN', revalued = false
  let adjustment = 'DRAFT'
  const request = async (method, path, body) => {
    calls.push({ method, path, body })
    if (path === '/api/finance/periods/generate') return ok([])
    if (path === '/api/finance/periods?year=2099') return ok([{ id: 'P', periodMonth: '2099-01', status }])
    if (path.endsWith('/close-check')) {
      const issues = revalued ? (options.afterIssues || []) : (options.issues || [])
      return ok({ passed: issues.length === 0, issues })
    }
    if (path.endsWith('/fx-revaluation')) {
      if (options.fxFailure) return conflict('未找到业务日期对应的有效汇率')
      revalued = true
      return ok({ status: 'POSTED', alreadyPosted: options.alreadyPosted || false })
    }
    if (path.endsWith('/lock')) {
      status = 'LOCKED'
      return ok({ status })
    }
    if (path.endsWith('/reopen')) {
      status = 'OPEN'
      return ok({ status })
    }
    if (path.startsWith('/api/masterdata/warehouses?')) return ok({ records: [{ id: 'W' }] })
    if (path === '/api/masterdata/products') return ok({ id: 'G' })
    if (path === '/api/inventory/adjustments') return ok({ id: 'A', status: 'DRAFT' })
    if (path === '/api/inventory/adjustments/A/post') {
      if (options.postAllowed) { adjustment = 'POSTED'; return ok({ status: adjustment }) }
      return options.postResult || conflict('业务日期 2099-01-15 所属期间 2099-01 已锁定，不能执行库存调整过账')
    }
    if (path === '/api/inventory/adjustments/A') return ok({ id: 'A', status: adjustment })
    if (path === '/api/inventory/adjustments/A/cancel') return ok({ status: 'CANCELLED' })
    if (path.endsWith('/disable')) return ok({ status: 'INACTIVE' })
    if (path.endsWith('/fx-revaluation/cancel')) {
      if (options.cleanupFailure) return conflict('撤销失败')
      revalued = false
      return ok({ status: 'CANCELLED' })
    }
    throw new Error(`Unexpected ${method} ${path}`)
  }
  return { request, calls, state: () => ({ status, revalued }) }
}

test('foreign balance requires revaluation before lock and cancels only its own adjustment afterward', async () => {
  const f = fixture({ issues: [missingFx] })
  const result = await runPeriodLockCheck(f.request, { stamp: 'test' })
  assert.equal(result.pass, true)
  assert.deepEqual(result.evidence.closeCheckBeforeLock.body.data.issues, [missingFx])
  assert.equal(result.evidence.closeCheckAfterRevaluation.body.data.passed, true)
  assert.equal(result.evidence.post.http, 409)
  assert.equal(result.evidence.adjustmentAfterPost.status, 'DRAFT')
  assert.deepEqual(f.state(), { status: 'OPEN', revalued: false })
  const paths = f.calls.map(c => c.path)
  assert(paths.indexOf('/api/finance/periods/P/fx-revaluation') < paths.indexOf('/api/finance/periods/P/lock'))
  assert(paths.indexOf('/api/finance/periods/P/reopen') < paths.indexOf('/api/finance/periods/P/fx-revaluation/cancel'))
})

test('an already-ready period needs no revaluation', async () => {
  const f = fixture()
  assert.equal((await runPeriodLockCheck(f.request, { stamp: 'test' })).pass, true)
  assert(!f.calls.some(c => c.path.includes('fx-revaluation')))
})

test('unrelated or stale month-end failures remain visible without locking or changing financial records', async () => {
  for (const issues of [
    [{ type: 'INVENTORY_FINANCE_RECONCILIATION', message: '差异' }],
    [{ ...missingFx, message: '无法校验期末调汇：未找到业务日期对应的有效汇率' }],
    [{ ...missingFx, message: '期末外币余额、币种构成或汇率已变化' }],
    [missingFx, { type: 'VOUCHER_UNBALANCED', message: '借贷不平' }],
  ]) {
    const f = fixture({ issues })
    const result = await runPeriodLockCheck(f.request, { stamp: 'test' })
    assert.equal(result.pass, false)
    assert.deepEqual(result.evidence.closeCheckBeforeLock.body.data.issues, issues)
    assert(!f.calls.some(c => c.path.endsWith('/lock') || c.path.includes('fx-revaluation')))
  }
})

test('existing locked or closed periods are not reopened by the test', async () => {
  for (const periodStatus of ['LOCKED', 'CLOSED']) {
    const f = fixture({ periodStatus })
    assert.equal((await runPeriodLockCheck(f.request, { stamp: 'test' })).pass, false)
    assert(!f.calls.some(c => c.path.endsWith('/reopen')))
  }
})

test('a failed revaluation stops before locking and is not cancelled as if owned', async () => {
  const f = fixture({ issues: [missingFx], fxFailure: true })
  assert.equal((await runPeriodLockCheck(f.request, { stamp: 'test' })).pass, false)
  assert(!f.calls.some(c => c.path.endsWith('/lock') || c.path.endsWith('/cancel')))
})

test('month-end checks must pass after revaluation; cleanup still runs on failure', async () => {
  const f = fixture({ issues: [missingFx], afterIssues: [{ type: 'VOUCHER_UNBALANCED', message: '借贷不平' }] })
  assert.equal((await runPeriodLockCheck(f.request, { stamp: 'test' })).pass, false)
  assert(!f.calls.some(c => c.path.endsWith('/lock')))
  assert.equal(f.state().revalued, false)
})

test('a concurrently existing revaluation is preserved', async () => {
  const f = fixture({ issues: [missingFx], alreadyPosted: true })
  assert.equal((await runPeriodLockCheck(f.request, { stamp: 'test' })).pass, true)
  assert(!f.calls.some(c => c.path.endsWith('/fx-revaluation/cancel')))
  assert.equal(f.state().revalued, true)
})

test('HTTP errors mentioning a period, unrelated conflicts and successful posting cannot pass the negative check', async () => {
  for (const options of [
    { postResult: { http: 500, body: { code: '500', message: '2099-01 已锁定' } } },
    { postResult: conflict('未生成会计期间') },
    { postResult: conflict('权限不足') },
    { postAllowed: true },
  ]) {
    const f = fixture(options)
    assert.equal((await runPeriodLockCheck(f.request, { stamp: 'test' })).pass, false)
    assert.equal(f.state().status, 'OPEN')
  }
})

test('cleanup failure makes the check fail even after a correct locked-period rejection', async () => {
  const f = fixture({ issues: [missingFx], cleanupFailure: true })
  const result = await runPeriodLockCheck(f.request, { stamp: 'test' })
  assert.equal(result.pass, false)
  assert.equal(result.evidence.post.http, 409)
  assert.match(result.detail, /cleanup failed/)
})
