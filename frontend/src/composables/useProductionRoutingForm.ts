import { computed, reactive, ref } from 'vue'

import type {
  Routing,
  RoutingCreateRequest,
  RoutingOperation,
  RoutingUpdateRequest
} from '@/api/production'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface ProductionRoutingFormState {
  id: string | number | undefined
  routingCode: string
  routingName: string
  bomId: string | number | undefined
  remark: string
  operations: RoutingOperation[]
}

const emptyOperation = (): RoutingOperation => ({
  operationCode: '',
  operationName: '',
  workCenterId: '',
  standardMinutes: 1,
  remark: ''
})

const emptyForm = (): ProductionRoutingFormState => ({
  id: undefined,
  routingCode: '',
  routingName: '',
  bomId: undefined,
  remark: '',
  operations: []
})

/**
 * Create/edit dialog for production routings, including operation line edits.
 * Element form validation stays on the page; payload completeness lives here.
 */
export const useProductionRoutingForm = (
  t: Translate,
  options: {
    getRouting: (id: string | number) => Promise<Routing>
    createRouting: (data: RoutingCreateRequest) => Promise<unknown>
    updateRouting: (id: string | number, data: RoutingUpdateRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const formData = reactive<ProductionRoutingFormState>(emptyForm())
  const isEdit = computed(() => formData.id != null)

  const dialogTitle = computed(() => formData.id
    ? t('productionRouting.dialog.edit')
    : t('productionRouting.dialog.create'))

  const resetForm = () => {
    Object.assign(formData, emptyForm(), { operations: [] as RoutingOperation[] })
    formData.operations = []
  }

  const handleAdd = () => {
    resetForm()
    dialogVisible.value = true
  }

  /** Editing always refetches the detail so operation lines are complete. */
  const handleEdit = async (row: Routing) => {
    try {
      const res = await options.getRouting(row.id)
      Object.assign(formData, {
        id: res.id,
        routingCode: res.routingCode,
        routingName: res.routingName,
        bomId: res.bomId,
        remark: res.remark || '',
        operations: (res.operations || []).map((op) => ({ ...op }))
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('productionRouting.message.detailLoadFailed'))
      return false
    }
  }

  const handleAddOperation = () => {
    formData.operations.push(emptyOperation())
  }

  const handleDeleteOperation = (index: number) => {
    formData.operations.splice(index, 1)
  }

  const validateOperations = () => {
    if (formData.operations.length === 0) {
      options.onWarning?.(t('productionRouting.validation.operations'))
      return false
    }
    for (const [i, op] of formData.operations.entries()) {
      if (!op.operationCode || !op.operationName || !op.workCenterId || !op.standardMinutes) {
        options.onWarning?.(t('productionRouting.validation.operationRequired', { line: i + 1 }))
        return false
      }
    }
    return true
  }

  const handleSubmit = async () => {
    if (!validateOperations()) return false

    submitLoading.value = true
    try {
      const operations = formData.operations.map((op) => ({
        operationCode: op.operationCode,
        operationName: op.operationName,
        workCenterId: op.workCenterId,
        standardMinutes: op.standardMinutes,
        remark: op.remark
      }))
      if (formData.id != null) {
        const payload: RoutingUpdateRequest = {
          routingName: formData.routingName,
          remark: formData.remark,
          operations
        }
        await options.updateRouting(formData.id, payload)
        options.onSuccess?.(t('productionRouting.message.updated'))
      } else {
        if (formData.bomId == null || formData.bomId === '') {
          options.onWarning?.(t('productionRouting.validation.bom'))
          return false
        }
        const payload: RoutingCreateRequest = {
          routingCode: formData.routingCode,
          routingName: formData.routingName,
          bomId: formData.bomId,
          remark: formData.remark,
          operations
        }
        await options.createRouting(payload)
        options.onSuccess?.(t('productionRouting.message.created'))
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
    handleAddOperation,
    handleDeleteOperation,
    handleEdit,
    handleSubmit,
    isEdit,
    resetForm,
    submitLoading
  }
}
