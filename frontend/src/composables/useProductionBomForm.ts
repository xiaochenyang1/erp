import { computed, reactive, ref } from 'vue'

import type { BOM, BOMItem, BOMRequest } from '@/api/production'
import type { Product } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface ProductionBomFormState {
  id: string | number | undefined
  productId: string | number | undefined
  baseQty: number
  items: BOMItem[]
  remark: string
}

const emptyItem = (): BOMItem => ({
  materialId: '',
  materialCode: '',
  materialName: '',
  quantity: 1,
  unit: '',
  scrapRate: 0,
  remark: ''
})

const emptyForm = (): ProductionBomFormState => ({
  id: undefined,
  productId: undefined,
  baseQty: 1,
  items: [],
  remark: ''
})

/**
 * Create/edit dialog state for production BOMs, including material line edits.
 * Element form validation stays on the page; this composable owns payload checks.
 */
export const useProductionBomForm = (
  t: Translate,
  options: {
    getBOM: (id: string | number) => Promise<BOM>
    createBOM: (data: BOMRequest) => Promise<unknown>
    updateBOM: (id: string | number, data: BOMRequest) => Promise<unknown>
    /** Live product options used to denormalize material code/name/unit on select. */
    getProducts: () => Product[]
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitLoading = ref(false)
  const formData = reactive<ProductionBomFormState>(emptyForm())

  const dialogTitle = computed(() => formData.id
    ? t('productionBom.dialog.edit')
    : t('productionBom.dialog.create'))

  const resetForm = () => {
    Object.assign(formData, emptyForm(), { items: [] as BOMItem[] })
    formData.items = []
  }

  const handleAdd = () => {
    resetForm()
    formData.baseQty = 1
    dialogVisible.value = true
  }

  /** Editing always refetches the detail so material lines are complete. */
  const handleEdit = async (row: BOM) => {
    try {
      const res = await options.getBOM(row.id)
      Object.assign(formData, {
        id: res.id,
        productId: res.productId,
        baseQty: res.baseQty,
        items: (res.items || []).map((item) => ({ ...item })),
        remark: res.remark || ''
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('productionBom.message.detailLoadFailed'))
      return false
    }
  }

  const handleMaterialChange = (materialId: string | number, row: BOMItem) => {
    const material = options.getProducts().find((p) => String(p.id) === String(materialId))
    if (material) {
      row.materialId = material.id
      row.materialCode = material.productCode || material.code || ''
      row.materialName = material.productName || material.name || ''
      row.unit = material.unitName || material.unit || ''
    }
  }

  const handleAddItem = () => {
    formData.items.push(emptyItem())
  }

  const handleDeleteItem = (index: number) => {
    formData.items.splice(index, 1)
  }

  const handleSubmit = async () => {
    if (formData.items.length === 0) {
      options.onWarning?.(t('productionBom.validation.materials'))
      return false
    }
    if (formData.productId == null || formData.productId === '') {
      options.onWarning?.(t('productionBom.validation.product'))
      return false
    }

    submitLoading.value = true
    try {
      const payload: BOMRequest = {
        productId: formData.productId,
        baseQty: formData.baseQty,
        items: formData.items,
        remark: formData.remark
      }
      if (formData.id) {
        await options.updateBOM(formData.id, payload)
        options.onSuccess?.(t('productionBom.message.updated'))
      } else {
        await options.createBOM(payload)
        options.onSuccess?.(t('productionBom.message.created'))
      }
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('productionBom.message.actionFailed'))
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
    handleAddItem,
    handleDeleteItem,
    handleEdit,
    handleMaterialChange,
    handleSubmit,
    resetForm,
    submitLoading
  }
}
