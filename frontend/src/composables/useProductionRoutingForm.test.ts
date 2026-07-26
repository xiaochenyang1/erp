import { describe, expect, it, vi } from 'vitest'

import type { Routing } from '@/api/production'
import { useProductionRoutingForm } from './useProductionRoutingForm'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.line != null ? `${key}:${params.line}` : key

const detail = {
  id: 'r1',
  routingCode: 'RT001',
  routingName: '装配路线',
  bomId: 'b1',
  remark: '备注',
  operations: [
    {
      operationCode: 'OP10',
      operationName: '下料',
      workCenterId: 'wc1',
      standardMinutes: 12,
      remark: ''
    }
  ]
} as unknown as Routing

const createForm = (overrides: Partial<Parameters<typeof useProductionRoutingForm>[1]> = {}) =>
  useProductionRoutingForm(t, {
    getRouting: vi.fn(async () => detail),
    createRouting: vi.fn(async () => ({})),
    updateRouting: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    onWarning: vi.fn(),
    ...overrides
  })

describe('production routing form', () => {
  it('opens a blank create form', () => {
    const form = createForm()
    form.handleAdd()

    expect(form.dialogVisible.value).toBe(true)
    expect(form.isEdit.value).toBe(false)
    expect(form.formData.operations).toEqual([])
    expect(form.dialogTitle.value).toBe('productionRouting.dialog.create')
  })

  it('loads the detail for edit and clones operations', async () => {
    const getRouting = vi.fn(async () => detail)
    const form = createForm({ getRouting })

    expect(await form.handleEdit({ id: 'r1' } as Routing)).toBe(true)
    expect(getRouting).toHaveBeenCalledWith('r1')
    expect(form.isEdit.value).toBe(true)
    expect(form.formData).toMatchObject({
      id: 'r1',
      routingCode: 'RT001',
      routingName: '装配路线',
      bomId: 'b1',
      remark: '备注'
    })
    expect(form.formData.operations).toHaveLength(1)
    expect(form.formData.operations[0]).not.toBe(detail.operations![0])
    expect(form.dialogTitle.value).toBe('productionRouting.dialog.edit')
  })

  it('reports detail failures without opening the dialog', async () => {
    const onError = vi.fn()
    const form = createForm({
      getRouting: vi.fn(async () => { throw new Error('boom') }),
      onError
    })

    expect(await form.handleEdit({ id: 'r1' } as Routing)).toBe(false)
    expect(form.dialogVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('productionRouting.message.detailLoadFailed')
  })

  it('adds and removes operations', () => {
    const form = createForm()
    form.handleAdd()
    form.handleAddOperation()
    form.handleAddOperation()
    expect(form.formData.operations).toHaveLength(2)
    form.handleDeleteOperation(0)
    expect(form.formData.operations).toHaveLength(1)
  })

  it('blocks submit without complete operations', async () => {
    const onWarning = vi.fn()
    const form = createForm({ onWarning })
    form.handleAdd()

    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('productionRouting.validation.operations')

    form.handleAddOperation()
    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('productionRouting.validation.operationRequired:1')
  })

  it('creates when no id is present and updates when editing', async () => {
    const createRouting = vi.fn(async () => ({}))
    const updateRouting = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createRouting, updateRouting, onSubmitted })

    form.handleAdd()
    form.formData.routingCode = 'RT-NEW'
    form.formData.routingName = '新路线'
    form.formData.bomId = 'b9'
    form.handleAddOperation()
    Object.assign(form.formData.operations[0], {
      operationCode: 'OP10',
      operationName: '装配',
      workCenterId: 'wc1',
      standardMinutes: 5
    })
    expect(await form.handleSubmit()).toBe(true)
    expect(createRouting).toHaveBeenCalledWith({
      routingCode: 'RT-NEW',
      routingName: '新路线',
      bomId: 'b9',
      remark: '',
      operations: [
        {
          operationCode: 'OP10',
          operationName: '装配',
          workCenterId: 'wc1',
          standardMinutes: 5,
          remark: ''
        }
      ]
    })
    expect(onSubmitted).toHaveBeenCalled()

    await form.handleEdit({ id: 'r1' } as Routing)
    form.formData.routingName = '改名'
    expect(await form.handleSubmit()).toBe(true)
    expect(updateRouting).toHaveBeenCalledWith('r1', {
      routingName: '改名',
      remark: '备注',
      operations: [
        {
          operationCode: 'OP10',
          operationName: '下料',
          workCenterId: 'wc1',
          standardMinutes: 12,
          remark: ''
        }
      ]
    })
  })

  it('requires bomId when creating', async () => {
    const onWarning = vi.fn()
    const form = createForm({ onWarning })
    form.handleAdd()
    form.formData.routingCode = 'RT'
    form.formData.routingName = '路线'
    form.handleAddOperation()
    Object.assign(form.formData.operations[0], {
      operationCode: 'OP10',
      operationName: '装配',
      workCenterId: 'wc1',
      standardMinutes: 5
    })
    expect(await form.handleSubmit()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('productionRouting.validation.bom')
  })
})
