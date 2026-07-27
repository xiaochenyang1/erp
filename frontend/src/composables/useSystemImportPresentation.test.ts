import { describe, expect, it } from 'vitest'

import type { ImportJob } from '@/api/imports'
import { useSystemImportPresentation } from './useSystemImportPresentation'

const t = (key: string) => key

describe('system import presentation', () => {
  it('maps import type/status labels and commit eligibility', () => {
    const presentation = useSystemImportPresentation(t)
    expect(presentation.importTypeOptions.value).toHaveLength(9)
    expect(presentation.statusOptions.value).toHaveLength(5)
    expect(presentation.importTypeLabel('PRODUCT')).toBe('systemImports.types.product')
    expect(presentation.statusLabel('VALIDATED')).toBe('systemImports.statuses.validated')
    expect(presentation.statusTagType('FAILED')).toBe('danger')
    expect(presentation.canCommit({
      status: 'VALIDATED',
      validRows: 1,
      errorRows: 0,
      committedRows: 0
    } as ImportJob)).toBe(true)
    expect(presentation.canCommit({
      status: 'FAILED',
      validRows: 2,
      errorRows: 0,
      committedRows: 0
    } as ImportJob)).toBe(true)
    expect(presentation.canCommit({
      status: 'FAILED',
      validRows: 2,
      errorRows: 1,
      committedRows: 0
    } as ImportJob)).toBe(false)
    expect(presentation.countJobsWithErrors([
      { errorRows: 1 } as ImportJob,
      { errorRows: 0 } as ImportJob
    ])).toBe(1)
    expect(presentation.formatJson({ a: 1 })).toContain('"a": 1')
  })
})
