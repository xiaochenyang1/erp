import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { printHtml } = vi.hoisted(() => ({ printHtml: vi.fn() }))
vi.mock('@/utils/printDoc', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/utils/printDoc')>(),
  printHtml
}))

import type { PartnerStatement } from '@/api/finance'
import { useFinanceStatementPresentation } from '@/composables/useFinanceStatementPresentation'
import { i18n, setI18nLocale } from '@/i18n'
import { printFinanceInvoice, printPartnerStatement } from './bizPrint'

const statement: PartnerStatement = {
  partnerType: 'CUSTOMER',
  partnerId: '2072561615605100546',
  partnerName: 'Acme & Co',
  dateFrom: '2026-09-01',
  dateTo: '2026-09-30',
  baseCurrencyCode: 'EUR',
  openingBalance: 0,
  totalIncrease: 720.01,
  totalDecrease: 720.01,
  closingBalance: 0,
  lines: [
    { bizDate: '2026-09-01', docType: 'RECEIVABLE', docNo: 'AR-001', direction: 'INCREASE', amount: 700.01, balance: 700.01 },
    { bizDate: '2026-09-02', docType: 'RECEIPT', docNo: 'RC-001', direction: 'DECREASE', amount: 720.01, balance: -20 },
    { bizDate: '2026-09-02', docType: 'FX_ADJUSTMENT', docNo: 'RC-001', direction: 'INCREASE', amount: 19.99, balance: -0.01 },
    { bizDate: '2026-09-02', docType: 'ROUNDING_ADJUSTMENT', docNo: 'RC-001', direction: 'INCREASE', amount: 0.01, balance: 0 }
  ]
}

const printedDocument = () => {
  expect(printHtml).toHaveBeenCalledOnce()
  const document = new DOMParser().parseFromString(printHtml.mock.calls[0][1], 'text/html')
  return document
}

describe('partner statement printing', () => {
  beforeEach(() => {
    printHtml.mockClear()
    localStorage.setItem('locale', 'en-US')
    setI18nLocale('en-US')
  })
  afterEach(() => {
    localStorage.removeItem('locale')
    setI18nLocale('zh-CN')
  })

  it('prints actual cash and translated adjustments in the statement base currency', () => {
    const { toPrintDto } = useFinanceStatementPresentation((key, params) => i18n.global.t(key, params || {}))

    printPartnerStatement(toPrintDto(statement))

    const document = printedDocument()
    const rows = Array.from(document.querySelectorAll('table:not(.info) tbody tr'))
      .map((row) => Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent))
    expect(rows.slice(0, 4)).toEqual([
      ['2026-09-01', 'Receivable', 'AR-001', 'Increase', '€700.01', '€700.01', ''],
      ['2026-09-02', 'Receipt', 'RC-001', 'Decrease', '€720.01', '-€20.00', ''],
      ['2026-09-02', 'FX adjustment', 'RC-001', 'Increase', '€19.99', '-€0.01', ''],
      ['2026-09-02', 'Rounding adjustment', 'RC-001', 'Increase', '€0.01', '€0.00', '']
    ])
    expect(document.querySelector('.info')?.textContent).toContain('本位币EUR')
    expect(document.querySelector('.info')?.textContent).toContain('FX and rounding adjustments do not represent additional cash movements.')
    expect(document.body.textContent).toContain('Acme & Co')
    expect(document.body.textContent).not.toContain('¥')
    expect(statement.lines[2]).not.toHaveProperty('docTypeLabel')
  })

  it('prints legacy statements with the CNY fallback', () => {
    printPartnerStatement({ ...statement, baseCurrencyCode: undefined })

    const document = printedDocument()
    expect(document.querySelector('.info')?.textContent).toContain('本位币CNY')
    expect(document.body.textContent).toContain('CN¥720.01')
  })

  it('prints invoice amounts together with the saved currency and rate', () => {
    printFinanceInvoice({ invoiceNo: 'FX-1', partnerName: 'Customer', amount: 100, taxAmount: 10, currencyCode: 'USD', exchangeRate: 7.2 })
    const document = printedDocument()
    expect(document.querySelector('.info')?.textContent).toContain('单据币种USD')
    expect(document.querySelector('.info')?.textContent).toContain('汇率7.2')
    expect(document.body.textContent).toContain('100.00')
  })
})
