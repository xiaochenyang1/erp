import { beforeEach, describe, expect, it } from 'vitest'

import type { PartnerStatement } from '@/api/finance'
import { useFinanceStatementPresentation } from './useFinanceStatementPresentation'

const t = (key: string) => `translated:${key}`

const LONG_PARTNER_ID = '2072561615605100546'

const createStatement = (): PartnerStatement => ({
  partnerType: 'CUSTOMER',
  partnerId: LONG_PARTNER_ID,
  partnerName: 'Acme Corp',
  dateFrom: '2026-07-01',
  dateTo: '2026-07-31',
  openingBalance: 100.25,
  totalIncrease: 80.5,
  totalDecrease: 30.75,
  closingBalance: 150,
  lines: [
    {
      bizDate: '2026-07-05',
      docType: 'RECEIVABLE',
      docNo: 'AR-001',
      direction: 'INCREASE',
      amount: 80.5,
      balance: 180.75,
      remark: 'Invoice posted'
    },
    {
      bizDate: '2026-07-20',
      docType: 'FUTURE_DOCUMENT',
      docNo: 'X-002',
      direction: 'FUTURE_DIRECTION',
      amount: 30.75,
      balance: 150
    }
  ]
})

describe('finance statement presentation', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('locale', 'en-US')
    localStorage.setItem('timeZone', 'UTC')
  })

  it('formats amounts and dates with the page fallbacks', () => {
    const { formatDate, money } = useFinanceStatementPresentation(t)

    expect(money(1234.5)).toContain('1,234.50')
    expect(money('12.30')).toContain('12.30')
    expect(money(undefined)).toContain('0.00')

    expect(formatDate('2026-07-29')).toBe('07/29/2026')
    expect(formatDate('not-a-date')).toBe('not-a-date')
    expect(formatDate('')).toBe('-')
    expect(formatDate(undefined)).toBe('-')
    expect(formatDate(null)).toBe('-')
  })

  it('resolves partner display fields with the template precedence', () => {
    const { partnerLabel } = useFinanceStatementPresentation(t)

    expect(partnerLabel({
      name: 'Normalized name',
      customerName: 'Customer name',
      supplierName: 'Supplier name'
    })).toBe('Normalized name')
    expect(partnerLabel({ customerName: 'Customer name' })).toBe('Customer name')
    expect(partnerLabel({ supplierName: 'Supplier name' })).toBe('Supplier name')
    expect(partnerLabel({})).toBe('')
    expect(partnerLabel(undefined)).toBe('')
    expect(partnerLabel(null)).toBe('')
  })

  it('maps partner, document and direction values without language assumptions', () => {
    const presentation = useFinanceStatementPresentation(t)

    expect(presentation.partnerTypeLabel('CUSTOMER'))
      .toBe('translated:financeStatement.customer')
    expect(presentation.partnerTypeLabel('SUPPLIER'))
      .toBe('translated:financeStatement.supplier')

    expect(presentation.documentTypeLabel('RECEIVABLE'))
      .toBe('translated:financeStatement.document.receivable')
    expect(presentation.documentTypeLabel('RECEIPT'))
      .toBe('translated:financeStatement.document.receipt')
    expect(presentation.documentTypeLabel('PAYABLE'))
      .toBe('translated:financeStatement.document.payable')
    expect(presentation.documentTypeLabel('PAYMENT'))
      .toBe('translated:financeStatement.document.payment')

    expect(presentation.directionLabel('INCREASE'))
      .toBe('translated:financeStatement.directionValue.increase')
    expect(presentation.directionLabel('DECREASE'))
      .toBe('translated:financeStatement.directionValue.decrease')
  })

  it('preserves unknown and empty label values', () => {
    const presentation = useFinanceStatementPresentation(t)

    expect(presentation.partnerTypeLabel('PARTNER_V2')).toBe('PARTNER_V2')
    expect(presentation.documentTypeLabel('CREDIT_NOTE')).toBe('CREDIT_NOTE')
    expect(presentation.directionLabel('REVERSAL')).toBe('REVERSAL')

    for (const value of ['', undefined, null]) {
      expect(presentation.partnerTypeLabel(value)).toBe('')
      expect(presentation.documentTypeLabel(value)).toBe('')
      expect(presentation.directionLabel(value)).toBe('')
    }
  })

  it('builds a complete print DTO without converting Long identifiers', () => {
    const statement = createStatement()
    const { toPrintDto } = useFinanceStatementPresentation(t)

    const payload = toPrintDto(statement)

    expect(payload).toEqual({
      partnerType: 'CUSTOMER',
      partnerId: LONG_PARTNER_ID,
      partnerName: 'Acme Corp',
      dateFrom: '2026-07-01',
      dateTo: '2026-07-31',
      openingBalance: 100.25,
      totalIncrease: 80.5,
      totalDecrease: 30.75,
      closingBalance: 150,
      partnerTypeLabel: 'translated:financeStatement.customer',
      lines: [
        {
          bizDate: '2026-07-05',
          docType: 'RECEIVABLE',
          docNo: 'AR-001',
          direction: 'INCREASE',
          amount: 80.5,
          balance: 180.75,
          remark: 'Invoice posted',
          docTypeLabel: 'translated:financeStatement.document.receivable',
          directionLabel: 'translated:financeStatement.directionValue.increase'
        },
        {
          bizDate: '2026-07-20',
          docType: 'FUTURE_DOCUMENT',
          docNo: 'X-002',
          direction: 'FUTURE_DIRECTION',
          amount: 30.75,
          balance: 150,
          docTypeLabel: 'FUTURE_DOCUMENT',
          directionLabel: 'FUTURE_DIRECTION'
        }
      ]
    })
    expect(payload.partnerId).toBe(LONG_PARTNER_ID)
    expect(typeof payload.partnerId).toBe('string')
    expect(payload).not.toBe(statement)
    expect(payload.lines[0]).not.toBe(statement.lines[0])
    expect(statement.lines[0]).not.toHaveProperty('docTypeLabel')
  })

  it('falls back to an empty line list for an incomplete response', () => {
    const statement = createStatement()
    statement.lines = undefined as unknown as PartnerStatement['lines']

    expect(useFinanceStatementPresentation(t).toPrintDto(statement).lines).toEqual([])
  })
})
