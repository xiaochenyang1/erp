import { computed, reactive, ref } from 'vue'

import type {
  ExceptionSlaPolicy,
  ExceptionSlaPolicyUpdateRequest
} from '@/api/exceptionSlaPolicy'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Edit dialog for exception SLA policies.
 * Element form validation stays on the page around handleSaveEdit.
 */
export const useExceptionSlaPolicyForm = (
  t: Translate,
  options: {
    updatePolicy: (id: string, data: ExceptionSlaPolicyUpdateRequest) => Promise<unknown>
    categoryLabel: (value?: string) => string
    priorityLabel: (value?: string) => string
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const editDialogVisible = ref(false)
  const editSubmitting = ref(false)
  const editTarget = ref<ExceptionSlaPolicy>()
  const editForm = reactive<ExceptionSlaPolicyUpdateRequest>({
    dueHours: 24,
    escalationEnabled: true,
    escalateToPriority: 'HIGH',
    enabled: true,
    remark: ''
  })

  const editTargetLabel = computed(() => {
    if (!editTarget.value) return ''
    return `${options.categoryLabel(editTarget.value.category)} / ${options.priorityLabel(editTarget.value.priority)}`
  })

  const openEditDialog = (row: ExceptionSlaPolicy) => {
    editTarget.value = row
    editForm.dueHours = row.dueHours
    editForm.escalationEnabled = row.escalationEnabled
    editForm.escalateToPriority = row.escalateToPriority
    editForm.enabled = row.enabled
    editForm.remark = row.remark || ''
    editDialogVisible.value = true
  }

  const handleSaveEdit = async () => {
    if (!editTarget.value) return false
    editSubmitting.value = true
    try {
      await options.updatePolicy(editTarget.value.id, {
        dueHours: editForm.dueHours,
        escalationEnabled: editForm.escalationEnabled,
        escalateToPriority: editForm.escalateToPriority,
        enabled: editForm.enabled,
        remark: editForm.remark?.trim() || undefined
      })
      options.onSuccess?.(t('exceptionSlaPolicy.message.saved'))
      editDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('exceptionSlaPolicy.message.saveFailed'))
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
    editTargetLabel,
    handleSaveEdit,
    openEditDialog
  }
}
