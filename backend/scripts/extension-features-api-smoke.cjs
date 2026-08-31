/**
 * Track B 扩展能力 API smoke（询价/发票/OQC/信用）。
 * 需本机后端已启动 local + erp_codex_runtime，且 Flyway 已到 V109。
 *
 * 用法：
 *   node scripts/extension-features-api-smoke.cjs
 *   BASE_URL=http://127.0.0.1:8080 node scripts/extension-features-api-smoke.cjs
 */
const fs = require('fs')
const path = require('path')

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8080'
const ADMIN_USER = process.env.ADMIN_USER || 'admin'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'LocalAdmin123'
const SUBMITTER_USER = process.env.SUBMITTER_USER || 'runtime_smoke'
const SUBMITTER_PASSWORD = process.env.SUBMITTER_PASSWORD || 'RuntimeSmoke123'
const SUFFIX = String(Date.now()).slice(-8)
const today = new Date().toISOString().slice(0, 10)

const results = []

function row(id, title, pass, detail) {
  results.push({ id, title, pass: !!pass, detail: String(detail || '') })
  console.log(`[${pass ? 'PASS' : 'FAIL'}] ${id} ${title} — ${detail}`)
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
  if (body !== undefined) opts.body = JSON.stringify(body)
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

function failMsg(res) {
  return res.body?.message || `HTTP ${res.status}`
}

async function ensureOpenPeriod(token, bizDate) {
  const year = Number(String(bizDate).slice(0, 4))
  await request(token, 'POST', `/api/finance/periods/generate?year=${year}`)
}

async function request(token, method, urlPath, body) {
  return api(token, method, urlPath.startsWith('/api/') ? urlPath : `/api${urlPath}`, body)
}

async function must(token, method, urlPath, body, label) {
  const res = await request(token, method, urlPath, body)
  if (!ok(res)) {
    throw new Error(`${label}: ${failMsg(res)}`)
  }
  return dataOf(res)
}

async function smokePurchaseInquiry(adminToken, submitterToken) {
  const id = `INQ${SUFFIX}`
  try {
    const supplier = await must(adminToken, 'POST', '/api/masterdata/suppliers', {
      supplierCode: `EXS${SUFFIX}`,
      supplierName: `扩展询价供应商${SUFFIX}`,
      contactName: 'smoke',
      contactPhone: '13800000001',
      settlementMethod: 'BANK_TRANSFER',
      address: 'smoke',
      remark: id,
    }, 'create supplier')
    const product = await must(adminToken, 'POST', '/api/masterdata/products', {
      productCode: `EXP${SUFFIX}`,
      productName: `扩展询价商品${SUFFIX}`,
      productType: 'GOODS',
      categoryName: '扩展联调',
      specification: 'smoke',
      unitName: '件',
      purchasePrice: 12,
      salePrice: 20,
      taxRate: 13,
      lotControlled: false,
      shelfLifeControlled: false,
      inspectionRequired: false,
      remark: id,
    }, 'create product')
    const secondProduct = await must(adminToken, 'POST', '/api/masterdata/products', {
      productCode: `EXQ${SUFFIX}`,
      productName: `扩展询价商品二${SUFFIX}`,
      productType: 'GOODS',
      categoryName: '扩展联调',
      specification: 'smoke-2',
      unitName: '件',
      purchasePrice: 21,
      salePrice: 30,
      taxRate: 6,
      lotControlled: false,
      shelfLifeControlled: false,
      inspectionRequired: false,
      remark: id,
    }, 'create second product')

    const inquiry = await must(adminToken, 'POST', '/api/purchase/inquiries', {
      inquiryDate: today,
      title: `扩展询价 ${SUFFIX}`,
      remark: id,
      lines: [
        { productId: product.id, qty: 5, remark: 'line1' },
        { productId: secondProduct.id, qty: 3, remark: 'line2' },
      ],
    }, 'create inquiry')
    row('B3-1', '创建询价单', inquiry.status === 'DRAFT', inquiry.inquiryNo)

    const inquiryLineByProduct = new Map(
      (inquiry.lines || []).map((line) => [String(line.productId), line]),
    )
    const firstInquiryLine = inquiryLineByProduct.get(String(product.id))
    const secondInquiryLine = inquiryLineByProduct.get(String(secondProduct.id))
    if (!firstInquiryLine?.id || !secondInquiryLine?.id) {
      throw new Error('created inquiry did not return both line ids')
    }

    const submitted = await must(adminToken, 'POST', `/api/purchase/inquiries/${inquiry.id}/submit`, undefined, 'submit inquiry')
    row('B3-2', '提交询价单', submitted.status === 'SUBMITTED', submitted.status)

    const quoted = await must(adminToken, 'POST', `/api/purchase/inquiries/${inquiry.id}/quotes`, {
      supplierId: supplier.id,
      lines: [
        {
          inquiryLineId: firstInquiryLine.id,
          unitPrice: 12.5,
          taxRate: 13,
        },
        {
          inquiryLineId: secondInquiryLine.id,
          unitPrice: 21.75,
          taxRate: 6,
        },
      ],
      remark: 'quote',
    }, 'add quote')
    const quote = (quoted.quotes || []).find((q) => String(q.supplierId) === String(supplier.id))
    const quotePriceByInquiryLine = new Map(
      (quote?.lines || []).map((line) => [String(line.inquiryLineId), Number(line.unitPrice)]),
    )
    row(
      'B3-3',
      '录入逐行报价',
      !!quote
        && quotePriceByInquiryLine.get(String(firstInquiryLine.id)) === 12.5
        && quotePriceByInquiryLine.get(String(secondInquiryLine.id)) === 21.75,
      quote?.id || 'missing quote',
    )

    const closed = await must(adminToken, 'POST', `/api/purchase/inquiries/${inquiry.id}/select-quote`, {
      quoteId: quote.id,
    }, 'select quote')
    row('B3-4', '选定中标关闭', closed.status === 'CLOSED' && String(closed.selectedSupplierId) === String(supplier.id), closed.status)

    const prefill = await must(adminToken, 'GET', `/api/purchase/inquiries/${inquiry.id}/po-prefill`, undefined, 'po-prefill')
    const prefillPriceByProduct = new Map(
      (prefill.lines || []).map((line) => [String(line.productId), Number(line.price)]),
    )
    row(
      'B3-5',
      'PO 逐行价格预填',
      String(prefill.supplierId) === String(supplier.id)
        && prefillPriceByProduct.get(String(product.id)) === 12.5
        && prefillPriceByProduct.get(String(secondProduct.id)) === 21.75,
      prefill.inquiryNo,
    )

    const order = await must(
      adminToken,
      'POST',
      `/api/purchase/inquiries/${inquiry.id}/convert-to-purchase-order`,
      undefined,
      'atomically convert inquiry to po',
    )
    row(
      'B3-6',
      '原子转换 PO 草稿',
      order.status === 'DRAFT'
        && String(order.sourceInquiryId) === String(inquiry.id)
        && String(order.sourceQuoteId) === String(quote.id)
        && (order.lines || []).some((line) => (
          String(line.sourceInquiryLineId) === String(firstInquiryLine.id)
          && Number(line.price) === 12.5
        ))
        && (order.lines || []).some((line) => (
          String(line.sourceInquiryLineId) === String(secondInquiryLine.id)
          && Number(line.price) === 21.75
        )),
      order.orderNo,
    )

    const retryOrder = await must(
      adminToken,
      'POST',
      `/api/purchase/inquiries/${inquiry.id}/convert-to-purchase-order`,
      undefined,
      'retry atomic inquiry conversion',
    )
    const convertedInquiry = await must(
      adminToken,
      'GET',
      `/api/purchase/inquiries/${inquiry.id}`,
      undefined,
      'load converted inquiry',
    )
    row(
      'B3-7',
      '转换幂等与反向追溯',
      String(retryOrder.id) === String(order.id)
        && convertedInquiry.status === 'CONVERTED'
        && String(convertedInquiry.convertedOrderId) === String(order.id)
        && convertedInquiry.convertedOrderNo === order.orderNo,
      retryOrder.orderNo,
    )
    return order
  } catch (e) {
    row('B3', '采购询价闭环', false, e.message)
    return null
  }
}

async function smokeInvoice(adminToken, purchaseOrder) {
  try {
    if (!purchaseOrder?.id) {
      throw new Error('purchase order fixture is unavailable')
    }
    const created = await must(adminToken, 'POST', '/api/finance/invoices', {
      invoiceType: 'INPUT',
      partnerName: `扩展供应商${SUFFIX}`,
      invoiceDate: today,
      amount: 100,
      taxAmount: 13,
      relatedBizType: 'PURCHASE_ORDER',
      relatedBizId: purchaseOrder.id,
      remark: `invoice-smoke-${SUFFIX}`,
    }, 'create invoice')
    row('B6-1', '创建发票登记草稿', created.status === 'DRAFT', created.invoiceNo)

    const posted = await must(adminToken, 'POST', `/api/finance/invoices/${created.id}/post`, undefined, 'post invoice')
    row('B6-2', '确认发票登记', posted.status === 'POSTED', posted.status)

    const cancelled = await must(adminToken, 'POST', `/api/finance/invoices/${created.id}/cancel`, undefined, 'cancel invoice')
    row('B6-3', '作废发票登记', cancelled.status === 'CANCELLED', cancelled.status)
  } catch (e) {
    row('B6', '发票登记闭环', false, e.message)
  }
}

async function smokeSalesCredit(adminToken, submitterToken) {
  try {
    const customer = await must(adminToken, 'POST', '/api/masterdata/customers', {
      customerCode: `EXC${SUFFIX}`,
      customerName: `扩展信用客户${SUFFIX}`,
      customerType: 'ENTERPRISE',
      contactName: 'smoke',
      contactPhone: '13800000002',
      creditLimit: 1,
      settlementMethod: 'BANK_TRANSFER',
      address: 'smoke',
      remark: `credit-${SUFFIX}`,
    }, 'create customer credit=1')

    const product = await must(adminToken, 'POST', '/api/masterdata/products', {
      productCode: `EXCP${SUFFIX}`,
      productName: `扩展信用商品${SUFFIX}`,
      productType: 'GOODS',
      categoryName: '扩展联调',
      specification: 'smoke',
      unitName: '件',
      purchasePrice: 10,
      salePrice: 100,
      taxRate: 13,
      lotControlled: false,
      shelfLifeControlled: false,
      inspectionRequired: false,
      remark: `credit-p-${SUFFIX}`,
    }, 'create product for credit')

    const warehouse = await must(adminToken, 'POST', '/api/masterdata/warehouses', {
      warehouseCode: `EXCW${SUFFIX}`,
      warehouseName: `扩展信用仓${SUFFIX}`,
      deptId: 3501,
      managerUserId: 4001,
      address: 'smoke',
      remark: `credit-w-${SUFFIX}`,
    }, 'create warehouse')

    const order = await must(submitterToken, 'POST', '/api/sales/orders', {
      customerId: customer.id,
      warehouseId: warehouse.id,
      orderDate: today,
      deliveryDate: today,
      remark: `credit-order-${SUFFIX}`,
      lines: [{
        productId: product.id,
        qty: 10,
        price: 100,
        taxRate: 0.13,
        remark: 'over limit',
      }],
    }, 'create large sales order')

    const submit = await request(
      submitterToken,
      'POST',
      `/api/sales/orders/${order.id}/submit`,
      { remark: 'submit credit order' },
    )
    if (!ok(submit)) {
      const blocked = /信用|额度|超限|credit/i.test(String(failMsg(submit)))
      row('B2-1', '超信用额度提交拦截', blocked, failMsg(submit))
      return
    }

    const approve = await request(
      adminToken,
      'POST',
      `/api/sales/orders/${order.id}/approve`,
      { remark: 'should fail credit' },
    )
    const blocked = !ok(approve) && /信用|额度|超限|credit/i.test(String(failMsg(approve)))
    row('B2-1', '超信用额度审批拦截', blocked, failMsg(approve))
  } catch (e) {
    row('B2', '销售信用拦截', false, e.message)
  }
}

async function smokeOqc(adminToken, submitterToken) {
  try {
    await ensureOpenPeriod(adminToken, today)
    const customer = await must(adminToken, 'POST', '/api/masterdata/customers', {
      customerCode: `EXO${SUFFIX}`,
      customerName: `扩展OQC客户${SUFFIX}`,
      customerType: 'ENTERPRISE',
      contactName: 'smoke',
      contactPhone: '13800000003',
      creditLimit: 0,
      settlementMethod: 'BANK_TRANSFER',
      address: 'smoke',
      remark: `oqc-c-${SUFFIX}`,
    }, 'create oqc customer')
    const warehouse = await must(adminToken, 'POST', '/api/masterdata/warehouses', {
      warehouseCode: `EXOW${SUFFIX}`,
      warehouseName: `扩展OQC仓${SUFFIX}`,
      deptId: 3501,
      managerUserId: 4001,
      address: 'smoke',
      remark: `oqc-w-${SUFFIX}`,
    }, 'create oqc warehouse')
    const product = await must(adminToken, 'POST', '/api/masterdata/products', {
      productCode: `EXOP${SUFFIX}`,
      productName: `扩展OQC商品${SUFFIX}`,
      productType: 'GOODS',
      categoryName: '扩展联调',
      specification: 'smoke',
      unitName: '件',
      purchasePrice: 10,
      salePrice: 30,
      taxRate: 13,
      lotControlled: false,
      shelfLifeControlled: false,
      inspectionRequired: true,
      remark: `oqc-p-${SUFFIX}`,
    }, 'create oqc product')

    // seed stock via inventory adjustment
    const adjust = await must(adminToken, 'POST', '/api/inventory/adjustments', {
      warehouseId: warehouse.id,
      adjustmentDate: today,
      remark: `oqc-adj-${SUFFIX}`,
      lines: [{ productId: product.id, direction: 'IN', qty: 20, unitCost: 10, reason: 'seed' }],
    }, 'create adjustment')
    await must(adminToken, 'POST', `/api/inventory/adjustments/${adjust.id}/post`, undefined, 'post adjustment')

    const order = await must(submitterToken, 'POST', '/api/sales/orders', {
      customerId: customer.id,
      warehouseId: warehouse.id,
      orderDate: today,
      deliveryDate: today,
      remark: `oqc-order-${SUFFIX}`,
      lines: [{ productId: product.id, qty: 4, price: 30, taxRate: 0.13, remark: 'oqc' }],
    }, 'create sales order for oqc')
    await must(submitterToken, 'POST', `/api/sales/orders/${order.id}/submit`, { remark: 'submit' }, 'submit oqc so')
    const approved = await must(adminToken, 'POST', `/api/sales/orders/${order.id}/approve`, { remark: 'approve' }, 'approve oqc so')
    const orderLine = (approved.lines || approved.items || [])[0]
    if (!orderLine?.id) throw new Error('sales order missing line id')

    const delivery = await must(adminToken, 'POST', '/api/sales/deliveries', {
      orderId: approved.id,
      warehouseId: warehouse.id,
      deliveryDate: today,
      remark: `oqc-delivery-${SUFFIX}`,
      lines: [{ orderLineId: orderLine.id, qty: 4, remark: 'oqc delivery' }],
    }, 'create draft delivery')

    const blocked = await request(adminToken, 'POST', `/api/sales/deliveries/${delivery.id}/post`, undefined)
    const gateBlocked = !ok(blocked) && /检验|质检|OQC|需检验/i.test(String(failMsg(blocked)))
    row('B4-1', '未做 OQC 禁止出库过账', gateBlocked, failMsg(blocked))

    const inspection = await must(adminToken, 'POST', '/api/qc/inspections', {
      inspectionType: 'OQC',
      deliveryId: delivery.id,
      inspectionDate: today,
      remark: `oqc-insp-${SUFFIX}`,
    }, 'create oqc inspection')
    row('B4-2', '创建 OQC 检验单', inspection.inspectionType === 'OQC' && inspection.status === 'DRAFT', inspection.inspectionNo)

    await must(adminToken, 'POST', `/api/qc/inspections/${inspection.id}/submit`, undefined, 'submit oqc')
    const detail = await must(adminToken, 'GET', `/api/qc/inspections/${inspection.id}`, undefined, 'get oqc detail')
    const judgeLines = (detail.lines || []).map((line) => ({
      lineId: line.id,
      qualifiedQty: Number(line.inspectedQty ?? line.qty ?? 4),
      unqualifiedQty: 0,
    }))
    const judged = await must(adminToken, 'POST', `/api/qc/inspections/${inspection.id}/judge`, {
      lines: judgeLines,
    }, 'judge oqc')
    row('B4-3', '判定 OQC', judged.status === 'JUDGED', judged.status)

    const posted = await must(adminToken, 'POST', `/api/sales/deliveries/${delivery.id}/post`, undefined, 'post delivery after oqc')
    row('B4-4', 'OQC 后允许出库过账', posted.status === 'POSTED', posted.status)
  } catch (e) {
    row('B4', 'OQC 闸门闭环', false, e.message)
  }
}

async function main() {
  console.log(`extension-features-api-smoke BASE=${BASE} suffix=${SUFFIX}`)
  let admin
  let submitter
  try {
    admin = await login(ADMIN_USER, ADMIN_PASSWORD)
    submitter = await login(SUBMITTER_USER, SUBMITTER_PASSWORD)
    row('AUTH', '双账号登录', true, `${ADMIN_USER}+${SUBMITTER_USER}`)
  } catch (e) {
    row('AUTH', '双账号登录', false, e.message)
    writeReport()
    process.exit(1)
  }

  const purchaseOrder = await smokePurchaseInquiry(
    admin.accessToken || admin.token,
    submitter.accessToken || submitter.token,
  )
  await smokeInvoice(admin.accessToken || admin.token, purchaseOrder)
  await smokeSalesCredit(admin.accessToken || admin.token, submitter.accessToken || submitter.token)
  await smokeOqc(admin.accessToken || admin.token, submitter.accessToken || submitter.token)

  writeReport()
  const failed = results.filter((r) => !r.pass)
  if (failed.length) {
    console.error(`\nFAILED ${failed.length}/${results.length}`)
    process.exit(1)
  }
  console.log(`\nALL PASS ${results.length}/${results.length}`)
}

function writeReport() {
  const outDir = path.join(process.cwd(), 'target', 'extension-features-api-smoke')
  fs.mkdirSync(outDir, { recursive: true })
  const summary = {
    generatedAt: new Date().toISOString(),
    base: BASE,
    suffix: SUFFIX,
    total: results.length,
    passed: results.filter((r) => r.pass).length,
    failed: results.filter((r) => !r.pass).length,
    results,
  }
  fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2))
  const md = [
    '# Extension Features API Smoke',
    '',
    `- time: ${summary.generatedAt}`,
    `- base: ${BASE}`,
    `- pass: ${summary.passed}/${summary.total}`,
    '',
    ...results.map((r) => `- [${r.pass ? 'x' : ' '}] ${r.id} ${r.title}: ${r.detail}`),
    '',
  ].join('\n')
  fs.writeFileSync(path.join(outDir, 'summary.md'), md)
  console.log(`report: ${path.join(outDir, 'summary.md')}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
