import type { PartnerStatement, PartnerStatementLine } from '@/api/finance'
import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string

export interface FinanceStatementPartnerOption {
  name?: string | null
  customerName?: string | null
  supplierName?: string | null
}

export interface FinanceStatementPrintLine extends PartnerStatementLine {
  docTypeLabel: string
  directionLabel: string
}

export type FinanceStatementPrintDto = Omit<PartnerStatement, 'lines'> & {
  partnerTypeLabel: string
  lines: FinanceStatementPrintLine[]
}

const PARTNER_TYPE_KEYS: Record<string, string> = {
  CUSTOMER: 'financeStatement.customer',
  SUPPLIER: 'financeStatement.supplier'
}

const DOCUMENT_TYPE_KEYS: Record<string, string> = {
  RECEIVABLE: 'financeStatement.document.receivable',
  RECEIPT: 'financeStatement.document.receipt',
  PAYABLE: 'financeStatement.document.payable',
  PAYMENT: 'financeStatement.document.payment'
}

const DIRECTION_KEYS: Record<string, string> = {
  INCREASE: 'financeStatement.directionValue.increase',
  DECREASE: 'financeStatement.directionValue.decrease'
}

/** Pure display and print-payload mapping for partner statements. */
export const useFinanceStatementPresentation = (t: Translate) => {
  const translatedLabel = (
    value: string | null | undefined,
    keys: Record<string, string>
  ) => {
    const key = value ? keys[value] : undefined
    return key ? t(key) : value || ''
  }

  const money = (value?: number | string | null) =>
    formatLocalizedCurrency(Number(value ?? 0))

  const formatDate = (value?: string | null) => formatLocalizedDate(value) || '-'

  const partnerLabel = (partner?: FinanceStatementPartnerOption | null) =>
    partner?.name || partner?.customerName || partner?.supplierName || ''

  const partnerTypeLabel = (value?: string | null) =>
    translatedLabel(value, PARTNER_TYPE_KEYS)

  const documentTypeLabel = (value?: string | null) =>
    translatedLabel(value, DOCUMENT_TYPE_KEYS)

  const directionLabel = (value?: string | null) =>
    translatedLabel(value, DIRECTION_KEYS)

  const toPrintDto = (statement: PartnerStatement): FinanceStatementPrintDto => ({
    ...statement,
    partnerTypeLabel: partnerTypeLabel(statement.partnerType),
    lines: (statement.lines || []).map((line) => ({
      ...line,
      docTypeLabel: documentTypeLabel(line.docType),
      directionLabel: directionLabel(line.direction)
    }))
  })

  return {
    directionLabel,
    documentTypeLabel,
    formatDate,
    money,
    partnerLabel,
    partnerTypeLabel,
    toPrintDto
  }
}
