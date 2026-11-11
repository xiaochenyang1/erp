import assert from 'node:assert/strict'
import test from 'node:test'

import {
  verifyFinancialReportCloseCheck,
  verifyFinancialReportCloseSnapshot,
  verifyFinancialStatementDelta
} from './financial-statements-api-smoke.mjs'

const reportSet = () => ({
  trial: {
    periodDebit: 50,
    periodCredit: 50,
    openingBalanced: true,
    periodBalanced: true,
    closingBalanced: true
  },
  balance: {
    formulaVersion: 'TYPE_MAPPING_V1',
    totalAssets: 500,
    totalEquity: 300,
    balanced: true,
    unclassifiedSubjectCodes: [],
    mappingErrors: []
  },
  income: {
    formulaVersion: 'TYPE_MAPPING_V1',
    totalExpense: 40,
    netProfit: 160,
    unclassifiedSubjectCodes: [],
    mappingErrors: []
  },
  cash: {
    formulaVersion: 'CASH_FLOW_DIRECT_V1',
    netOperatingCashFlow: 80,
    netIncreaseInCash: 80,
    closingCash: 180,
    reconciled: true,
    unmappedSubjectCodes: [],
    mappingErrors: []
  }
})

test('financial statement smoke accepts the expected posted cash-expense deltas', () => {
  const before = reportSet()
  const after = structuredClone(before)
  after.trial.periodDebit += 128
  after.trial.periodCredit += 128
  after.balance.totalAssets -= 128
  after.balance.totalEquity -= 128
  after.income.totalExpense += 128
  after.income.netProfit -= 128
  after.cash.netOperatingCashFlow -= 128
  after.cash.netIncreaseInCash -= 128
  after.cash.closingCash -= 128

  assert.deepEqual(verifyFinancialStatementDelta(before, after, 128).cashFlowDelta, {
    operating: -128,
    netIncrease: -128,
    closingCash: -128
  })
})

test('financial statement smoke rejects a report that only looks successful', () => {
  const before = reportSet()
  const after = structuredClone(before)
  after.trial.periodDebit += 128
  after.trial.periodCredit += 128
  after.balance.totalAssets -= 128
  after.balance.totalEquity -= 128
  after.income.totalExpense += 128
  after.income.netProfit -= 128
  after.cash.netOperatingCashFlow -= 128
  after.cash.netIncreaseInCash -= 128
  after.cash.closingCash -= 127

  assert.throws(() => verifyFinancialStatementDelta(before, after, 128), /closing cash delta/)
})

const reportSnapshot = (reportType, formulaVersion) => ({
  id: `${reportType}-1`,
  reportType,
  formulaVersion,
  configurationDigest: 'a'.repeat(64),
  configuration: { formulaVersion },
  dateFrom: '2000-01-01',
  dateTo: '2000-01-31',
  verified: true,
  payloadSha256: 'b'.repeat(64),
  payload: { formulaVersion }
})

const closeSnapshot = () => ({
  id: '91',
  actionType: 'CLOSE',
  passed: true,
  issueCount: 0,
  items: [{
    code: 'FINANCIAL_REPORTS_RECONCILED',
    passed: true,
    metric: 0,
    message: 'all reports reconciled'
  }],
  financialReports: [
    reportSnapshot('TRIAL_BALANCE', 'LEDGER_TRIAL_BALANCE_V1'),
    reportSnapshot('BALANCE_SHEET', 'TYPE_MAPPING_V1'),
    reportSnapshot('INCOME_STATEMENT', 'TYPE_MAPPING_V1'),
    reportSnapshot('CASH_FLOW_DIRECT', 'CASH_FLOW_DIRECT_V1')
  ]
})

test('financial statement smoke accepts a complete close check and immutable snapshot set', () => {
  assert.equal(verifyFinancialReportCloseCheck({ checks: closeSnapshot().items }).metric, 0)
  assert.deepEqual(verifyFinancialReportCloseSnapshot(closeSnapshot()).reportTypes, [
    'TRIAL_BALANCE', 'BALANCE_SHEET', 'INCOME_STATEMENT', 'CASH_FLOW_DIRECT'
  ])
})

test('financial statement smoke rejects missing or tampered close evidence', () => {
  assert.throws(() => verifyFinancialReportCloseCheck({ checks: [] }), /missing FINANCIAL_REPORTS_RECONCILED/)
  const incomplete = closeSnapshot()
  incomplete.financialReports.pop()
  assert.throws(() => verifyFinancialReportCloseSnapshot(incomplete), /expected 4/)

  const tampered = closeSnapshot()
  tampered.financialReports[0].payloadSha256 = 'not-a-digest'
  assert.throws(() => verifyFinancialReportCloseSnapshot(tampered), /payload digest is not SHA-256/)
})
