import { reactive, ref } from 'vue'

import type { Product, Warehouse } from '@/api/masterdata'
import type {
  SalesOrder,
  SalesQuote,
  SalesQuoteQuery
} from '@/api/sales'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: { type?: string }) => Promise<unknown>
type Alert = (message: string, title: string) => Promise<unknown> | unknown

/**
 * Sales quote list: query/paging, confirm/cancel, print, detail alert, convert-to-order.
 */
export const useSalesQuoteList = (
  t: Translate,
  options: {
    getSalesQuotes: (params: SalesQuoteQuery) => Promise<PageResponse<SalesQuote>>
    getSalesQuote: (id: string | number) => Promise<SalesQuote>
    confirmSalesQuote: (id: string | number) => Promise<unknown>
    cancelSalesQuote: (id: string | number) => Promise<unknown>
    convertSalesQuoteToOrder: (
      id: string | number,
      warehouseId: string | number
    ) => Promise<Pick<SalesOrder, 'orderNo'> | SalesOrder>
    getWarehouses: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Warehouse>>
    getProducts?: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    printSalesQuote: (quote: SalesQuote) => void
    detailContent: (detail: SalesQuote) => string
    confirm: Confirm
    alert: Alert
    products?: { value: Product[] }
    loadProducts?: () => Promise<boolean>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const loading = ref(false)
  const converting = ref(false)
  const rows = ref<SalesQuote[]>([])
  const total = ref(0)
  const warehouses = ref<Warehouse[]>([])
  const convertVisible = ref(false)
  const convertQuoteId = ref<string | number>('')
  const convertWarehouseId = ref('')

  const query = reactive<SalesQuoteQuery>({
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    status: ''
  })

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getSalesQuotes({
        ...query,
        keyword: query.keyword || undefined,
        status: query.status || undefined
      })
      rows.value = page.records || []
      total.value = page.total || 0
      return true
    } catch {
      options.onError?.(t('salesQuote.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    query.pageNo = 1
    return loadData()
  }

  const handleReset = () => {
    query.keyword = ''
    query.status = ''
    query.pageNo = 1
    return loadData()
  }

  const handlePageChange = (page: number) => {
    query.pageNo = page
    return loadData()
  }

  const handleSizeChange = (size: number) => {
    query.pageSize = size
    query.pageNo = 1
    return loadData()
  }

  const resolveProductMap = async () => {
    const productMap = new Map<string, Product>()
    const cached = options.products?.value || []
    cached.forEach((product) => productMap.set(String(product.id), product))
    if (productMap.size === 0 && options.loadProducts) {
      await options.loadProducts()
      ;(options.products?.value || []).forEach((product) =>
        productMap.set(String(product.id), product)
      )
    }
    if (productMap.size === 0 && options.getProducts) {
      try {
        const page = await options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
        ;(page.records || []).forEach((product) => productMap.set(String(product.id), product))
      } catch {
        // Print can still fall back to product ids.
      }
    }
    return productMap
  }

  const handlePrint = async (row: SalesQuote) => {
    try {
      const detail = await options.getSalesQuote(row.id)
      const productMap = await resolveProductMap()
      options.printSalesQuote({
        ...detail,
        lines: (detail.lines || []).map((line) => {
          const product = productMap.get(String(line.productId)) as
            | (Product & { code?: string; name?: string })
            | undefined
          return {
            ...line,
            productCode: product?.productCode || product?.code || String(line.productId),
            productName: product?.productName || product?.name || ''
          }
        })
      })
      return true
    } catch {
      options.onError?.(t('salesQuote.message.printLoadFailed'))
      return false
    }
  }

  const openView = async (row: SalesQuote) => {
    try {
      const detail = await options.getSalesQuote(row.id)
      await options.alert(options.detailContent(detail), t('salesQuote.detailTitle'))
      return true
    } catch {
      options.onError?.(t('salesQuote.message.detailLoadFailed'))
      return false
    }
  }

  const confirmQuote = async (row: SalesQuote) => {
    try {
      await options.confirmSalesQuote(row.id)
      options.onSuccess?.(t('salesQuote.message.confirmed'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('salesQuote.message.confirmFailed'))
      return false
    }
  }

  const cancelQuote = async (row: SalesQuote) => {
    try {
      await options.confirm(
        t('salesQuote.message.cancelConfirm', { quoteNo: row.quoteNo }),
        t('salesQuote.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.cancelSalesQuote(row.id)
      options.onSuccess?.(t('salesQuote.message.cancelled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('salesQuote.message.cancelFailed'))
      return false
    }
  }

  const loadWarehouses = async () => {
    try {
      const page = await options.getWarehouses({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      warehouses.value = page.records || []
      return true
    } catch {
      warehouses.value = []
      options.onError?.(t('salesQuote.message.optionsLoadFailed'))
      return false
    }
  }

  const openConvert = async (row: SalesQuote) => {
    await loadWarehouses()
    convertQuoteId.value = row.id
    convertWarehouseId.value = ''
    convertVisible.value = true
  }

  const doConvert = async () => {
    if (!convertWarehouseId.value) {
      options.onWarning?.(t('salesQuote.message.selectWarehouse'))
      return false
    }
    converting.value = true
    try {
      const order = await options.convertSalesQuoteToOrder(
        convertQuoteId.value,
        convertWarehouseId.value
      )
      options.onSuccess?.(
        t('salesQuote.message.converted', { orderNo: order.orderNo })
      )
      convertVisible.value = false
      await loadData()
      return true
    } catch {
      options.onError?.(t('salesQuote.message.convertFailed'))
      return false
    } finally {
      converting.value = false
    }
  }

  return {
    cancelQuote,
    confirmQuote,
    convertQuoteId,
    convertVisible,
    convertWarehouseId,
    converting,
    doConvert,
    handlePageChange,
    handlePrint,
    handleReset,
    handleSearch,
    handleSizeChange,
    loadData,
    loadWarehouses,
    loading,
    openConvert,
    openView,
    query,
    rows,
    total,
    warehouses
  }
}
