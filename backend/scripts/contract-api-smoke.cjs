/**
 * 合同管理 API smoke。需本机后端已启动，并连接可写的联调库。
 *
 * 用法：
 *   node scripts/contract-api-smoke.cjs
 *   BASE_URL=http://127.0.0.1:8080 ADMIN_PASSWORD=LocalAdmin123 node scripts/contract-api-smoke.cjs
 */
const fs = require('fs')
const path = require('path')

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'LocalAdmin123'
const SUFFIX = String(Date.now()).slice(-8)
const shanghaiDateParts = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).formatToParts(new Date())
const shanghaiDatePart = (type) => shanghaiDateParts.find((part) => part.type === type)?.value
const TODAY = `${shanghaiDatePart('year')}-${shanghaiDatePart('month')}-${shanghaiDatePart('day')}`
const results = []

function record(id, title, pass, detail) {
  results.push({ id, title, pass: Boolean(pass), detail: String(detail || '') })
  console.log(`[${pass ? 'PASS' : 'FAIL'}] ${id} ${title} - ${detail}`)
}

async function api(token, method, urlPath, body) {
  const response = await fetch(`${BASE}${urlPath}`, {
    method,
    headers: {
      Authorization: token ? `Bearer ${token}` : undefined,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  let parsed
  try { parsed = JSON.parse(text) } catch { parsed = { code: String(response.status), message: text } }
  return { status: response.status, body: parsed }
}

async function download(token, urlPath) {
  const response = await fetch(`${BASE}${urlPath}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return {
    status: response.status,
    contentType: response.headers.get('content-type') || '',
    disposition: response.headers.get('content-disposition') || '',
    text: await response.text(),
  }
}

function ok(response) {
  return response.status < 400 && String(response.body?.code) === '0'
}

async function must(token, method, urlPath, body, label) {
  const response = await api(token, method, urlPath, body)
  if (!ok(response)) throw new Error(`${label}: ${response.body?.message || response.status}`)
  return response.body.data
}

async function main() {
  console.log(`BASE_URL=${BASE}`)
  const login = await api(null, 'POST', '/api/auth/login', {
    username: ADMIN_USER,
    password: ADMIN_PASSWORD,
  })
  if (!ok(login)) throw new Error(`登录失败：${login.body?.message || login.status}`)
  const token = login.body.data.accessToken
  const permissions = new Set(login.body.data.permissions || [])
  const expectedPermissions = ['contract:view', 'contract:manage', 'contract:approve']
  record('C0', '合同权限已授予 ERP_ADMIN', expectedPermissions.every((item) => permissions.has(item)), expectedPermissions.join(', '))

  const customer = await must(token, 'POST', '/api/masterdata/customers', {
    customerCode: `CTC${SUFFIX}`,
    customerName: `合同 Smoke 客户 ${SUFFIX}`,
    customerType: 'ENTERPRISE',
    contactName: 'contract-smoke',
    contactPhone: '13800000003',
    creditLimit: 100000,
    settlementMethod: 'BANK_TRANSFER',
    address: 'contract-smoke',
    remark: `contract-smoke-${SUFFIX}`,
  }, '创建合同 smoke 客户')
  const product = await must(token, 'POST', '/api/masterdata/products', {
    productCode: `CTP${SUFFIX}`,
    productName: `合同 Smoke 商品 ${SUFFIX}`,
    productType: 'GOODS',
    categoryName: '合同联调',
    specification: 'smoke',
    unitName: '件',
    purchasePrice: 10,
    salePrice: 20,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    inspectionRequired: false,
    remark: `contract-smoke-${SUFFIX}`,
  }, '创建合同 smoke 商品')

  const contractName = `合同 API Smoke ${SUFFIX}`
  const basePayload = {
    contractType: 'SALES',
    customerId: customer.id,
    contractName,
    signedDate: TODAY,
    effectiveFrom: TODAY,
    remark: `contract-api-smoke-${SUFFIX}`,
    lines: [{ productId: product.id, quantity: 10, unitPrice: 20, remark: 'initial' }],
  }
  const create = await api(token, 'POST', '/api/contracts', basePayload)
  record('C1', '创建销售合同草稿', ok(create)
    && create.body.data.status === 'DRAFT'
    && Number(create.body.data.totalAmount) === 200,
  ok(create) ? `id=${create.body.data.id} no=${create.body.data.contractNo}` : create.body?.message)
  if (!ok(create)) throw new Error('创建合同失败')
  const contractId = create.body.data.id

  const update = await api(token, 'PUT', `/api/contracts/${contractId}`, {
    ...basePayload,
    remark: `contract-api-smoke-updated-${SUFFIX}`,
    lines: [{ productId: product.id, quantity: 12, unitPrice: 25, remark: 'updated' }],
  })
  record('C2', '编辑草稿并重算金额', ok(update)
    && update.body.data.status === 'DRAFT'
    && Number(update.body.data.totalAmount) === 300,
  ok(update) ? `amount=${update.body.data.totalAmount}` : update.body?.message)

  const versions = await api(token, 'GET', `/api/contracts/${contractId}/versions`)
  const events = ok(versions) ? (versions.body.data || []).map((item) => item.eventType) : []
  record('C3', '创建和编辑版本可追溯', ok(versions)
    && events.includes('CREATED')
    && events.includes('EDITED'),
  ok(versions) ? `events=${events.join(',')}` : versions.body?.message)

  const submit = await api(token, 'POST', `/api/contracts/${contractId}/submit`)
  record('C4', '合同提交审批', ok(submit) && submit.body.data.status === 'SUBMITTED', ok(submit) ? submit.body.data.status : submit.body?.message)

  const approve = await api(token, 'POST', `/api/contracts/${contractId}/approve`)
  record('C5', '合同审批生效', ok(approve) && approve.body.data.status === 'ACTIVE', ok(approve) ? approve.body.data.status : approve.body?.message)

  const detail = await api(token, 'GET', `/api/contracts/${contractId}`)
  record('C6', '生效合同详情与履约余额正确', ok(detail)
    && detail.body.data.status === 'ACTIVE'
    && Number(detail.body.data.lines?.[0]?.quantity) === 12
    && Number(detail.body.data.lines?.[0]?.committedQuantity || 0) === 0
    && Number(detail.body.data.lines?.[0]?.fulfilledQuantity || 0) === 0,
  ok(detail) ? `quantity=${detail.body.data.lines?.[0]?.quantity}` : detail.body?.message)

  const page = await api(token, 'GET', `/api/contracts?keyword=${encodeURIComponent(contractName)}&status=ACTIVE&pageSize=20`)
  const listed = ok(page) && (page.body.data.records || []).some((item) => String(item.id) === String(contractId))
  record('C7', '生效合同可按关键字和状态回读', listed, ok(page) ? `total=${page.body.data.total}` : page.body?.message)

  const exported = await download(token, `/api/contracts/export?keyword=${encodeURIComponent(contractName)}`)
  record('C8', '合同台账可导出 CSV', exported.status < 400
    && exported.contentType.includes('text/csv')
    && exported.disposition.includes('attachment')
    && exported.text.includes(contractName),
  `${exported.status} ${exported.contentType} bytes=${Buffer.byteLength(exported.text)}`)

  const close = await api(token, 'POST', `/api/contracts/${contractId}/close`)
  record('C9', '生效合同关闭', ok(close) && close.body.data.status === 'CLOSED', ok(close) ? close.body.data.status : close.body?.message)

  const closedPage = await api(token, 'GET', `/api/contracts?keyword=${encodeURIComponent(contractName)}&status=CLOSED&pageSize=20`)
  const closedListed = ok(closedPage) && (closedPage.body.data.records || []).some((item) => String(item.id) === String(contractId))
  record('C10', '关闭合同可按状态回读', closedListed, ok(closedPage) ? `total=${closedPage.body.data.total}` : closedPage.body?.message)

  const outputDir = path.resolve(__dirname, '..', 'target', 'contract-api-smoke')
  fs.mkdirSync(outputDir, { recursive: true })
  const reportPath = path.join(outputDir, `report-${SUFFIX}.json`)
  fs.writeFileSync(reportPath, JSON.stringify({ baseUrl: BASE, contractId, customerId: customer.id, productId: product.id, results }, null, 2))
  const failed = results.filter((item) => !item.pass)
  console.log(`RESULT ${results.length - failed.length}/${results.length} PASS`)
  console.log(`REPORT ${reportPath}`)
  if (failed.length) process.exitCode = 1
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error))
  process.exitCode = 1
})
