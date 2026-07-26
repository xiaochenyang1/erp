import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Warehouse } from '@/api/masterdata'
import { useProductionOrderPresentation } from './useProductionOrderPresentation'

const messages: Record<string, string> = {
  'productionOrder.status.draft': 'Draft',
  'productionOrder.status.released': 'Released',
  'productionOrder.status.materialIssued': 'Materials issued',
  'productionOrder.status.inProgress': 'In progress',
  'productionOrder.status.completed': 'Completed',
  'productionOrder.status.cancelled': 'Cancelled',
  'productionOrder.priority.low': 'Low',
  'productionOrder.priority.normal': 'Normal',
  'productionOrder.priority.high': 'High',
  'productionOrder.priority.urgent': 'Urgent',
  'productionOrder.operationStatus.pending': 'Pending',
  'productionOrder.operationStatus.inProgress': 'In progress',
  'productionOrder.operationStatus.done': 'Done',
  'productionOrder.warehouseFallback': 'Warehouse {id}'
}

const translate = (key: string, params?: Record<string, unknown>) => {
  const template = messages[key] || key
  return Object.entries(params || {}).reduce(
    (result, [name, value]) => result.replace(`{${name}}`, String(value)),
    template
  )
}

describe('production order presentation', () => {
  it('maps production order status and priority labels with tag types', () => {
    const presentation = useProductionOrderPresentation(ref<Warehouse[]>([]), translate)

    expect(presentation.getStatusLabel('MATERIAL_ISSUED')).toBe('Materials issued')
    expect(presentation.getStatusType('COMPLETED')).toBe('success')
    expect(presentation.getPriorityLabel('URGENT')).toBe('Urgent')
    expect(presentation.getPriorityType('HIGH')).toBe('warning')
    expect(presentation.getStatusLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('maps operation statuses and warehouse labels reactively', () => {
    const warehouses = ref<Warehouse[]>([])
    const presentation = useProductionOrderPresentation(warehouses, translate)

    expect(presentation.opStatusText('PENDING')).toBe('Pending')
    expect(presentation.opStatusType('DONE')).toBe('success')
    expect(presentation.warehouseLabel('99')).toBe('Warehouse 99')
    expect(presentation.warehouseLabel()).toBe('-')

    warehouses.value = [{ id: '99', name: 'Material warehouse' } as Warehouse]
    expect(presentation.warehouseLabel('99')).toBe('Material warehouse')
  })
})
