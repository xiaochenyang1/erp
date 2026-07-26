import { type Ref } from 'vue'

import type { Warehouse } from '@/api/masterdata'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary' | undefined

export const useProductionOrderPresentation = (
  warehouses: Ref<Warehouse[]>,
  t: Translate
) => {
  const getStatusLabel = (status: string) => {
    const map: Record<string, string> = {
      DRAFT: t('productionOrder.status.draft'),
      RELEASED: t('productionOrder.status.released'),
      MATERIAL_ISSUED: t('productionOrder.status.materialIssued'),
      IN_PROGRESS: t('productionOrder.status.inProgress'),
      COMPLETED: t('productionOrder.status.completed'),
      CANCELLED: t('productionOrder.status.cancelled')
    }
    return map[status] || status
  }

  const getStatusType = (status: string): TagType => {
    const map: Record<string, TagType> = {
      DRAFT: 'info',
      RELEASED: 'warning',
      MATERIAL_ISSUED: 'primary',
      IN_PROGRESS: 'primary',
      COMPLETED: 'success',
      CANCELLED: 'danger'
    }
    return map[status]
  }

  const getPriorityLabel = (priority: string) => {
    const map: Record<string, string> = {
      LOW: t('productionOrder.priority.low'),
      NORMAL: t('productionOrder.priority.normal'),
      HIGH: t('productionOrder.priority.high'),
      URGENT: t('productionOrder.priority.urgent')
    }
    return map[priority] || priority
  }

  const getPriorityType = (priority: string): TagType => {
    const map: Record<string, TagType> = {
      LOW: 'info',
      NORMAL: 'info',
      HIGH: 'warning',
      URGENT: 'danger'
    }
    return map[priority]
  }

  const warehouseLabel = (warehouseId?: string | number) => {
    if (warehouseId == null || warehouseId === '') return '-'
    const warehouse = warehouses.value.find((item) => String(item.id) === String(warehouseId))
    return warehouse?.warehouseName || warehouse?.name || t('productionOrder.warehouseFallback', { id: warehouseId })
  }

  const opStatusText = (status: string) =>
    ({
      PENDING: t('productionOrder.operationStatus.pending'),
      IN_PROGRESS: t('productionOrder.operationStatus.inProgress'),
      DONE: t('productionOrder.operationStatus.done')
    }[status] || status)

  const opStatusType = (status: string) =>
    ({ PENDING: 'info', IN_PROGRESS: 'warning', DONE: 'success' }[status] || 'info') as
      | 'info'
      | 'warning'
      | 'success'

  return {
    getPriorityLabel,
    getPriorityType,
    getStatusLabel,
    getStatusType,
    opStatusText,
    opStatusType,
    warehouseLabel
  }
}
