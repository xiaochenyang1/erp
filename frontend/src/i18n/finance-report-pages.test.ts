import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { financeReportPageMessages } from './finance-report-pages'

const componentPaths = [
  'src/views/finance/subjects/index.vue',
  'src/views/finance/invoices/index.vue',
  'src/views/finance/ledger/index.vue',
  'src/views/finance/vouchers/index.vue',
  'src/composables/useVoucherList.ts',
  'src/composables/useVoucherPresentation.ts',
  'src/views/finance/periods/index.vue',
  'src/views/finance/funds/index.vue',
  'src/views/finance/expenses/index.vue',
  'src/views/finance/budgets/index.vue',
  'src/views/finance/payments/index.vue',
  'src/views/finance/vouchers/manual/index.vue',
  'src/views/reports/index.vue',
  'src/composables/useReportPresentation.ts',
  'src/composables/useReportList.ts',
  'src/views/reports/traces/index.vue'
] as const

const readNestedValue = (source: unknown, path: string): unknown => (
  path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined
    return (current as Record<string, unknown>)[segment]
  }, source)
)

const stripComments = (source: string) => source
  .replace(/<!--[\s\S]*?-->/g, '')
  .replace(/^\s*\/\/.*$/gm, '')

describe('finance and report page localization', () => {
  it('keeps a single non-conflicting top-level namespace in both locales', () => {
    expect(Object.keys(financeReportPageMessages['zh-CN'])).toEqual(['financeReportPages'])
    expect(Object.keys(financeReportPageMessages['en-US'])).toEqual(['financeReportPages'])
  })

  it('defines every page-referenced key in both locales', () => {
    const keys = new Set<string>()
    for (const path of componentPaths) {
      const source = readFileSync(resolve(process.cwd(), path), 'utf8')
      for (const match of source.matchAll(/financeReportPages(?:\.[A-Za-z0-9_]+)+/g)) {
        if (source.slice((match.index || 0) + match[0].length).startsWith('.${')) continue
        keys.add(match[0])
      }
    }

    expect(keys.size).toBeGreaterThan(100)
    for (const locale of ['zh-CN', 'en-US'] as const) {
      for (const key of keys) {
        expect(typeof readNestedValue(financeReportPageMessages[locale], key), `${locale}:${key}`)
          .toBe('string')
      }
    }
  })

  it('provides representative English labels, statuses, feedback, and file names', () => {
    const english = financeReportPageMessages['en-US']

    expect(readNestedValue(english, 'financeReportPages.subjects.categoryValue.asset')).toBe('Asset')
    expect(readNestedValue(english, 'financeReportPages.invoices.message.posted')).toBe('Invoice confirmed')
    expect(readNestedValue(english, 'financeReportPages.ledger.fileName')).toBe('general_ledger_{timestamp}.csv')
    expect(readNestedValue(english, 'financeReportPages.vouchers.sourceValue.expenseReversal')).toBe('Expense reversal voucher')
    expect(readNestedValue(english, 'financeReportPages.periods.status.locked')).toBe('Locked')
    expect(readNestedValue(english, 'financeReportPages.funds.message.unmatchTitle')).toBe('Unmatch')
    expect(readNestedValue(english, 'financeReportPages.expenses.status.posted')).toBe('Posted')
    expect(readNestedValue(english, 'financeReportPages.budgets.message.executionLoadFailed')).toBe('Failed to load budget execution')
    expect(readNestedValue(english, 'financeReportPages.payments.validation.allocationRequired')).toBe('Enter at least one allocation amount')
    expect(readNestedValue(english, 'financeReportPages.manualVouchers.message.posted')).toBe('Voucher posted')
    expect(readNestedValue(english, 'financeReportPages.reports.fileName')).toBe('{report}-{timestamp}.csv')
    expect(readNestedValue(english, 'financeReportPages.traces.ticketStatus.processing')).toBe('In progress')
  })

  it('leaves no hard-coded Chinese UI copy or static copy attributes in the migrated pages', () => {
    for (const path of componentPaths) {
      const source = stripComments(readFileSync(resolve(process.cwd(), path), 'utf8'))

      expect(source, path).not.toMatch(/[\u3400-\u9fff]/)
      expect(source, path).not.toMatch(/\s(?:label|placeholder|title|description|empty-text|range-separator|start-placeholder|end-placeholder|aria-label)="[^"]+"/)
      expect(source, path).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
    }
  })
})
