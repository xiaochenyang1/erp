import { describe, expect, it } from 'vitest'

import { useSystemDeptPresentation } from './useSystemDeptPresentation'

const t = (key: string) => key

describe('system dept presentation', () => {
  it('maps status labels and builds parent tree with root node', () => {
    const presentation = useSystemDeptPresentation(t)

    expect(presentation.statusText('ACTIVE')).toBe('systemDept.active')
    expect(presentation.statusText('INACTIVE')).toBe('systemDept.inactive')
    expect(presentation.statusType('ACTIVE')).toBe('success')
    expect(presentation.statusType('INACTIVE')).toBe('danger')

    const tree = presentation.buildParentTree([
      { id: '1', name: '研发', orderNum: 1, status: 'ACTIVE' }
    ] as any)
    expect(tree).toHaveLength(1)
    expect(tree[0].id).toBe('0')
    expect(tree[0].name).toBe('systemDept.root')
    expect(tree[0].children).toHaveLength(1)
  })
})
