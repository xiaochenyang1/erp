import { describe, expect, it } from 'vitest'

import { incrementScannedLine } from '@/utils/barcode'

describe('incrementScannedLine', () => {
  it('increments the matching line without converting a Long id to number', () => {
    const lines = [
      { productId: '9007199254740993', quantity: 0, maximum: 2 },
      { productId: '9007199254740995', quantity: 0, maximum: 3 }
    ]

    const result = incrementScannedLine(
      lines,
      '9007199254740993',
      (line) => line.maximum
    )

    expect(result).toEqual({ status: 'incremented', index: 0, quantity: 1 })
    expect(lines.map((line) => line.quantity)).toEqual([1, 0])
  })

  it('matches equivalent string and number ids without precision arithmetic', () => {
    const lines = [{ productId: '42', quantity: 0, maximum: 1 }]

    const result = incrementScannedLine(lines, 42, (line) => line.maximum)

    expect(result.status).toBe('incremented')
    expect(lines[0].quantity).toBe(1)
  })

  it('does not mutate lines when the scanned product is unrelated', () => {
    const lines = [{ productId: '11', quantity: 0, maximum: 2 }]

    const result = incrementScannedLine(lines, '12', (line) => line.maximum)

    expect(result).toEqual({ status: 'not-found' })
    expect(lines[0].quantity).toBe(0)
  })

  it('does not exceed the order-derived maximum', () => {
    const lines = [{ productId: '11', quantity: 2, maximum: 2 }]

    const result = incrementScannedLine(lines, '11', (line) => line.maximum)

    expect(result).toEqual({ status: 'at-maximum', index: 0, quantity: 2 })
    expect(lines[0].quantity).toBe(2)
  })
})
