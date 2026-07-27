import { computed, reactive, ref } from 'vue'

import type { Location, Product } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

/**
 * Inventory serial create form: serial-controlled products only.
 */
export const useInventorySerialForm = (
  t: Translate,
  options: {
    createInventorySerial: (data: {
      productId: string | number
      warehouseId?: string | number
      locationId?: string | number
      serialNo: string
      inboundBizNo?: string
      remark?: string
    }) => Promise<unknown>
    getProducts: (params: {
      pageNo: number
      pageSize: number
      status?: string
    }) => Promise<PageResponse<Product>>
    locationsForWarehouse: (
      warehouseId?: string | number | null,
      all?: Location[]
    ) => Location[]
    allLocations: { value: Location[] }
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const saving = ref(false)
  const products = ref<Product[]>([])

  const form = reactive({
    productId: '',
    warehouseId: '',
    locationId: '',
    serialNo: '',
    inboundBizNo: '',
    remark: ''
  })

  const locationsForForm = computed(() =>
    options.locationsForWarehouse(form.warehouseId, options.allLocations.value)
  )

  const resetForm = () => {
    form.productId = ''
    form.warehouseId = ''
    form.locationId = ''
    form.serialNo = ''
    form.inboundBizNo = ''
    form.remark = ''
  }

  const handleFormWarehouseChange = () => {
    form.locationId = ''
  }

  const openCreate = async () => {
    try {
      const page = await options.getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      products.value = (page.records || []).filter((product) => Boolean(product.serialControlled))
    } catch {
      products.value = []
      options.onError?.(t('inventorySerial.message.productsLoadFailed'))
    }
    resetForm()
    dialogVisible.value = true
  }

  const validateForm = () => {
    if (!form.productId || !form.serialNo) {
      options.onWarning?.(t('inventorySerial.validation.required'))
      return false
    }
    return true
  }

  const save = async () => {
    if (!validateForm()) return false
    saving.value = true
    try {
      await options.createInventorySerial({
        productId: form.productId,
        warehouseId: form.warehouseId || undefined,
        locationId: form.locationId || undefined,
        serialNo: form.serialNo,
        inboundBizNo: form.inboundBizNo || undefined,
        remark: form.remark || undefined
      })
      options.onSuccess?.(t('inventorySerial.message.created'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('inventorySerial.message.createFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    dialogVisible,
    form,
    handleFormWarehouseChange,
    locationsForForm,
    openCreate,
    products,
    resetForm,
    save,
    saving,
    validateForm
  }
}
