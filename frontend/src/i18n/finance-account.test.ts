import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

import { i18n, setI18nLocale, type SupportedLocale } from './index'

const requiredKeys = [
  'tabs.receivables', 'tabs.payables',
  'receivableNo', 'receivableNoPlaceholder', 'payableNo', 'payableNoPlaceholder',
  'customer', 'selectCustomer', 'supplier', 'selectSupplier', 'statusLabel', 'selectStatus',
  'search', 'export', 'sourceNo', 'bizDate', 'createdTime', 'updatedTime', 'actions', 'view',
  'receivableAmount', 'receivedAmount', 'unreceivedAmount',
  'payableAmount', 'paidAmount', 'unpaidAmount',
  'status.unsettled', 'status.partiallySettled', 'status.settled', 'status.offset',
  'dialog.receivable', 'dialog.payable', 'file.receivables', 'file.payables',
  'message.receivablesLoadFailed', 'message.payablesLoadFailed',
  'message.exported', 'message.exportFailed',
  'message.receivableDetailLoadFailed', 'message.payableDetailLoadFailed',
  'message.customersLoadFailed', 'message.suppliersLoadFailed'
] as const

const readNestedValue = (source: unknown, path: string): unknown => (
  path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined
    return (current as Record<string, unknown>)[segment]
  }, source)
)

describe('finance account localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  for (const locale of ['zh-CN', 'en-US'] satisfies SupportedLocale[]) {
    it(`defines every finance account key in ${locale}`, () => {
      const localeMessages = i18n.global.getLocaleMessage(locale)
      for (const key of requiredKeys) {
        expect(typeof readNestedValue(localeMessages, `financeAccount.${key}`)).toBe('string')
      }
    })
  }

  it('renders representative English labels, status, feedback, and file names', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('financeAccount.tabs.receivables')).toBe('Receivables')
    expect(i18n.global.t('financeAccount.status.partiallySettled')).toBe('Partially settled')
    expect(i18n.global.t('financeAccount.sourceNo')).toBe('Source no.')
    expect(i18n.global.t('financeAccount.message.payableDetailLoadFailed')).toBe('Failed to load payable details')
    expect(i18n.global.t('financeAccount.file.payables', { timestamp: 123 })).toBe('payables_123.csv')
  })

  it('keeps the shared page template free of hard-coded Chinese UI copy', () => {
    const componentPath = resolve(process.cwd(), 'src/views/finance/FinanceAccountsPage.vue')
    const source = readFileSync(componentPath, 'utf8')

    expect(source).not.toMatch(/[\u3400-\u9fff]/)
    expect(source).not.toMatch(/¥|\.toFixed\(/)
    expect(source).not.toMatch(/\s(?:label|placeholder|title)="[^"]+"/)
    expect(source).not.toMatch(/ElMessage\.(?:error|success)\(\s*['"`]/)
    expect(source).not.toMatch(/\b(?:orderNo|dueDate|createdAt|updatedAt)\b/)
    expect(source).toContain('prop="sourceNo"')
    expect(source).toContain('row.bizDate')
    expect(source).toContain('row.createdTime')
    for (const status of ['UNSETTLED', 'PARTIALLY_SETTLED', 'SETTLED', 'OFFSET']) {
      expect(source).toContain(status)
    }
    expect(source).toContain('formatLocalizedCurrency')
    expect(source).toContain('formatLocalizedDateTime')
  })
})
