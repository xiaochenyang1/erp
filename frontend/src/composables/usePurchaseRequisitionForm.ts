import { reactive, ref } from 'vue'

import type { PurchaseRequisition } from '@/api/purchase'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface PurchaseRequisitionLineForm {
  productId: string
  qty: number
  remark: string
}

export interface PurchaseRequisitionFormState {
  requisitionDate: string
  neededDate: string
  supplierId: string
  remark: string
  lines: PurchaseRequisitionLineForm[]
}

export interface PurchaseRequisitionSavePayload {
  requisitionDate: string
  neededDate: string | null
  supplierId: string | null
  remark?: string
  lines: Array<{
    productId: string
    qty: number
    remark?: string
  }>
}

const emptyLine = (): PurchaseRequisitionLineForm => ({
  productId: '',
  qty: 1,
  remark: ''
})

/**
 * Create/edit dialog for purchase requisitions.
 * Options loading is injected so the form can share the list's product/supplier cache.
 */
export const usePurchaseRequisitionForm = (
  t: Translate,
  options: {
    getRequisition: (id: string | number) => Promise<PurchaseRequisition>
    createRequisition: (data: PurchaseRequisitionSavePayload) => Promise<unknown>
    updateRequisition: (
      id: string | number,
      data: PurchaseRequisitionSavePayload
    ) => Promise<unknown>
    ensureOptions?: () => void | Promise<void>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const saving = ref(false)
  const editingId = ref<string | number | null>(null)

  const form = reactive<PurchaseRequisitionFormState>({
    requisitionDate: formatBusinessDate(),
    neededDate: '',
    supplierId: '',
    remark: '',
    lines: []
  })

  const resetForm = () => {
    editingId.value = null
    form.requisitionDate = formatBusinessDate()
    form.neededDate = ''
    form.supplierId = ''
    form.remark = ''
    form.lines = [emptyLine()]
  }

  const openCreate = async () => {
    await options.ensureOptions?.()
    resetForm()
    dialogVisible.value = true
  }

  const openEdit = async (row: PurchaseRequisition) => {
    try {
      await options.ensureOptions?.()
      const detailData = await options.getRequisition(row.id)
      editingId.value = detailData.id
      form.requisitionDate = detailData.requisitionDate
      form.neededDate = detailData.neededDate || ''
      form.supplierId = detailData.supplierId != null ? String(detailData.supplierId) : ''
      form.remark = detailData.remark || ''
      form.lines = (detailData.lines || []).map((line) => ({
        productId: String(line.productId),
        qty: Number(line.qty || 1),
        remark: line.remark || ''
      }))
      if (form.lines.length === 0) {
        form.lines = [emptyLine()]
      }
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('purchaseRequisition.message.detailLoadFailed'))
      return false
    }
  }

  const addLine = () => {
    form.lines.push(emptyLine())
  }

  const removeLine = (index: number) => {
    form.lines.splice(index, 1)
  }

  const save = async () => {
    if (
      !form.requisitionDate
      || !form.lines.length
      || form.lines.some((line) => !line.productId || !line.qty)
    ) {
      options.onWarning?.(t('purchaseRequisition.validation.required'))
      return false
    }

    saving.value = true
    try {
      const payload: PurchaseRequisitionSavePayload = {
        requisitionDate: form.requisitionDate,
        neededDate: form.neededDate || null,
        supplierId: form.supplierId || null,
        remark: form.remark || undefined,
        lines: form.lines.map((line) => ({
          productId: line.productId,
          qty: line.qty,
          remark: line.remark || undefined
        }))
      }
      if (editingId.value) {
        await options.updateRequisition(editingId.value, payload)
        options.onSuccess?.(t('purchaseRequisition.message.saved'))
      } else {
        await options.createRequisition(payload)
        options.onSuccess?.(t('purchaseRequisition.message.created'))
      }
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('purchaseRequisition.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    addLine,
    dialogVisible,
    editingId,
    form,
    openCreate,
    openEdit,
    removeLine,
    resetForm,
    save,
    saving
  }
}
