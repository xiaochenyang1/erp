// F10 runs in a dedicated acceptance account book. A future period still carries
// that book's open foreign balances, so normal month-end prerequisites apply.
async function runPeriodLockCheck(request, { stamp }) {
  const evidence = { cleanup: [] }
  let periodId, productId, adjustmentId
  let ownsLock = false, ownsRevaluation = false
  let pass = false, detail = ''
  const successful = response => response.http >= 200 && response.http < 300 && String(response.body?.code) === '0'
  const requireOk = (response, action) => {
    if (!successful(response)) throw new Error(`${action}: ${response.body?.message || response.http}`)
    return response.body.data
  }
  const check = async key => {
    const response = await request('GET', `/api/finance/periods/${periodId}/close-check`)
    evidence[key] = response
    return requireOk(response, 'month-end check failed')
  }
  const cleanup = async (action, method, path, expectedStatus) => {
    try {
      const response = await request(method, path)
      evidence.cleanup.push({ action, response })
      const result = requireOk(response, action)
      if (expectedStatus && result?.status !== expectedStatus) throw new Error(`${action}: unexpected status ${result?.status}`)
      return true
    } catch (error) {
      evidence.cleanup.push({ action, error: error.message })
      pass = false
      detail += `; cleanup failed: ${error.message}`
      return false
    }
  }

  try {
    requireOk(await request('POST', '/api/finance/periods/generate', { year: 2099 }), 'generate period failed')
    const periods = requireOk(await request('GET', '/api/finance/periods?year=2099'), 'list periods failed')
    const period = periods.find(p => p.periodMonth === '2099-01')
    if (!period || period.status !== 'OPEN') throw new Error('F10 requires an OPEN 2099-01 period; existing locked/closed periods are not changed')
    periodId = period.id
    evidence.period = period
    let beforeLock = await check('closeCheckBeforeLock')
    if (beforeLock.passed !== true) {
      const issues = beforeLock.issues || []
      // Only supply the missing normal prerequisite. Existing/stale revaluations,
      // missing exchange rates and unrelated month-end issues remain failures.
      if (!issues.length || !issues.every(issue => issue.type === 'FX_REVALUATION' && issue.message === '尚未执行期末调汇')) {
        throw new Error(`month-end prerequisites failed: ${JSON.stringify(issues)}`)
      }
      const response = await request('POST', `/api/finance/periods/${periodId}/fx-revaluation`)
      evidence.revaluation = response
      const revaluation = requireOk(response, 'prepare period-end revaluation failed')
      ownsRevaluation = revaluation?.alreadyPosted === false
      if (revaluation?.status !== 'POSTED' || typeof revaluation.alreadyPosted !== 'boolean') {
        throw new Error('period-end revaluation did not return a valid posted result')
      }
      beforeLock = await check('closeCheckAfterRevaluation')
      if (beforeLock.passed !== true) throw new Error(`month-end prerequisites still failed: ${JSON.stringify(beforeLock.issues)}`)
    }

    evidence.lock = await request('POST', `/api/finance/periods/${periodId}/lock`)
    const locked = requireOk(evidence.lock, 'lock failed')
    ownsLock = true
    if (locked?.status !== 'LOCKED') throw new Error('lock did not return LOCKED status')

    const warehouses = requireOk(await request('GET', '/api/masterdata/warehouses?pageNo=1&pageSize=5&status=ACTIVE'), 'warehouses failed')
    const warehouse = warehouses.records?.[0]
    if (!warehouse) throw new Error('F10 requires an active warehouse')
    const product = requireOk(await request('POST', '/api/masterdata/products', {
      productCode: `F10P${stamp}`, productName: `F10商品${stamp}`, productType: 'GOODS',
      categoryName: 'SIGN', unitName: 'pcs', purchasePrice: 1, salePrice: 1, taxRate: 0,
    }), 'create F10 product failed')
    productId = product.id
    const adjustment = requireOk(await request('POST', '/api/inventory/adjustments', {
      warehouseId: warehouse.id, adjustmentDate: '2099-01-15', remark: `F10 lock ${stamp}`,
      lines: [{ productId, direction: 'IN', qty: 1, unitCost: 1, reason: 'F10' }],
    }), 'create adjustment failed')
    adjustmentId = adjustment.id
    evidence.adjustmentBeforePost = adjustment
    evidence.post = await request('POST', `/api/inventory/adjustments/${adjustmentId}/post`)
    const message = evidence.post.body?.message || ''
    const rejectedForLockedPeriod = evidence.post.http === 409 && String(evidence.post.body?.code) === '409'
      && /2099-01.*已锁定/.test(message)
    evidence.adjustmentAfterPost = requireOk(await request('GET', `/api/inventory/adjustments/${adjustmentId}`), 'read adjustment after rejection failed')
    if (!rejectedForLockedPeriod || evidence.adjustmentAfterPost.status !== 'DRAFT') {
      throw new Error(`expected locked-period conflict and unchanged DRAFT; got HTTP ${evidence.post.http}: ${message}`)
    }
    evidence.reopen = await request('POST', `/api/finance/periods/${periodId}/reopen`)
    if (requireOk(evidence.reopen, 'reopen failed')?.status !== 'OPEN') throw new Error('reopen did not return OPEN status')
    ownsLock = false
    pass = true
    detail = `period=2099-01; prerequisites passed; post rejected with locked-period 409; adjustment remained DRAFT; reopened=true; preparedRevaluation=${ownsRevaluation}`
  } catch (error) {
    detail = `error: ${error.message}`
  } finally {
    if (ownsLock && await cleanup('reopen F10 period', 'POST', `/api/finance/periods/${periodId}/reopen`, 'OPEN')) ownsLock = false
    if (adjustmentId) await cleanup('cancel F10 adjustment', 'POST', `/api/inventory/adjustments/${adjustmentId}/cancel`, 'CANCELLED')
    if (productId) await cleanup('disable F10 product', 'POST', `/api/masterdata/products/${productId}/disable`, 'INACTIVE')
    if (ownsRevaluation && !ownsLock) await cleanup('cancel F10 revaluation', 'POST', `/api/finance/periods/${periodId}/fx-revaluation/cancel`, 'CANCELLED')
  }
  return { pass, detail, evidence }
}

module.exports = { runPeriodLockCheck }
