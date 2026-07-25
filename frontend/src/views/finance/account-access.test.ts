import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  canLoadFinanceAccountOptions,
  canViewFinanceAccountTab,
  resolveFinanceAccountTab
} from './account-access'

const checker = (...permissions: string[]) => (permission: string) => permissions.includes(permission)

describe('finance account access', () => {
  it('keeps a single-permission user on the only authorized tab', () => {
    const receivableOnly = checker('finance:receivable:view')
    const payableOnly = checker('finance:payable:view')

    expect(resolveFinanceAccountTab('/finance/payables', receivableOnly)).toBe('receivables')
    expect(resolveFinanceAccountTab('/finance/receivables', payableOnly)).toBe('payables')
    expect(canViewFinanceAccountTab('payables', receivableOnly)).toBe(false)
  })

  it('returns no tab when neither finance permission is available', () => {
    expect(resolveFinanceAccountTab('/finance/receivables', checker())).toBeNull()
  })

  it('loads filter options only when both finance and master-data permissions exist', () => {
    expect(canLoadFinanceAccountOptions(
      'receivables',
      checker('finance:receivable:view', 'masterdata:customer:view')
    )).toBe(true)
    expect(canLoadFinanceAccountOptions('receivables', checker('finance:receivable:view'))).toBe(false)
    expect(canLoadFinanceAccountOptions('payables', checker('masterdata:supplier:view'))).toBe(false)
  })

  it('hides master-data filters when their option permission is unavailable', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/views/finance/FinanceAccountsPage.vue'),
      'utf8'
    )

    expect(source).toContain('v-if="canLoadReceivableOptions"')
    expect(source).toContain('v-if="canLoadPayableOptions"')
    expect(source).toContain("computed(() => canLoadFinanceAccountOptions('receivables', userStore.hasPermission))")
    expect(source).toContain("computed(() => canLoadFinanceAccountOptions('payables', userStore.hasPermission))")
  })
})
