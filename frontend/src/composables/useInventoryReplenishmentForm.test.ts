import { describe, expect, it, vi } from 'vitest'

import type { InventoryReplenishmentSuggestion } from '@/api/inventory'
import { useInventoryReplenishmentForm } from './useInventoryReplenishmentForm'

const t = (key: string) => key

const row = {
  id: 's1',
  suggestionNo: 'RS001',
  warehouseName: '主仓',
  productCode: 'P1',
  productName: '商品',
  supplierId: 'sup1',
  suggestedQty: 8,
  expectedArrivalDate: '2026-07-30',
  remark: '备注'
} as InventoryReplenishmentSuggestion

const createForm = (overrides: Partial<Parameters<typeof useInventoryReplenishmentForm>[1]> = {}) =>
  useInventoryReplenishmentForm(t, {
    updateSuggestion: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('inventory replenishment form', () => {
  it('opens edit with current suggestion values', () => {
    const form = createForm()
    form.handleEdit(row)
    expect(form.editDialogVisible.value).toBe(true)
    expect(form.editForm).toMatchObject({
      id: 's1',
      suggestionNo: 'RS001',
      warehouseName: '主仓',
      productName: 'P1 商品',
      supplierId: 'sup1',
      suggestedQty: 8,
      expectedArrivalDate: '2026-07-30',
      remark: '备注'
    })
  })

  it('saves updates and refreshes the list', async () => {
    const updateSuggestion = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ updateSuggestion, onSubmitted })
    form.handleEdit(row)
    form.editForm.suggestedQty = 12
    form.editForm.remark = ''
    expect(await form.submitEdit()).toBe(true)
    expect(updateSuggestion).toHaveBeenCalledWith('s1', {
      supplierId: 'sup1',
      suggestedQty: 12,
      expectedArrivalDate: '2026-07-30',
      remark: undefined
    })
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('reports save failures without closing the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      updateSuggestion: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    form.handleEdit(row)
    expect(await form.submitEdit()).toBe(false)
    expect(form.editDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('inventoryReplenishment.message.saveFailed')
  })
})
