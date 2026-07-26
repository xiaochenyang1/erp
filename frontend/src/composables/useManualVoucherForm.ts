import { computed, reactive, ref } from 'vue'

import type { ManualVoucher, ManualVoucherSaveRequest } from '@/api/finance'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface ManualVoucherEditLine {
  subjectId: string
  debitAmount: number
  creditAmount: number
  summary: string
}

/** Double entry needs at least one debit and one credit line. */
const MIN_LINES = 2

export const emptyManualVoucherLine = (): ManualVoucherEditLine => ({
  subjectId: '',
  debitAmount: 0,
  creditAmount: 0,
  summary: ''
})

export const useManualVoucherForm = (
  t: Translate,
  options: {
    getVoucher: (id: string | number) => Promise<ManualVoucher>
    createVoucher: (data: ManualVoucherSaveRequest) => Promise<unknown>
    updateVoucher: (id: string | number, data: ManualVoucherSaveRequest) => Promise<unknown>
    /** Accounts are lazy-loaded, so the dialog makes sure they exist first. */
    ensureSubjects: () => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const editVisible = ref(false)
  const editMode = ref<'create' | 'edit'>('create')
  const editingId = ref<string>('')
  const saving = ref(false)

  const editForm = reactive<{
    bizDate: string
    remark: string
    lines: ManualVoucherEditLine[]
  }>({
    bizDate: '',
    remark: '',
    lines: []
  })

  const addLine = () => editForm.lines.push(emptyManualVoucherLine())

  /** The two structural lines can never be removed. */
  const removeLine = (index: number) => {
    if (editForm.lines.length > MIN_LINES) editForm.lines.splice(index, 1)
  }

  /** Debit and credit are mutually exclusive on a single line. */
  const onDebitChange = (row: ManualVoucherEditLine) => {
    if (row.debitAmount) row.creditAmount = 0
  }

  const onCreditChange = (row: ManualVoucherEditLine) => {
    if (row.creditAmount) row.debitAmount = 0
  }

  const debitTotal = computed(() =>
    editForm.lines.reduce((sum, line) => sum + Number(line.debitAmount || 0), 0)
  )
  const creditTotal = computed(() =>
    editForm.lines.reduce((sum, line) => sum + Number(line.creditAmount || 0), 0)
  )

  /** Compared in cents so float drift cannot mark a balanced voucher unbalanced. */
  const balanced = computed(() => {
    const debit = Math.round(debitTotal.value * 100)
    const credit = Math.round(creditTotal.value * 100)
    return debit === credit && debit > 0
  })

  const canSave = computed(() => {
    if (!editForm.bizDate) return false
    if (editForm.lines.length < MIN_LINES) return false
    for (const line of editForm.lines) {
      if (!line.subjectId) return false
      const hasDebit = Number(line.debitAmount || 0) > 0
      const hasCredit = Number(line.creditAmount || 0) > 0
      // Exactly one side must carry an amount.
      if (hasDebit === hasCredit) return false
    }
    return balanced.value
  })

  const resetEditForm = () => {
    editForm.bizDate = ''
    editForm.remark = ''
    editForm.lines = [emptyManualVoucherLine(), emptyManualVoucherLine()]
    editingId.value = ''
  }

  const openCreate = async () => {
    editMode.value = 'create'
    resetEditForm()
    await options.ensureSubjects()
    editVisible.value = true
  }

  /** The list row may omit entry lines, so editing always loads the full detail. */
  const openEdit = async (row: ManualVoucher) => {
    editMode.value = 'edit'
    editingId.value = row.id
    await options.ensureSubjects()
    try {
      const detail = await options.getVoucher(row.id)
      editForm.bizDate = detail.bizDate
      editForm.remark = detail.remark || ''
      editForm.lines = (detail.lines || []).map((line) => ({
        subjectId: line.subjectId,
        debitAmount: line.debitAmount,
        creditAmount: line.creditAmount,
        summary: line.summary || ''
      }))
      while (editForm.lines.length < MIN_LINES) {
        editForm.lines.push(emptyManualVoucherLine())
      }
      editVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.manualVouchers.message.detailLoadFailed'))
      return false
    }
  }

  const handleSave = async () => {
    if (!canSave.value) {
      options.onWarning?.(t('financeReportPages.manualVouchers.message.invalidEntries'))
      return false
    }
    saving.value = true
    try {
      const payload: ManualVoucherSaveRequest = {
        bizDate: editForm.bizDate,
        remark: editForm.remark,
        lines: editForm.lines.map((line) => ({
          subjectId: line.subjectId,
          debitAmount: Number(line.debitAmount || 0),
          creditAmount: Number(line.creditAmount || 0),
          summary: line.summary
        }))
      }
      if (editMode.value === 'create') {
        await options.createVoucher(payload)
        options.onSuccess?.(t('financeReportPages.manualVouchers.message.created'))
      } else {
        await options.updateVoucher(editingId.value, payload)
        options.onSuccess?.(t('financeReportPages.manualVouchers.message.updated'))
      }
      editVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.manualVouchers.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    addLine,
    balanced,
    canSave,
    creditTotal,
    debitTotal,
    editForm,
    editMode,
    editVisible,
    editingId,
    handleSave,
    onCreditChange,
    onDebitChange,
    openCreate,
    openEdit,
    removeLine,
    resetEditForm,
    saving
  }
}
