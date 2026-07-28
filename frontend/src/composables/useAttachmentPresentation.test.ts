import { describe, expect, it, vi } from 'vitest'

import { useAttachmentPresentation } from './useAttachmentPresentation'

describe('attachment presentation', () => {
  it('formats byte, kilobyte and megabyte ranges with the injected locale formatter', () => {
    const formatNumber = vi.fn((value: number) => String(value))
    const { formatFileSize } = useAttachmentPresentation({ formatNumber })

    expect(formatFileSize(1023)).toBe('1023 B')
    expect(formatFileSize(1536)).toBe('1.5 KB')
    expect(formatFileSize(2.25 * 1024 * 1024)).toBe('2.25 MB')

    expect(formatNumber).toHaveBeenNthCalledWith(1, 1023)
    expect(formatNumber).toHaveBeenNthCalledWith(2, 1.5, { maximumFractionDigits: 1 })
    expect(formatNumber).toHaveBeenNthCalledWith(3, 2.25, { maximumFractionDigits: 1 })
  })
})
