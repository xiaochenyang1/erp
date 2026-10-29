import { reactive, ref } from 'vue'

import type { BOM, ProductionOrder, ProductionOrderQuery } from '@/api/production'
import type { Location, Product, Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type PageQuery = { pageNo?: number; pageSize?: number; status?: string }

export const useProductionOrderList = (
  t: Translate,
  options: {
    getOrders: (params: ProductionOrderQuery) => Promise<PageResponse<ProductionOrder>>
    getOrder: (orderId: string | number) => Promise<ProductionOrder>
    getProducts: (params: PageQuery) => Promise<PageResponse<Product>>
    getWarehouses: (params: PageQuery) => Promise<PageResponse<Warehouse>>
    getBoms: (params: PageQuery) => Promise<PageResponse<BOM>>
    getLocations: (params: PageQuery & { warehouseId?: string | number }) => Promise<PageResponse<Location>>
    printOrder: (order: ProductionOrder) => void
    onError?: Notify
  }
) => {
  const queryForm = reactive({
    orderNo: '',
    productId: undefined as string | number | undefined,
    status: ''
  })
  const loading = ref(false)
  const tableData = ref<ProductionOrder[]>([])
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })
  const productOptions = ref<Product[]>([])
  const warehouseOptions = ref<Warehouse[]>([])
  const allBomOptions = ref<BOM[]>([])
  const finishedLocations = ref<Location[]>([])
  const materialLocations = ref<Location[]>([])
  const viewDialogVisible = ref(false)
  const viewLoading = ref(false)
  const viewData = ref<ProductionOrder>({} as ProductionOrder)
  let listRequestId = 0
  let detailRequestId = 0

  const loadLocationsByWarehouse = async (warehouseId?: string | number) => {
    if (warehouseId == null || warehouseId === '') {
      return [] as Location[]
    }
    const page = await options.getLocations({
      pageNo: 1,
      pageSize: 500,
      status: 'ACTIVE',
      warehouseId
    })
    return page.records || []
  }

  const loadFinishedLocations = async (warehouseId?: string | number) => {
    try {
      finishedLocations.value = await loadLocationsByWarehouse(warehouseId)
    } catch (error) {
      finishedLocations.value = []
      console.error(t('productionOrder.message.optionsLoadFailed'), error)
    }
  }

  const loadMaterialLocations = async (warehouseId?: string | number) => {
    try {
      materialLocations.value = await loadLocationsByWarehouse(warehouseId)
    } catch (error) {
      materialLocations.value = []
      console.error(t('productionOrder.message.optionsLoadFailed'), error)
    }
  }

  const loadOptions = async () => {
    try {
      const optionPageQuery = { pageNo: 1, pageSize: 200 }
      const [products, warehouses, boms] = await Promise.all([
        options.getProducts(optionPageQuery),
        options.getWarehouses(optionPageQuery),
        options.getBoms(optionPageQuery)
      ])
      productOptions.value = products.records || []
      warehouseOptions.value = warehouses.records || []
      allBomOptions.value = boms.records || []
    } catch (error) {
      console.error(t('productionOrder.message.optionsLoadFailed'), error)
    }
  }

  const loadData = async () => {
    const requestId = ++listRequestId
    loading.value = true
    try {
      const params: ProductionOrderQuery = {
        keyword: queryForm.orderNo || undefined,
        productId: queryForm.productId,
        status: queryForm.status || undefined,
        pageNo: pagination.page,
        pageSize: pagination.size
      }
      const res = await options.getOrders(params)
      if (requestId !== listRequestId) return
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } catch (error) {
      if (requestId !== listRequestId) return
      console.error(t('productionOrder.message.orderLoadFailed'), error)
      options.onError?.(t('productionOrder.message.loadFailed'))
    } finally {
      if (requestId === listRequestId) loading.value = false
    }
  }

  const handleQuery = () => {
    pagination.page = 1
    return loadData()
  }

  const handlePageChange = (page: number) => {
    pagination.page = page
    return loadData()
  }

  const handleSizeChange = (size: number) => {
    pagination.size = size
    pagination.page = 1
    return loadData()
  }

  const handleReset = () => {
    Object.assign(queryForm, {
      orderNo: '',
      productId: undefined,
      status: ''
    })
    pagination.page = 1
    return loadData()
  }

  const handleView = async (row: ProductionOrder) => {
    const requestId = ++detailRequestId
    viewDialogVisible.value = true
    viewLoading.value = true
    viewData.value = {} as ProductionOrder
    try {
      const detail = await options.getOrder(row.id)
      if (requestId !== detailRequestId) return false
      viewData.value = detail
      return true
    } catch {
      if (requestId !== detailRequestId) return false
      options.onError?.(t('productionOrder.message.detailLoadFailed'))
      viewDialogVisible.value = false
      return false
    } finally {
      if (requestId === detailRequestId) viewLoading.value = false
    }
  }

  const handlePrint = async (row: ProductionOrder) => {
    try {
      const order = await options.getOrder(row.id)
      options.printOrder(order)
    } catch {
      options.onError?.(t('productionOrder.message.printLoadFailed'))
    }
  }

  return {
    allBomOptions,
    finishedLocations,
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleView,
    loadData,
    loadFinishedLocations,
    loadMaterialLocations,
    loadOptions,
    loading,
    materialLocations,
    pagination,
    productOptions,
    queryForm,
    tableData,
    viewData,
    viewDialogVisible,
    viewLoading,
    warehouseOptions
  }
}
