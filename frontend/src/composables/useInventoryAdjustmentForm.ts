import { computed, reactive, ref } from 'vue'

import type {
  InventoryAdjustment,
  InventoryAdjustmentCreateRequest,
  InventoryAdjustmentItem
} from '@/api/inventory'
import type { Product } from '@/api/masterdata'
import { formatBusinessDate } from '@/utils/locale'
import {
  hydrateProductLineLabels,
  validateProductControlLines
} from '@/utils/productLines'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type InventoryAdjustmentFormData = InventoryAdjustmentCreateRequest & {
  items: InventoryAdjustmentItem[]
}

const emptyItem = (): InventoryAdjustmentItem => ({
  productId: 0,
  productCode: '',
  productName: '',
  quantity: 0,
  unitCost: 0,
  locationId: undefined,
  serialNos: '',
  lotNo: '',
  productionDate: '',
  expiryDate: '',
  lotControlled: undefined,
  shelfLifeControlled: undefined,
  serialControlled: undefined,
  reason: ''
})

export const useInventoryAdjustmentForm = (
  t: Translate,
  options: {
    getAdjustment: (id: string | number) => Promise<InventoryAdjustment>
    createAdjustment: (data: InventoryAdjustmentCreateRequest) => Promise<unknown>
    findProduct: (productId: string | number) => Product | undefined
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const submitLoading = ref(false)
  const isView = ref(false)

  const formData = reactive<InventoryAdjustmentFormData>({
    warehouseId: 0,
    adjustmentDate: '',
    type: 'GAIN',
    items: [],
    remark: ''
  })

  const selectedWarehouseId = computed(() => formData.warehouseId || undefined)

  const hydrateItems = async (items: InventoryAdjustmentItem[]) =>
    hydrateProductLineLabels(items, async (productId) => options.findProduct(productId) || {})

  const resetForm = () => {
    formData.warehouseId = 0
    formData.adjustmentDate = formatBusinessDate()
    formData.type = 'GAIN'
    formData.items = []
    formData.remark = ''
  }

  const handleCreate = () => {
    dialogTitle.value = t('inventoryAdjustments.dialog.create')
    isView.value = false
    resetForm()
    dialogVisible.value = true
  }

  const handleView = async (row: InventoryAdjustment) => {
    dialogTitle.value = t('inventoryAdjustments.dialog.view')
    isView.value = true
    try {
      const detail = await options.getAdjustment(row.id)
      Object.assign(formData, detail)
      formData.items = await hydrateItems(formData.items || [])
      dialogVisible.value = true
    } catch {
      options.onError?.(t('inventoryAdjustments.message.detailLoadFailed'))
    }
  }

  const handleAddItem = () => {
    formData.items.push(emptyItem())
  }

  const handleDeleteItem = (index: number) => {
    formData.items.splice(index, 1)
  }

  const handleProductChange = async (index: number) => {
    const item = formData.items[index]
    if (!item) return
    const product = options.findProduct(item.productId)
    if (product) {
      item.productCode = product.code || product.productCode || ''
      item.productName = product.name || product.productName || ''
      item.unitCost = product.purchasePrice ?? item.unitCost ?? 0
      item.lotControlled = Boolean(product.lotControlled)
      item.shelfLifeControlled = Boolean(product.shelfLifeControlled)
      item.serialControlled = Boolean(product.serialControlled)
    }
    if (item.productId) {
      const [hydrated] = await hydrateProductLineLabels([item], async () => product || {})
      Object.assign(item, hydrated)
    }
  }

  /** Lot/serial capture must match the adjusted quantity before the draft is created. */
  const validateForm = async () => {
    if (formData.items.length === 0) {
      options.onWarning?.(t('inventoryAdjustments.validation.itemRequired'))
      return false
    }

    formData.items = await hydrateItems(formData.items)
    const issues = validateProductControlLines(formData.items)
    if (issues.length > 0) {
      const issue = issues[0]
      const product = issue.productCode || issue.productName || String(issue.productId)
      options.onWarning?.(t(`inventoryAdjustments.validation.${issue.messageKey}`, {
        line: issue.index + 1,
        product,
        expected: issue.expectedSerialCount,
        actual: issue.actualSerialCount
      }))
      return false
    }
    return true
  }

  const handleSubmit = async () => {
    if (!(await validateForm())) return false

    submitLoading.value = true
    try {
      await options.createAdjustment(formData)
      options.onSuccess?.(t('inventoryAdjustments.message.success'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('inventoryAdjustments.message.failed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    dialogTitle,
    dialogVisible,
    formData,
    handleAddItem,
    handleCreate,
    handleDeleteItem,
    handleProductChange,
    handleSubmit,
    handleView,
    isView,
    resetForm,
    selectedWarehouseId,
    submitLoading,
    validateForm
  }
}
