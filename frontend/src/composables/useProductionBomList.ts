import { reactive, ref } from 'vue'

import type { BOM, BOMQuery } from '@/api/production'
import type { Product } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface ProductionBomQueryForm {
  bomCode: string
  productId: string | number | undefined
  status: string
}

/**
 * Query, pagination, product options, detail and print for the production BOM page.
 * Print decoration is left to the page so it can reuse presentation labels.
 */
export const useProductionBomList = (
  t: Translate,
  options: {
    getBOMs: (params: BOMQuery) => Promise<PageResponse<BOM>>
    getBOM: (id: string | number) => Promise<BOM>
    getProducts: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    printBOM: (doc: BOM) => void
    decoratePrint?: (doc: BOM) => BOM
    onError?: Notify
  }
) => {
  const queryForm = reactive<ProductionBomQueryForm>({
    bomCode: '',
    productId: undefined,
    status: ''
  })

  const loading = ref(false)
  const tableData = ref<BOM[]>([])
  const productOptions = ref<Product[]>([])

  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const viewDialogVisible = ref(false)
  const viewData = ref<BOM>({} as BOM)

  const loadProducts = async () => {
    try {
      // Keep the shared option-page contract used by inventory/production pages.
      const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
      const res = await options.getProducts(optionPageQuery)
      productOptions.value = res.records || []
    } catch {
      options.onError?.(t('productionBom.message.productsLoadFailed'))
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const params: BOMQuery = {
        bomCode: queryForm.bomCode || undefined,
        productId: queryForm.productId,
        status: queryForm.status || undefined,
        pageNo: pagination.page,
        pageSize: pagination.size
      }
      const res = await options.getBOMs(params)
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } catch {
      options.onError?.(t('productionBom.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    await loadData()
  }

  const handlePageChange = async (page: number) => {
    pagination.page = page
    await loadData()
  }

  const handleSizeChange = async (size: number) => {
    pagination.size = size
    pagination.page = 1
    await loadData()
  }

  const handleReset = async () => {
    queryForm.bomCode = ''
    queryForm.productId = undefined
    queryForm.status = ''
    pagination.page = 1
    await loadData()
  }

  const handleView = async (row: BOM) => {
    try {
      viewData.value = await options.getBOM(row.id)
      viewDialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('productionBom.message.detailLoadFailed'))
      return false
    }
  }

  const handlePrint = async (row: BOM) => {
    try {
      const detail = await options.getBOM(row.id)
      options.printBOM(options.decoratePrint ? options.decoratePrint(detail) : detail)
      return true
    } catch {
      options.onError?.(t('productionBom.message.printLoadFailed'))
      return false
    }
  }

  return {
    handlePageChange,
    handlePrint,
    handleQuery,
    handleReset,
    handleSizeChange,
    handleView,
    loadData,
    loadProducts,
    loading,
    pagination,
    productOptions,
    queryForm,
    tableData,
    viewData,
    viewDialogVisible
  }
}
