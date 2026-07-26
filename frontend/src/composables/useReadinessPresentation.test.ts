import { describe, expect, it } from 'vitest'

import type { ReadinessRun } from '@/api/readiness'
import { useReadinessPresentation } from './useReadinessPresentation'

const t = (key: string) => key

describe('readiness presentation', () => {
  it('maps preflight/run/item/decision labels and tags', () => {
    const presentation = useReadinessPresentation(t)

    expect(presentation.preflightStatusLabel('PASS')).toBe('systemReadiness.statuses.passed')
    expect(presentation.preflightStatusLabel(undefined)).toBe('systemReadiness.statuses.unchecked')
    expect(presentation.runStatusLabel('IN_PROGRESS')).toBe('systemReadiness.statuses.inProgress')
    expect(presentation.itemStatusLabel('PENDING')).toBe('systemReadiness.statuses.pending')
    expect(presentation.decisionLabel('GO')).toBe('systemReadiness.decisions.go')

    expect(presentation.preflightTagType('FAIL')).toBe('danger')
    expect(presentation.runStatusTagType('NO_GO')).toBe('danger')
    expect(presentation.itemStatusTagType('PASSED')).toBe('success')
    expect(presentation.decisionTagType('PENDING')).toBe('info')
    expect(presentation.priorityTagType('P0')).toBe('danger')
  })

  it('detects closed runs and joins sample text', () => {
    const presentation = useReadinessPresentation(t)
    expect(presentation.isRunClosed({ status: 'PASSED' } as ReadinessRun)).toBe(true)
    expect(presentation.isRunClosed({ status: 'DRAFT' } as ReadinessRun)).toBe(false)
    expect(presentation.sampleText(['a', 'b'])).toBe('asystemReadiness.listSeparatorb')
    expect(presentation.sampleText([])).toBe('-')
  })
})
