import { describe, expect, it } from 'vitest'

import { useProductionWorkCenterPresentation } from './useProductionWorkCenterPresentation'

const t = (key: string) => key

describe('production work center presentation', () => {
  it('maps status labels and tag types', () => {
    const presentation = useProductionWorkCenterPresentation(t)
    expect(presentation.getStatusLabel('ACTIVE')).toBe('productionWorkCenter.status.active')
    expect(presentation.getStatusType('ACTIVE')).toBe('success')
    expect(presentation.getStatusLabel('DISABLED')).toBe('productionWorkCenter.status.disabled')
    expect(presentation.getStatusType('DISABLED')).toBe('danger')
    expect(presentation.getStatusLabel('OTHER')).toBe('OTHER')
    expect(presentation.getStatusType('OTHER')).toBe('info')
  })
})
