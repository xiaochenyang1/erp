type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary' | ''

/** Default balance side for a newly created chart-of-accounts node. */
export const defaultBalanceDirection = (subjectType?: string) =>
  ['LIABILITY', 'EQUITY', 'REVENUE'].includes(subjectType || '') ? 'CREDIT' : 'DEBIT'

/** Display helpers for the account subject tree. */
export const useAccountSubjectPresentation = (t: Translate) => {
  const getCategoryLabel = (category?: string) => {
    const map: Record<string, string> = {
      ASSET: t('financeReportPages.subjects.categoryValue.asset'),
      LIABILITY: t('financeReportPages.subjects.categoryValue.liability'),
      EQUITY: t('financeReportPages.subjects.categoryValue.equity'),
      REVENUE: t('financeReportPages.subjects.categoryValue.revenue'),
      EXPENSE: t('financeReportPages.subjects.categoryValue.expense')
    }
    return category ? map[category] || category : '-'
  }

  const getCategoryType = (category?: string): TagType => {
    const map: Record<string, TagType> = {
      ASSET: 'success',
      LIABILITY: 'warning',
      EQUITY: 'info',
      REVENUE: 'success',
      EXPENSE: 'danger'
    }
    return category ? map[category] || '' : ''
  }

  const statusLabel = (status?: string) =>
    status === 'ACTIVE'
      ? t('financeReportPages.subjects.status.active')
      : t('financeReportPages.subjects.status.disabled')

  const statusType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'danger'

  const leafLabel = (isLeaf?: boolean) =>
    isLeaf ? t('financeReportPages.subjects.yes') : t('financeReportPages.subjects.no')

  const leafType = (isLeaf?: boolean): TagType => (isLeaf ? 'success' : 'info')

  const subjectDisplayName = (row?: {
    id?: string | number
    name?: string
    subjectName?: string
  }) => row?.name || row?.subjectName || String(row?.id ?? '')

  return {
    getCategoryLabel,
    getCategoryType,
    leafLabel,
    leafType,
    statusLabel,
    statusType,
    subjectDisplayName
  }
}
