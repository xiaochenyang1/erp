// These scenarios use the same isolated database and browser as ui-smoke.mjs.
export async function runCurrencyDocumentsUiSmoke(h) {
  const { cdp, auth, apiRequest, setBrowserAuth, frontendBaseUrl, prepareSalesOrderFixture,
    waitForPage, invokeElement, clickButton, setElementValue, dialogExpression } = h
  const fixture = await prepareSalesOrderFixture(auth)
  await ensureCurrencyRate(h, 'USD', 7.2, fixture.orderDate)
  const request = {
    contractType: 'SALES', customerId: fixture.customer.id, contractName: `UI FX contract ${fixture.suffix}`,
    signedDate: fixture.orderDate, effectiveFrom: fixture.orderDate,
    currencyCode: 'USD', exchangeRate: 7.2,
    lines: [{ productId: fixture.product.id, quantity: 1, unitPrice: 100 }]
  }
  const contract = await apiRequest(auth, 'POST', '/contracts', request)
  await setBrowserAuth(cdp, auth)
  await cdp.send('Page.navigate', { url: `${frontendBaseUrl}/contracts` })
  await waitForPage(cdp, `document.body.innerText.includes(${JSON.stringify(contract.contractNo)})`, 'foreign contract listed')
  await invokeElement(cdp, rowButton(contract.contractNo, '编辑'), 'edit foreign contract')
  const contractDialog = dialogExpression('编辑合同')
  await waitForPage(cdp, `${contractDialog}?.innerText.includes('USD')`, 'contract currency loaded')
  await setElementValue(cdp, `${contractDialog}.querySelector('textarea')`, 'UI FX contract edited', 'contract note')
  await clickButton(cdp, '保存', contractDialog)
  await waitForPage(cdp, changedDocument('/contracts/' + contract.id, 'UI FX contract edited'), 'contract saved with currency')
  const savedContract = await apiRequest(auth, 'GET', `/contracts/${contract.id}`)
  assertSnapshot(savedContract, 'baseTotalAmount')
  const versions = await apiRequest(auth, 'GET', `/contracts/${contract.id}/versions`)
  const original = versions.find(item => item.eventType === 'CREATED')
  assertSnapshot(original.header, 'baseTotalAmount')
  const restored = await apiRequest(auth, 'POST', `/contracts/${contract.id}/versions/${original.id}/restore`)
  assertSnapshot(restored, 'baseTotalAmount')

  const invoice = await apiRequest(auth, 'POST', '/finance/invoices', {
    invoiceType: 'OUTPUT', partnerName: fixture.customer.customerName, invoiceDate: fixture.orderDate,
    amount: 100, taxAmount: 10, currencyCode: 'USD', exchangeRate: 7.2, remark: 'UI FX invoice'
  })
  await cdp.send('Page.navigate', { url: `${frontendBaseUrl}/finance/invoices` })
  await waitForPage(cdp, `document.body.innerText.includes(${JSON.stringify(invoice.invoiceNo)})`, 'foreign invoice listed')
  await invokeElement(cdp, rowButton(invoice.invoiceNo, '编辑'), 'edit foreign invoice')
  const invoiceDialog = dialogExpression('编辑登记')
  await waitForPage(cdp, `${invoiceDialog}?.innerText.includes('USD')`, 'invoice currency loaded')
  await setElementValue(cdp, `${invoiceDialog}.querySelector('textarea')`, 'UI FX invoice edited', 'invoice note')
  await clickButton(cdp, '保存', invoiceDialog)
  await waitForPage(cdp, changedDocument('/finance/invoices/' + invoice.id, 'UI FX invoice edited'), 'invoice saved with currency')
  assertSnapshot(await apiRequest(auth, 'GET', `/finance/invoices/${invoice.id}`), 'baseAmount')
  return { name: 'currency-contract-invoice-edit-and-version', passed: true, failures: [],
    contractNo: contract.contractNo, restoredContractNo: restored.contractNo, invoiceNo: invoice.invoiceNo }
}

export async function runRoundingReportUiSmoke(h) {
  const { cdp, auth, apiRequest, preparePostedSalesDeliveryFixture, setBrowserAuth, frontendBaseUrl, waitForPage } = h
  const date = new Date().toISOString().slice(0, 10)
  // XTS is the ISO code reserved for testing, avoiding changes to a business currency's rates.
  await ensureCurrencyRate(h, 'XTS', 2.5, date)
  const fixture = await preparePostedSalesDeliveryFixture(auth, { currencyCode: 'XTS', exchangeRate: 2.5 })
  let receipt
  for (let i = 0; i < 2; i++) {
    receipt = await apiRequest(auth, 'POST', '/finance/receipts', {
      customerId: fixture.customer.id, receiptDate: fixture.orderDate, currencyCode: 'XTS', exchangeRate: 2.5,
      amount: 0.01, allocations: [{ receivableId: fixture.receivable.id, amount: 0.01 }],
      remark: `UI rounding partial ${i}`
    })
  }
  const report = await apiRequest(auth, 'GET', `/reports/fx-gain-loss?pageNo=1&pageSize=20&currencyCode=XTS&partnerId=${fixture.customer.id}`)
  const row = report.records.find(item => item.settlementNo === receipt.receiptNo)
  if (!row || Math.abs(Number(row.roundingAmount) - 0.01) > 0.000001 || Number(row.fxGainLossAmount) !== 0 || !row.postingSnapshot) {
    throw new Error(`Rounding report disagrees with the partial settlement: ${JSON.stringify(row)}`)
  }
  await setBrowserAuth(cdp, auth)
  await cdp.send('Page.navigate', { url: `${frontendBaseUrl}/reports?tab=fxGainLoss&keyword=XTS` })
  await waitForPage(cdp, `(() => {
    const table = document.querySelector('.el-table');
    if (!table) return false;
    const headers = [...table.querySelectorAll('.el-table__header-wrapper th')].map(cell => cell.innerText.trim());
    const row = [...table.querySelectorAll('.el-table__body-wrapper tbody tr')]
      .find(item => item.innerText.includes(${JSON.stringify(receipt.receiptNo)}));
    if (!row) return false;
    const cells = [...row.querySelectorAll('td')];
    const cell = label => cells[headers.indexOf(label)]?.innerText.trim();
    const amount = label => Number(cell(label)?.replace(/[^0-9.-]/g, ''));
    return amount('尾差调整') === 0.01 && amount('汇兑损益') === 0
      && amount('损益合计') === 0.01 && cell('金额口径') === '凭证金额';
  })()`, 'rounding and total amounts rendered in the receipt row')
  return { name: 'currency-rounding-report', passed: true, failures: [], receiptNo: receipt.receiptNo, roundingAmount: row.roundingAmount }
}

async function ensureCurrencyRate({ apiRequest, auth }, code, rate, date) {
  const currencies = await apiRequest(auth, 'GET', '/finance/currencies')
  if (!currencies.some(item => item.currencyCode === code)) {
    await apiRequest(auth, 'POST', '/finance/currencies', { currencyCode: code, currencyName: `UI smoke ${code}`, decimalPlaces: 2 })
  }
  const base = await apiRequest(auth, 'GET', '/finance/currencies/base')
  if (base !== 'CNY') throw new Error('Currency UI smoke requires an isolated CNY account book')
  const rates = await apiRequest(auth, 'GET', `/finance/currencies/rates?from=${code}&to=CNY`)
  const existing = rates.find(item => item.effectiveFrom === date && item.status === 'ENABLED')
  if (existing) {
    if (Number(existing.rate) !== rate) throw new Error(`Existing ${code} fixture rate differs; use an isolated database`)
  } else {
    await apiRequest(auth, 'POST', '/finance/currencies/rates', { fromCurrencyCode: code, toCurrencyCode: 'CNY', rate, effectiveFrom: date })
  }
}

function rowButton(no, label) {
  return `(() => {
    const row = [...document.querySelectorAll('.el-table__body-wrapper tbody tr')].find(item => item.innerText.includes(${JSON.stringify(no)}));
    return row && [...row.querySelectorAll('button')].find(button => button.innerText.trim() === ${JSON.stringify(label)});
  })()`
}

function changedDocument(path, remark) {
  return `(async () => {
    const response = await fetch('/api' + ${JSON.stringify(path)}, { headers: { Authorization: 'Bearer ' + localStorage.getItem('token') } });
    if (!response.ok) return false;
    const doc = (await response.json()).data;
    return doc?.remark === ${JSON.stringify(remark)} && doc.currencyCode === 'USD' && Number(doc.exchangeRate) === 7.2;
  })()`
}

function assertSnapshot(doc, amountField) {
  if (doc?.currencyCode !== 'USD' || Number(doc.exchangeRate) !== 7.2 || Number(doc[amountField]) !== 720) {
    throw new Error(`Currency snapshot lost: ${JSON.stringify(doc)}`)
  }
}
