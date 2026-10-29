import { reactive, ref, type ComputedRef, type Ref } from 'vue'

import type {
  Supplier,
  SupplierPayableExposure,
  SupplierQuery
} from '@/api/masterdata'
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
type SupplierTexts = {
  loadFailed: string
  loadDetailFailed: string
  confirmTitle: string
  confirmDelete: string
  confirmEnable: string
  delete: string
  enable: string
  cancel: string
  confirm: string
  deleteSuccess: string
  deleteFailed: string
  enableSuccess: string
  enableFailed: string
  exportSuccess: string
  exportFailed: string
  exportFilename: string
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
  supplierCode: string
  supplierName: string
  contact: string
  phone: string
  email: string
  settlementMethod: string
  creditPeriod: string
  status: string
  active: string
  inactive: string
  [key: string]: string
}

const normalizeSupplier = (item: Supplier): Supplier => ({
  ...item,
  code: item.supplierCode || item.code,
  name: item.supplierName || item.name,
  contact: item.contactName || item.contact,
  mobile: item.contactPhone || item.mobile
})

export const useSupplierList = (
  texts: ComputedRef<SupplierTexts> | Ref<SupplierTexts>,
  options: {
    getSuppliers: (params: SupplierQuery) => Promise<PageResponse<Supplier>>
    getSupplier: (id: string | number) => Promise<Supplier>
    getPayableExposure: (id: string | number) => Promise<SupplierPayableExposure>
    enableSupplier: (id: string | number) => Promise<unknown>
    deleteSupplier: (id: string | number) => Promise<unknown>
    exportSuppliers: (params: SupplierQuery) => Promise<Blob>
    confirm: Confirm
    interpolate: (template: string, params: Record<string, string | number>) => string
    joinNames: (items: string[], locale: string) => string
    locale: Ref<string> | ComputedRef<string>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const searchForm = reactive<SupplierQuery>({
    pageNo: 1,
    pageSize: 20,
    code: '',
    name: '',
    status: ''
  })
  const tableData = ref<Supplier[]>([])
  const total = ref(0)
  const loading = ref(false)
  const selectedRows = ref<Supplier[]>([])
  const detailVisible = ref(false)
  const currentRow = ref<Supplier>()
  const payableExposure = ref<SupplierPayableExposure>()
  const batchRunning = ref(false)

  const handleSelectionChange = (rows: Supplier[]) => {
    selectedRows.value = rows
  }

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getSuppliers(searchForm)
      tableData.value = (res.records || []).map(normalizeSupplier)
      total.value = res.total || 0
    } catch (error) {
      console.error(texts.value.loadFailed, error)
      options.onError?.(texts.value.loadFailed)
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    searchForm.pageNo = 1
    void loadData()
  }

  const handleReset = () => {
    searchForm.code = ''
    searchForm.name = ''
    searchForm.status = ''
    searchForm.pageNo = 1
    void loadData()
  }

  const handlePageChange = (page: number, size: number) => {
    searchForm.pageNo = page
    searchForm.pageSize = size
    void loadData()
  }

  const handleView = async (row: Supplier) => {
    try {
      currentRow.value = normalizeSupplier(await options.getSupplier(row.id))
      payableExposure.value = await options.getPayableExposure(row.id)
      detailVisible.value = true
    } catch {
      options.onError?.(texts.value.loadDetailFailed)
    }
  }

  const handleDelete = async (row: Supplier) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmDelete, {
          name: row.name || row.supplierName || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.delete,
          cancelButtonText: texts.value.cancel,
          type: 'warning'
        }
      )
      await options.deleteSupplier(row.id)
      options.onSuccess?.(texts.value.deleteSuccess)
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(texts.value.deleteFailed)
      }
    }
  }

  const handleEnable = async (row: Supplier) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmEnable, {
          name: row.name || row.supplierName || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.enable,
          cancelButtonText: texts.value.cancel,
          type: 'warning'
        }
      )
      await options.enableSupplier(row.id)
      options.onSuccess?.(texts.value.enableSuccess)
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(texts.value.enableFailed)
      }
    }
  }

  const runBatch = async (
    rows: Supplier[],
    action: (row: Supplier) => Promise<unknown>,
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
          failed.push(row.name || row.supplierName || row.code || row.supplierCode || String(row.id))
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
    return runBatch(rows, (row) => options.enableSupplier(row.id), {
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
    return runBatch(rows, (row) => options.deleteSupplier(row.id), {
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
      texts.value.supplierCode,
      texts.value.supplierName,
      texts.value.contact,
      texts.value.phone,
      texts.value.email,
      texts.value.settlementMethod,
      texts.value.creditPeriod,
      texts.value.status
    ]
    const lines = rows.map((row) => [
      row.code || '',
      row.name || row.supplierName || '',
      row.contact || row.contactName || '',
      row.mobile || row.contactPhone || '',
      row.email || '',
      row.settlementMethod || '',
      row.creditPeriod != null ? String(row.creditPeriod) : '',
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
      const blob = await options.exportSuppliers(searchForm)
      downloadBlob(blob, `${texts.value.exportFilename}_${Date.now()}.csv`)
      options.onSuccess?.(texts.value.exportSuccess)
    } catch {
      options.onError?.(texts.value.exportFailed)
    }
  }

  return {
    batchRunning,
    currentRow,
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
    loading,
    payableExposure,
    searchForm,
    selectedRows,
    tableData,
    total
  }
}
