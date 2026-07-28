import { describe, expect, it } from 'vitest'

import { useSystemPostPresentation } from './useSystemPostPresentation'

const t = (key: string) => key

describe('system post presentation', () => {
  it('maps active/inactive status text and tag type', () => {
    const presentation = useSystemPostPresentation(t)

    expect(presentation.statusText('ACTIVE')).toBe('systemPost.active')
    expect(presentation.statusText('INACTIVE')).toBe('systemPost.inactive')
    expect(presentation.statusText('OTHER')).toBe('OTHER')
    expect(presentation.statusText()).toBe('')

    expect(presentation.statusType('ACTIVE')).toBe('success')
    expect(presentation.statusType('INACTIVE')).toBe('danger')
    expect(presentation.statusType('OTHER')).toBe('danger')
  })
})
