import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { Dept, Post, Role } from '@/api/system'
import type { Warehouse } from '@/api/masterdata'
import { flattenDepts, useSystemUserPresentation } from './useSystemUserPresentation'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

describe('system user presentation', () => {
  it('flattens nested department trees', () => {
    const tree = [
      {
        id: '1',
        name: '总部',
        children: [
          { id: '11', name: '研发', children: [{ id: '111', name: '前端' }] },
          { id: '12', name: '销售' }
        ]
      }
    ] as unknown as Dept[]

    expect(flattenDepts(tree).map((item) => item.id)).toEqual(['1', '11', '111', '12'])
  })

  it('resolves department/post/role/status labels', () => {
    const presentation = useSystemUserPresentation(t, {
      depts: ref([
        { id: '1', name: '总部', children: [{ id: '11', name: '研发' }] }
      ] as Dept[]),
      posts: ref([{ id: 'p1', name: '工程师' }] as Post[]),
      warehouses: ref([])
    })

    expect(presentation.deptName('11')).toBe('研发')
    expect(presentation.deptName('missing')).toBe('systemUsers.departmentFallback:{"id":"missing"}')
    expect(presentation.postName('p1')).toBe('工程师')
    expect(presentation.roleNames([
      { name: '管理员' } as Role,
      { code: 'AUDITOR' } as Role
    ])).toBe('管理员systemUsers.listSeparatorAUDITOR')
    expect(presentation.roleNames([])).toBe('-')
    expect(presentation.statusText('ACTIVE')).toBe('systemUsers.active')
    expect(presentation.statusType('ACTIVE')).toBe('success')
    expect(presentation.statusType('LOCKED')).toBe('warning')
    expect(presentation.statusType('INACTIVE')).toBe('info')
  })

  it('formats warehouse option labels from name/code variants', () => {
    const presentation = useSystemUserPresentation(t, {
      depts: ref([]),
      posts: ref([]),
      warehouses: ref([])
    })

    expect(presentation.warehouseOptionLabel({
      id: 'w1',
      name: '主仓',
      code: 'WH01'
    } as Warehouse)).toBe('systemUsers.warehouseOption:{"name":"主仓","code":"WH01"}')
    expect(presentation.warehouseOptionLabel({
      id: 'w2',
      warehouseName: '备用仓'
    } as Warehouse)).toBe('备用仓')
  })
})
