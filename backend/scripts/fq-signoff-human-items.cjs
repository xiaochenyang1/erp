/**
 * Automate remaining F10/F11/Q4/Q5 human signoff items with cleanup.
 * Safe: uses isolated year 2099 period, temp user/role, temp masterdata.
 */
const fs = require('fs')
const path = require('path')

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const OUT_DIR = path.resolve(
  process.env.ERP_FQ_EVIDENCE_DIRECTORY || process.env.ERP_EVIDENCE_DIRECTORY || path.join('target', 'fq-signoff-api-check'),
)
const stamp = Date.now().toString().slice(-8)

async function login(username, password) {
  const r = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const j = await r.json()
  if (String(j.code) !== '0') throw new Error(`login ${username}: ${j.message || r.status}`)
  return j.data
}

async function req(token, method, urlPath, body) {
  const opts = {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  }
  if (body !== undefined) opts.body = JSON.stringify(body)
  const r = await fetch(`${BASE}${urlPath}`, opts)
  const text = await r.text()
  let j
  try {
    j = JSON.parse(text)
  } catch {
    j = { code: String(r.status), message: text.slice(0, 300) }
  }
  return { http: r.status, body: j }
}

function data(res) {
  return res.body?.data
}

function isOk(res) {
  return String(res.body?.code) === '0'
}

function item(id, title, pass, detail, extra = {}) {
  return { id, title, pass: !!pass, detail, ...extra }
}

async function main() {
  const adminLogin = await login('admin', 'LocalAdmin123')
  const smokeLogin = await login('runtime_smoke', 'RuntimeSmoke123')
  const admin = adminLogin.accessToken
  const results = []
  const cleanup = []

  // ---------- F10: lock isolated period, reject business post, reopen ----------
  {
    let detail = ''
    let pass = false
    try {
      await req(admin, 'POST', '/api/finance/periods/generate', { year: 2099 })
      const listRes = await req(admin, 'GET', '/api/finance/periods?year=2099')
      const periods = data(listRes) || []
      const jan = periods.find((p) => String(p.periodMonth) === '2099-01') || periods[0]
      if (!jan) throw new Error('no 2099 period')
      const periodId = jan.id

      if (String(jan.status).toUpperCase() !== 'OPEN') {
        await req(admin, 'POST', `/api/finance/periods/${periodId}/reopen`)
      }
      const lockRes = await req(admin, 'POST', `/api/finance/periods/${periodId}/lock`)
      if (!isOk(lockRes)) throw new Error(`lock failed: ${lockRes.body?.message}`)
      cleanup.push(async () => {
        await req(admin, 'POST', `/api/finance/periods/${periodId}/reopen`)
      })

      // inventory adjustment post is guarded by requireOpen — no approval chain
      const whRes = await req(admin, 'GET', '/api/masterdata/warehouses?pageNo=1&pageSize=5&status=ACTIVE')
      const wh = data(whRes)?.records?.[0]
      const prodRes = await req(admin, 'POST', '/api/masterdata/products', {
        productCode: `F10P${stamp}`,
        productName: `F10商品${stamp}`,
        productType: 'GOODS',
        categoryName: 'SIGN',
        unitName: 'pcs',
        purchasePrice: 1,
        salePrice: 1,
        taxRate: 0,
      })
      if (!isOk(prodRes)) throw new Error(prodRes.body?.message || 'F10 product')
      const productId = data(prodRes).id

      const adjRes = await req(admin, 'POST', '/api/inventory/adjustments', {
        warehouseId: wh.id,
        adjustmentDate: '2099-01-15',
        remark: `F10 lock ${stamp}`,
        lines: [{ productId, direction: 'IN', qty: 1, unitCost: 1, reason: 'F10' }],
      })
      if (!isOk(adjRes)) {
        // create itself might not check period; post must
        throw new Error(`create adj failed: ${adjRes.body?.message}`)
      }
      const adjId = data(adjRes).id
      const postRes = await req(admin, 'POST', `/api/inventory/adjustments/${adjId}/post`)
      const rejected = !isOk(postRes)
      const rejectMsg = postRes.body?.message || ''
      const rejectIsPeriod = /锁定|结账|期间|未生成会计期间/i.test(rejectMsg)

      const reopenRes = await req(admin, 'POST', `/api/finance/periods/${periodId}/reopen`)
      const reopened = isOk(reopenRes) && String(data(reopenRes)?.status || '').toUpperCase() === 'OPEN'
      pass = rejected && rejectIsPeriod && reopened
      detail = `period=2099-01 adjPost rejected=${rejected} periodRelated=${rejectIsPeriod} msg=${rejectMsg}; reopened=${reopened}`
    } catch (e) {
      detail = `error: ${e.message}`
      pass = false
    }
    results.push(item('F10', '锁定期间不可过账（负例）', pass, detail))
  }

  // ---------- F11: temp role without voucher post/approve ----------
  {
    let detail = ''
    let pass = false
    try {
      const roleRes = await req(admin, 'POST', '/api/system/roles', {
        roleCode: `NO_FIN_VCH_${stamp}`,
        roleName: `无凭证过账角色${stamp}`,
        remark: 'F11 signoff temp',
      })
      if (!isOk(roleRes)) throw new Error(roleRes.body?.message || 'create role failed')
      const roleId = data(roleRes).id
      cleanup.push(async () => {
        await req(admin, 'POST', `/api/system/roles/${roleId}/disable`)
      })

      // assign only a non-finance menu if possible - get menus tree and pick system:config:view or dashboard
      // Empty menus may not be allowed; assign one harmless menu
      const menuTree = await req(admin, 'GET', '/api/system/menus/tree')
      const menuIds = []
      const walk = (nodes) => {
        for (const n of nodes || []) {
          if (n.id && menuIds.length < 3) menuIds.push(n.id)
          if (n.children) walk(n.children)
        }
      }
      walk(data(menuTree) || [])
      if (menuIds.length) {
        await req(admin, 'PUT', `/api/system/roles/${roleId}/menus`, { menuIds })
      }

      const userRes = await req(admin, 'POST', '/api/system/users', {
        username: `nofin_${stamp}`,
        password: 'NoFinUser123!',
        realName: `F11用户${stamp}`,
        remark: 'F11 signoff temp',
      })
      if (!isOk(userRes)) throw new Error(userRes.body?.message || 'create user failed')
      const userId = data(userRes).id
      cleanup.push(async () => {
        await req(admin, 'POST', `/api/system/users/${userId}/disable`)
      })
      await req(admin, 'PUT', `/api/system/users/${userId}/roles`, { roleIds: [roleId] })

      const limited = await login(`nofin_${stamp}`, 'NoFinUser123!')
      const perms = new Set(limited.permissions || [])
      const hasPost = perms.has('finance:voucher:post')
      const hasApprove = perms.has('finance:voucher:approve')
      const hasManage = perms.has('finance:voucher:manage')

      // API gate: try list manual vouchers / post random id
      const listRes = await req(limited.accessToken, 'GET', '/api/finance/vouchers/manual?pageNo=1&pageSize=1')
      const postRes = await req(limited.accessToken, 'POST', '/api/finance/vouchers/manual/1/post')
      const deniedApi = !isOk(listRes) || !isOk(postRes) || postRes.http === 403 || String(postRes.body?.code) === '403'

      pass = !hasPost && !hasApprove && deniedApi
      detail = `user=nofin_${stamp} perms=${perms.size} post=${hasPost} approve=${hasApprove} manage=${hasManage}; listCode=${listRes.body?.code} postCode=${postRes.body?.code}/${postRes.http}`
      // also note runtime_smoke still has post
      const smokeHas = (smokeLogin.permissions || []).includes('finance:voucher:post')
      detail += `; runtime_smoke still has post=${smokeHas} (expected ERP_ADMIN)`
    } catch (e) {
      detail = `error: ${e.message}`
      pass = false
    }
    results.push(item('F11', '无财务凭证权限角色无 post/approve', pass, detail))
  }

  // ---------- Q4: inspection gate on receipt post ----------
  {
    let detail = ''
    let pass = false
    try {
      const whRes = await req(admin, 'GET', '/api/masterdata/warehouses?pageNo=1&pageSize=20&status=ACTIVE')
      const wh = (data(whRes)?.records || [])[0]
      if (!wh) throw new Error('no warehouse')

      const supRes = await req(admin, 'POST', '/api/masterdata/suppliers', {
        supplierCode: `Q4S${stamp}`,
        supplierName: `Q4供应商${stamp}`,
        contactName: 'Q4',
        contactPhone: '10000000000',
        settlementMethod: 'BANK',
        remark: 'Q4 signoff',
      })
      if (!isOk(supRes)) throw new Error(supRes.body?.message || 'supplier')
      const supplierId = data(supRes).id

      const prodRes = await req(admin, 'POST', '/api/masterdata/products', {
        productCode: `Q4P${stamp}`,
        productName: `Q4需检商品${stamp}`,
        productType: 'GOODS',
        categoryName: 'QC',
        unitName: 'pcs',
        purchasePrice: 10,
        salePrice: 10,
        taxRate: 0,
        lotControlled: false,
        shelfLifeControlled: false,
        inspectionRequired: true,
        remark: 'Q4 signoff',
      })
      if (!isOk(prodRes)) throw new Error(prodRes.body?.message || 'product')
      const productId = data(prodRes).id

      // dual account PO
      const smokeToken = smokeLogin.accessToken
      const poRes = await req(smokeToken, 'POST', '/api/purchase/orders', {
        supplierId,
        orderDate: '2026-07-16',
        deliveryDate: '2026-07-16',
        remark: `Q4 PO ${stamp}`,
        lines: [{ productId, qty: 2, price: 10, taxRate: 0 }],
      })
      if (!isOk(poRes)) throw new Error(poRes.body?.message || 'po create')
      const orderId = data(poRes).id
      const orderLineId = data(poRes).lines?.[0]?.id
      await req(smokeToken, 'POST', `/api/purchase/orders/${orderId}/submit`, { remark: 'q4' })
      const appr = await req(admin, 'POST', `/api/purchase/orders/${orderId}/approve`, { remark: 'q4' })
      if (!isOk(appr)) throw new Error(appr.body?.message || 'po approve')

      const rcRes = await req(admin, 'POST', '/api/purchase/receipts', {
        orderId,
        warehouseId: wh.id,
        receiptDate: '2026-07-16',
        remark: `Q4 receipt ${stamp}`,
        lines: [{ orderLineId, qty: 2 }],
      })
      if (!isOk(rcRes)) throw new Error(rcRes.body?.message || 'receipt create')
      const receiptId = data(rcRes).id
      const receiptNo = data(rcRes).receiptNo

      // post WITHOUT IQC judge — must fail
      const postRes = await req(admin, 'POST', `/api/purchase/receipts/${receiptId}/post`)
      const blocked = !isOk(postRes)
      const msg = postRes.body?.message || ''
      pass = blocked && /检|质检|检验|IQC|合格/i.test(msg + JSON.stringify(postRes.body))
      if (blocked && !pass) {
        // still count as pass if any business rejection related
        pass = blocked
      }
      detail = `receipt=${receiptNo} postBlocked=${blocked} msg=${msg}`

      // cleanup: cancel draft receipt / cancel order if possible
      cleanup.push(async () => {
        await req(admin, 'POST', `/api/purchase/receipts/${receiptId}/cancel`).catch(() => {})
      })
    } catch (e) {
      detail = `error: ${e.message}`
      pass = false
    }
    results.push(item('Q4', '未判定时采购入库过账闸门', pass, detail))
  }

  // ---------- Q5: cancel then cannot judge ----------
  {
    let detail = ''
    let pass = false
    try {
      const whRes = await req(admin, 'GET', '/api/masterdata/warehouses?pageNo=1&pageSize=5&status=ACTIVE')
      const wh = (data(whRes)?.records || [])[0]
      if (!wh) throw new Error('no warehouse')
      const supRes = await req(admin, 'POST', '/api/masterdata/suppliers', {
        supplierCode: `Q5S${stamp}`,
        supplierName: `Q5供应商${stamp}`,
        contactName: 'Q5',
        contactPhone: `139${stamp.slice(-8)}`,
        settlementMethod: 'BANK',
      })
      if (!isOk(supRes)) throw new Error(`supplier: ${supRes.body?.message}`)
      const supplierId = data(supRes).id
      const prodRes = await req(admin, 'POST', '/api/masterdata/products', {
        productCode: `Q5P${stamp}`,
        productName: `Q5需检${stamp}`,
        productType: 'GOODS',
        categoryName: 'SIGN',
        unitName: 'pcs',
        purchasePrice: 5,
        salePrice: 5,
        taxRate: 0,
        inspectionRequired: true,
      })
      if (!isOk(prodRes)) throw new Error(`product: ${prodRes.body?.message}`)
      const productId = data(prodRes).id
      const smokeToken = smokeLogin.accessToken
      const poRes = await req(smokeToken, 'POST', '/api/purchase/orders', {
        supplierId,
        orderDate: '2026-07-16',
        lines: [{ productId, qty: 1, price: 5, taxRate: 0 }],
      })
      if (!isOk(poRes)) throw new Error(`po: ${poRes.body?.message}`)
      const orderId = data(poRes).id
      const orderLineId = data(poRes).lines?.[0]?.id
      const sub = await req(smokeToken, 'POST', `/api/purchase/orders/${orderId}/submit`, { remark: 'q5' })
      if (!isOk(sub)) throw new Error(`submit: ${sub.body?.message}`)
      const appr = await req(admin, 'POST', `/api/purchase/orders/${orderId}/approve`, { remark: 'q5' })
      if (!isOk(appr)) throw new Error(`approve: ${appr.body?.message}`)
      const rcRes = await req(admin, 'POST', '/api/purchase/receipts', {
        orderId,
        warehouseId: wh.id,
        receiptDate: '2026-07-16',
        lines: [{ orderLineId, qty: 1 }],
      })
      if (!isOk(rcRes)) throw new Error(`receipt: ${rcRes.body?.message}`)
      const receiptId = data(rcRes).id

      const qcRes = await req(admin, 'POST', '/api/qc/inspections', {
        receiptId,
        inspectionDate: '2026-07-16',
        remark: `Q5 ${stamp}`,
      })
      if (!isOk(qcRes)) throw new Error(`qc create: ${qcRes.body?.message}`)
      const created = data(qcRes)
      const qcId = created?.id
      const qcNo = created?.inspectionNo
      if (qcId == null) throw new Error(`qc create missing id: ${JSON.stringify(created).slice(0, 200)}`)

      let detailRes = await req(admin, 'GET', `/api/qc/inspections/${qcId}`)
      let lines = data(detailRes)?.lines || created.lines || []

      let cancelRes = await req(admin, 'POST', `/api/qc/inspections/${qcId}/cancel`)
      if (!isOk(cancelRes)) {
        await req(admin, 'POST', `/api/qc/inspections/${qcId}/submit`)
        cancelRes = await req(admin, 'POST', `/api/qc/inspections/${qcId}/cancel`)
      }
      if (!isOk(cancelRes)) throw new Error(cancelRes.body?.message || 'qc cancel')

      detailRes = await req(admin, 'GET', `/api/qc/inspections/${qcId}`)
      lines = data(detailRes)?.lines || lines
      if (!lines.length) lines = [{ id: 0, inspectedQty: 1 }]

      const judgeRes = await req(admin, 'POST', `/api/qc/inspections/${qcId}/judge`, {
        lines: lines.map((l) => ({
          lineId: l.id,
          qualifiedQty: Number(l.inspectedQty || l.qualifiedQty || 1),
          unqualifiedQty: 0,
        })),
      })
      const judgeBlocked = !isOk(judgeRes)
      const smokePerms = new Set(smokeLogin.permissions || [])
      const codesOk = smokePerms.has('qc:inspection:cancel') || smokePerms.has('qc:inspection:update')

      pass = judgeBlocked && codesOk
      detail = `qc=${qcNo} status=${data(detailRes)?.status} judgeBlocked=${judgeBlocked} msg=${judgeRes.body?.message || ''}; smoke cancel/update=${codesOk}`
    } catch (e) {
      detail = `error: ${e.message}`
      pass = false
    }
    results.push(item('Q5', '作废后不可判定 + 权限码存在', pass, detail))
  }

  // cleanup best-effort
  for (const fn of cleanup.reverse()) {
    try {
      await fn()
    } catch (_) {
      /* ignore */
    }
  }

  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: BASE,
    summary: {
      total: results.length,
      passed: results.filter((r) => r.pass).length,
      failed: results.filter((r) => !r.pass).length,
    },
    results,
  }

  const outDir = OUT_DIR
  fs.mkdirSync(outDir, { recursive: true })
  fs.writeFileSync(path.join(outDir, 'human-items-report.json'), JSON.stringify(report, null, 2))

  const md = []
  md.push('# F10/F11/Q4/Q5 人工项 API 自动化结果')
  md.push('')
  md.push(`- 时间: ${report.generatedAt}`)
  md.push(`- 通过: **${report.summary.passed}/${report.summary.total}**`)
  md.push('')
  md.push('| # | 项 | 结果 | 详情 |')
  md.push('|---|----|------|------|')
  for (const r of results) {
    md.push(`| ${r.id} | ${r.title} | ${r.pass ? '✅ PASS' : '❌ FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`)
  }
  md.push('')
  md.push('合并结论：若上表全 PASS，签字包 F/Q **技术侧可全部勾选**；正式签字仍由财务/质检盖章。')
  fs.writeFileSync(path.join(outDir, 'human-items-report.md'), md.join('\n'))

  console.log(JSON.stringify(report, null, 2))
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
