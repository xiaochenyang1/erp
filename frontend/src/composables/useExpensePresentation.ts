import { computed, type Ref } from 'vue'

import type { AccountSubject, ExpenseReconciliation } from '@/api/finance'
import { formatLocalizedNumber } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const STATUS_KEYS: Record<string, string> = {
  DRAFT: 'financeReportPages.expenses.status.draft',
  PENDING: 'financeReportPages.expenses.status.pending',
  APPROVED: 'financeReportPages.expenses.status.approved',
  REJECTED: 'financeReportPages.expenses.status.rejected',
  POSTED: 'financeReportPages.expenses.status.posted',
  CANCELLED: 'financeReportPages.expenses.status.cancelled'
}

const STATUS_TAG_TYPES: Record<string, TagType> = {
  DRAFT: 'info',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  POSTED: 'primary',
  CANCELLED: 'info'
}

/** Depth-first flatten so nested chart-of-accounts nodes are all selectable. */
export const flattenSubjects = (subjects: AccountSubject[]): AccountSubject[] =>
  subjects.flatMap((subject) => [subject, ...flattenSubjects(subject.children || [])])

export const useExpensePresentation = (
  t: Translate,
  resources: {
    subjects: Ref<AccountSubject[]>
    reconciliation: Ref<ExpenseReconciliation | undefined>
  }
) => {
  const subjectOptions = resources.subjects
  const subjectMap = computed(
    () => new Map(subjectOptions.value.map((subject) => [String(subject.id), subject]))
  )

  /** Expense lines post against EXPENSE accounts; the credit side is cash/payable. */
  const expenseSubjects = computed(() =>
    subjectOptions.value.filter(
      (subject) => subject.status === 'ACTIVE' && subject.category === 'EXPENSE'
    )
  )

  const paymentSubjects = computed(() =>
    subjectOptions.value.filter(
      (subject) =>
        subject.status === 'ACTIVE'
        && ['ASSET', 'LIABILITY'].includes(String(subject.category))
    )
  )

  const subjectLabel = (subject: AccountSubject) =>
    `${subject.code || subject.subjectCode} - ${subject.name || subject.subjectName}`

  const subjectName = (id?: string | number | null) => {
    if (id == null) return '-'
    const subject = subjectMap.value.get(String(id))
    return subject
      ? subjectLabel(subject)
      : t('financeReportPages.expenses.subjectFallback', { id })
  }

  const findSubject = (id?: string | number | null) =>
    id == null ? undefined : subjectMap.value.get(String(id))

  /** Printed documents show the plain account name, not the "code - name" label. */
  const rawSubjectName = (id?: string | number | null) => {
    const subject = findSubject(id)
    return subject ? subject.subjectName || subject.name || '' : ''
  }

  const formatAmount = (amount?: number) =>
    formatLocalizedNumber(Number(amount || 0), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })

  const getStatusLabel = (status?: string) => {
    if (!status) return ''
    const key = STATUS_KEYS[status]
    return key ? t(key) : status
  }

  const getStatusType = (status?: string): TagType =>
    (status && STATUS_TAG_TYPES[status]) || 'info'

  const checkLabel = (passed: boolean) =>
    passed ? t('financeReportPages.common.normal') : t('financeReportPages.common.abnormal')

  const checkTagType = (passed: boolean): 'success' | 'danger' => (passed ? 'success' : 'danger')

  /**
   * A reconciliation only passes when the voucher exists, balances, matches the
   * expense amount and links back to it. A reversed expense must additionally
   * have a balanced, amount-matched reversal voucher.
   */
  const isReconciliationPassed = (data?: ExpenseReconciliation) => {
    if (!data) return false
    const reversalPassed =
      !data.reversed || Boolean(data.reversalVoucherBalanced && data.reversalAmountMatched)
    return (
      !data.voucherMissing
      && !data.entriesMissing
      && Boolean(data.voucherBalanced)
      && Boolean(data.amountMatched)
      && Boolean(data.voucherLinkedToExpense)
      && reversalPassed
    )
  }

  const reconciliationPassed = computed(() =>
    isReconciliationPassed(resources.reconciliation.value)
  )

  return {
    checkLabel,
    checkTagType,
    expenseSubjects,
    findSubject,
    formatAmount,
    getStatusLabel,
    getStatusType,
    isReconciliationPassed,
    paymentSubjects,
    rawSubjectName,
    reconciliationPassed,
    subjectLabel,
    subjectName
  }
}
