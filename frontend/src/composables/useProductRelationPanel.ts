import { computed, reactive, ref } from 'vue'

import type { Product } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type ProductRelationMode = 'CUSTOMER' | 'SUPPLIER'

/**
 * Normalized shape shared by customer-product and supplier-product relations.
 * The partner-specific code/name pair is folded into ownerProductCode/Name so a
 * single panel can render both sides without duplicating the table and form.
 */
export interface ProductRelationRow {
  id: string
  productId: string
  productCode?: string
  productName?: string
  ownerProductCode?: string
  ownerProductName?: string
  deliveryPreference?: string
  packagingPreference?: string
  minPurchaseQty?: number
  leadTimeDays?: number
  defaultSupplier?: boolean
  remark?: string
  status?: string
}

export interface ProductRelationForm {
  productId: string
  ownerProductCode: string
  ownerProductName: string
  deliveryPreference: string
  packagingPreference: string
  minPurchaseQty: number
  leadTimeDays: number
  defaultSupplier: boolean
  remark: string
}

const emptyForm = (): ProductRelationForm => ({
  productId: '',
  ownerProductCode: '',
  ownerProductName: '',
  deliveryPreference: '',
  packagingPreference: '',
  minPurchaseQty: 0,
  leadTimeDays: 0,
  defaultSupplier: false,
  remark: ''
})

/**
 * Product-relation maintenance for one master record: existing relations, the
 * selectable product list, and the create/edit/delete actions. The backend save
 * endpoint upserts on (owner, product), so create mode only offers products that
 * are not mapped yet and edit mode pins the product.
 */
export const useProductRelationPanel = (
  t: Translate,
  options: {
    loadRelations: (ownerId: string) => Promise<ProductRelationRow[]>
    saveRelation: (ownerId: string, form: ProductRelationForm) => Promise<unknown>
    removeRelation: (ownerId: string, id: string) => Promise<unknown>
    loadProducts: () => Promise<Product[]>
    confirm: (message: string, title: string) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const ownerId = ref('')
  const rows = ref<ProductRelationRow[]>([])
  const products = ref<Product[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const editorVisible = ref(false)
  const editingId = ref<string | null>(null)
  const form = reactive<ProductRelationForm>(emptyForm())

  const productLabel = (product: Product) =>
    `${product.productCode || product.code || ''} ${product.productName || product.name || ''}`.trim()

  const relationProductLabel = (row: ProductRelationRow) => {
    const label = `${row.productCode || ''} ${row.productName || ''}`.trim()
    return label || t('productRelation.productUnavailable')
  }

  const mappedProductIds = computed(() => new Set(rows.value.map((row) => String(row.productId))))

  const selectableProducts = computed(() => products.value.filter((product) => (
    String(product.id) === form.productId || !mappedProductIds.value.has(String(product.id))
  )))

  const loadRows = async () => {
    if (!ownerId.value) return false
    loading.value = true
    try {
      rows.value = await options.loadRelations(ownerId.value)
      return true
    } catch {
      rows.value = []
      options.onError?.(t('productRelation.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const loadProductOptions = async () => {
    try {
      products.value = await options.loadProducts()
      return true
    } catch {
      products.value = []
      options.onError?.(t('productRelation.message.productsFailed'))
      return false
    }
  }

  const closeEditor = () => {
    editorVisible.value = false
    editingId.value = null
    Object.assign(form, emptyForm())
  }

  const open = async (id: string | number) => {
    ownerId.value = String(id ?? '')
    closeEditor()
    rows.value = []
    const [relationsLoaded, productsLoaded] = await Promise.all([loadRows(), loadProductOptions()])
    return relationsLoaded && productsLoaded
  }

  const reset = () => {
    closeEditor()
    ownerId.value = ''
    rows.value = []
    products.value = []
  }

  const openCreate = () => {
    Object.assign(form, emptyForm())
    editingId.value = null
    editorVisible.value = true
  }

  const openEdit = (row: ProductRelationRow) => {
    Object.assign(form, {
      productId: String(row.productId),
      ownerProductCode: row.ownerProductCode || '',
      ownerProductName: row.ownerProductName || '',
      deliveryPreference: row.deliveryPreference || '',
      packagingPreference: row.packagingPreference || '',
      minPurchaseQty: Number(row.minPurchaseQty || 0),
      leadTimeDays: Number(row.leadTimeDays || 0),
      defaultSupplier: Boolean(row.defaultSupplier),
      remark: row.remark || ''
    })
    editingId.value = row.id
    editorVisible.value = true
  }

  const submit = async () => {
    if (!form.productId) {
      options.onWarning?.(t('productRelation.validation.product'))
      return false
    }
    saving.value = true
    try {
      await options.saveRelation(ownerId.value, { ...form })
      options.onSuccess?.(t('productRelation.message.saved'))
      closeEditor()
      await loadRows()
      return true
    } catch {
      options.onError?.(t('productRelation.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  const remove = async (row: ProductRelationRow) => {
    try {
      await options.confirm(
        t('productRelation.message.confirmDelete', { product: relationProductLabel(row) }),
        t('productRelation.message.prompt')
      )
    } catch {
      return false
    }
    try {
      await options.removeRelation(ownerId.value, row.id)
      options.onSuccess?.(t('productRelation.message.deleted'))
      await loadRows()
      return true
    } catch {
      options.onError?.(t('productRelation.message.deleteFailed'))
      return false
    }
  }

  return {
    closeEditor,
    editingId,
    editorVisible,
    form,
    loadRows,
    loading,
    open,
    openCreate,
    openEdit,
    ownerId,
    productLabel,
    products,
    relationProductLabel,
    remove,
    reset,
    rows,
    saving,
    selectableProducts,
    submit
  }
}
