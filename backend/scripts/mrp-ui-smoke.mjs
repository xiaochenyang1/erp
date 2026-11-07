import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'

// Runs inside ui-smoke.mjs's disposable database and browser session.
export async function runMrpUiSmoke(h) {
  const { cdp, auth, apiRequest, setBrowserAuth, frontendBaseUrl, prepareSalesOrderFixture,
    waitForPage, invokeElement, clickButton } = h
  const fixture = await prepareSalesOrderFixture(auth) // 20 on hand; no sales demand.
  const supplier = await apiRequest(auth, 'POST', '/masterdata/suppliers', {
    supplierCode: `MRPS${fixture.suffix}`, supplierName: `MRP supplier ${fixture.suffix}`,
    settlementMethod: 'BANK_TRANSFER', remark: 'MRP isolated UI regression'
  })
  await apiRequest(auth, 'POST', '/inventory/alert-rules', {
    warehouseId: fixture.warehouse.id, productId: fixture.product.id, minQty: 24,
    remark: '20 on hand + 4 replenishment = 24 safety stock'
  })
  await setBrowserAuth(cdp, auth)
  await cdp.send('Page.navigate', { url: `${frontendBaseUrl}/inventory/mrp` })
  await waitForPage(cdp, `document.body.innerText.includes('运行计划')`, 'MRP page ready')
  await clickButton(cdp, '运行计划')
  const row = `[...document.querySelectorAll('.el-table__body tr')].find(tr => tr.innerText.includes(${JSON.stringify(fixture.product.productCode)}))`
  await waitForPage(cdp, `${row}?.innerText.includes('转采购')`, 'MRP purchase suggestion visible')
  await waitForPage(cdp, `Number(${row}?.querySelectorAll('td')[2]?.innerText.trim()) === 4`, 'MRP UI quantity is 4')
  const history = await apiRequest(auth, 'GET', '/inventory/mrp/runs?pageNo=1&pageSize=1')
  const runId = history.records[0].id
  const run = await apiRequest(auth, 'GET', `/inventory/mrp/runs/${runId}`)
  const line = run.purchaseLines.find(item => String(item.productId) === String(fixture.product.id))
  if (!line || Number(line.netQty) !== 4 || Number(line.demandQty) !== 24 || Number(line.onHandQty) !== 20) {
    throw new Error(`MRP safety-stock arithmetic is incorrect: ${JSON.stringify(line)}`)
  }
  await invokeElement(cdp, `[...(${row}).querySelectorAll('button')].find(b => b.innerText.trim() === '转采购')`, 'convert MRP line to PO')
  await confirmPurchase(h, supplier)
  await waitForPage(cdp, `(async () => {
    const token = localStorage.getItem('token');
    const response = await fetch('/api/inventory/mrp/runs/${runId}', { headers: { Authorization: 'Bearer ' + token } });
    const payload = await response.json();
    return payload.data?.purchaseLines?.some(l => String(l.id) === ${JSON.stringify(String(line.id))} && l.status === 'CONVERTED');
  })()`, 'MRP conversion persisted')
  const refreshed = await apiRequest(auth, 'GET', `/inventory/mrp/runs/${runId}`)
  const converted = refreshed.purchaseLines.find(item => String(item.id) === String(line.id))
  const order = await apiRequest(auth, 'GET', `/purchase/orders/${converted.convertedBizId}`)
  if (order.lines.length !== 1 || Number(order.lines[0].qty) !== 4 || String(order.plannedWarehouseId) !== String(fixture.warehouse.id)) {
    throw new Error(`Converted PO differs from MRP: ${JSON.stringify(order.lines)}`)
  }
  await waitForPage(cdp, `${row}?.innerText.includes(${JSON.stringify(converted.convertedBizNo)})`, 'MRP row shows converted PO')
  return { name: 'mrp-safety-stock-run-and-convert', passed: true, failures: [], runNo: run.runNo,
    purchaseOrderNo: converted.convertedBizNo, expectedQty: 4, actualQty: Number(order.lines[0].qty) }
}

export async function runMultilevelMrpUiSmoke(h) {
  const { auth, apiRequest, cdp, waitForPage, invokeElement, clickButton } = h
  const fixture = await h.prepareSalesOrderFixture(auth)
  const suffix = fixture.suffix
  const finishedWarehouse = await apiRequest(auth, 'POST', '/masterdata/warehouses', {
    warehouseCode: `MRPF${suffix}`, warehouseName: `MRP成品仓${suffix}`, deptId: 3501, managerUserId: 4001
  })
  const products = []
  for (const code of ['A', 'B', 'C']) {
    products.push(await apiRequest(auth, 'POST', '/masterdata/products', {
      productCode: `MRP${code}${suffix}`, productName: `MRP多层${code}${suffix}`, productType: 'GOODS',
      categoryName: 'MRP多层验收', unitName: '件', purchasePrice: 1, salePrice: 2, taxRate: 0,
      mrpPolicy: { supplyType: code === 'C' ? 'PURCHASE' : 'PRODUCTION',
        materialWarehouseId: code === 'A' ? fixture.warehouse.id : null }
    }))
  }
  const [a, b, c] = products
  for (const [parent, child, per] of [[a, b, 2], [b, c, 3]]) {
    await apiRequest(auth, 'POST', '/production/boms', { productId: parent.id, baseQty: 1,
      lines: [{ materialProductId: child.id, qtyPer: per, lossRate: 0 }] })
  }
  const supplier = await createSupplier(h, `MRPM${suffix}`)
  await stockMaterials(h, fixture.warehouse.id, fixture.orderDate, [[b, 4], [c, 10]])
  await stockMaterials(h, finishedWarehouse.id, fixture.orderDate, [[b, 100], [c, 100]])
  // Safety demand permits the exact 10A example through public APIs; sales approval still reserves physical stock.
  await apiRequest(auth, 'POST', '/inventory/alert-rules', { warehouseId: finishedWarehouse.id, productId: a.id, minQty: 10 })
  await openMrp(h)
  await clickButton(cdp, '运行计划')
  await waitForPage(cdp, `Number(${rowFor(c.productCode)}?.querySelectorAll('td')[2]?.innerText.trim()) === 38`, 'three-level MRP C shortage is 38')
  const run = await latestRun(h)
  const aLine = run.productionLines.find(l => String(l.productId) === String(a.id))
  const bLine = run.productionLines.find(l => String(l.productId) === String(b.id))
  const cLine = run.purchaseLines.find(l => String(l.productId) === String(c.id))
  assert.deepEqual([aLine, bLine, cLine].map(l => Number(l.netQty)), [10, 16, 38])
  assert.deepEqual([aLine, bLine, cLine].map(l => Number(l.bomLevel)), [0, 1, 2])
  assert.equal(String(aLine.warehouseId), String(finishedWarehouse.id))
  assert.equal(String(aLine.materialWarehouseId), String(fixture.warehouse.id))
  assert.equal(String(bLine.warehouseId), String(fixture.warehouse.id))
  assert.equal(String(cLine.warehouseId), String(fixture.warehouse.id))
  await invokeElement(cdp, rowButton(c.productCode, '计算依据'), 'open material calculation')
  const drawer = `document.querySelector('.el-drawer')`
  await waitForPage(cdp, `${drawer}?.innerText.includes('16 × 3 / 1 × (1 + 0) = 48')`, 'BOM formula is visible')
  await screenshot(h, 'multilevel-trace')
  await invokeElement(cdp, `[...${drawer}.querySelectorAll('button')].find(b => b.innerText.includes('查看上层'))`, 'trace C to B')
  await waitForPage(cdp, `${drawer}.querySelector('h3')?.innerText.includes(${JSON.stringify(b.productCode)})`, 'B parent calculation')
  await invokeElement(cdp, `[...${drawer}.querySelectorAll('button')].find(b => b.innerText.includes('查看上层'))`, 'trace B to A')
  await waitForPage(cdp, `${drawer}.querySelector('h3')?.innerText.includes(${JSON.stringify(a.productCode)}) && ${drawer}.innerText.includes('安全库存目标')`, 'root safety requirement')
  await invokeElement(cdp, `${drawer}.querySelector('.el-drawer__close-btn')`, 'close calculation')

  const converted = []
  for (const [line, product] of [[cLine, c], [aLine, a], [bLine, b]]) {
    const button = line.suggestionType === 'PURCHASE' ? '转采购' : '转生产'
    await invokeElement(cdp, rowButton(product.productCode, button), `convert ${product.productCode}`)
    if (line.suggestionType === 'PURCHASE') await confirmPurchase(h, supplier)
    else await clickButton(cdp, '生成草稿订单', h.dialogExpression('生成订单'))
    await waitConverted(h, run.id, line)
    const refreshed = await apiRequest(auth, 'GET', `/inventory/mrp/runs/${run.id}`)
    const saved = [...refreshed.productionLines, ...refreshed.purchaseLines].find(l => String(l.id) === String(line.id))
    converted.push(saved)
    await waitForPage(cdp, `${rowFor(product.productCode)}?.innerText.includes(${JSON.stringify(saved.convertedBizNo)})`, 'converted order shown in row')
  }
  const po = await apiRequest(auth, 'GET', `/purchase/orders/${converted[0].convertedBizId}`)
  assert.equal(Number(po.lines[0].qty), 38)
  assert.equal(String(po.plannedWarehouseId), String(fixture.warehouse.id))
  const moA = await apiRequest(auth, 'GET', `/production/orders/${converted[1].convertedBizId}`)
  const moB = await apiRequest(auth, 'GET', `/production/orders/${converted[2].convertedBizId}`)
  assert.equal(Number(moA.plannedQty), 10); assert.equal(Number(moB.plannedQty), 16)
  assert.equal(String(moA.finishedWarehouseId), String(finishedWarehouse.id))
  assert.equal(String(moA.materialWarehouseId), String(fixture.warehouse.id))
  await screenshot(h, 'multilevel-converted')
  await clickButton(cdp, '运行计划')
  await waitForPage(cdp, `!${rowFor(c.productCode)} && !${rowFor(a.productCode)} && !${rowFor(b.productCode)}`, 'converted drafts cover all three shortages')
  const rerun = await latestRun(h)
  assert.notEqual(String(rerun.id), String(run.id))
  const plannedProducts = new Set(products.map(p => String(p.id)))
  assert([...rerun.purchaseLines, ...rerun.productionLines].every(l => !plannedProducts.has(String(l.productId))))
  const cNode = rerun.calculationSnapshot.nodes.find(n => String(n.productId) === String(c.id) && String(n.warehouseId) === String(fixture.warehouse.id))
  assert.equal(Number(cNode.netQty), 0)
  assert(cNode.sources.some(s => s.type === 'PRODUCTION_MATERIAL' && String(s.documentId) === String(moB.id)))
  return { name: 'mrp-multilevel-warehouse-trace-and-convert', passed: true, failures: [], runNo: run.runNo,
    expected: { productionA: 10, productionB: 16, purchaseC: 38 }, orders: converted.map(l => l.convertedBizNo),
    separateWarehouses: true, sourceTrace: true, rerunAvoidedDuplicateSupply: true }
}

export async function runMrpWarehouseUiSmoke(h) {
  const { auth, apiRequest, cdp, clickButton, invokeElement, waitForPage } = h
  const fixture = await h.prepareSalesOrderFixture(auth)
  const supplier = await createSupplier(h, `MRPW${fixture.suffix}`)
  const order = await apiRequest(auth, 'POST', '/purchase/orders', {
    supplierId: supplier.id, orderDate: fixture.orderDate,
    lines: [{ productId: fixture.product.id, qty: 2, price: 50, taxRate: 0 }]
  })
  await apiRequest(auth, 'POST', `/purchase/orders/${order.id}/submit`, { remark: 'MRP warehouse assignment regression' })
  await apiRequest(auth, 'POST', '/inventory/alert-rules', { warehouseId: fixture.warehouse.id, productId: fixture.product.id, minQty: 24 })
  await openMrp(h)
  await clickButton(cdp, '运行计划')
  await waitForPage(cdp, `document.body.innerText.includes(${JSON.stringify(order.orderNo)}) && document.body.innerText.includes('在途采购缺少预计收货仓库')`, 'missing warehouse warning lists source order')
  await invokeElement(cdp, rowButton(order.orderNo, '设置收货仓'), 'assign open purchase warehouse')
  const dialog = h.dialogExpression('设置收货仓')
  await invokeElement(cdp, `${dialog}.querySelector('.el-select__wrapper')`, 'open receiving warehouse choices')
  await invokeElement(cdp, optionFor(fixture.warehouse.warehouseName), 'select planned receiving warehouse')
  await clickButton(cdp, '保存', dialog)
  await waitForPage(cdp, `!(${dialog})`, 'warehouse assignment saved')
  const assigned = await apiRequest(auth, 'GET', `/purchase/orders/${order.id}`)
  assert.equal(String(assigned.plannedWarehouseId), String(fixture.warehouse.id))
  assert.equal(assigned.status, 'SUBMITTED')
  await clickButton(cdp, '运行计划')
  await waitForPage(cdp, `Number(${rowFor(fixture.product.productCode)}?.querySelectorAll('td')[2]?.innerText.trim()) === 2`, 'assigned incoming purchase nets only its warehouse')
  const run = await latestRun(h)
  assert(!run.calculationSnapshot.warnings.some(w => String(w.documentId) === String(order.id)))
  const line = run.purchaseLines.find(l => String(l.productId) === String(fixture.product.id))
  assert.equal(Number(line.openSupplyQty), 2)
  await invokeElement(cdp, rowButton(fixture.product.productCode, '转采购'), 'convert remaining shortage')
  await confirmPurchase(h, supplier)
  await waitConverted(h, run.id, line)
  return { name: 'mrp-purchase-warehouse-assignment', passed: true, failures: [], sourceOrderNo: order.orderNo,
    runNo: run.runNo, demand: 24, onHand: 20, assignedIncoming: 2, netQty: 2 }
}

const rowFor = code => `[...document.querySelectorAll('.el-table__body tr')].find(tr => tr.innerText.includes(${JSON.stringify(code)}))`
const rowButton = (code, label) => `[...(${rowFor(code)})?.querySelectorAll('button') || []].find(b => b.innerText.trim() === ${JSON.stringify(label)})`
const optionFor = label => `[...document.querySelectorAll('.el-select-dropdown__item')].find(e => e.getBoundingClientRect().height > 0 && e.innerText.includes(${JSON.stringify(label)}))`

async function openMrp(h) {
  await h.setBrowserAuth(h.cdp, h.auth)
  await h.cdp.send('Page.navigate', { url: `${h.frontendBaseUrl}/inventory/mrp` })
  await h.waitForPage(h.cdp, `document.body.innerText.includes('运行计划')`, 'MRP page ready')
}
async function latestRun(h) {
  const history = await h.apiRequest(h.auth, 'GET', '/inventory/mrp/runs?pageNo=1&pageSize=1')
  return h.apiRequest(h.auth, 'GET', `/inventory/mrp/runs/${history.records[0].id}`)
}
async function createSupplier(h, code) {
  return h.apiRequest(h.auth, 'POST', '/masterdata/suppliers', { supplierCode: code, supplierName: `MRP供应商${code}`, settlementMethod: 'BANK_TRANSFER' })
}
async function confirmPurchase(h, supplier) {
  const dialog = h.dialogExpression('生成订单')
  await h.invokeElement(h.cdp, `${dialog}.querySelector('.el-select__wrapper')`, 'open supplier choices')
  await h.invokeElement(h.cdp, optionFor(supplier.supplierName), 'select MRP supplier')
  await h.clickButton(h.cdp, '生成草稿订单', dialog)
}
async function waitConverted(h, runId, line) {
  await h.waitForPage(h.cdp, `(async () => {
    const response = await fetch('/api/inventory/mrp/runs/${runId}', { headers: { Authorization: 'Bearer ' + localStorage.getItem('token') } });
    const payload = await response.json();
    return [...(payload.data?.purchaseLines || []), ...(payload.data?.productionLines || [])].some(l => String(l.id) === ${JSON.stringify(String(line.id))} && l.status === 'CONVERTED');
  })()`, 'converted MRP line persisted')
}
async function stockMaterials(h, warehouseId, date, materials) {
  const adjustment = await h.apiRequest(h.auth, 'POST', '/inventory/adjustments', { warehouseId, adjustmentDate: date,
    lines: materials.map(([p, qty]) => ({ productId: p.id, direction: 'IN', qty, unitCost: 1, reason: 'MRP isolated stock fixture' })) })
  await h.approveInventoryAdjustmentFixture(h.auth, adjustment.id)
  await h.apiRequest(h.auth, 'POST', `/inventory/adjustments/${adjustment.id}/post`)
}
async function screenshot(h, name) {
  const result = await h.cdp.send('Page.captureScreenshot', { format: 'png', fromSurface: true })
  writeFileSync(join(h.targetDir, `ui-smoke-mrp-${name}.png`), Buffer.from(result.data, 'base64'))
}
