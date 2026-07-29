import { computed, reactive, ref } from 'vue'

import type { Location, LocationSaveRequest } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type WarehouseLocationFormData = {
  warehouseId: string
  locationCode: string
  locationName: string
  isDefault: boolean
  remark: string
}

export type WarehouseLocationFormOptions = {
  createLocation: (payload: LocationSaveRequest) => Promise<unknown>
  updateLocation: (
    id: string | number,
    payload: LocationSaveRequest
  ) => Promise<unknown>
  onSuccess?: Notify
  onWarning?: Notify
  onError?: Notify
  onSubmitted?: () => void | Promise<void>
}

/**
 * Warehouse location create/edit dialog state, validation and persistence.
 *
 * A location cannot be moved to another warehouse while it is being edited.
 * Consumers should disable the warehouse control when `isEditing` is true;
 * the update payload also omits `warehouseId` so the API contract enforces the
 * same invariant even if form state is changed programmatically.
 */
export const useWarehouseLocationForm = (
  t: Translate,
  options: WarehouseLocationFormOptions
) => {
  const dialogVisible = ref(false)
  const editingId = ref<string | number | null>(null)
  const saving = ref(false)

  const form = reactive<WarehouseLocationFormData>({
    warehouseId: '',
    locationCode: '',
    locationName: '',
    isDefault: false,
    remark: ''
  })

  const isEditing = computed(() => editingId.value !== null)

  const resetForm = (warehouseId: string | number | null | undefined = '') => {
    editingId.value = null
    form.warehouseId = warehouseId == null ? '' : String(warehouseId)
    form.locationCode = ''
    form.locationName = ''
    form.isDefault = false
    form.remark = ''
  }

  const openCreate = (defaultWarehouseId?: string | number | null) => {
    resetForm(defaultWarehouseId)
    dialogVisible.value = true
  }

  const openEdit = (row: Location) => {
    editingId.value = row.id
    form.warehouseId = String(row.warehouseId)
    form.locationCode = row.locationCode
    form.locationName = row.locationName
    form.isDefault = Boolean(row.isDefault)
    form.remark = row.remark || ''
    dialogVisible.value = true
  }

  const validateForm = () => {
    if (!form.warehouseId || !form.locationCode || !form.locationName) {
      options.onWarning?.(t('warehouseLocation.validation.required'))
      return false
    }
    return true
  }

  const buildCreatePayload = (): LocationSaveRequest => ({
    warehouseId: form.warehouseId,
    locationCode: form.locationCode,
    locationName: form.locationName,
    isDefault: form.isDefault,
    remark: form.remark || undefined
  })

  const buildUpdatePayload = (): LocationSaveRequest => ({
    locationCode: form.locationCode,
    locationName: form.locationName,
    isDefault: form.isDefault,
    remark: form.remark || undefined
  })

  const save = async () => {
    if (!validateForm()) return false

    saving.value = true
    try {
      if (editingId.value !== null) {
        await options.updateLocation(editingId.value, buildUpdatePayload())
        options.onSuccess?.(t('warehouseLocation.message.saved'))
      } else {
        await options.createLocation(buildCreatePayload())
        options.onSuccess?.(t('warehouseLocation.message.created'))
      }
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('warehouseLocation.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    buildCreatePayload,
    buildUpdatePayload,
    dialogVisible,
    editingId,
    form,
    isEditing,
    openCreate,
    openEdit,
    resetForm,
    save,
    saving,
    validateForm
  }
}
