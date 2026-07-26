import { reactive, ref } from 'vue'

import type {
  SalesDelivery,
  SalesDeliveryQuery
} from '@/api/sales'
import type { Customer, Location, Warehouse } from '@/api/masterdata'
import type { SalesOrder } from '@/api/sales'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>
type PageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
}

export const useSalesDeliveryList = (
  t: Translate,
  options: {
    getDeliveries: (params: SalesDeliveryQuery) => Promise<PageResponse<SalesDelivery>>
    getDelivery: (id: string | number) => Promise<SalesDelivery>
    cancelDelivery: (id: string | number) => Promise<unknown>
    postDelivery: (id: string | number) => Promise<unknown>
    updateLogistics: (
      id: string | number,
      payload: { logisticsStatus: string; carrierName?: string; trackingNo?: string }
    ) => Promise<unknown>
    getCustomers: (params: PageQuery) => Promise<PageResponse<Customer>>
    getWarehouses: (params: PageQuery) => Promise<PageResponse<Warehouse>>
    getOrders: (params: PageQuery) => Promise<PageResponse<SalesOrder>>
    getLocations: (params: PageQuery & { warehouseId?: string | number }) => Promise<PageResponse<Location>>
    printDelivery: (doc: SalesDelivery) => void
    confirm: Confirm
    initialDeliveryNo?: string
    initialLogisticsStatus?: string
    initialTrackingNo?: string
    onError?: Notify
    onSuccess?: Notify
    onInfo?: Notify
  }
) => {
  const queryParams = reactive<SalesDeliveryQuery>({
    pageNo: 1,
    pageSize: 10,
    deliveryNo: options.initialDeliveryNo || '',
    orderId: undefined,
    customerId: undefined,
    status: '',
    logisticsStatus: options.initialLogisticsStatus || '',
    trackingNo: options.initialTrackingNo || '',
    startDate: '',
    endDate: ''
  })
  const dateRange = ref<[string, string] | null>(null)
  const loading = ref(false)
  const tableData = ref<SalesDelivery[]>([])
  const total = ref(0)
  const customers = ref<Customer[]>([])
  const warehouses = ref<Warehouse[]>([])
  const locations = ref<Location[]>([])
  const orders = ref<SalesOrder[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getDeliveries(queryParams)
      tableData.value = response.records || []
      total.value = response.total || 0
    } catch {
      options.onError?.(t('salesDelivery.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadCustomers = async () => {
    try {
      const response = await options.getCustomers({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
      customers.value = response.records || []
    } catch {
      options.onError?.(t('salesDelivery.message.customersLoadFailed'))
    }
  }

  const loadWarehouses = async () => {
    try {
      const response = await options.getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
      warehouses.value = response.records || []
    } catch {
      options.onError?.(t('salesDelivery.message.warehousesLoadFailed'))
    }
  }

  const loadLocations = async (warehouseId?: string | number) => {
    try {
      const page = await options.getLocations({
        pageNo: 1,
        pageSize: 500,
        status: 'ACTIVE',
        warehouseId: warehouseId || undefined
      })
      locations.value = page.records || []
    } catch {
      locations.value = []
    }
  }

  const loadOrders = async () => {
    try {
      const response = await options.getOrders({ pageNo: 1, pageSize: 1000, status: 'APPROVED' })
      orders.value = response.records || []
    } catch {
      options.onError?.(t('salesDelivery.message.ordersLoadFailed'))
    }
  }

  const loadOptions = async () => {
    await Promise.all([loadCustomers(), loadWarehouses(), loadOrders(), loadLocations()])
  }

  const handleQuery = () => {
    if (dateRange.value) {
      queryParams.startDate = dateRange.value[0]
      queryParams.endDate = dateRange.value[1]
    } else {
      queryParams.startDate = ''
      queryParams.endDate = ''
    }
    queryParams.pageNo = 1
    void loadData()
  }

  const handleReset = () => {
    queryParams.deliveryNo = ''
    queryParams.orderId = undefined
    queryParams.customerId = undefined
    queryParams.status = ''
    queryParams.logisticsStatus = ''
    queryParams.trackingNo = ''
    dateRange.value = null
    queryParams.startDate = ''
    queryParams.endDate = ''
    handleQuery()
  }

  const handlePrint = async (row: SalesDelivery) => {
    try {
      const detail = await options.getDelivery(row.id)
      options.printDelivery(detail)
    } catch {
      options.onError?.(t('salesDelivery.message.printLoadFailed'))
    }
  }

  const handleCancel = async (row: SalesDelivery) => {
    try {
      await options.confirm(
        t('salesDelivery.message.cancelConfirm'),
        t('salesDelivery.message.prompt'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'warning'
        }
      )
      await options.cancelDelivery(row.id)
      options.onSuccess?.(t('salesDelivery.message.cancelled'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('salesDelivery.message.cancelFailed'))
      }
    }
  }

  const handlePost = async (row: SalesDelivery) => {
    try {
      await options.confirm(
        t('salesDelivery.message.postConfirm'),
        t('salesDelivery.message.prompt'),
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'warning'
        }
      )
      await options.postDelivery(row.id)
      options.onSuccess?.(t('salesDelivery.message.posted'))
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(t('salesDelivery.message.postFailed'))
      }
    }
  }

  const advanceLogistics = async (row: SalesDelivery) => {
    const order = ['PENDING_SHIP', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED']
    const current = row.logisticsStatus || 'PENDING_SHIP'
    const idx = order.indexOf(current)
    const next = order[Math.min(idx + 1, order.length - 1)]
    if (next === current) {
      options.onInfo?.(t('salesDelivery.message.logisticsDone'))
      return
    }
    await options.updateLogistics(row.id, {
      logisticsStatus: next,
      carrierName: row.carrierName,
      trackingNo: row.trackingNo
    })
    options.onSuccess?.(t('salesDelivery.message.logisticsUpdated'))
    handleQuery()
  }

  return {
    advanceLogistics,
    customers,
    dateRange,
    handleCancel,
    handlePost,
    handlePrint,
    handleQuery,
    handleReset,
    loadData,
    loadLocations,
    loadOptions,
    loadOrders,
    loading,
    locations,
    orders,
    queryParams,
    tableData,
    total,
    warehouses
  }
}
