import { reactive, ref } from 'vue'

import type {
  Location,
  LocationQuery,
  Warehouse,
  WarehouseQuery
} from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

type WarehouseLocationQuery = {
  warehouseId: string
  status: string
  keyword: string
  pageNo: number
  pageSize: number
}

/**
 * Warehouse-location options, paged query and enable/disable actions.
 * The create/edit form is coordinated by its dedicated form composable.
 */
export const useWarehouseLocationList = (
  t: Translate,
  options: {
    getWarehouses: (params: WarehouseQuery) => Promise<PageResponse<Warehouse>>
    getLocations: (params: LocationQuery) => Promise<PageResponse<Location>>
    enableLocation: (id: string | number) => Promise<unknown>
    disableLocation: (id: string | number) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const rows = ref<Location[]>([])
  const warehouses = ref<Warehouse[]>([])
  const total = ref(0)

  const query = reactive<WarehouseLocationQuery>({
    warehouseId: '',
    status: '',
    keyword: '',
    pageNo: 1,
    pageSize: 50
  })

  const loadOptions = async () => {
    try {
      const page = await options.getWarehouses({
        pageNo: 1,
        pageSize: 200,
        status: 'ACTIVE'
      })
      warehouses.value = page.records || []
      return true
    } catch {
      warehouses.value = []
      options.onError?.(t('warehouseLocation.message.optionsLoadFailed'))
      return false
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getLocations({
        pageNo: query.pageNo,
        pageSize: query.pageSize,
        warehouseId: query.warehouseId || undefined,
        status: query.status || undefined,
        keyword: query.keyword || undefined
      })
      rows.value = page.records || []
      total.value = page.total || 0
      return true
    } catch {
      options.onError?.(t('warehouseLocation.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleQuery = () => {
    query.pageNo = 1
    return loadData()
  }

  const handleReset = () => {
    query.warehouseId = ''
    query.status = ''
    query.keyword = ''
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

  const toggle = async (row: Location, enable: boolean) => {
    try {
      if (enable) {
        await options.enableLocation(row.id)
      } else {
        await options.disableLocation(row.id)
      }
      options.onSuccess?.(t(
        enable
          ? 'warehouseLocation.message.enabled'
          : 'warehouseLocation.message.disabled'
      ))
      await loadData()
      return true
    } catch {
      options.onError?.(t('warehouseLocation.message.toggleFailed'))
      return false
    }
  }

  return {
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loadOptions,
    loading,
    query,
    rows,
    toggle,
    total,
    warehouses
  }
}
