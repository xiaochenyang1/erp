/**
 * 数据范围 API 联调 smoke（需本机后端已启动 local + erp_codex_runtime）。
 *
 * 验证：
 * 1) 用户/角色 data-scope GET/PUT 可用
 * 2) 受限角色去掉 ALL 后仅 SELF：仅见自己创建的采购单；他人单据详情 403
 * 3) 仓库范围 WAREHOUSE：库存余额仅见授权仓
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

  // 3) 采购订单 SELF：admin 有 1 条历史单；受限用户列表不应包含他人单；他人详情 403
  let foreignOrderId
  let ownOrderId
  {
    const adminList = await api(adminToken, 'GET', '/api/purchase/orders?pageNo=1&pageSize=20')
    const adminOrders = recordsOf(adminList)
    foreignOrderId = adminOrders.find((o) => String(o.createdBy || o.createdById || '') !== limitedUserId)?.id
      || adminOrders[0]?.id
    row('S5', 'admin 可列出采购订单作为对照',
      ok(adminList) && adminOrders.length > 0,
      `total=${dataOf(adminList)?.total} sampleId=${foreignOrderId || '-'}`)

    // 受限用户建一张自己的草稿单（若无供应商可跳过 create，仅测列表为空/详情拒绝）
    const suppliers = await api(adminToken, 'GET', '/api/masterdata/suppliers?pageNo=1&pageSize=5&status=ACTIVE')
    const products = await api(adminToken, 'GET', '/api/masterdata/products?pageNo=1&pageSize=5&status=ACTIVE')
    const supplierId = recordsOf(suppliers)[0]?.id
    const productId = recordsOf(products)[0]?.id
    if (supplierId && productId) {
      // 用 admin 代建会记 createdBy=admin；改用受限用户自己创建
      const createPo = await api(limitedToken, 'POST', '/api/purchase/orders', {
        supplierId,
        orderDate: new Date().toISOString().slice(0, 10),
        remark: `data-scope-smoke-${SUFFIX}`,
        lines: [{ productId, qty: 1, price: 1, taxRate: 0 }],
      })
      if (ok(createPo)) {
        ownOrderId = String(dataOf(createPo).id)
      } else {
        console.log('  note: create PO as limited user failed:', createPo.body?.message || createPo.status)
      }
    }

    const limitedList = await api(limitedToken, 'GET', '/api/purchase/orders?pageNo=1&pageSize=50')
    const limitedOrders = recordsOf(limitedList)
    const seesForeign = foreignOrderId
      ? limitedOrders.some((o) => String(o.id) === String(foreignOrderId))
      : false
    const seesOwn = ownOrderId
      ? limitedOrders.some((o) => String(o.id) === String(ownOrderId))
      : false

    // 空列表 + 不看见他人单 = SELF 过滤有效；若成功创建本单则必须出现
    row('S6', 'SELF 列表不出现他人采购单',
      ok(limitedList) && !seesForeign,
      `limitedCount=${limitedOrders.length} seesForeign=${seesForeign} ownId=${ownOrderId || '-'}`)

    row('S6b', 'SELF 列表可见本人创建单',
      !!ownOrderId && seesOwn,
      ownOrderId ? `ownId=${ownOrderId} seesOwn=${seesOwn}` : 'create own PO failed')

    if (foreignOrderId) {
      const detail = await api(limitedToken, 'GET', `/api/purchase/orders/${foreignOrderId}`)
      row('S7', 'SELF 详情访问他人采购单被拒绝',
        isForbidden(detail) || (ok(detail) === false && String(detail.body?.message || '').includes('权')),
        `status=${detail.status} code=${detail.body?.code} msg=${detail.body?.message || ''}`)
    } else {
      row('S7', 'SELF 详情访问他人采购单被拒绝', false, 'no foreign order id')
    }
  }

  // 4) 仓库范围：角色改为仅 MAIN_WH(4501)，库存余额不应含其它仓
  {
    const scopeWh = await api(adminToken, 'PUT', `/api/system/roles/${roleId}/data-scope`, {
      hasAllScope: false,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: [4501],
    })
    // 重新登录以刷新 principal（虽有 evictAll，稳妥起见 re-login）
    const reLogin = await login(USER_NAME, USER_PASSWORD)
    limitedToken = reLogin.accessToken

    const adminBal = await api(adminToken, 'GET', '/api/inventory/balances?pageNo=1&pageSize=100')
    const limitedBal = await api(limitedToken, 'GET', '/api/inventory/balances?pageNo=1&pageSize=100')
    const adminRecs = recordsOf(adminBal)
    const limitedRecs = recordsOf(limitedBal)
    const foreignWhExists = adminRecs.some((r) => String(r.warehouseId) !== '4501')
    const limitedOnly4501 = limitedRecs.every((r) => String(r.warehouseId) === '4501')
    const limitedHasOther = limitedRecs.some((r) => String(r.warehouseId) !== '4501')

    // only4501：有数据时全部为 4501；无数据时也算通过（主仓可能无余额行），但不能出现其它仓
    row('S8', '角色 WAREHOUSE=4501 后库存余额仅主仓',
      ok(scopeWh) && ok(limitedBal) && !limitedHasOther && (limitedRecs.length === 0 || limitedOnly4501),
      `adminWhMix=${foreignWhExists} adminCount=${adminRecs.length} limitedCount=${limitedRecs.length} only4501=${limitedOnly4501} roleScopeOk=${ok(scopeWh)}`)

    // 若 admin 侧存在非 4501 余额，受限侧不得出现这些 id
    if (foreignWhExists && limitedRecs.length > 0) {
      row('S8b', '受限库存不含非授权仓行', !limitedHasOther, `limitedHasOther=${limitedHasOther}`)
    }
  }

  // 5) 恢复：受限角色改回 ALL，避免污染联调库长期缺范围（用户可留）
  {
    const restore = await api(adminToken, 'PUT', `/api/system/roles/${roleId}/data-scope`, {
      hasAllScope: true,
      deptScoped: false,
      postScoped: false,
      selfScoped: false,
      warehouseIds: [],
    })
    row('S9', 'smoke 后角色范围恢复为 ALL', ok(restore), `roleId=${roleId}`)
  }

  const failed = results.filter((r) => !r.pass)
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: BASE,
    roleCode: ROLE_CODE,
    username: USER_NAME,
    roleId,
    limitedUserId,
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
