import { computed, reactive, ref, type Ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type { BOM, ProductionOrder, ProductionOrderRequest, ProductionOrderUpdateRequest } from '@/api/production'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title: string, options?: { type?: string }) => Promise<unknown>

export const useProductionOrderForm = (
  t: Translate,
  options: {
    allBomOptions: Ref<BOM[]>
    loadOrder: (orderId: string | number) => Promise<ProductionOrder>
    createOrder: (payload: ProductionOrderRequest) => Promise<unknown>
    updateOrder: (orderId: string | number, payload: ProductionOrderUpdateRequest) => Promise<unknown>
    releaseOrder: (orderId: string | number) => Promise<unknown>
    cancelOrder: (orderId: string | number) => Promise<unknown>
    confirm: Confirm
    onSuccess?: Notify
    onError?: Notify
    onCompleted?: () => void
  }
) => {
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const submitLoading = ref(false)
  const formRef = ref<FormInstance>()
  const bomOptions = ref<BOM[]>([])
  const formData = reactive({
    id: undefined as string | number | undefined,
    productId: undefined as string | number | undefined,
    bomId: undefined as string | number | undefined,
    planQuantity: 1,
    materialWarehouseId: undefined as string | number | undefined,
    finishedWarehouseId: undefined as string | number | undefined,
    planStartDate: '',
    planEndDate: '',
    priority: 'NORMAL',
    remark: ''
  })

  const formRules = computed<FormRules>(() => ({
    productId: [{ required: true, message: t('productionOrder.validation.product'), trigger: 'change' }],
    bomId: [{ required: true, message: t('productionOrder.validation.bom'), trigger: 'change' }],
    planQuantity: [{ required: true, message: t('productionOrder.validation.quantity'), trigger: 'blur' }],
    materialWarehouseId: [{ required: true, message: t('productionOrder.validation.materialWarehouse'), trigger: 'change' }],
    finishedWarehouseId: [{ required: true, message: t('productionOrder.validation.finishedWarehouse'), trigger: 'change' }],
    planStartDate: [{ required: true, message: t('productionOrder.validation.startDate'), trigger: 'change' }],
    planEndDate: [{ required: true, message: t('productionOrder.validation.endDate'), trigger: 'change' }]
  }))

  const bomLabel = (bom: BOM) => {
    const code = bom.bomCode || bom.bomNo || `BOM${bom.id}`
    return `${code} - ${t('productionOrder.baseQuantity', { quantity: bom.baseQty })} - ${bom.status}`
  }

  const getProgressColor = (rate: number) => {
    if (rate < 0.3) return '#909399'
    if (rate < 0.7) return '#e6a23c'
    if (rate < 1) return '#409eff'
    return '#67c23a'
  }

  const resetFormData = () => {
    Object.assign(formData, {
      id: undefined,
      productId: undefined,
      bomId: undefined,
      planQuantity: 1,
      materialWarehouseId: undefined,
      finishedWarehouseId: undefined,
      planStartDate: '',
      planEndDate: '',
      priority: 'NORMAL',
      remark: ''
    })
    bomOptions.value = options.allBomOptions.value
  }

  const handleAdd = () => {
    resetFormData()
    dialogTitle.value = t('productionOrder.dialog.create')
    dialogVisible.value = true
  }

  const handleEdit = async (row: ProductionOrder) => {
    try {
      const order = await options.loadOrder(row.id)
      Object.assign(formData, {
        id: order.id,
        productId: order.productId,
        bomId: order.bomId,
        planQuantity: order.planQuantity,
        materialWarehouseId: order.materialWarehouseId,
        finishedWarehouseId: order.finishedWarehouseId,
        planStartDate: order.planStartDate,
        planEndDate: order.planEndDate,
        priority: order.priority,
        remark: order.remark || ''
      })
      bomOptions.value = options.allBomOptions.value.filter(
        (bom) => String(bom.productId) === String(order.productId)
      )
      dialogTitle.value = t('productionOrder.dialog.edit')
      dialogVisible.value = true
    } catch {
      options.onError?.(t('productionOrder.message.orderLoadFailed'))
    }
  }

  const handleProductChange = (productId: string | number) => {
    formData.bomId = undefined
    bomOptions.value = options.allBomOptions.value.filter(
      (bom) => String(bom.productId) === String(productId)
    )
  }

  const handleSubmit = async () => {
    if (!formRef.value) return

    await formRef.value.validate(async (valid) => {
      if (!valid) return

      submitLoading.value = true
      try {
        if (formData.id) {
          await options.updateOrder(formData.id, formData)
          options.onSuccess?.(t('productionOrder.message.updated'))
        } else {
          await options.createOrder(formData)
          options.onSuccess?.(t('productionOrder.message.created'))
        }
        dialogVisible.value = false
        options.onCompleted?.()
      } catch {
        options.onError?.(
          t(formData.id ? 'productionOrder.message.updateFailed' : 'productionOrder.message.createFailed')
        )
      } finally {
        submitLoading.value = false
      }
    })
  }

  const handleDialogClose = () => {
    formRef.value?.resetFields()
    resetFormData()
  }

  const handleRelease = async (row: ProductionOrder) => {
    try {
      await options.confirm(
        t('productionOrder.message.releaseConfirm', { orderNo: row.orderNo }),
        t('productionOrder.message.prompt'),
        { type: 'warning' }
      )
      await options.releaseOrder(row.id)
      options.onSuccess?.(t('productionOrder.message.released'))
      options.onCompleted?.()
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('productionOrder.message.releaseFailed'))
      }
    }
  }

  const handleCancel = async (row: ProductionOrder) => {
    try {
      await options.confirm(
        t('productionOrder.message.cancelConfirm', { orderNo: row.orderNo }),
        t('productionOrder.message.prompt'),
        { type: 'warning' }
      )
      await options.cancelOrder(row.id)
      options.onSuccess?.(t('productionOrder.message.cancelled'))
      options.onCompleted?.()
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('productionOrder.message.cancelFailed'))
      }
    }
  }

  return {
    bomLabel,
    bomOptions,
    dialogTitle,
    dialogVisible,
    formData,
    formRef,
    formRules,
    getProgressColor,
    handleAdd,
    handleCancel,
    handleDialogClose,
    handleEdit,
    handleProductChange,
    handleRelease,
    handleSubmit,
    resetFormData,
    submitLoading
  }
}
