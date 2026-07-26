import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { Dept, User } from '@/api/system'
import type { Warehouse } from '@/api/masterdata'
import { useWarehousePresentation } from './useWarehousePresentation'

describe('warehouse presentation', () => {
  const displayPreferences = ref({
    locale: 'en-US',
    timeZone: 'UTC'
  })

  it('formats values and resolves department/manager labels', () => {
    const presentation = useWarehousePresentation(displayPreferences)
    const depts = [
      {
        id: 'd1',
        name: 'Root',
        children: [{ id: 'd2', name: 'Child', children: [] }]
      }
    ] as Dept[]
    const users = [
      { id: 'u1', username: 'alice', realName: 'Alice' }
    ] as User[]

    expect(presentation.interpolate('WH {name}', { name: 'A' })).toBe('WH A')
    expect(presentation.deptLabel(depts, 'd2')).toBe('Child')
    expect(presentation.managerLabel(users, 'u1')).toBe('Alice')
    expect(presentation.activeCount([
      { id: '1', status: 'ACTIVE' },
      { id: '2', status: 'INACTIVE' }
    ] as Warehouse[])).toBe(1)
    expect(presentation.warehouseLabel({ id: '9', warehouseName: 'Main' } as Warehouse)).toBe('Main')
  })
})
