import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { FundAccount } from '@/api/fund'
import { useFundPresentation } from './useFundPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.id != null ? `${key}:${params.id}` : key

const account = (overrides: Partial<FundAccount> = {}) => ({
  id: '1',
  accountCode: 'BANK001',
  accountName: '工行基本户',
  accountType: 'BANK',
  currencyCode: 'CNY',
  openingBalance: 0,
  status: 'ENABLED',
  ...overrides
} as FundAccount)

describe('fund presentation', () => {
  it('resolves statement account names from the loaded options', () => {
    const accounts = ref<FundAccount[]>([account()])
    const { accountName } = useFundPresentation(t, accounts)

    expect(accountName('1')).toBe('BANK001 - 工行基本户')
    expect(accountName(1)).toBe('BANK001 - 工行基本户')
    expect(accountName('9')).toBe('financeReportPages.funds.accountFallback:9')

    accounts.value = [account({ id: '9', accountCode: 'CASH001', accountName: '现金' })]
    expect(accountName('9')).toBe('CASH001 - 现金')
    expect(accountName('1')).toBe('financeReportPages.funds.accountFallback:1')
  })

  it('translates known enums and falls back to the raw value', () => {
    const {
      accountStatusLabel,
      accountTypeLabel,
      businessTypeLabel,
      statementDirectionLabel,
      statementStatusLabel
    } = useFundPresentation(t, ref([]))

    expect(accountTypeLabel('BANK')).toBe('financeReportPages.funds.accountType.bank')
    expect(accountTypeLabel('CASH')).toBe('financeReportPages.funds.accountType.cash')
    expect(accountTypeLabel('OTHER')).toBe('OTHER')
    expect(accountTypeLabel()).toBe('')

    expect(accountStatusLabel('ENABLED')).toBe('financeReportPages.funds.enabled')
    expect(accountStatusLabel('DISABLED')).toBe('financeReportPages.funds.disabled')

    expect(statementDirectionLabel('IN')).toBe('financeReportPages.funds.income')
    expect(statementDirectionLabel('OUT')).toBe('financeReportPages.funds.expense')

    expect(statementStatusLabel('MATCHED')).toBe('financeReportPages.funds.matched')
    expect(statementStatusLabel('UNMATCHED')).toBe('financeReportPages.funds.unmatched')

    expect(businessTypeLabel('RECEIPT')).toBe('financeReportPages.funds.receipt')
    expect(businessTypeLabel('PAYMENT')).toBe('financeReportPages.funds.payment')
    // Unmatched statements have no business type, so the detail shows a dash.
    expect(businessTypeLabel()).toBe('-')
  })

  it('keeps two decimals for money and treats missing amounts as zero', () => {
    const { formatMoney } = useFundPresentation(t, ref([]))

    expect(formatMoney(1234.5)).toBe('1,234.50')
    expect(formatMoney(0)).toBe('0.00')
    expect(formatMoney()).toBe('0.00')
  })
})
