import assert from 'node:assert/strict'
import test from 'node:test'

import { verifyFinancialStatementDelta } from './financial-statements-api-smoke.mjs'

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
