import { computed, reactive, ref } from 'vue'

import type { AccountSubject, AccountSubjectSaveRequest } from '@/api/finance'
import { defaultBalanceDirection } from './useAccountSubjectPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Account subject create/edit form (root, child, and edit modes).
 * Element Plus field rules stay on the page around handleSubmit.
 */
export const useAccountSubjectForm = (
  t: Translate,
  options: {
    getAccountSubject: (id: string | number) => Promise<AccountSubject>
    createAccountSubject: (data: AccountSubjectSaveRequest) => Promise<unknown>
    updateAccountSubject: (
      id: string | number,
      data: AccountSubjectSaveRequest
    ) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const dialogMode = ref<'create' | 'createChild' | 'edit'>('create')
  const submitLoading = ref(false)

  const formData = reactive({
    id: undefined as string | undefined,
    parentId: undefined as string | undefined,
    subjectCode: '',
    subjectName: '',
    subjectType: '',
    balanceDirection: 'DEBIT',
    remark: ''
  })

  const dialogTitle = computed(() =>
    ({
      create: t('financeReportPages.subjects.dialog.create'),
      createChild: t('financeReportPages.subjects.dialog.createChild'),
      edit: t('financeReportPages.subjects.dialog.edit')
    })[dialogMode.value]
  )

  const resetForm = () => {
    Object.assign(formData, {
      id: undefined,
      parentId: undefined,
      subjectCode: '',
      subjectName: '',
      subjectType: '',
      balanceDirection: 'DEBIT',
      remark: ''
    })
  }

  const handleAdd = () => {
    resetForm()
    dialogMode.value = 'create'
    dialogVisible.value = true
  }

  const handleAddChild = (row: Pick<AccountSubject, 'id'>) => {
    resetForm()
    dialogMode.value = 'createChild'
    formData.parentId = String(row.id)
    dialogVisible.value = true
  }

  const handleEdit = async (row: Pick<AccountSubject, 'id'>) => {
    dialogMode.value = 'edit'
    try {
      const subject = await options.getAccountSubject(row.id)
      Object.assign(formData, {
        id: String(subject.id),
        parentId: subject.parentId != null ? String(subject.parentId) : undefined,
        subjectCode: subject.subjectCode || subject.code || '',
        subjectName: subject.subjectName || subject.name || '',
        subjectType: subject.subjectType || subject.category || '',
        balanceDirection:
          subject.balanceDirection
          || defaultBalanceDirection(subject.subjectType || subject.category),
        remark: subject.remark || ''
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('financeReportPages.subjects.message.detailLoadFailed'))
      return false
    }
  }

  const buildPayload = (): AccountSubjectSaveRequest => ({
    parentId: formData.parentId,
    subjectCode: formData.subjectCode,
    subjectName: formData.subjectName,
    subjectType: formData.subjectType,
    balanceDirection: formData.balanceDirection,
    remark: formData.remark || undefined
  })

  const submitSave = async () => {
    submitLoading.value = true
    try {
      const payload = buildPayload()
      if (formData.id) {
        await options.updateAccountSubject(formData.id, payload)
        options.onSuccess?.(t('financeReportPages.subjects.message.updated'))
      } else {
        await options.createAccountSubject(payload)
        options.onSuccess?.(t('financeReportPages.subjects.message.created'))
      }
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('financeReportPages.subjects.message.actionFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const handleDialogClose = () => {
    resetForm()
  }

  return {
    buildPayload,
    dialogMode,
    dialogTitle,
    dialogVisible,
    formData,
    handleAdd,
    handleAddChild,
    handleDialogClose,
    handleEdit,
    resetForm,
    submitLoading,
    submitSave
  }
}
