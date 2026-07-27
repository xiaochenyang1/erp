import { describe, expect, it } from 'vitest'

import { useWorkflowTaskPresentation } from './useWorkflowTaskPresentation'

const t = (key: string) => key

describe('workflow task presentation', () => {
  it('maps business types, statuses and tag types', () => {
    const presentation = useWorkflowTaskPresentation(t)
    expect(presentation.businessTypeLabel('SALES_ORDER')).toBe('workflow.salesOrder')
    expect(presentation.businessTypeLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(presentation.businessTypeLabel()).toBe('-')

    expect(presentation.taskStatusLabel('PENDING')).toBe('workflow.pending')
    expect(presentation.taskStatusLabel('APPROVED')).toBe('workflow.approved')
    expect(presentation.taskStatusType('PENDING')).toBe('warning')
    expect(presentation.taskStatusType('APPROVED')).toBe('success')
    expect(presentation.taskStatusType('REJECTED')).toBe('danger')
    expect(presentation.taskStatusType('CANCELLED')).toBe('info')
    expect(presentation.taskStatusType('OTHER')).toBe('info')
  })

  it('formats times with a dash fallback', () => {
    const presentation = useWorkflowTaskPresentation(t)
    expect(presentation.formatTime(undefined)).toBe('-')
    expect(presentation.formatTime('')).toBe('-')
  })
})
