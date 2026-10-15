// Runs inside ui-smoke.mjs's disposable database and browser session.
export async function runMrpUiSmoke(h) {
  const { cdp, auth, apiRequest, setBrowserAuth, frontendBaseUrl, prepareSalesOrderFixture,
    waitForPage, invokeElement, clickButton } = h
  const fixture = await prepareSalesOrderFixture(auth) // 20 on hand; no sales demand.
  await apiRequest(auth, 'POST', '/masterdata/suppliers', {
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
  await waitForPage(cdp, `(async () => {
    const token = localStorage.getItem('token');
    const response = await fetch('/api/inventory/mrp/runs/${runId}', { headers: { Authorization: 'Bearer ' + token } });
    const payload = await response.json();
    return payload.data?.purchaseLines?.some(l => String(l.id) === ${JSON.stringify(String(line.id))} && l.status === 'CONVERTED');
  })()`, 'MRP conversion persisted')
  const refreshed = await apiRequest(auth, 'GET', `/inventory/mrp/runs/${runId}`)
  const converted = refreshed.purchaseLines.find(item => String(item.id) === String(line.id))
  const order = await apiRequest(auth, 'GET', `/purchase/orders/${converted.convertedBizId}`)
  if (order.lines.length !== 1 || Number(order.lines[0].qty) !== 4) {
    throw new Error(`Converted PO differs from MRP: ${JSON.stringify(order.lines)}`)
  }
  await waitForPage(cdp, `${row}?.innerText.includes(${JSON.stringify(converted.convertedBizNo)})`, 'MRP row shows converted PO')
  return { name: 'mrp-safety-stock-run-and-convert', passed: true, failures: [], runNo: run.runNo,
    purchaseOrderNo: converted.convertedBizNo, expectedQty: 4, actualQty: Number(order.lines[0].qty) }
}
