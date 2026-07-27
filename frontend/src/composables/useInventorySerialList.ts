import { computed, reactive, ref } from 'vue'

import type { InventorySerial } from '@/api/inventory'
import type { Location, Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

type SerialQuery = {
  keyword: string
  status: string
  warehouseId: string
  locationId: string
  pageNo: number
  pageSize: number
}

/**
 * Inventory serial list: options, query, issue and scrap.
 * Create form lives in useInventorySerialForm.
 */
export const useInventorySerialList = (
  t: Translate,
  options: {
    getInventorySerials: (params: {
      keyword?: string
      status?: string
      warehouseId?: string | number
      locationId?: string | number
      pageNo: number
      pageSize: number
    }) => Promise<PageResponse<InventorySerial>>
    issueInventorySerial: (id: string | number) => Promise<unknown>
    scrapInventorySerial: (id: string | number) => Promise<unknown>
    getWarehouses: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Warehouse>>
    getLocations: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Location>>
    locationsForWarehouse: (
      warehouseId?: string | number | null,
      all?: Location[]
    ) => Location[]
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const rows = ref<InventorySerial[]>([])
  const warehouses = ref<Warehouse[]>([])
  const locations = ref<Location[]>([])

  const query = reactive<SerialQuery>({
    keyword: '',
    status: '',
    warehouseId: '',
    locationId: '',
    pageNo: 1,
    pageSize: 50
  })

  const locationsForQuery = computed(() =>
    options.locationsForWarehouse(query.warehouseId, locations.value)
  )

  const loadOptions = async () => {
    try {
      const [warehousePage, locationPage] = await Promise.all([
        options.getWarehouses({ pageNo: 1, pageSize: 500, status: 'ACTIVE' }),
        options.getLocations({ pageNo: 1, pageSize: 500, status: 'ACTIVE' })
      ])
      warehouses.value = warehousePage.records || []
      locations.value = locationPage.records || []
      return true
    } catch {
      warehouses.value = []
      locations.value = []
      options.onError?.(t('inventorySerial.message.optionsLoadFailed'))
      return false
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getInventorySerials({
        keyword: query.keyword || undefined,
        status: query.status || undefined,
        warehouseId: query.warehouseId || undefined,
        locationId: query.locationId || undefined,
        pageNo: query.pageNo,
        pageSize: query.pageSize
      })
      rows.value = page.records || []
      return true
    } catch {
      options.onError?.(t('inventorySerial.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    query.pageNo = 1
    return loadData()
  }

  const handleQueryWarehouseChange = () => {
    query.locationId = ''
  }

  const issue = async (row: InventorySerial) => {
    try {
      await options.issueInventorySerial(row.id)
      options.onSuccess?.(t('inventorySerial.message.issued'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('inventorySerial.message.issueFailed'))
      return false
    }
  }

  const scrap = async (row: InventorySerial) => {
    try {
      await options.scrapInventorySerial(row.id)
      options.onSuccess?.(t('inventorySerial.message.scrapped'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('inventorySerial.message.scrapFailed'))
      return false
    }
  }

  return {
    handleQueryWarehouseChange,
    handleSearch,
    issue,
    loadData,
    loadOptions,
    loading,
    locations,
    locationsForQuery,
    query,
    rows,
    scrap,
    warehouses
  }
}
