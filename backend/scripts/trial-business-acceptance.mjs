import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { mkdirSync, readFileSync, realpathSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs, parseEnv } from 'node:util'
import { verifyDeploymentBundle } from './deployment-bundle.mjs'

const script = fileURLToPath(import.meta.url)
const repo = path.resolve(path.dirname(script), '../..')
export const trialPermissions = [
  'purchase:order:view', 'purchase:order:create', 'purchase:order:submit',
  'sales:order:view', 'sales:order:create', 'sales:order:submit',
  'masterdata:product:view', 'masterdata:supplier:view', 'masterdata:customer:view',
  'masterdata:warehouse:view', 'inventory:stock:view'
]

export function localOrigin(value) {
  const url = new URL(value)
  assert(url.protocol === 'http:' && url.hostname === '127.0.0.1' && !url.username && !url.password &&
    !url.search && !url.hash && url.pathname === '/', 'Trial requires a plain http://127.0.0.1 origin')
  return url.origin
}

export function businessDate(value) {
  assert(/^\d{4}-\d{2}-\d{2}$/.test(value) && new Date(value).toISOString().slice(0, 10) === value,
    'Use a valid YYYY-MM-DD business date')
  const year = Number(value.slice(0, 4))
  assert(year >= 2000 && year <= 2100, 'Business year must be between 2000 and 2100')
  return value
}

export function selectTrialMenus(tree) {
  const nodes = new Map()
  const visit = rows => rows.forEach(row => { nodes.set(String(row.id), row); visit(row.children || []) })
  visit(tree)
  const selected = new Set()
  for (const permission of trialPermissions) {
    const matches = [...nodes.values()].filter(row => row.permission === permission && row.status === 'ACTIVE')
    assert.equal(matches.length, 1, `Expected one active menu for ${permission}`)
    let row = matches[0]
    const parents = new Set()
    while (row) {
      assert(!parents.has(String(row.id)), 'Cyclic menu tree')
      parents.add(String(row.id))
      assert.equal(row.status, 'ACTIVE', 'Required menu ancestor is disabled')
      assert(!row.permission || trialPermissions.includes(row.permission), 'Menu ancestor would grant an extra permission')
      selected.add(String(row.id))
      if (String(row.parentId) === '0' || row.parentId == null) break
      row = nodes.get(String(row.parentId))
      assert(row, 'Missing menu ancestor')
    }
  }
  return [...selected]
}

export function checkSettlement(record, { original, settled, remaining }) {
  for (const [field, expected] of Object.entries({ originalAmount: original, settledAmount: settled,
    remainingAmount: remaining, baseRemainingAmount: remaining })) {
    assert(record[field] !== null && record[field] !== undefined && record[field] !== '', `Missing ${field}`)
    assert.equal(Number(record[field]), expected, `${field} differs from the sample calculation`)
  }
  assert.equal(record.currencyCode, 'CNY', 'Trial settlement must use CNY')
  assert.equal(Number(record.exchangeRate), 1)
  if (remaining === 0) assert.equal(record.status, 'SETTLED', 'Zero balance must be SETTLED')
  else assert.notEqual(record.status, 'SETTLED', 'Partially settled balance cannot be SETTLED')
  return { original, settled, remaining, status: record.status }
}

export function checkStock(rows, warehouseId, productId, quantity, amount) {
  const selected = rows.filter(row => String(row.warehouseId) === String(warehouseId) && String(row.productId) === String(productId))
  assert.equal(selected.length, 1, 'Expected a single balance for the dedicated trial product')
  const row = selected[0]
  for (const [field, expected] of Object.entries({ qtyOnHand: quantity, qtyAvailable: quantity, qtyReserved: 0, amountOnHand: amount })) {
    assert(row[field] !== null && row[field] !== undefined && row[field] !== '', `Missing ${field}`)
    assert.equal(Number(row[field]), expected, `Stock ${field} differs from the sample calculation`)
  }
  return { quantity, amount, reserved: 0 }
}

export function checkContainers(containers, { project, bundle, port }) {
  assert(/^erp-preview-[a-z0-9-]+$/.test(project), 'Use a dedicated erp-preview-* Compose project')
  assert.equal(containers.length, 4, 'Expected four running trial services')
  const services = new Set()
  for (const container of containers) {
    const labels = container.Config.Labels
    assert.equal(labels['com.docker.compose.project'], project)
    assert.equal(labels['com.docker.compose.project.working_dir'], bundle, 'Compose directory differs from the verified bundle')
    assert.equal(labels['com.docker.compose.project.config_files'], path.join(bundle, 'compose.yaml'))
    assert.equal(container.State.Health?.Status, 'healthy', 'Trial service is not healthy')
    const service = labels['com.docker.compose.service']
    assert(!services.has(service)); services.add(service)
    const published = Object.entries(container.NetworkSettings.Ports || {}).filter(([, bindings]) => bindings?.length)
    if (service === 'web') assert.deepEqual(published, [['8080/tcp', [{ HostIp: '127.0.0.1', HostPort: String(port) }]]])
    else assert.equal(published.length, 0, 'Only the trial Web port may be published')
  }
  assert.deepEqual([...services].sort(), ['erp-server', 'mysql', 'redis', 'web'])
}

export async function runTrial({ bundleDirectory, expectedManifestSha256, project, outputDirectory, date }) {
  date = businessDate(date)
  const bundle = realpathSync(bundleDirectory)
  const deployment = verifyDeploymentBundle({ bundleDirectory: bundle, expectedManifestSha256 })
  const config = parseEnv(readFileSync(path.join(bundle, '.env'), 'utf8'))
  const origin = localOrigin(config.ERP_PUBLIC_ORIGIN)
  assert.equal(new URL(origin).port || '80', config.ERP_HTTP_PORT)
  assert.equal(config.ERP_HTTP_BIND_HOST, '127.0.0.1')
  assert(config.ERP_BOOTSTRAP_ADMIN_PASSWORD, 'Missing private bootstrap password')
  const git = args => execFileSync('git', args, { cwd: repo, encoding: 'utf8' }).trim()
  const sourceRevision = git(['rev-parse', 'HEAD'])
  assert.equal(git(['status', '--porcelain']), '', 'Commit trial tools before recording acceptance evidence')
  const docker = args => execFileSync('docker', args, { encoding: 'utf8', timeout: 30000 }).trim()
  const ids = docker(['ps', '-q', '--filter', `label=com.docker.compose.project=${project}`]).split(/\s+/).filter(Boolean)
  assert(ids.length, 'No running trial project')
  const containers = JSON.parse(docker(['inspect', ...ids]))
  checkContainers(containers, { project, bundle, port: config.ERP_HTTP_PORT })
  const backend = containers.find(c => c.Config.Labels['com.docker.compose.service'] === 'erp-server')
  const manifest = JSON.parse(readFileSync(path.join(bundle, 'deployment-manifest.json')))
  assert.equal(docker(['exec', backend.Id, 'sha256sum', '/app/erp-server.jar']).split(/\s/)[0], manifest.jarSha256)
  const output = path.resolve(outputDirectory)
  const parent = realpathSync(path.dirname(output))
  for (const forbidden of [repo, bundle]) assert(parent !== forbidden && !parent.startsWith(forbidden + path.sep), 'Evidence must be outside source and bundle')
  const evidence = path.join(parent, path.basename(output))
  mkdirSync(evidence, { mode: 0o700 }) // Refuse reuse, including a previous partial run.
  const suffix = randomBytes(5).toString('hex')
  const prefix = `DEMO-${suffix}`
  const account = { username: `demo_entry_${suffix}`, password: `Demo1${randomBytes(24).toString('hex')}` }
  writeFileSync(path.join(evidence, 'private-account.json'), JSON.stringify(account, null, 2) + '\n', { flag: 'wx', mode: 0o600 })
  const report = { status: 'RUNNING', scope: 'Local synthetic trial only; retained sample documents; no business signoff',
    startedAt: new Date().toISOString(), sourceRevision, dirtyWorktree: false, ...deployment,
    project, origin, businessDate: date, prefix, submitter: account.username, resources: {}, checks: [] }
  const secrets = [config.ERP_BOOTSTRAP_ADMIN_PASSWORD, account.password]
  const redact = value => secrets.reduce((result, secret) => result.replaceAll(secret, '[REDACTED]'), String(value))
  const persist = () => writeFileSync(path.join(evidence, 'report.json'), JSON.stringify(report, null, 2) + '\n', { mode: 0o600 })
  let adminToken, submitterToken, trialMenus
  const request = async (method, route, body, token = adminToken, expectedStatus = 200) => {
    const response = await fetch(origin + route, { method, redirect: 'error', signal: AbortSignal.timeout(15000),
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}) })
    const payload = await response.json()
    assert.equal(response.status, expectedStatus, `${method} ${route}: HTTP ${response.status}; ${redact(payload.message || '')}`)
    assert.equal(payload.code, expectedStatus === 200 ? '0' : String(expectedStatus), `${method} ${route}: ${redact(payload.message || '')}`)
    return payload.data
  }
  const create = async (name, route, body, token) => {
    const data = await request('POST', route, body, token)
    assert(data?.id, `Missing created ID for ${name}`)
    report.resources[name] = data; persist() // Keep each confirmed ID even if a later step fails.
    return data
  }
  const check = async (name, action) => {
    report.activeStep = name; persist(); console.log(`[trial] ${name}`)
    const result = await action()
    report.checks.push({ name, passed: true, ...result }); report.activeStep = null; persist()
  }
  const rememberToken = login => { assert(login.accessToken); secrets.push(login.accessToken); if (login.refreshToken) secrets.push(login.refreshToken); return login.accessToken }
  const remark = `演示数据 ${prefix}，不是真实期初或业务验收签字`
  try {
    await check('Verify deployed versions and bootstrap login', async () => {
      const response = await fetch(origin + '/__deployment.json', { redirect: 'error', signal: AbortSignal.timeout(10000) })
      assert.equal(response.status, 200)
      assert.deepEqual(await response.json(), { applicationRevision: deployment.applicationRevision, toolRevision: deployment.toolRevision,
        jarSha256: manifest.jarSha256, frontendManifestSha256: manifest.frontendManifestSha256 })
      const login = await request('POST', '/api/auth/login', { username: 'admin', password: config.ERP_BOOTSTRAP_ADMIN_PASSWORD })
      adminToken = rememberToken(login); report.adminUserId = login.user.id
      trialMenus = selectTrialMenus(await request('GET', '/api/system/menus/tree'))
      return { versionsMatch: true }
    })
    await check('Resolve an open business period without reopening locked periods', async () => {
      const year = Number(date.slice(0, 4))
      let periods = await request('GET', `/api/finance/periods?year=${year}`)
      if (!periods.length) periods = await request('POST', '/api/finance/periods/generate', { year })
      const matches = periods.filter(p => p.startDate <= date && p.endDate >= date)
      assert.equal(matches.length, 1, 'Expected one period covering the business date')
      assert.equal(matches[0].status, 'OPEN', 'Business period is not OPEN; existing locks are preserved')
      report.period = matches[0]; return { periodId: matches[0].id }
    })
    let warehouse, supplier, customer, product
    await check('Create clearly labelled trial master data', async () => {
      const dept = await create('department', '/api/system/depts', { deptCode: prefix, deptName: `演示业务部 ${suffix}`, parentId: 0, sortNo: 99, remark })
      warehouse = await create('warehouse', '/api/masterdata/warehouses', { warehouseCode: prefix, warehouseName: `演示仓 ${suffix}`, deptId: dept.id, managerUserId: report.adminUserId, remark })
      supplier = await create('supplier', '/api/masterdata/suppliers', { supplierCode: prefix, supplierName: `演示供应商 ${suffix}`, settlementMethod: 'BANK', remark })
      customer = await create('customer', '/api/masterdata/customers', { customerCode: prefix, customerName: `演示客户 ${suffix}`, customerType: 'ENTERPRISE', settlementMethod: 'BANK', creditLimit: 1000, remark })
      product = await create('product', '/api/masterdata/products', { productCode: prefix, productName: `演示商品 ${suffix}`, productType: 'GOODS', categoryName: '演示', unitName: '件', purchasePrice: 10, salePrice: 15, taxRate: 0, lotControlled: false, shelfLifeControlled: false, remark })
      return { warehouseId: warehouse.id, productId: product.id }
    })
    await check('Create a restricted order-entry user and verify denied finance access', async () => {
      const role = await create('role', '/api/system/roles', { roleCode: prefix, roleName: `演示录单 ${suffix}`, remark })
      await request('PUT', `/api/system/roles/${role.id}/menus`, { menuIds: trialMenus })
      await request('PUT', `/api/system/roles/${role.id}/data-scope`, { hasAllScope: false, deptScoped: false, postScoped: false, selfScoped: true, warehouseIds: [warehouse.id] })
      const user = await create('user', '/api/system/users', { ...account, realName: `演示录单员 ${suffix}`, deptId: report.resources.department.id, remark })
      await request('PUT', `/api/system/users/${user.id}/roles`, { roleIds: [role.id] })
      const login = await request('POST', '/api/auth/login', account)
      submitterToken = rememberToken(login)
      assert.deepEqual([...login.permissions].sort(), [...trialPermissions].sort(), 'Trial user has unexpected permissions')
      await request('GET', '/api/finance/payables?pageNo=1&pageSize=1', undefined, submitterToken, 403)
      await request('GET', '/api/system/users?pageNo=1&pageSize=1', undefined, submitterToken, 403)
      return { permissions: trialPermissions, financeDenied: true, userAdministrationDenied: true }
    })
    let payable, receivable
    await check('Purchase 10 units at CNY 10 and receive inventory', async () => {
      const order = await create('purchaseOrder', '/api/purchase/orders', { supplierId: supplier.id, orderDate: date, deliveryDate: date, remark,
        lines: [{ productId: product.id, qty: 10, price: 10, taxRate: 0, remark }] }, submitterToken)
      await request('POST', `/api/purchase/orders/${order.id}/submit`, { remark }, submitterToken)
      await request('POST', `/api/purchase/orders/${order.id}/approve`, { remark }, submitterToken, 403)
      await request('POST', `/api/purchase/orders/${order.id}/approve`, { remark })
      const receipt = await create('purchaseReceipt', '/api/purchase/receipts', { orderId: order.id, warehouseId: warehouse.id, receiptDate: date, remark,
        lines: [{ orderLineId: order.lines[0].id, qty: 10, remark }] })
      await request('POST', `/api/purchase/receipts/${receipt.id}/post`)
      const page = await request('GET', `/api/finance/payables?supplierId=${supplier.id}&sourceType=PURCHASE_RECEIPT&pageSize=100`)
      const matches = page.records.filter(p => String(p.sourceId) === String(receipt.id) && p.sourceType === 'PURCHASE_RECEIPT')
      assert.equal(matches.length, 1); payable = matches[0]; report.resources.payable = payable
      const balances = await request('GET', `/api/inventory/balances?warehouseId=${warehouse.id}&productId=${product.id}&pageSize=100`)
      return { payable: checkSettlement(payable, { original: 100, settled: 0, remaining: 100 }), stock: checkStock(balances.records, warehouse.id, product.id, 10, 100), submitterApprovalDenied: true }
    })
    for (const [index, amount] of [40, 60].entries()) {
      await check(`Pay supplier installment ${index + 1}: CNY ${amount}`, async () => {
        await create(`payment${index + 1}`, '/api/finance/payments', { supplierId: supplier.id, paymentDate: date, amount, remark,
          allocations: [{ payableId: payable.id, amount }] })
        const actual = await request('GET', `/api/finance/payables/${payable.id}`)
        report.resources.payable = actual
        return checkSettlement(actual, { original: 100, settled: index === 0 ? 40 : 100, remaining: index === 0 ? 60 : 0 })
      })
    }
    await check('Sell 4 units at CNY 15 and verify remaining inventory', async () => {
      const order = await create('salesOrder', '/api/sales/orders', { customerId: customer.id, warehouseId: warehouse.id, orderDate: date, deliveryDate: date, remark,
        lines: [{ productId: product.id, qty: 4, price: 15, taxRate: 0, remark }] }, submitterToken)
      await request('POST', `/api/sales/orders/${order.id}/submit`, { remark }, submitterToken)
      await request('POST', `/api/sales/orders/${order.id}/approve`, { remark }, submitterToken, 403)
      await request('POST', `/api/sales/orders/${order.id}/approve`, { remark })
      const delivery = await create('salesDelivery', '/api/sales/deliveries', { orderId: order.id, warehouseId: warehouse.id, deliveryDate: date, remark,
        lines: [{ orderLineId: order.lines[0].id, qty: 4, remark }] })
      await request('POST', `/api/sales/deliveries/${delivery.id}/post`)
      const page = await request('GET', `/api/finance/receivables?customerId=${customer.id}&sourceType=SALES_DELIVERY&pageSize=100`)
      const matches = page.records.filter(p => String(p.sourceId) === String(delivery.id) && p.sourceType === 'SALES_DELIVERY')
      assert.equal(matches.length, 1); receivable = matches[0]; report.resources.receivable = receivable
      const balances = await request('GET', `/api/inventory/balances?warehouseId=${warehouse.id}&productId=${product.id}&pageSize=100`)
      return { receivable: checkSettlement(receivable, { original: 60, settled: 0, remaining: 60 }), stock: checkStock(balances.records, warehouse.id, product.id, 6, 60), submitterApprovalDenied: true }
    })
    for (const [index, amount] of [20, 40].entries()) {
      await check(`Receive customer installment ${index + 1}: CNY ${amount}`, async () => {
        await create(`cashReceipt${index + 1}`, '/api/finance/receipts', { customerId: customer.id, receiptDate: date, amount, remark,
          allocations: [{ receivableId: receivable.id, amount }] })
        const actual = await request('GET', `/api/finance/receivables/${receivable.id}`)
        report.resources.receivable = actual
        return checkSettlement(actual, { original: 60, settled: index === 0 ? 20 : 60, remaining: index === 0 ? 40 : 0 })
      })
    }
    await check('Restricted user can read its own orders and warehouse balance', async () => {
      for (const [type, resource] of [['purchase', 'purchaseOrder'], ['sales', 'salesOrder']]) {
        const order = await request('GET', `/api/${type}/orders/${report.resources[resource].id}`, undefined, submitterToken)
        assert.equal(String(order.id), String(report.resources[resource].id))
      }
      const balances = await request('GET', `/api/inventory/balances?warehouseId=${warehouse.id}&productId=${product.id}&pageSize=100`, undefined, submitterToken)
      return { stock: checkStock(balances.records, warehouse.id, product.id, 6, 60) }
    })
    assert.equal(git(['rev-parse', 'HEAD']), sourceRevision)
    assert.equal(git(['status', '--porcelain']), '', 'Trial tooling changed during acceptance')
    report.status = 'PASSED'
  } catch (error) {
    report.status = 'FAILED'; report.failure = redact(error.message)
    throw new Error(report.failure)
  } finally {
    report.finishedAt = new Date().toISOString(); persist()
    const r = report.resources
    const rows = Object.entries(r).map(([name, value]) => `| ${name} | ${value.id} | ${value.orderNo || value.receiptNo || value.deliveryNo || value.paymentNo || value.receivableNo || value.payableNo || value.productCode || value.warehouseCode || ''} |`)
    const markdown = `# 本机演示业务记录\n\n结果：${report.status}。${remark}。\n\n入口：${origin}；业务日期：${date}；命名标记：${prefix}。\n\n录单账号：\`${account.username}\`，密码在同目录 private-account.json。管理员负责审批、出入库及财务操作。\n\n采购 10 件 × 10 元 = 100 元，付款 40 + 60 元；销售 4 件 × 15 元 = 60 元，收款 20 + 40 元。预期结余 6 件 / 60 元，应收应付剩余均为 0。失败时以 report.json 中 activeStep 和已保存资源为准，不重复运行同一证据目录。\n\n| 对象 | ID | 单号 / 编码 |\n|---|---|---|\n${rows.join('\n')}\n\n在采购订单、采购入库、付款单、销售订单、销售发货、收款单和库存查询中按上述单号 / 编码核对。实际人工作业与财务签字仍待完成。\n`
    writeFileSync(path.join(evidence, 'HANDOFF.md'), markdown, { mode: 0o600 })
  }
  return { status: report.status, checks: report.checks.length, evidenceDirectory: evidence, origin, submitter: account.username }
}

if (process.argv[1] && path.resolve(process.argv[1]) === script) {
  const { values } = parseArgs({ options: { bundle: { type: 'string' }, 'expected-manifest-sha256': { type: 'string' },
    project: { type: 'string' }, evidence: { type: 'string' }, date: { type: 'string' } } })
  try {
    console.log(JSON.stringify(await runTrial({ bundleDirectory: values.bundle, expectedManifestSha256: values['expected-manifest-sha256'],
      project: values.project, outputDirectory: values.evidence, date: values.date }), null, 2))
  } catch (error) { console.error(error.message); process.exitCode = 1 }
}
