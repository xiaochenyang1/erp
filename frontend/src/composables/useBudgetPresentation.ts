import { computed, type Ref } from 'vue'

import type { AccountSubject } from '@/api/finance'
import type { Dept } from '@/api/system'
import { formatBusinessDate, formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const STATUS_TAG_TYPES: Record<string, TagType> = {
  APPROVED: 'success',
  CANCELLED: 'danger',
  CLOSED: 'primary',
  SUBMITTED: 'warning'
}

/** Period 0 carries the annual allocation instead of a calendar month. */
export const ANNUAL_PERIOD = 0

/**
 * Year and month defaults follow the business timezone, so a New Year or
 * month-end rollover can never default the form to the previous period.
 */
export const businessYear = () => Number(formatBusinessDate().slice(0, 4))
export const businessMonth = () => Number(formatBusinessDate().slice(5, 7))

/**
 * Labels and amount formatting for the budget register, its detail dialog and
 * the execution enquiry. Both trees arrive already flattened by the list.
 */
export const useBudgetPresentation = (
  t: Translate,
  resources: {
    subjects: Ref<AccountSubject[]>
    departments: Ref<Dept[]>
  }
) => {
  const subjectLabel = (subject: AccountSubject) =>
    `${subject.code || subject.subjectCode || ''} - ${subject.name || subject.subjectName || ''}`

  const deptLabel = (dept: Dept) =>
    `${dept.code || dept.deptCode || ''} ${dept.name || dept.deptName || ''}`.trim()

  const subjectMap = computed(
    () => new Map(resources.subjects.value.map((subject) => [String(subject.id), subject]))
  )

  const deptMap = computed(
    () => new Map(resources.departments.value.map((dept) => [String(dept.id), dept]))
  )

  /** An unknown account still shows its id, which is what the register used to print. */
  const subjectName = (id?: string | number) => {
    const subject = subjectMap.value.get(String(id))
    return subject ? subjectLabel(subject) : String(id || '-')
  }

  const deptName = (id?: string | number) => {
    const dept = deptMap.value.get(String(id))
    return dept ? deptLabel(dept) : '-'
  }

  const formatAmount = (value?: number) => formatLocalizedNumber(
    Number(value || 0),
    { minimumFractionDigits: 2, maximumFractionDigits: 2 }
  )

  const statusText = (status?: string) =>
    (status ? t(`financeReportPages.budgets.status.${status.toLowerCase()}`) : '-')

  const statusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  const policyText = (policy?: string) =>
    (policy ? t(`financeReportPages.budgets.policyValue.${policy.toLowerCase()}`) : '-')

  /** Which allocation the backend charged the request against: monthly, annual or none. */
  const periodSourceText = (periodSource?: string) =>
    (periodSource ? t(`financeReportPages.budgets.periodSourceValue.${periodSource.toLowerCase()}`) : '-')

  const monthLabel = (month?: number) => {
    if (month == null) return '-'
    return month === ANNUAL_PERIOD
      ? t('financeReportPages.budgets.annual')
      : `${month}${t('financeReportPages.budgets.monthSuffix')}`
  }

  return {
    deptLabel,
    deptName,
    formatAmount,
    monthLabel,
    periodSourceText,
    policyText,
    statusText,
    statusType,
    subjectLabel,
    subjectName
  }
}
