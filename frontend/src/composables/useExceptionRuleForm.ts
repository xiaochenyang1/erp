import { reactive, ref } from 'vue'

import type {
  ExceptionRule,
  ExceptionRuleUpdateRequest
} from '@/api/exceptionRule'
import { normalizeOptionalId } from './useExceptionRuleList'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Edit dialog for exception rule thresholds / priority / assignee / interval.
 * Element form validation stays on the page around handleSaveEdit.
 */
export const useExceptionRuleForm = (
  t: Translate,
  options: {
    updateRule: (id: string | number, data: ExceptionRuleUpdateRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const editDialogVisible = ref(false)
  const editSubmitting = ref(false)
  const editTarget = ref<ExceptionRule>()
  const editForm = reactive<ExceptionRuleUpdateRequest>({
    thresholdValue: undefined,
    thresholdUnit: '',
    priority: '',
    assigneeUserId: undefined,
    scheduleIntervalMinutes: 60,
    remark: ''
  })

  const openEditDialog = (row: ExceptionRule) => {
    editTarget.value = row
    editForm.thresholdValue = Number(row.thresholdValue || 0)
    editForm.thresholdUnit = row.thresholdUnit
    editForm.priority = row.priority
    editForm.assigneeUserId = row.assigneeUserId
    editForm.scheduleIntervalMinutes = row.scheduleIntervalMinutes || 60
    editForm.remark = row.remark || ''
    editDialogVisible.value = true
  }

  const handleSaveEdit = async () => {
    if (!editTarget.value) return false
    editSubmitting.value = true
    try {
      await options.updateRule(editTarget.value.id, {
        thresholdValue: editForm.thresholdValue,
        thresholdUnit: editForm.thresholdUnit,
        priority: editForm.priority,
        assigneeUserId: normalizeOptionalId(editForm.assigneeUserId),
        scheduleIntervalMinutes: editForm.scheduleIntervalMinutes,
        remark: editForm.remark?.trim() || undefined
      })
      options.onSuccess?.(t('exceptionRule.message.saved'))
      editDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('exceptionRule.message.saveFailed'))
      return false
    } finally {
      editSubmitting.value = false
    }
  }

  return {
    editDialogVisible,
    editForm,
    editSubmitting,
    editTarget,
    handleSaveEdit,
    openEditDialog
  }
}
