import { computed, reactive, ref, type ComputedRef, type Ref } from 'vue'

import type { Customer, CustomerSaveRequest } from '@/api/masterdata'

type Notify = (message: string) => void
type CustomerTexts = {
  editCustomer: string
  createCustomer: string
  validationEnterCode: string
  validationCodeLength: string
  validationEnterName: string
  validationNameLength: string
  validationSettlementMethod: string
  validationCreditLimit: string
  validationMobile: string
  validationEmail: string
  updateSuccess: string
  createSuccess: string
  updateFailed: string
  createFailed: string
  [key: string]: string
}

export const useCustomerForm = (
  texts: ComputedRef<CustomerTexts> | Ref<CustomerTexts>,
  options: {
    createCustomer: (payload: CustomerSaveRequest) => Promise<unknown>
    updateCustomer: (id: string | number, payload: CustomerSaveRequest) => Promise<unknown>
    onSuccess?: Notify
    onError?: Notify
    onCompleted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitting = ref(false)
  const formData = reactive<CustomerSaveRequest & { id?: string }>({
    code: '',
    name: '',
    type: 'COMPANY',
    contact: '',
    mobile: '',
    email: '',
    address: '',
    settlementMethod: 'BANK_TRANSFER',
    creditLimit: 0,
    creditPeriod: undefined,
    status: 'ACTIVE',
    remark: ''
  })

  const dialogTitle = computed(() => (
    formData.id ? texts.value.editCustomer : texts.value.createCustomer
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
    creditLimit: [{ required: true, message: texts.value.validationCreditLimit, trigger: 'blur' }],
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
      type: 'COMPANY',
      contact: '',
      mobile: '',
      email: '',
      address: '',
      settlementMethod: 'BANK_TRANSFER',
      creditLimit: 0,
      creditPeriod: undefined,
      status: 'ACTIVE',
      remark: ''
    })
    dialogVisible.value = true
  }

  const handleEdit = (row: Customer) => {
    Object.assign(formData, {
      id: row.id,
      code: row.customerCode || row.code,
      name: row.customerName || row.name,
      type: row.type,
      contact: row.contactName || row.contact,
      mobile: row.contactPhone || row.mobile,
      email: row.email,
      address: row.address,
      settlementMethod: row.settlementMethod || 'BANK_TRANSFER',
      creditLimit: row.creditLimit ?? 0,
      creditPeriod: row.creditPeriod ?? undefined,
      status: row.status,
      remark: row.remark
    })
    dialogVisible.value = true
  }

  const handleSubmit = async (values: any) => {
    submitting.value = true
    try {
      const payload = {
        customerCode: values.code,
        customerName: values.name,
        customerType: values.type,
        contactName: values.contact,
        contactPhone: values.mobile,
        email: values.email,
        settlementMethod: values.settlementMethod || 'BANK_TRANSFER',
        creditLimit: values.creditLimit ?? 0,
        creditPeriod: values.creditPeriod ?? undefined,
        address: values.address,
        status: values.status,
        remark: values.remark
      }

      if (formData.id) {
        await options.updateCustomer(formData.id, payload)
        options.onSuccess?.(texts.value.updateSuccess)
      } else {
        await options.createCustomer(payload)
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
