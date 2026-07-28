import { describe, expect, it } from 'vitest'

import { useSystemMenuPresentation } from './useSystemMenuPresentation'

const t = (key: string) => key

describe('system menu presentation', () => {
  it('maps type/status labels and builds parent tree with root node', () => {
    const presentation = useSystemMenuPresentation(t)

    expect(presentation.statusText('ACTIVE')).toBe('systemMenu.active')
    expect(presentation.statusType('INACTIVE')).toBe('danger')
    expect(presentation.typeText('MENU')).toBe('systemMenu.menu')
    expect(presentation.typeText('BUTTON')).toBe('systemMenu.button')
    expect(presentation.typeTagType('BUTTON')).toBe('info')

    const tree = presentation.buildParentTree([
      { id: '1', name: '系统', orderNum: 1, type: 'MENU', status: 'ACTIVE' }
    ] as any)
    expect(tree[0].id).toBe('0')
    expect(tree[0].name).toBe('systemMenu.root')
    expect(tree[0].children).toHaveLength(1)
  })
})
