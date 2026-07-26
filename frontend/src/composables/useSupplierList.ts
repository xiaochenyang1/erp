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
  deleteSuccess: string
  deleteFailed: string
  enableSuccess: string
  enableFailed: string
  exportSuccess: string
  exportFailed: string
  exportFilename: string
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
    onError?: Notify
    onSuccess?: Notify
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
  const detailVisible = ref(false)
  const currentRow = ref<Supplier>()
  const payableExposure = ref<SupplierPayableExposure>()

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
    currentRow,
    detailVisible,
    handleDelete,
    handleEnable,
    handleExport,
    handlePageChange,
    handleReset,
    handleSearch,
    handleView,
    loadData,
    loading,
    payableExposure,
    searchForm,
    tableData,
    total
  }
}
