import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'

import type { Product, Supplier, SupplierProductRelation } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: Record<string, unknown>) => Promise<unknown>

export type SupplierProductRelationTexts = {
  createRelation: string
  editRelation: string
  relationLoadFailed: string
  relationOptionsLoadFailed: string
  relationSaveSuccess: string
  relationSaveFailed: string
  relationDeleteSuccess: string
  relationDeleteFailed: string
  confirmTitle: string
  confirmDeleteRelation: string
  validationSelectProduct: string
  [key: string]: string
}

type RelationFormState = {
  id?: string
  productId: string
  supplierProductCode: string
  supplierProductName: string
  minPurchaseQty: number
  leadTimeDays: number
  defaultSupplier: boolean
  remark: string
}

const emptyForm = (): RelationFormState => ({
  id: undefined,
  productId: '',
  supplierProductCode: '',
  supplierProductName: '',
  minPurchaseQty: 0,
  leadTimeDays: 0,
  defaultSupplier: false,
  remark: ''
})

/**
 * 供应商商品关系维护：最小采购量与交期在采购订单提交时被校验，默认供应商每个商品只允许一个。
 * 列表请求带序号守卫，避免快速切换供应商时旧响应覆盖新数据。
 */
export const useSupplierProductRelations = (
  texts: ComputedRef<SupplierProductRelationTexts> | Ref<SupplierProductRelationTexts>,
  options: {
    getSupplierProductRelations: (supplierId: string | number) => Promise<SupplierProductRelation[]>
    saveSupplierProductRelation: (
      supplierId: string | number,
      data: Omit<SupplierProductRelation, 'id' | 'supplierId' | 'productCode' | 'productName' | 'status'>
    ) => Promise<SupplierProductRelation>
    deleteSupplierProductRelation: (supplierId: string | number, id: string | number) => Promise<unknown>
    getProducts: (params: { pageNo: number; pageSize: number; status?: string }) => Promise<PageResponse<Product>>
    confirm: Confirm
    interpolate: (template: string, params: Record<string, string | number>) => string
    cancelLabel?: () => string
    onSuccess?: Notify
    onError?: Notify
  }
) => {
  const relationVisible = ref(false)
  const relationLoading = ref(false)
  const relationSubmitting = ref(false)
  const relationRows = ref<SupplierProductRelation[]>([])
  const relationProducts = ref<Product[]>([])
  const relationOwner = ref<Supplier | null>(null)
  const relationFormVisible = ref(false)
  const relationForm = reactive<RelationFormState>(emptyForm())
  let loadToken = 0

  const relationFormTitle = computed(() => (
    relationForm.id ? texts.value.editRelation : texts.value.createRelation
  ))

  const relationFormRules = computed(() => ({
    productId: [{ required: true, message: texts.value.validationSelectProduct, trigger: 'change' }]
  }))

  const productLabel = (row: SupplierProductRelation) => {
    if (row.productCode && row.productName) {
      return `${row.productCode} / ${row.productName}`
    }
    return row.productCode || row.productName || String(row.productId)
  }

  const loadRelationProducts = async () => {
    try {
      const page = await options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      relationProducts.value = page.records || []
      return true
    } catch {
      relationProducts.value = []
      options.onError?.(texts.value.relationOptionsLoadFailed)
      return false
    }
  }

  const loadRelations = async () => {
    const supplierId = relationOwner.value?.id
    if (!supplierId) {
      relationRows.value = []
      return false
    }
    const token = ++loadToken
    relationLoading.value = true
    try {
      const rows = await options.getSupplierProductRelations(supplierId)
      if (token !== loadToken) {
        return false
      }
      relationRows.value = rows || []
      return true
    } catch {
      if (token === loadToken) {
        relationRows.value = []
        options.onError?.(texts.value.relationLoadFailed)
      }
      return false
    } finally {
      if (token === loadToken) {
        relationLoading.value = false
      }
    }
  }

  const openRelations = async (row: Supplier) => {
    relationOwner.value = row
    relationVisible.value = true
    relationFormVisible.value = false
    Object.assign(relationForm, emptyForm())
    const [, loaded] = await Promise.all([loadRelationProducts(), loadRelations()])
    return loaded
  }

  const closeRelations = () => {
    relationVisible.value = false
    relationFormVisible.value = false
    relationOwner.value = null
    relationRows.value = []
    Object.assign(relationForm, emptyForm())
  }

  const handleRelationCreate = () => {
    Object.assign(relationForm, emptyForm())
    relationFormVisible.value = true
  }

  const handleRelationEdit = (row: SupplierProductRelation) => {
    Object.assign(relationForm, {
      id: row.id,
      productId: String(row.productId),
      supplierProductCode: row.supplierProductCode || '',
      supplierProductName: row.supplierProductName || '',
      minPurchaseQty: Number(row.minPurchaseQty || 0),
      leadTimeDays: Number(row.leadTimeDays || 0),
      defaultSupplier: Boolean(row.defaultSupplier),
      remark: row.remark || ''
    })
    relationFormVisible.value = true
  }

  const handleRelationSubmit = async () => {
    const supplierId = relationOwner.value?.id
    if (!supplierId) {
      return false
    }
    if (!relationForm.productId) {
      options.onError?.(texts.value.validationSelectProduct)
      return false
    }
    relationSubmitting.value = true
    try {
      await options.saveSupplierProductRelation(supplierId, {
        productId: relationForm.productId,
        supplierProductCode: relationForm.supplierProductCode || undefined,
        supplierProductName: relationForm.supplierProductName || undefined,
        minPurchaseQty: Number(relationForm.minPurchaseQty || 0),
        leadTimeDays: Number(relationForm.leadTimeDays || 0),
        defaultSupplier: Boolean(relationForm.defaultSupplier),
        remark: relationForm.remark || undefined
      })
      options.onSuccess?.(texts.value.relationSaveSuccess)
      relationFormVisible.value = false
      Object.assign(relationForm, emptyForm())
      await loadRelations()
      return true
    } catch {
      options.onError?.(texts.value.relationSaveFailed)
      return false
    } finally {
      relationSubmitting.value = false
    }
  }

  const handleRelationDelete = async (row: SupplierProductRelation) => {
    const supplierId = relationOwner.value?.id
    if (!supplierId) {
      return false
    }
    try {
      await options.confirm(
        options.interpolate(texts.value.confirmDeleteRelation, { product: productLabel(row) }),
        texts.value.confirmTitle,
        { type: 'warning', cancelButtonText: options.cancelLabel?.() }
      )
    } catch {
      return false
    }
    try {
      await options.deleteSupplierProductRelation(supplierId, row.id)
      options.onSuccess?.(texts.value.relationDeleteSuccess)
      await loadRelations()
      return true
    } catch {
      options.onError?.(texts.value.relationDeleteFailed)
      return false
    }
  }

  return {
    closeRelations,
    handleRelationCreate,
    handleRelationDelete,
    handleRelationEdit,
    handleRelationSubmit,
    loadRelations,
    openRelations,
    productLabel,
    relationForm,
    relationFormRules,
    relationFormTitle,
    relationFormVisible,
    relationLoading,
    relationOwner,
    relationProducts,
    relationRows,
    relationSubmitting,
    relationVisible
  }
}
