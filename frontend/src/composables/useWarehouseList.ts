import { reactive, ref, type ComputedRef, type Ref } from 'vue'

import type {
  Warehouse,
  WarehouseQuery,
  WarehouseStockSummary
} from '@/api/masterdata'
import type { Dept, User } from '@/api/system'
import type { PageResponse } from '@/types/common'
import { downloadBlob } from '@/utils/download'

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
type WarehouseTexts = {
  loadFailed: string
  loadOptionsFailed: string
  loadDetailFailed: string
  confirmTitle: string
  confirmDelete: string
  confirmEnable: string
  delete: string
  enable: string
  cancel: string
  deleteSuccess: string
  deleteFailed: string
  enableSuccess: string
  enableFailed: string
  exportSuccess: string
  exportFailed: string
  exportFilename: string
  [key: string]: string
}

const normalizeWarehouse = (item: Warehouse): Warehouse => ({
  ...item,
  code: item.warehouseCode || item.code,
  name: item.warehouseName || item.name
})

export const useWarehouseList = (
  texts: ComputedRef<WarehouseTexts> | Ref<WarehouseTexts>,
  options: {
    getWarehouses: (params: WarehouseQuery) => Promise<PageResponse<Warehouse>>
    getWarehouse: (id: string | number) => Promise<Warehouse>
    getStockSummary: (id: string | number) => Promise<WarehouseStockSummary>
    enableWarehouse: (id: string | number) => Promise<unknown>
    deleteWarehouse: (id: string | number) => Promise<unknown>
    exportWarehouses: (params: WarehouseQuery) => Promise<Blob>
    getDeptTree: () => Promise<Dept[]>
    getUsers: (params: { pageNo: number; pageSize: number; status: string }) => Promise<PageResponse<User>>
    confirm: Confirm
    interpolate: (template: string, params: Record<string, string | number>) => string
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const searchForm = reactive<WarehouseQuery>({
    pageNo: 1,
    pageSize: 20,
    code: '',
    name: '',
    deptId: undefined,
    managerUserId: undefined,
    status: ''
  })
  const tableData = ref<Warehouse[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detailVisible = ref(false)
  const currentRow = ref<Warehouse>()
  const stockSummary = ref<WarehouseStockSummary>()
  const deptOptions = ref<Dept[]>([])
  const userOptions = ref<User[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getWarehouses(searchForm)
      tableData.value = (res.records || []).map(normalizeWarehouse)
      total.value = res.total || 0
    } catch (error) {
      console.error(texts.value.loadFailed, error)
      options.onError?.(texts.value.loadFailed)
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    try {
      const [depts, users] = await Promise.all([
        options.getDeptTree(),
        options.getUsers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      ])
      deptOptions.value = depts || []
      userOptions.value = users.records || []
    } catch {
      options.onError?.(texts.value.loadOptionsFailed)
    }
  }

  const handleSearch = () => {
    searchForm.pageNo = 1
    void loadData()
  }

  const handleReset = () => {
    searchForm.code = ''
    searchForm.name = ''
    searchForm.deptId = undefined
    searchForm.managerUserId = undefined
    searchForm.status = ''
    searchForm.pageNo = 1
    void loadData()
  }

  const handlePageChange = (page: number, size: number) => {
    searchForm.pageNo = page
    searchForm.pageSize = size
    void loadData()
  }

  const handleView = async (row: Warehouse) => {
    try {
      currentRow.value = normalizeWarehouse(await options.getWarehouse(row.id))
      stockSummary.value = await options.getStockSummary(row.id)
      detailVisible.value = true
    } catch {
      options.onError?.(texts.value.loadDetailFailed)
    }
  }

  const handleDelete = async (row: Warehouse) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmDelete, {
          name: row.warehouseName || row.name || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.delete,
          cancelButtonText: texts.value.cancel,
          type: 'warning'
        }
      )
      await options.deleteWarehouse(row.id)
      options.onSuccess?.(texts.value.deleteSuccess)
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(texts.value.deleteFailed)
      }
    }
  }

  const handleEnable = async (row: Warehouse) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmEnable, {
          name: row.warehouseName || row.name || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.enable,
          cancelButtonText: texts.value.cancel,
          type: 'warning'
        }
      )
      await options.enableWarehouse(row.id)
      options.onSuccess?.(texts.value.enableSuccess)
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(texts.value.enableFailed)
      }
    }
  }

  const handleExport = async () => {
    try {
      const blob = await options.exportWarehouses(searchForm)
      downloadBlob(blob, `${texts.value.exportFilename}_${Date.now()}.csv`)
      options.onSuccess?.(texts.value.exportSuccess)
    } catch {
      options.onError?.(texts.value.exportFailed)
    }
  }

  return {
    currentRow,
    deptOptions,
    detailVisible,
    handleDelete,
    handleEnable,
    handleExport,
    handlePageChange,
    handleReset,
    handleSearch,
    handleView,
    loadData,
    loadOptions,
    loading,
    searchForm,
    stockSummary,
    tableData,
    total,
    userOptions
  }
}
