import { describe, expect, it, vi } from 'vitest'

import {
  formatAuxQuantity,
  hydrateProductLineLabels,
  parseSerialNos,
  serialCaptureProgress,
  validateProductControlLines
} from '@/utils/productLines'

describe('hydrateProductLineLabels', () => {
  it('fills missing product labels and reuses one lookup per product', async () => {
    const loadProduct = vi.fn().mockResolvedValue({
      productCode: 'P-001',
      productName: '测试商品',
      lotControlled: true,
      shelfLifeControlled: true,
      serialControlled: false
    })
    const lines = [
      { productId: '9007199254740993', quantity: 1 },
      { productId: '9007199254740993', quantity: 2 }
    ]

    const result = await hydrateProductLineLabels(lines, loadProduct)

    expect(result).toEqual([
      {
        productId: '9007199254740993',
        quantity: 1,
        productCode: 'P-001',
        productName: '测试商品',
        lotControlled: true,
        shelfLifeControlled: true,
        serialControlled: false
      },
      {
        productId: '9007199254740993',
        quantity: 2,
        productCode: 'P-001',
        productName: '测试商品',
        lotControlled: true,
        shelfLifeControlled: true,
        serialControlled: false
      }
    ])
    expect(loadProduct).toHaveBeenCalledOnce()
    expect(loadProduct).toHaveBeenCalledWith('9007199254740993')
  })

  it('still loads control flags when product labels already exist', async () => {
    const loadProduct = vi.fn().mockResolvedValue({
      productCode: 'IGNORED',
      productName: 'IGNORED',
      lotControlled: true,
      shelfLifeControlled: false,
      serialControlled: true
    })
    const lines = [
      {
        productId: '1',
        productCode: 'KNOWN',
        productName: '已有名称',
        quantity: 2
      }
    ]

    const result = await hydrateProductLineLabels(lines, loadProduct)

    expect(result).toEqual([
      {
        productId: '1',
        productCode: 'KNOWN',
        productName: '已有名称',
        quantity: 2,
        lotControlled: true,
        shelfLifeControlled: false,
        serialControlled: true
      }
    ])
    expect(loadProduct).toHaveBeenCalledOnce()
    expect(loadProduct).toHaveBeenCalledWith('1')
  })

  it('preserves existing labels and leaves a line usable when lookup fails', async () => {
    const loadProduct = vi.fn().mockRejectedValue(new Error('offline'))
    const lines = [
      { productId: '1', productCode: 'KNOWN', productName: '已有名称' },
      { productId: '2', quantity: 3 }
    ]

    const result = await hydrateProductLineLabels(lines, loadProduct)

    expect(result[0]).toEqual(lines[0])
    expect(result[1]).toEqual(lines[1])
    // Labels-only line still needs control flags, so both products are requested.
    expect(loadProduct).toHaveBeenCalledTimes(2)
    expect(loadProduct).toHaveBeenCalledWith('1')
    expect(loadProduct).toHaveBeenCalledWith('2')
  })

  it('skips lookup when labels, control flags and aux conversion are already present', async () => {
    const loadProduct = vi.fn()
    const lines = [
      {
        productId: '1',
        productCode: 'KNOWN',
        productName: '已有名称',
        lotControlled: false,
        shelfLifeControlled: false,
        serialControlled: false,
        auxUnitName: '箱',
        conversionFactor: 12
      }
    ]

    const result = await hydrateProductLineLabels(lines, loadProduct)

    expect(result).toEqual(lines)
    expect(loadProduct).not.toHaveBeenCalled()
  })
})

describe('parseSerialNos', () => {
  it('splits mixed separators and de-duplicates serials', () => {
    expect(parseSerialNos('SN1, SN2;SN2\nSN3，SN1')).toEqual(['SN1', 'SN2', 'SN3'])
    expect(parseSerialNos('  ')).toEqual([])
  })
})

describe('formatAuxQuantity', () => {
  it('formats base quantity into aux packaging units', () => {
    expect(formatAuxQuantity(24, 12, '箱')).toBe('2 箱')
    expect(formatAuxQuantity(10, 0, '箱')).toBe('-')
    expect(formatAuxQuantity(10, 12, '')).toBe('-')
  })
})

describe('serialCaptureProgress', () => {
  it('reports captured vs expected serial counts', () => {
    expect(serialCaptureProgress('A,B', 2)).toEqual({ count: 2, expected: 2, complete: true })
    expect(serialCaptureProgress('A', 3)).toEqual({ count: 1, expected: 3, complete: false })
  })
})

describe('validateProductControlLines', () => {
  it('requires lot, expiry and matching serial count for controlled products', () => {
    const issues = validateProductControlLines([
      {
        productId: '1',
        productCode: 'LOT',
        quantity: 2,
        lotControlled: true,
        shelfLifeControlled: true,
        serialControlled: true,
        lotNo: '',
        expiryDate: '',
        serialNos: 'A'
      },
      {
        productId: '2',
        productCode: 'OK',
        quantity: 2,
        lotControlled: true,
        shelfLifeControlled: true,
        serialControlled: true,
        lotNo: 'L1',
        expiryDate: '2026-12-31',
        serialNos: 'S1,S2'
      },
      {
        productId: '3',
        productCode: 'ZERO',
        quantity: 0,
        lotControlled: true,
        serialControlled: true
      }
    ])

    expect(issues.map((item) => item.messageKey)).toEqual([
      'lotRequired',
      'expiryRequired',
      'serialCountMismatch'
    ])
    expect(issues[2].expectedSerialCount).toBe(2)
    expect(issues[2].actualSerialCount).toBe(1)
  })
})
