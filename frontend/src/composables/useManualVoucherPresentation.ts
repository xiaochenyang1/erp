import { computed } from 'vue'

import type { AccountSubject, ManualVoucherStatus } from '@/api/finance'
import { formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const STATUS_KEYS: Array<{ value: ManualVoucherStatus; key: string }> = [
  { value: 'DRAFT', key: 'financeReportPages.manualVouchers.status.draft' },
  { value: 'PENDING', key: 'financeReportPages.manualVouchers.status.pending' },
  { value: 'APPROVED', key: 'financeReportPages.manualVouchers.status.approved' },
  { value: 'POSTED', key: 'financeReportPages.manualVouchers.status.posted' },
  { value: 'CANCELLED', key: 'financeReportPages.manualVouchers.status.cancelled' }
]

const STATUS_TAG_TYPES: Record<string, TagType> = {
  DRAFT: 'info',
  PENDING: 'warning',
  APPROVED: 'primary',
  POSTED: 'success',
  CANCELLED: 'danger'
}

export const useManualVoucherPresentation = (t: Translate) => {
  const statusOptions = computed<Array<{ label: string; value: ManualVoucherStatus }>>(() =>
    STATUS_KEYS.map((entry) => ({ label: t(entry.key), value: entry.value }))
  )

  const statusLabel = (status?: string) => {
    if (!status) return ''
    const entry = STATUS_KEYS.find((item) => item.value === status)
    return entry ? t(entry.key) : status
  }

  const statusTagType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  /** Non-numeric amounts render as zero rather than NaN. */
  const formatAmount = (amount?: number | string) => {
    const value = Number(amount ?? 0)
    return Number.isFinite(value)
      ? formatLocalizedNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : '0.00'
  }

  /** Entry pickers show "code name" so accounts stay distinguishable by number. */
  const subjectLabel = (subject: AccountSubject) => {
    const code = subject.subjectCode || subject.code || ''
    const name = subject.subjectName || subject.name || ''
    return `${code} ${name}`.trim() || String(subject.id)
  }

  return {
    formatAmount,
    statusLabel,
    statusOptions,
    statusTagType,
    subjectLabel
  }
}
