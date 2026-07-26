import { reactive, ref } from 'vue'

import type { PurchaseRequisition } from '@/api/purchase'
import type { Product, Supplier } from '@/api/masterdata'
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

export type PurchaseRequisitionAction = 'submit' | 'approve' | 'reject' | 'cancel' | 'convert'

export interface PurchaseRequisitionQueryForm {
  keyword: string
  status: string
  pageNo: number
  pageSize: number
}

/**
 * Query, master-data options, detail, print and lifecycle actions for requisitions.
 */
export const usePurchaseRequisitionList = (
  t: Translate,
  options: {
    getRequisitions: (params: {
      keyword?: string
      status?: string
      pageNo: number
      pageSize: number
    }) => Promise<PageResponse<PurchaseRequisition>>
    getRequisition: (id: string | number) => Promise<PurchaseRequisition>
    getProducts: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    getSuppliers: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Supplier>>
    submit: (id: string | number) => Promise<unknown>
    approve: (id: string | number) => Promise<unknown>
    reject: (id: string | number) => Promise<unknown>
    cancel: (id: string | number) => Promise<unknown>
    convert: (id: string | number) => Promise<unknown>
    printRequisition: (doc: PurchaseRequisition) => void
    decoratePrint?: (doc: PurchaseRequisition) => PurchaseRequisition
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const detailLoading = ref(false)
  const rows = ref<PurchaseRequisition[]>([])
  const total = ref(0)
  const products = ref<Product[]>([])
  const suppliers = ref<Supplier[]>([])
  const detailVisible = ref(false)
  const detail = ref<PurchaseRequisition | null>(null)

  const query = reactive<PurchaseRequisitionQueryForm>({
    keyword: '',
    status: '',
    pageNo: 1,
    pageSize: 20
  })

  const loadData = async () => {
    loading.value = true
    try {
      const page = await options.getRequisitions({
        keyword: query.keyword || undefined,
        status: query.status || undefined,
        pageNo: query.pageNo,
        pageSize: query.pageSize
      })
      rows.value = page.records || []
      total.value = Number(page.total || 0)
    } catch {
      options.onError?.(t('purchaseRequisition.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadOptions = async () => {
    try {
      const [productPage, supplierPage] = await Promise.all([
        options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
        options.getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      ])
      products.value = productPage.records || []
      suppliers.value = supplierPage.records || []
    } catch {
      options.onError?.(t('purchaseRequisition.message.optionsLoadFailed'))
    }
  }

  const handleSearch = async () => {
    query.pageNo = 1
    await loadData()
  }

  const handleReset = async () => {
    query.keyword = ''
    query.status = ''
    query.pageNo = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    query.pageNo = page
    await loadData()
  }

  const handleSizeChange = async (size?: number) => {
    if (typeof size === 'number') query.pageSize = size
    query.pageNo = 1
    await loadData()
  }

  const openDetail = async (row: PurchaseRequisition) => {
    detailVisible.value = true
    detail.value = null
    detailLoading.value = true
    try {
      if (suppliers.value.length === 0) {
        await loadOptions()
      }
      detail.value = await options.getRequisition(row.id)
      return true
    } catch {
      options.onError?.(t('purchaseRequisition.message.detailLoadFailed'))
      detailVisible.value = false
      return false
    } finally {
      detailLoading.value = false
    }
  }

  const handlePrint = async (row: PurchaseRequisition) => {
    try {
      if (products.value.length === 0 || suppliers.value.length === 0) {
        await loadOptions()
      }
      const detailData = await options.getRequisition(row.id)
      options.printRequisition(
        options.decoratePrint ? options.decoratePrint(detailData) : detailData
      )
      return true
    } catch {
      options.onError?.(t('purchaseRequisition.message.printLoadFailed'))
      return false
    }
  }

  const act = async (row: PurchaseRequisition, type: PurchaseRequisitionAction) => {
    const confirmKey: Record<PurchaseRequisitionAction, string> = {
      submit: 'purchaseRequisition.message.submitConfirm',
      approve: 'purchaseRequisition.message.approveConfirm',
      reject: 'purchaseRequisition.message.rejectConfirm',
      cancel: 'purchaseRequisition.message.cancelConfirm',
      convert: 'purchaseRequisition.message.convertConfirm'
    }
    try {
      await options.confirm(
        t(confirmKey[type], { no: row.requisitionNo }),
        t('purchaseRequisition.prompt'),
        {
          confirmButtonText: t('purchaseRequisition.confirm'),
          cancelButtonText: t('purchaseRequisition.close'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }

    const map = {
      submit: options.submit,
      approve: options.approve,
      reject: options.reject,
      cancel: options.cancel,
      convert: options.convert
    }
    try {
      await map[type](row.id)
      options.onSuccess?.(t('purchaseRequisition.message.done'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('purchaseRequisition.message.failed'))
      return false
    }
  }

  return {
    act,
    detail,
    detailLoading,
    detailVisible,
    handlePageChange,
    handlePrint,
    handleReset,
    handleSearch,
    handleSizeChange,
    loadData,
    loadOptions,
    loading,
    openDetail,
    products,
    query,
    rows,
    suppliers,
    total
  }
}
