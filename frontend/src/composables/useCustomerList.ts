import { reactive, ref, type ComputedRef, type Ref } from 'vue'

import type {
  Customer,
  CustomerCreditExposure,
  CustomerQuery
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
type CustomerTexts = {
  loadFailed: string
  loadDetailFailed: string
  confirmTitle: string
  confirmDelete: string
  confirmEnable: string
  delete: string
  enable: string
  cancel?: string
  deleteSuccess: string
  deleteFailed: string
  enableSuccess: string
  enableFailed: string
  exportSuccess: string
  exportFailed: string
  exportFilename: string
  [key: string]: string | undefined
}

const normalizeCustomer = (item: Customer): Customer => ({
  ...item,
  code: item.customerCode || item.code,
  name: item.customerName || item.name,
  contact: item.contactName || item.contact,
  mobile: item.contactPhone || item.mobile
})

export const useCustomerList = (
  texts: ComputedRef<CustomerTexts> | Ref<CustomerTexts>,
  options: {
    getCustomers: (params: CustomerQuery) => Promise<PageResponse<Customer>>
    getCustomer: (id: string | number) => Promise<Customer>
    getCreditExposure: (id: string | number) => Promise<CustomerCreditExposure>
    enableCustomer: (id: string | number) => Promise<unknown>
    deleteCustomer: (id: string | number) => Promise<unknown>
    exportCustomers: (params: CustomerQuery) => Promise<Blob>
    confirm: Confirm
    cancelLabel: () => string
    interpolate: (template: string, params: Record<string, string | number>) => string
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const searchForm = reactive<CustomerQuery>({
    pageNo: 1,
    pageSize: 20,
    code: '',
    name: '',
    type: '',
    status: ''
  })
  const tableData = ref<Customer[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detailVisible = ref(false)
  const currentRow = ref<Customer>()
  const creditExposure = ref<CustomerCreditExposure>()

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getCustomers(searchForm)
      tableData.value = (res.records || []).map(normalizeCustomer)
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
    searchForm.type = ''
    searchForm.status = ''
    searchForm.pageNo = 1
    void loadData()
  }

  const handlePageChange = (page: number, size: number) => {
    searchForm.pageNo = page
    searchForm.pageSize = size
    void loadData()
  }

  const handleView = async (row: Customer) => {
    try {
      currentRow.value = normalizeCustomer(await options.getCustomer(row.id))
      creditExposure.value = await options.getCreditExposure(row.id)
      detailVisible.value = true
    } catch {
      options.onError?.(texts.value.loadDetailFailed)
    }
  }

  const handleDelete = async (row: Customer) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmDelete, {
          name: row.name || row.customerName || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.delete,
          cancelButtonText: options.cancelLabel(),
          type: 'warning'
        }
      )
      await options.deleteCustomer(row.id)
      options.onSuccess?.(texts.value.deleteSuccess)
      await loadData()
    } catch (error: any) {
      if (error !== 'cancel') {
        options.onError?.(texts.value.deleteFailed)
      }
    }
  }

  const handleEnable = async (row: Customer) => {
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmEnable, {
          name: row.name || row.customerName || row.code || row.id
        }),
        texts.value.confirmTitle,
        {
          confirmButtonText: texts.value.enable,
          cancelButtonText: options.cancelLabel(),
          type: 'warning'
        }
      )
      await options.enableCustomer(row.id)
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
      const blob = await options.exportCustomers(searchForm)
      downloadBlob(blob, `${texts.value.exportFilename}_${Date.now()}.csv`)
      options.onSuccess?.(texts.value.exportSuccess)
    } catch {
      options.onError?.(texts.value.exportFailed)
    }
  }

  return {
    creditExposure,
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
    searchForm,
    tableData,
    total
  }
}
