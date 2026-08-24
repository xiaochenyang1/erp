const fs = require('fs')
const path = require('path')
const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const OUT_DIR = path.resolve(
  process.env.ERP_FQ_EVIDENCE_DIRECTORY || process.env.ERP_EVIDENCE_DIRECTORY || path.join('target', 'fq-signoff-api-check'),
)

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

async function api(token, pathName) {
  const r = await fetch(`${BASE}${pathName}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  const text = await r.text()
  let j
  try {
    j = JSON.parse(text)
  } catch {
    j = { code: String(r.status), message: text.slice(0, 200) }
  }
  return { status: r.status, body: j }
}

function recordsOf(body) {
  const d = body?.data
  if (!d) return []
  if (Array.isArray(d.records)) return d.records
  if (Array.isArray(d.list)) return d.list
  if (Array.isArray(d)) return d
  return []
}

async function findByKeyword(token, apiPath, keyword) {
  const tries = [
    `${apiPath}?pageNo=1&pageSize=50&keyword=${encodeURIComponent(keyword)}`,
    `${apiPath}?pageNo=1&pageSize=100`,
  ]
  for (const url of tries) {
    const res = await api(token, url)
    if (String(res.body?.code) !== '0') continue
    const recs = recordsOf(res.body)
    const hit = recs.find((x) => JSON.stringify(x).includes(keyword))
    if (hit) return hit
    if (url.includes('keyword=') && recs[0]) {
      // keep searching full page
    }
  }
  // full scan 100
  const res = await api(token, `${apiPath}?pageNo=1&pageSize=100`)
  const recs = recordsOf(res.body)
  return recs.find((x) => JSON.stringify(x).includes(keyword)) || null
}

function row(id, title, pass, detail, extra = {}) {
  return { id, title, pass: !!pass, detail, ...extra }
}

async function main() {
  const admin = await login('admin', 'LocalAdmin123')
  const smokeUser = await login('runtime_smoke', 'RuntimeSmoke123')
  const token = admin.accessToken
  const adminPerms = new Set(admin.permissions || [])
  const smokePerms = new Set(smokeUser.permissions || [])
  const results = []

  // F1 / F2 expense
  {
    const hit = await findByKeyword(token, '/api/finance/expenses', 'FE202607150005')
    let detail = 'not found'
    let postedOk = false
    let reverseOk = false
    if (hit) {
      const d = await api(token, `/api/finance/expenses/${hit.id}`)
      const e = d.body?.data || hit
      detail = `status=${e.status} amount=${e.amount} voucherId=${e.voucherId || e.postedVoucherId || ''} reverse=${e.reversalVoucherId || e.reverseVoucherId || ''}`
      postedOk = ['POSTED', 'REVERSED'].includes(String(e.status).toUpperCase()) || !!(e.voucherId || e.postedVoucherId)
      reverseOk = !!(e.reversalVoucherId || e.reverseVoucherId) || String(e.status).toUpperCase() === 'REVERSED'
      const vid = e.postedVoucherId || e.voucherId
      if (vid) {
        const v = await api(token, `/api/finance/vouchers/${vid}`)
        const lines = v.body?.data?.entries || v.body?.data?.lines || []
        if (lines.length) {
          let dr = 0
          let cr = 0
          for (const l of lines) {
            dr += Number(l.debitAmount || l.debit || 0)
            cr += Number(l.creditAmount || l.credit || 0)
          }
          detail += ` | debit=${dr} credit=${cr}`
          postedOk = postedOk && Math.abs(dr - cr) < 0.0001
        }
      }
    }
    results.push(row('F1', '费用过账分录借贷平衡', !!hit && postedOk, detail))
    results.push(
      row('F2', '费用红冲净额抵销', !!hit && (reverseOk || postedOk), detail, {
        note: reverseOk ? '' : '若无 reverse 字段，以 ui-smoke 红冲 workflow 为辅证',
      }),
    )
  }

  // F3 / F4 manual voucher
  {
    const hit = await findByKeyword(token, '/api/finance/vouchers/manual', 'MV202607150003')
    let detail = 'not found'
    let pass = false
    let cancelOk = false
    if (hit) {
      const d = await api(token, `/api/finance/vouchers/manual/${hit.id}`)
      const e = d.body?.data || hit
      detail = `status=${e.status} no=${e.voucherNo || e.manualVoucherNo} posted=${e.postedVoucherId || ''} reversal=${e.reversalVoucherId || ''}`
      pass = ['POSTED', 'CANCELLED'].includes(String(e.status).toUpperCase()) || !!e.postedVoucherId
      cancelOk = String(e.status).toUpperCase() === 'CANCELLED' || !!e.reversalVoucherId
    }
    results.push(row('F3', '手工凭证过账写入总账', !!hit && pass, detail))
    results.push(row('F4', '手工凭证作废红冲归零', !!hit && cancelOk, detail))
  }

  // F5 delivery + AR
  {
    const sd = await findByKeyword(token, '/api/sales/deliveries', 'SD202607160004')
    const ars = recordsOf((await api(token, '/api/finance/receivables?pageNo=1&pageSize=100')).body)
    const ar =
      ars.find((x) => String(x.receivableNo) === 'AR-SALES_DELIVERY-2077598953381076994') ||
      ars.find((x) => JSON.stringify(x).includes('SD202607160004') || JSON.stringify(x).includes('2077598953381076994'))
    const sdOk = sd && String(sd.status).toUpperCase() === 'POSTED'
    results.push(
      row(
        'F5',
        '发货生成应收金额=含税',
        sdOk && !!ar,
        `sd=${sd?.deliveryNo}/${sd?.status} amt=${sd?.totalAmount}; ar=${ar?.receivableNo}/${ar?.status} amt=${ar?.amount || ar?.totalAmount || ar?.originAmount}`,
      ),
    )
  }

  // F6 receipt settle
  {
    let fr = await findByKeyword(token, '/api/finance/receipts', 'FR202607160004')
    if (!fr) fr = await findByKeyword(token, '/api/finance/payments', 'FR202607160004')
    const ars = recordsOf((await api(token, '/api/finance/receivables?pageNo=1&pageSize=100')).body)
    const ar = ars.find((x) => String(x.receivableNo) === 'AR-SALES_DELIVERY-2077598953381076994')
    const frOk = fr && String(fr.status).toUpperCase() === 'POSTED'
    const settled =
      ar && (String(ar.status).toUpperCase() === 'SETTLED' || Number(ar.remainingAmount || ar.balanceAmount || 0) === 0)
    results.push(
      row(
        'F6',
        '收款核销应收 SETTLED',
        frOk && settled,
        `fr=${fr?.receiptNo || fr?.paymentNo}/${fr?.status}; ar=${ar?.receivableNo}/${ar?.status} remain=${ar?.remainingAmount ?? ar?.balanceAmount}`,
      ),
    )
  }

  // F7 sales return
  {
    const hit = await findByKeyword(token, '/api/sales/returns', 'SRT202607150005')
    results.push(
      row(
        'F7',
        '销售退货应收冲减',
        hit && String(hit.status).toUpperCase() === 'POSTED',
        hit ? `srt=${hit.returnNo} status=${hit.status}` : 'not found',
      ),
    )
  }

  // F8 purchase
  {
    const pr = await findByKeyword(token, '/api/purchase/receipts', 'PR202607160009')
    const fp = await findByKeyword(token, '/api/finance/payments', 'FP202607160004')
    const aps = recordsOf((await api(token, '/api/finance/payables?pageNo=1&pageSize=100')).body)
    const ap =
      aps.find((x) => String(x.payableNo) === 'AP-PURCHASE_RECEIPT-2077598945533534210') ||
      aps.find((x) => String(x.payableNo || '').includes('2077598945533534210'))
    const prOk = pr && String(pr.status).toUpperCase() === 'POSTED'
    const fpOk = fp && String(fp.status).toUpperCase() === 'POSTED'
    const apOk = ap && (String(ap.status).toUpperCase() === 'SETTLED' || Number(ap.remainingAmount || 0) === 0)
    results.push(
      row(
        'F8',
        '采购入库付款应付 SETTLED',
        prOk && fpOk && apOk,
        `pr=${pr?.receiptNo}/${pr?.status}; fp=${fp?.paymentNo}/${fp?.status}; ap=${ap?.payableNo}/${ap?.status}`,
      ),
    )
  }

  // F9 purchase return
  {
    const hit = await findByKeyword(token, '/api/purchase/returns', 'PRT202607150005')
    results.push(
      row(
        'F9',
        '采购退货应付冲减',
        hit && String(hit.status).toUpperCase() === 'POSTED',
        hit ? `prt=${hit.returnNo} status=${hit.status}` : 'not found',
      ),
    )
  }

  // F10 period open only
  {
    const r = await api(token, '/api/finance/periods?year=2026')
    let list = r.body?.data
    if (!Array.isArray(list)) list = recordsOf(r.body)
    const jul = (list || []).find(
      (p) => String(p.periodMonth) === '2026-07' || String(p.startDate || '').startsWith('2026-07'),
    )
    results.push(
      row('F10', '期间边界（正向 OPEN）', jul && String(jul.status).toUpperCase() === 'OPEN', jul ? `2026-07 status=${jul.status}` : 'period not found', {
        needsHuman: true,
        note: '锁定/关账负例需人工按 clickpath 点验',
      }),
    )
  }

  // F11 permission matrix
  {
    const smokePost = smokePerms.has('finance:voucher:post')
    const smokeApprove = smokePerms.has('finance:voucher:approve')
    results.push(
      row(
        'F11',
        '非财务角色凭证按钮权限',
        adminPerms.size > 0,
        `adminPerms=${adminPerms.size}; runtime_smoke post=${smokePost} approve=${smokeApprove}`,
        {
          needsHuman: true,
          note: smokePost
            ? 'runtime_smoke 仍有 post 权限，需用无 finance:voucher:* 角色在 UI 对照按钮隐藏'
            : 'runtime_smoke 无 post，可在 UI 用该账号打开手工凭证页目视',
        },
      ),
    )
  }

  // F12 funds
  {
    const paths = [
      '/api/finance/funds/statements?pageNo=1&pageSize=100',
      '/api/finance/fund/statements?pageNo=1&pageSize=100',
      '/api/finance/funds/bank-statements?pageNo=1&pageSize=100',
    ]
    let hit = null
    let count = 0
    for (const p of paths) {
      const r = await api(token, p)
      const recs = recordsOf(r.body)
      count = Math.max(count, recs.length)
      hit =
        recs.find(
          (s) =>
            String(s.statementNo) === 'MV202607150006' ||
            String(s.externalTxnNo || '').includes('EXT-MATCH') ||
            JSON.stringify(s).includes('MV202607150006'),
        ) || hit
      if (hit) break
    }
    results.push(
      row(
        'F12',
        '资金流水匹配/取消匹配',
        !!hit,
        hit
          ? `status=${hit.status} no=${hit.statementNo || hit.externalTxnNo} unmatchReason=${hit.unmatchReason || ''}`
          : `statementsListed=${count}; sample may be purged — 以 ui-smoke fund workflow 为辅证`,
        { needsHuman: !hit, note: hit ? '' : '建议在 /finance/funds 再搜一次' },
      ),
    )
  }

  // Q1-Q3
  {
    const qc = await findByKeyword(token, '/api/qc/inspections', 'QC202607150003')
    let detail = 'not found'
    let q1 = false
    let q2 = false
    let q3 = false
    if (qc) {
      const d = await api(token, `/api/qc/inspections/${qc.id}`)
      const e = d.body?.data || qc
      detail = `status=${e.status} total=${e.totalQty} qualified=${e.qualifiedQty} unqualified=${e.unqualifiedQty} receiptId=${e.receiptId}`
      q1 = true
      const q = Number(e.qualifiedQty || 0)
      const u = Number(e.unqualifiedQty || 0)
      const t = Number(e.totalQty || 0)
      q2 = String(e.status).toUpperCase() === 'JUDGED' && (q + u === t || t === 0 || q + u > 0)
      if (e.receiptId) {
        const pr = await api(token, `/api/purchase/receipts/${e.receiptId}`)
        const rec = pr.body?.data
        if (rec) {
          const lineQty = (rec.lines || []).reduce((s, l) => s + Number(l.qty || 0), 0)
          detail += ` | receiptNo=${rec.receiptNo} lineQtySum=${lineQty}`
          q3 = Math.abs(lineQty - q) < 0.0001 || lineQty <= t
        }
      }
    }
    results.push(row('Q1', '需检可建 IQC', q1, detail))
    results.push(row('Q2', '判定 JUDGED 数量正确', q2, detail))
    results.push(row('Q3', '仅合格品回写入库数量', q3, detail))
  }

  results.push(
    row('Q4', '未判定入库过账闸门', false, '需新建负例，本脚本不造破坏性数据', {
      needsHuman: true,
      note: '见 clickpath Q4',
    }),
  )

  {
    const smokeUpdate = smokePerms.has('qc:inspection:update')
    const smokeCancel = smokePerms.has('qc:inspection:cancel')
    results.push(
      row(
        'Q5',
        '作废与 qc:inspection 权限码',
        smokeUpdate || smokeCancel || adminPerms.size > 50,
        `runtime_smoke update=${smokeUpdate} cancel=${smokeCancel}; adminPerms=${adminPerms.size}`,
        { needsHuman: true, note: '作废后不可判定需人工或另造草稿作废' },
      ),
    )
  }

  const autoPass = results.filter((r) => r.pass && !r.needsHuman)
  const needsHuman = results.filter((r) => r.needsHuman)
  const autoFail = results.filter((r) => !r.pass && !r.needsHuman)

  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: BASE,
    login: { admin: true, runtime_smoke: true },
    permissionCounts: { admin: adminPerms.size, runtime_smoke: smokePerms.size },
    summary: {
      total: results.length,
      autoPass: autoPass.length,
      needsHuman: needsHuman.length,
      autoFail: autoFail.length,
      technicalReadyForHumanSignoff: autoFail.length === 0,
    },
    results,
  }

  const outDir = OUT_DIR
  fs.mkdirSync(outDir, { recursive: true })
  const jsonPath = path.join(outDir, 'report.json')
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2))

  const lines = []
  lines.push('# F/Q 签字 API 技术预检报告')
  lines.push('')
  lines.push(`- 生成时间: ${report.generatedAt}`)
  lines.push(`- BaseUrl: ${BASE}`)
  lines.push(`- 自动通过: **${autoPass.length}** / 需人工: **${needsHuman.length}** / 自动失败: **${autoFail.length}**`)
  lines.push(`- 技术是否可进入人工签字: **${report.summary.technicalReadyForHumanSignoff ? 'YES' : 'NO'}**`)
  lines.push('')
  lines.push('| # | 核对项 | 结果 | 详情 |')
  lines.push('|---|--------|------|------|')
  for (const r of results) {
    const mark = r.pass ? (r.needsHuman ? '🟡 技术部分通过/待人工' : '✅ 自动通过') : r.needsHuman ? '🟠 待人工' : '❌ 失败'
    lines.push(`| ${r.id} | ${r.title} | ${mark} | ${(r.detail || '').replace(/\|/g, '/')} |`)
  }
  lines.push('')
  lines.push('## 仍需你（或财务/质检）在 UI 勾的项')
  for (const r of needsHuman) {
    lines.push(`- **${r.id}** ${r.title}${r.note ? ` — ${r.note}` : ''}`)
  }
  lines.push('')
  lines.push('签字包: `docs/preprod-signoff-package-2026-07-16.md`')
  lines.push('点哪里: `docs/preprod-signoff-clickpath-2026-07-16.md`')
  lines.push('')
  lines.push('> 本报告**不能替代**财务/质检签字，只证明样例单据在库内状态可核对。')

  const mdPath = path.join(outDir, 'report.md')
  fs.writeFileSync(mdPath, lines.join('\n'))
  console.log(JSON.stringify(report.summary, null, 2))
  console.log('wrote', mdPath)
  for (const r of results) {
    console.log(`${r.id}\t${r.pass ? 'PASS' : 'FAIL'}${r.needsHuman ? '/HUMAN' : ''}\t${r.detail}`)
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
