/**
 * Financial-statement API smoke for trial balance, balance sheet, income
 * statement and direct-method cash flow. The scenario posts one balanced cash
 * expense voucher and proves the expected report deltas instead of relying on
 * an empty database.
 *
 * Usage:
 *   node scripts/financial-statements-api-smoke.mjs
 *   BASE_URL=http://127.0.0.1:8080 ADMIN_PASSWORD=LocalAdmin123 \
 *     node scripts/financial-statements-api-smoke.mjs
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const defaultBaseUrl = 'http://127.0.0.1:8080'
const epsilon = 0.001
const financialReportTypes = ['TRIAL_BALANCE', 'BALANCE_SHEET', 'INCOME_STATEMENT', 'CASH_FLOW_DIRECT']
const financialReportVersions = {
  TRIAL_BALANCE: 'LEDGER_TRIAL_BALANCE_V1',
  BALANCE_SHEET: 'TYPE_MAPPING_V1',
  INCOME_STATEMENT: 'TYPE_MAPPING_V1',
  CASH_FLOW_DIRECT: 'CASH_FLOW_DIRECT_V1'
}
const sha256Pattern = /^[a-f0-9]{64}$/i

const number = (value) => Number(value ?? 0)
const closeTo = (actual, expected) => Math.abs(number(actual) - number(expected)) < epsilon

const flatten = (items = []) => items.flatMap((item) => [item, ...flatten(item.children || [])])

const requireCheck = (condition, message) => {
  if (!condition) throw new Error(message)
}

const reports = async (request, date) => {
  const range = `dateFrom=${encodeURIComponent(date)}&dateTo=${encodeURIComponent(date)}`
  return {
    trial: await request('GET', `/finance/ledger/trial-balance?${range}`),
    balance: await request('GET', `/finance/financial-statements/balance-sheet?asOfDate=${encodeURIComponent(date)}`),
    income: await request('GET', `/finance/financial-statements/income-statement?${range}`),
    cash: await request('GET', `/finance/financial-statements/cash-flow?${range}`)
  }
}

export function verifyFinancialStatementDelta(before, after, amount) {
  requireCheck(after.trial.openingBalanced && after.trial.periodBalanced && after.trial.closingBalanced,
    'trial balance is not balanced')
  requireCheck(closeTo(number(after.trial.periodDebit) - number(before.trial.periodDebit), amount),
    'trial-balance debit delta does not match the posted voucher')
  requireCheck(closeTo(number(after.trial.periodCredit) - number(before.trial.periodCredit), amount),
    'trial-balance credit delta does not match the posted voucher')

  requireCheck(after.balance.formulaVersion === 'TYPE_MAPPING_V1', 'unexpected balance-sheet formula version')
  requireCheck(after.balance.balanced === true, 'balance sheet is not reconciled')
  requireCheck((after.balance.unclassifiedSubjectCodes || []).length === 0, 'balance sheet has unclassified subjects')
  requireCheck((after.balance.mappingErrors || []).length === 0, 'balance sheet has mapping errors')
  requireCheck(closeTo(number(after.balance.totalAssets) - number(before.balance.totalAssets), -amount),
    'balance-sheet asset delta does not match the cash payment')
  requireCheck(closeTo(number(after.balance.totalEquity) - number(before.balance.totalEquity), -amount),
    'balance-sheet equity delta does not match the expense')

  requireCheck(after.income.formulaVersion === 'TYPE_MAPPING_V1', 'unexpected income-statement formula version')
  requireCheck((after.income.unclassifiedSubjectCodes || []).length === 0, 'income statement has unclassified subjects')
  requireCheck((after.income.mappingErrors || []).length === 0, 'income statement has mapping errors')
  requireCheck(closeTo(number(after.income.totalExpense) - number(before.income.totalExpense), amount),
    'income-statement expense delta does not match the posted voucher')
  requireCheck(closeTo(number(after.income.netProfit) - number(before.income.netProfit), -amount),
    'income-statement profit delta does not match the posted voucher')

  requireCheck(after.cash.formulaVersion === 'CASH_FLOW_DIRECT_V1', 'unexpected cash-flow formula version')
  requireCheck(after.cash.reconciled === true, 'cash-flow statement is not reconciled')
  requireCheck((after.cash.unmappedSubjectCodes || []).length === 0, 'cash-flow statement has unmapped subjects')
  requireCheck((after.cash.mappingErrors || []).length === 0, 'cash-flow statement has mapping errors')
  requireCheck(closeTo(number(after.cash.netOperatingCashFlow) - number(before.cash.netOperatingCashFlow), -amount),
    'operating cash-flow delta does not match the cash payment')
  requireCheck(closeTo(number(after.cash.netIncreaseInCash) - number(before.cash.netIncreaseInCash), -amount),
    'net cash increase delta does not match the cash payment')
  requireCheck(closeTo(number(after.cash.closingCash) - number(before.cash.closingCash), -amount),
    'closing cash delta does not match the cash payment')

  return {
    trialBalanceDelta: { debit: amount, credit: amount },
    balanceSheetDelta: { assets: -amount, equity: -amount },
    incomeStatementDelta: { expense: amount, netProfit: -amount },
    cashFlowDelta: { operating: -amount, netIncrease: -amount, closingCash: -amount }
  }
}

export function verifyFinancialReportCloseCheck(check) {
  const item = (check?.checks || []).find((candidate) => candidate.code === 'FINANCIAL_REPORTS_RECONCILED')
  requireCheck(item, 'period close check is missing FINANCIAL_REPORTS_RECONCILED')
  requireCheck(item.passed === true, `financial-report close check failed: ${item.message || 'unknown error'}`)
  requireCheck(number(item.metric) === 0, 'financial-report close check has a non-zero failure metric')
  return { code: item.code, message: item.message, metric: number(item.metric) }
}

export function verifyFinancialReportCloseSnapshot(snapshot) {
  requireCheck(snapshot?.actionType === 'CLOSE', 'financial-report evidence is not a CLOSE snapshot')
  requireCheck(snapshot?.passed === true && number(snapshot?.issueCount) === 0,
    'financial-report close snapshot is not a successful zero-issue snapshot')
  verifyFinancialReportCloseCheck({ checks: snapshot.items || [] })

  const reports = snapshot.financialReports || []
  requireCheck(reports.length === financialReportTypes.length,
    `expected ${financialReportTypes.length} financial-report snapshots, got ${reports.length}`)
  const actualTypes = reports.map((report) => report.reportType)
  requireCheck(new Set(actualTypes).size === financialReportTypes.length,
    'financial-report snapshot contains duplicate report types')
  requireCheck(financialReportTypes.every((reportType) => actualTypes.includes(reportType)),
    `financial-report snapshot types are incomplete: ${actualTypes.join(', ')}`)

  for (const report of reports) {
    requireCheck(report.verified === true, `${report.reportType} snapshot is not verified`)
    requireCheck(report.formulaVersion === financialReportVersions[report.reportType],
      `${report.reportType} snapshot has unexpected formula version`)
    requireCheck(sha256Pattern.test(report.configurationDigest || ''),
      `${report.reportType} configuration digest is not SHA-256`)
    requireCheck(sha256Pattern.test(report.payloadSha256 || ''),
      `${report.reportType} payload digest is not SHA-256`)
    requireCheck(report.configuration && typeof report.configuration === 'object',
      `${report.reportType} configuration payload is missing`)
    requireCheck(report.payload && typeof report.payload === 'object',
      `${report.reportType} report payload is missing`)
    requireCheck(Boolean(report.dateFrom) && Boolean(report.dateTo),
      `${report.reportType} snapshot date range is missing`)
  }

  return {
    snapshotId: String(snapshot.id),
    reportTypes: financialReportTypes,
    payloadDigests: Object.fromEntries(reports.map((report) => [report.reportType, report.payloadSha256]))
  }
}

export async function exerciseFinancialReportCloseSnapshot(request, options = {}) {
  const startYear = Number(options.startYear || 2000)
  const endYear = Number(options.endYear || 2199)
  let period
  for (let year = startYear; year <= endYear && !period; year += 1) {
    let periods = await request('GET', `/finance/periods?year=${year}`)
    if (!periods?.length) periods = await request('POST', '/finance/periods/generate', { year })
    period = (periods || []).find((candidate) => candidate.status !== 'CLOSED')
  }
  requireCheck(period?.id, `no reusable smoke accounting period found in ${startYear}-${endYear}`)
  requireCheck(['OPEN', 'LOCKED'].includes(period.status),
    `smoke accounting period ${period.periodMonth} has unsupported status ${period.status}`)

  const closeCheck = await request('GET', `/finance/periods/${period.id}/close-check`)
  const closeCheckEvidence = verifyFinancialReportCloseCheck(closeCheck)
  if (period.status === 'OPEN') {
    const locked = await request('POST', `/finance/periods/${period.id}/lock`)
    requireCheck(locked?.status === 'LOCKED', `account period ${period.periodMonth} was not locked`)
  }
  const closed = await request('POST', `/finance/periods/${period.id}/close`)
  requireCheck(closed?.status === 'CLOSED', `account period ${period.periodMonth} was not closed`)

  const snapshots = await request('GET', `/finance/periods/${period.id}/close-snapshots`)
  const closeSnapshot = (snapshots || []).find((snapshot) => snapshot.actionType === 'CLOSE')
  const snapshotEvidence = verifyFinancialReportCloseSnapshot(closeSnapshot)
  return {
    periodId: String(period.id),
    periodYear: period.periodYear,
    periodMonth: period.periodMonth,
    closeCheck: closeCheckEvidence,
    ...snapshotEvidence
  }
}

export async function exerciseFinancialStatements(request, options = {}) {
  const date = options.date || new Date().toISOString().slice(0, 10)
  const amount = Number(options.amount || 128)
  const suffix = options.suffix || String(Date.now()).slice(-10)
  const year = Number(date.slice(0, 4))
  const periodMonth = date.slice(0, 7)

  let periods = await request('GET', `/finance/periods?year=${year}`)
  if (!(periods || []).some((period) => period.periodMonth === periodMonth)) {
    periods = await request('POST', '/finance/periods/generate', { year })
  }
  const period = (periods || []).find((item) => item.periodMonth === periodMonth)
  requireCheck(period?.status === 'OPEN', `account period ${periodMonth} is not open`)

  const subjects = flatten(await request('GET', '/finance/account-subjects/tree'))
  const cashSubject = subjects.find((subject) => (subject.code || subject.subjectCode) === '1002' && subject.status === 'ACTIVE')
  const expenseSubject = subjects.find((subject) => (subject.code || subject.subjectCode) === '6602' && subject.status === 'ACTIVE')
  requireCheck(cashSubject?.id, 'active cash subject 1002 is missing')
  requireCheck(expenseSubject?.id, 'active expense subject 6602 is missing')

  const balanceConfiguration = await request('GET', '/finance/financial-statements/configuration?statementType=BALANCE_SHEET')
  const incomeConfiguration = await request('GET', '/finance/financial-statements/configuration?statementType=INCOME_STATEMENT')
  const cashConfiguration = await request('GET', '/finance/financial-statements/cash-flow/configuration')
  requireCheck(balanceConfiguration.formulaVersion === 'TYPE_MAPPING_V1', 'balance-sheet mapping version is missing')
  requireCheck(incomeConfiguration.formulaVersion === 'TYPE_MAPPING_V1', 'income-statement mapping version is missing')
  requireCheck((balanceConfiguration.validationErrors || []).length === 0, 'balance-sheet mapping configuration is invalid')
  requireCheck((incomeConfiguration.validationErrors || []).length === 0, 'income-statement mapping configuration is invalid')
  requireCheck(cashConfiguration.formulaVersion === 'CASH_FLOW_DIRECT_V1', 'cash-flow mapping version is missing')
  requireCheck((cashConfiguration.validationErrors || []).length === 0, 'cash-flow mapping configuration is invalid')
  requireCheck((cashConfiguration.cashSubjectCodes || []).includes('1002'), 'cash-flow cash scope does not include 1002')
  requireCheck((cashConfiguration.items || []).some((item) => (item.subjectCodes || []).includes('6602')),
    'cash-flow counterpart mapping does not include 6602')

  const before = await reports(request, date)
  const voucher = await request('POST', '/finance/vouchers/manual', {
    bizDate: date,
    remark: `${options.remarkPrefix || 'Financial statement smoke'} ${suffix}`,
    lines: [
      { subjectId: expenseSubject.id, debitAmount: amount, creditAmount: 0, summary: 'Operating cash expense' },
      { subjectId: cashSubject.id, debitAmount: 0, creditAmount: amount, summary: 'Cash payment' }
    ]
  })
  requireCheck(voucher?.id, 'manual voucher creation did not return an id')
  await request('POST', `/finance/vouchers/manual/${voucher.id}/submit`)
  await request('POST', `/finance/vouchers/manual/${voucher.id}/approve`)
  await request('POST', `/finance/vouchers/manual/${voucher.id}/post`)
  const posted = await request('GET', `/finance/vouchers/manual/${voucher.id}`)
  requireCheck(posted?.status === 'POSTED' && posted?.postedVoucherId, 'manual voucher was not posted')

  const after = await reports(request, date)
  const delta = verifyFinancialStatementDelta(before, after, amount)
  return {
    date,
    amount,
    voucherId: String(voucher.id),
    voucherNo: posted.voucherNo,
    postedVoucherId: String(posted.postedVoucherId),
    delta,
    reports: after
  }
}

async function main() {
  const baseUrl = (process.env.BASE_URL || defaultBaseUrl).replace(/\/$/, '')
  const username = process.env.ADMIN_USER || 'admin'
  const password = process.env.ADMIN_PASSWORD || 'LocalAdmin123'
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const loginPayload = await response.json()
  requireCheck(response.ok && loginPayload?.code === '0' && loginPayload?.data?.accessToken,
    `login failed: HTTP ${response.status} ${loginPayload?.message || ''}`)
  const token = loginPayload.data.accessToken
  const request = async (method, path, body) => {
    const apiResponse = await fetch(`${baseUrl}/api${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        ...(body == null ? {} : { 'Content-Type': 'application/json' })
      },
      body: body == null ? undefined : JSON.stringify(body)
    })
    const text = await apiResponse.text()
    const payload = text ? JSON.parse(text) : null
    requireCheck(apiResponse.ok && payload?.code === '0',
      `${method} ${path} failed: HTTP ${apiResponse.status} ${text.slice(0, 1000)}`)
    return payload.data
  }

  const statementEvidence = await exerciseFinancialStatements(request)
  const closeSnapshot = await exerciseFinancialReportCloseSnapshot(request)
  const evidence = { ...statementEvidence, closeSnapshot }
  const outputDir = join(scriptDir, '..', 'target', 'financial-statements-api-smoke')
  mkdirSync(outputDir, { recursive: true })
  const reportPath = join(outputDir, `report-${Date.now()}.json`)
  writeFileSync(reportPath, JSON.stringify({ baseUrl, username, checkedAt: new Date().toISOString(), evidence }, null, 2))
  console.log(`[PASS] FS-1 posted voucher ${evidence.voucherNo}`)
  console.log('[PASS] FS-2 trial balance debit and credit deltas reconcile')
  console.log('[PASS] FS-3 balance sheet and income statement deltas reconcile')
  console.log('[PASS] FS-4 direct cash flow reconciles to closing cash')
  console.log(`[PASS] FS-5 period ${closeSnapshot.periodMonth} captured four immutable financial-report snapshots`)
  console.log(`REPORT ${reportPath}`)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.stack || error.message || String(error))
    process.exitCode = 1
  })
}
