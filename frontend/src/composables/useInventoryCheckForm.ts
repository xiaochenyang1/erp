import { computed, reactive, ref } from 'vue'

import type {
  InventoryCheck,
  InventoryCheckCreateRequest,
  InventoryCheckItem,
  InventoryCheckUpdateRequest,
  InventoryStock,
  InventoryStockQuery
} from '@/api/inventory'
import type { Product } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'
import { formatBusinessDate } from '@/utils/locale'
import {
  hydrateProductLineLabels,
  validateProductControlLines
} from '@/utils/productLines'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type InventoryCheckFormData = InventoryCheckCreateRequest & { items: InventoryCheckItem[] }

const emptyItem = (): InventoryCheckItem => ({
  productId: 0,
  productCode: '',
  productName: '',
  locationId: undefined,
  lotNo: '',
  productionDate: '',
  expiryDate: '',
  serialNos: '',
  lotControlled: undefined,
  shelfLifeControlled: undefined,
  serialControlled: undefined,
  bookQuantity: 0,
  actualQuantity: undefined,
  difference: undefined,
  remark: ''
})

export const useInventoryCheckForm = (
  t: Translate,
  options: {
    getCheck: (id: string | number) => Promise<InventoryCheck>
    createCheck: (data: InventoryCheckCreateRequest) => Promise<unknown>
    updateCheck: (id: string | number, data: InventoryCheckUpdateRequest) => Promise<unknown>
    getStocks: (params: InventoryStockQuery) => Promise<PageResponse<InventoryStock>>
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
  const isEdit = ref(false)
  const currentId = ref<string | number>('')

  const formData = reactive<InventoryCheckFormData>({
    warehouseId: 0,
    checkDate: '',
    items: [],
    remark: ''
  })

  const selectedWarehouseId = computed(() => formData.warehouseId || undefined)

  const loadProductLabels = async (productId: string | number) =>
    options.findProduct(productId) || {}

  const hydrateItems = async (items: InventoryCheckItem[]) =>
    hydrateProductLineLabels(items, loadProductLabels)

  const resetForm = () => {
    formData.warehouseId = 0
    formData.checkDate = formatBusinessDate()
    formData.items = []
    formData.remark = ''
    currentId.value = ''
  }

  const handleCreate = () => {
    dialogTitle.value = t('inventoryChecks.dialog.create')
    isView.value = false
    isEdit.value = false
    resetForm()
    dialogVisible.value = true
  }

  const openDetail = async (row: InventoryCheck, mode: 'view' | 'edit') => {
    dialogTitle.value = t(`inventoryChecks.dialog.${mode}`)
    isView.value = mode === 'view'
    isEdit.value = mode === 'edit'
    currentId.value = mode === 'edit' ? row.id : ''
    try {
      const detail = await options.getCheck(row.id)
      Object.assign(formData, detail)
      formData.items = await hydrateItems(formData.items || [])
      dialogVisible.value = true
    } catch {
      options.onError?.(t('inventoryChecks.message.detailLoadFailed'))
    }
  }

  const handleView = (row: InventoryCheck) => openDetail(row, 'view')
  const handleEdit = (row: InventoryCheck) => openDetail(row, 'edit')

  /** Prefill count lines from current on-hand stock of the chosen warehouse. */
  const handleWarehouseChange = async () => {
    if (!formData.warehouseId) return
    try {
      const response = await options.getStocks({
        pageNo: 1,
        pageSize: 1000,
        warehouseId: formData.warehouseId
      })
      formData.items = await hydrateItems((response.records || []).map((stock) => ({
        ...emptyItem(),
        productId: stock.productId,
        productCode: stock.productCode,
        productName: stock.productName,
        locationId: stock.locationId ?? undefined,
        bookQuantity: stock.quantity
      })))
    } catch {
      options.onError?.(t('inventoryChecks.message.stockLoadFailed'))
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
      item.productCode = product.code || product.productCode
      item.productName = product.name || product.productName
      item.bookQuantity = 0
      item.lotControlled = Boolean(product.lotControlled)
      item.shelfLifeControlled = Boolean(product.shelfLifeControlled)
      item.serialControlled = Boolean(product.serialControlled)
    }
    if (item.productId) {
      const [hydrated] = await hydrateProductLineLabels([item], async () => product || {})
      Object.assign(item, hydrated)
    }
  }

  const handleQuantityChange = (item: InventoryCheckItem) => {
    if (item.actualQuantity !== undefined) {
      item.difference = item.actualQuantity - (item.bookQuantity || 0)
    }
  }

  /** Serial/lot capture is validated against counted quantity, not book quantity. */
  const validateItems = async () => {
    if (formData.items.length === 0) {
      options.onWarning?.(t('inventoryChecks.validation.itemRequired'))
      return false
    }

    formData.items = await hydrateItems(formData.items)
    const issues = validateProductControlLines(formData.items.map((item) => ({
      ...item,
      quantity: item.actualQuantity
    })))
    if (issues.length > 0) {
      const issue = issues[0]
      const product = issue.productCode || issue.productName || String(issue.productId)
      options.onWarning?.(t(`inventoryChecks.validation.${issue.messageKey}`, {
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
    if (!(await validateItems())) return false

    submitLoading.value = true
    try {
      if (isEdit.value) {
        await options.updateCheck(currentId.value, { items: formData.items })
      } else {
        await options.createCheck(formData)
      }
      options.onSuccess?.(t('inventoryChecks.message.success'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('inventoryChecks.message.failed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    currentId,
    dialogTitle,
    dialogVisible,
    formData,
    handleAddItem,
    handleCreate,
    handleDeleteItem,
    handleEdit,
    handleProductChange,
    handleQuantityChange,
    handleSubmit,
    handleView,
    handleWarehouseChange,
    isEdit,
    isView,
    resetForm,
    selectedWarehouseId,
    submitLoading,
    validateItems
  }
}
