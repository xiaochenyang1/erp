import { reactive, ref, type ComputedRef, type Ref } from 'vue'

import type {
  Product,
  ProductQuery,
  ProductStockSummary
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
type ProductTexts = {
  confirm: string
  cancel: string
  loadFailed: string
  loadDetailFailed: string
  confirmTitle: string
  confirmDelete: string
  confirmEnable: string
  deleteSuccess: string
  deleteFailed: string
  enableSuccess: string
  enableFailed: string
  exportSuccess: string
  exportFailed: string
  exportFilename: string
  selectedExportFilename: string
  batchEnableTitle: string
  batchDisableTitle: string
  batchEnableConfirm: string
  batchDisableConfirm: string
  batchEnableSuccess: string
  batchDisableSuccess: string
  batchEnablePartial: string
  batchDisablePartial: string
  productCode: string
  productName: string
  productCategory: string
  specification: string
  unit: string
  auxUnit: string
  conversionFactor: string
  salePrice: string
  costPrice: string
  status: string
  active: string
  inactive: string
  [key: string]: string
}

const normalizeProduct = (item: Product): Product => ({
  ...item,
  code: item.productCode || item.code,
  name: item.productName || item.name,
  specifications: item.specification || item.specifications,
  unit: item.unitName || item.unit,
  unitPrice: item.salePrice ?? item.unitPrice,
  costPrice: item.purchasePrice ?? item.costPrice
})

export const useProductList = (
  texts: ComputedRef<ProductTexts> | Ref<ProductTexts>,
  options: {
    getProducts: (params: ProductQuery) => Promise<PageResponse<Product>>
    getProduct: (id: string | number) => Promise<Product>
    getStockSummary: (id: string | number) => Promise<ProductStockSummary>
    enableProduct: (id: string | number) => Promise<unknown>
    deleteProduct: (id: string | number) => Promise<unknown>
    exportProducts: (params: ProductQuery) => Promise<Blob>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    interpolate: (template: string, params: Record<string, string | number>) => string
    joinNames: (items: string[], locale: string) => string
    formatUnit: (value?: string | null) => string
    formatCurrency: (value?: number | string | null) => string
    locale: Ref<string> | ComputedRef<string>
  }
) => {
  const searchForm = reactive<ProductQuery>({
    pageNo: 1,
    pageSize: 20,
    code: '',
    name: '',
    status: ''
  })
  const tableData = ref<Product[]>([])
  const total = ref(0)
  const loading = ref(false)
  const selectedRows = ref<Product[]>([])
  const detailVisible = ref(false)
  const currentRow = ref<Product>()
  const stockSummary = ref<ProductStockSummary>()
  const batchRunning = ref(false)

  const handleSelectionChange = (rows: Product[]) => {
    selectedRows.value = rows
  }

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getProducts(searchForm)
      tableData.value = (res.records || []).map(normalizeProduct)
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

  const handleView = async (row: Product) => {
    try {
      currentRow.value = normalizeProduct(await options.getProduct(row.id))
      stockSummary.value = await options.getStockSummary(row.id)
      detailVisible.value = true
    } catch {
      options.onError?.(texts.value.loadDetailFailed)
    }
  }

  const handleDelete = async (row: Product) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmDelete, {
          name: row.name || row.productName || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.confirm,
          cancelButtonText: texts.value.cancel,
          type: 'warning'
        }
      )
      await options.deleteProduct(row.id)
      options.onSuccess?.(texts.value.deleteSuccess)
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(texts.value.deleteFailed)
      }
    }
  }

  const handleEnable = async (row: Product) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmEnable, {
          name: row.name || row.productName || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.confirm,
          cancelButtonText: texts.value.cancel,
          type: 'warning'
        }
      )
      await options.enableProduct(row.id)
      options.onSuccess?.(texts.value.enableSuccess)
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(texts.value.enableFailed)
      }
    }
  }

  const runBatch = async (
    rows: Product[],
    action: (row: Product) => Promise<unknown>,
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
          failed.push(row.name || row.productName || row.code || row.productCode || String(row.id))
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
    return runBatch(rows, (row) => options.enableProduct(row.id), {
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
    return runBatch(rows, (row) => options.deleteProduct(row.id), {
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
    downloadBlob(new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' }), `${filename}.csv`)
  }

  const handleExportSelected = () => {
    const rows = selectedRows.value
    if (rows.length === 0) return
    const headers = [
      texts.value.productCode,
      texts.value.productName,
      texts.value.productCategory,
      texts.value.specification,
      texts.value.unit,
      texts.value.auxUnit,
      texts.value.conversionFactor,
      texts.value.salePrice,
      texts.value.costPrice,
      texts.value.status
    ]
    const lines = rows.map((row) => [
      row.code || '',
      row.name || '',
      row.categoryName ?? '',
      row.specifications ?? '',
      options.formatUnit(row.unit),
      options.formatUnit(row.auxUnitName),
      row.conversionFactor != null && row.auxUnitName ? row.conversionFactor : '',
      row.unitPrice != null ? options.formatCurrency(row.unitPrice) : '',
      row.costPrice != null ? options.formatCurrency(row.costPrice) : '',
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
      const blob = await options.exportProducts(searchForm)
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
    searchForm,
    selectedRows,
    stockSummary,
    tableData,
    total
  }
}
