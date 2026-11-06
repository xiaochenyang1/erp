import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { checkStock } from './trial-business-acceptance.mjs'

const adjustments = '/api/inventory/adjustments', checks = '/api/inventory/checks'

export async function seedHistoricalBusiness({ api, admin, date, rememberSecret }) {
  const suffix = randomBytes(5).toString('hex')
  const prefix = `UPGRADE-${suffix}`
  const remark = `V168 恢复演练样例 ${prefix}`
  const year = Number(date.slice(0, 4))
  let periods = await api('GET', `/api/finance/periods?year=${year}`)
  if (!periods.length) periods = await api('POST', '/api/finance/periods/generate', { year })
  assert.equal(periods.find(p => p.startDate <= date && p.endDate >= date)?.status, 'OPEN')
  const dept = await api('POST', '/api/system/depts', { deptCode: prefix, deptName: remark, parentId: 0, sortNo: 99, remark })
  const warehouse = await api('POST', '/api/masterdata/warehouses', { warehouseCode: prefix, warehouseName: remark, deptId: dept.id, managerUserId: admin.id, remark })
  const createProduct = label => api('POST', '/api/masterdata/products', { productCode: `${prefix}-${label}`, productName: `${remark} ${label}`,
    productType: 'GOODS', categoryName: '演练', unitName: '件', purchasePrice: 10, salePrice: 15, taxRate: 0,
    lotControlled: false, shelfLifeControlled: false, remark })
  const product = await createProduct('posted'), countedProduct = await createProduct('counted')
  const count = await api('POST', checks, { warehouseId: warehouse.id, checkDate: date, remark,
    lines: [{ productId: product.id, actualQty: 10, unitCost: 10, remark }] })
  assert.equal(count.status, 'COUNTED')
  // The V168 application posts the generated adjustment immediately.
  const adjusted = await api('POST', `${checks}/${count.id}/adjust`)
  assert.equal(adjusted.status, 'ADJUSTED')
  const posted = await api('GET', `${adjustments}/${adjusted.generatedAdjustmentId}`)
  assert.equal(posted.status, 'POSTED')
  const createAdjustment = qty => api('POST', adjustments, { warehouseId: warehouse.id, adjustmentDate: date, remark,
    lines: [{ productId: product.id, direction: 'IN', qty, unitCost: 10, reason: remark }] })
  const draft = await createAdjustment(2)
  assert.equal(draft.status, 'DRAFT')
  const cancelled = await api('POST', `${adjustments}/${(await createAdjustment(1)).id}/cancel`)
  assert.equal(cancelled.status, 'CANCELLED')
  const counted = await api('POST', checks, { warehouseId: warehouse.id, checkDate: date, remark,
    lines: [{ productId: countedProduct.id, actualQty: 3, unitCost: 10, remark }] })
  assert.equal(counted.status, 'COUNTED')
  const customer = await api('POST', '/api/masterdata/customers', { customerCode: prefix, customerName: `历史客户 🧪 ${suffix}`,
    customerType: 'ENTERPRISE', settlementMethod: 'BANK', creditLimit: 1000, remark })
  const reviewer = { username: `recovery_review_${suffix}`, password: `Recovery1${randomBytes(24).toString('hex')}` }
  rememberSecret(reviewer.password)
  const user = await api('POST', '/api/system/users', { ...reviewer, realName: '恢复演练审核员', deptId: dept.id, remark })
  const roles = await api('GET', '/api/system/roles?pageNo=1&pageSize=200')
  const role = roles.records.find(row => row.roleCode === 'ERP_ADMIN')
  assert(role, 'Missing ERP_ADMIN role')
  await api('PUT', `/api/system/users/${user.id}/roles`, { roleIds: [role.id] })
  const stocks = await api('GET', `/api/inventory/balances?pageNo=1&pageSize=200&warehouseId=${warehouse.id}`)
  checkStock(stocks.records, warehouse.id, product.id, 10, 100)
  return { warehouse, product, countedProduct, adjusted, posted, draft, cancelled, counted, customer,
    reviewer, reviewerId: user.id, adminRoleId: role.id, prefix }
}

export async function verifyHistoricalReads(api, fixture) {
  const { posted, draft, cancelled, adjusted, counted, customer } = fixture
  for (const row of [posted, draft, cancelled]) {
    const actual = await api('GET', `${adjustments}/${row.id}`)
    for (const field of ['id', 'status', 'adjustmentNo', 'totalQuantity', 'totalAmount']) assert.equal(String(actual[field]), String(row[field]), `Historical ${field} changed`)
  }
  const oldCount = await api('GET', `${checks}/${adjusted.id}`)
  assert.equal(oldCount.status, 'ADJUSTED')
  assert.equal(oldCount.generatedAdjustmentId, posted.id)
  assert.equal((await api('GET', `${checks}/${counted.id}`)).status, 'COUNTED')
  assert.equal((await api('GET', `/api/masterdata/customers/${customer.id}`)).customerName, customer.customerName)
  return { postedPreserved: true, draftPreserved: true, cancelledPreserved: true, adjustedLinkPreserved: true, countedPreserved: true, unicodeCustomerPreserved: true }
}

export async function verifyUpgradedApproval({ api, fixture, reviewerToken }) {
  const { draft, warehouse, product, counted, countedProduct } = fixture
  const stock = async (item, qty, amount) => {
    const rows = (await api('GET', `/api/inventory/balances?pageNo=1&pageSize=200&warehouseId=${warehouse.id}`)).records
    return checkStock(rows, warehouse.id, item.id, qty, amount)
  }
  const countedStockStillEmpty = async () => {
    const rows = (await api('GET', `/api/inventory/balances?pageNo=1&pageSize=200&warehouseId=${warehouse.id}`)).records
    if (rows.some(row => String(row.productId) === String(countedProduct.id))) checkStock(rows, warehouse.id, countedProduct.id, 0, 0)
  }
  await api('POST', `${adjustments}/${draft.id}/post`, undefined, undefined, 400, /审批通过/)
  assert.equal((await api('POST', `${adjustments}/${draft.id}/submit`)).status, 'SUBMITTED')
  await stock(product, 10, 100)
  await api('POST', `${adjustments}/${draft.id}/approve`, undefined, undefined, 400, /自己的单据/)
  assert.equal((await api('POST', `${adjustments}/${draft.id}/approve`, undefined, reviewerToken)).status, 'APPROVED')
  await stock(product, 10, 100)
  assert.equal((await api('POST', `${adjustments}/${draft.id}/post`)).status, 'POSTED')
  await api('POST', `${adjustments}/${draft.id}/post`, undefined, undefined, 400, /审批通过/)
  const draftStock = await stock(product, 12, 120)
  await countedStockStillEmpty()
  const pending = await api('POST', `${checks}/${counted.id}/adjust`)
  assert.equal(pending.status, 'ADJUSTMENT_PENDING')
  const generated = pending.generatedAdjustmentId
  assert(generated)
  assert.equal((await api('GET', `${adjustments}/${generated}`)).status, 'DRAFT')
  await countedStockStillEmpty()
  await api('POST', `${adjustments}/${generated}/submit`)
  await api('POST', `${adjustments}/${generated}/approve`, undefined, reviewerToken)
  assert.equal((await api('GET', `${checks}/${counted.id}`)).status, 'ADJUSTMENT_PENDING')
  await countedStockStillEmpty()
  await api('POST', `${adjustments}/${generated}/post`)
  assert.equal((await api('GET', `${checks}/${counted.id}`)).status, 'ADJUSTED')
  const countedStock = await stock(countedProduct, 3, 30)
  return { oldDraftRequiresApproval: true, selfApprovalRejected: true, duplicatePostingRejected: true,
    countWaitedForPosting: true, draftStock, countedStock }
}
