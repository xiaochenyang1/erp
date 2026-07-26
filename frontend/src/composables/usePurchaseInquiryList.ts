import { reactive, ref } from 'vue'

import type {
  PurchaseInquiry,
  PurchaseInquiryQuery
} from '@/api/purchase'
import type { Product, Supplier } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title?: string,
  options?: {
    type?: string
    confirmButtonText?: string
    cancelButtonText?: string
  }
) => Promise<unknown>
type PageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
}

export const usePurchaseInquiryList = (
  t: Translate,
  options: {
    getInquiries: (params: PurchaseInquiryQuery) => Promise<PageResponse<PurchaseInquiry>>
    getInquiry: (id: string | number) => Promise<PurchaseInquiry>
    submitInquiry: (id: string | number) => Promise<unknown>
    cancelInquiry: (id: string | number) => Promise<unknown>
    getProducts: (params: PageQuery) => Promise<PageResponse<Product>>
    getSuppliers: (params: PageQuery) => Promise<PageResponse<Supplier>>
    printInquiry: (doc: any) => void
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const optionsLoading = ref(false)
  const tableData = ref<PurchaseInquiry[]>([])
  const total = ref(0)
  const products = ref<Product[]>([])
  const suppliers = ref<Supplier[]>([])
  const searchForm = reactive<PurchaseInquiryQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    status: ''
  })

  const loadOptions = async () => {
    optionsLoading.value = true
    try {
      const [productPage, supplierPage] = await Promise.all([
        options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
        options.getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      ])
      products.value = productPage.records || []
      suppliers.value = supplierPage.records || []
    } catch {
      // Shared request interceptor surfaces the error.
    } finally {
      optionsLoading.value = false
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getInquiries(searchForm)
      tableData.value = res.records || []
      total.value = res.total || 0
    } catch {
      // Shared request interceptor surfaces the error.
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    searchForm.pageNo = 1
    void loadData()
  }

  const handleReset = () => {
    searchForm.keyword = ''
    searchForm.status = ''
    searchForm.pageNo = 1
    void loadData()
  }

  const handlePageChange = (page: number) => {
    searchForm.pageNo = page
    void loadData()
  }

  const handleSizeChange = (size: number) => {
    searchForm.pageSize = size
    searchForm.pageNo = 1
    void loadData()
  }

  const handleSubmit = async (row: PurchaseInquiry) => {
    try {
      await options.confirm(
        t('purchaseInquiryOps.message.submitConfirm', { no: row.inquiryNo }),
        t('purchaseInquiryOps.dialog.submit'),
        {
          type: 'warning',
          confirmButtonText: t('purchaseInquiryOps.action.confirm'),
          cancelButtonText: t('purchaseInquiryOps.action.cancel')
        }
      )
    } catch {
      return
    }
    try {
      await options.submitInquiry(row.id)
      options.onSuccess?.(t('purchaseInquiryOps.message.submitted'))
      await loadData()
    } catch {
      // Shared request interceptor surfaces the error.
    }
  }

  const handleCancel = async (row: PurchaseInquiry) => {
    try {
      await options.confirm(
        t('purchaseInquiryOps.message.cancelConfirm', { no: row.inquiryNo }),
        t('purchaseInquiryOps.dialog.cancel'),
        {
          type: 'warning',
          confirmButtonText: t('purchaseInquiryOps.action.confirm'),
          cancelButtonText: t('purchaseInquiryOps.action.cancel')
        }
      )
    } catch {
      return
    }
    try {
      await options.cancelInquiry(row.id)
      options.onSuccess?.(t('purchaseInquiryOps.message.cancelled'))
      await loadData()
    } catch {
      // Shared request interceptor surfaces the error.
    }
  }

  const handlePrint = async (row: PurchaseInquiry) => {
    try {
      await loadOptions()
      const detail = await options.getInquiry(row.id)
      const productMap = new Map(products.value.map((product) => [String(product.id), product]))
      const supplier = suppliers.value.find((item) => String(item.id) === String(detail.selectedSupplierId))
      options.printInquiry({
        ...detail,
        selectedSupplierName: supplier?.supplierName || supplier?.name || detail.selectedSupplierId,
        lines: (detail.lines || []).map((line) => {
          const product = productMap.get(String(line.productId))
          return {
            ...line,
            productCode: line.productCode || product?.productCode || product?.code || line.productId,
            productName: line.productName || product?.productName || product?.name || ''
          }
        })
      })
    } catch {
      options.onError?.(t('purchaseInquiryOps.message.printLoadFailed'))
    }
  }

  return {
    handleCancel,
    handlePageChange,
    handlePrint,
    handleReset,
    handleSearch,
    handleSizeChange,
    handleSubmit,
    loadData,
    loadOptions,
    loading,
    optionsLoading,
    products,
    searchForm,
    suppliers,
    tableData,
    total
  }
}
