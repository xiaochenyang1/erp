import { describe, expect, it } from 'vitest'

import { toProductCreateContract, toProductUpdateContract } from './masterdata'

const form = {
  code: ' P-001 ',
  name: ' 测试商品 ',
  productType: 'PHYSICAL',
  categoryName: ' 成品 ',
  specifications: '10x20',
  unit: '件',
  unitPrice: 120,
  costPrice: 80,
  taxRate: 13,
  barcode: ' 690000000001 ',
  lotControlled: true,
  shelfLifeControlled: true,
  inspectionRequired: true,
  remark: ' 备注 '
}

describe('product OpenAPI contract mapping', () => {
  it('maps every required create field and optional control field', () => {
    expect(toProductCreateContract(form)).toEqual({
      productCode: 'P-001',
      productName: '测试商品',
      productType: 'PHYSICAL',
      categoryName: '成品',
      specification: '10x20',
      unitName: '件',
      salePrice: 120,
      purchasePrice: 80,
      taxRate: 13,
      barcode: '690000000001',
      lotControlled: true,
      shelfLifeControlled: true,
      inspectionRequired: true,
      serialControlled: false,
      remark: '备注'
    })
  })

  it('omits immutable create-only fields from updates', () => {
    const update = toProductUpdateContract(form)
    expect(update).not.toHaveProperty('productCode')
    expect(update).not.toHaveProperty('productType')
    expect(update.productName).toBe('测试商品')
  })

  it('rejects an incomplete request before sending it', () => {
    expect(() => toProductCreateContract({ ...form, taxRate: undefined }))
      .toThrow('税率不能为空')
  })
})
