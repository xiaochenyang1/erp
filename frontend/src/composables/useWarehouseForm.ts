import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'

import type { Warehouse, WarehouseSaveRequest } from '@/api/masterdata'

type Notify = (message: string) => void
type WarehouseTexts = {
  editWarehouse: string
  createWarehouse: string
  validationEnterCode: string
  validationCodeLength: string
  validationEnterName: string
  validationNameLength: string
  validationDepartment: string
  validationManager: string
  updateSuccess: string
  createSuccess: string
  updateFailed: string
  createFailed: string
  [key: string]: string
}

export const useWarehouseForm = (
  texts: ComputedRef<WarehouseTexts> | Ref<WarehouseTexts>,
  options: {
    createWarehouse: (payload: WarehouseSaveRequest) => Promise<unknown>
    updateWarehouse: (id: string | number, payload: WarehouseSaveRequest) => Promise<unknown>
    onSuccess?: Notify
    onError?: Notify
    onCompleted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitting = ref(false)
  const formData = reactive<WarehouseSaveRequest & { id?: string }>({
    code: '',
    name: '',
    deptId: undefined as string | undefined,
    managerUserId: undefined as string | undefined,
    address: '',
    status: 'ACTIVE',
    remark: ''
  })

  const dialogTitle = computed(() => (
    formData.id ? texts.value.editWarehouse : texts.value.createWarehouse
  ))

  const formRules = computed(() => ({
    code: [
      { required: true, message: texts.value.validationEnterCode, trigger: 'blur' },
      { min: 2, max: 50, message: texts.value.validationCodeLength, trigger: 'blur' }
    ],
    name: [
      { required: true, message: texts.value.validationEnterName, trigger: 'blur' },
      { min: 2, max: 100, message: texts.value.validationNameLength, trigger: 'blur' }
    ],
    deptId: [
      { required: true, message: texts.value.validationDepartment, trigger: 'change' }
    ],
    managerUserId: [
      { required: true, message: texts.value.validationManager, trigger: 'change' }
    ]
  }))

  const handleCreate = () => {
    Object.assign(formData, {
      id: undefined,
      code: '',
      name: '',
      deptId: undefined as string | undefined,
      managerUserId: undefined as string | undefined,
      address: '',
      status: 'ACTIVE',
      remark: ''
    })
    dialogVisible.value = true
  }

  const handleEdit = (row: Warehouse) => {
    Object.assign(formData, {
      id: row.id,
      code: row.warehouseCode || row.code,
      name: row.warehouseName || row.name,
      deptId: row.deptId,
      managerUserId: row.managerUserId,
      address: row.address,
      status: row.status,
      remark: row.remark
    })
    dialogVisible.value = true
  }

  const handleSubmit = async (values: any) => {
    submitting.value = true
    try {
      const payload = {
        warehouseCode: values.code,
        warehouseName: values.name,
        deptId: values.deptId,
        managerUserId: values.managerUserId,
        address: values.address,
        status: values.status,
        remark: values.remark
      }

      if (formData.id) {
        await options.updateWarehouse(formData.id, payload)
        options.onSuccess?.(texts.value.updateSuccess)
      } else {
        await options.createWarehouse(payload)
        options.onSuccess?.(texts.value.createSuccess)
      }
      dialogVisible.value = false
      await options.onCompleted?.()
    } catch (error) {
      console.error(error)
      options.onError?.(formData.id ? texts.value.updateFailed : texts.value.createFailed)
    } finally {
      submitting.value = false
    }
  }

  return {
    dialogTitle,
    dialogVisible,
    formData,
    formRules,
    handleCreate,
    handleEdit,
    handleSubmit,
    submitting
  }
}
