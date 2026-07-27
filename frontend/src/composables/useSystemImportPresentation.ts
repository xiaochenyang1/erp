import { computed } from 'vue'

import type { ImportJob, ImportJobStatus, ImportType } from '@/api/imports'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

/** Labels and helpers for initial-data import jobs. */
export const useSystemImportPresentation = (t: Translate) => {
  const importTypeOptions = computed<Array<{ label: string; value: ImportType }>>(() => [
    { label: t('systemImports.types.product'), value: 'PRODUCT' },
    { label: t('systemImports.types.customer'), value: 'CUSTOMER' },
    { label: t('systemImports.types.supplier'), value: 'SUPPLIER' },
    { label: t('systemImports.types.warehouse'), value: 'WAREHOUSE' },
    { label: t('systemImports.types.location'), value: 'LOCATION' },
    { label: t('systemImports.types.openingInventory'), value: 'OPENING_INVENTORY' },
    { label: t('systemImports.types.openingReceivable'), value: 'OPENING_RECEIVABLE' },
    { label: t('systemImports.types.openingPayable'), value: 'OPENING_PAYABLE' },
    { label: t('systemImports.types.openingAccountBalance'), value: 'OPENING_ACCOUNT_BALANCE' }
  ])

  const statusOptions = computed<Array<{ label: string; value: ImportJobStatus }>>(() => [
    { label: t('systemImports.statuses.validated'), value: 'VALIDATED' },
    { label: t('systemImports.statuses.invalid'), value: 'INVALID' },
    { label: t('systemImports.statuses.committing'), value: 'COMMITTING' },
    { label: t('systemImports.statuses.committed'), value: 'COMMITTED' },
    { label: t('systemImports.statuses.failed'), value: 'FAILED' }
  ])

  const importTypeLabel = (value?: string) =>
    importTypeOptions.value.find((item) => item.value === value)?.label || value || ''

  const statusLabel = (value?: string) =>
    statusOptions.value.find((item) => item.value === value)?.label || value || ''

  const statusTagType = (value?: string): TagType => {
    if (value === 'VALIDATED' || value === 'COMMITTED') return 'success'
    if (value === 'INVALID' || value === 'FAILED') return 'danger'
    if (value === 'COMMITTING') return 'warning'
    return 'info'
  }

  const canCommit = (row: Pick<ImportJob, 'status' | 'validRows' | 'errorRows' | 'committedRows'>) => {
    if (row.status === 'VALIDATED') return true
    return row.status === 'FAILED'
      && row.validRows > 0
      && row.errorRows === 0
      && row.committedRows === 0
  }

  const formatJson = (value: unknown) => JSON.stringify(value || {}, null, 2)

  const countJobsWithErrors = (jobs: ImportJob[]) =>
    (jobs || []).filter((job) => Number(job.errorRows || 0) > 0).length

  return {
    canCommit,
    countJobsWithErrors,
    formatJson,
    importTypeLabel,
    importTypeOptions,
    statusLabel,
    statusOptions,
    statusTagType
  }
}
