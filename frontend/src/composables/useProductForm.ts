import { computed, reactive, ref, watch, type ComputedRef, type Ref } from 'vue'

import type { Product, ProductSaveRequest } from '@/api/masterdata'

type Notify = (message: string) => void
type ProductTexts = {
  editProduct: string
  createProduct: string
  validationEnterCode: string
  validationCodeLength: string
  validationEnterName: string
  validationNameLength: string
  validationProductType: string
  validationCategory: string
  validationUnit: string
  validationConversionFactor: string
  validationSalePrice: string
  validationCostPrice: string
  validationTaxRate: string
  updateSuccess: string
  createSuccess: string
  updateFailed: string
  createFailed: string
  [key: string]: string
}

export const useProductForm = (
  texts: ComputedRef<ProductTexts> | Ref<ProductTexts>,
  options: {
    createProduct: (payload: ProductSaveRequest) => Promise<unknown>
    updateProduct: (id: string | number, payload: ProductSaveRequest) => Promise<unknown>
    onSuccess?: Notify
    onError?: Notify
    onCompleted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const submitting = ref(false)
  const formData = reactive<ProductSaveRequest & { id?: string }>({
    code: '',
    name: '',
    productType: 'PHYSICAL',
    categoryName: '',
    specifications: '',
    unit: '',
    auxUnitName: '',
    conversionFactor: undefined,
    unitPrice: undefined,
    costPrice: undefined,
    taxRate: 13,
    barcode: '',
    status: 'ACTIVE',
    inspectionRequired: false,
    serialControlled: false,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: ''
  })

  const dialogTitle = computed(() => (
    formData.id ? texts.value.editProduct : texts.value.createProduct
  ))

  watch(() => formData.auxUnitName, (value) => {
    if (!value) formData.conversionFactor = undefined
  })

  const formRules = computed(() => ({
    code: [
      { required: true, message: texts.value.validationEnterCode, trigger: 'blur' },
      { min: 2, max: 50, message: texts.value.validationCodeLength, trigger: 'blur' }
    ],
    name: [
      { required: true, message: texts.value.validationEnterName, trigger: 'blur' },
      { min: 2, max: 100, message: texts.value.validationNameLength, trigger: 'blur' }
    ],
    productType: [{ required: true, message: texts.value.validationProductType, trigger: 'change' }],
    categoryName: [{ required: true, message: texts.value.validationCategory, trigger: 'blur' }],
    unit: [{ required: true, message: texts.value.validationUnit, trigger: 'change' }],
    conversionFactor: [{
      validator: (_rule: any, value: number | undefined | null, callback: (error?: Error) => void) => {
        if (!formData.auxUnitName) {
          callback()
          return
        }
        if (value == null || !Number.isFinite(Number(value)) || Number(value) <= 0) {
          callback(new Error(texts.value.validationConversionFactor))
          return
        }
        callback()
      },
      trigger: 'blur'
    }],
    unitPrice: [{ required: true, message: texts.value.validationSalePrice, trigger: 'blur' }],
    costPrice: [{ required: true, message: texts.value.validationCostPrice, trigger: 'blur' }],
    taxRate: [{ required: true, message: texts.value.validationTaxRate, trigger: 'blur' }]
  }))

  const handleCreate = () => {
    Object.assign(formData, {
      id: undefined,
      code: '',
      name: '',
      productType: 'PHYSICAL',
      categoryName: '',
      specifications: '',
      unit: '',
      auxUnitName: '',
      conversionFactor: undefined,
      unitPrice: undefined,
      costPrice: undefined,
      taxRate: 13,
      barcode: '',
      status: 'ACTIVE',
      inspectionRequired: false,
      serialControlled: false,
      lotControlled: false,
      shelfLifeControlled: false,
      remark: ''
    })
    dialogVisible.value = true
  }

  const handleEdit = (row: Product) => {
    Object.assign(formData, {
      id: String(row.id),
      code: row.code || row.productCode || '',
      name: row.name || row.productName || '',
      productType: row.productType || 'PHYSICAL',
      categoryName: row.categoryName || '',
      specifications: row.specifications || row.specification || '',
      unit: row.unit || row.unitName || '',
      auxUnitName: row.auxUnitName || '',
      conversionFactor: row.conversionFactor,
      unitPrice: row.unitPrice ?? row.salePrice,
      costPrice: row.costPrice ?? row.purchasePrice,
      taxRate: row.taxRate ?? 13,
      barcode: row.barcode || '',
      status: row.status || 'ACTIVE',
      inspectionRequired: !!row.inspectionRequired,
      serialControlled: !!row.serialControlled,
      lotControlled: !!row.lotControlled,
      shelfLifeControlled: !!row.shelfLifeControlled,
      remark: row.remark || ''
    })
    dialogVisible.value = true
  }

  const handleSubmit = async (values: any) => {
    submitting.value = true
    try {
      const payload = {
        ...formData,
        ...values
      }
      if (formData.id) {
        await options.updateProduct(formData.id, payload)
        options.onSuccess?.(texts.value.updateSuccess)
      } else {
        await options.createProduct(payload)
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
