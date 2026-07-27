import { reactive, ref, watch } from 'vue'

import type {
  PurchasePrice,
  PurchasePriceQuery,
  PurchasePriceSaveRequest
} from '@/api/purchase'
import type { Product, Supplier } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: { type?: string }) => Promise<unknown>

/**
 * Purchase price list + create/edit form with enable/disable and print load.
 * Element form validation stays on the page around confirmSave.
 */
export const usePurchasePriceList = (
  t: Translate,
  options: {
    getPurchasePrices: (params: PurchasePriceQuery) => Promise<PageResponse<PurchasePrice>>
    getPurchasePrice: (id: string | number) => Promise<PurchasePrice>
    createPurchasePrice: (data: PurchasePriceSaveRequest) => Promise<unknown>
    updatePurchasePrice: (id: string | number, data: PurchasePriceSaveRequest) => Promise<unknown>
    enablePurchasePrice: (id: string | number) => Promise<unknown>
    disablePurchasePrice: (id: string | number) => Promise<unknown>
    getSuppliers: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Supplier>>
    getProducts: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    printPurchasePrice: (price: PurchasePrice) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const loading = ref(false)
  const submitting = ref(false)
  const tableData = ref<PurchasePrice[]>([])
  const total = ref(0)
  const suppliers = ref<Supplier[]>([])
  const products = ref<Product[]>([])
  const dialogVisible = ref(false)
  const editingId = ref<string | number | null>(null)
  const scopeType = ref<'PRODUCT' | 'SUPPLIER'>('PRODUCT')

  const searchForm = reactive<PurchasePriceQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    supplierId: '',
    status: ''
  })

  const form = reactive({
    supplierId: '' as string,
    productId: '' as string,
    listPrice: 0,
    maxPrice: 0,
    effectiveFrom: '',
    effectiveTo: '' as string,
    remark: ''
  })

  watch(scopeType, (value) => {
    if (value === 'PRODUCT') {
      form.supplierId = ''
    }
  })

  const today = () => formatBusinessDate()

  const loadOptions = async () => {
    try {
      const [supplierPage, productPage] = await Promise.all([
        options.getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
        options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      ])
      suppliers.value = supplierPage.records || []
      products.value = productPage.records || []
      return true
    } catch {
      suppliers.value = []
      products.value = []
      options.onError?.(t('purchasePrice.message.optionsLoadFailed'))
      return false
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getPurchasePrices({
        ...searchForm,
        supplierId: searchForm.supplierId || undefined,
        status: searchForm.status || undefined,
        keyword: searchForm.keyword || undefined
      })
      tableData.value = res.records || []
      total.value = res.total || 0
      return true
    } catch {
      options.onError?.(t('purchasePrice.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    searchForm.pageNo = 1
    return loadData()
  }

  const handleReset = () => {
    searchForm.keyword = ''
    searchForm.supplierId = ''
    searchForm.status = ''
    searchForm.pageNo = 1
    return loadData()
  }

  const handlePageChange = (page: number) => {
    searchForm.pageNo = page
    return loadData()
  }

  const handleSizeChange = (size: number) => {
    searchForm.pageSize = size
    searchForm.pageNo = 1
    return loadData()
  }

  const resetForm = () => {
    editingId.value = null
    scopeType.value = 'PRODUCT'
    form.supplierId = ''
    form.productId = ''
    form.listPrice = 0
    form.maxPrice = 0
    form.effectiveFrom = today()
    form.effectiveTo = ''
    form.remark = ''
  }

  const handleCreate = async () => {
    await loadOptions()
    resetForm()
    dialogVisible.value = true
  }

  const handleEdit = async (row: PurchasePrice) => {
    await loadOptions()
    editingId.value = row.id
    scopeType.value = row.supplierId ? 'SUPPLIER' : 'PRODUCT'
    form.supplierId = row.supplierId ? String(row.supplierId) : ''
    form.productId = String(row.productId)
    form.listPrice = Number(row.listPrice || 0)
    form.maxPrice = Number(row.maxPrice || 0)
    form.effectiveFrom = row.effectiveFrom
    form.effectiveTo = row.effectiveTo || ''
    form.remark = row.remark || ''
    dialogVisible.value = true
  }

  const handlePrint = async (row: PurchasePrice) => {
    try {
      const detail = await options.getPurchasePrice(row.id)
      options.printPurchasePrice(detail)
      return true
    } catch {
      options.onError?.(t('purchasePrice.message.printLoadFailed'))
      return false
    }
  }

  const buildPayload = (): PurchasePriceSaveRequest => ({
    supplierId: scopeType.value === 'SUPPLIER' ? form.supplierId : null,
    productId: form.productId,
    listPrice: form.listPrice,
    maxPrice: form.maxPrice,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || null,
    remark: form.remark || undefined
  })

  const validateBusinessRules = () => {
    if (scopeType.value === 'SUPPLIER' && !form.supplierId) {
      options.onWarning?.(t('purchasePrice.validation.supplier'))
      return false
    }
    if (form.maxPrice < form.listPrice) {
      options.onWarning?.(t('purchasePrice.validation.maxBelowList'))
      return false
    }
    return true
  }

  const submitSave = async () => {
    if (!validateBusinessRules()) return false
    submitting.value = true
    try {
      const payload = buildPayload()
      if (editingId.value) {
        await options.updatePurchasePrice(editingId.value, payload)
        options.onSuccess?.(t('purchasePrice.message.saved'))
      } else {
        await options.createPurchasePrice(payload)
        options.onSuccess?.(t('purchasePrice.message.created'))
      }
      dialogVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('purchasePrice.message.saveFailed'))
      return false
    } finally {
      submitting.value = false
    }
  }

  const handleEnable = async (row: PurchasePrice) => {
    try {
      await options.enablePurchasePrice(row.id)
      options.onSuccess?.(t('purchasePrice.message.enabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('purchasePrice.message.enableFailed'))
      return false
    }
  }

  const handleDisable = async (row: PurchasePrice) => {
    try {
      await options.confirm(
        t('purchasePrice.message.disableConfirm'),
        t('purchasePrice.message.prompt'),
        { type: 'warning' }
      )
      await options.disablePurchasePrice(row.id)
      options.onSuccess?.(t('purchasePrice.message.disabled'))
      await loadData()
      return true
    } catch {
      return false
    }
  }

  return {
    buildPayload,
    dialogVisible,
    editingId,
    form,
    handleCreate,
    handleDisable,
    handleEdit,
    handleEnable,
    handlePageChange,
    handlePrint,
    handleReset,
    handleSearch,
    handleSizeChange,
    loadData,
    loadOptions,
    loading,
    products,
    resetForm,
    scopeType,
    searchForm,
    submitSave,
    submitting,
    suppliers,
    tableData,
    total,
    validateBusinessRules
  }
}
