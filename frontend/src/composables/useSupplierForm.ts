import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'

import type { Supplier, SupplierSaveRequest } from '@/api/masterdata'

type Notify = (message: string) => void
type SupplierTexts = {
  editSupplier: string
  createSupplier: string
  validationEnterCode: string
  validationCodeLength: string
  validationEnterName: string
  validationNameLength: string
  validationSettlementMethod: string
  validationMobile: string
  validationEmail: string
  updateSuccess: string
  createSuccess: string
  updateFailed: string
  createFailed: string
  [key: string]: string
}

const hasCreditPeriod = (value?: number | string | null) => Number(value) > 0

export const useSupplierForm = (
  texts: ComputedRef<SupplierTexts> | Ref<SupplierTexts>,
  options: {
    createSupplier: (payload: SupplierSaveRequest) => Promise<unknown>
    updateSupplier: (id: string | number, payload: SupplierSaveRequest) => Promise<unknown>
    onSuccess?: Notify
    onError?: Notify
    onCompleted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitting = ref(false)
  const formData = reactive<SupplierSaveRequest & { id?: string }>({
    code: '',
    name: '',
    contact: '',
    mobile: '',
    email: '',
    settlementMethod: 'BANK_TRANSFER',
    address: '',
    creditPeriod: undefined,
    status: 'ACTIVE',
    remark: ''
  })

  const dialogTitle = computed(() => (
    formData.id ? texts.value.editSupplier : texts.value.createSupplier
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
    settlementMethod: [{ required: true, message: texts.value.validationSettlementMethod, trigger: 'change' }],
    mobile: [
      { pattern: /^1[3-9]\d{9}$/, message: texts.value.validationMobile, trigger: 'blur' }
    ],
    email: [
      { type: 'email', message: texts.value.validationEmail, trigger: 'blur' }
    ]
  }))

  const handleCreate = () => {
    Object.assign(formData, {
      id: undefined,
      code: '',
      name: '',
      contact: '',
      mobile: '',
      email: '',
      settlementMethod: 'BANK_TRANSFER',
      address: '',
      creditPeriod: undefined,
      status: 'ACTIVE',
      remark: ''
    })
    dialogVisible.value = true
  }

  const handleEdit = (row: Supplier) => {
    Object.assign(formData, {
      id: row.id,
      code: row.supplierCode || row.code,
      name: row.supplierName || row.name,
      contact: row.contactName || row.contact,
      mobile: row.contactPhone || row.mobile,
      email: row.email,
      settlementMethod: row.settlementMethod || 'BANK_TRANSFER',
      address: row.address,
      creditPeriod: hasCreditPeriod(row.creditPeriod) ? Number(row.creditPeriod) : undefined,
      status: row.status,
      remark: row.remark
    })
    dialogVisible.value = true
  }

  const handleSubmit = async (values: any) => {
    submitting.value = true
    try {
      const payload = {
        supplierCode: values.code,
        supplierName: values.name,
        contactName: values.contact,
        contactPhone: values.mobile,
        email: values.email,
        settlementMethod: values.settlementMethod || 'BANK_TRANSFER',
        creditPeriod: hasCreditPeriod(values.creditPeriod) ? Number(values.creditPeriod) : undefined,
        address: values.address,
        status: values.status,
        remark: values.remark
      }

      if (formData.id) {
        await options.updateSupplier(formData.id, payload)
        options.onSuccess?.(texts.value.updateSuccess)
      } else {
        await options.createSupplier(payload)
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
