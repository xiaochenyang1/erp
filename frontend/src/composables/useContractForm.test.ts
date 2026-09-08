import { describe, expect, it, vi } from 'vitest'

import type { ContractRecord } from '@/api/contracts'
import { useContractForm } from './useContractForm'

const t = (key: string) => key

const detail = (overrides: Partial<ContractRecord> = {}) => ({
  id: 'c1',
  contractNo: 'HT001',
  contractType: 'PURCHASE',
  supplierId: 'su1',
  contractName: '采购框架协议',
  signedDate: '2026-08-01',
  effectiveFrom: '2026-08-01',
  effectiveTo: '2026-12-31',
  remark: '年度',
  status: 'DRAFT',
  totalAmount: 100,
  lines: [{ id: 'l1', productId: 'p1', quantity: 2, unitPrice: 50, amount: 100 }],
  ...overrides
}) as ContractRecord

const createForm = (overrides: Partial<Parameters<typeof useContractForm>[1]> = {}) =>
  useContractForm(t, {
    getContract: vi.fn(async () => detail()),
    createContract: vi.fn(async () => ({})),
    updateContract: vi.fn(async () => ({})),
    ensureOptions: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    onSubmitted: vi.fn(),
    ...overrides
  })

const fill = (form: ReturnType<typeof createForm>['form']) => {
  form.contractName = '新合同'
  form.customerId = 'cu1'
  form.lines = [{ productId: 'p1', quantity: 2, unitPrice: 5 }]
}

describe('contract form', () => {
  it('opens a blank sales contract dated today with one line', async () => {
    const ensureOptions = vi.fn(async () => true)
    const form = createForm({ ensureOptions })

    expect(await form.openCreate()).toBe(true)
    expect(ensureOptions).toHaveBeenCalled()
    expect(form.formVisible.value).toBe(true)
    expect(form.editingId.value).toBeNull()
    expect(form.dialogTitle.value).toBe('contractPage.createTitle')
    expect(form.form.contractType).toBe('SALES')
    expect(form.form.signedDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(form.form.effectiveFrom).toBe(form.form.signedDate)
    expect(form.form.lines).toEqual([{ productId: '', quantity: 1, unitPrice: 0 }])
  })

  it('refetches the contract when editing and titles the dialog accordingly', async () => {
    const getContract = vi.fn(async () => detail())
    const form = createForm({ getContract })

    expect(await form.openEdit({ id: 'c1' } as ContractRecord)).toBe(true)
    expect(getContract).toHaveBeenCalledWith('c1')
    expect(form.editingId.value).toBe('c1')
    expect(form.dialogTitle.value).toBe('contractPage.editTitle')
    expect(form.form.contractType).toBe('PURCHASE')
    expect(form.form.supplierId).toBe('su1')
    expect(form.form.lines).toEqual([{ productId: 'p1', quantity: 2, unitPrice: 50 }])

    const onError = vi.fn()
    const failing = createForm({
      getContract: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    expect(await failing.openEdit({ id: 'c1' } as ContractRecord)).toBe(false)
    expect(failing.formVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.detailFailed')
  })

  it('adds lines and keeps the last one so the editor is never empty', () => {
    const form = createForm()

    form.addLine()
    expect(form.form.lines).toHaveLength(2)
    form.removeLine(0)
    expect(form.form.lines).toHaveLength(1)
    form.removeLine(0)
    expect(form.form.lines).toHaveLength(1)
  })

  it('warns instead of saving while the contract is incomplete', async () => {
    const onWarning = vi.fn()
    const createContract = vi.fn(async () => ({}))
    const form = createForm({ onWarning, createContract })

    expect(await form.save()).toBe(false)
    expect(createContract).not.toHaveBeenCalled()
    expect(onWarning).toHaveBeenCalledWith('contractPage.message.completeForm')

    form.form.contractName = '新合同'
    expect(await form.save()).toBe(false)

    form.form.customerId = 'cu1'
    expect(await form.save()).toBe(false)

    form.form.lines = [{ productId: 'p1', quantity: 0, unitPrice: 5 }]
    expect(await form.save()).toBe(false)
    expect(createContract).not.toHaveBeenCalled()
  })

  it('creates a sales contract without the supplier side', async () => {
    const createContract = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const onSuccess = vi.fn()
    const form = createForm({ createContract, onSubmitted, onSuccess })

    fill(form.form)
    form.form.supplierId = 'su1'
    expect(await form.save()).toBe(true)

    expect(createContract).toHaveBeenCalledWith(expect.objectContaining({
      contractType: 'SALES',
      customerId: 'cu1',
      supplierId: undefined,
      lines: [{ productId: 'p1', quantity: 2, unitPrice: 5 }]
    }))
    expect(form.formVisible.value).toBe(false)
    expect(form.saving.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('contractPage.message.saved')
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('updates an edited purchase contract without the customer side', async () => {
    const updateContract = vi.fn(async () => ({}))
    const form = createForm({ updateContract })

    await form.openEdit({ id: 'c1' } as ContractRecord)
    expect(await form.save()).toBe(true)
    expect(updateContract).toHaveBeenCalledWith('c1', expect.objectContaining({
      contractType: 'PURCHASE',
      customerId: undefined,
      supplierId: 'su1'
    }))
  })

  it('reports a failed save, keeps the dialog open and clears the saving flag', async () => {
    const onError = vi.fn()
    const form = createForm({
      createContract: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    await form.openCreate()
    fill(form.form)
    expect(await form.save()).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.saveFailed')
    expect(form.saving.value).toBe(false)
    expect(form.formVisible.value).toBe(true)
  })

  it('resets back to a blank draft', async () => {
    const form = createForm()

    await form.openEdit({ id: 'c1' } as ContractRecord)
    form.resetForm()

    expect(form.editingId.value).toBeNull()
    expect(form.form.contractType).toBe('SALES')
    expect(form.form.contractName).toBe('')
    expect(form.form.lines).toEqual([{ productId: '', quantity: 1, unitPrice: 0 }])
  })
})
