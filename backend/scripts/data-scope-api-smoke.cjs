/**
 * 数据范围 API 联调 smoke（需本机后端已启动 local + erp_codex_runtime）。
 *
 * 验证：
 * 1) 用户/角色 data-scope GET/PUT 可用
 * 2) 受限角色仅 SELF：采购/销售六类核心单据的列表、详情、CSV/报表口径一致
 * 3) 业务追踪不能通过隐藏业务号泄漏单据、工作流、日志、往来或异常信息
 * 4) 越权作废在业务副作用前拒绝，单据状态与库存流水不变
 * 5) 仓库范围 WAREHOUSE：库存余额、流水、详情与报表仅见授权仓
 *
 * 用法：
 *   node scripts/data-scope-api-smoke.cjs
 *   BASE_URL=http://127.0.0.1:8080 ADMIN_PASSWORD=LocalAdmin123 node scripts/data-scope-api-smoke.cjs
 */
const fs = require('fs')
const path = require('path')

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'LocalAdmin123'
const SUFFIX = String(Date.now()).slice(-8)
const ROLE_CODE = `DS_SMOKE_${SUFFIX}`
const USER_NAME = `ds_smoke_${SUFFIX}`
const CONTROL_USER_NAME = `ds_control_${SUFFIX}`
const USER_PASSWORD = 'DataScopeSmoke123'

const results = []

function row(id, title, pass, detail) {
  results.push({ id, title, pass: !!pass, detail: String(detail || '') })
  const mark = pass ? 'PASS' : 'FAIL'
  console.log(`[${mark}] ${id} ${title} — ${detail}`)
}

async function login(username, password) {
  const r = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const j = await r.json()
  if (String(j.code) !== '0') {
    throw new Error(`login ${username}: ${j.message || r.status}`)
  }
  return j.data
}

async function api(token, method, urlPath, body) {
  const opts = {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  }
  if (body !== undefined) {
    opts.body = JSON.stringify(body)
  }
  const r = await fetch(`${BASE}${urlPath}`, opts)
  const text = await r.text()
  let j
  try {
    j = JSON.parse(text)
  } catch {
    j = { code: String(r.status), message: text.slice(0, 300) }
  }
  return { status: r.status, body: j }
}

async function textApi(token, urlPath) {
  const r = await fetch(`${BASE}${urlPath}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return {
    status: r.status,
    contentType: r.headers.get('content-type') || '',
    text: await r.text(),
  }
}

function ok(res) {
  return res.status < 400 && String(res.body?.code) === '0'
}

function dataOf(res) {
  return res.body?.data
}

function recordsOf(res) {
  const d = dataOf(res)
  if (!d) return []
  if (Array.isArray(d.records)) return d.records
  if (Array.isArray(d)) return d
  return []
}

function isForbidden(res) {
  return res.status === 403 || String(res.body?.code) === '403'
}

function bizDate() {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
}

function requireOk(res, label) {
  if (!ok(res)) {
    throw new Error(`${label}: ${res.status} ${res.body?.message || JSON.stringify(res.body).slice(0, 300)}`)
  }
  return dataOf(res)
}

function containsId(res, id) {
  return recordsOf(res).some((record) => String(record?.id) === String(id))
}

function csvDataRows(text) {
  return String(text || '')
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .filter((line) => line.trim() !== '')
    .slice(1)
}

async function verifySelfDocument({
  idPrefix,
  title,
  limitedToken,
  controlToken,
  listPath,
  own,
  foreign,
  exportPath,
}) {
  const ownList = await api(
    limitedToken,
    'GET',
    `${listPath}?pageNo=1&pageSize=50&keyword=${encodeURIComponent(own.no)}`
  )
  const foreignList = await api(
    limitedToken,
    'GET',
    `${listPath}?pageNo=1&pageSize=50&keyword=${encodeURIComponent(foreign.no)}`
  )
  const controlForeignList = await api(
    controlToken,
    'GET',
    `${listPath}?pageNo=1&pageSize=50&keyword=${encodeURIComponent(foreign.no)}`
  )
  row(`${idPrefix}a`, `${title} SELF 列表仅见本人单`,
    ok(ownList) && containsId(ownList, own.id)
      && ok(foreignList) && !containsId(foreignList, foreign.id)
      && ok(controlForeignList) && containsId(controlForeignList, foreign.id),
    `ownVisible=${containsId(ownList, own.id)} foreignVisible=${containsId(foreignList, foreign.id)} controlVisible=${containsId(controlForeignList, foreign.id)}`)

  const hiddenDetail = await api(limitedToken, 'GET', `${listPath}/${foreign.id}`)
  row(`${idPrefix}b`, `${title} 他人详情被拒绝`, isForbidden(hiddenDetail),
    `status=${hiddenDetail.status} code=${hiddenDetail.body?.code} msg=${hiddenDetail.body?.message || ''}`)

  if (exportPath) {
    const ownExport = await textApi(
      limitedToken,
      `${exportPath}?keyword=${encodeURIComponent(own.no)}`
    )
    const foreignExport = await textApi(
      limitedToken,
      `${exportPath}?keyword=${encodeURIComponent(foreign.no)}`
    )
    row(`${idPrefix}c`, `${title} CSV 不导出他人单`,
      ownExport.status < 400
        && ownExport.contentType.includes('text/csv')
        && ownExport.text.includes(String(own.no))
        && foreignExport.status < 400
        && !foreignExport.text.includes(String(foreign.no)),
      `ownRows=${csvDataRows(ownExport.text).length} foreignRows=${csvDataRows(foreignExport.text).length}`)
  }
}

function collectMenuIds(nodes, out = []) {
  for (const n of nodes || []) {
    if (n?.id != null) out.push(String(n.id))
    if (n?.children?.length) collectMenuIds(n.children, out)
  }
  return out
}

async function loadActiveMenuIdSet(token) {
  // 优先全量树
  const tree = await api(token, 'GET', '/api/system/menus/tree')
  if (ok(tree) && Array.isArray(dataOf(tree))) {
    return new Set(collectMenuIds(dataOf(tree)))
  }
  // 分页兜底
  const ids = new Set()
  let pageNo = 1
  for (;;) {
    const page = await api(token, 'GET', `/api/system/menus?pageNo=${pageNo}&pageSize=200`)
    if (!ok(page)) break
    const recs = recordsOf(page)
    for (const m of recs) {
      if (m?.id != null && (m.deletedFlag == null || Number(m.deletedFlag) === 0)) {
        ids.add(String(m.id))
      }
    }
    const total = Number(dataOf(page)?.total || 0)
    if (pageNo * 200 >= total || recs.length === 0) break
    pageNo += 1
  }
  return ids
}

async function main() {
  console.log(`BASE_URL=${BASE}`)
  const adminLogin = await login(ADMIN_USER, ADMIN_PASSWORD)
  const adminToken = adminLogin.accessToken
  const adminUserId = String(adminLogin.user?.id || '4001')

  // 0) 健康：admin 能读自身 data-scope
  {
    const res = await api(adminToken, 'GET', `/api/system/users/${adminUserId}/data-scope`)
    row('S0', 'admin GET 用户 data-scope', ok(res), ok(res)
      ? `hasAll=${dataOf(res)?.hasAllScope} effectiveAll=${dataOf(res)?.effectiveHasAllScope}`
      : `${res.status} ${res.body?.message || JSON.stringify(res.body).slice(0, 120)}`)
  }

  // 1) 创建受限角色 + 复制 ERP_ADMIN 菜单，去掉角色 ALL，改为 SELF
  let roleId
  {
    const createRole = await api(adminToken, 'POST', '/api/system/roles', {
      roleCode: ROLE_CODE,
      roleName: `数据范围Smoke ${SUFFIX}`,
      remark: 'data-scope-api-smoke',
    })
    if (!ok(createRole)) {
      row('S1', '创建受限角色', false, createRole.body?.message || createRole.status)
      throw new Error('create role failed')
    }
    roleId = String(dataOf(createRole).id)
    // 后端 assignMenus 已过滤已删菜单；可直接复制 ERP_ADMIN 绑定（含历史幽灵 id 也不再整批失败）
    const menus = await api(adminToken, 'GET', '/api/system/roles/3002/menus')
    const rawMenuIds = (dataOf(menus)?.menuIds || []).map(String)
    let assignMenus = await api(adminToken, 'PUT', `/api/system/roles/${roleId}/menus`, {
      menuIds: rawMenuIds,
    })
    let assignedCount = 0
    if (ok(assignMenus)) {
      assignedCount = (dataOf(assignMenus)?.menuIds || []).length
    } else {
      // 兼容旧后端：求交后再赋
      const activeMenuIds = await loadActiveMenuIdSet(adminToken)
      const menuIds = rawMenuIds.filter((id) => activeMenuIds.has(id))
      assignMenus = await api(adminToken, 'PUT', `/api/system/roles/${roleId}/menus`, { menuIds })
      assignedCount = menuIds.length
    }
    row('S1', '创建受限角色并复制菜单（脏绑定可被后端过滤）',
      ok(createRole) && ok(assignMenus) && assignedCount > 0,
      `roleId=${roleId} raw=${rawMenuIds.length} assigned=${assignedCount} assignOk=${ok(assignMenus)} msg=${assignMenus.body?.message || ''}`)

    // 去掉默认 ALL：仅 SELF（用户创建的单据）
    const scopePut = await api(adminToken, 'PUT', `/api/system/roles/${roleId}/data-scope`, {
      hasAllScope: false,
      deptScoped: false,
      postScoped: false,
      selfScoped: true,
      warehouseIds: [],
    })
    const scopeGet = await api(adminToken, 'GET', `/api/system/roles/${roleId}/data-scope`)
    const scoped = dataOf(scopeGet)
    row('S2', '角色 data-scope 设为仅 SELF 并可回读',
      ok(scopePut) && ok(scopeGet) && scoped?.selfScoped === true && scoped?.hasAllScope === false,
      ok(scopeGet)
        ? `self=${scoped.selfScoped} all=${scoped.hasAllScope} warehouses=${(scoped.warehouseIds || []).length}`
        : `${scopePut.body?.message || scopeGet.body?.message}`)
  }

  // 2) 创建受限用户并赋角色
  let limitedUserId
  let limitedToken
  {
    const createUser = await api(adminToken, 'POST', '/api/system/users', {
      username: USER_NAME,
      password: USER_PASSWORD,
      employeeNo: `DS${SUFFIX}`,
      realName: `数据范围Smoke${SUFFIX}`,
      mobile: '1390000' + SUFFIX.slice(-4),
      deptId: 3501,
      postId: 3601,
      remark: 'data-scope-api-smoke',
    })
    if (!ok(createUser)) {
      row('S3', '创建受限用户', false, createUser.body?.message || createUser.status)
      throw new Error('create user failed')
    }
    limitedUserId = String(dataOf(createUser).id)
    const assignRoles = await api(adminToken, 'PUT', `/api/system/users/${limitedUserId}/roles`, {
      roleIds: [roleId],
    })
    // 用户级不追加 ALL
    const userScope = await api(adminToken, 'PUT', `/api/system/users/${limitedUserId}/data-scope`, {
      hasAllScope: false,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: [],
    })
    const userScopeGet = await api(adminToken, 'GET', `/api/system/users/${limitedUserId}/data-scope`)
    const us = dataOf(userScopeGet)
    row('S3', '创建受限用户并赋角色，生效范围含 SELF',
      ok(assignRoles) && ok(userScope) && ok(userScopeGet) && us?.effectiveSelfScoped === true && us?.effectiveHasAllScope === false,
      ok(userScopeGet)
        ? `userId=${limitedUserId} effectiveSelf=${us.effectiveSelfScoped} effectiveAll=${us.effectiveHasAllScope}`
        : userScopeGet.body?.message)

    const limitedLogin = await login(USER_NAME, USER_PASSWORD)
    limitedToken = limitedLogin.accessToken
    row('S4', '受限用户可登录', !!limitedToken, `username=${USER_NAME}`)
  }

  // 2b) 创建同角色的非管理员对照账号，用用户级 ALL 形成稳定对照
  let controlUserId
  let controlToken
  {
    const createControl = await api(adminToken, 'POST', '/api/system/users', {
      username: CONTROL_USER_NAME,
      password: USER_PASSWORD,
      employeeNo: `DC${SUFFIX}`,
      realName: `数据范围对照${SUFFIX}`,
      mobile: '1380000' + SUFFIX.slice(-4),
      deptId: 3501,
      postId: 3601,
      remark: 'data-scope-api-smoke-control',
    })
    if (!ok(createControl)) {
      row('S4b', '创建非管理员 ALL 对照账号', false, createControl.body?.message || createControl.status)
      throw new Error('create control user failed')
    }
    controlUserId = String(dataOf(createControl).id)
    const assignRoles = await api(adminToken, 'PUT', `/api/system/users/${controlUserId}/roles`, {
      roleIds: [roleId],
    })
    const assignAll = await api(adminToken, 'PUT', `/api/system/users/${controlUserId}/data-scope`, {
      hasAllScope: true,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: [],
    })
    const controlScope = await api(adminToken, 'GET', `/api/system/users/${controlUserId}/data-scope`)
    const controlLogin = await login(CONTROL_USER_NAME, USER_PASSWORD)
    controlToken = controlLogin.accessToken
    row('S4b', '非管理员对照账号生效范围为 ALL',
      ok(assignRoles) && ok(assignAll) && ok(controlScope)
        && dataOf(controlScope)?.effectiveHasAllScope === true && !!controlToken,
      `userId=${controlUserId} effectiveAll=${dataOf(controlScope)?.effectiveHasAllScope}`)
  }

  // 3) 构造采购/销售六类单据和库存流水。两账号权限相同，仅 data-scope 不同。
  const date = bizDate()
  const mainWarehouseId = '4501'
  const suppliers = requireOk(
    await api(adminToken, 'GET', '/api/masterdata/suppliers?pageNo=1&pageSize=100&status=ACTIVE'),
    'load suppliers'
  )
  const products = requireOk(
    await api(adminToken, 'GET', '/api/masterdata/products?pageNo=1&pageSize=200&status=ACTIVE'),
    'load products'
  )
  const customers = requireOk(
    await api(adminToken, 'GET', '/api/masterdata/customers?pageNo=1&pageSize=100&status=ACTIVE'),
    'load customers'
  )
  const supplierId = (suppliers.records || [])[0]?.id
  const product = (products.records || []).find((item) =>
    item.lotControlled !== true
      && item.serialControlled !== true
      && item.inspectionRequired !== true
      && String(item.productType || '').toUpperCase() !== 'SERVICE')
  const customer = (customers.records || []).find((item) => Number(item.creditLimit || 0) >= 100)
    || (customers.records || [])[0]
  if (!supplierId || !product?.id || !customer?.id) {
    throw new Error('active supplier/product/customer fixture not found')
  }

  async function createPurchaseOrder(token, marker) {
    const created = requireOk(await api(token, 'POST', '/api/purchase/orders', {
      supplierId,
      orderDate: date,
      deliveryDate: date,
      remark: marker,
      lines: [{ productId: product.id, qty: 5, price: 1, taxRate: 0, remark: marker }],
    }), `create purchase order ${marker}`)
    return { id: String(created.id), no: String(created.orderNo), data: created }
  }

  async function approvePurchaseOrder(ownerToken, document) {
    requireOk(await api(ownerToken, 'POST', `/api/purchase/orders/${document.id}/submit`, {
      remark: `submit-${SUFFIX}`,
    }), `submit purchase order ${document.no}`)
    requireOk(await api(adminToken, 'POST', `/api/purchase/orders/${document.id}/approve`, {
      remark: `approve-${SUFFIX}`,
    }), `approve purchase order ${document.no}`)
  }

  async function createPurchaseReceipt(token, order, marker) {
    const orderLineId = order.data.lines?.[0]?.id
    const created = requireOk(await api(token, 'POST', '/api/purchase/receipts', {
      orderId: order.id,
      warehouseId: mainWarehouseId,
      receiptDate: date,
      remark: marker,
      lines: [{ orderLineId, qty: 5, remark: marker }],
    }), `create purchase receipt ${marker}`)
    const posted = requireOk(
      await api(token, 'POST', `/api/purchase/receipts/${created.id}/post`),
      `post purchase receipt ${created.receiptNo}`
    )
    return { id: String(posted.id), no: String(posted.receiptNo), data: posted }
  }

  async function createPurchaseReturn(token, receipt, marker) {
    const receiptLineId = receipt.data.lines?.[0]?.id
    const created = requireOk(await api(token, 'POST', '/api/purchase/returns', {
      receiptId: receipt.id,
      returnDate: date,
      remark: marker,
      lines: [{ receiptLineId, qty: 1, remark: marker }],
    }), `create purchase return ${marker}`)
    return { id: String(created.id), no: String(created.returnNo), data: created }
  }

  async function createSalesOrder(token, marker) {
    const created = requireOk(await api(token, 'POST', '/api/sales/orders', {
      customerId: customer.id,
      warehouseId: mainWarehouseId,
      orderDate: date,
      deliveryDate: date,
      remark: marker,
      lines: [{ productId: product.id, qty: 1, price: 0, taxRate: 0, remark: marker }],
    }), `create sales order ${marker}`)
    return { id: String(created.id), no: String(created.orderNo), data: created }
  }

  async function approveSalesOrder(ownerToken, document) {
    requireOk(await api(ownerToken, 'POST', `/api/sales/orders/${document.id}/submit`, {
      remark: `submit-${SUFFIX}`,
    }), `submit sales order ${document.no}`)
    requireOk(await api(adminToken, 'POST', `/api/sales/orders/${document.id}/approve`, {
      remark: `approve-${SUFFIX}`,
    }), `approve sales order ${document.no}`)
  }

  async function createSalesDelivery(token, order, marker) {
    const orderLineId = order.data.lines?.[0]?.id
    const created = requireOk(await api(token, 'POST', '/api/sales/deliveries', {
      orderId: order.id,
      warehouseId: mainWarehouseId,
      deliveryDate: date,
      remark: marker,
      logisticsStatus: 'PENDING_SHIP',
      lines: [{ orderLineId, qty: 1, remark: marker }],
    }), `create sales delivery ${marker}`)
    const posted = requireOk(
      await api(token, 'POST', `/api/sales/deliveries/${created.id}/post`),
      `post sales delivery ${created.deliveryNo}`
    )
    return { id: String(posted.id), no: String(posted.deliveryNo), data: posted }
  }

  async function createSalesReturn(token, delivery, marker) {
    const deliveryLineId = delivery.data.lines?.[0]?.id
    const created = requireOk(await api(token, 'POST', '/api/sales/returns', {
      deliveryId: delivery.id,
      returnDate: date,
      remark: marker,
      lines: [{ deliveryLineId, qty: 1, remark: marker }],
    }), `create sales return ${marker}`)
    return { id: String(created.id), no: String(created.returnNo), data: created }
  }

  const foreignPurchaseOrder = await createPurchaseOrder(controlToken, `foreign-po-${SUFFIX}`)
  const ownPurchaseOrder = await createPurchaseOrder(limitedToken, `own-po-${SUFFIX}`)
  await approvePurchaseOrder(controlToken, foreignPurchaseOrder)
  await approvePurchaseOrder(limitedToken, ownPurchaseOrder)
  const foreignPurchaseReceipt = await createPurchaseReceipt(controlToken, foreignPurchaseOrder, `foreign-pr-${SUFFIX}`)
  const ownPurchaseReceipt = await createPurchaseReceipt(limitedToken, ownPurchaseOrder, `own-pr-${SUFFIX}`)
  const foreignPurchaseReturn = await createPurchaseReturn(controlToken, foreignPurchaseReceipt, `foreign-prt-${SUFFIX}`)
  const ownPurchaseReturn = await createPurchaseReturn(limitedToken, ownPurchaseReceipt, `own-prt-${SUFFIX}`)

  const foreignSalesOrder = await createSalesOrder(controlToken, `foreign-so-${SUFFIX}`)
  const ownSalesOrder = await createSalesOrder(limitedToken, `own-so-${SUFFIX}`)
  await approveSalesOrder(controlToken, foreignSalesOrder)
  await approveSalesOrder(limitedToken, ownSalesOrder)
  const foreignSalesDelivery = await createSalesDelivery(controlToken, foreignSalesOrder, `foreign-sd-${SUFFIX}`)
  const ownSalesDelivery = await createSalesDelivery(limitedToken, ownSalesOrder, `own-sd-${SUFFIX}`)
  const foreignSalesReturn = await createSalesReturn(controlToken, foreignSalesDelivery, `foreign-srt-${SUFFIX}`)
  const ownSalesReturn = await createSalesReturn(limitedToken, ownSalesDelivery, `own-srt-${SUFFIX}`)
  row('S5', '六类核心单据及库存流水夹具创建完成', true,
    `productId=${product.id} PO=${ownPurchaseOrder.no}/${foreignPurchaseOrder.no} SO=${ownSalesOrder.no}/${foreignSalesOrder.no}`)

  // 4) SELF：六类单据列表/详情/CSV，订单报表和业务追踪使用相同口径。
  await verifySelfDocument({
    idPrefix: 'S6PO', title: '采购订单', limitedToken, controlToken,
    listPath: '/api/purchase/orders', exportPath: '/api/purchase/orders/export',
    own: ownPurchaseOrder, foreign: foreignPurchaseOrder,
  })
  await verifySelfDocument({
    idPrefix: 'S6PR', title: '采购入库', limitedToken, controlToken,
    listPath: '/api/purchase/receipts', exportPath: '/api/purchase/receipts/export',
    own: ownPurchaseReceipt, foreign: foreignPurchaseReceipt,
  })
  await verifySelfDocument({
    idPrefix: 'S6PT', title: '采购退货', limitedToken, controlToken,
    listPath: '/api/purchase/returns', exportPath: '/api/purchase/returns/export',
    own: ownPurchaseReturn, foreign: foreignPurchaseReturn,
  })
  await verifySelfDocument({
    idPrefix: 'S6SO', title: '销售订单', limitedToken, controlToken,
    listPath: '/api/sales/orders', own: ownSalesOrder, foreign: foreignSalesOrder,
  })
  await verifySelfDocument({
    idPrefix: 'S6SD', title: '销售出库', limitedToken, controlToken,
    listPath: '/api/sales/deliveries', own: ownSalesDelivery, foreign: foreignSalesDelivery,
  })
  await verifySelfDocument({
    idPrefix: 'S6ST', title: '销售退货', limitedToken, controlToken,
    listPath: '/api/sales/returns', own: ownSalesReturn, foreign: foreignSalesReturn,
  })

  for (const reportCase of [
    { id: 'S7PO', title: '采购订单报表', path: '/api/reports/purchase-orders', own: ownPurchaseOrder, foreign: foreignPurchaseOrder },
    { id: 'S7SO', title: '销售订单报表', path: '/api/reports/sales-orders', own: ownSalesOrder, foreign: foreignSalesOrder },
  ]) {
    const ownReport = await api(limitedToken, 'GET', `${reportCase.path}?pageNo=1&pageSize=50&keyword=${encodeURIComponent(reportCase.own.no)}`)
    const foreignReport = await api(limitedToken, 'GET', `${reportCase.path}?pageNo=1&pageSize=50&keyword=${encodeURIComponent(reportCase.foreign.no)}`)
    const ownCsv = await textApi(limitedToken, `${reportCase.path}/export?keyword=${encodeURIComponent(reportCase.own.no)}`)
    const foreignCsv = await textApi(limitedToken, `${reportCase.path}/export?keyword=${encodeURIComponent(reportCase.foreign.no)}`)
    row(reportCase.id, `${reportCase.title}与页面 SELF 口径一致`,
      ok(ownReport) && containsId(ownReport, reportCase.own.id)
        && ok(foreignReport) && !containsId(foreignReport, reportCase.foreign.id)
        && ownCsv.status < 400 && ownCsv.text.includes(reportCase.own.no)
        && foreignCsv.status < 400 && !foreignCsv.text.includes(reportCase.foreign.no),
      `ownTotal=${dataOf(ownReport)?.total} foreignTotal=${dataOf(foreignReport)?.total} ownCsvRows=${csvDataRows(ownCsv.text).length} foreignCsvRows=${csvDataRows(foreignCsv.text).length}`)
  }

  const hiddenTrace = await api(
    limitedToken,
    'GET',
    `/api/reports/business-traces?keyword=${encodeURIComponent(foreignPurchaseOrder.no)}`
  )
  const trace = dataOf(hiddenTrace) || {}
  const tracePayload = JSON.stringify({
    documents: trace.documents || [],
    timeline: trace.timeline || [],
    exceptionTickets: trace.exceptionTickets || [],
  })
  const controlTrace = await api(
    controlToken,
    'GET',
    `/api/reports/business-traces?keyword=${encodeURIComponent(foreignPurchaseOrder.no)}`
  )
  row('S7TR', '业务追踪不通过隐藏编号泄漏单据或二级数据',
    ok(hiddenTrace)
      && !tracePayload.includes(foreignPurchaseOrder.no)
      && (trace.documents || []).length === 0
      && (trace.timeline || []).length === 0
      && ok(controlTrace)
      && JSON.stringify(dataOf(controlTrace) || {}).includes(foreignPurchaseOrder.no),
    `limitedDocuments=${(trace.documents || []).length} limitedTimeline=${(trace.timeline || []).length} controlVisible=${JSON.stringify(dataOf(controlTrace) || {}).includes(foreignPurchaseOrder.no)}`)

  // 5) 越权写：拒绝发生在状态修改和下游库存流水之前。
  const foreignDraft = await createPurchaseOrder(controlToken, `foreign-write-${SUFFIX}`)
  const beforeDraft = requireOk(
    await api(controlToken, 'GET', `/api/purchase/orders/${foreignDraft.id}`),
    'load foreign draft before forbidden write'
  )
  const beforeTransactions = await api(
    controlToken,
    'GET',
    `/api/inventory/transactions?pageNo=1&pageSize=50&bizNo=${encodeURIComponent(foreignDraft.no)}`
  )
  const forbiddenCancel = await api(limitedToken, 'POST', `/api/purchase/orders/${foreignDraft.id}/cancel`)
  const afterDraft = requireOk(
    await api(controlToken, 'GET', `/api/purchase/orders/${foreignDraft.id}`),
    'load foreign draft after forbidden write'
  )
  const afterTransactions = await api(
    controlToken,
    'GET',
    `/api/inventory/transactions?pageNo=1&pageSize=50&bizNo=${encodeURIComponent(foreignDraft.no)}`
  )
  row('S8', '越权作废被拒绝且业务状态、明细和库存流水不变',
    isForbidden(forbiddenCancel)
      && JSON.stringify(beforeDraft) === JSON.stringify(afterDraft)
      && ok(beforeTransactions) && ok(afterTransactions)
      && Number(dataOf(beforeTransactions)?.total || 0) === Number(dataOf(afterTransactions)?.total || 0),
    `status=${forbiddenCancel.status} beforeStatus=${beforeDraft.status} afterStatus=${afterDraft.status} beforeTxn=${dataOf(beforeTransactions)?.total} afterTxn=${dataOf(afterTransactions)?.total}`)

  // 6) 仓库范围：余额、流水、详情、CSV 和报表均只见 MAIN_WH(4501)。
  let foreignBalance
  let foreignTransaction
  const warehousePage = requireOk(
    await api(adminToken, 'GET', '/api/masterdata/warehouses?pageNo=1&pageSize=100&status=ACTIVE'),
    'load warehouses'
  )
  for (const warehouse of warehousePage.records || []) {
    if (String(warehouse.id) === mainWarehouseId) continue
    if (!foreignBalance) {
      const balances = await api(controlToken, 'GET', `/api/inventory/balances?pageNo=1&pageSize=5&warehouseId=${warehouse.id}`)
      foreignBalance = recordsOf(balances)[0]
    }
    if (!foreignTransaction) {
      const transactions = await api(controlToken, 'GET', `/api/inventory/transactions?pageNo=1&pageSize=5&warehouseId=${warehouse.id}`)
      foreignTransaction = recordsOf(transactions)[0]
    }
    if (foreignBalance && foreignTransaction) break
  }

  const scopeWh = await api(adminToken, 'PUT', `/api/system/roles/${roleId}/data-scope`, {
    hasAllScope: false,
    deptScoped: false,
    postScoped: false,
    selfScoped: false,
    warehouseIds: [mainWarehouseId],
  })
  limitedToken = (await login(USER_NAME, USER_PASSWORD)).accessToken

  const limitedBalances = await api(limitedToken, 'GET', '/api/inventory/balances?pageNo=1&pageSize=200')
  const balanceReport = await api(limitedToken, 'GET', '/api/reports/inventory-balances?pageNo=1&pageSize=200')
  const balanceRows = recordsOf(limitedBalances)
  row('S9BA', 'WAREHOUSE 余额列表与报表仅见授权仓',
    ok(scopeWh) && ok(limitedBalances) && ok(balanceReport)
      && balanceRows.every((item) => String(item.warehouseId) === mainWarehouseId)
      && recordsOf(balanceReport).every((item) => String(item.warehouseId) === mainWarehouseId)
      && Number(dataOf(limitedBalances)?.total || 0) === Number(dataOf(balanceReport)?.total || 0),
    `listTotal=${dataOf(limitedBalances)?.total} reportTotal=${dataOf(balanceReport)?.total} onlyMain=${balanceRows.every((item) => String(item.warehouseId) === mainWarehouseId)}`)

  const limitedTransactions = await api(limitedToken, 'GET', '/api/inventory/transactions?pageNo=1&pageSize=200')
  const transactionReport = await api(limitedToken, 'GET', '/api/reports/inventory-transactions?pageNo=1&pageSize=200')
  const transactionRows = recordsOf(limitedTransactions)
  row('S9TX', 'WAREHOUSE 流水列表与报表仅见授权仓',
    ok(limitedTransactions) && ok(transactionReport)
      && transactionRows.every((item) => String(item.warehouseId) === mainWarehouseId)
      && recordsOf(transactionReport).every((item) => String(item.warehouseId) === mainWarehouseId)
      && Number(dataOf(limitedTransactions)?.total || 0) === Number(dataOf(transactionReport)?.total || 0),
    `listTotal=${dataOf(limitedTransactions)?.total} reportTotal=${dataOf(transactionReport)?.total} onlyMain=${transactionRows.every((item) => String(item.warehouseId) === mainWarehouseId)}`)

  if (foreignBalance) {
    const hiddenBalanceList = await api(limitedToken, 'GET', `/api/inventory/balances?pageNo=1&pageSize=20&warehouseId=${foreignBalance.warehouseId}`)
    const hiddenBalanceDetail = await api(limitedToken, 'GET', `/api/inventory/balances/${foreignBalance.id}`)
    const hiddenBalanceReport = await api(limitedToken, 'GET', `/api/reports/inventory-balances?pageNo=1&pageSize=20&warehouseId=${foreignBalance.warehouseId}`)
    const hiddenBalanceCsv = await textApi(limitedToken, `/api/inventory/balances/export?warehouseId=${foreignBalance.warehouseId}`)
    row('S9BD', '非授权仓余额列表/详情/报表/CSV 均不可见',
      ok(hiddenBalanceList) && recordsOf(hiddenBalanceList).length === 0
        && isForbidden(hiddenBalanceDetail)
        && ok(hiddenBalanceReport) && recordsOf(hiddenBalanceReport).length === 0
        && hiddenBalanceCsv.status < 400 && csvDataRows(hiddenBalanceCsv.text).length === 0,
      `warehouseId=${foreignBalance.warehouseId} list=${recordsOf(hiddenBalanceList).length} detailStatus=${hiddenBalanceDetail.status} report=${recordsOf(hiddenBalanceReport).length} csvRows=${csvDataRows(hiddenBalanceCsv.text).length}`)
  } else {
    row('S9BD', '非授权仓余额列表/详情/报表/CSV 均不可见', false, 'control account has no foreign warehouse balance fixture')
  }

  if (foreignTransaction) {
    const hiddenTxnList = await api(limitedToken, 'GET', `/api/inventory/transactions?pageNo=1&pageSize=20&warehouseId=${foreignTransaction.warehouseId}`)
    const hiddenTxnDetail = await api(limitedToken, 'GET', `/api/inventory/transactions/${foreignTransaction.id}`)
    const hiddenTxnReport = await api(limitedToken, 'GET', `/api/reports/inventory-transactions?pageNo=1&pageSize=20&warehouseId=${foreignTransaction.warehouseId}`)
    const hiddenTxnCsv = await textApi(limitedToken, `/api/reports/inventory-transactions/export?warehouseId=${foreignTransaction.warehouseId}`)
    row('S9TD', '非授权仓流水列表/详情/报表/CSV 均不可见',
      ok(hiddenTxnList) && recordsOf(hiddenTxnList).length === 0
        && isForbidden(hiddenTxnDetail)
        && ok(hiddenTxnReport) && recordsOf(hiddenTxnReport).length === 0
        && hiddenTxnCsv.status < 400 && csvDataRows(hiddenTxnCsv.text).length === 0,
      `warehouseId=${foreignTransaction.warehouseId} list=${recordsOf(hiddenTxnList).length} detailStatus=${hiddenTxnDetail.status} report=${recordsOf(hiddenTxnReport).length} csvRows=${csvDataRows(hiddenTxnCsv.text).length}`)
  } else {
    row('S9TD', '非授权仓流水列表/详情/报表/CSV 均不可见', false, 'control account has no foreign warehouse transaction fixture')
  }

  // 7) 恢复：受限角色改回 ALL，避免联调账号长期保留受限配置（测试用户可留）。
  {
    const restore = await api(adminToken, 'PUT', `/api/system/roles/${roleId}/data-scope`, {
      hasAllScope: true,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: [],
    })
    row('S10', 'smoke 后角色范围恢复为 ALL', ok(restore), `roleId=${roleId}`)
  }

  const failed = results.filter((r) => !r.pass)
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: BASE,
    roleCode: ROLE_CODE,
    username: USER_NAME,
    controlUsername: CONTROL_USER_NAME,
    roleId,
    limitedUserId,
    controlUserId,
    summary: { total: results.length, passed: results.length - failed.length, failed: failed.length },
    results,
  }
  const outDir = path.join('target', 'data-scope-api-smoke')
  fs.mkdirSync(outDir, { recursive: true })
  const outFile = path.join(outDir, `report-${SUFFIX}.json`)
  fs.writeFileSync(outFile, JSON.stringify(report, null, 2), 'utf8')
  console.log(`\nReport: ${outFile}`)
  console.log(`Summary: ${report.summary.passed}/${report.summary.total} passed`)
  if (failed.length) {
    process.exitCode = 1
  }
}

main().catch((err) => {
  console.error('FATAL', err)
  process.exitCode = 1
})
