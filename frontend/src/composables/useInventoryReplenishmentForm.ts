import { reactive, ref } from 'vue'

import type {
  InventoryReplenishmentSuggestion,
  InventoryReplenishmentSuggestionUpdateRequest
} from '@/api/inventory'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface InventoryReplenishmentEditForm {
  id: string
  suggestionNo: string
  warehouseName: string
  productName: string
  supplierId: string | number | undefined
  suggestedQty: number
  expectedArrivalDate: string
  remark: string
}

/**
 * Edit dialog for draft replenishment suggestions.
 * Element form validation stays on the page around submitEdit.
 */
export const useInventoryReplenishmentForm = (
  t: Translate,
  options: {
    updateSuggestion: (
      id: string | number,
      data: InventoryReplenishmentSuggestionUpdateRequest
    ) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const editDialogVisible = ref(false)
  const editSubmitting = ref(false)
  const editForm = reactive<InventoryReplenishmentEditForm>({
    id: '',
    suggestionNo: '',
    warehouseName: '',
    productName: '',
    supplierId: undefined,
    suggestedQty: 1,
    expectedArrivalDate: '',
    remark: ''
  })

  const handleEdit = (row: InventoryReplenishmentSuggestion) => {
    Object.assign(editForm, {
      id: row.id,
      suggestionNo: row.suggestionNo,
      warehouseName: row.warehouseName || '-',
      productName: `${row.productCode || ''} ${row.productName || ''}`.trim() || '-',
      supplierId: row.supplierId,
      suggestedQty: Number(row.suggestedQty || 0),
      expectedArrivalDate: row.expectedArrivalDate || '',
      remark: row.remark || ''
    })
    editDialogVisible.value = true
  }

  const submitEdit = async () => {
    editSubmitting.value = true
    try {
      await options.updateSuggestion(editForm.id, {
        supplierId: editForm.supplierId,
        suggestedQty: editForm.suggestedQty,
        expectedArrivalDate: editForm.expectedArrivalDate || undefined,
        remark: editForm.remark || undefined
      })
      options.onSuccess?.(t('inventoryReplenishment.message.updated'))
      editDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('inventoryReplenishment.message.saveFailed'))
      return false
    } finally {
      editSubmitting.value = false
    }
  }

  return {
    editDialogVisible,
    editForm,
    editSubmitting,
    handleEdit,
    submitEdit
  }
}
