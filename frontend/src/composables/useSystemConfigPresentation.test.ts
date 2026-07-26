import { describe, expect, it } from 'vitest'

import { useSystemConfigPresentation } from './useSystemConfigPresentation'

const t = (key: string) => key

describe('system config presentation', () => {
  it('maps status labels and tag types', () => {
    const presentation = useSystemConfigPresentation(t)
    expect(presentation.statusText('ACTIVE')).toBe('systemConfigs.active')
    expect(presentation.statusType('ACTIVE')).toBe('success')
    expect(presentation.statusText('DISABLED')).toBe('systemConfigs.disabled')
    expect(presentation.statusType('DISABLED')).toBe('info')
  })

  it('validates positive and non-negative integer strings', () => {
    const presentation = useSystemConfigPresentation(t)
    const ok = viCallback()
    const bad = viCallback()

    presentation.validatePositiveInteger(null, '4', ok.cb)
    expect(ok.error).toBeUndefined()
    presentation.validatePositiveInteger(null, '0', bad.cb)
    expect(bad.error?.message).toBe('systemConfigs.validation.positiveInteger')

    presentation.validateNonNegativeInteger(null, '0', ok.cb)
    expect(ok.error).toBeUndefined()
    presentation.validateNonNegativeInteger(null, '-1', bad.cb)
    expect(bad.error?.message).toBe('systemConfigs.validation.nonNegativeInteger')
  })
})

const viCallback = () => {
  const state: { error?: Error } = {}
  return {
    get error() {
      return state.error
    },
    cb: (error?: Error) => {
      state.error = error
    }
  }
}
