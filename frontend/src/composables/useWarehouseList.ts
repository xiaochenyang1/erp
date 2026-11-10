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
  confirm: string
  selectedExportFilename: string
  batchEnable: string
  batchDisable: string
  exportSelected: string
  batchEnableTitle: string
  batchDisableTitle: string
  batchEnableConfirm: string
  batchDisableConfirm: string
  batchEnableSuccess: string
  batchDisableSuccess: string
  batchEnablePartial: string
  batchDisablePartial: string
  warehouseCode: string
  warehouseName: string
  department: string
  address: string
  manager: string
  status: string
  active: string
  inactive: string
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
    joinNames: (items: string[], locale: string) => string
    locale: Ref<string> | ComputedRef<string>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
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
  const selectedRows = ref<Warehouse[]>([])
  const detailVisible = ref(false)
  const currentRow = ref<Warehouse>()
  const stockSummary = ref<WarehouseStockSummary>()
  const deptOptions = ref<Dept[]>([])
  const userOptions = ref<User[]>([])
  const batchRunning = ref(false)

  const handleSelectionChange = (rows: Warehouse[]) => {
    selectedRows.value = rows
  }

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

  const runBatch = async (
    rows: Warehouse[],
    action: (row: Warehouse) => Promise<unknown>,
    actionTexts: {
      confirmTitle: string
      confirmText: string
      successText: (success: number) => string
      partialText: (success: number, failed: string[]) => string
    }
  ) => {
    if (rows.length === 0 || batchRunning.value) return
    await options.confirm(actionTexts.confirmText, actionTexts.confirmTitle, {
      confirmButtonText: texts.value.confirm,
      cancelButtonText: texts.value.cancel,
      type: 'warning'
    })

    batchRunning.value = true
    let success = 0
    const failed: string[] = []
    try {
      for (const row of rows) {
        try {
          await action(row)
          success += 1
        } catch {
          failed.push(row.name || row.warehouseName || row.code || row.warehouseCode || String(row.id))
        }
      }
      if (failed.length === 0) {
        options.onSuccess?.(actionTexts.successText(success))
      } else {
        options.onWarning?.(actionTexts.partialText(success, failed))
      }
      await loadData()
    } finally {
      batchRunning.value = false
    }
  }

  const handleBatchEnable = () => {
    const rows = selectedRows.value
    return runBatch(rows, (row) => options.enableWarehouse(row.id), {
      confirmTitle: texts.value.batchEnableTitle,
      confirmText: options.interpolate(texts.value.batchEnableConfirm, { count: rows.length }),
      successText: (success) => options.interpolate(texts.value.batchEnableSuccess, { count: success }),
      partialText: (success, failed) => options.interpolate(texts.value.batchEnablePartial, {
        success,
        failedCount: failed.length,
        failed: options.joinNames(failed, options.locale.value)
      })
    })
  }

  const handleBatchDisable = () => {
    const rows = selectedRows.value
    return runBatch(rows, (row) => options.deleteWarehouse(row.id), {
      confirmTitle: texts.value.batchDisableTitle,
      confirmText: options.interpolate(texts.value.batchDisableConfirm, { count: rows.length }),
      successText: (success) => options.interpolate(texts.value.batchDisableSuccess, { count: success }),
      partialText: (success, failed) => options.interpolate(texts.value.batchDisablePartial, {
        success,
        failedCount: failed.length,
        failed: options.joinNames(failed, options.locale.value)
      })
    })
  }

  const exportSelectedRowsToCsv = (
    filename: string,
    headers: string[],
    rows: Array<Array<string | number>>
  ) => {
    const escapeCell = (value: string | number) => `"${String(value ?? '').replace(/"/g, '""')}"`
    const csv = [headers, ...rows].map((row) => row.map(escapeCell).join(',')).join('\r\n')
    downloadBlob(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }), `${filename}.csv`)
  }

  const handleExportSelected = () => {
    const rows = selectedRows.value
    if (rows.length === 0) return
    const headers = [
      texts.value.warehouseCode,
      texts.value.warehouseName,
      texts.value.department,
      texts.value.address,
      texts.value.manager,
      texts.value.status
    ]
    const lines = rows.map((row) => [
      row.code || '',
      row.name || row.warehouseName || '',
      row.deptId != null ? String(row.deptId) : '',
      row.address || '',
      row.managerUserId != null ? String(row.managerUserId) : '',
      row.status === 'ACTIVE' ? texts.value.active : texts.value.inactive
    ])
    exportSelectedRowsToCsv(
      options.interpolate(texts.value.selectedExportFilename, { count: rows.length }),
      headers,
      lines
    )
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
    batchRunning,
    currentRow,
    deptOptions,
    detailVisible,
    handleBatchDisable,
    handleBatchEnable,
    handleDelete,
    handleEnable,
    handleExport,
    handleExportSelected,
    handlePageChange,
    handleReset,
    handleSearch,
    handleSelectionChange,
    handleView,
    loadData,
    loadOptions,
    loading,
    searchForm,
    selectedRows,
    stockSummary,
    tableData,
    total,
    userOptions
  }
}
