import assert from 'node:assert/strict'
import test from 'node:test'

import { interfaceIncludes } from './check-contract-utils.mjs'

test('interfaceIncludes only checks the selected interface', () => {
  const source = `
export interface LedgerSummary {
  subjectCode: string
  debitAmount: number
}

export interface PartnerStatementLine {
  amount: number
  balance: number
}
`

  assert.equal(interfaceIncludes(source, 'LedgerSummary', 'balance: number'), false)
  assert.equal(interfaceIncludes(source, 'PartnerStatementLine', 'balance: number'), true)
})
