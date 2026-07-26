import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { BOM, RoutingOperation, WorkCenter } from '@/api/production'
import { useProductionRoutingPresentation } from './useProductionRoutingPresentation'

const t = (key: string) => key

describe('production routing presentation', () => {
  it('formats BOM labels from code/name and falls back to id', () => {
    const presentation = useProductionRoutingPresentation(t, {
      workCenters: ref([])
    })

    expect(presentation.bomLabel({
      id: '1',
      bomCode: 'BOM-1',
      productName: '成品'
    } as BOM)).toBe('BOM-1 - 成品')
    expect(presentation.bomLabel({ id: '9', bomNo: 'B9' } as BOM)).toBe('B9')
    expect(presentation.bomLabel({ id: '7' } as BOM)).toBe('BOM7')
  })

  it('resolves work-center labels from operation fields then options', () => {
    const presentation = useProductionRoutingPresentation(t, {
      workCenters: ref([
        { id: 'wc1', workCenterCode: 'WC01', workCenterName: '装配' } as WorkCenter
      ])
    })

    expect(presentation.workCenterLabel({
      workCenterCode: 'X',
      workCenterName: '机加'
    } as RoutingOperation)).toBe('X 机加')
    expect(presentation.workCenterLabel({
      workCenterId: 'wc1'
    } as RoutingOperation)).toBe('WC01 - 装配')
    expect(presentation.workCenterLabel({
      workCenterId: 'missing'
    } as RoutingOperation)).toBe('missing')
  })

  it('maps status labels and tag types', () => {
    const presentation = useProductionRoutingPresentation(t, {
      workCenters: ref([])
    })

    expect(presentation.getStatusLabel('ACTIVE')).toBe('productionRouting.status.active')
    expect(presentation.getStatusType('ACTIVE')).toBe('success')
    expect(presentation.getStatusLabel('DISABLED')).toBe('productionRouting.status.disabled')
    expect(presentation.getStatusType('DISABLED')).toBe('danger')
    expect(presentation.getStatusLabel('OTHER')).toBe('OTHER')
    expect(presentation.getStatusType('OTHER')).toBe('info')
  })
})
