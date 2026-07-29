import { reactive, ref } from 'vue'

import type { Voucher, VoucherEntry, VoucherQuery } from '@/api/finance'
import type { PageResponse } from '@/types/common'

type Translate = (key: string) => string
type Notify = (message: string) => void

/** Read-only voucher query, detail and print workflows. */
export const useVoucherList = (
  t: Translate,
  options: {
    getVouchers: (params: VoucherQuery) => Promise<PageResponse<Voucher>>
    getVoucher: (id: string | number) => Promise<Voucher>
    getVoucherEntries: (id: string | number) => Promise<VoucherEntry[]>
    printVoucher: (voucher: Voucher & {
      sourceTypeLabel: string
      statusLabel: string
      entries: VoucherEntry[]
    }) => void
    sourceTypeLabel: (sourceType?: string) => string
    statusLabel: (status: string) => string
    onError?: Notify
  }
) => {
  const loading = ref(false)
  const detailLoading = ref(false)
  const tableData = ref<Voucher[]>([])
  const detailEntries = ref<VoucherEntry[]>([])
  const currentVoucher = ref<Voucher | null>(null)
  const total = ref(0)
  const detailVisible = ref(false)
  const dateRange = ref<[string, string] | null>(null)
  const queryParams = reactive<VoucherQuery>({
    pageNo: 1,
    pageSize: 10,
    sourceType: '',
    status: ''
  })

  const buildQueryParams = (): VoucherQuery => ({
    ...queryParams,
    dateFrom: dateRange.value?.[0],
    dateTo: dateRange.value?.[1]
  })

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getVouchers(buildQueryParams())
      tableData.value = response.records || []
      total.value = response.total || 0
      return true
    } catch {
      options.onError?.(t('financeReportPages.vouchers.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    queryParams.pageNo = 1
    return loadData()
  }

  const handleReset = async () => {
    queryParams.sourceType = ''
    queryParams.status = ''
    dateRange.value = null
    return handleQuery()
  }

  const handlePageChange = async (page: number) => {
    queryParams.pageNo = page
    return loadData()
  }

  const handleSizeChange = async (size: number) => {
    queryParams.pageSize = size
    queryParams.pageNo = 1
    return loadData()
  }

  const loadVoucherDetail = (row: Voucher) => Promise.all([
    options.getVoucher(row.id),
    options.getVoucherEntries(row.id)
  ])

  const handleView = async (row: Voucher) => {
    detailVisible.value = true
    detailLoading.value = true
    currentVoucher.value = null
    detailEntries.value = []
    try {
      const [voucher, entries] = await loadVoucherDetail(row)
      currentVoucher.value = voucher
      detailEntries.value = entries
      return true
    } catch {
      options.onError?.(t('financeReportPages.vouchers.message.detailLoadFailed'))
      return false
    } finally {
      detailLoading.value = false
    }
  }

  const handlePrint = async (row: Voucher) => {
    try {
      const [voucher, entries] = await loadVoucherDetail(row)
      options.printVoucher({
        ...voucher,
        sourceTypeLabel: options.sourceTypeLabel(voucher.sourceType),
        statusLabel: options.statusLabel(voucher.status),
        entries
      })
      return true
    } catch {
      options.onError?.(t('financeReportPages.vouchers.message.printLoadFailed'))
      return false
    }
  }

  return {
    buildQueryParams,
    currentVoucher,
    dateRange,
    detailEntries,
    detailLoading,
    detailVisible,
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleView,
    loadData,
    loading,
    queryParams,
    tableData,
    total
  }
}
