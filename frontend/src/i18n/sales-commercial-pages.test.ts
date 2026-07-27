import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { salesCommercialPageMessages } from './sales-commercial-pages'

const pages = {
  salesPrice: 'src/views/sales/prices/index.vue',
  purchasePrice: 'src/views/purchase/prices/index.vue',
  salesQuote: 'src/views/sales/quotes/index.vue'
} as const

const leafPaths = (value: unknown, prefix = ''): string[] => {
  if (typeof value === 'string') return [prefix]
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).flatMap(([key, nested]) => (
    leafPaths(nested, prefix ? `${prefix}.${key}` : key)
  ))
}

describe('sales price and quote localization', () => {
  it('keeps Chinese and English message structures aligned and non-empty', () => {
    const chinesePaths = leafPaths(salesCommercialPageMessages['zh-CN']).sort()
    const englishPaths = leafPaths(salesCommercialPageMessages['en-US']).sort()

    expect(englishPaths).toEqual(chinesePaths)
    expect(chinesePaths.length).toBeGreaterThan(80)
    for (const locale of ['zh-CN', 'en-US'] as const) {
      for (const path of leafPaths(salesCommercialPageMessages[locale])) {
        const value = path.split('.').reduce<unknown>((current, segment) => (
          current && typeof current === 'object'
            ? (current as Record<string, unknown>)[segment]
            : undefined
        ), salesCommercialPageMessages[locale])
        expect(value, `${locale}:${path}`).toBeTypeOf('string')
        expect((value as string).trim(), `${locale}:${path}`).not.toBe('')
      }
    }
  })

  it('provides representative English commercial copy', () => {
    expect(salesCommercialPageMessages['en-US'].salesPrice.customerSpecific).toBe('Customer-specific')
    expect(salesCommercialPageMessages['en-US'].purchasePrice.supplierSpecific).toBe('Supplier-specific')
    expect(salesCommercialPageMessages['zh-CN'].purchasePrice.maxPrice).toBe('最高价')
    expect(salesCommercialPageMessages['en-US'].salesPrice.validation.minAboveList)
      .toBe('Minimum price cannot exceed list price')
    expect(salesCommercialPageMessages['en-US'].salesQuote.statusValue.converted).toBe('Converted')
    expect(salesCommercialPageMessages['en-US'].salesQuote.message.selectWarehouse).toBe('Select a warehouse')
  })

  for (const [namespace, pagePath] of Object.entries(pages)) {
    it(`keeps ${namespace} free of hard-coded Chinese UI copy`, () => {
      const source = readFileSync(resolve(process.cwd(), pagePath), 'utf8')

      expect(source).not.toMatch(/[\u3400-\u9fff]/)
      expect(source).not.toMatch(/\s(?:label|placeholder|title|description)="[^"]+"/)
      expect(source).not.toMatch(/ElMessage\.(?:error|success|warning|info)\(\s*['"`]/)
      expect(source).toContain('useI18n')
    })
  }

  // Commercial pages delegate money/date rendering to presentation composables.
  for (const [namespace, config] of Object.entries({
    salesPrice: {
      page: pages.salesPrice,
      composable: 'src/composables/useSalesPricePresentation.ts',
      pageMarkers: ['formatMoney(row.listPrice)', 'formatEffectivePeriod(row.effectiveFrom, row.effectiveTo)']
    },
    purchasePrice: {
      page: pages.purchasePrice,
      composable: 'src/composables/usePurchasePricePresentation.ts',
      pageMarkers: ['formatMoney(row.listPrice)', 'formatEffectivePeriod(row.effectiveFrom, row.effectiveTo)']
    },
    salesQuote: {
      page: pages.salesQuote,
      composable: 'src/composables/useSalesQuotePresentation.ts',
      pageMarkers: ['formatMoney(row.totalAmount)', 'formatDate(row.quoteDate)']
    }
  })) {
    it(`formats ${namespace} money and dates through locale helpers`, () => {
      const pageSource = readFileSync(resolve(process.cwd(), config.page), 'utf8')
      const composableSource = readFileSync(resolve(process.cwd(), config.composable), 'utf8')

      for (const marker of config.pageMarkers) {
        expect(pageSource).toContain(marker)
      }
      expect(composableSource).toContain('formatLocalizedCurrency(')
      expect(composableSource).toContain('formatLocalizedDate(')
    })
  }
})
