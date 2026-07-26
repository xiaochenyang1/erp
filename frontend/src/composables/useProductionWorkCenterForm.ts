import { computed, reactive, ref } from 'vue'

import type {
  WorkCenter,
  WorkCenterCreateRequest,
  WorkCenterUpdateRequest
} from '@/api/production'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface ProductionWorkCenterFormState {
  id: string | number | undefined
  workCenterCode: string
  workCenterName: string
  remark: string
}

const emptyForm = (): ProductionWorkCenterFormState => ({
  id: undefined,
  workCenterCode: '',
  workCenterName: '',
  remark: ''
})

/**
 * Create/edit dialog for production work centers.
 * Element form validation stays on the page around handleSubmit.
 */
export const useProductionWorkCenterForm = (
  t: Translate,
  options: {
    createWorkCenter: (data: WorkCenterCreateRequest) => Promise<unknown>
    updateWorkCenter: (id: string | number, data: WorkCenterUpdateRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const formData = reactive<ProductionWorkCenterFormState>(emptyForm())
  const isEdit = computed(() => formData.id != null)
  const dialogTitle = computed(() => formData.id
    ? t('productionWorkCenter.dialog.edit')
    : t('productionWorkCenter.dialog.create'))

  const resetForm = () => {
    Object.assign(formData, emptyForm())
  }

  const handleAdd = () => {
    resetForm()
    dialogVisible.value = true
  }

  const handleEdit = (row: WorkCenter) => {
    Object.assign(formData, {
      id: row.id,
      workCenterCode: row.workCenterCode,
      workCenterName: row.workCenterName,
      remark: row.remark || ''
    })
    dialogVisible.value = true
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      if (formData.id != null) {
        await options.updateWorkCenter(formData.id, {
          workCenterName: formData.workCenterName,
          remark: formData.remark
        })
        options.onSuccess?.(t('productionWorkCenter.message.updated'))
      } else {
        await options.createWorkCenter({
          workCenterCode: formData.workCenterCode,
          workCenterName: formData.workCenterName,
          remark: formData.remark
        })
        options.onSuccess?.(t('productionWorkCenter.message.created'))
      }
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      // Global interceptor already surfaces API errors.
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    dialogTitle,
    dialogVisible,
    formData,
    handleAdd,
    handleEdit,
    handleSubmit,
    isEdit,
    resetForm,
    submitLoading
  }
}
