import { describe, expect, it } from 'vitest'

import { useSystemLogPresentation } from './useSystemLogPresentation'

describe('system log presentation', () => {
  it('classifies execution time thresholds', () => {
    const presentation = useSystemLogPresentation()
    expect(presentation.getExecutionTimeType(100)).toBe('success')
    expect(presentation.getExecutionTimeType(800)).toBe('warning')
    expect(presentation.getExecutionTimeType(3000)).toBe('danger')
  })

  it('detects success results and formats JSON safely', () => {
    const presentation = useSystemLogPresentation()
    expect(presentation.isSuccess('SUCCESS')).toBe(true)
    expect(presentation.isSuccess('FAIL')).toBe(false)
    expect(presentation.formatJson('{"a":1}')).toContain('"a": 1')
    expect(presentation.formatJson('not-json')).toBe('not-json')
  })

  it('builds day-boundary datetime strings', () => {
    const presentation = useSystemLogPresentation()
    expect(presentation.toStartDateTime('2026-07-01')).toBe('2026-07-01T00:00:00')
    expect(presentation.toEndDateTime('2026-07-01')).toBe('2026-07-01T23:59:59')
    expect(presentation.toStartDateTime(undefined)).toBeUndefined()
  })
})
