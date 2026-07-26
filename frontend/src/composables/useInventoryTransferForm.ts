import { computed, reactive, ref } from 'vue'

import type {
  InventoryTransfer,
  InventoryTransferCreateRequest,
  InventoryTransferItem
} from '@/api/inventory'
import type { Product } from '@/api/masterdata'
import { formatBusinessDate } from '@/utils/locale'
import {
  hydrateProductLineLabels,
  validateProductControlLines
} from '@/utils/productLines'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type InventoryTransferFormData = InventoryTransferCreateRequest & {
  items: InventoryTransferItem[]
}

const emptyItem = (): InventoryTransferItem => ({
  productId: 0,
  productCode: '',
  productName: '',
  quantity: 0,
  unitCost: 0,
  fromLocationId: undefined,
  toLocationId: undefined,
  serialNos: '',
  lotNo: '',
  productionDate: '',
  expiryDate: '',
  lotControlled: undefined,
  shelfLifeControlled: undefined,
  serialControlled: undefined,
  remark: ''
})

export const useInventoryTransferForm = (
  t: Translate,
  options: {
    getTransfer: (id: string | number) => Promise<InventoryTransfer>
    createTransfer: (data: InventoryTransferCreateRequest) => Promise<unknown>
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

  const formData = reactive<InventoryTransferFormData>({
    fromWarehouseId: 0,
    toWarehouseId: 0,
    transferDate: '',
    items: [],
    remark: ''
  })

  const selectedFromWarehouseId = computed(() => formData.fromWarehouseId || undefined)
  const selectedToWarehouseId = computed(() => formData.toWarehouseId || undefined)

  const hydrateItems = async (items: InventoryTransferItem[]) =>
    hydrateProductLineLabels(items, async (productId) => options.findProduct(productId) || {})

  const resetForm = () => {
    formData.fromWarehouseId = 0
    formData.toWarehouseId = 0
    formData.transferDate = formatBusinessDate()
    formData.items = []
    formData.remark = ''
  }

  const handleCreate = () => {
    dialogTitle.value = t('inventoryTransfers.dialog.create')
    isView.value = false
    resetForm()
    dialogVisible.value = true
  }

  const handleView = async (row: InventoryTransfer) => {
    dialogTitle.value = t('inventoryTransfers.dialog.view')
    isView.value = true
    try {
      const detail = await options.getTransfer(row.id)
      Object.assign(formData, detail)
      formData.items = await hydrateItems(formData.items || [])
      dialogVisible.value = true
    } catch {
      options.onError?.(t('inventoryTransfers.message.detailLoadFailed'))
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

  /** Source and target warehouse must differ, and lot/serial capture must match qty. */
  const validateForm = async () => {
    if (String(formData.fromWarehouseId) === String(formData.toWarehouseId)) {
      options.onWarning?.(t('inventoryTransfers.validation.warehousesDifferent'))
      return false
    }

    if (formData.items.length === 0) {
      options.onWarning?.(t('inventoryTransfers.validation.itemRequired'))
      return false
    }

    formData.items = await hydrateItems(formData.items)
    const issues = validateProductControlLines(formData.items)
    if (issues.length > 0) {
      const issue = issues[0]
      const product = issue.productCode || issue.productName || String(issue.productId)
      options.onWarning?.(t(`inventoryTransfers.validation.${issue.messageKey}`, {
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
      await options.createTransfer(formData)
      options.onSuccess?.(t('inventoryTransfers.message.success'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('inventoryTransfers.message.failed'))
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
    selectedFromWarehouseId,
    selectedToWarehouseId,
    submitLoading,
    validateForm
  }
}
