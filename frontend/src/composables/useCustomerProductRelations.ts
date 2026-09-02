import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'

import type { Customer, CustomerProductRelation, Product } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: Record<string, unknown>) => Promise<unknown>

export type CustomerProductRelationTexts = {
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
  customerProductCode: string
  customerProductName: string
  deliveryPreference: string
  packagingPreference: string
  remark: string
}

const emptyForm = (): RelationFormState => ({
  id: undefined,
  productId: '',
  customerProductCode: '',
  customerProductName: '',
  deliveryPreference: '',
  packagingPreference: '',
  remark: ''
})

/**
 * 客户商品关系维护：从客户列表打开的关系抽屉，负责关系列表、商品下拉和新增/编辑/删除。
 * 列表请求带序号守卫，避免快速切换客户时旧响应覆盖新数据。
 */
export const useCustomerProductRelations = (
  texts: ComputedRef<CustomerProductRelationTexts> | Ref<CustomerProductRelationTexts>,
  options: {
    getCustomerProductRelations: (customerId: string | number) => Promise<CustomerProductRelation[]>
    saveCustomerProductRelation: (
      customerId: string | number,
      data: Omit<CustomerProductRelation, 'id' | 'customerId' | 'productCode' | 'productName' | 'status'>
    ) => Promise<CustomerProductRelation>
    deleteCustomerProductRelation: (customerId: string | number, id: string | number) => Promise<unknown>
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
  const relationRows = ref<CustomerProductRelation[]>([])
  const relationProducts = ref<Product[]>([])
  const relationOwner = ref<Customer | null>(null)
  const relationFormVisible = ref(false)
  const relationForm = reactive<RelationFormState>(emptyForm())
  let loadToken = 0

  const relationFormTitle = computed(() => (
    relationForm.id ? texts.value.editRelation : texts.value.createRelation
  ))

  const relationFormRules = computed(() => ({
    productId: [{ required: true, message: texts.value.validationSelectProduct, trigger: 'change' }]
  }))

  const productLabel = (row: CustomerProductRelation) => {
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
    const customerId = relationOwner.value?.id
    if (!customerId) {
      relationRows.value = []
      return false
    }
    const token = ++loadToken
    relationLoading.value = true
    try {
      const rows = await options.getCustomerProductRelations(customerId)
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

  const openRelations = async (row: Customer) => {
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

  const handleRelationEdit = (row: CustomerProductRelation) => {
    Object.assign(relationForm, {
      id: row.id,
      productId: String(row.productId),
      customerProductCode: row.customerProductCode || '',
      customerProductName: row.customerProductName || '',
      deliveryPreference: row.deliveryPreference || '',
      packagingPreference: row.packagingPreference || '',
      remark: row.remark || ''
    })
    relationFormVisible.value = true
  }

  const handleRelationSubmit = async () => {
    const customerId = relationOwner.value?.id
    if (!customerId) {
      return false
    }
    if (!relationForm.productId) {
      options.onError?.(texts.value.validationSelectProduct)
      return false
    }
    relationSubmitting.value = true
    try {
      await options.saveCustomerProductRelation(customerId, {
        productId: relationForm.productId,
        customerProductCode: relationForm.customerProductCode || undefined,
        customerProductName: relationForm.customerProductName || undefined,
        deliveryPreference: relationForm.deliveryPreference || undefined,
        packagingPreference: relationForm.packagingPreference || undefined,
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

  const handleRelationDelete = async (row: CustomerProductRelation) => {
    const customerId = relationOwner.value?.id
    if (!customerId) {
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
      await options.deleteCustomerProductRelation(customerId, row.id)
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
