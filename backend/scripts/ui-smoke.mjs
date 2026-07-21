import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const backendDir = resolve(scriptDir, '..')
const frontendDir = process.env.ERP_FRONTEND_DIR
  ? resolve(process.env.ERP_FRONTEND_DIR)
  : resolve(backendDir, '..', 'frontend')
const targetDir = join(backendDir, 'target')
const chromePath = resolveChromePath()

const children = []
const loginCache = new Map()
const sleep = (ms) => new Promise((resolveSleep) => setTimeout(resolveSleep, ms))

function resolveChromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  ].filter(Boolean)

  const found = candidates.find((candidate) => existsSync(candidate))
  if (!found) {
    throw new Error('Chrome or Edge executable not found. Set CHROME_PATH to run UI smoke.')
  }
  return found
}

function start(name, command, args, cwd) {
  const child = spawn(command, args, {
    cwd,
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true
  })
  children.push({ name, child })
  child.stdout.on('data', (chunk) => appendLog(name, chunk))
  child.stderr.on('data', (chunk) => appendLog(name, chunk))
  child.on('exit', (code, signal) => appendLog(name, `\n[exit code=${code} signal=${signal}]\n`))
  return child
}

function appendLog(name, chunk) {
  writeFileSync(join(targetDir, `ui-smoke-${name}.log`), chunk, { flag: 'a' })
}

async function waitFor(name, url, timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs
  let lastError = ''
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) {
        return response
      }
      lastError = `${response.status} ${response.statusText}`
    } catch (error) {
      lastError = error.message
    }
    await sleep(1000)
  }
  throw new Error(`${name} did not become ready: ${lastError}`)
}

async function login() {
  const candidates = [
    { username: 'admin', password: 'LocalAdmin123' },
    { username: 'runtime_smoke', password: 'RuntimeSmoke123' }
  ]
  const failures = []
  for (const credentials of candidates) {
    try {
      return { credentials, auth: await loginWithCredentials(credentials) }
    } catch (error) {
      failures.push(`${credentials.username}: ${error.message}`)
    }
  }
  throw new Error(`login failed:\n${failures.join('\n')}`)
}

async function loginWithCredentials(credentials) {
  const cacheKey = `${credentials.username}\0${credentials.password}`
  const cached = loginCache.get(cacheKey)
  if (cached) {
    return cached
  }
  const response = await fetch('http://127.0.0.1:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials)
  })
  const body = await response.text()
  if (response.ok) {
    const payload = JSON.parse(body)
    if (payload.code === '0' && payload.data?.accessToken) {
      loginCache.set(cacheKey, payload.data)
      return payload.data
    }
  }
  throw new Error(`HTTP ${response.status} ${body}`)
}

async function apiRequest(auth, method, path, body) {
  const response = await fetch(`http://127.0.0.1:8080/api${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${auth.accessToken}`,
      ...(body == null ? {} : { 'Content-Type': 'application/json' })
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  let payload = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch (error) {
    throw new Error(`${method} ${path} returned non-json response: HTTP ${response.status} ${text.slice(0, 500)}`)
  }
  if (!response.ok || payload?.code !== '0') {
    throw new Error(`${method} ${path} failed: HTTP ${response.status} ${text.slice(0, 1000)}`)
  }
  return payload.data
}

async function ensureOpenPeriod(auth, dateText) {
  const year = Number(dateText.slice(0, 4))
  const periodMonth = dateText.slice(0, 7)
  let periods = await apiRequest(auth, 'GET', `/finance/periods?year=${year}`)
  if (!periods.some((period) => period.periodMonth === periodMonth)) {
    periods = await apiRequest(auth, 'POST', '/finance/periods/generate', { year })
  }
  const period = periods.find((item) => item.periodMonth === periodMonth)
  if (!period || period.status !== 'OPEN') {
    throw new Error(`required open account period ${periodMonth}, actual=${period ? period.status : 'missing'}`)
  }
}

async function prepareProductionOrderFixture(auth) {
  const suffix = String(Date.now()).slice(-10)
  const plannedStartDate = new Date().toISOString().slice(0, 10)
  const plannedFinishDate = plannedStartDate
  await ensureOpenPeriod(auth, plannedStartDate)

  const finishedProduct = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UIFG${suffix}`,
    productName: `UI生产成品${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI生产联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 20,
    salePrice: 50,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: 'UI smoke 生产成品'
  })
  const materialProduct = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UIMAT${suffix}`,
    productName: `UI生产材料${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI生产联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 5,
    salePrice: 8,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: 'UI smoke 生产材料'
  })
  const materialWarehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UIMW${suffix}`,
    warehouseName: `UI材料仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 材料仓',
    remark: 'UI smoke 生产材料仓'
  })
  const finishedWarehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UIFW${suffix}`,
    warehouseName: `UI成品仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 成品仓',
    remark: 'UI smoke 生产成品仓'
  })
  const bom = await apiRequest(auth, 'POST', '/production/boms', {
    productId: finishedProduct.id,
    baseQty: 1,
    remark: `UI smoke BOM ${suffix}`,
    lines: [
      {
        materialProductId: materialProduct.id,
        qtyPer: 2,
        lossRate: 0,
        remark: 'UI smoke 生产材料'
      }
    ]
  })
  const adjustment = await apiRequest(auth, 'POST', '/inventory/adjustments', {
    warehouseId: materialWarehouse.id,
    adjustmentDate: plannedStartDate,
    remark: `UI smoke 生产备料 ${suffix}`,
    lines: [
      {
        productId: materialProduct.id,
        direction: 'IN',
        qty: 20,
        unitCost: 5,
        reason: 'UI smoke 生产备料'
      }
    ]
  })
  await apiRequest(auth, 'POST', `/inventory/adjustments/${adjustment.id}/post`)

  return {
    suffix,
    plannedStartDate,
    plannedFinishDate,
    plannedQty: 5,
    requiredMaterialQty: 10,
    finishedProduct,
    materialProduct,
    materialWarehouse,
    finishedWarehouse,
    bom
  }
}

async function prepareSalesOrderFixture(auth) {
  const suffix = String(Date.now()).slice(-10)
  const orderDate = new Date().toISOString().slice(0, 10)
  const deliveryDate = orderDate
  await ensureOpenPeriod(auth, orderDate)

  const customer = await apiRequest(auth, 'POST', '/masterdata/customers', {
    customerCode: `UICS${suffix}`,
    customerName: `UI销售客户${suffix}`,
    contactName: 'UI smoke',
    contactPhone: '13800000000',
    settlementMethod: 'BANK_TRANSFER',
    creditLimit: 10000,
    address: 'UI smoke 客户地址',
    remark: 'UI smoke 销售客户'
  })
  const warehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UISW${suffix}`,
    warehouseName: `UI销售仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 销售仓',
    remark: 'UI smoke 销售发货仓'
  })
  const product = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UISP${suffix}`,
    productName: `UI销售商品${suffix}`,
    barcode: `UISB${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI销售联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 50,
    salePrice: 88.5,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: 'UI smoke 销售商品'
  })
  const adjustment = await apiRequest(auth, 'POST', '/inventory/adjustments', {
    warehouseId: warehouse.id,
    adjustmentDate: orderDate,
    remark: `UI smoke 销售备货 ${suffix}`,
    lines: [
      {
        productId: product.id,
        direction: 'IN',
        qty: 20,
        unitCost: 50,
        reason: 'UI smoke 销售备货'
      }
    ]
  })
  await apiRequest(auth, 'POST', `/inventory/adjustments/${adjustment.id}/post`)

  return {
    suffix,
    orderDate,
    deliveryDate,
    qty: 3,
    price: 88.5,
    taxRate: 0.13,
    customer,
    warehouse,
    product
  }
}

async function prepareInventoryAdjustmentFixture(auth) {
  const suffix = String(Date.now()).slice(-10)
  const adjustmentDate = new Date().toISOString().slice(0, 10)
  await ensureOpenPeriod(auth, adjustmentDate)

  const warehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UIAW${suffix}`,
    warehouseName: `UI调整仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 库存调整仓',
    remark: 'UI smoke 库存调整仓'
  })
  const product = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UIAP${suffix}`,
    productName: `UI调整商品${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI库存联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 12.5,
    salePrice: 20,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: 'UI smoke 库存调整商品'
  })

  return {
    suffix,
    adjustmentDate,
    qty: 7,
    warehouse,
    product
  }
}

async function prepareInventoryTransferFixture(auth) {
  const suffix = String(Date.now()).slice(-10)
  const transferDate = new Date().toISOString().slice(0, 10)
  await ensureOpenPeriod(auth, transferDate)

  const fromWarehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UITF${suffix}`,
    warehouseName: `UI调出仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 调出仓',
    remark: 'UI smoke 库存调拨调出仓'
  })
  const toWarehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UITT${suffix}`,
    warehouseName: `UI调入仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 调入仓',
    remark: 'UI smoke 库存调拨调入仓'
  })
  const product = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UITP${suffix}`,
    productName: `UI调拨商品${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI库存联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 9.5,
    salePrice: 18,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: 'UI smoke 库存调拨商品'
  })
  const initialQty = 11
  const transferQty = 4
  const adjustment = await apiRequest(auth, 'POST', '/inventory/adjustments', {
    warehouseId: fromWarehouse.id,
    adjustmentDate: transferDate,
    remark: `UI smoke 调拨备货 ${suffix}`,
    lines: [
      {
        productId: product.id,
        direction: 'IN',
        qty: initialQty,
        unitCost: 9.5,
        reason: 'UI smoke 调拨备货'
      }
    ]
  })
  await apiRequest(auth, 'POST', `/inventory/adjustments/${adjustment.id}/post`)

  return {
    suffix,
    transferDate,
    initialQty,
    transferQty,
    fromWarehouse,
    toWarehouse,
    product
  }
}

async function prepareInventoryCheckFixture(auth) {
  const suffix = String(Date.now()).slice(-10)
  const checkDate = new Date().toISOString().slice(0, 10)
  await ensureOpenPeriod(auth, checkDate)

  const warehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UICH${suffix}`,
    warehouseName: `UI盘点仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 库存盘点仓',
    remark: 'UI smoke 库存盘点仓'
  })
  const product = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UICP${suffix}`,
    productName: `UI盘点商品${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI库存联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 8,
    salePrice: 16,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: 'UI smoke 库存盘点商品'
  })
  const initialQty = 10
  const actualQty = 13
  const adjustment = await apiRequest(auth, 'POST', '/inventory/adjustments', {
    warehouseId: warehouse.id,
    adjustmentDate: checkDate,
    remark: `UI smoke 盘点备货 ${suffix}`,
    lines: [
      {
        productId: product.id,
        direction: 'IN',
        qty: initialQty,
        unitCost: 8,
        reason: 'UI smoke 盘点备货'
      }
    ]
  })
  await apiRequest(auth, 'POST', `/inventory/adjustments/${adjustment.id}/post`)

  return {
    suffix,
    checkDate,
    initialQty,
    actualQty,
    warehouse,
    product
  }
}

async function prepareApprovedSalesOrderFixture(auth) {
  const fixture = await prepareSalesOrderFixture(auth)
  const submitterCredentials = { username: 'runtime_smoke', password: 'RuntimeSmoke123' }
  const submitterAuth = await loginWithCredentials(submitterCredentials)
  const order = await apiRequest(submitterAuth, 'POST', '/sales/orders', {
    customerId: fixture.customer.id,
    warehouseId: fixture.warehouse.id,
    orderDate: fixture.orderDate,
    deliveryDate: fixture.deliveryDate,
    remark: `UI smoke 待发货销售订单 ${fixture.suffix}`,
    lines: [
      {
        productId: fixture.product.id,
        qty: fixture.qty,
        price: fixture.price,
        taxRate: fixture.taxRate,
        remark: 'UI smoke 销售订单明细'
      }
    ]
  })
  await apiRequest(submitterAuth, 'POST', `/sales/orders/${order.id}/submit`, { remark: 'UI smoke 提交销售订单' })
  const approvedOrder = await apiRequest(auth, 'POST', `/sales/orders/${order.id}/approve`, { remark: 'UI smoke 审批销售订单' })

  return {
    ...fixture,
    submitter: submitterCredentials.username,
    approver: 'admin',
    order: approvedOrder
  }
}

async function preparePostedSalesDeliveryFixture(auth) {
  const fixture = await prepareApprovedSalesOrderFixture(auth)
  const orderLine = (fixture.order.lines || fixture.order.items || [])[0]
  if (!orderLine?.id) {
    throw new Error(`approved sales order ${fixture.order.orderNo} has no line id`)
  }
  const delivery = await apiRequest(auth, 'POST', '/sales/deliveries', {
    orderId: fixture.order.id,
    warehouseId: fixture.warehouse.id,
    deliveryDate: fixture.orderDate,
    remark: `UI smoke 已过账销售发货 ${fixture.suffix}`,
    lines: [
      {
        orderLineId: orderLine.id,
        qty: fixture.qty,
        remark: 'UI smoke 销售发货明细'
      }
    ]
  })
  const postedDelivery = await apiRequest(auth, 'POST', `/sales/deliveries/${delivery.id}/post`)
  const receivables = await apiRequest(auth, 'GET', `/finance/receivables?pageNo=1&pageSize=20&customerId=${fixture.customer.id}`)
  const receivable = (receivables.records || []).find((item) => item.sourceNo === postedDelivery.deliveryNo)
  if (!receivable?.id) {
    throw new Error(`receivable not created for delivery ${postedDelivery.deliveryNo}`)
  }

  return {
    ...fixture,
    delivery: postedDelivery,
    receivable
  }
}

async function preparePurchaseOrderFixture(auth) {
  const suffix = String(Date.now()).slice(-10)
  const orderDate = new Date().toISOString().slice(0, 10)
  const deliveryDate = orderDate
  await ensureOpenPeriod(auth, orderDate)

  const supplier = await apiRequest(auth, 'POST', '/masterdata/suppliers', {
    supplierCode: `UIPS${suffix}`,
    supplierName: `UI采购供应商${suffix}`,
    contactName: 'UI smoke',
    contactPhone: '13900000000',
    settlementMethod: 'BANK_TRANSFER',
    address: 'UI smoke 供应商地址',
    remark: 'UI smoke 采购供应商'
  })
  const warehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UIPW${suffix}`,
    warehouseName: `UI采购仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 采购仓',
    remark: 'UI smoke 采购收货仓'
  })
  const product = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UIPP${suffix}`,
    productName: `UI采购商品${suffix}`,
    barcode: `UIPB${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI采购联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 36.6,
    salePrice: 66,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: 'UI smoke 采购商品'
  })

  return {
    suffix,
    orderDate,
    deliveryDate,
    qty: 4,
    price: 36.6,
    taxRate: 0.13,
    supplier,
    warehouse,
    product
  }
}

async function preparePostedPurchaseReceiptFixture(auth) {
  const fixture = await preparePurchaseOrderFixture(auth)
  const submitterCredentials = { username: 'runtime_smoke', password: 'RuntimeSmoke123' }
  const submitterAuth = await loginWithCredentials(submitterCredentials)
  const order = await apiRequest(submitterAuth, 'POST', '/purchase/orders', {
    supplierId: fixture.supplier.id,
    orderDate: fixture.orderDate,
    deliveryDate: fixture.deliveryDate,
    remark: `UI smoke 待收货采购订单 ${fixture.suffix}`,
    lines: [
      {
        productId: fixture.product.id,
        qty: fixture.qty,
        price: fixture.price,
        taxRate: fixture.taxRate,
        remark: 'UI smoke 采购订单明细'
      }
    ]
  })
  await apiRequest(submitterAuth, 'POST', `/purchase/orders/${order.id}/submit`, { remark: 'UI smoke 提交采购订单' })
  const approvedOrder = await apiRequest(auth, 'POST', `/purchase/orders/${order.id}/approve`, { remark: 'UI smoke 审批采购订单' })
  const orderLine = (approvedOrder.lines || approvedOrder.items || [])[0]
  if (!orderLine?.id) {
    throw new Error(`approved purchase order ${approvedOrder.orderNo} has no line id`)
  }
  const receipt = await apiRequest(auth, 'POST', '/purchase/receipts', {
    orderId: approvedOrder.id,
    warehouseId: fixture.warehouse.id,
    receiptDate: fixture.orderDate,
    remark: `UI smoke 已过账采购收货 ${fixture.suffix}`,
    lines: [
      {
        orderLineId: orderLine.id,
        qty: fixture.qty,
        remark: 'UI smoke 采购收货明细'
      }
    ]
  })
  const postedReceipt = await apiRequest(auth, 'POST', `/purchase/receipts/${receipt.id}/post`)
  const payables = await apiRequest(auth, 'GET', `/finance/payables?pageNo=1&pageSize=20&supplierId=${fixture.supplier.id}`)
  const payable = (payables.records || []).find((item) => item.sourceNo === postedReceipt.receiptNo)
  if (!payable?.id) {
    throw new Error(`payable not created for receipt ${postedReceipt.receiptNo}`)
  }

  return {
    ...fixture,
    order: approvedOrder,
    receipt: postedReceipt,
    payable
  }
}

async function prepareDraftPurchaseReceiptForQcFixture(auth) {
  const suffix = String(Date.now()).slice(-10)
  const orderDate = new Date().toISOString().slice(0, 10)
  await ensureOpenPeriod(auth, orderDate)

  const supplier = await apiRequest(auth, 'POST', '/masterdata/suppliers', {
    supplierCode: `UIQCS${suffix}`,
    supplierName: `UI质检供应商${suffix}`,
    contactName: 'UI smoke',
    contactPhone: '13900000000',
    settlementMethod: 'BANK_TRANSFER',
    address: 'UI smoke 质检供应商地址',
    remark: 'UI smoke 质检供应商'
  })
  const warehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `UIQCW${suffix}`,
    warehouseName: `UI质检收货仓${suffix}`,
    deptId: 3501,
    managerUserId: 4001,
    address: 'UI smoke 质检收货仓',
    remark: 'UI smoke 质检收货仓'
  })
  // 需检验商品:采购入库过账前须先完成来料质检,QC 判定后仅合格品入库。
  const product = await apiRequest(auth, 'POST', '/masterdata/products', {
    productCode: `UIQCP${suffix}`,
    productName: `UI质检商品${suffix}`,
    productType: 'GOODS',
    categoryName: 'UI质检联调',
    specification: 'UI smoke',
    unitName: '件',
    purchasePrice: 20,
    salePrice: 40,
    taxRate: 13,
    lotControlled: false,
    shelfLifeControlled: false,
    inspectionRequired: true,
    remark: 'UI smoke 需检验商品'
  })

  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  const qty = 4
  const price = 20
  const order = await apiRequest(submitterAuth, 'POST', '/purchase/orders', {
    supplierId: supplier.id,
    orderDate,
    deliveryDate: orderDate,
    remark: `UI smoke 质检采购订单 ${suffix}`,
    lines: [
      {
        productId: product.id,
        qty,
        price,
        taxRate: 0.13,
        remark: 'UI smoke 质检采购订单明细'
      }
    ]
  })
  await apiRequest(submitterAuth, 'POST', `/purchase/orders/${order.id}/submit`, { remark: 'UI smoke 提交质检采购订单' })
  const approvedOrder = await apiRequest(auth, 'POST', `/purchase/orders/${order.id}/approve`, { remark: 'UI smoke 审批质检采购订单' })
  const orderLine = (approvedOrder.lines || approvedOrder.items || [])[0]
  if (!orderLine?.id) {
    throw new Error(`approved purchase order ${approvedOrder.orderNo} has no line id`)
  }
  // 只创建到 DRAFT 入库单为止,不过账 —— QC 检验单引用的正是草稿入库单。
  const receipt = await apiRequest(auth, 'POST', '/purchase/receipts', {
    orderId: approvedOrder.id,
    warehouseId: warehouse.id,
    receiptDate: orderDate,
    remark: `UI smoke 待检验采购收货 ${suffix}`,
    lines: [
      {
        orderLineId: orderLine.id,
        qty,
        remark: 'UI smoke 质检采购收货明细'
      }
    ]
  })
  const receiptLine = (receipt.lines || [])[0]
  if (!receiptLine?.id) {
    throw new Error(`draft receipt ${receipt.receiptNo} has no line id`)
  }

  return {
    suffix,
    orderDate,
    qty,
    qualifiedQty: 3,
    unqualifiedQty: 1,
    supplier,
    warehouse,
    product,
    receipt,
    receiptLine
  }
}

async function connectCdp() {
  await waitFor('chrome cdp', 'http://127.0.0.1:9223/json/version', 20000)
  let pages = await (await fetch('http://127.0.0.1:9223/json/list')).json()
  let page = pages.find((entry) => entry.type === 'page')
  if (!page) {
    await fetch('http://127.0.0.1:9223/json/new?about:blank', { method: 'PUT' })
    pages = await (await fetch('http://127.0.0.1:9223/json/list')).json()
    page = pages.find((entry) => entry.type === 'page')
  }
  if (!page?.webSocketDebuggerUrl) {
    throw new Error('Chrome page WebSocket endpoint not found')
  }

  const ws = new WebSocket(page.webSocketDebuggerUrl)
  const pending = new Map()
  const events = { console: [], exceptions: [], apiErrors: [], networkFailures: [] }
  let sequence = 0

  ws.addEventListener('message', (message) => {
    const payload = JSON.parse(message.data)
    if (payload.id && pending.has(payload.id)) {
      const { resolve, reject } = pending.get(payload.id)
      pending.delete(payload.id)
      if (payload.error) {
        reject(new Error(`${payload.error.message}: ${payload.error.data || ''}`))
      } else {
        resolve(payload.result)
      }
      return
    }

    if (payload.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(payload.params.type)) {
      events.console.push({
        type: payload.params.type,
        text: payload.params.args.map((arg) => arg.value || arg.description || '').join(' ')
      })
    }
    if (payload.method === 'Runtime.exceptionThrown') {
      events.exceptions.push(payload.params.exceptionDetails.text)
    }
    if (payload.method === 'Network.loadingFailed') {
      events.networkFailures.push(payload.params.errorText)
    }
    if (payload.method === 'Network.responseReceived') {
      const response = payload.params.response
      if (response.url.includes('/api/') && response.status >= 400) {
        events.apiErrors.push(`${response.status} ${response.url}`)
      }
    }
  })

  await new Promise((resolveOpen, rejectOpen) => {
    ws.addEventListener('open', resolveOpen, { once: true })
    ws.addEventListener('error', rejectOpen, { once: true })
  })

  const send = (method, params = {}) => {
    const id = ++sequence
    ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolveSend, rejectSend) => pending.set(id, { resolve: resolveSend, reject: rejectSend }))
  }

  return { send, events, close: () => ws.close() }
}

function snapshotFailures(events) {
  return [
    ...events.exceptions.map((text) => `exception: ${text}`),
    ...events.console.map((entry) => `console ${entry.type}: ${entry.text}`),
    ...events.apiErrors.map((text) => `api: ${text}`),
    ...events.networkFailures.map((text) => `network: ${text}`)
  ]
}

function resetEvents(events) {
  events.console.length = 0
  events.exceptions.length = 0
  events.apiErrors.length = 0
  events.networkFailures.length = 0
}

async function evaluatePage(cdp, expression) {
  const result = await cdp.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (result.exceptionDetails) {
    const detail = result.exceptionDetails.exception?.description
      || result.exceptionDetails.exception?.value
      || result.exceptionDetails.text
      || 'page evaluation failed'
    throw new Error(detail)
  }
  return result.result.value
}

async function waitForPage(cdp, expression, label, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs
  let lastValue
  while (Date.now() < deadline) {
    lastValue = await evaluatePage(cdp, expression)
    if (lastValue) {
      return lastValue
    }
    await sleep(250)
  }
  throw new Error(`timeout waiting for ${label}: ${JSON.stringify(lastValue)}`)
}

async function markSmokeStep(cdp, step) {
  await evaluatePage(cdp, `
    (() => {
      window.__uiSmoke = {
        ...(window.__uiSmoke || {}),
        step: ${JSON.stringify(step)}
      };
      return true;
    })()
  `)
}

async function setBrowserAuth(cdp, auth) {
  await evaluatePage(cdp, `
    (() => {
      localStorage.setItem('token', ${JSON.stringify(auth.accessToken)});
      localStorage.setItem('refreshToken', ${JSON.stringify(auth.refreshToken)});
      return true;
    })()
  `)
}

function visibleElementWithTextExpression(selector, text) {
  return `
    (() => {
      const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      return [...document.querySelectorAll(${JSON.stringify(selector)})]
        .find((element) => visible(element) && element.innerText.includes(${JSON.stringify(text)}));
    })()
  `
}

function dialogExpression(text) {
  return visibleElementWithTextExpression('.el-dialog', text)
}

function messageBoxExpression(text) {
  return visibleElementWithTextExpression('.el-message-box', text)
}

function visibleTextExpression(scopeExpression, text) {
  return `
    (() => {
      const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      const scope = ${scopeExpression};
      return Boolean(scope && scope.innerText.includes(${JSON.stringify(text)}));
    })()
  `
}

function buttonExpression(text, scopeExpression = 'document') {
  return `((scope) => scope && [...scope.querySelectorAll('button')].find((item) => visible(item) && item.innerText.trim().includes(${JSON.stringify(text)})))(${scopeExpression})`
}

function rowActionScopeExpression(rowIndex = 0) {
  return `[...document.querySelectorAll('.row-actions')].filter(visible)[${rowIndex}]`
}

function rowActionButtonExpression(text, rowIndex = 0) {
  return buttonExpression(text, rowActionScopeExpression(rowIndex))
}

async function clickElement(cdp, elementExpression, label) {
  const target = await waitForPage(cdp, `
    (() => {
      const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      const element = ${elementExpression};
      if (!visible(element)) return null;
      const rect = element.getBoundingClientRect();
      return {
        x: rect.left + rect.width / 2,
        y: rect.top + rect.height / 2,
        text: element.innerText || element.getAttribute('aria-label') || ''
      };
    })()
  `, label)
  await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: target.x, y: target.y, button: 'none' })
  await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: target.x, y: target.y, button: 'left', clickCount: 1 })
  await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: target.x, y: target.y, button: 'left', clickCount: 1 })
  await sleep(250)
}

async function clickButton(cdp, text, scopeExpression = 'document') {
  await clickElement(cdp, buttonExpression(text, scopeExpression), `button ${text}`)
}

async function invokeElement(cdp, elementExpression, label) {
  await waitForPage(cdp, `
    (() => {
      const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      const element = ${elementExpression};
      if (!visible(element)) return false;
      element.click();
      return true;
    })()
  `, label)
  await sleep(250)
}

async function invokeButton(cdp, text, label, scopeExpression = 'document') {
  await invokeElement(cdp, buttonExpression(text, scopeExpression), label)
}

async function setElementValue(cdp, elementExpression, value, label) {
  await waitForPage(cdp, `
    (() => {
      const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      const element = ${elementExpression};
      if (!visible(element)) return false;
      element.focus();
      const prototype = element instanceof HTMLTextAreaElement
        ? HTMLTextAreaElement.prototype
        : HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
      if (setter) {
        setter.call(element, ${JSON.stringify(value)});
      } else {
        element.value = ${JSON.stringify(value)};
      }
      element.dispatchEvent(new InputEvent('input', { bubbles: true, data: ${JSON.stringify(value)}, inputType: 'insertText' }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
      element.blur();
      return element.value === ${JSON.stringify(value)};
    })()
  `, label)
}

async function pressElementKey(cdp, elementExpression, key, label) {
  await waitForPage(cdp, `
    (() => {
      const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      const element = ${elementExpression};
      if (!visible(element)) return false;
      element.focus();
      element.dispatchEvent(new KeyboardEvent('keydown', { key: ${JSON.stringify(key)}, code: ${JSON.stringify(key)}, bubbles: true }));
      element.dispatchEvent(new KeyboardEvent('keyup', { key: ${JSON.stringify(key)}, code: ${JSON.stringify(key)}, bubbles: true }));
      return true;
    })()
  `, label)
  await sleep(250)
}

async function pageDiagnostics(cdp, label) {
  return evaluatePage(cdp, `
    (() => {
      const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
      };
      const dialogs = [...document.querySelectorAll('.el-dialog')]
        .filter(visible)
        .map((dialog) => dialog.innerText.slice(0, 800));
      return {
        label: ${JSON.stringify(label)},
        path: location.pathname,
        dialogs,
        invalidText: [...document.querySelectorAll('.el-form-item__error')]
          .filter(visible)
          .map((item) => item.innerText.trim()),
        messages: [...document.querySelectorAll('.el-message')]
          .filter(visible)
          .map((item) => item.innerText.trim()),
        buttons: [...document.querySelectorAll('button')]
          .filter(visible)
          .map((item) => {
            const rect = item.getBoundingClientRect();
            return {
              text: item.innerText.trim(),
              disabled: item.disabled || item.getAttribute('aria-disabled') === 'true',
              rect: {
                x: Math.round(rect.x),
                y: Math.round(rect.y),
                width: Math.round(rect.width),
                height: Math.round(rect.height)
              }
            };
          })
          .slice(0, 40),
        inputs: [...document.querySelectorAll('input, textarea')]
          .filter(visible)
          .map((item) => ({
            tag: item.tagName.toLowerCase(),
            type: item.getAttribute('type') || '',
            placeholder: item.getAttribute('placeholder') || '',
            value: item.value || '',
            className: item.className || '',
            ariaLabel: item.getAttribute('aria-label') || ''
          }))
          .slice(0, 80),
        smoke: window.__uiSmoke || null,
        bodyText: document.body.innerText.slice(0, 1600)
        ,
        attachmentDownload: window.__uiSmokeAttachmentDownload || null
      };
    })()
  `)
}

async function runExceptionTicketWorkflow(cdp) {
  const title = `UI深度联调异常工单-${Date.now()}`
  const sourceNo = `UI-SMOKE-${Date.now()}`
  resetEvents(cdp.events)
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/exception-tickets' })
    await waitForPage(cdp, `document.body.innerText.includes('异常工单')`, 'exception ticket page')

    await clickButton(cdp, '新建')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新建异常工单')});
      })()
    `, 'create dialog')

    const createDialog = dialogExpression('新建异常工单')
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="说明异常事项"]')`, title, 'ticket title input')
    await setElementValue(
      cdp,
      `${createDialog}.querySelector('textarea[placeholder^="补充异常背景"]')`,
      '通过 Headless Chrome 从页面创建，用于验证异常工单真实页面操作闭环',
      'ticket description input'
    )
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="业务单据编号"]')`, sourceNo, 'ticket source number input')
    await evaluatePage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const button = ${buttonExpression('创建', createDialog)};
        window.__uiSmoke = { createClicks: 0, requests: [] };
        button.addEventListener('click', () => { window.__uiSmoke.createClicks += 1; }, { capture: true });
        const rawFetch = window.fetch;
        window.fetch = (...args) => {
          window.__uiSmoke.requests.push({ type: 'fetch', url: String(args[0]) });
          return rawFetch(...args);
        };
        const rawOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url, ...rest) {
          const record = { type: 'xhr', method, url: String(url), status: null, done: false, error: null, responseText: '' };
          window.__uiSmoke.requests.push(record);
          this.addEventListener('loadend', () => {
            record.status = this.status;
            record.done = true;
            record.responseText = String(this.responseText || '').slice(0, 500);
          });
          this.addEventListener('error', () => { record.error = 'error'; });
          this.addEventListener('timeout', () => { record.error = 'timeout'; });
          return rawOpen.call(this, method, url, ...rest);
        };
        return true;
      })()
    `)
    await clickButton(cdp, '创建', createDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('新建异常工单')};
      })()
    `, 'create dialog closed').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after create click'))}`)
    })

    await setElementValue(cdp, `document.querySelector('.keyword-input input')`, title, 'ticket keyword input')
    await clickButton(cdp, '查询')
    await waitForPage(cdp, `document.body.innerText.includes(${JSON.stringify(title)})`, 'created ticket visible')

    const rowTextExpression = `
      (() => {
        const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
        const row = rows.find((item) => item.innerText.includes(${JSON.stringify(title)}));
        return row?.innerText || '';
      })()
    `

    const runAction = async (buttonText, dialogText, commentText, expectedRowText, assigneeUserId) => {
      await invokeElement(cdp, rowActionButtonExpression(buttonText), `ticket action ${buttonText}`)
      await waitForPage(cdp, `
        (() => {
          const visible = (element) => {
            if (!element) return false;
            const style = window.getComputedStyle(element);
            const rect = element.getBoundingClientRect();
            return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
          };
          return Boolean(${dialogExpression(dialogText)});
        })()
      `, dialogText)
      const actionDialog = dialogExpression(dialogText)
      if (assigneeUserId) {
        await setElementValue(cdp, `${actionDialog}.querySelector('input[placeholder="用户ID"]')`, String(assigneeUserId), 'assignee input')
      }
      await setElementValue(cdp, `${actionDialog}.querySelector('textarea')`, commentText, `${dialogText} comment input`)
      await clickButton(cdp, '确认', actionDialog)
      await waitForPage(cdp, `
        (() => {
          const visible = (element) => {
            if (!element) return false;
            const style = window.getComputedStyle(element);
            const rect = element.getBoundingClientRect();
            return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
          };
          const dialog = ${dialogExpression(dialogText)};
          const rowText = ${rowTextExpression};
          return !dialog && rowText.includes(${JSON.stringify(expectedRowText)});
        })()
      `, `${dialogText} submitted`).catch(async (error) => {
        throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, `after ${dialogText}`))}`)
      })
    }

    await runAction('分派', '分派异常工单', '页面联调：分派给本地管理员', '4001', 4001)
    await runAction('开始', '开始处理', '页面联调：开始处理', '处理中')
    await runAction('解决', '解决异常工单', '页面联调：已解决', '已解决')
    await runAction('关闭', '关闭异常工单', '页面联调：关闭确认', '已关闭')
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const result = await evaluatePage(cdp, `({
    title: ${JSON.stringify(title)},
    sourceNo: ${JSON.stringify(sourceNo)},
    finalTextContainsTitle: document.body.innerText.includes(${JSON.stringify(title)}),
    finalTextContainsClosed: document.body.innerText.includes('已关闭')
  })`)

  const failures = snapshotFailures(cdp.events)
  if (!result.finalTextContainsTitle) {
    failures.push(`created ticket not visible: ${title}`)
  }
  if (!result.finalTextContainsClosed) {
    failures.push(`created ticket not closed: ${title}`)
  }

  return {
    name: 'exception-ticket-create-and-close',
    title: result.title,
    sourceNo: result.sourceNo,
    passed: failures.length === 0,
    failures
  }
}

async function runSlaPolicyWorkflow(cdp) {
  const remark = `UI smoke SLA策略保存 ${Date.now()}`
  resetEvents(cdp.events)
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/exception-sla-policies' })
    await waitForPage(cdp, `document.body.innerText.includes('SLA策略') && document.body.innerText.includes('配置')`, 'SLA policy page')

    await invokeButton(cdp, '配置', 'rule config button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('配置SLA策略')});
      })()
    `, 'SLA edit dialog')

    const dialog = dialogExpression('配置SLA策略')
    await setElementValue(cdp, `${dialog}.querySelector('textarea[placeholder^="说明 SLA"]')`, remark, 'SLA remark input')
    await clickButton(cdp, '保存', dialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('配置SLA策略')} && document.body.innerText.includes(${JSON.stringify(remark)});
      })()
    `, 'SLA policy saved').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after SLA save'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'SLA workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  const result = await evaluatePage(cdp, `({
    remark: ${JSON.stringify(remark)},
    finalTextContainsRemark: document.body.innerText.includes(${JSON.stringify(remark)})
  })`)
  if (!result.finalTextContainsRemark) {
    failures.push(`saved SLA remark not visible: ${remark}`)
  }
  return {
    name: 'exception-sla-policy-save',
    remark: result.remark,
    passed: failures.length === 0,
    failures
  }
}

async function runExceptionRuleWorkflow(cdp) {
  const remark = `UI smoke 异常规则配置 ${Date.now()}`
  let toggledTo = ''
  let restoredTo = ''
  resetEvents(cdp.events)
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/exception-rules' })
    await waitForPage(cdp, `document.body.innerText.includes('异常规则') && document.body.innerText.includes('扫描全部')`, 'exception rule page')

    const readFirstToggleButton = `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const actions = ${rowActionScopeExpression(0)};
        if (!actions) return null;
        return [...actions.querySelectorAll('button')]
          .filter(visible)
          .map((button) => button.innerText.trim())
          .find((text) => text.includes('停用') || text.includes('启用')) || null;
      })()
    `

    const initialToggleText = await waitForPage(cdp, readFirstToggleButton, 'first rule toggle button')
    await invokeElement(cdp, rowActionButtonExpression('配置'), 'rule config button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('配置异常规则')});
      })()
    `, 'rule edit dialog')

    const dialog = dialogExpression('配置异常规则')
    await setElementValue(cdp, `${dialog}.querySelector('textarea[placeholder^="说明扫描口径"]')`, remark, 'rule remark input')
    await clickButton(cdp, '保存', dialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('配置异常规则')} && document.body.innerText.includes(${JSON.stringify(remark)});
      })()
    `, 'rule config saved').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after rule config save'))}`)
    })

    const toggleText = await waitForPage(cdp, readFirstToggleButton, 'first rule toggle button after save')
    const restoreText = toggleText.includes('停用') ? '启用' : '停用'
    toggledTo = restoreText === '启用' ? '停用' : '启用'
    restoredTo = initialToggleText.includes('停用') ? '启用' : '停用'
    await invokeElement(cdp, rowActionButtonExpression(toggleText), `rule ${toggleText}`)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const messages = [...document.querySelectorAll('.el-message')].filter(visible).map((item) => item.innerText.trim());
        const nextButton = ${rowActionButtonExpression(restoreText)};
        return messages.some((message) => message.includes(${JSON.stringify(`规则已${toggledTo}`)})) || Boolean(nextButton);
      })()
    `, `rule toggled to ${toggledTo}`).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, `after rule ${toggleText}`))}`)
    })

    await invokeElement(cdp, rowActionButtonExpression(restoreText), `rule restore ${restoreText}`)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const messages = [...document.querySelectorAll('.el-message')].filter(visible).map((item) => item.innerText.trim());
        const initialButton = ${rowActionButtonExpression(toggleText)};
        return messages.some((message) => message.includes(${JSON.stringify(`规则已${restoredTo}`)})) || Boolean(initialButton);
      })()
    `, `rule restored to ${restoredTo}`).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, `after rule restore ${restoreText}`))}`)
    })

    await clickButton(cdp, '扫描全部')
    await waitForPage(cdp, `
      document.body.innerText.includes('扫描完成')
        || [...document.querySelectorAll('.el-message')].some((item) => item.innerText.includes('扫描完成'))
    `, 'rule scan all completed', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after rule scan all'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'rule workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  const result = await evaluatePage(cdp, `({
    remark: ${JSON.stringify(remark)},
    toggledTo: ${JSON.stringify(toggledTo)},
    restoredTo: ${JSON.stringify(restoredTo)},
    finalTextContainsRemark: document.body.innerText.includes(${JSON.stringify(remark)}),
    finalTextContainsScanResult: document.body.innerText.includes('扫描完成')
  })`)
  if (!result.finalTextContainsRemark) {
    failures.push(`saved rule remark not visible: ${remark}`)
  }
  if (!result.finalTextContainsScanResult) {
    failures.push('rule scan result not visible')
  }
  return {
    name: 'exception-rule-config-toggle-and-scan-all',
    remark: result.remark,
    toggledTo: result.toggledTo,
    restoredTo: result.restoredTo,
    passed: failures.length === 0,
    failures
  }
}

async function findTraceKeyword(cdp) {
  return evaluatePage(cdp, `
    (async () => {
      const token = localStorage.getItem('token');
      const endpoints = [
        { url: '/api/reports/purchase-orders?pageNo=1&pageSize=1', fields: ['bizNo'] },
        { url: '/api/reports/sales-orders?pageNo=1&pageSize=1', fields: ['bizNo'] },
        { url: '/api/reports/inventory-transactions?pageNo=1&pageSize=1', fields: ['bizNo'] },
        { url: '/api/reports/finance-settlements?pageNo=1&pageSize=1', fields: ['bizNo', 'sourceNo'] }
      ];
      for (const endpoint of endpoints) {
        const response = await fetch(endpoint.url, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) continue;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const row = records[0];
        if (!row) continue;
        for (const field of endpoint.fields) {
          if (row[field]) return String(row[field]);
        }
      }
      return '';
    })()
  `)
}

async function runBusinessTraceWorkflow(cdp) {
  resetEvents(cdp.events)
  const keyword = await findTraceKeyword(cdp)
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/reports/traces' })
    await waitForPage(cdp, `document.body.innerText.includes('生命周期时间线')`, 'business trace page')

    if (keyword) {
      await setElementValue(cdp, `document.querySelector('.keyword-input input')`, keyword, 'trace keyword input')
      await clickButton(cdp, '查询')
      await waitForPage(cdp, `
        (() => {
          const text = document.body.innerText;
          return text.includes(${JSON.stringify(keyword)})
            && text.includes('匹配单据')
            && !text.includes('暂无追踪事件');
        })()
      `, 'business trace result').catch(async (error) => {
        throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after business trace search'))}`)
      })
    } else {
      await setElementValue(cdp, `document.querySelector('.keyword-input input')`, `UI-SMOKE-NO-MATCH-${Date.now()}`, 'trace no match keyword input')
      await clickButton(cdp, '查询')
      await waitForPage(cdp, `
        document.body.innerText.includes('暂无追踪事件')
          && document.body.innerText.includes('匹配单据')
      `, 'business trace empty result').catch(async (error) => {
        throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after empty business trace search'))}`)
      })
    }

    await clickButton(cdp, '重置')
    await waitForPage(cdp, `
      document.body.innerText.includes('输入业务单号后查看关联单据')
        && document.body.innerText.includes('暂无追踪事件')
    `, 'business trace reset')
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'business trace workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'business-trace-search-and-reset',
    keyword: keyword || null,
    mode: keyword ? 'matched' : 'empty',
    passed: failures.length === 0,
    failures
  }
}

async function runFinancePeriodWorkflow(cdp) {
  resetEvents(cdp.events)
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/periods' })
    await waitForPage(cdp, `document.body.innerText.includes('会计期间管理') && document.body.innerText.includes('生成年期间')`, 'finance periods page')

    await invokeButton(cdp, '生成年期间', 'generate periods button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('生成会计期间')});
      })()
    `, 'generate periods confirm')
    const confirmBox = messageBoxExpression('生成会计期间')
    await invokeButton(cdp, '确定', 'confirm generate periods', confirmBox)
    await waitForPage(cdp, `
      (() => {
        const rows = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')];
        return document.body.innerText.includes('会计期间生成成功')
          || rows.length >= 12
          || document.body.innerText.includes('期间总数\\n12');
      })()
    `, 'periods generated').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after period generate'))}`)
    })

    await invokeButton(cdp, '检查', 'period close check button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const dialog = ${dialogExpression('月结检查')};
        return Boolean(dialog) && (dialog.innerText.includes('检查通过') || dialog.innerText.includes('检查未通过'));
      })()
    `, 'period close check dialog').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after period close check'))}`)
    })
    await clickButton(cdp, '关闭', dialogExpression('月结检查'))

    await invokeButton(cdp, '对账', 'period reconciliation button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const dialog = ${dialogExpression('库存财务对账')};
        return Boolean(dialog)
          && dialog.innerText.includes('库存净额')
          && dialog.innerText.includes('对账状态')
          && dialog.innerText.includes('刷新差异');
      })()
    `, 'period reconciliation dialog').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after period reconciliation'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'finance period workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'finance-period-generate-check-and-reconcile',
    passed: failures.length === 0,
    failures
  }
}

async function runReportExportWorkflow(cdp) {
  resetEvents(cdp.events)
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/reports' })
    await waitForPage(cdp, `document.body.innerText.includes('当前报表') && document.body.innerText.includes('导出')`, 'reports page')
    await evaluatePage(cdp, `
      (() => {
        window.__uiSmokeDownload = { blobs: [], clicks: [] };
        const rawCreateObjectURL = URL.createObjectURL.bind(URL);
        URL.createObjectURL = (blob) => {
          window.__uiSmokeDownload.blobs.push({ size: blob.size, type: blob.type });
          return rawCreateObjectURL(blob);
        };
        const rawClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function() {
          window.__uiSmokeDownload.clicks.push({
            download: this.download || '',
            href: String(this.href || '')
          });
          return rawClick.call(this);
        };
        return true;
      })()
    `)
    await clickButton(cdp, '导出')
    await waitForPage(cdp, `
      (() => {
        const download = window.__uiSmokeDownload || { blobs: [], clicks: [] };
        return document.body.innerText.includes('导出成功')
          && download.blobs.some((blob) => blob.size > 0)
          && download.clicks.some((click) => click.download.endsWith('.csv'));
      })()
    `, 'report export completed', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after report export'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'report export workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  const download = await evaluatePage(cdp, `window.__uiSmokeDownload || { blobs: [], clicks: [] }`)
  return {
    name: 'report-export-download',
    downloads: download,
    passed: failures.length === 0,
    failures
  }
}

async function runAttachmentWorkflow(cdp) {
  const businessNo = `ATT-SMOKE-${Date.now()}`
  const filename = `attachment-smoke-${Date.now()}.txt`
  resetEvents(cdp.events)
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/system/attachments' })
    await waitForPage(cdp, `document.body.innerText.includes('附件中心') && document.body.innerText.includes('上传')`, 'attachments page')

    await clickButton(cdp, '上传')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('上传附件')});
      })()
    `, 'attachment upload dialog')

    const uploadDialog = dialogExpression('上传附件')
    await setElementValue(cdp, `${uploadDialog}.querySelector('input[placeholder="如 SALES_ORDER"]')`, 'SALES_ORDER', 'attachment business type input')
    await setElementValue(cdp, `${uploadDialog}.querySelector('input[placeholder="请输入业务ID"]')`, '910001', 'attachment business id input')
    await setElementValue(cdp, `${uploadDialog}.querySelector('input[placeholder="请输入业务编号"]')`, businessNo, 'attachment business number input')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const dialog = ${uploadDialog};
        const input = dialog?.querySelector('input[type="file"]');
        if (!input) return false;
        const file = new File(['attachment smoke content'], ${JSON.stringify(filename)}, { type: 'text/plain' });
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
        input.dispatchEvent(new Event('change', { bubbles: true }));
        return input.files.length === 1;
      })()
    `, 'attachment file selected')

    await clickButton(cdp, '保存', uploadDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('上传附件')}
          && document.body.innerText.includes(${JSON.stringify(filename)})
          && document.body.innerText.includes(${JSON.stringify(businessNo)});
      })()
    `, 'attachment uploaded and listed', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after attachment upload'))}`)
    })

    await evaluatePage(cdp, `
      (() => {
        window.__uiSmokeAttachmentDownload = { blobs: [], clicks: [] };
        const rawCreateObjectURL = URL.createObjectURL.bind(URL);
        URL.createObjectURL = (blob) => {
          window.__uiSmokeAttachmentDownload.blobs.push({ size: blob.size, type: blob.type });
          return rawCreateObjectURL(blob);
        };
        const rawClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function() {
          window.__uiSmokeAttachmentDownload.clicks.push({
            download: this.download || '',
            href: String(this.href || '')
          });
          return rawClick.call(this);
        };
        return true;
      })()
    `)

    await clickElement(cdp, buttonExpression('下载'), 'attachment download button')
    await waitForPage(cdp, `
      (() => {
        const download = window.__uiSmokeAttachmentDownload || { blobs: [], clicks: [] };
        return document.body.innerText.includes('下载成功')
          && download.blobs.some((blob) => blob.size > 0 && blob.type.includes('text/plain'))
          && download.clicks.some((click) => click.download === ${JSON.stringify(filename)});
      })()
    `, 'attachment download completed', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after attachment download'))}`)
    })

    await clickElement(cdp, buttonExpression('删除'), 'attachment delete button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确定删除附件')});
      })()
    `, 'attachment delete confirmation')
    await clickButton(cdp, '确定', messageBoxExpression('确定删除附件'))
    await waitForPage(cdp, `!document.body.innerText.includes(${JSON.stringify(filename)})`, 'attachment deleted', 20000)
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'attachment workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  const download = await evaluatePage(cdp, `window.__uiSmokeAttachmentDownload || { blobs: [], clicks: [] }`)
  return {
    name: 'attachment-upload-download-delete',
    businessNo,
    filename,
    downloads: download,
    passed: failures.length === 0,
    failures
  }
}

async function runReadinessWorkflow(cdp) {
  resetEvents(cdp.events)
  const releaseCommit = `ui-smoke-readiness-${Date.now()}`
  const releaseVersion = `smoke-${Date.now()}`
  const remark = `UI smoke 预生产验收 ${Date.now()}`
  const evidenceSummary = `UI smoke 发布门禁证据 ${Date.now()}`
  const actualResult = `UI smoke 发布门禁通过 ${Date.now()}`
  const decisionComment = `UI smoke No-Go 决策 ${Date.now()}`
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/system/readiness' })
    await waitForPage(cdp, `
      document.body.innerText.includes('预生产验收')
        && document.body.innerText.includes('迁移前健康检查')
        && document.body.innerText.includes('验收运行单')
    `, 'readiness page')

    await clickButton(cdp, '新建运行单')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新建验收运行单')});
      })()
    `, 'readiness run dialog')

    const dialog = dialogExpression('新建验收运行单')
    await setElementValue(cdp, `${dialog}.querySelectorAll('.el-form-item')[0].querySelector('input')`, releaseCommit, 'readiness commit input')
    await setElementValue(cdp, `${dialog}.querySelectorAll('.el-form-item')[1].querySelector('input')`, releaseVersion, 'readiness version input')
    await setElementValue(cdp, `${dialog}.querySelectorAll('.el-form-item')[2].querySelector('input')`, 'LOCAL', 'readiness environment input')
    await setElementValue(cdp, `${dialog}.querySelectorAll('.el-form-item')[3].querySelector('input')`, 'erp_codex_runtime', 'readiness database input')
    await setElementValue(cdp, `${dialog}.querySelector('textarea')`, remark, 'readiness remark input')
    await clickButton(cdp, '保存', dialog)

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const drawer = document.querySelector('.el-drawer');
        const text = drawer?.innerText || '';
        return visible(drawer)
          && text.includes(${JSON.stringify(releaseCommit)})
          && text.includes('发布门禁')
          && text.includes('迁移前健康检查');
      })()
    `, 'readiness detail drawer created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after readiness run create'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const listResponse = await fetch('/api/system/readiness/runs?pageNo=1&pageSize=1&releaseCommit=' + encodeURIComponent(${JSON.stringify(releaseCommit)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!listResponse.ok) return false;
        const listPayload = await listResponse.json();
        const run = listPayload?.data?.records?.[0];
        if (!run?.id) return false;
        const detailResponse = await fetch('/api/system/readiness/runs/' + run.id, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const items = detailPayload?.data?.items || [];
        return items.some((item) => item.itemCode === 'MIGRATION_PREFLIGHT' && (item.evidence || []).length > 0)
          && items.some((item) => item.itemCode === 'RELEASE_GATE');
      })()
    `, 'readiness run persisted with preflight evidence', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after readiness persistence check'))}`)
    })

    const releaseGateRowExpression = `
      [...document.querySelectorAll('.el-drawer .el-table__body-wrapper tbody tr')]
        .find((row) => visible(row) && row.innerText.includes('RELEASE_GATE'))
    `
    await invokeElement(cdp, `[...document.querySelectorAll('.el-drawer button')].find((button) => visible(button) && button.innerText.trim() === '证据')`, 'readiness release gate evidence button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('登记验收证据')});
      })()
    `, 'readiness evidence dialog')
    const evidenceDialog = dialogExpression('登记验收证据')
    await setElementValue(cdp, `${evidenceDialog}.querySelectorAll('.el-form-item')[1].querySelector('input')`, evidenceSummary, 'readiness evidence summary input')
    await setElementValue(cdp, `${evidenceDialog}.querySelectorAll('.el-form-item')[2].querySelector('input')`, 'GET', 'readiness evidence method input')
    await setElementValue(cdp, `${evidenceDialog}.querySelectorAll('.el-form-item')[3].querySelector('input')`, '/actuator/health', 'readiness evidence uri input')
    await setElementValue(cdp, `${evidenceDialog}.querySelectorAll('.el-form-item')[4].querySelector('input')`, '200', 'readiness evidence status input')
    await setElementValue(cdp, `${evidenceDialog}.querySelector('textarea')`, 'UI smoke 手工证据登记', 'readiness evidence detail input')
    await clickButton(cdp, '保存', evidenceDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('登记验收证据')};
      })()
    `, 'readiness evidence saved', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after readiness evidence save'))}`)
    })

    await invokeElement(cdp, `[...document.querySelectorAll('.el-drawer button')].find((button) => visible(button) && button.innerText.trim() === '结果')`, 'readiness release gate result button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('记录验收结果')});
      })()
    `, 'readiness result dialog')
    const resultDialog = dialogExpression('记录验收结果')
    await setElementValue(cdp, `${resultDialog}.querySelector('textarea')`, actualResult, 'readiness actual result input')
    await clickButton(cdp, '保存', resultDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${releaseGateRowExpression};
        return !${dialogExpression('记录验收结果')} && row?.innerText.includes('通过');
      })()
    `, 'readiness result saved', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after readiness result save'))}`)
    })

    await clickElement(cdp, `document.querySelector('.el-drawer__close-btn')`, 'readiness detail drawer close')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !visible(document.querySelector('.el-drawer'));
      })()
    `, 'readiness drawer closed')

    const runRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((row) => visible(row) && row.innerText.includes(${JSON.stringify(releaseCommit)}))
    `
    await invokeElement(cdp, `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '决策')`, 'readiness decision button')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('发布决策')});
      })()
    `, 'readiness decision dialog')
    const decisionDialog = dialogExpression('发布决策')
    await clickElement(cdp, `${decisionDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'readiness decision select')
    await clickElement(cdp, `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes('No-Go'))`, 'readiness no-go option')
    await setElementValue(cdp, `${decisionDialog}.querySelector('textarea')`, decisionComment, 'readiness decision comment input')
    await clickButton(cdp, '保存', decisionDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${runRowExpression};
        return !${dialogExpression('发布决策')}
          && row?.innerText.includes('No-Go')
          && row?.innerText.includes('不发布');
      })()
    `, 'readiness no-go decision saved', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after readiness decision save'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const listResponse = await fetch('/api/system/readiness/runs?pageNo=1&pageSize=1&releaseCommit=' + encodeURIComponent(${JSON.stringify(releaseCommit)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!listResponse.ok) return false;
        const listPayload = await listResponse.json();
        const run = listPayload?.data?.records?.[0];
        if (!run?.id || run.status !== 'NO_GO' || run.decision !== 'NO_GO' || run.decisionComment !== ${JSON.stringify(decisionComment)}) return false;
        const detailResponse = await fetch('/api/system/readiness/runs/' + run.id, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const items = detailPayload?.data?.items || [];
        const releaseGate = items.find((item) => item.itemCode === 'RELEASE_GATE');
        return releaseGate?.status === 'PASSED'
          && releaseGate?.actualResult === ${JSON.stringify(actualResult)}
          && (releaseGate.evidence || []).some((evidence) => evidence.summary === ${JSON.stringify(evidenceSummary)});
      })()
    `, 'readiness result evidence and decision persisted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after readiness final persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'readiness workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'readiness-run-evidence-result-and-no-go',
    releaseCommit,
    releaseVersion,
    passed: failures.length === 0,
    failures
  }
}

async function runFundWorkflow(cdp) {
  resetEvents(cdp.events)
  const suffix = String(Date.now()).slice(-10)
  const accountCode = `FUNDUI${suffix}`
  const accountName = `UI资金账户${suffix}`
  const externalTxnNo = `EXT-FUND-${suffix}`
  const counterpartyName = `UI往来方${suffix}`
  const summary = `UI smoke 银行流水 ${suffix}`
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/funds' })
    await waitForPage(cdp, `
      document.body.innerText.includes('资金对账')
        && document.body.innerText.includes('新增账户')
        && document.body.innerText.includes('银行流水')
    `, 'fund page')

    await clickButton(cdp, '新增账户')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增资金账户')});
      })()
    `, 'fund account dialog')

    const accountDialog = dialogExpression('新增资金账户')
    await setElementValue(cdp, `${accountDialog}.querySelector('input[placeholder="如 ICBC001"]')`, accountCode, 'fund account code input')
    await setElementValue(cdp, `${accountDialog}.querySelector('input[placeholder="请输入账户名称"]')`, accountName, 'fund account name input')
    await setElementValue(cdp, `${accountDialog}.querySelector('input[placeholder="银行账户可填"]')`, 'UI测试银行', 'fund bank name input')
    await setElementValue(cdp, `${accountDialog}.querySelectorAll('input[placeholder="银行账户可填"]')[1]`, `6222${suffix}`, 'fund bank account input')
    await setElementValue(cdp, `${accountDialog}.querySelector('.el-input-number input')`, '0.00', 'fund opening balance input')
    await setElementValue(cdp, `${accountDialog}.querySelector('textarea')`, 'UI smoke 资金账户', 'fund account remark input')
    await clickButton(cdp, '保存', accountDialog)

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('新增资金账户')}
          && document.body.innerText.includes(${JSON.stringify(accountCode)})
          && document.body.innerText.includes(${JSON.stringify(accountName)});
      })()
    `, 'fund account created', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after fund account create'))}`)
    })

    await clickElement(cdp, `[...document.querySelectorAll('.el-tabs__item')].find((item) => visible(item) && item.innerText.includes('银行流水'))`, 'bank statement tab')
    await waitForPage(cdp, `document.body.innerText.includes('新增流水')`, 'bank statement tab visible')
    await clickButton(cdp, '新增流水')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增银行流水')});
      })()
    `, 'bank statement dialog')

    const statementDialog = dialogExpression('新增银行流水')
    await clickElement(cdp, `${statementDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'fund account select')
    await clickElement(cdp, `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(accountCode)}))`, 'created fund account option')
    await setElementValue(cdp, `${statementDialog}.querySelectorAll('.el-form-item')[1].querySelector('input')`, externalTxnNo, 'bank statement external txn input')
    await setElementValue(cdp, `${statementDialog}.querySelectorAll('.el-form-item')[4].querySelector('input')`, '128.50', 'bank statement amount input')
    await setElementValue(cdp, `${statementDialog}.querySelectorAll('.el-form-item')[5].querySelector('input')`, counterpartyName, 'bank statement counterparty input')
    await setElementValue(cdp, `${statementDialog}.querySelectorAll('.el-form-item')[6].querySelector('input')`, summary, 'bank statement summary input')
    await setElementValue(cdp, `${statementDialog}.querySelector('textarea')`, 'UI smoke 银行流水', 'bank statement remark input')
    await clickButton(cdp, '保存', statementDialog)

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('新增银行流水')}
          && document.body.innerText.includes(${JSON.stringify(accountCode)})
          && document.body.innerText.includes(${JSON.stringify(summary)})
          && document.body.innerText.includes('未匹配');
      })()
    `, 'bank statement created', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after bank statement create'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const accountResponse = await fetch('/api/finance/fund/accounts?pageNo=1&pageSize=1&keyword=' + encodeURIComponent(${JSON.stringify(accountCode)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!accountResponse.ok) return false;
        const accountPayload = await accountResponse.json();
        const account = accountPayload?.data?.records?.[0];
        if (!account?.id || account.accountCode !== ${JSON.stringify(accountCode)}) return false;
        const statementResponse = await fetch('/api/finance/fund/statements?pageNo=1&pageSize=10&fundAccountId=' + account.id + '&status=UNMATCHED', {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!statementResponse.ok) return false;
        const statementPayload = await statementResponse.json();
        const statements = statementPayload?.data?.records || [];
        return statements.some((statement) =>
          statement.externalTxnNo === ${JSON.stringify(externalTxnNo)}
            && statement.summary === ${JSON.stringify(summary)}
            && statement.status === 'UNMATCHED'
            && Number(statement.amount) === 128.5
        );
      })()
    `, 'fund account and statement persisted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after fund persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'fund workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'fund-account-and-statement-create',
    accountCode,
    externalTxnNo,
    passed: failures.length === 0,
    failures
  }
}

async function runFundMatchUnmatchWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await preparePostedSalesDeliveryFixture(auth)
  const suffix = `${fixture.suffix}${String(Date.now()).slice(-4)}`
  const amount = Number(fixture.receivable.remainingAmount ?? fixture.receivable.receivableAmount ?? 0)
  const accountCode = `FUNDMT${suffix}`
  const accountName = `UI匹配账户${suffix}`
  const externalTxnNo = `EXT-MATCH-${suffix}`
  const summary = `UI smoke 匹配流水 ${suffix}`
  const unmatchReason = `UI smoke 取消匹配 ${suffix}`
  const receiptRemark = `UI smoke 对账收款 ${suffix}`
  let account = null
  let statement = null
  let receipt = null

  if (!(amount > 0)) {
    throw new Error(`receivable ${fixture.receivable.receivableNo} has invalid remaining amount: ${amount}`)
  }

  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'fund-match:prepare-fixture')
    receipt = await apiRequest(auth, 'POST', '/finance/receipts', {
      customerId: fixture.customer.id,
      receiptDate: fixture.orderDate,
      amount,
      remark: receiptRemark,
      allocations: [
        {
          receivableId: fixture.receivable.id,
          amount
        }
      ]
    })
    if (receipt.status !== 'POSTED') {
      throw new Error(`created receipt ${receipt.receiptNo || receipt.id} is not POSTED: ${receipt.status}`)
    }

    account = await apiRequest(auth, 'POST', '/finance/fund/accounts', {
      accountCode,
      accountName,
      accountType: 'BANK',
      bankName: 'UI测试银行',
      bankAccountNo: `6222${suffix}`,
      currencyCode: 'CNY',
      openingBalance: 0,
      remark: 'UI smoke 对账匹配账户'
    })
    statement = await apiRequest(auth, 'POST', '/finance/fund/statements', {
      fundAccountId: account.id,
      externalTxnNo,
      transactionDate: fixture.orderDate,
      direction: 'IN',
      amount,
      counterpartyName: fixture.customer.customerName,
      summary,
      remark: 'UI smoke 对账匹配流水'
    })
    if (statement.status !== 'UNMATCHED') {
      throw new Error(`created statement ${statement.statementNo || statement.id} is not UNMATCHED: ${statement.status}`)
    }

    await markSmokeStep(cdp, 'fund-match:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/funds' })
    await waitForPage(cdp, `
      document.body.innerText.includes('资金对账')
        && document.body.innerText.includes('银行流水')
    `, 'fund page for match')
    await clickElement(cdp, `[...document.querySelectorAll('.el-tabs__item')].find((item) => visible(item) && item.innerText.includes('银行流水'))`, 'bank statement tab for match')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(summary)}));
        return row?.innerText.includes('未匹配') && row?.innerText.includes(${JSON.stringify(accountCode)});
      })()
    `, 'unmatched bank statement row visible', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after fund match row load'))}`)
    })

    await markSmokeStep(cdp, 'fund-match:open-match-dialog')
    await invokeElement(
      cdp,
      `(() => {
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(summary)}));
        return [...(row?.querySelectorAll('button') || [])]
          .find((button) => visible(button) && button.innerText.trim() === '匹配');
      })()`,
      'bank statement match button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('匹配业务单据')});
      })()
    `, 'bank statement match dialog')
    const matchDialog = dialogExpression('匹配业务单据')
    await waitForPage(cdp, visibleTextExpression(matchDialog, '收款单'), 'receipt match type selected')
    await setElementValue(cdp, `${matchDialog}.querySelector('input[placeholder="请输入已过账收/付款单 ID"]')`, String(receipt.id), 'match receipt id input')
    await setElementValue(cdp, `${matchDialog}.querySelector('textarea')`, 'UI smoke 银行流水匹配收款单', 'match remark input')
    await clickButton(cdp, '保存', matchDialog)

    await markSmokeStep(cdp, 'fund-match:wait-matched')
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/fund/statements/' + ${JSON.stringify(String(statement.id))}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = payload?.data;
        return record?.status === 'MATCHED'
          && record.matchedBizType === 'RECEIPT'
          && String(record.matchedBizId) === ${JSON.stringify(String(receipt.id))}
          && record.matchedBizNo === ${JSON.stringify(receipt.receiptNo)};
      })()
    `, 'bank statement matched persisted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after fund match persistence check'))}`)
    })

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(summary)}));
        return row?.innerText.includes('已匹配') && row?.innerText.includes(${JSON.stringify(receipt.receiptNo)});
      })()
    `, 'matched bank statement row visible', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after fund matched row check'))}`)
    })

    await markSmokeStep(cdp, 'fund-match:unmatch')
    await invokeElement(
      cdp,
      `(() => {
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(summary)}));
        return [...(row?.querySelectorAll('button') || [])]
          .find((button) => visible(button) && button.innerText.trim() === '取消匹配');
      })()`,
      'bank statement unmatch button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('取消匹配')});
      })()
    `, 'bank statement unmatch prompt')
    const unmatchBox = messageBoxExpression('取消匹配')
    await setElementValue(cdp, `${unmatchBox}.querySelector('input')`, unmatchReason, 'unmatch reason input')
    await clickButton(cdp, '确定', unmatchBox)

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/fund/statements/' + ${JSON.stringify(String(statement.id))}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = payload?.data;
        return record?.status === 'UNMATCHED'
          && !record.matchedBizType
          && !record.matchedBizId
          && !record.matchedBizNo
          && record.unmatchReason === ${JSON.stringify(unmatchReason)};
      })()
    `, 'bank statement unmatched persisted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after fund unmatch persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'fund match workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'fund-statement-match-unmatch-receipt',
    accountCode,
    externalTxnNo,
    statementNo: statement?.statementNo || null,
    receiptNo: receipt?.receiptNo || null,
    passed: failures.length === 0,
    failures
  }
}

async function runExpenseWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const suffix = String(Date.now()).slice(-10)
  const expenseDate = new Date().toISOString().slice(0, 10)
  const amount = 96.8
  const remark = `UI smoke 费用 ${suffix}`
  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  let expense = null

  try {
    await ensureOpenPeriod(auth, expenseDate)
    const expenseSubject = await apiRequest(auth, 'POST', '/finance/account-subjects', {
      subjectCode: `66${suffix.slice(-6)}`,
      subjectName: `UI费用科目${suffix}`,
      subjectType: 'EXPENSE',
      balanceDirection: 'DEBIT',
      remark: 'UI smoke 费用科目'
    })
    const paymentSubject = await apiRequest(auth, 'POST', '/finance/account-subjects', {
      subjectCode: `10${suffix.slice(-6)}`,
      subjectName: `UI支付科目${suffix}`,
      subjectType: 'ASSET',
      balanceDirection: 'DEBIT',
      remark: 'UI smoke 支付科目'
    })

    await setBrowserAuth(cdp, submitterAuth)
    await markSmokeStep(cdp, 'expense:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/expenses' })
    await waitForPage(cdp, `
      document.body.innerText.includes('费用管理')
        && document.body.innerText.includes('新增费用')
    `, 'expense page')

    await markSmokeStep(cdp, 'expense:open-create-dialog')
    await clickButton(cdp, '新增费用')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增费用')});
      })()
    `, 'expense create dialog')

    const createDialog = dialogExpression('新增费用')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'expense subject select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(expenseSubject.subjectCode)}))`,
      'expense subject option'
    )
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'expense payment subject select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(paymentSubject.subjectCode)}))`,
      'expense payment subject option'
    )
    await setElementValue(cdp, `${createDialog}.querySelector('.el-input-number input')`, String(amount), 'expense amount input')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'expense remark input')
    await clickButton(cdp, '保存', createDialog)

    await markSmokeStep(cdp, 'expense:wait-created')
    expense = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/finance/expenses?pageNo=1&pageSize=20&status=DRAFT', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        if (String(record.subjectId) !== ${JSON.stringify(String(expenseSubject.id))}) return false;
        if (String(record.paymentSubjectId) !== ${JSON.stringify(String(paymentSubject.id))}) return false;
        if (Number(record.amount) !== ${amount}) return false;
        return record;
      })()
    `, 'expense created as draft', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after expense create'))}`)
    })

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(expense.expenseNo)}));
        return row?.innerText.includes('草稿') && row?.innerText.includes(${JSON.stringify(remark)});
      })()
    `, 'expense draft row visible')

    const rowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(expense.expenseNo)}))
    `
    const clickRowButton = async (text, label) => {
      await invokeElement(
        cdp,
        `(() => {
          const row = ${rowExpression};
          return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === ${JSON.stringify(text)});
        })()`,
        label
      )
    }
    const confirmPrompt = async (title) => {
      await waitForPage(cdp, `
        (() => {
          const visible = (element) => {
            if (!element) return false;
            const style = window.getComputedStyle(element);
            const rect = element.getBoundingClientRect();
            return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
          };
          return Boolean(${messageBoxExpression(title)});
        })()
      `, `${title} confirm`)
      await clickButton(cdp, '确定', messageBoxExpression(title))
    }
    const waitExpenseStatus = async (status, label, extraPredicate = 'true') => {
      await waitForPage(cdp, `
        (async () => {
          const token = localStorage.getItem('token');
          const response = await fetch('/api/finance/expenses/' + ${JSON.stringify(String(expense.id))}, {
            headers: token ? { Authorization: 'Bearer ' + token } : {}
          });
          if (!response.ok) return false;
          const payload = await response.json();
          const record = payload?.data;
          return record?.status === ${JSON.stringify(status)} && (${extraPredicate}) ? record : false;
        })()
      `, label, 30000).catch(async (error) => {
        throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, label))}`)
      })
    }

    await markSmokeStep(cdp, 'expense:submit')
    await clickRowButton('提交', 'expense submit button')
    await confirmPrompt('提示')
    await waitExpenseStatus('PENDING', 'expense submitted as pending')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${rowExpression};
        return row?.innerText.includes('待审批');
      })()
    `, 'expense pending row visible')

    await markSmokeStep(cdp, 'expense:approve')
    await setBrowserAuth(cdp, auth)
    await clickRowButton('审批', 'expense approve button')
    await confirmPrompt('提示')
    await waitExpenseStatus('APPROVED', 'expense approved')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${rowExpression};
        return row?.innerText.includes('已批准');
      })()
    `, 'expense approved row visible')

    await markSmokeStep(cdp, 'expense:post')
    await clickRowButton('过账', 'expense post button')
    await confirmPrompt('提示')
    await waitExpenseStatus(
      'POSTED',
      'expense posted with balanced voucher',
      `record.voucherNo && record.voucherStatus === 'POSTED' && Number(record.voucherEntryCount) === 2 && record.voucherBalanced === true && record.amountMatched === true`
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${rowExpression};
        return row?.innerText.includes('已过账') && row?.innerText.includes('VO-EXPENSE-') && row?.innerText.includes('平衡');
      })()
    `, 'expense posted row visible')

    await markSmokeStep(cdp, 'expense:reverse')
    await clickRowButton('红冲', 'expense reverse button')
    await confirmPrompt('提示')
    await waitExpenseStatus(
      'POSTED',
      'expense reversed with balanced reversal voucher',
      `record.reversed === true && record.reversalVoucherNo && record.reversalVoucherStatus === 'POSTED' && Number(record.reversalVoucherEntryCount) === 2 && record.reversalVoucherBalanced === true && record.reversalAmountMatched === true`
    )
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'expense workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'finance-expense-create-submit-approve-post-reverse',
    expenseNo: expense?.expenseNo || null,
    passed: failures.length === 0,
    failures
  }
}

async function runFinanceSubjectVoucherWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const suffix = String(Date.now()).slice(-10)
  const subjectCode = `21${suffix.slice(-6)}`
  const subjectName = `UI负债科目${suffix}`
  const updatedSubjectName = `UI负债科目已改${suffix}`
  const subjectRemark = `UI smoke 科目 ${suffix}`
  const updatedSubjectRemark = `UI smoke 科目已改 ${suffix}`
  const expenseDate = new Date().toISOString().slice(0, 10)
  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  let subject = null
  let voucherNo = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `

  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'finance-subject:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/subjects' })
    await waitForPage(cdp, `
      document.body.innerText.includes('会计科目管理')
        && document.body.innerText.includes('新增科目')
    `, 'finance subject page')

    await markSmokeStep(cdp, 'finance-subject:create')
    await clickButton(cdp, '新增科目')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('新增科目')}); })()`, 'subject create dialog')
    const createDialog = dialogExpression('新增科目')
    await setElementValue(
      cdp,
      `[...${createDialog}.querySelectorAll('.el-form-item')].find((item) => item.innerText.includes('科目编码')).querySelector('input')`,
      subjectCode,
      'subject code input'
    )
    await setElementValue(
      cdp,
      `[...${createDialog}.querySelectorAll('.el-form-item')].find((item) => item.innerText.includes('科目名称')).querySelector('input')`,
      subjectName,
      'subject name input'
    )
    await clickElement(
      cdp,
      `[...${createDialog}.querySelectorAll('.el-form-item')].find((item) => item.innerText.includes('科目类别')).querySelector('.el-select__wrapper')`,
      'subject type select'
    )
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes('负债'))`,
      'liability subject type option'
    )
    await clickElement(
      cdp,
      `[...${createDialog}.querySelectorAll('.el-radio')].find((item) => visible(item) && item.innerText.includes('贷方'))`,
      'credit balance direction radio'
    )
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, subjectRemark, 'subject remark input')
    await clickButton(cdp, '确定', createDialog)

    subject = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/account-subjects?pageNo=1&pageSize=20&subjectCode=' + encodeURIComponent(${JSON.stringify(subjectCode)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = payload?.data?.records?.find((item) => item.subjectCode === ${JSON.stringify(subjectCode)});
        if (!record) return false;
        if (record.subjectName !== ${JSON.stringify(subjectName)}) return false;
        if (record.subjectType !== 'LIABILITY') return false;
        if (record.balanceDirection !== 'CREDIT') return false;
        if (record.status !== 'ACTIVE') return false;
        if (record.remark !== ${JSON.stringify(subjectRemark)}) return false;
        return record;
      })()
    `, 'subject created through page', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after subject create'))}`)
    })

    const subjectRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(subjectCode)}))
    `
    const subjectRowButton = (text) => `(() => {
      const row = ${subjectRowExpression};
      return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim().includes(${JSON.stringify(text)}));
    })()`

    await waitForPage(cdp, `
      (() => {
        ${visibleDefinition}
        const row = ${subjectRowExpression};
        return row?.innerText.includes(${JSON.stringify(subjectName)}) && row?.innerText.includes('启用');
      })()
    `, 'subject row visible')

    await markSmokeStep(cdp, 'finance-subject:edit')
    await invokeElement(cdp, subjectRowButton('编辑'), 'subject edit button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('编辑科目')}); })()`, 'subject edit dialog')
    const editDialog = dialogExpression('编辑科目')
    await setElementValue(
      cdp,
      `[...${editDialog}.querySelectorAll('.el-form-item')].find((item) => item.innerText.includes('科目名称')).querySelector('input')`,
      updatedSubjectName,
      'updated subject name input'
    )
    await setElementValue(cdp, `${editDialog}.querySelector('textarea')`, updatedSubjectRemark, 'updated subject remark input')
    await clickButton(cdp, '确定', editDialog)
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/account-subjects/' + ${JSON.stringify(String(subject.id))}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = payload?.data;
        return record?.subjectName === ${JSON.stringify(updatedSubjectName)}
          && record?.remark === ${JSON.stringify(updatedSubjectRemark)}
          && record?.subjectCode === ${JSON.stringify(subjectCode)}
          && record?.subjectType === 'LIABILITY'
          && record?.balanceDirection === 'CREDIT';
      })()
    `, 'subject edited through page', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after subject edit'))}`)
    })

    await markSmokeStep(cdp, 'finance-subject:disable')
    await invokeElement(cdp, subjectRowButton('停用'), 'subject disable button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('提示')}); })()`, 'subject disable prompt')
    await clickButton(cdp, '确定', messageBoxExpression('提示'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/account-subjects/' + ${JSON.stringify(String(subject.id))}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        return payload?.data?.status === 'DISABLED';
      })()
    `, 'subject disabled through page', 30000)

    await markSmokeStep(cdp, 'finance-subject:enable')
    await waitForPage(cdp, `
      (() => {
        ${visibleDefinition}
        const row = ${subjectRowExpression};
        return row?.innerText.includes('停用');
      })()
    `, 'subject disabled row visible')
    await invokeElement(cdp, subjectRowButton('启用'), 'subject enable button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('提示')}); })()`, 'subject enable prompt')
    await clickButton(cdp, '确定', messageBoxExpression('提示'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/account-subjects/' + ${JSON.stringify(String(subject.id))}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        return payload?.data?.status === 'ACTIVE';
      })()
    `, 'subject enabled through page', 30000)

    await markSmokeStep(cdp, 'finance-voucher:prepare-expense-voucher')
    await ensureOpenPeriod(auth, expenseDate)
    const expenseSubject = await apiRequest(auth, 'POST', '/finance/account-subjects', {
      subjectCode: `67${suffix.slice(-6)}`,
      subjectName: `UI凭证费用科目${suffix}`,
      subjectType: 'EXPENSE',
      balanceDirection: 'DEBIT',
      remark: 'UI smoke 凭证费用科目'
    })
    const paymentSubject = await apiRequest(auth, 'POST', '/finance/account-subjects', {
      subjectCode: `11${suffix.slice(-6)}`,
      subjectName: `UI凭证支付科目${suffix}`,
      subjectType: 'ASSET',
      balanceDirection: 'DEBIT',
      remark: 'UI smoke 凭证支付科目'
    })
    const expense = await apiRequest(submitterAuth, 'POST', '/finance/expenses', {
      expenseDate,
      subjectId: expenseSubject.id,
      paymentSubjectId: paymentSubject.id,
      amount: 88.88,
      remark: `UI smoke 凭证查询 ${suffix}`
    })
    await apiRequest(submitterAuth, 'POST', `/finance/expenses/${expense.id}/submit`, { remark: 'UI smoke 提交费用' })
    await apiRequest(auth, 'POST', `/finance/expenses/${expense.id}/approve`, { remark: 'UI smoke 审批费用' })
    const postedExpense = await apiRequest(auth, 'POST', `/finance/expenses/${expense.id}/post`)
    voucherNo = postedExpense.voucherNo
    if (!voucherNo) {
      throw new Error(`posted expense did not return voucherNo: ${JSON.stringify(postedExpense)}`)
    }

    await markSmokeStep(cdp, 'finance-voucher:navigate')
    await setBrowserAuth(cdp, auth)
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/vouchers' })
    await waitForPage(cdp, `
      document.body.innerText.includes('凭证查询')
        && document.body.innerText.includes(${JSON.stringify(voucherNo)})
    `, 'voucher query page with expense voucher', 30000)
    await waitForPage(cdp, `
      (() => {
        ${visibleDefinition}
        const blocked = ['新增凭证', '审批', '过账', '作废'];
        return ![...document.querySelectorAll('button')]
          .filter(visible)
          .some((button) => blocked.includes(button.innerText.trim()));
      })()
    `, 'voucher page has no unsupported write buttons')
    await invokeElement(
      cdp,
      `(() => {
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(voucherNo)}));
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '查看');
      })()`,
      'voucher detail button'
    )
    await waitForPage(cdp, `
      (() => {
        ${visibleDefinition}
        const dialog = ${dialogExpression('凭证详情')};
        if (!dialog) return false;
        const text = dialog.innerText;
        return text.includes(${JSON.stringify(voucherNo)})
          && text.includes(${JSON.stringify(expenseSubject.subjectCode)})
          && text.includes(${JSON.stringify(paymentSubject.subjectCode)})
          && text.includes('88.88');
      })()
    `, 'voucher detail with balanced entries', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after voucher detail'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'finance subject voucher workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'finance-subject-save-toggle-and-voucher-readonly-detail',
    subjectCode,
    voucherNo,
    passed: failures.length === 0,
    failures
  }
}

async function runInventoryAdjustmentWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareInventoryAdjustmentFixture(auth)
  const remark = `UI smoke 库存调整 ${fixture.suffix}`
  let adjustment = null
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/inventory/adjustments' })
    await waitForPage(cdp, `
      document.body.innerText.includes('新增调整')
        && document.body.innerText.includes('调整单号')
    `, 'inventory adjustment page')

    await clickButton(cdp, '新增调整')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增库存调整')});
      })()
    `, 'inventory adjustment create dialog')

    const createDialog = dialogExpression('新增库存调整')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'adjustment warehouse select')
    await invokeElement(
      cdp,
      `(() => {
        const isVisible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-select-dropdown__item')]
          .find((item) => isVisible(item) && item.innerText.includes(${JSON.stringify(fixture.warehouse.warehouseName)}));
      })()`,
      'adjustment warehouse option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.warehouse.warehouseName), 'adjustment warehouse selected')
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="请选择调整日期"]')`, fixture.adjustmentDate, 'adjustment date input')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'adjustment remark input')
    await clickButton(cdp, '添加产品', createDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return ${createDialog}?.querySelectorAll('.el-table__body-wrapper tbody tr').length > 0;
      })()
    `, 'adjustment line added')
    await clickElement(cdp, `${createDialog}.querySelector('.el-table__body-wrapper .el-select .el-select__wrapper')`, 'adjustment product select')
    await invokeElement(
      cdp,
      `(() => {
        const isVisible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-select-dropdown__item')]
          .find((item) => isVisible(item) && item.innerText.includes(${JSON.stringify(fixture.product.productCode)}));
      })()`,
      'adjustment product option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.product.productCode), 'adjustment product selected')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-table__body-wrapper .el-input-number input')`, String(fixture.qty), 'adjustment qty input')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-table__body-wrapper input[placeholder="请输入原因"]')`, 'UI smoke 盘盈入库', 'adjustment reason input')
    await clickButton(cdp, '确定', createDialog)

    adjustment = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/inventory/adjustments?pageNo=1&pageSize=20', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        const detailResponse = await fetch('/api/inventory/adjustments/' + encodeURIComponent(record.id), { headers });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const detail = detailPayload?.data;
        const line = detail?.lines?.[0];
        if (!line || String(line.productId) !== ${JSON.stringify(String(fixture.product.id))}) return false;
        if (line.direction !== 'IN' || Number(line.qty) !== ${fixture.qty}) return false;
        return detail;
      })()
    `, 'inventory adjustment created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory adjustment create'))}`)
    })

    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入调整单号"]')`, adjustment.adjustmentNo, 'adjustment no search input')
    await clickButton(cdp, '查询')
    const adjustmentRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(adjustment.adjustmentNo)}))
    `
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${adjustmentRowExpression};
        return row?.innerText.includes('草稿') && row?.innerText.includes(${JSON.stringify(remark)});
      })()
    `, 'inventory adjustment draft row visible')
    await evaluatePage(cdp, `
      (() => {
        window.__uiSmoke = {
          ...(window.__uiSmoke || {}),
          adjustmentPostRequests: []
        };
        if (!window.__uiSmokeAdjustmentPostPatched) {
          window.__uiSmokeAdjustmentPostPatched = true;
          const rawFetch = window.fetch;
          window.fetch = async (...args) => {
            const url = String(args[0]);
            const method = String(args[1]?.method || 'GET').toUpperCase();
            const shouldRecord = url.includes('/api/inventory/adjustments/') && url.includes('/post') && method === 'POST';
            const record = shouldRecord ? { type: 'fetch', url, status: null, responseText: '', done: false } : null;
            if (record) window.__uiSmoke.adjustmentPostRequests.push(record);
            const response = await rawFetch(...args);
            if (record) {
              record.status = response.status;
              record.responseText = await response.clone().text();
              record.done = true;
            }
            return response;
          };
          const rawOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function(method, url, ...rest) {
            const normalizedMethod = String(method || 'GET').toUpperCase();
            if (String(url).includes('/api/inventory/adjustments/') && String(url).includes('/post') && normalizedMethod === 'POST') {
              this.__uiSmokeAdjustmentPostRecord = { type: 'xhr', method: normalizedMethod, url: String(url), status: null, responseText: '', done: false };
              window.__uiSmoke.adjustmentPostRequests.push(this.__uiSmokeAdjustmentPostRecord);
              this.addEventListener('loadend', () => {
                this.__uiSmokeAdjustmentPostRecord.status = this.status;
                this.__uiSmokeAdjustmentPostRecord.responseText = String(this.responseText || '').slice(0, 3000);
                this.__uiSmokeAdjustmentPostRecord.done = true;
              });
            }
            return rawOpen.call(this, method, url, ...rest);
          };
        }
        return true;
      })()
    `)
    await invokeElement(
      cdp,
      `(() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${adjustmentRowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '过账');
      })()`,
      'inventory adjustment post button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认过账此库存调整吗')});
      })()
    `, 'inventory adjustment post confirm')
    await clickButton(cdp, '确定', messageBoxExpression('确认过账此库存调整吗'))
    await waitForPage(cdp, `
      (() => (window.__uiSmoke?.adjustmentPostRequests || []).some((request) => request.done))()
    `, 'inventory adjustment post api request', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory adjustment post confirm'))}`)
    })
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${adjustmentRowExpression};
        return row?.innerText.includes('已完成');
      })()
    `, 'inventory adjustment posted row visible', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory adjustment post'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const detailResponse = await fetch('/api/inventory/adjustments/' + ${JSON.stringify(adjustment.id)}, { headers });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const detail = detailPayload?.data;
        const balanceResponse = await fetch('/api/inventory/balances?pageNo=1&pageSize=20&warehouseId=' + encodeURIComponent(${JSON.stringify(String(fixture.warehouse.id))}) + '&productId=' + encodeURIComponent(${JSON.stringify(String(fixture.product.id))}), { headers });
        if (!balanceResponse.ok) return false;
        const balancePayload = await balanceResponse.json();
        const balance = (balancePayload?.data?.records || []).find((item) => String(item.warehouseId) === ${JSON.stringify(String(fixture.warehouse.id))} && String(item.productId) === ${JSON.stringify(String(fixture.product.id))});
        return detail?.status === 'POSTED'
          && Number(detail?.lines?.[0]?.qty) === ${fixture.qty}
          && Number(balance?.qtyOnHand) === ${fixture.qty}
          && Number(balance?.qtyAvailable) === ${fixture.qty}
          && Number(balance?.qtyReserved) === 0;
      })()
    `, 'inventory adjustment persisted and balance updated', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory adjustment persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'inventory adjustment workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'inventory-adjustment-create-post-balance',
    adjustmentNo: adjustment?.adjustmentNo || null,
    warehouseCode: fixture.warehouse.warehouseCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function runInventoryTransferWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareInventoryTransferFixture(auth)
  const remark = `UI smoke 库存调拨 ${fixture.suffix}`
  let transfer = null
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/inventory/transfers' })
    await waitForPage(cdp, `
      document.body.innerText.includes('新增调拨')
        && document.body.innerText.includes('调拨单号')
    `, 'inventory transfer page')

    await clickButton(cdp, '新增调拨')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增库存调拨')});
      })()
    `, 'inventory transfer create dialog')

    const createDialog = dialogExpression('新增库存调拨')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'transfer from warehouse select')
    await invokeElement(
      cdp,
      `(() => {
        const isVisible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-select-dropdown__item')]
          .find((item) => isVisible(item) && item.innerText.includes(${JSON.stringify(fixture.fromWarehouse.warehouseName)}));
      })()`,
      'transfer from warehouse option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.fromWarehouse.warehouseName), 'transfer from warehouse selected')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'transfer to warehouse select')
    await invokeElement(
      cdp,
      `(() => {
        const isVisible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-select-dropdown__item')]
          .find((item) => isVisible(item) && item.innerText.includes(${JSON.stringify(fixture.toWarehouse.warehouseName)}));
      })()`,
      'transfer to warehouse option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.toWarehouse.warehouseName), 'transfer to warehouse selected')
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="请选择调拨日期"]')`, fixture.transferDate, 'transfer date input')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'transfer remark input')
    await clickButton(cdp, '添加产品', createDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return ${createDialog}?.querySelectorAll('.el-table__body-wrapper tbody tr').length > 0;
      })()
    `, 'transfer line added')
    await clickElement(cdp, `${createDialog}.querySelector('.el-table__body-wrapper .el-select .el-select__wrapper')`, 'transfer product select')
    await invokeElement(
      cdp,
      `(() => {
        const isVisible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-select-dropdown__item')]
          .find((item) => isVisible(item) && item.innerText.includes(${JSON.stringify(fixture.product.productCode)}));
      })()`,
      'transfer product option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.product.productCode), 'transfer product selected')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-table__body-wrapper .el-input-number input')`, String(fixture.transferQty), 'transfer qty input')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-table__body-wrapper input[placeholder="请输入备注"]')`, 'UI smoke 调拨明细', 'transfer line remark input')
    await clickButton(cdp, '确定', createDialog)

    transfer = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/inventory/transfers?pageNo=1&pageSize=20', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        const detailResponse = await fetch('/api/inventory/transfers/' + encodeURIComponent(record.id), { headers });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const detail = detailPayload?.data;
        const line = detail?.lines?.[0];
        if (!line || String(line.productId) !== ${JSON.stringify(String(fixture.product.id))}) return false;
        if (Number(line.qty) !== ${fixture.transferQty} || Number(line.unitCost) !== 9.5) return false;
        return detail;
      })()
    `, 'inventory transfer created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory transfer create'))}`)
    })

    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入调拨单号"]')`, transfer.transferNo, 'transfer no search input')
    await clickButton(cdp, '查询')
    const transferRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(transfer.transferNo)}))
    `
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${transferRowExpression};
        return row?.innerText.includes('草稿') && row?.innerText.includes(${JSON.stringify(remark)});
      })()
    `, 'inventory transfer draft row visible')
    await invokeElement(
      cdp,
      `(() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${transferRowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '过账');
      })()`,
      'inventory transfer post button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认过账此库存调拨吗')});
      })()
    `, 'inventory transfer post confirm')
    await clickButton(cdp, '确定', messageBoxExpression('确认过账此库存调拨吗'))
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${transferRowExpression};
        return row?.innerText.includes('已完成');
      })()
    `, 'inventory transfer posted row visible', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory transfer post'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const detailResponse = await fetch('/api/inventory/transfers/' + ${JSON.stringify(transfer.id)}, { headers });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const detail = detailPayload?.data;
        const findBalance = async (warehouseId) => {
          const response = await fetch('/api/inventory/balances?pageNo=1&pageSize=20&warehouseId=' + encodeURIComponent(warehouseId) + '&productId=' + encodeURIComponent(${JSON.stringify(String(fixture.product.id))}), { headers });
          if (!response.ok) return null;
          const payload = await response.json();
          return (payload?.data?.records || []).find((item) => String(item.warehouseId) === String(warehouseId) && String(item.productId) === ${JSON.stringify(String(fixture.product.id))});
        };
        const fromBalance = await findBalance(${JSON.stringify(String(fixture.fromWarehouse.id))});
        const toBalance = await findBalance(${JSON.stringify(String(fixture.toWarehouse.id))});
        return detail?.status === 'POSTED'
          && Number(detail?.lines?.[0]?.qty) === ${fixture.transferQty}
          && Number(fromBalance?.qtyOnHand) === ${fixture.initialQty - fixture.transferQty}
          && Number(fromBalance?.qtyAvailable) === ${fixture.initialQty - fixture.transferQty}
          && Number(fromBalance?.qtyReserved) === 0
          && Number(toBalance?.qtyOnHand) === ${fixture.transferQty}
          && Number(toBalance?.qtyAvailable) === ${fixture.transferQty}
          && Number(toBalance?.qtyReserved) === 0;
      })()
    `, 'inventory transfer persisted and balances updated', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory transfer persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'inventory transfer workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'inventory-transfer-create-post-balance',
    transferNo: transfer?.transferNo || null,
    fromWarehouseCode: fixture.fromWarehouse.warehouseCode,
    toWarehouseCode: fixture.toWarehouse.warehouseCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function runInventoryCheckWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareInventoryCheckFixture(auth)
  const remark = `UI smoke 库存盘点 ${fixture.suffix}`
  let check = null
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/inventory/checks' })
    await waitForPage(cdp, `
      document.body.innerText.includes('新增盘点')
        && document.body.innerText.includes('盘点单号')
    `, 'inventory check page')

    await clickButton(cdp, '新增盘点')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增库存盘点')});
      })()
    `, 'inventory check create dialog')

    const createDialog = dialogExpression('新增库存盘点')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'check warehouse select')
    await invokeElement(
      cdp,
      `(() => {
        const isVisible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-select-dropdown__item')]
          .find((item) => isVisible(item) && item.innerText.includes(${JSON.stringify(fixture.warehouse.warehouseName)}));
      })()`,
      'check warehouse option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.warehouse.warehouseName), 'check warehouse selected')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const dialog = ${createDialog};
        return dialog?.innerText.includes(${JSON.stringify(fixture.product.productCode)})
          && dialog?.innerText.includes(${JSON.stringify(fixture.product.productName)})
          && dialog?.innerText.includes(${JSON.stringify(String(fixture.initialQty))});
      })()
    `, 'check stock line auto filled', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after check warehouse selected'))}`)
    })
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="请选择盘点日期"]')`, fixture.checkDate, 'check date input')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'check remark input')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-table__body-wrapper .el-input-number input')`, String(fixture.actualQty), 'check actual qty input')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const dialog = ${createDialog};
        return dialog?.innerText.includes(${JSON.stringify(String(fixture.actualQty - fixture.initialQty))});
      })()
    `, 'check difference calculated')
    await clickButton(cdp, '确定', createDialog)

    check = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/inventory/checks?pageNo=1&pageSize=20', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'COUNTED') return false;
        const detailResponse = await fetch('/api/inventory/checks/' + encodeURIComponent(record.id), { headers });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const detail = detailPayload?.data;
        const line = detail?.lines?.[0];
        if (!line || String(line.productId) !== ${JSON.stringify(String(fixture.product.id))}) return false;
        if (Number(line.bookQty) !== ${fixture.initialQty}) return false;
        if (Number(line.actualQty) !== ${fixture.actualQty}) return false;
        if (Number(line.differenceQty) !== ${fixture.actualQty - fixture.initialQty}) return false;
        return detail;
      })()
    `, 'inventory check created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory check create'))}`)
    })

    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入盘点单号"]')`, check.checkNo, 'check no search input')
    await clickButton(cdp, '查询')
    const checkRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(check.checkNo)}))
    `
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${checkRowExpression};
        return row?.innerText.includes('已录入') && row?.innerText.includes(${JSON.stringify(remark)});
      })()
    `, 'inventory check counted row visible')
    await invokeElement(
      cdp,
      `(() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${checkRowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '调整');
      })()`,
      'inventory check adjust button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认根据此盘点差异调整库存吗')});
      })()
    `, 'inventory check adjust confirm')
    await clickButton(cdp, '确定', messageBoxExpression('确认根据此盘点差异调整库存吗'))
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${checkRowExpression};
        return row?.innerText.includes('已调整');
      })()
    `, 'inventory check adjusted row visible', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory check adjust'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const detailResponse = await fetch('/api/inventory/checks/' + ${JSON.stringify(check.id)}, { headers });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const detail = detailPayload?.data;
        const balanceResponse = await fetch('/api/inventory/balances?pageNo=1&pageSize=20&warehouseId=' + encodeURIComponent(${JSON.stringify(String(fixture.warehouse.id))}) + '&productId=' + encodeURIComponent(${JSON.stringify(String(fixture.product.id))}), { headers });
        if (!balanceResponse.ok) return false;
        const balancePayload = await balanceResponse.json();
        const balance = (balancePayload?.data?.records || []).find((item) => String(item.warehouseId) === ${JSON.stringify(String(fixture.warehouse.id))} && String(item.productId) === ${JSON.stringify(String(fixture.product.id))});
        return detail?.status === 'ADJUSTED'
          && Boolean(detail?.generatedAdjustmentNo)
          && Number(detail?.lines?.[0]?.differenceQty) === ${fixture.actualQty - fixture.initialQty}
          && Number(balance?.qtyOnHand) === ${fixture.actualQty}
          && Number(balance?.qtyAvailable) === ${fixture.actualQty}
          && Number(balance?.qtyReserved) === 0;
      })()
    `, 'inventory check persisted and balance adjusted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after inventory check persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'inventory check workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'inventory-check-create-adjust-balance',
    checkNo: check?.checkNo || null,
    warehouseCode: fixture.warehouse.warehouseCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function runSalesOrderWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareSalesOrderFixture(auth)
  const submitterCredentials = { username: 'runtime_smoke', password: 'RuntimeSmoke123' }
  const submitterAuth = await loginWithCredentials(submitterCredentials)
  const remark = `UI smoke 销售订单 ${fixture.suffix}`
  let order = null
  try {
    await setBrowserAuth(cdp, submitterAuth)
    await markSmokeStep(cdp, 'sales:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/sales/orders' })
    await waitForPage(cdp, `
      document.body.innerText.includes('销售订单')
        && document.body.innerText.includes('新增订单')
    `, 'sales order page')

    await markSmokeStep(cdp, 'sales:open-create-dialog')
    await clickButton(cdp, '新增订单')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增销售订单')});
      })()
    `, 'sales create dialog')

    const createDialog = `(${dialogExpression('新增销售订单')} || document.createElement('div'))`
    await markSmokeStep(cdp, 'sales:select-customer')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'sales customer select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.customer.customerName)}))`,
      'sales customer option'
    )
    await markSmokeStep(cdp, 'sales:select-warehouse')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'sales warehouse select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.warehouse.warehouseName)}))`,
      'sales warehouse option'
    )
    await markSmokeStep(cdp, 'sales:skip-hidden-date-fields')
    await markSmokeStep(cdp, 'sales:set-remark-and-product')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'sales remark input')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[2].querySelector('.el-select__wrapper')`, 'sales product select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.product.productCode)}))`,
      'sales product option'
    )
    await markSmokeStep(cdp, 'sales:set-line-and-save')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-input-number input')[0]`, String(fixture.qty), 'sales qty input')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-input-number input')[1]`, String(fixture.price), 'sales price input')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-input-number input')[2]`, String(fixture.taxRate), 'sales tax rate input')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-table__body-wrapper input[placeholder="备注"]')`, 'UI smoke 销售明细', 'sales line remark input')
    await clickElement(cdp, `${createDialog}.querySelector('.el-dialog__footer .el-button--primary')`, 'production save button')

    await markSmokeStep(cdp, 'sales:wait-created')
    order = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/sales/orders?pageNo=1&pageSize=20&customerId=' + encodeURIComponent(${JSON.stringify(fixture.customer.id)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT' || record.approvalStatus !== 'NOT_SUBMITTED') return false;
        return record;
      })()
    `, 'sales order created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales order create'))}`)
    })

    await markSmokeStep(cdp, 'sales:search-created-order')
    await setElementValue(cdp, `document.querySelector('input[placeholder="订单号"]')`, order.orderNo, 'sales order no search input')
    await clickButton(cdp, '查询')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
        return row?.innerText.includes('草稿');
      })()
    `, 'sales draft row visible')

    const orderRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}))
    `
    await markSmokeStep(cdp, 'sales:submit-order')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '提交')`,
      'sales submit button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确定提交该销售订单吗')});
      })()
    `, 'sales submit confirm')
    await clickButton(cdp, '确定', messageBoxExpression('确定提交该销售订单吗'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/sales/orders/' + ${JSON.stringify(order.id)}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const detail = payload?.data;
        return detail?.status === 'SUBMITTED' && detail.approvalStatus === 'IN_APPROVAL';
      })()
    `, 'sales order submitted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales submit'))}`)
    })

    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'sales:search-submitted-order-as-approver')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/sales/orders' })
    await waitForPage(cdp, `
      document.body.innerText.includes('销售订单')
        && document.body.innerText.includes('新增订单')
    `, 'sales order page as approver')
    await setElementValue(cdp, `document.querySelector('input[placeholder="订单号"]')`, order.orderNo, 'sales order no search input after submit')
    await clickButton(cdp, '查询')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .some((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
      })()
    `, 'sales submitted row visible', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales submit search'))}`)
    })

    await markSmokeStep(cdp, 'sales:approve-order')
    await evaluatePage(cdp, `
      (() => {
        window.__uiSmoke = {
          ...(window.__uiSmoke || {}),
          approveRequests: []
        };
        if (!window.__uiSmokeApprovePatched) {
          window.__uiSmokeApprovePatched = true;
          const rawFetch = window.fetch;
          window.fetch = (...args) => {
            const url = String(args[0]);
            if (url.includes('/api/sales/orders/') && url.includes('/approve')) {
              window.__uiSmoke.approveRequests.push({ type: 'fetch', url, status: null, responseText: '', done: false });
            }
            return rawFetch(...args);
          };
          const rawOpen = XMLHttpRequest.prototype.open;
          const rawSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(method, url, ...rest) {
            this.__uiSmokeRecord = null;
            if (String(url).includes('/api/sales/orders/') && String(url).includes('/approve')) {
              this.__uiSmokeRecord = { type: 'xhr', method, url: String(url), status: null, responseText: '', done: false };
              window.__uiSmoke.approveRequests.push(this.__uiSmokeRecord);
              this.addEventListener('loadend', () => {
                this.__uiSmokeRecord.status = this.status;
                this.__uiSmokeRecord.responseText = String(this.responseText || '').slice(0, 1000);
                this.__uiSmokeRecord.done = true;
              });
            }
            return rawOpen.call(this, method, url, ...rest);
          };
          XMLHttpRequest.prototype.send = function(...args) {
            return rawSend.apply(this, args);
          };
        }
        return true;
      })()
    `)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${orderRowExpression};
        if (!row) return false;
        return [...row.querySelectorAll('button')].some((button) => visible(button) && button.innerText.trim() === '通过');
      })()
    `, 'sales approve button visible', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales submit row'))}`)
    })
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '通过')`,
      'sales approve button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确定审批通过该销售订单吗')});
      })()
    `, 'sales approve confirm')
    await invokeButton(cdp, '确定', 'sales approve confirm button', messageBoxExpression('确定审批通过该销售订单吗'))
    await waitForPage(cdp, `
      (() => (window.__uiSmoke?.approveRequests || []).some((request) => request.done))
    `, 'sales approve api request', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales approve confirm'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/sales/orders/' + ${JSON.stringify(order.id)}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const detail = payload?.data;
        const line = detail?.lines?.[0];
        return detail?.status === 'APPROVED'
          && detail.approvalStatus === 'APPROVED'
          && Number(detail.totalQuantity) === ${fixture.qty}
          && Number(detail.totalAmount) === ${fixture.qty * fixture.price}
          && line?.productId === ${JSON.stringify(fixture.product.id)}
          && Number(line.qty) === ${fixture.qty}
          && Number(line.price) === ${fixture.price};
      })()
    `, 'sales order persisted as approved', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'sales workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'sales-order-create-submit-approve',
    orderNo: order?.orderNo || null,
    submitter: submitterCredentials.username,
    approver: 'admin',
    customerCode: fixture.customer.customerCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function runWorkflowTaskApprovalWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareSalesOrderFixture(auth)
  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  const remark = `UI smoke 审批中心 ${fixture.suffix}`
  const approveComment = `审批中心通过 ${fixture.suffix}`
  let order = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `

  try {
    await markSmokeStep(cdp, 'workflow-task:prepare-submitted-sales-order')
    order = await apiRequest(submitterAuth, 'POST', '/sales/orders', {
      customerId: fixture.customer.id,
      warehouseId: fixture.warehouse.id,
      orderDate: fixture.orderDate,
      deliveryDate: fixture.orderDate,
      remark,
      lines: [
        {
          productId: fixture.product.id,
          qty: fixture.qty,
          price: fixture.price,
          taxRate: fixture.taxRate,
          remark: 'UI smoke 审批中心明细'
        }
      ]
    })
    await apiRequest(submitterAuth, 'POST', `/sales/orders/${order.id}/submit`, { remark: 'UI smoke 审批中心提交' })

    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'workflow-task:navigate')
    await cdp.send('Page.navigate', {
      url: `http://127.0.0.1:5173/workflow/tasks?businessType=SALES_ORDER&businessId=${encodeURIComponent(order.id)}&status=PENDING`
    })
    await waitForPage(cdp, `
      document.body.innerText.includes('审批待办')
        && document.body.innerText.includes(${JSON.stringify(order.orderNo)})
    `, 'workflow task page with pending sales order', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow task navigate'))}`)
    })

    await markSmokeStep(cdp, 'workflow-task:view-detail')
    await invokeElement(
      cdp,
      `(() => {
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '查看');
      })()`,
      'workflow task detail button'
    )
    await waitForPage(cdp, `
      (() => {
        ${visibleDefinition}
        const dialog = ${dialogExpression('审批任务详情')};
        return Boolean(dialog)
          && dialog.innerText.includes(${JSON.stringify(order.orderNo)})
          && dialog.innerText.includes(${JSON.stringify(String(order.id))})
          && dialog.innerText.includes('待审批');
      })()
    `, 'workflow task detail dialog', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow task detail'))}`)
    })
    await clickButton(cdp, '关闭', dialogExpression('审批任务详情'))

    await markSmokeStep(cdp, 'workflow-task:approve')
    await invokeElement(
      cdp,
      `(() => {
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '通过');
      })()`,
      'workflow task approve button'
    )
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('审批通过')}); })()`, 'workflow approve dialog')
    const approveDialog = dialogExpression('审批通过')
    await setElementValue(cdp, `${approveDialog}.querySelector('textarea')`, approveComment, 'workflow approve comment input')
    await clickButton(cdp, '确定', approveDialog)

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const orderResponse = await fetch('/api/sales/orders/' + ${JSON.stringify(String(order.id))}, { headers });
        if (!orderResponse.ok) return false;
        const orderPayload = await orderResponse.json();
        const detail = orderPayload?.data;
        if (detail?.status !== 'APPROVED' || detail?.approvalStatus !== 'APPROVED') return false;
        const taskResponse = await fetch('/api/workflow/tasks?businessType=SALES_ORDER&businessId=' + encodeURIComponent(${JSON.stringify(String(order.id))}) + '&pageNo=1&pageSize=20', { headers });
        if (!taskResponse.ok) return false;
        const taskPayload = await taskResponse.json();
        const task = (taskPayload?.data?.records || []).find((item) => String(item.businessId) === ${JSON.stringify(String(order.id))});
        if (!task || task.status !== 'APPROVED') return false;
        const recordResponse = await fetch('/api/workflow/records?businessType=SALES_ORDER&businessId=' + encodeURIComponent(${JSON.stringify(String(order.id))}) + '&action=APPROVE&pageNo=1&pageSize=20', { headers });
        if (!recordResponse.ok) return false;
        const recordPayload = await recordResponse.json();
        return (recordPayload?.data?.records || []).some((item) =>
          String(item.businessId) === ${JSON.stringify(String(order.id))}
          && item.action === 'APPROVE'
          && item.comment === ${JSON.stringify(approveComment)}
        );
      })()
    `, 'workflow task approval persisted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow task approve persistence'))}`)
    })

    await markSmokeStep(cdp, 'workflow-record:navigate-filtered')
    await cdp.send('Page.navigate', {
      url: `http://127.0.0.1:5173/workflow/records?businessType=SALES_ORDER&businessId=${encodeURIComponent(order.id)}&action=APPROVE`
    })
    await waitForPage(cdp, `
      document.body.innerText.includes('审批记录')
        && document.body.innerText.includes(${JSON.stringify(order.orderNo)})
        && document.body.innerText.includes(${JSON.stringify(approveComment)})
    `, 'workflow records page filtered by business id', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow records navigate'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'workflow task approval workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'workflow-task-detail-approve-and-record-filter',
    orderNo: order?.orderNo || null,
    submitter: 'runtime_smoke',
    approver: 'admin',
    passed: failures.length === 0,
    failures
  }
}

async function runWorkflowTaskRejectWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareSalesOrderFixture(auth)
  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  const remark = `UI smoke 审批驳回 ${fixture.suffix}`
  const rejectComment = `审批中心驳回 ${fixture.suffix}`
  let order = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `

  try {
    await markSmokeStep(cdp, 'workflow-reject:prepare-submitted-sales-order')
    order = await apiRequest(submitterAuth, 'POST', '/sales/orders', {
      customerId: fixture.customer.id,
      warehouseId: fixture.warehouse.id,
      orderDate: fixture.orderDate,
      deliveryDate: fixture.orderDate,
      remark,
      lines: [
        {
          productId: fixture.product.id,
          qty: fixture.qty,
          price: fixture.price,
          taxRate: fixture.taxRate,
          remark: 'UI smoke 审批驳回明细'
        }
      ]
    })
    await apiRequest(submitterAuth, 'POST', `/sales/orders/${order.id}/submit`, { remark: 'UI smoke 审批驳回提交' })

    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'workflow-reject:navigate')
    await cdp.send('Page.navigate', {
      url: `http://127.0.0.1:5173/workflow/tasks?businessType=SALES_ORDER&businessId=${encodeURIComponent(order.id)}&status=PENDING`
    })
    await waitForPage(cdp, `
      document.body.innerText.includes('审批待办')
        && document.body.innerText.includes(${JSON.stringify(order.orderNo)})
    `, 'workflow task page with pending sales order for reject', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow reject navigate'))}`)
    })

    await markSmokeStep(cdp, 'workflow-reject:click-reject')
    await invokeElement(
      cdp,
      `(() => {
        ${visibleDefinition}
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '驳回');
      })()`,
      'workflow task reject button'
    )
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('审批驳回')}); })()`, 'workflow reject dialog')
    const rejectDialog = dialogExpression('审批驳回')
    await setElementValue(cdp, `${rejectDialog}.querySelector('textarea')`, rejectComment, 'workflow reject comment input')
    await clickButton(cdp, '确定', rejectDialog)

    await markSmokeStep(cdp, 'workflow-reject:verify')
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const orderResponse = await fetch('/api/sales/orders/' + ${JSON.stringify(String(order.id))}, { headers });
        if (!orderResponse.ok) return false;
        const detail = (await orderResponse.json())?.data;
        if (detail?.status !== 'REJECTED' || detail?.approvalStatus !== 'REJECTED') return false;
        const taskResponse = await fetch('/api/workflow/tasks?businessType=SALES_ORDER&businessId=' + encodeURIComponent(${JSON.stringify(String(order.id))}) + '&pageNo=1&pageSize=20', { headers });
        if (!taskResponse.ok) return false;
        const task = ((await taskResponse.json())?.data?.records || []).find((item) => String(item.businessId) === ${JSON.stringify(String(order.id))});
        if (!task || task.status !== 'REJECTED') return false;
        const recordResponse = await fetch('/api/workflow/records?businessType=SALES_ORDER&businessId=' + encodeURIComponent(${JSON.stringify(String(order.id))}) + '&action=REJECT&pageNo=1&pageSize=20', { headers });
        if (!recordResponse.ok) return false;
        return ((await recordResponse.json())?.data?.records || []).some((item) =>
          String(item.businessId) === ${JSON.stringify(String(order.id))}
          && item.action === 'REJECT'
          && item.comment === ${JSON.stringify(rejectComment)}
        );
      })()
    `, 'workflow task reject persisted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow reject persistence'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'workflow task reject workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'workflow-task-reject',
    orderNo: order?.orderNo || null,
    submitter: 'runtime_smoke',
    rejector: 'admin',
    passed: failures.length === 0,
    failures
  }
}

async function runWorkflowWithdrawWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareSalesOrderFixture(auth)
  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  const remark = `UI smoke 审批撤回 ${fixture.suffix}`
  const withdrawComment = `提交人撤回 ${fixture.suffix}`
  let order = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `

  try {
    await markSmokeStep(cdp, 'workflow-withdraw:prepare-submitted-sales-order')
    order = await apiRequest(submitterAuth, 'POST', '/sales/orders', {
      customerId: fixture.customer.id,
      warehouseId: fixture.warehouse.id,
      orderDate: fixture.orderDate,
      deliveryDate: fixture.orderDate,
      remark,
      lines: [
        {
          productId: fixture.product.id,
          qty: fixture.qty,
          price: fixture.price,
          taxRate: fixture.taxRate,
          remark: 'UI smoke 审批撤回明细'
        }
      ]
    })
    await apiRequest(submitterAuth, 'POST', `/sales/orders/${order.id}/submit`, { remark: 'UI smoke 审批撤回提交' })

    // 撤回必须由提交人操作
    await setBrowserAuth(cdp, submitterAuth)
    await markSmokeStep(cdp, 'workflow-withdraw:navigate-records')
    await cdp.send('Page.navigate', {
      url: `http://127.0.0.1:5173/workflow/records?businessType=SALES_ORDER&businessId=${encodeURIComponent(order.id)}&action=SUBMIT`
    })
    await waitForPage(cdp, `
      document.body.innerText.includes('审批记录')
        && document.body.innerText.includes(${JSON.stringify(order.orderNo)})
    `, 'workflow records page with submit record', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow withdraw navigate'))}`)
    })

    await markSmokeStep(cdp, 'workflow-withdraw:click-withdraw')
    await invokeElement(
      cdp,
      `(() => {
        ${visibleDefinition}
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '撤回');
      })()`,
      'workflow withdraw button'
    )
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('撤回审批')}); })()`, 'workflow withdraw dialog')
    const withdrawDialog = dialogExpression('撤回审批')
    await setElementValue(cdp, `${withdrawDialog}.querySelector('textarea')`, withdrawComment, 'workflow withdraw comment input')
    await clickButton(cdp, '确认撤回', withdrawDialog)

    await markSmokeStep(cdp, 'workflow-withdraw:verify')
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const recordResponse = await fetch('/api/workflow/records?businessType=SALES_ORDER&businessId=' + encodeURIComponent(${JSON.stringify(String(order.id))}) + '&action=WITHDRAW&pageNo=1&pageSize=20', { headers });
        if (!recordResponse.ok) return false;
        const hasWithdraw = ((await recordResponse.json())?.data?.records || []).some((item) =>
          String(item.businessId) === ${JSON.stringify(String(order.id))}
          && item.action === 'WITHDRAW'
          && item.comment === ${JSON.stringify(withdrawComment)}
        );
        if (!hasWithdraw) return false;
        const taskResponse = await fetch('/api/workflow/tasks?businessType=SALES_ORDER&businessId=' + encodeURIComponent(${JSON.stringify(String(order.id))}) + '&pageNo=1&pageSize=20', { headers });
        if (!taskResponse.ok) return false;
        const tasks = (await taskResponse.json())?.data?.records || [];
        const pending = tasks.filter((item) => String(item.businessId) === ${JSON.stringify(String(order.id))} && item.status === 'PENDING');
        const cancelled = tasks.some((item) => String(item.businessId) === ${JSON.stringify(String(order.id))} && item.status === 'CANCELLED');
        return pending.length === 0 && cancelled;
      })()
    `, 'workflow withdraw persisted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after workflow withdraw persistence'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'workflow withdraw workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  } finally {
    // 恢复 admin 会话，避免影响后续 workflow
    await setBrowserAuth(cdp, auth).catch(() => null)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'workflow-withdraw',
    orderNo: order?.orderNo || null,
    submitter: 'runtime_smoke',
    passed: failures.length === 0,
    failures
  }
}

async function runSalesDeliveryWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareApprovedSalesOrderFixture(auth)
  const remark = `UI smoke 销售发货 ${fixture.suffix}`
  let delivery = null
  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'sales-delivery:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/sales/deliveries' })
    await waitForPage(cdp, `
      document.body.innerText.includes('销售发货')
        && document.body.innerText.includes('新增发货')
    `, 'sales delivery page')

    await markSmokeStep(cdp, 'sales-delivery:open-create-dialog')
    await clickButton(cdp, '新增发货')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增销售发货')});
      })()
    `, 'sales delivery create dialog')

    const createDialog = `(${dialogExpression('新增销售发货')} || document.createElement('div'))`
    await markSmokeStep(cdp, 'sales-delivery:select-order')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'sales delivery order select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.order.orderNo)}))`,
      'sales delivery order option'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return ${createDialog}.innerText.includes(${JSON.stringify(String(fixture.qty))});
      })()
    `, 'sales delivery lines loaded').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales delivery order select'))}`)
    })

    await markSmokeStep(cdp, 'sales-delivery:select-warehouse-and-save')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'sales delivery warehouse select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.warehouse.warehouseName)}))`,
      'sales delivery warehouse option'
    )
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'sales delivery remark input')
    await clickButton(cdp, '确定', createDialog)

    await markSmokeStep(cdp, 'sales-delivery:wait-created')
    delivery = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/sales/deliveries?pageNo=1&pageSize=20&orderId=' + encodeURIComponent(${JSON.stringify(fixture.order.id)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        return record;
      })()
    `, 'sales delivery created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales delivery create'))}`)
    })

    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入发货单号"]')`, delivery.deliveryNo, 'sales delivery no search input')
    await clickButton(cdp, '查询')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(delivery.deliveryNo)}));
        if (!row) return false;
        return [...row.querySelectorAll('button')].some((button) => visible(button) && button.innerText.trim() === '过账');
      })()
    `, 'sales delivery post button visible', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales delivery create row'))}`)
    })
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '过账')`,
      'sales delivery post button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确定过账该销售发货单吗')});
      })()
    `, 'sales delivery post confirm')
    await invokeButton(cdp, '确定', 'sales delivery post confirm button', messageBoxExpression('确定过账该销售发货单吗'))

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const deliveryResponse = await fetch('/api/sales/deliveries/' + ${JSON.stringify(delivery.id)}, { headers });
        if (!deliveryResponse.ok) return false;
        const deliveryPayload = await deliveryResponse.json();
        const detail = deliveryPayload?.data;
        const expectedReceivableAmount = Number(detail?.totalAmount || 0) + Number(detail?.totalTaxAmount || 0);
        const orderResponse = await fetch('/api/sales/orders/' + ${JSON.stringify(fixture.order.id)}, { headers });
        if (!orderResponse.ok) return false;
        const orderPayload = await orderResponse.json();
        const orderDetail = orderPayload?.data;
        const receivableResponse = await fetch('/api/finance/receivables?pageNo=1&pageSize=20&customerId=' + encodeURIComponent(${JSON.stringify(fixture.customer.id)}), { headers });
        if (!receivableResponse.ok) return false;
        const receivablePayload = await receivableResponse.json();
        const receivables = receivablePayload?.data?.records || [];
        return detail?.status === 'POSTED'
          && Number(detail.totalQuantity) === ${fixture.qty}
          && orderDetail?.deliveryStatus === 'FULL_DELIVERED'
          && receivables.some((item) =>
            item.sourceNo === detail.deliveryNo
              && Math.abs(Number(item.remainingAmount) - expectedReceivableAmount) < 0.001
              && item.status === 'UNSETTLED'
          );
      })()
    `, 'sales delivery posted and receivable created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales delivery post'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'sales delivery workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'sales-delivery-create-post-receivable',
    orderNo: fixture.order.orderNo,
    deliveryNo: delivery?.deliveryNo || null,
    customerCode: fixture.customer.customerCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function runSalesReceiptWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await preparePostedSalesDeliveryFixture(auth)
  const remark = `UI smoke 销售收款 ${fixture.suffix}`
  let receipt = null
  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'sales-receipt:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/payments' })
    await waitForPage(cdp, `
      document.body.innerText.includes('收款管理')
        && document.body.innerText.includes('新增收款')
    `, 'finance receipts page')

    await markSmokeStep(cdp, 'sales-receipt:open-create-dialog')
    await clickButton(cdp, '新增收款')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增收款')});
      })()
    `, 'receipt create dialog')

    const createDialog = `(${dialogExpression('新增收款')} || document.createElement('div'))`
    await markSmokeStep(cdp, 'sales-receipt:select-customer')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'receipt customer select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.customer.customerName)}))`,
      'receipt customer option'
    )
    await markSmokeStep(cdp, 'sales-receipt:select-receivable')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'receipt receivable select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.receivable.receivableNo)}))`,
      'receipt receivable option'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const amountInput = ${createDialog}.querySelector('.el-input-number input');
        return amountInput && Number(amountInput.value) > 0;
      })()
    `, 'receipt amount filled')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'receipt remark input')
    await clickButton(cdp, '确定', createDialog)

    await markSmokeStep(cdp, 'sales-receipt:wait-created')
    receipt = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/finance/receipts?pageNo=1&pageSize=20&customerId=' + encodeURIComponent(${JSON.stringify(fixture.customer.id)}), { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'POSTED') return false;
        const receivableResponse = await fetch('/api/finance/receivables/' + ${JSON.stringify(fixture.receivable.id)}, { headers });
        if (!receivableResponse.ok) return false;
        const receivablePayload = await receivableResponse.json();
        const receivable = receivablePayload?.data;
        if (receivable?.status !== 'SETTLED' || Number(receivable.remainingAmount) !== 0) return false;
        return record;
      })()
    `, 'receipt created and receivable settled', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after receipt create'))}`)
    })

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(receipt.receiptNo)}));
        return row?.innerText.includes('已过账');
      })()
    `, 'receipt row shows posted status', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after receipt row status check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'sales receipt workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'sales-receipt-create-settle-receivable',
    orderNo: fixture.order.orderNo,
    deliveryNo: fixture.delivery.deliveryNo,
    receiptNo: receipt?.receiptNo || null,
    receivableNo: fixture.receivable.receivableNo,
    passed: failures.length === 0,
    failures
  }
}

async function runSalesReturnWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await preparePostedSalesDeliveryFixture(auth)
  const remark = `UI smoke 销售退货 ${fixture.suffix}`
  let salesReturn = null
  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'sales-return:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/sales/returns' })
    await waitForPage(cdp, `
      document.body.innerText.includes('销售退货')
        && document.body.innerText.includes('新增退货')
    `, 'sales return page')

    await markSmokeStep(cdp, 'sales-return:open-create-dialog')
    await clickButton(cdp, '新增退货')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增销售退货')});
      })()
    `, 'sales return create dialog')

    const createDialog = `(${dialogExpression('新增销售退货')} || document.createElement('div'))`
    await markSmokeStep(cdp, 'sales-return:select-delivery')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'sales return delivery select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.delivery.deliveryNo)}))`,
      'sales return delivery option'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return ${createDialog}.innerText.includes(${JSON.stringify(fixture.product.productCode)})
          && ${createDialog}.innerText.includes(${JSON.stringify(String(fixture.qty))});
      })()
    `, 'sales return lines loaded').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales return delivery select'))}`)
    })
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'sales return remark input')
    await clickButton(cdp, '确定', createDialog)

    await markSmokeStep(cdp, 'sales-return:wait-created')
    salesReturn = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/sales/returns?pageNo=1&pageSize=20&deliveryId=' + encodeURIComponent(${JSON.stringify(String(fixture.delivery.id))}), { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        const detailResponse = await fetch('/api/sales/returns/' + encodeURIComponent(record.id), { headers });
        if (!detailResponse.ok) return false;
        const detailPayload = await detailResponse.json();
        const detail = detailPayload?.data;
        const line = detail?.lines?.[0];
        if (!line || String(line.productId) !== ${JSON.stringify(String(fixture.product.id))}) return false;
        if (String(line.deliveryLineId) !== ${JSON.stringify(String(fixture.delivery.lines?.[0]?.id || fixture.delivery.items?.[0]?.id))}) return false;
        if (Number(line.qty) !== ${fixture.qty}) return false;
        return detail;
      })()
    `, 'sales return created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales return create'))}`)
    })

    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入退货单号"]')`, salesReturn.returnNo, 'sales return no search input')
    await clickButton(cdp, '查询')
    const salesReturnRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(salesReturn.returnNo)}))
    `
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${salesReturnRowExpression};
        return row?.innerText.includes('草稿') && row?.innerText.includes(${JSON.stringify(remark)});
      })()
    `, 'sales return draft row visible')
    await invokeElement(
      cdp,
      `(() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${salesReturnRowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '过账');
      })()`,
      'sales return post button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认过账此销售退货单吗')});
      })()
    `, 'sales return post confirm')
    await invokeButton(cdp, '确定', 'sales return post confirm button', messageBoxExpression('确认过账此销售退货单吗'))
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${salesReturnRowExpression};
        return row?.innerText.includes('已过账');
      })()
    `, 'sales return posted row visible', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales return post'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const returnResponse = await fetch('/api/sales/returns/' + ${JSON.stringify(salesReturn.id)}, { headers });
        if (!returnResponse.ok) return false;
        const returnPayload = await returnResponse.json();
        const detail = returnPayload?.data;
        const deliveryResponse = await fetch('/api/sales/deliveries/' + ${JSON.stringify(fixture.delivery.id)}, { headers });
        if (!deliveryResponse.ok) return false;
        const deliveryPayload = await deliveryResponse.json();
        const deliveryDetail = deliveryPayload?.data;
        const receivableResponse = await fetch('/api/finance/receivables?pageNo=1&pageSize=20&customerId=' + encodeURIComponent(${JSON.stringify(fixture.customer.id)}), { headers });
        if (!receivableResponse.ok) return false;
        const receivablePayload = await receivableResponse.json();
        const receivables = receivablePayload?.data?.records || [];
        const balanceResponse = await fetch('/api/inventory/balances?pageNo=1&pageSize=20&warehouseId=' + encodeURIComponent(${JSON.stringify(String(fixture.warehouse.id))}) + '&productId=' + encodeURIComponent(${JSON.stringify(String(fixture.product.id))}), { headers });
        if (!balanceResponse.ok) return false;
        const balancePayload = await balanceResponse.json();
        const balance = (balancePayload?.data?.records || []).find((item) => String(item.warehouseId) === ${JSON.stringify(String(fixture.warehouse.id))} && String(item.productId) === ${JSON.stringify(String(fixture.product.id))});
        return detail?.status === 'POSTED'
          && Number(detail?.lines?.[0]?.qty) === ${fixture.qty}
          && Number(deliveryDetail?.lines?.[0]?.returnedQty) === ${fixture.qty}
          && receivables.some((item) =>
            item.sourceType === 'SALES_RETURN'
              && item.sourceNo === detail.returnNo
              && item.direction === 'DECREASE'
              && item.status === 'OFFSET'
          )
          && Number(balance?.qtyOnHand) === 20
          && Number(balance?.qtyAvailable) === 20
          && Number(balance?.qtyReserved) === 0;
      })()
    `, 'sales return posted, receivable offset, and stock restored', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales return persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'sales return workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'sales-return-create-post-stock-receivable-offset',
    orderNo: fixture.order.orderNo,
    deliveryNo: fixture.delivery.deliveryNo,
    returnNo: salesReturn?.returnNo || null,
    customerCode: fixture.customer.customerCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function prepareDraftPurchaseReceiptFixture(auth) {
  const fixture = await preparePurchaseOrderFixture(auth)
  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  const order = await apiRequest(submitterAuth, 'POST', '/purchase/orders', {
    supplierId: fixture.supplier.id,
    orderDate: fixture.orderDate,
    deliveryDate: fixture.deliveryDate,
    remark: `UI smoke 待收货采购订单 ${fixture.suffix}`,
    lines: [
      {
        productId: fixture.product.id,
        qty: fixture.qty,
        price: fixture.price,
        taxRate: fixture.taxRate,
        remark: 'UI smoke 采购订单明细'
      }
    ]
  })
  await apiRequest(submitterAuth, 'POST', `/purchase/orders/${order.id}/submit`, { remark: 'UI smoke 提交采购订单' })
  const approvedOrder = await apiRequest(auth, 'POST', `/purchase/orders/${order.id}/approve`, { remark: 'UI smoke 审批采购订单' })
  const orderLine = (approvedOrder.lines || approvedOrder.items || [])[0]
  if (!orderLine?.id) {
    throw new Error(`approved purchase order ${approvedOrder.orderNo} has no line id`)
  }
  const receipt = await apiRequest(auth, 'POST', '/purchase/receipts', {
    orderId: approvedOrder.id,
    warehouseId: fixture.warehouse.id,
    receiptDate: fixture.orderDate,
    remark: `UI smoke 草稿采购收货 ${fixture.suffix}`,
    lines: [
      {
        orderLineId: orderLine.id,
        qty: fixture.qty,
        remark: 'UI smoke 采购收货明细'
      }
    ]
  })
  if (receipt.status !== 'DRAFT') {
    throw new Error(`created purchase receipt ${receipt.receiptNo} is not DRAFT: ${receipt.status}`)
  }
  return {
    suffix: fixture.suffix,
    id: receipt.id,
    docNo: receipt.receiptNo,
    detailPath: `/purchase/receipts/${receipt.id}`,
    barcode: fixture.product.barcode,
    productCode: fixture.product.productCode
  }
}

async function prepareDraftPurchaseReturnFixture(auth) {
  const fixture = await preparePostedPurchaseReceiptFixture(auth)
  const receiptDetail = await apiRequest(auth, 'GET', `/purchase/receipts/${fixture.receipt.id}`)
  const receiptLine = (receiptDetail.lines || [])[0]
  if (!receiptLine?.id) {
    throw new Error(`posted purchase receipt ${fixture.receipt.receiptNo} has no line id`)
  }
  const purchaseReturn = await apiRequest(auth, 'POST', '/purchase/returns', {
    receiptId: fixture.receipt.id,
    returnDate: fixture.orderDate,
    remark: `UI smoke 草稿采购退货 ${fixture.suffix}`,
    lines: [
      {
        receiptLineId: receiptLine.id,
        qty: 2,
        remark: 'UI smoke 采购退货明细'
      }
    ]
  })
  if (purchaseReturn.status !== 'DRAFT') {
    throw new Error(`created purchase return ${purchaseReturn.returnNo} is not DRAFT: ${purchaseReturn.status}`)
  }
  return {
    suffix: fixture.suffix,
    id: purchaseReturn.id,
    docNo: purchaseReturn.returnNo,
    detailPath: `/purchase/returns/${purchaseReturn.id}`
  }
}

async function prepareDraftSalesDeliveryFixture(auth) {
  const fixture = await prepareApprovedSalesOrderFixture(auth)
  const orderLine = (fixture.order.lines || fixture.order.items || [])[0]
  if (!orderLine?.id) {
    throw new Error(`approved sales order ${fixture.order.orderNo} has no line id`)
  }
  const delivery = await apiRequest(auth, 'POST', '/sales/deliveries', {
    orderId: fixture.order.id,
    warehouseId: fixture.warehouse.id,
    deliveryDate: fixture.orderDate,
    remark: `UI smoke 草稿销售发货 ${fixture.suffix}`,
    lines: [
      {
        orderLineId: orderLine.id,
        qty: fixture.qty,
        remark: 'UI smoke 销售发货明细'
      }
    ]
  })
  if (delivery.status !== 'DRAFT') {
    throw new Error(`created sales delivery ${delivery.deliveryNo} is not DRAFT: ${delivery.status}`)
  }
  return {
    suffix: fixture.suffix,
    id: delivery.id,
    docNo: delivery.deliveryNo,
    detailPath: `/sales/deliveries/${delivery.id}`,
    barcode: fixture.product.barcode,
    productCode: fixture.product.productCode
  }
}

async function prepareDraftSalesReturnFixture(auth) {
  const fixture = await preparePostedSalesDeliveryFixture(auth)
  const deliveryDetail = await apiRequest(auth, 'GET', `/sales/deliveries/${fixture.delivery.id}`)
  const deliveryLine = (deliveryDetail.lines || [])[0]
  if (!deliveryLine?.id) {
    throw new Error(`posted sales delivery ${fixture.delivery.deliveryNo} has no line id`)
  }
  const salesReturn = await apiRequest(auth, 'POST', '/sales/returns', {
    deliveryId: fixture.delivery.id,
    returnDate: fixture.orderDate,
    remark: `UI smoke 草稿销售退货 ${fixture.suffix}`,
    lines: [
      {
        deliveryLineId: deliveryLine.id,
        qty: fixture.qty,
        remark: 'UI smoke 销售退货明细'
      }
    ]
  })
  if (salesReturn.status !== 'DRAFT') {
    throw new Error(`created sales return ${salesReturn.returnNo} is not DRAFT: ${salesReturn.status}`)
  }
  return {
    suffix: fixture.suffix,
    id: salesReturn.id,
    docNo: salesReturn.returnNo,
    detailPath: `/sales/returns/${salesReturn.id}`
  }
}

async function runDraftBarcodeScan(cdp, prepared, editDialog, step) {
  if (!prepared.barcode) {
    throw new Error(`${step} fixture is missing product barcode`)
  }

  await markSmokeStep(cdp, `${step}:reset-scan-quantity`)
  await clickButton(cdp, '清零数量', editDialog)
  await waitForPage(
    cdp,
    visibleTextExpression('document.body', '确认清零当前'),
    `${step} reset quantity confirm`
  )
  await invokeButton(cdp, '清零', `${step} reset quantity confirm button`, messageBoxExpression('确认清零当前'))

  const quantityInput = `${editDialog}.querySelector('.el-input-number input')`
  await waitForPage(cdp, `Number((${quantityInput})?.value) === 0`, `${step} quantity reset to zero`)

  const barcodeInput = `${editDialog}.querySelector('input[placeholder="扫描或输入商品条码"]')`
  await markSmokeStep(cdp, `${step}:scan-barcode`)
  await setElementValue(cdp, barcodeInput, prepared.barcode, `${step} barcode input`)
  await pressElementKey(cdp, barcodeInput, 'Enter', `${step} scanner Enter`)
  await waitForPage(cdp, `
    Number((${quantityInput})?.value) === 1
      && ${editDialog}.innerText.includes(${JSON.stringify(prepared.productCode)})
  `, `${step} scanned quantity incremented`, 15000)

  prepared.expectedLineQty = 1
  const screenshot = await cdp.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  writeFileSync(
    join(targetDir, `ui-smoke-${step}-barcode.png`),
    Buffer.from(screenshot.data, 'base64')
  )
}

// 草稿单据 PUT 编辑通用 workflow：四个单据页(采购收货/采购退货/销售发货/销售退货)的
// 草稿编辑弹窗完全同构 —— 列表 DRAFT 行的「编辑」入口打开弹窗、改备注、点「确定」走 PUT。
// 这里只专注覆盖此前没有独立 browser smoke 的编辑 PUT 路径：草稿单先走 API 造好，
// 浏览器里改备注保存后回查后端确认 remark 已更新且状态仍为 DRAFT(未误触发状态流转)。
async function runDraftEditWorkflow(cdp, auth, opts) {
  resetEvents(cdp.events)
  const prepared = await opts.prepareDraft(auth)
  const newRemark = `UI smoke ${opts.docLabel}编辑 ${prepared.suffix}`
  const rowExpression = `
    [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
      .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(prepared.docNo)}))
  `
  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, `${opts.step}:navigate`)
    await cdp.send('Page.navigate', { url: opts.url })
    await waitForPage(cdp, `document.body.innerText.includes(${JSON.stringify(opts.pageText)})`, `${opts.docLabel} page`)

    await markSmokeStep(cdp, `${opts.step}:search`)
    await setElementValue(cdp, `document.querySelector('input[placeholder=${JSON.stringify(opts.searchPlaceholder)}]')`, prepared.docNo, `${opts.docLabel} search input`)
    await clickButton(cdp, opts.searchButton)

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${rowExpression};
        return row?.innerText.includes('草稿');
      })()
    `, `${opts.docLabel} draft row visible`).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, `after ${opts.step} search`))}`)
    })

    await markSmokeStep(cdp, `${opts.step}:open-edit-dialog`)
    await invokeElement(cdp, `
      (() => {
        const row = ${rowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '编辑');
      })()
    `, `${opts.docLabel} edit button`)

    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression(opts.editTitle)});
      })()
    `, `${opts.docLabel} edit dialog`)

    const editDialog = `(${dialogExpression(opts.editTitle)} || document.createElement('div'))`
    if (opts.beforeSave) {
      await opts.beforeSave(cdp, prepared, editDialog)
    }
    await markSmokeStep(cdp, `${opts.step}:update-remark`)
    await setElementValue(cdp, `${editDialog}.querySelector('textarea')`, newRemark, `${opts.docLabel} remark input`)
    await clickButton(cdp, '确定', editDialog)

    await markSmokeStep(cdp, `${opts.step}:verify`)
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api${prepared.detailPath}', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const detail = payload?.data;
        const detailLines = detail?.lines || detail?.items || [];
        return detail?.remark === ${JSON.stringify(newRemark)}
          && detail?.status === 'DRAFT'
          && (${prepared.expectedLineQty == null
            ? 'true'
            : `Number(detailLines[0]?.qty ?? detailLines[0]?.quantity) === ${JSON.stringify(prepared.expectedLineQty)}`});
      })()
    `, `${opts.docLabel} draft remark updated and status still DRAFT`, 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, `after ${opts.step} edit submit`))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, `${opts.name} workflow failure`).catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: opts.name,
    docNo: prepared.docNo,
    editedRemark: newRemark,
    scannedBarcode: prepared.barcode || null,
    lineQty: prepared.expectedLineQty || null,
    passed: failures.length === 0,
    failures
  }
}

function runPurchaseReceiptDraftEditWorkflow(cdp, auth) {
  return runDraftEditWorkflow(cdp, auth, {
    name: 'purchase-receipt-draft-edit',
    step: 'purchase-receipt-draft-edit',
    docLabel: '采购收货',
    url: 'http://127.0.0.1:5173/purchase/receipts',
    pageText: '采购收货管理',
    searchPlaceholder: '请输入收货单号',
    searchButton: '搜索',
    editTitle: '编辑采购收货',
    prepareDraft: prepareDraftPurchaseReceiptFixture,
    beforeSave: (browser, prepared, dialog) => runDraftBarcodeScan(
      browser,
      prepared,
      dialog,
      'purchase-receipt-draft-edit'
    )
  })
}

function runPurchaseReturnDraftEditWorkflow(cdp, auth) {
  return runDraftEditWorkflow(cdp, auth, {
    name: 'purchase-return-draft-edit',
    step: 'purchase-return-draft-edit',
    docLabel: '采购退货',
    url: 'http://127.0.0.1:5173/purchase/returns',
    pageText: '采购退货',
    searchPlaceholder: '请输入退货单号',
    searchButton: '搜索',
    editTitle: '编辑采购退货',
    prepareDraft: prepareDraftPurchaseReturnFixture
  })
}

function runSalesDeliveryDraftEditWorkflow(cdp, auth) {
  return runDraftEditWorkflow(cdp, auth, {
    name: 'sales-delivery-draft-edit',
    step: 'sales-delivery-draft-edit',
    docLabel: '销售发货',
    url: 'http://127.0.0.1:5173/sales/deliveries',
    pageText: '销售发货',
    searchPlaceholder: '请输入发货单号',
    searchButton: '查询',
    editTitle: '编辑销售发货',
    prepareDraft: prepareDraftSalesDeliveryFixture,
    beforeSave: (browser, prepared, dialog) => runDraftBarcodeScan(
      browser,
      prepared,
      dialog,
      'sales-delivery-draft-edit'
    )
  })
}

function runSalesReturnDraftEditWorkflow(cdp, auth) {
  return runDraftEditWorkflow(cdp, auth, {
    name: 'sales-return-draft-edit',
    step: 'sales-return-draft-edit',
    docLabel: '销售退货',
    url: 'http://127.0.0.1:5173/sales/returns',
    pageText: '销售退货',
    searchPlaceholder: '请输入退货单号',
    searchButton: '查询',
    editTitle: '编辑销售退货',
    prepareDraft: prepareDraftSalesReturnFixture
  })
}

async function runPurchaseReturnWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await preparePostedPurchaseReceiptFixture(auth)
  const returnQty = 2
  const remark = `UI smoke 采购退货 ${fixture.suffix}`
  let purchaseReturn = null
  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'purchase-return:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/purchase/returns' })
    await waitForPage(cdp, `
      document.body.innerText.includes('采购退货')
        && document.body.innerText.includes('新增退货')
    `, 'purchase return page')

    await markSmokeStep(cdp, 'purchase-return:open-create-dialog')
    await clickButton(cdp, '新增退货')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增采购退货')});
      })()
    `, 'purchase return create dialog')

    const createDialog = `(${dialogExpression('新增采购退货')} || document.createElement('div'))`
    await markSmokeStep(cdp, 'purchase-return:select-receipt')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'purchase return receipt select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.receipt.receiptNo)}))`,
      'purchase return receipt option'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return ${createDialog}.innerText.includes(${JSON.stringify(fixture.product.productName)})
          && ${createDialog}.innerText.includes(${JSON.stringify(String(fixture.qty))});
      })()
    `, 'purchase return lines loaded').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase return receipt select'))}`)
    })
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-date-editor input')[0]`, fixture.orderDate, 'purchase return date input')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-input-number input')`, String(returnQty), 'purchase return qty input')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'purchase return remark input')
    await clickButton(cdp, '确定', createDialog)

    await markSmokeStep(cdp, 'purchase-return:wait-created')
    purchaseReturn = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/purchase/returns?pageNo=1&pageSize=20&receiptId=' + encodeURIComponent(${JSON.stringify(fixture.receipt.id)}), { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        return record;
      })()
    `, 'purchase return created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase return create'))}`)
    })

    await markSmokeStep(cdp, 'purchase-return:search-created-return')
    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入退货单号"]')`, purchaseReturn.returnNo, 'purchase return no search input')
    await clickButton(cdp, '搜索')
    const purchaseReturnRowExpression = `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(purchaseReturn.returnNo)}));
      })()
    `
    await waitForPage(cdp, `
      (() => {
        const row = ${purchaseReturnRowExpression};
        return row && row.innerText.includes(${JSON.stringify(purchaseReturn.returnNo)}) && row.innerText.includes('草稿');
      })()
    `, 'purchase return draft row visible', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase return row'))}`)
    })

    await markSmokeStep(cdp, 'purchase-return:post')
    await invokeElement(
      cdp,
      `(() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${purchaseReturnRowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '过账');
      })()`,
      'purchase return post button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认过账此采购退货单吗')});
      })()
    `, 'purchase return post confirm')
    await invokeButton(cdp, '确定', 'purchase return post confirm button', messageBoxExpression('确认过账此采购退货单吗'))
    await waitForPage(cdp, `
      (() => {
        const row = ${purchaseReturnRowExpression};
        return row?.innerText.includes('已过账');
      })()
    `, 'purchase return posted row visible', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase return post'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const returnResponse = await fetch('/api/purchase/returns/' + ${JSON.stringify(purchaseReturn.id)}, { headers });
        if (!returnResponse.ok) return false;
        const returnPayload = await returnResponse.json();
        const detail = returnPayload?.data;
        const orderResponse = await fetch('/api/purchase/orders/' + ${JSON.stringify(fixture.order.id)}, { headers });
        if (!orderResponse.ok) return false;
        const orderPayload = await orderResponse.json();
        const orderDetail = orderPayload?.data;
        const payableResponse = await fetch('/api/finance/payables?pageNo=1&pageSize=20&supplierId=' + encodeURIComponent(${JSON.stringify(fixture.supplier.id)}), { headers });
        if (!payableResponse.ok) return false;
        const payablePayload = await payableResponse.json();
        const payables = payablePayload?.data?.records || [];
        const balanceResponse = await fetch('/api/inventory/balances?pageNo=1&pageSize=20&warehouseId=' + encodeURIComponent(${JSON.stringify(String(fixture.warehouse.id))}) + '&productId=' + encodeURIComponent(${JSON.stringify(String(fixture.product.id))}), { headers });
        if (!balanceResponse.ok) return false;
        const balancePayload = await balanceResponse.json();
        const balance = (balancePayload?.data?.records || []).find((item) => String(item.warehouseId) === ${JSON.stringify(String(fixture.warehouse.id))} && String(item.productId) === ${JSON.stringify(String(fixture.product.id))});
        const line = detail?.lines?.[0];
        const orderLine = (orderDetail?.lines || orderDetail?.items || [])[0];
        return detail?.status === 'POSTED'
          && Number(line?.qty) === ${returnQty}
          && Number(line?.returnedQty) === ${returnQty}
          && Number(line?.availableReturnQty) === ${fixture.qty - returnQty}
          && orderDetail?.receiptStatus === 'PARTIAL_RECEIVED'
          && Number(orderLine?.receivedQty) === ${fixture.qty - returnQty}
          && payables.some((item) =>
            item.sourceType === 'PURCHASE_RETURN'
              && item.sourceNo === detail.returnNo
              && item.direction === 'DECREASE'
              && item.status === 'OFFSET'
          )
          && Number(balance?.qtyOnHand) === ${fixture.qty - returnQty}
          && Number(balance?.qtyAvailable) === ${fixture.qty - returnQty}
          && Number(balance?.qtyReserved) === 0;
      })()
    `, 'purchase return posted, payable offset, and stock reduced', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase return persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'purchase return workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'purchase-return-create-post-stock-payable-offset',
    orderNo: fixture.order.orderNo,
    receiptNo: fixture.receipt.receiptNo,
    returnNo: purchaseReturn?.returnNo || null,
    supplierCode: fixture.supplier.supplierCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function runPurchaseToPaymentWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await preparePurchaseOrderFixture(auth)
  const submitterCredentials = { username: 'runtime_smoke', password: 'RuntimeSmoke123' }
  const submitterAuth = await loginWithCredentials(submitterCredentials)
  const orderRemark = `UI smoke 采购订单 ${fixture.suffix}`
  const receiptRemark = `UI smoke 采购收货 ${fixture.suffix}`
  const paymentRemark = `UI smoke 采购付款 ${fixture.suffix}`
  let order = null
  let receipt = null
  let payment = null

  try {
    await setBrowserAuth(cdp, submitterAuth)
    await markSmokeStep(cdp, 'purchase-order:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/purchase/orders' })
    await waitForPage(cdp, `
      document.body.innerText.includes('采购订单管理')
        && document.body.innerText.includes('新增订单')
    `, 'purchase order page')

    await markSmokeStep(cdp, 'purchase-order:open-create-dialog')
    await clickButton(cdp, '新增订单')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增采购订单')});
      })()
    `, 'purchase create dialog')

    const orderDialog = `(${dialogExpression('新增采购订单')} || document.createElement('div'))`
    await markSmokeStep(cdp, 'purchase-order:select-supplier')
    await clickElement(cdp, `${orderDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'purchase supplier select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.supplier.supplierName)}))`,
      'purchase supplier option'
    )
    await markSmokeStep(cdp, 'purchase-order:set-dates')
    await setElementValue(cdp, `${orderDialog}.querySelectorAll('.el-date-editor input')[0]`, fixture.orderDate, 'purchase order date input')
    await setElementValue(cdp, `${orderDialog}.querySelectorAll('.el-date-editor input')[1]`, fixture.deliveryDate, 'purchase delivery date input')
    await setElementValue(cdp, `${orderDialog}.querySelector('textarea')`, orderRemark, 'purchase order remark input')

    await markSmokeStep(cdp, 'purchase-order:add-line')
    await clickButton(cdp, '添加商品', orderDialog)
    await clickElement(cdp, `${orderDialog}.querySelectorAll('.el-table__body-wrapper .el-select .el-select__wrapper')[0]`, 'purchase product select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.product.productCode)}))`,
      'purchase product option'
    )
    await setElementValue(cdp, `${orderDialog}.querySelectorAll('.el-input-number input')[0]`, String(fixture.qty), 'purchase qty input')
    await setElementValue(cdp, `${orderDialog}.querySelectorAll('.el-input-number input')[1]`, String(fixture.price), 'purchase price input')
    await setElementValue(cdp, `${orderDialog}.querySelector('.el-table__body-wrapper input[placeholder="选填"]')`, 'UI smoke 采购明细', 'purchase line remark input')
    await clickButton(cdp, '确定', orderDialog)

    await markSmokeStep(cdp, 'purchase-order:wait-created')
    order = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/purchase/orders?pageNo=1&pageSize=20&supplierId=' + encodeURIComponent(${JSON.stringify(fixture.supplier.id)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(orderRemark)});
        if (!record?.id || record.status !== 'DRAFT' || record.approvalStatus !== 'NOT_SUBMITTED') return false;
        return record;
      })()
    `, 'purchase order created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase order create'))}`)
    })

    await markSmokeStep(cdp, 'purchase-order:search-created-order')
    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入订单编号"]')`, order.orderNo, 'purchase order no search input')
    await clickButton(cdp, '搜索')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .some((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}) && item.innerText.includes('草稿'));
      })()
    `, 'purchase draft row visible')

    await markSmokeStep(cdp, 'purchase-order:submit')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '提交')`,
      'purchase submit button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认提交订单')});
      })()
    `, 'purchase submit confirm')
    await invokeButton(cdp, '确定', 'purchase submit confirm button', messageBoxExpression('确认提交订单'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/purchase/orders/' + ${JSON.stringify(order.id)}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const detail = payload?.data;
        return detail?.status === 'SUBMITTED' && detail.approvalStatus === 'IN_APPROVAL';
      })()
    `, 'purchase order submitted', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase submit'))}`)
    })

    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'purchase-order:approve-as-admin')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/purchase/orders' })
    await waitForPage(cdp, `document.body.innerText.includes('采购订单管理')`, 'purchase order page as approver')
    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入订单编号"]')`, order.orderNo, 'purchase order no search input after submit')
    await clickButton(cdp, '搜索')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
        return row && [...row.querySelectorAll('button')].some((button) => visible(button) && button.innerText.trim() === '审核');
      })()
    `, 'purchase approve button visible', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase submit row'))}`)
    })
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '审核')`,
      'purchase approve button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认审核通过订单')});
      })()
    `, 'purchase approve confirm')
    await invokeButton(cdp, '确定', 'purchase approve confirm button', messageBoxExpression('确认审核通过订单'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/purchase/orders/' + ${JSON.stringify(order.id)}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const detail = payload?.data;
        const line = detail?.lines?.[0];
        return detail?.status === 'APPROVED'
          && detail.approvalStatus === 'APPROVED'
          && Number(detail.totalQuantity) === ${fixture.qty}
          && Number(detail.totalAmount) === ${fixture.qty * fixture.price}
          && line?.productId === ${JSON.stringify(fixture.product.id)}
          && Number(line.qty) === ${fixture.qty}
          && Number(line.price) === ${fixture.price};
      })()
    `, 'purchase order persisted as approved', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase persistence check'))}`)
    })

    await markSmokeStep(cdp, 'purchase-receipt:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/purchase/receipts' })
    await waitForPage(cdp, `
      document.body.innerText.includes('采购收货管理')
        && document.body.innerText.includes('新增收货')
    `, 'purchase receipt page')
    await clickButton(cdp, '新增收货')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增采购收货')});
      })()
    `, 'purchase receipt create dialog')
    const receiptDialog = `(${dialogExpression('新增采购收货')} || document.createElement('div'))`
    await clickElement(cdp, `${receiptDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'purchase receipt order select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}))`,
      'purchase receipt order option'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return ${receiptDialog}.innerText.includes(${JSON.stringify(String(fixture.qty))});
      })()
    `, 'purchase receipt lines loaded')
    await setElementValue(cdp, `${receiptDialog}.querySelectorAll('.el-date-editor input')[0]`, fixture.orderDate, 'purchase receipt date input')
    await clickElement(cdp, `${receiptDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'purchase receipt warehouse select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.warehouse.warehouseName)}))`,
      'purchase receipt warehouse option'
    )
    await setElementValue(cdp, `${receiptDialog}.querySelector('textarea')`, receiptRemark, 'purchase receipt remark input')
    await clickButton(cdp, '确定', receiptDialog)

    receipt = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/purchase/receipts?pageNo=1&pageSize=20&orderId=' + encodeURIComponent(${JSON.stringify(order.id)}), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(receiptRemark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        return record;
      })()
    `, 'purchase receipt created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase receipt create'))}`)
    })

    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入收货单号"]')`, receipt.receiptNo, 'purchase receipt no search input')
    await clickButton(cdp, '搜索')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(receipt.receiptNo)}));
        return row && [...row.querySelectorAll('button')].some((button) => visible(button) && button.innerText.trim() === '过账');
      })()
    `, 'purchase receipt post button visible', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase receipt row'))}`)
    })
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '过账')`,
      'purchase receipt post button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认过账收货单')});
      })()
    `, 'purchase receipt post confirm')
    await invokeButton(cdp, '确定', 'purchase receipt post confirm button', messageBoxExpression('确认过账收货单'))

    const payable = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const receiptResponse = await fetch('/api/purchase/receipts/' + ${JSON.stringify(receipt.id)}, { headers });
        if (!receiptResponse.ok) return false;
        const receiptPayload = await receiptResponse.json();
        const receiptDetail = receiptPayload?.data;
        const orderResponse = await fetch('/api/purchase/orders/' + ${JSON.stringify(order.id)}, { headers });
        if (!orderResponse.ok) return false;
        const orderPayload = await orderResponse.json();
        const orderDetail = orderPayload?.data;
        const payableResponse = await fetch('/api/finance/payables?pageNo=1&pageSize=20&supplierId=' + encodeURIComponent(${JSON.stringify(fixture.supplier.id)}), { headers });
        if (!payableResponse.ok) return false;
        const payablePayload = await payableResponse.json();
        const payables = payablePayload?.data?.records || [];
        const expectedPayableAmount = Number(receiptDetail?.totalAmount || 0) + Number(receiptDetail?.totalTaxAmount || 0);
        const payable = payables.find((item) =>
          item.sourceNo === receiptDetail?.receiptNo
            && Math.abs(Number(item.remainingAmount) - expectedPayableAmount) < 0.001
            && item.status === 'UNSETTLED'
        );
        if (receiptDetail?.status !== 'POSTED' || orderDetail?.receiptStatus !== 'RECEIVED' || !payable?.id) return false;
        return payable;
      })()
    `, 'purchase receipt posted and payable created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase receipt post'))}`)
    })

    await markSmokeStep(cdp, 'purchase-payment:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/payments' })
    await waitForPage(cdp, `document.body.innerText.includes('收款管理') && document.body.innerText.includes('付款管理')`, 'finance payments page')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-tabs__item')].find((item) => visible(item) && item.innerText.includes('付款管理'))`,
      'payments tab'
    )
    await waitForPage(cdp, `document.body.innerText.includes('新增付款')`, 'payments tab active')
    await clickButton(cdp, '新增付款')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增付款')});
      })()
    `, 'payment create dialog')
    const paymentDialog = `(${dialogExpression('新增付款')} || document.createElement('div'))`
    await clickElement(cdp, `${paymentDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'payment supplier select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.supplier.supplierName)}))`,
      'payment supplier option'
    )
    await clickElement(cdp, `${paymentDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'payment payable select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(payable.payableNo)}))`,
      'payment payable option'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const amountInput = ${paymentDialog}.querySelector('.el-input-number input');
        return amountInput && Number(amountInput.value) > 0;
      })()
    `, 'payment amount filled')
    await setElementValue(cdp, `${paymentDialog}.querySelector('textarea')`, paymentRemark, 'payment remark input')
    await clickButton(cdp, '确定', paymentDialog)

    payment = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/finance/payments?pageNo=1&pageSize=20&supplierId=' + encodeURIComponent(${JSON.stringify(fixture.supplier.id)}), { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => item.remark === ${JSON.stringify(paymentRemark)});
        if (!record?.id || record.status !== 'POSTED') return false;
        const payableResponse = await fetch('/api/finance/payables/' + ${JSON.stringify(payable.id)}, { headers });
        if (!payableResponse.ok) return false;
        const payablePayload = await payableResponse.json();
        const payableDetail = payablePayload?.data;
        if (payableDetail?.status !== 'SETTLED' || Number(payableDetail.remainingAmount) !== 0) return false;
        return record;
      })()
    `, 'payment created and payable settled', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after payment create'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'purchase to payment workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'purchase-order-receipt-payment-settle-payable',
    orderNo: order?.orderNo || null,
    receiptNo: receipt?.receiptNo || null,
    paymentNo: payment?.paymentNo || null,
    supplierCode: fixture.supplier.supplierCode,
    productCode: fixture.product.productCode,
    passed: failures.length === 0,
    failures
  }
}

async function runProductionOrderWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareProductionOrderFixture(auth)
  const remark = `UI smoke 生产订单 ${fixture.suffix}`
  let order = null
  try {
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/production/orders' })
    await waitForPage(cdp, `
      document.body.innerText.includes('生产订单管理')
        && document.body.innerText.includes('新增订单')
    `, 'production order page')

    await clickButton(cdp, '新增订单')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增生产订单')});
      })()
    `, 'production create dialog')

    const createDialog = dialogExpression('新增生产订单')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[0].querySelector('.el-select__wrapper')`, 'production product select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.finishedProduct.productCode)}))`,
      'production product option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.finishedProduct.productCode), 'production product selected')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[1].querySelector('.el-select__wrapper')`, 'production bom select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.bom.bomNo)}))`,
      'production bom option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.bom.bomNo), 'production bom selected')
    await setElementValue(cdp, `${createDialog}.querySelector('.el-input-number input')`, String(fixture.plannedQty), 'production planned qty input')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[2].querySelector('.el-select__wrapper')`, 'production material warehouse select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.materialWarehouse.warehouseName)}))`,
      'production material warehouse option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.materialWarehouse.warehouseName), 'production material warehouse selected')
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select')[3].querySelector('.el-select__wrapper')`, 'production finished warehouse select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.finishedWarehouse.warehouseName)}))`,
      'production finished warehouse option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.finishedWarehouse.warehouseName), 'production finished warehouse selected')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('input[placeholder="请选择日期"]')[0]`, fixture.plannedStartDate, 'production start date input')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('input[placeholder="请选择日期"]')[1]`, fixture.plannedFinishDate, 'production finish date input')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'production remark input')
    await evaluatePage(cdp, `
      (() => {
        window.__uiSmoke = {
          ...(window.__uiSmoke || {}),
          productionCreateRequests: []
        };
        if (!window.__uiSmokeProductionCreatePatched) {
          window.__uiSmokeProductionCreatePatched = true;
          const rawFetch = window.fetch;
          window.fetch = async (...args) => {
            const url = String(args[0]);
            const method = String(args[1]?.method || 'GET').toUpperCase();
            const shouldRecord = url.includes('/api/production/orders') && method === 'POST' && !url.includes('/release') && !url.includes('/issue') && !url.includes('/complete');
            const record = shouldRecord ? { type: 'fetch', url, status: null, responseText: '', done: false } : null;
            if (record) window.__uiSmoke.productionCreateRequests.push(record);
            const response = await rawFetch(...args);
            if (record) {
              record.status = response.status;
              record.responseText = await response.clone().text();
              record.done = true;
            }
            return response;
          };
          const rawOpen = XMLHttpRequest.prototype.open;
          const rawSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(method, url, ...rest) {
            this.__uiSmokeProductionCreateRecord = null;
            const normalizedMethod = String(method || 'GET').toUpperCase();
            if (String(url).includes('/api/production/orders') && normalizedMethod === 'POST'
              && !String(url).includes('/release') && !String(url).includes('/issue') && !String(url).includes('/complete')) {
              this.__uiSmokeProductionCreateRecord = { type: 'xhr', method: normalizedMethod, url: String(url), status: null, responseText: '', done: false };
              window.__uiSmoke.productionCreateRequests.push(this.__uiSmokeProductionCreateRecord);
              this.addEventListener('loadend', () => {
                this.__uiSmokeProductionCreateRecord.status = this.status;
                this.__uiSmokeProductionCreateRecord.responseText = String(this.responseText || '').slice(0, 3000);
                this.__uiSmokeProductionCreateRecord.done = true;
              });
            }
            return rawOpen.call(this, method, url, ...rest);
          };
          XMLHttpRequest.prototype.send = function(...args) {
            return rawSend.apply(this, args);
          };
        }
        return true;
      })()
    `)
    await clickButton(cdp, '保存', createDialog)

    order = await waitForPage(cdp, `
      (async () => {
        const createRecord = (window.__uiSmoke?.productionCreateRequests || []).find((request) => request.done);
        if (!createRecord) return false;
        let createPayload = null;
        try {
          createPayload = JSON.parse(createRecord.responseText || '{}');
        } catch (error) {
          return false;
        }
        if (createRecord.status < 200 || createRecord.status >= 300 || createPayload?.code !== '0' || !createPayload?.data?.id) {
          return false;
        }
        const created = createPayload.data;
        const token = localStorage.getItem('token');
        const response = await fetch('/api/production/orders/' + encodeURIComponent(created.id), {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = payload?.data;
        if (!record?.id || record.status !== 'DRAFT') return false;
        if (String(record.materialWarehouseId) !== ${JSON.stringify(String(fixture.materialWarehouse.id))}) return false;
        if (String(record.finishedWarehouseId) !== ${JSON.stringify(String(fixture.finishedWarehouse.id))}) return false;
        if (String(record.materialWarehouseId) === String(record.finishedWarehouseId)) return false;
        return record;
      })()
    `, 'production order created', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after production order create'))}`)
    })

    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入订单号"]')`, order.orderNo, 'production order no search input')
    await clickButton(cdp, '查询')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}));
        return row?.innerText.includes('草稿')
          && row?.innerText.includes(${JSON.stringify(fixture.materialWarehouse.warehouseName)})
          && row?.innerText.includes(${JSON.stringify(fixture.finishedWarehouse.warehouseName)});
      })()
    `, 'production draft row visible')

    const orderRowExpression = `
      [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(order.orderNo)}))
    `
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '下达')`,
      'production release button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('下达生产订单')});
      })()
    `, 'production release confirm')
    await clickButton(cdp, '确定', messageBoxExpression('下达生产订单'))
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${orderRowExpression};
        return row?.innerText.includes('已下达');
      })()
    `, 'production order released', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after production release'))}`)
    })

    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '领料')`,
      'production issue button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('按剩余需求领料')});
      })()
    `, 'production issue confirm')
    await clickButton(cdp, '确定', messageBoxExpression('按剩余需求领料'))
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${orderRowExpression};
        return row?.innerText.includes('已领料');
      })()
    `, 'production order issued', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after production issue'))}`)
    })

    await invokeElement(
      cdp,
      `[...document.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '完工')`,
      'production complete button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('生产完工')});
      })()
    `, 'production complete dialog')
    const completeDialog = dialogExpression('生产完工')
    await setElementValue(cdp, `${completeDialog}.querySelector('textarea')`, 'UI smoke 生产完工', 'production complete remark input')
    await clickButton(cdp, '确定完工', completeDialog)
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${orderRowExpression};
        return !${dialogExpression('生产完工')} && row?.innerText.includes('已完成');
      })()
    `, 'production order completed', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after production complete'))}`)
    })

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/production/orders/' + ${JSON.stringify(order.id)}, {
          headers: token ? { Authorization: 'Bearer ' + token } : {}
        });
        if (!response.ok) return false;
        const payload = await response.json();
        const detail = payload?.data;
        const material = detail?.materials?.[0];
        const findBalance = async (warehouseId, productId) => {
          const balanceResponse = await fetch('/api/inventory/balances?pageNo=1&pageSize=20&warehouseId=' + encodeURIComponent(warehouseId) + '&productId=' + encodeURIComponent(productId), {
            headers: token ? { Authorization: 'Bearer ' + token } : {}
          });
          if (!balanceResponse.ok) return null;
          const balancePayload = await balanceResponse.json();
          const records = balancePayload?.data?.records || [];
          return records.find((item) => String(item.warehouseId) === String(warehouseId) && String(item.productId) === String(productId)) || null;
        };
        const materialBalance = await findBalance(${JSON.stringify(String(fixture.materialWarehouse.id))}, ${JSON.stringify(String(fixture.materialProduct.id))});
        const finishedBalance = await findBalance(${JSON.stringify(String(fixture.finishedWarehouse.id))}, ${JSON.stringify(String(fixture.finishedProduct.id))});
        return detail?.status === 'COMPLETED'
          && String(detail.materialWarehouseId) === ${JSON.stringify(String(fixture.materialWarehouse.id))}
          && String(detail.finishedWarehouseId) === ${JSON.stringify(String(fixture.finishedWarehouse.id))}
          && Number(detail.completedQty) === ${fixture.plannedQty}
          && String(material?.materialProductId) === ${JSON.stringify(String(fixture.materialProduct.id))}
          && Number(material.issuedQty) === ${fixture.requiredMaterialQty}
          && Number(materialBalance?.qtyOnHand) === 10
          && Number(materialBalance?.qtyReserved) === 0
          && Number(materialBalance?.qtyAvailable) === 10
          && Number(finishedBalance?.qtyOnHand) === ${fixture.plannedQty};
      })()
    `, 'production order persisted as completed', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after production persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'production workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'production-order-create-release-issue-complete',
    orderNo: order?.orderNo || null,
    bomNo: fixture.bom.bomNo,
    materialWarehouse: fixture.materialWarehouse.warehouseName,
    finishedWarehouse: fixture.finishedWarehouse.warehouseName,
    passed: failures.length === 0,
    failures
  }
}

async function killProcessTree(pid) {
  if (!pid) {
    return
  }
  await new Promise((resolveKill) => {
    const killer = spawn('taskkill.exe', ['/PID', String(pid), '/T', '/F'], {
      stdio: 'ignore',
      windowsHide: true
    })
    killer.on('exit', resolveKill)
    killer.on('error', resolveKill)
  })
}

async function runManualVoucherWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const suffix = String(Date.now()).slice(-10)
  const bizDate = new Date().toISOString().slice(0, 10)
  const remark = `UI smoke 手工凭证 ${suffix}`
  const cancelReason = `UI smoke 作废红冲 ${suffix}`
  const amount = 128
  let voucher = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `

  try {
    await markSmokeStep(cdp, 'manual-voucher:prepare-subjects')
    await ensureOpenPeriod(auth, bizDate)
    const debitSubject = await apiRequest(auth, 'POST', '/finance/account-subjects', {
      subjectCode: `66${suffix.slice(-6)}`,
      subjectName: `UI手工借方科目${suffix}`,
      subjectType: 'EXPENSE',
      balanceDirection: 'DEBIT',
      remark: 'UI smoke 手工凭证借方科目'
    })
    const creditSubject = await apiRequest(auth, 'POST', '/finance/account-subjects', {
      subjectCode: `22${suffix.slice(-6)}`,
      subjectName: `UI手工贷方科目${suffix}`,
      subjectType: 'LIABILITY',
      balanceDirection: 'CREDIT',
      remark: 'UI smoke 手工凭证贷方科目'
    })

    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'manual-voucher:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/vouchers/manual' })
    await waitForPage(cdp, `
      document.body.innerText.includes('手工凭证')
        && document.body.innerText.includes('录入凭证')
    `, 'manual voucher page', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after manual voucher navigate'))}`)
    })

    await markSmokeStep(cdp, 'manual-voucher:create')
    await clickButton(cdp, '录入凭证')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('录入手工凭证')}); })()`, 'manual voucher create dialog')
    const createDialog = dialogExpression('录入手工凭证')
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="选择凭证日期"]')`, bizDate, 'manual voucher date input')
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="整张凭证的摘要"]')`, remark, 'manual voucher remark input')

    // 第 1 行:借方科目 + 借方金额
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-table__body-wrapper tbody tr')[0].querySelector('.el-select__wrapper')`, 'manual voucher row0 subject select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(debitSubject.subjectCode)}))`,
      'manual voucher row0 subject option'
    )
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-table__body-wrapper tbody tr')[0].querySelectorAll('.el-input-number input')[0]`, String(amount), 'manual voucher row0 debit input')

    // 第 2 行:贷方科目 + 贷方金额
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-table__body-wrapper tbody tr')[1].querySelector('.el-select__wrapper')`, 'manual voucher row1 subject select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(creditSubject.subjectCode)}))`,
      'manual voucher row1 subject option'
    )
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-table__body-wrapper tbody tr')[1].querySelectorAll('.el-input-number input')[1]`, String(amount), 'manual voucher row1 credit input')

    await waitForPage(cdp, `(() => { ${visibleDefinition}; const d = ${createDialog}; return Boolean(d) && d.innerText.includes('借贷平衡'); })()`, 'manual voucher balanced tag')
    await clickButton(cdp, '保存草稿', createDialog)

    await markSmokeStep(cdp, 'manual-voucher:wait-draft')
    voucher = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/finance/vouchers/manual?pageNo=1&pageSize=20&status=DRAFT', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = (payload?.data?.records || []).find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        if (Number(record.amount) !== ${amount}) return false;
        return record;
      })()
    `, 'manual voucher created as draft', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after manual voucher create'))}`)
    })

    const voucherRowButton = (label) => `(() => {
      const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(voucher.voucherNo)}));
      return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === ${JSON.stringify(label)});
    })()`
    await waitForPage(cdp, `(() => { ${visibleDefinition}; const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(voucher.voucherNo)})); return row?.innerText.includes('草稿'); })()`, 'manual voucher draft row visible')

    // 提交 -> PENDING
    await markSmokeStep(cdp, 'manual-voucher:submit')
    await invokeElement(cdp, voucherRowButton("提交"), 'manual voucher submit button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('提交审批')}); })()`, 'manual voucher submit prompt')
    await clickButton(cdp, '确定', messageBoxExpression('提交审批'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/vouchers/manual/' + ${JSON.stringify(String(voucher.id))}, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
        if (!response.ok) return false;
        return (await response.json())?.data?.status === 'PENDING';
      })()
    `, 'manual voucher submitted', 30000)

    // 审批 -> APPROVED
    await markSmokeStep(cdp, 'manual-voucher:approve')
    await invokeElement(cdp, voucherRowButton("审批"), 'manual voucher approve button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('审批凭证')}); })()`, 'manual voucher approve prompt')
    await clickButton(cdp, '确定', messageBoxExpression('审批凭证'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/vouchers/manual/' + ${JSON.stringify(String(voucher.id))}, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
        if (!response.ok) return false;
        return (await response.json())?.data?.status === 'APPROVED';
      })()
    `, 'manual voucher approved', 30000)

    // 过账 -> POSTED
    await markSmokeStep(cdp, 'manual-voucher:post')
    await invokeElement(cdp, voucherRowButton("过账"), 'manual voucher post button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('过账凭证')}); })()`, 'manual voucher post prompt')
    await clickButton(cdp, '确定', messageBoxExpression('过账凭证'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/vouchers/manual/' + ${JSON.stringify(String(voucher.id))}, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
        if (!response.ok) return false;
        const record = (await response.json())?.data;
        return record?.status === 'POSTED' && record?.postedVoucherId;
      })()
    `, 'manual voucher posted with generated voucher', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after manual voucher post'))}`)
    })

    // 作废 -> CANCELLED + 红冲凭证
    await markSmokeStep(cdp, 'manual-voucher:cancel')
    await invokeElement(cdp, voucherRowButton("作废"), 'manual voucher cancel button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('作废凭证')}); })()`, 'manual voucher cancel dialog')
    const cancelDialog = dialogExpression('作废凭证')
    await setElementValue(cdp, `${cancelDialog}.querySelector('textarea')`, cancelReason, 'manual voucher cancel reason input')
    await clickButton(cdp, '确定作废', cancelDialog)
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/vouchers/manual/' + ${JSON.stringify(String(voucher.id))}, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
        if (!response.ok) return false;
        const record = (await response.json())?.data;
        return record?.status === 'CANCELLED'
          && record?.postedVoucherId
          && record?.reversalVoucherId
          && record?.cancelReason === ${JSON.stringify(cancelReason)};
      })()
    `, 'manual voucher cancelled with reversal voucher', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after manual voucher cancel'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'manual voucher workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'manual-voucher-create-submit-approve-post-cancel',
    voucherNo: voucher?.voucherNo || null,
    passed: failures.length === 0,
    failures
  }
}

async function runQcInspectionWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareDraftPurchaseReceiptForQcFixture(auth)
  const remark = `UI smoke 来料检验 ${fixture.suffix}`
  let inspection = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `

  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'qc-inspection:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/qc/inspections' })
    await waitForPage(cdp, `document.body.innerText.includes('新建检验单')`, 'qc inspection page', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after qc inspection navigate'))}`)
    })

    await markSmokeStep(cdp, 'qc-inspection:create')
    await clickButton(cdp, '新建检验单')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('新建检验单')}); })()`, 'qc inspection create dialog')
    const createDialog = dialogExpression('新建检验单')
    // 默认 IQC；等草稿采购入库单选项加载完成
    await clickElement(cdp, `${createDialog}.querySelectorAll('.el-select__wrapper')[0]`, 'qc inspection receipt select')
    await invokeElement(
      cdp,
      `[...document.querySelectorAll('.el-select-dropdown__item')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(fixture.receipt.receiptNo)}))`,
      'qc inspection receipt option'
    )
    await waitForPage(cdp, visibleTextExpression(createDialog, fixture.receipt.receiptNo), 'qc inspection receipt selected')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'qc inspection remark input')
    await clickButton(cdp, '确定', createDialog)

    await markSmokeStep(cdp, 'qc-inspection:wait-draft')
    inspection = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/qc/inspections?pageNo=1&pageSize=20&status=DRAFT', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = (payload?.data?.records || []).find((item) => String(item.receiptId) === ${JSON.stringify(String(fixture.receipt.id))});
        if (!record?.id || record.status !== 'DRAFT') return false;
        if (Number(record.totalQty) !== ${fixture.qty}) return false;
        return record;
      })()
    `, 'qc inspection created as draft', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after qc inspection create'))}`)
    })

    const inspectionRowButton = (label) => `(() => {
      const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(inspection.inspectionNo)}));
      return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === ${JSON.stringify(label)});
    })()`
    await waitForPage(cdp, `(() => { ${visibleDefinition}; const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')].find((item) => visible(item) && item.innerText.includes(${JSON.stringify(inspection.inspectionNo)})); return row?.innerText.includes('草稿'); })()`, 'qc inspection draft row visible')

    // 提交 -> SUBMITTED
    await markSmokeStep(cdp, 'qc-inspection:submit')
    await invokeElement(cdp, inspectionRowButton("提交"), 'qc inspection submit button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('提示')}); })()`, 'qc inspection submit prompt')
    await clickButton(cdp, '确定', messageBoxExpression('提示'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/qc/inspections/' + ${JSON.stringify(String(inspection.id))}, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
        if (!response.ok) return false;
        return (await response.json())?.data?.status === 'SUBMITTED';
      })()
    `, 'qc inspection submitted', 30000)

    // 判定 -> JUDGED,合格 3 / 不合格 1
    await markSmokeStep(cdp, 'qc-inspection:judge')
    await invokeElement(cdp, inspectionRowButton("判定"), 'qc inspection judge button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('判定检验单')}); })()`, 'qc inspection judge dialog')
    const judgeDialog = dialogExpression('判定检验单')
    await setElementValue(cdp, `${judgeDialog}.querySelectorAll('.el-table__body-wrapper tbody tr')[0].querySelectorAll('.el-input-number input')[0]`, String(fixture.qualifiedQty), 'qc inspection qualified input')
    await setElementValue(cdp, `${judgeDialog}.querySelectorAll('.el-table__body-wrapper tbody tr')[0].querySelectorAll('.el-input-number input')[1]`, String(fixture.unqualifiedQty), 'qc inspection unqualified input')
    await clickButton(cdp, '确认判定', judgeDialog)

    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/qc/inspections/' + ${JSON.stringify(String(inspection.id))}, { headers });
        if (!response.ok) return false;
        const record = (await response.json())?.data;
        if (record?.status !== 'JUDGED') return false;
        if (Number(record?.qualifiedQty) !== ${fixture.qualifiedQty}) return false;
        if (Number(record?.unqualifiedQty) !== ${fixture.unqualifiedQty}) return false;
        // 仅合格品入库:引用的草稿入库单行数量已回写为合格数量
        const receiptResponse = await fetch('/api/purchase/receipts/' + ${JSON.stringify(String(fixture.receipt.id))}, { headers });
        if (!receiptResponse.ok) return false;
        const receipt = (await receiptResponse.json())?.data;
        const line = (receipt?.lines || []).find((item) => String(item.id) === ${JSON.stringify(String(fixture.receiptLine.id))});
        return line && Number(line.qty) === ${fixture.qualifiedQty};
      })()
    `, 'qc inspection judged and only-qualified rewritten to draft receipt', 30000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after qc inspection judge'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'qc inspection workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'qc-inspection-create-submit-judge-only-qualified',
    inspectionNo: inspection?.inspectionNo || null,
    receiptNo: fixture.receipt.receiptNo,
    passed: failures.length === 0,
    failures
  }
}

async function runSystemConfigCreateWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const suffix = Date.now().toString().slice(-9)
  const configKey = `ui.smoke.config.${suffix}`
  const configName = `UI smoke 配置 ${suffix}`
  const configValue = `value-${suffix}`
  const description = `UI smoke 系统配置新增 ${suffix}`
  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'system-config:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/system/configs' })
    await waitForPage(cdp, `
      document.body.innerText.includes('系统配置管理')
        && document.body.innerText.includes('新增配置')
    `, 'system config page')

    await markSmokeStep(cdp, 'system-config:open-create-dialog')
    await clickButton(cdp, '新增配置')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${dialogExpression('新增配置')});
      })()
    `, 'system config create dialog')

    const dialog = `(${dialogExpression('新增配置')} || document.createElement('div'))`
    await markSmokeStep(cdp, 'system-config:fill-form')
    // 弹窗字段：配置键/配置名称为 el-input(2 个 input)，配置值/描述为 type="textarea"(2 个 textarea)
    await setElementValue(cdp, `${dialog}.querySelectorAll('.el-input input')[0]`, configKey, 'system config key input')
    await setElementValue(cdp, `${dialog}.querySelectorAll('.el-input input')[1]`, configName, 'system config name input')
    await setElementValue(cdp, `${dialog}.querySelectorAll('textarea')[0]`, configValue, 'system config value input')
    await setElementValue(cdp, `${dialog}.querySelectorAll('textarea')[1]`, description, 'system config description input')
    await clickButton(cdp, '保存', dialog)

    await markSmokeStep(cdp, 'system-config:wait-created')
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return !${dialogExpression('新增配置')}
          && [...document.querySelectorAll('.el-message')].some((item) => visible(item) && item.innerText.includes('创建成功'));
      })()
    `, 'system config created message').catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after system config create'))}`)
    })

    await markSmokeStep(cdp, 'system-config:verify-persisted')
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        // 后端原始字段为 configCode(前端 normalize 才映射成 configKey)；直查生 API 需兼容两者
        const response = await fetch('/api/system/configs?pageNo=1&pageSize=100&keyword=' + encodeURIComponent(${JSON.stringify(configKey)}), { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const record = records.find((item) => (item.configKey || item.configCode) === ${JSON.stringify(configKey)});
        return Boolean(record?.id)
          && record.configValue === ${JSON.stringify(configValue)}
          && record.status === 'ACTIVE';
      })()
    `, 'system config persisted', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after system config persistence check'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'system config workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'system-config-create',
    configKey,
    passed: failures.length === 0,
    failures
  }
}

async function runFinanceInvoiceWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const suffix = Date.now().toString().slice(-9)
  const partnerName = `UI发票单位${suffix}`
  const remark = `UI smoke 发票登记 ${suffix}`
  const amount = 128.5
  const taxAmount = 16.7
  const invoiceDate = new Date().toISOString().slice(0, 10)
  let invoice = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `
  try {
    await ensureOpenPeriod(auth, invoiceDate)
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'finance-invoice:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/finance/invoices' })
    await waitForPage(cdp, `document.body.innerText.includes('发票登记') && document.body.innerText.includes('新增登记')`, 'finance invoice page', 30000)

    await markSmokeStep(cdp, 'finance-invoice:create')
    await clickButton(cdp, '新增登记')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${dialogExpression('新增登记')}); })()`, 'finance invoice create dialog')
    const createDialog = dialogExpression('新增登记')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-input input:not([readonly])')[0]`, partnerName, 'invoice partner input')
    await setElementValue(cdp, `${createDialog}.querySelector('input[placeholder="请选择日期"]')`, invoiceDate, 'invoice date input')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-input-number input')[0]`, String(amount), 'invoice amount input')
    await setElementValue(cdp, `${createDialog}.querySelectorAll('.el-input-number input')[1]`, String(taxAmount), 'invoice tax input')
    await setElementValue(cdp, `${createDialog}.querySelector('textarea')`, remark, 'invoice remark input')
    await clickButton(cdp, '保存', createDialog)

    await markSmokeStep(cdp, 'finance-invoice:wait-draft')
    invoice = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/finance/invoices?pageNo=1&pageSize=20&status=DRAFT', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const record = (payload?.data?.records || []).find((item) => item.remark === ${JSON.stringify(remark)});
        if (!record?.id || record.status !== 'DRAFT') return false;
        return record;
      })()
    `, 'finance invoice draft created', 30000)

    const rowButton = (label) => `(() => {
      const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(invoice.invoiceNo)}));
      return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === ${JSON.stringify(label)});
    })()`

    await markSmokeStep(cdp, 'finance-invoice:post')
    await invokeElement(cdp, rowButton('确认'), 'invoice post button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('提示')}); })()`, 'invoice post prompt')
    await clickButton(cdp, '确定', messageBoxExpression('提示'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/invoices/' + ${JSON.stringify(String(invoice.id))}, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
        if (!response.ok) return false;
        return (await response.json())?.data?.status === 'POSTED';
      })()
    `, 'finance invoice posted', 30000)

    await markSmokeStep(cdp, 'finance-invoice:cancel')
    await invokeElement(cdp, rowButton('作废'), 'invoice cancel button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('提示')}); })()`, 'invoice cancel prompt')
    await clickButton(cdp, '确定', messageBoxExpression('提示'))
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const response = await fetch('/api/finance/invoices/' + ${JSON.stringify(String(invoice.id))}, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
        if (!response.ok) return false;
        return (await response.json())?.data?.status === 'CANCELLED';
      })()
    `, 'finance invoice cancelled', 30000)
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'finance invoice workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'finance-invoice-create-post-cancel',
    invoiceNo: invoice?.invoiceNo || null,
    passed: failures.length === 0,
    failures
  }
}

async function runPurchaseInquiryConvertToPoWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const suffix = Date.now().toString().slice(-9)
  const orderDate = new Date().toISOString().slice(0, 10)
  let inquiry = null
  let purchaseOrder = null
  const visibleDefinition = `
    const visible = (element) => {
      if (!element) return false;
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
    };
  `
  try {
    await ensureOpenPeriod(auth, orderDate)
    const supplier = await apiRequest(auth, 'POST', '/masterdata/suppliers', {
      supplierCode: `UIINQS${suffix}`,
      supplierName: `UI询价供应商${suffix}`,
      contactName: 'UI smoke',
      contactPhone: '13900000011',
      settlementMethod: 'BANK_TRANSFER',
      address: 'UI smoke',
      remark: 'UI smoke inquiry supplier'
    })
    const product = await apiRequest(auth, 'POST', '/masterdata/products', {
      productCode: `UIINQP${suffix}`,
      productName: `UI询价商品${suffix}`,
      productType: 'GOODS',
      categoryName: 'UI询价联调',
      specification: 'UI smoke',
      unitName: '件',
      purchasePrice: 15,
      salePrice: 25,
      taxRate: 13,
      lotControlled: false,
      shelfLifeControlled: false,
      inspectionRequired: false,
      remark: 'UI smoke inquiry product'
    })

    inquiry = await apiRequest(auth, 'POST', '/purchase/inquiries', {
      inquiryDate: orderDate,
      title: `UI smoke 询价 ${suffix}`,
      remark: `UI smoke inquiry ${suffix}`,
      lines: [{ productId: product.id, qty: 6, remark: 'inquiry line' }]
    })
    await apiRequest(auth, 'POST', `/purchase/inquiries/${inquiry.id}/submit`)
    const quoted = await apiRequest(auth, 'POST', `/purchase/inquiries/${inquiry.id}/quotes`, {
      supplierId: supplier.id,
      unitPrice: 15.5,
      taxRate: 13,
      remark: 'quote'
    })
    const quote = (quoted.quotes || []).find((item) => String(item.supplierId) === String(supplier.id))
    if (!quote?.id) {
      throw new Error('inquiry quote missing after add')
    }
    inquiry = await apiRequest(auth, 'POST', `/purchase/inquiries/${inquiry.id}/select-quote`, { quoteId: quote.id })
    if (inquiry.status !== 'CLOSED') {
      throw new Error(`expected CLOSED inquiry, got ${inquiry.status}`)
    }

    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'purchase-inquiry:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/purchase/inquiries' })
    await waitForPage(cdp, `document.body.innerText.includes('采购询价') || document.body.innerText.includes('新建询价单')`, 'purchase inquiry page', 30000)
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return [...document.querySelectorAll('.el-table__body-wrapper tbody tr')].some((row) => visible(row) && row.innerText.includes(${JSON.stringify(inquiry.inquiryNo)})); })()`, 'inquiry row visible', 30000)

    const beforeOrders = await apiRequest(auth, 'GET', '/purchase/orders?pageNo=1&pageSize=5')
    const beforeIdList = JSON.stringify((beforeOrders.records || []).map((item) => String(item.id)))

    await markSmokeStep(cdp, 'purchase-inquiry:create-po')
    await invokeElement(cdp, `(() => {
      const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
        .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(inquiry.inquiryNo)}));
      return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.includes('生成采购订单'));
    })()`, 'inquiry create po button')
    await waitForPage(cdp, `(() => { ${visibleDefinition}; return Boolean(${messageBoxExpression('生成采购订单')}); })()`, 'inquiry create po confirm', 15000)
    await clickButton(cdp, '确定', messageBoxExpression('生成采购订单'))

    // 成功提示或后端新单任一即可；优先用供应商+询价单号回查，避免 keyword 不扫 remark
    await waitForPage(cdp, `
      (() => {
        ${visibleDefinition}
        return [...document.querySelectorAll('.el-message')].some((item) => visible(item) && item.innerText.includes('已创建采购订单草稿'));
      })()
    `, 'inquiry create po success message', 15000).catch(() => null)

    purchaseOrder = await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/purchase/orders?pageNo=1&pageSize=30', { headers });
        if (!response.ok) return false;
        const payload = await response.json();
        const records = payload?.data?.records || [];
        const beforeIds = ${beforeIdList};
        const match = records.find((item) => {
          const id = String(item.id);
          const remark = String(item.remark || '');
          const supplierMatch = String(item.supplierId) === ${JSON.stringify(String(supplier.id))};
          const remarkMatch = remark.includes(${JSON.stringify(inquiry.inquiryNo)});
          const isNew = !beforeIds.includes(id);
          return supplierMatch && (remarkMatch || isNew);
        });
        return match || false;
      })()
    `, 'purchase order created from inquiry', 30000)
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'purchase inquiry workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'purchase-inquiry-create-and-convert-to-po',
    inquiryNo: inquiry?.inquiryNo || null,
    orderNo: purchaseOrder?.orderNo || null,
    passed: failures.length === 0 && Boolean(purchaseOrder?.orderNo),
    failures: failures.length ? failures : (purchaseOrder?.orderNo ? [] : ['purchase order not created'])
  }
}

async function runPurchaseOrderUnapproveWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await preparePurchaseOrderFixture(auth)
  const submitterAuth = await loginWithCredentials({ username: 'runtime_smoke', password: 'RuntimeSmoke123' })
  const orderRemark = `UI smoke 反审核采购订单 ${fixture.suffix}`
  // API 造 APPROVED 采购订单(create -> submit -> approve),浏览器只做反审核动作与回查。
  const order = await apiRequest(submitterAuth, 'POST', '/purchase/orders', {
    supplierId: fixture.supplier.id,
    orderDate: fixture.orderDate,
    deliveryDate: fixture.deliveryDate,
    remark: orderRemark,
    lines: [
      { productId: fixture.product.id, qty: fixture.qty, price: fixture.price, taxRate: fixture.taxRate, remark: 'UI smoke 采购订单明细' }
    ]
  })
  await apiRequest(submitterAuth, 'POST', `/purchase/orders/${order.id}/submit`, { remark: 'UI smoke 提交采购订单' })
  const approvedOrder = await apiRequest(auth, 'POST', `/purchase/orders/${order.id}/approve`, { remark: 'UI smoke 审批采购订单' })
  if (approvedOrder.status !== 'APPROVED') {
    throw new Error(`purchase order ${approvedOrder.orderNo} not APPROVED before unapprove: ${approvedOrder.status}`)
  }

  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'purchase-order-unapprove:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/purchase/orders' })
    await waitForPage(cdp, `
      document.body.innerText.includes('采购订单管理')
        && document.body.innerText.includes('新增订单')
    `, 'purchase order page')

    await markSmokeStep(cdp, 'purchase-order-unapprove:search')
    await setElementValue(cdp, `document.querySelector('input[placeholder="请输入订单编号"]')`, approvedOrder.orderNo, 'purchase order no search input')
    // 采购订单页搜索按钮文案是「搜索」，不是多数列表页的「查询」
    await clickButton(cdp, '搜索')
    const orderRowExpression = `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-table__body-wrapper tbody tr, .el-table__fixed-body-wrapper tbody tr, tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(approvedOrder.orderNo)}));
      })()
    `
    await waitForPage(cdp, `Boolean(${orderRowExpression})`, 'purchase order approved row visible', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase order search'))}`)
    })

    await markSmokeStep(cdp, 'purchase-order-unapprove:click')
    await invokeElement(
      cdp,
      `(() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${orderRowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '反审核');
      })()`,
      'purchase order unapprove button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确认反审核订单')});
      })()
    `, 'purchase order unapprove confirm')
    await invokeButton(cdp, '确定', 'purchase order unapprove confirm button', messageBoxExpression('确认反审核订单'))

    await markSmokeStep(cdp, 'purchase-order-unapprove:verify')
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/purchase/orders/' + ${JSON.stringify(order.id)}, { headers });
        if (!response.ok) return false;
        const detail = (await response.json())?.data;
        return detail?.status === 'DRAFT'
          && detail?.approvalStatus === 'NOT_SUBMITTED'
          && detail?.receiptStatus === 'NOT_RECEIVED';
      })()
    `, 'purchase order unapproved to draft', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after purchase order unapprove'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'purchase order unapprove workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'purchase-order-unapprove',
    orderNo: approvedOrder.orderNo,
    passed: failures.length === 0,
    failures
  }
}

async function runSalesOrderUnapproveWorkflow(cdp, auth) {
  resetEvents(cdp.events)
  const fixture = await prepareApprovedSalesOrderFixture(auth)
  const approvedOrder = fixture.order
  if (approvedOrder.status !== 'APPROVED') {
    throw new Error(`sales order ${approvedOrder.orderNo} not APPROVED before unapprove: ${approvedOrder.status}`)
  }

  try {
    await setBrowserAuth(cdp, auth)
    await markSmokeStep(cdp, 'sales-order-unapprove:navigate')
    await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/sales/orders' })
    await waitForPage(cdp, `
      document.body.innerText.includes('销售订单')
        && document.body.innerText.includes('新增订单')
    `, 'sales order page')

    await markSmokeStep(cdp, 'sales-order-unapprove:search')
    // 销售订单页关键词输入框 placeholder 是「订单号」
    await setElementValue(cdp, `document.querySelector('input[placeholder="订单号"]')`, approvedOrder.orderNo, 'sales order no search input')
    await clickButton(cdp, '查询')
    const orderRowExpression = `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return [...document.querySelectorAll('.el-table__body-wrapper tbody tr')]
          .find((item) => visible(item) && item.innerText.includes(${JSON.stringify(approvedOrder.orderNo)}));
      })()
    `
    await waitForPage(cdp, `Boolean(${orderRowExpression})`, 'sales order approved row visible', 12000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales order search'))}`)
    })

    await markSmokeStep(cdp, 'sales-order-unapprove:click')
    await invokeElement(
      cdp,
      `(() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const row = ${orderRowExpression};
        return row && [...row.querySelectorAll('button')].find((button) => visible(button) && button.innerText.trim() === '反审核');
      })()`,
      'sales order unapprove button'
    )
    await waitForPage(cdp, `
      (() => {
        const visible = (element) => {
          if (!element) return false;
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        return Boolean(${messageBoxExpression('确定反审核该销售订单吗')});
      })()
    `, 'sales order unapprove confirm')
    await invokeButton(cdp, '确定', 'sales order unapprove confirm button', messageBoxExpression('确定反审核该销售订单吗'))

    await markSmokeStep(cdp, 'sales-order-unapprove:verify')
    await waitForPage(cdp, `
      (async () => {
        const token = localStorage.getItem('token');
        const headers = token ? { Authorization: 'Bearer ' + token } : {};
        const response = await fetch('/api/sales/orders/' + ${JSON.stringify(approvedOrder.id)}, { headers });
        if (!response.ok) return false;
        const detail = (await response.json())?.data;
        return detail?.status === 'DRAFT'
          && detail?.approvalStatus === 'NOT_SUBMITTED'
          && detail?.deliveryStatus === 'NOT_DELIVERED';
      })()
    `, 'sales order unapproved to draft', 20000).catch(async (error) => {
      throw new Error(`${error.message}: ${JSON.stringify(await pageDiagnostics(cdp, 'after sales order unapprove'))}`)
    })
  } catch (error) {
    const diagnostic = await pageDiagnostics(cdp, 'sales order unapprove workflow failure').catch(() => null)
    throw new Error(`${error.message}${diagnostic ? `\n${JSON.stringify(diagnostic, null, 2)}` : ''}`)
  }

  const failures = snapshotFailures(cdp.events)
  return {
    name: 'sales-order-unapprove',
    orderNo: approvedOrder.orderNo,
    passed: failures.length === 0,
    failures
  }
}

async function main() {
  mkdirSync(targetDir, { recursive: true })
  const chromeProfileDir = join(targetDir, `ui-smoke-chrome-profile-${process.pid}-${Date.now()}`)
  const chromeWindowSize = process.env.UI_SMOKE_WINDOW_SIZE || '1440,1000'

  start('backend', 'java', [
    '-jar',
    'target\\erp-server-1.0.0.jar',
    '--spring.profiles.active=local',
    '--spring.datasource.url=jdbc:mysql://localhost:3306/erp_codex_runtime?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
  ], backendDir)

  await waitFor('backend', 'http://127.0.0.1:8080/actuator/health')
  const { credentials, auth } = await login()

  start('frontend', process.env.ComSpec || 'cmd.exe', [
    '/d',
    '/s',
    '/c',
    'npm run dev -- --host 127.0.0.1 --port 5173'
  ], frontendDir)

  await waitFor('frontend', 'http://127.0.0.1:5173/')

  start('chrome', chromePath, [
    '--headless=new',
    '--disable-gpu',
    '--disable-gpu-shader-disk-cache',
    '--disable-gpu-program-cache',
    '--disable-extensions',
    '--disable-background-networking',
    `--window-size=${chromeWindowSize}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--remote-allow-origins=*',
    '--remote-debugging-port=9223',
    `--user-data-dir=${chromeProfileDir}`,
    'about:blank'
  ], backendDir)

  const cdp = await connectCdp()
  await cdp.send('Runtime.enable')
  await cdp.send('Page.enable')
  await cdp.send('Network.enable')

  await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/login' })
  await sleep(1500)
  await setBrowserAuth(cdp, auth)

  const routes = [
    { path: '/dashboard', text: '今日采购订单' },
    { path: '/exception-tickets', text: '异常工单' },
    { path: '/exception-rules', text: '异常规则' },
    { path: '/exception-sla-policies', text: 'SLA策略' },
    { path: '/reports/traces', text: '暂无追踪事件' },
    { path: '/finance/periods', text: '会计期间管理' },
    { path: '/finance/expenses', text: '费用管理' },
    { path: '/system/attachments', text: '附件中心' },
    { path: '/inventory/stocks', text: '库存查询' },
    { path: '/sales/orders', text: '销售订单' },
    { path: '/sales/deliveries', text: '销售发货' },
    { path: '/system/users', text: '用户管理' },
    { path: '/finance/payments', text: '收款管理' },
    { path: '/production/orders', text: '生产订单管理' },
    { path: '/system/user-sessions', text: '在线会话' },
    { path: '/system/notifications', text: '通知中心' },
    { path: '/system/observability', text: '可观测性' },
    { path: '/system/readiness', text: '预生产验收' },
    { path: '/finance/funds', text: '资金对账' },
    { path: '/finance/invoices', text: '发票登记' },
    { path: '/purchase/inquiries', text: '新建询价单' },
    { path: '/qc/inspections', text: '新建检验单' },
    { path: '/production/work-centers', text: '工作中心' },
    { path: '/production/routings', text: '工艺路线' },
    { path: '/system/document-state-rules', text: '单据状态流转规则' }
  ]

  const shouldRunRoutes = process.env.UI_SMOKE_ROUTES !== '0'
  const results = []
  for (const route of shouldRunRoutes ? routes : []) {
    resetEvents(cdp.events)
    await cdp.send('Page.navigate', { url: `http://127.0.0.1:5173${route.path}` })
    await sleep(4500)
    const state = await cdp.send('Runtime.evaluate', {
      expression: `({
        path: location.pathname,
        title: document.title,
        text: document.body.innerText,
        message: document.querySelector('.el-message')?.innerText || ''
      })`,
      returnByValue: true
    })
    const page = state.result.value
    const failures = snapshotFailures(cdp.events)
    if (page.path !== route.path) {
      failures.push(`redirected to ${page.path}`)
    }
    if (!page.text.includes(route.text)) {
      failures.push(`missing expected text: ${route.text}`)
    }
    if (page.text.includes('欢迎登录') || page.text.includes('请输入您的账号密码')) {
      failures.push('rendered login page after authenticated navigation')
    }
    if (page.text.includes('开发中')) {
      failures.push('placeholder development page rendered')
    }
    if (page.text.includes('您没有访问该页面的权限') || page.message.includes('您没有访问该页面的权限')) {
      failures.push('permission denied message rendered')
    }
    results.push({
      route: route.path,
      title: page.title,
      expectedText: route.text,
      passed: failures.length === 0,
      failures
    })
  }

  const workflowFilter = (process.env.UI_SMOKE_WORKFLOW || '').toLowerCase()
  const workflowFilterTokens = workflowFilter
    .split(/[,\s]+/)
    .map((token) => token.trim())
    .filter(Boolean)
  const workflowDefinitions = [
    { name: 'exception-ticket-create-and-close', run: () => runExceptionTicketWorkflow(cdp) },
    { name: 'exception-sla-policy-save', run: () => runSlaPolicyWorkflow(cdp) },
    { name: 'exception-rule-config-toggle-and-scan-all', run: () => runExceptionRuleWorkflow(cdp) },
    { name: 'business-trace-search-and-reset', run: () => runBusinessTraceWorkflow(cdp) },
    { name: 'finance-period-generate-check-and-reconcile', run: () => runFinancePeriodWorkflow(cdp) },
    { name: 'report-export-download', run: () => runReportExportWorkflow(cdp) },
    { name: 'attachment-upload-download-delete', run: () => runAttachmentWorkflow(cdp) },
    { name: 'readiness-run-evidence-result-and-no-go', run: () => runReadinessWorkflow(cdp) },
    { name: 'fund-account-and-statement-create', run: () => runFundWorkflow(cdp) },
    { name: 'fund-statement-match-unmatch-receipt', run: () => runFundMatchUnmatchWorkflow(cdp, auth) },
    { name: 'finance-expense-create-submit-approve-post-reverse', run: () => runExpenseWorkflow(cdp, auth) },
    { name: 'finance-subject-save-toggle-and-voucher-readonly-detail', run: () => runFinanceSubjectVoucherWorkflow(cdp, auth) },
    { name: 'workflow-task-detail-approve-and-record-filter', run: () => runWorkflowTaskApprovalWorkflow(cdp, auth) },
    { name: 'workflow-task-reject', run: () => runWorkflowTaskRejectWorkflow(cdp, auth) },
    { name: 'workflow-withdraw', run: () => runWorkflowWithdrawWorkflow(cdp, auth) },
    { name: 'sales-order-create-submit-approve', run: () => runSalesOrderWorkflow(cdp, auth) },
    { name: 'sales-delivery-create-post-receivable', run: () => runSalesDeliveryWorkflow(cdp, auth) },
    { name: 'sales-receipt-create-settle-receivable', run: () => runSalesReceiptWorkflow(cdp, auth) },
    { name: 'sales-return-create-post-stock-receivable-offset', run: () => runSalesReturnWorkflow(cdp, auth) },
    { name: 'purchase-return-create-post-stock-payable-offset', run: () => runPurchaseReturnWorkflow(cdp, auth) },
    { name: 'purchase-order-receipt-payment-settle-payable', run: () => runPurchaseToPaymentWorkflow(cdp, auth) },
    { name: 'inventory-adjustment-create-post-balance', run: () => runInventoryAdjustmentWorkflow(cdp, auth) },
    { name: 'inventory-transfer-create-post-balance', run: () => runInventoryTransferWorkflow(cdp, auth) },
    { name: 'inventory-check-create-adjust-balance', run: () => runInventoryCheckWorkflow(cdp, auth) },
    { name: 'production-order-create-release-issue-complete', run: () => runProductionOrderWorkflow(cdp, auth) },
    { name: 'manual-voucher-create-submit-approve-post-cancel', run: () => runManualVoucherWorkflow(cdp, auth) },
    { name: 'qc-inspection-create-submit-judge-only-qualified', run: () => runQcInspectionWorkflow(cdp, auth) },
    { name: 'system-config-create', run: () => runSystemConfigCreateWorkflow(cdp, auth) },
    { name: 'finance-invoice-create-post-cancel', run: () => runFinanceInvoiceWorkflow(cdp, auth) },
    { name: 'purchase-inquiry-create-and-convert-to-po', run: () => runPurchaseInquiryConvertToPoWorkflow(cdp, auth) },
    { name: 'purchase-order-unapprove', run: () => runPurchaseOrderUnapproveWorkflow(cdp, auth) },
    { name: 'sales-order-unapprove', run: () => runSalesOrderUnapproveWorkflow(cdp, auth) },
    { name: 'purchase-receipt-draft-edit', run: () => runPurchaseReceiptDraftEditWorkflow(cdp, auth) },
    { name: 'purchase-return-draft-edit', run: () => runPurchaseReturnDraftEditWorkflow(cdp, auth) },
    { name: 'sales-delivery-draft-edit', run: () => runSalesDeliveryDraftEditWorkflow(cdp, auth) },
    { name: 'sales-return-draft-edit', run: () => runSalesReturnDraftEditWorkflow(cdp, auth) }
  ]
  const workflows = []
  for (const workflow of workflowDefinitions) {
    if (
      workflowFilterTokens.length > 0
      && !workflowFilterTokens.some((token) => workflow.name.toLowerCase().includes(token))
    ) {
      continue
    }
    try {
      workflows.push(await workflow.run())
    } catch (error) {
      // 单条失败不中断整套，保证 ui-smoke-report.json 能覆盖全部 workflow 结果
      workflows.push({
        name: workflow.name,
        passed: false,
        failures: [error.message]
      })
    }
  }

  cdp.close()
  const failed = results.filter((result) => !result.passed)
  const failedWorkflows = workflows.filter((workflow) => !workflow.passed)
  const report = {
    loginUser: credentials.username,
    checkedAt: new Date().toISOString(),
    results,
    workflows
  }
  writeFileSync(join(targetDir, 'ui-smoke-report.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report, null, 2))

  if (failed.length > 0 || failedWorkflows.length > 0) {
    throw new Error(`${failed.length} route smoke checks and ${failedWorkflows.length} workflow smoke checks failed`)
  }
}

try {
  await main()
} finally {
  for (const item of children.reverse()) {
    await killProcessTree(item.child.pid)
  }
}
