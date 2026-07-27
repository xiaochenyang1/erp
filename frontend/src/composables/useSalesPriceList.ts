import { reactive, ref, watch } from 'vue'

import type {
  SalesPrice,
  SalesPriceQuery,
  SalesPriceSaveRequest
} from '@/api/sales'
import type { Customer, Product } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: { type?: string }) => Promise<unknown>

/**
 * Sales price list + create/edit form with enable/disable and print load.
 * Element form validation stays on the page around confirmSave.
 */
export const useSalesPriceList = (
  t: Translate,
  options: {
    getSalesPrices: (params: SalesPriceQuery) => Promise<PageResponse<SalesPrice>>
    getSalesPrice: (id: string | number) => Promise<SalesPrice>
    createSalesPrice: (data: SalesPriceSaveRequest) => Promise<unknown>
    updateSalesPrice: (id: string | number, data: SalesPriceSaveRequest) => Promise<unknown>
    enableSalesPrice: (id: string | number) => Promise<unknown>
    disableSalesPrice: (id: string | number) => Promise<unknown>
    getCustomers: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Customer>>
    getProducts: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    printSalesPrice: (price: SalesPrice) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const loading = ref(false)
  const submitting = ref(false)
  const tableData = ref<SalesPrice[]>([])
  const total = ref(0)
  const customers = ref<Customer[]>([])
  const products = ref<Product[]>([])
  const dialogVisible = ref(false)
  const editingId = ref<string | number | null>(null)
  const scopeType = ref<'PRODUCT' | 'CUSTOMER'>('PRODUCT')

  const searchForm = reactive<SalesPriceQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    customerId: '',
    status: ''
  })

  const form = reactive({
    customerId: '' as string,
    productId: '' as string,
    listPrice: 0,
    minPrice: 0,
    effectiveFrom: '',
    effectiveTo: '' as string,
    remark: ''
  })

  watch(scopeType, (value) => {
    if (value === 'PRODUCT') {
      form.customerId = ''
    }
  })

  const today = () => formatBusinessDate()

  const loadOptions = async () => {
    try {
      const [customerPage, productPage] = await Promise.all([
        options.getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
        options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      ])
      customers.value = customerPage.records || []
      products.value = productPage.records || []
      return true
    } catch {
      customers.value = []
      products.value = []
      options.onError?.(t('salesPrice.message.optionsLoadFailed'))
      return false
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getSalesPrices({
        ...searchForm,
        customerId: searchForm.customerId || undefined,
        status: searchForm.status || undefined,
        keyword: searchForm.keyword || undefined
      })
      tableData.value = res.records || []
      total.value = res.total || 0
      return true
    } catch {
      options.onError?.(t('salesPrice.message.loadFailed'))
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
    searchForm.customerId = ''
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
    form.customerId = ''
    form.productId = ''
    form.listPrice = 0
    form.minPrice = 0
    form.effectiveFrom = today()
    form.effectiveTo = ''
    form.remark = ''
  }

  const handleCreate = async () => {
    await loadOptions()
    resetForm()
    dialogVisible.value = true
  }

  const handleEdit = async (row: SalesPrice) => {
    await loadOptions()
    editingId.value = row.id
    scopeType.value = row.customerId ? 'CUSTOMER' : 'PRODUCT'
    form.customerId = row.customerId ? String(row.customerId) : ''
    form.productId = String(row.productId)
    form.listPrice = Number(row.listPrice || 0)
    form.minPrice = Number(row.minPrice || 0)
    form.effectiveFrom = row.effectiveFrom
    form.effectiveTo = row.effectiveTo || ''
    form.remark = row.remark || ''
    dialogVisible.value = true
  }

  const handlePrint = async (row: SalesPrice) => {
    try {
      const detail = await options.getSalesPrice(row.id)
      options.printSalesPrice(detail)
      return true
    } catch {
      options.onError?.(t('salesPrice.message.printLoadFailed'))
      return false
    }
  }

  const buildPayload = (): SalesPriceSaveRequest => ({
    customerId: scopeType.value === 'CUSTOMER' ? form.customerId : null,
    productId: form.productId,
    listPrice: form.listPrice,
    minPrice: form.minPrice,
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || null,
    remark: form.remark || undefined
  })

  const validateBusinessRules = () => {
    if (scopeType.value === 'CUSTOMER' && !form.customerId) {
      options.onWarning?.(t('salesPrice.validation.customer'))
      return false
    }
    if (form.minPrice > form.listPrice) {
      options.onWarning?.(t('salesPrice.validation.minAboveList'))
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
        await options.updateSalesPrice(editingId.value, payload)
        options.onSuccess?.(t('salesPrice.message.saved'))
      } else {
        await options.createSalesPrice(payload)
        options.onSuccess?.(t('salesPrice.message.created'))
      }
      dialogVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('salesPrice.message.saveFailed'))
      return false
    } finally {
      submitting.value = false
    }
  }

  const handleEnable = async (row: SalesPrice) => {
    try {
      await options.enableSalesPrice(row.id)
      options.onSuccess?.(t('salesPrice.message.enabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('salesPrice.message.enableFailed'))
      return false
    }
  }

  const handleDisable = async (row: SalesPrice) => {
    try {
      await options.confirm(
        t('salesPrice.message.disableConfirm'),
        t('salesPrice.message.prompt'),
        { type: 'warning' }
      )
      await options.disableSalesPrice(row.id)
      options.onSuccess?.(t('salesPrice.message.disabled'))
      await loadData()
      return true
    } catch {
      // Cancelled by user or request failure — interceptor may also surface errors.
      return false
    }
  }

  return {
    buildPayload,
    customers,
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
    tableData,
    total,
    validateBusinessRules
  }
}
