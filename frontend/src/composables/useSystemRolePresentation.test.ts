import { describe, expect, it } from 'vitest'

import type { Warehouse } from '@/api/masterdata'
import { useSystemRolePresentation } from './useSystemRolePresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

describe('system role presentation', () => {
  it('maps status labels and tag types', () => {
    const presentation = useSystemRolePresentation(t)
    expect(presentation.statusText('ACTIVE')).toBe('systemRoles.active')
    expect(presentation.statusType('ACTIVE')).toBe('success')
    expect(presentation.statusText('INACTIVE')).toBe('systemRoles.inactive')
    expect(presentation.statusType('INACTIVE')).toBe('danger')
    expect(presentation.statusText(undefined)).toBe('')
  })

  it('formats warehouse options and permission counts', () => {
    const presentation = useSystemRolePresentation(t)
    expect(presentation.permissionCount(['a', 'b'])).toBe(2)
    expect(presentation.permissionCount(undefined)).toBe(0)
    expect(presentation.warehouseOptionLabel({
      id: 'w1',
      name: '主仓',
      code: 'WH01'
    } as Warehouse)).toBe('systemRoles.warehouseOption:{"name":"主仓","code":"WH01"}')
    expect(presentation.warehouseOptionLabel({
      id: 'w2',
      warehouseName: '备用仓'
    } as Warehouse)).toBe('备用仓')
  })
})
