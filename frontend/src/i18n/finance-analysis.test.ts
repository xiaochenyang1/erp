import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

import { i18n, setI18nLocale, type SupportedLocale } from './index'

const requiredKeys = {
  financeAging: [
    'asOfDate', 'todayPlaceholder', 'search', 'reset', 'receivableTotal', 'payableTotal',
    'asOfDateValue', 'outstandingOnly', 'receivableBuckets', 'payableBuckets', 'bucket',
    'count', 'amount', 'overdueReceivables', 'overduePayables', 'receivablesLedger',
    'payablesLedger', 'receivableNo', 'payableNo', 'customer', 'supplier', 'bizDate',
    'agingDays', 'outstandingAmount', 'bucketLabel.d0_30', 'bucketLabel.d31_60',
    'bucketLabel.d61_90', 'bucketLabel.d90Plus', 'message.loadFailed'
  ],
  financeGrossMargin: [
    'period', 'startDate', 'endDate', 'search', 'salesAmount', 'costApprox', 'grossMargin',
    'marginRate', 'costNotice', 'productCode', 'productName', 'salesQuantity', 'costAmount',
    'message.selectRange', 'message.loadFailed'
  ],
  financeStatement: [
    'partnerType', 'customer', 'supplier', 'selectPartner', 'period', 'startDate', 'endDate',
    'search', 'partnerTypeValue', 'periodValue', 'openingValue', 'increaseValue',
    'decreaseValue', 'closingValue', 'date', 'docType', 'docNo', 'direction', 'amount',
    'balance', 'remark', 'document.receivable', 'document.receipt', 'document.payable',
    'document.payment', 'directionValue.increase', 'directionValue.decrease',
    'message.selectPartnerAndRange', 'message.loadFailed', 'message.optionsLoadFailed'
  ]
} as const

const components = {
  financeAging: {
    path: 'src/views/finance/aging/index.vue',
    helpers: [
      'formatLocalizedCurrency',
      'formatLocalizedNumber',
      'formatLocalizedDate',
      'formatBusinessDate'
    ]
  },
  financeGrossMargin: {
    path: 'src/views/finance/gross-margin/index.vue',
    helpers: ['formatLocalizedCurrency', 'formatLocalizedNumber', 'getBusinessMonthDateRange']
  },
  financeStatement: {
    path: 'src/views/finance/statements/index.vue',
    helpers: ['formatLocalizedCurrency', 'formatLocalizedDate', 'getBusinessMonthDateRange']
  }
} as const

const readNestedValue = (source: unknown, path: string): unknown => (
  path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined
    return (current as Record<string, unknown>)[segment]
  }, source)
)

describe('finance analysis localization', () => {
  afterEach(() => setI18nLocale('zh-CN'))

  for (const locale of ['zh-CN', 'en-US'] satisfies SupportedLocale[]) {
    it(`defines every finance analysis key directly in ${locale}`, () => {
      const localeMessages = i18n.global.getLocaleMessage(locale)

      for (const [namespace, keys] of Object.entries(requiredKeys)) {
        for (const key of keys) {
          expect(typeof readNestedValue(localeMessages, `${namespace}.${key}`), `${namespace}.${key}`)
            .toBe('string')
        }
      }
    })
  }

  it('renders representative English analysis labels and feedback', () => {
    setI18nLocale('en-US')

    expect(i18n.global.t('financeAging.bucketLabel.d90Plus')).toBe('Over 90 days')
    expect(i18n.global.t('financeGrossMargin.marginRate')).toBe('Gross margin rate')
    expect(i18n.global.t('financeStatement.document.payment')).toBe('Payment')
    expect(i18n.global.t('financeStatement.message.optionsLoadFailed')).toBe('Failed to load partners')
  })

  for (const [namespace, component] of Object.entries(components)) {
    it(`keeps ${namespace} free of hard-coded UI copy and uses locale helpers`, () => {
      const source = readFileSync(resolve(process.cwd(), component.path), 'utf8')

      expect(source).not.toMatch(/[\u3400-\u9fff]/)
      expect(source).not.toMatch(/¥|\.toFixed\(/)
      expect(source).not.toMatch(/\s(?:label|placeholder|title)="[^"]+"/)
      expect(source).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
      for (const helper of component.helpers) {
        expect(source).toContain(helper)
      }
    })
  }
})
