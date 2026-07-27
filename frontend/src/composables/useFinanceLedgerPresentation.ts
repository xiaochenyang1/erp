import type { AccountSubject } from '@/api/finance'
import { formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string

/** Recursively find a chart-of-accounts node by code. */
export const findSubjectByCode = (
  subjects: AccountSubject[],
  code?: string
): AccountSubject | null => {
  if (!code) return null
  for (const subject of subjects) {
    if (subject.code === code || subject.subjectCode === code) return subject
    if (subject.children?.length) {
      const found = findSubjectByCode(subject.children, code)
      if (found) return found
    }
  }
  return null
}

/** Recursively find a chart-of-accounts node by id. */
export const findSubjectById = (
  subjects: AccountSubject[],
  id?: string | number
): AccountSubject | null => {
  if (id == null || id === '') return null
  for (const subject of subjects) {
    if (String(subject.id) === String(id)) return subject
    if (subject.children?.length) {
      const found = findSubjectById(subject.children, id)
      if (found) return found
    }
  }
  return null
}

/** Client-side page slice used by the detail ledger. */
export const paginateEntries = <T>(entries: T[], page = 1, size = 20) => {
  const start = (page - 1) * size
  return entries.slice(start, start + size)
}

/** Display helpers for general and detail ledgers. */
export const useFinanceLedgerPresentation = (t: Translate) => {
  const formatAmount = (amount?: number | string) =>
    formatLocalizedCurrency(Number(amount || 0))

  const formatDate = (value?: string) => formatLocalizedDate(value) || '-'

  const getGeneralSummary = (param: {
    columns: Array<{ property?: string }>
    data: Array<Record<string, unknown>>
  }) => {
    const { columns, data } = param
    const sums: string[] = []
    columns.forEach((column, index) => {
      if (index === 0) {
        sums[index] = t('financeReportPages.ledger.total')
        return
      }
      if (index === 1) {
        sums[index] = ''
        return
      }
      const property = column.property
      if (!property) {
        sums[index] = ''
        return
      }
      const values = data.map((item) => Number(item[property]))
      if (!values.every((value) => Number.isNaN(value))) {
        const total = values.reduce((prev, curr) => {
          const value = Number(curr)
          return Number.isNaN(value) ? prev : prev + value
        }, 0)
        sums[index] = formatAmount(total)
      } else {
        sums[index] = ''
      }
    })
    return sums
  }

  return {
    formatAmount,
    formatDate,
    getGeneralSummary
  }
}
