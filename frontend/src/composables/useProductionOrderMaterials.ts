import { reactive, ref } from 'vue'

import type { ProductionOrder, ProductionOrderMaterial } from '@/api/production'
import {
  validateProductControlLines,
  type ProductControlValidationIssue
} from '@/utils/productLines'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type MaterialControls = {
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  productCode?: string
  productName?: string
}
type HydrateMaterialControls = <T extends {
  materialProductId?: string | number
  materialId?: string | number
  productCode?: string
  productName?: string
  materialCode?: string
  materialName?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
}>(materials: T[]) => Promise<(T & MaterialControls)[]>

export type IssueMaterialRow = ProductionOrderMaterial & {
  remainingQty: number
  issueQty: number
  lotNo?: string
  locationId?: string | number
  serialNos?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  productCode?: string
  productName?: string
  quantity?: number
  remark?: string
}

export type ReturnMaterialRow = ProductionOrderMaterial & {
  returnQty: number
  lotNo?: string
  locationId?: string | number
  serialNos?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  productCode?: string
  productName?: string
  quantity?: number
  remark?: string
}

type IssuePayload = {
  issueDate?: string
  remark?: string
  lines: Array<{
    orderMaterialId?: string | number
    issueQty: number
    lotNo?: string
    locationId?: string | number
    serialNos?: string
    remark?: string
  }>
}

type ReturnPayload = {
  returnDate?: string
  remark?: string
  lines: Array<{
    orderMaterialId?: string | number
    returnQty: number
    lotNo?: string
    locationId?: string | number
    serialNos?: string
    remark?: string
  }>
}

export const useProductionOrderMaterials = (
  t: Translate,
  options: {
    loadOrder: (orderId: string | number) => Promise<ProductionOrder>
    issueOrder: (orderId: string | number, payload: IssuePayload) => Promise<unknown>
    returnMaterials: (orderId: string | number, payload: ReturnPayload) => Promise<unknown>
    hydrateMaterialControls: HydrateMaterialControls
    loadMaterialLocations: (warehouseId?: string | number) => void | Promise<void>
    formatBusinessDate: () => string
    onSuccess?: Notify
    onError?: Notify
    onWarning?: Notify
    onCompleted?: () => void
  }
) => {
  const issueDialogVisible = ref(false)
  const returnDialogVisible = ref(false)
  const submitLoading = ref(false)
  const issueForm = reactive({
    orderId: '' as string | number,
    issueDate: '',
    remark: '',
    materials: [] as IssueMaterialRow[]
  })
  const returnForm = reactive({
    orderId: '' as string | number,
    returnDate: '',
    remark: '',
    materials: [] as ReturnMaterialRow[]
  })

  const canReturnMaterials = (row: ProductionOrder) => {
    return ['MATERIAL_ISSUED', 'IN_PROGRESS'].includes(row.status)
  }

  const warnControlIssue = (issue: ProductControlValidationIssue) => {
    const product = issue.productCode || issue.productName || String(issue.productId)
    options.onWarning?.(t(`productionOrder.validation.${issue.messageKey}`, {
      line: issue.index + 1,
      product,
      expected: issue.expectedSerialCount,
      actual: issue.actualSerialCount
    }))
  }

  const handleIssue = async (row: ProductionOrder) => {
    try {
      const order = await options.loadOrder(row.id)
      const issuableMaterials = await options.hydrateMaterialControls(
        (order.materials || [])
          .map((material) => {
            const remainingQty = Math.max(
              Number(material.requiredQuantity || 0) - Number(material.issuedQuantity || 0),
              0
            )
            return {
              ...material,
              remainingQty,
              issueQty: remainingQty,
              lotNo: '',
              locationId: undefined,
              serialNos: '',
              remark: '',
              productCode: material.materialCode,
              productName: material.materialName
            } as IssueMaterialRow
          })
          .filter((material) => Number(material.remainingQty || 0) > 0)
      )

      if (issuableMaterials.length === 0) {
        options.onWarning?.(t('productionOrder.message.noIssuableMaterials'))
        return
      }

      issueForm.orderId = order.id
      issueForm.issueDate = order.actualStartDate || order.planStartDate || options.formatBusinessDate()
      issueForm.remark = t('productionOrder.issueRemark')
      issueForm.materials = issuableMaterials
      issueDialogVisible.value = true
      void options.loadMaterialLocations(order.materialWarehouseId || order.warehouseId)
    } catch {
      options.onError?.(t('productionOrder.message.issuableLoadFailed'))
    }
  }

  const handleConfirmIssueMaterials = async () => {
    const selectedMaterials = issueForm.materials.filter((material) => Number(material.issueQty || 0) > 0)
    if (selectedMaterials.length === 0) {
      options.onWarning?.(t('productionOrder.validation.issueQuantity'))
      return
    }

    const controlIssues = validateProductControlLines(selectedMaterials.map((material) => ({
      productId: material.materialProductId ?? material.materialId,
      productCode: material.productCode || material.materialCode,
      productName: material.productName || material.materialName,
      quantity: material.issueQty,
      lotNo: material.lotNo,
      serialNos: material.serialNos,
      lotControlled: material.lotControlled,
      shelfLifeControlled: material.shelfLifeControlled,
      serialControlled: material.serialControlled
    })))
    if (controlIssues.length > 0) {
      warnControlIssue(controlIssues[0])
      return
    }

    const lines = selectedMaterials.map((material) => ({
      orderMaterialId: material.id,
      issueQty: material.issueQty,
      lotNo: material.lotNo || undefined,
      locationId: material.locationId || undefined,
      serialNos: material.serialNos || undefined,
      remark: material.remark || undefined
    }))

    submitLoading.value = true
    try {
      await options.issueOrder(issueForm.orderId, {
        issueDate: issueForm.issueDate,
        remark: issueForm.remark || undefined,
        lines
      })
      options.onSuccess?.(t('productionOrder.message.issued'))
      issueDialogVisible.value = false
      options.onCompleted?.()
    } catch {
      options.onError?.(t('productionOrder.message.issueFailed'))
    } finally {
      submitLoading.value = false
    }
  }

  const handleReturnMaterials = async (row: ProductionOrder) => {
    try {
      const order = await options.loadOrder(row.id)
      const returnableMaterials = await options.hydrateMaterialControls(
        (order.materials || [])
          .filter((material) => Number(material.issuedQuantity || 0) > 0)
          .map((material) => ({
            ...material,
            returnQty: 0,
            lotNo: '',
            locationId: undefined,
            serialNos: '',
            remark: '',
            productCode: material.materialCode,
            productName: material.materialName
          } as ReturnMaterialRow))
      )

      if (returnableMaterials.length === 0) {
        options.onWarning?.(t('productionOrder.message.noReturnableMaterials'))
        return
      }

      returnForm.orderId = order.id
      returnForm.returnDate = order.actualStartDate || order.planStartDate || options.formatBusinessDate()
      returnForm.remark = ''
      returnForm.materials = returnableMaterials
      returnDialogVisible.value = true
      void options.loadMaterialLocations(order.materialWarehouseId || order.warehouseId)
    } catch {
      options.onError?.(t('productionOrder.message.returnableLoadFailed'))
    }
  }

  const handleConfirmReturnMaterials = async () => {
    const selectedMaterials = returnForm.materials.filter((material) => Number(material.returnQty || 0) > 0)
    if (selectedMaterials.length === 0) {
      options.onWarning?.(t('productionOrder.validation.returnQuantity'))
      return
    }

    const controlIssues = validateProductControlLines(selectedMaterials.map((material) => ({
      productId: material.materialProductId ?? material.materialId,
      productCode: material.productCode || material.materialCode,
      productName: material.productName || material.materialName,
      quantity: material.returnQty,
      lotNo: material.lotNo,
      serialNos: material.serialNos,
      lotControlled: material.lotControlled,
      shelfLifeControlled: material.shelfLifeControlled,
      serialControlled: material.serialControlled
    })))
    if (controlIssues.length > 0) {
      warnControlIssue(controlIssues[0])
      return
    }

    const lines = selectedMaterials.map((material) => ({
      orderMaterialId: material.id,
      returnQty: material.returnQty,
      lotNo: material.lotNo || undefined,
      locationId: material.locationId || undefined,
      serialNos: material.serialNos || undefined,
      remark: material.remark || undefined
    }))

    submitLoading.value = true
    try {
      await options.returnMaterials(returnForm.orderId, {
        returnDate: returnForm.returnDate,
        remark: returnForm.remark,
        lines
      })
      options.onSuccess?.(t('productionOrder.message.returned'))
      returnDialogVisible.value = false
      options.onCompleted?.()
    } catch {
      options.onError?.(t('productionOrder.message.returnFailed'))
    } finally {
      submitLoading.value = false
    }
  }

  return {
    canReturnMaterials,
    handleConfirmIssueMaterials,
    handleConfirmReturnMaterials,
    handleIssue,
    handleReturnMaterials,
    issueDialogVisible,
    issueForm,
    returnDialogVisible,
    returnForm,
    submitLoading
  }
}
