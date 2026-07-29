import { describe, expect, it, vi } from 'vitest'

import type { Location, LocationSaveRequest } from '@/api/masterdata'
import { useWarehouseLocationForm } from './useWarehouseLocationForm'

const t = (key: string) => key

const location = (overrides: Partial<Location> = {}): Location => ({
  id: 'location-1',
  warehouseId: 'warehouse-1',
  warehouseName: 'Main warehouse',
  locationCode: 'A-01',
  locationName: 'Aisle A',
  isDefault: true,
  status: 'ACTIVE',
  remark: 'Near the door',
  ...overrides
})

const createForm = (
  overrides: Partial<Parameters<typeof useWarehouseLocationForm>[1]> = {}
) => useWarehouseLocationForm(t, {
  createLocation: vi.fn(async () => ({})),
  updateLocation: vi.fn(async () => ({})),
  onSuccess: vi.fn(),
  onWarning: vi.fn(),
  onError: vi.fn(),
  onSubmitted: vi.fn(),
  ...overrides
})

describe('warehouse location form', () => {
  it('resets state and opens create with the selected default warehouse', () => {
    const formState = createForm()

    formState.openEdit(location())
    formState.resetForm()

    expect(formState.editingId.value).toBeNull()
    expect(formState.isEditing.value).toBe(false)
    expect(formState.form).toEqual({
      warehouseId: '',
      locationCode: '',
      locationName: '',
      isDefault: false,
      remark: ''
    })

    formState.openCreate(200)

    expect(formState.dialogVisible.value).toBe(true)
    expect(formState.editingId.value).toBeNull()
    expect(formState.isEditing.value).toBe(false)
    expect(formState.form).toEqual({
      warehouseId: '200',
      locationCode: '',
      locationName: '',
      isDefault: false,
      remark: ''
    })
  })

  it('hydrates edit state and marks the warehouse control as locked', () => {
    const formState = createForm()

    formState.openEdit(location({
      id: 9,
      warehouseId: 12,
      isDefault: false,
      remark: undefined
    }))

    expect(formState.dialogVisible.value).toBe(true)
    expect(formState.editingId.value).toBe(9)
    expect(formState.isEditing.value).toBe(true)
    expect(formState.form).toEqual({
      warehouseId: '12',
      locationCode: 'A-01',
      locationName: 'Aisle A',
      isDefault: false,
      remark: ''
    })
  })

  it('validates required fields before saving', async () => {
    const createLocation = vi.fn(async () => ({}))
    const onWarning = vi.fn()
    const formState = createForm({ createLocation, onWarning })

    formState.openCreate()

    expect(formState.validateForm()).toBe(false)
    expect(await formState.save()).toBe(false)
    expect(onWarning).toHaveBeenCalledWith('warehouseLocation.validation.required')
    expect(createLocation).not.toHaveBeenCalled()
    expect(formState.saving.value).toBe(false)
    expect(formState.dialogVisible.value).toBe(true)
  })

  it('creates with the page payload contract, then refreshes and closes', async () => {
    const createLocation = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onSubmitted = vi.fn(async () => {})
    const formState = createForm({ createLocation, onSuccess, onSubmitted })
    formState.openCreate('warehouse-2')
    Object.assign(formState.form, {
      locationCode: 'B-02',
      locationName: 'Aisle B',
      isDefault: true,
      remark: ''
    })

    expect(await formState.save()).toBe(true)

    expect(createLocation).toHaveBeenCalledWith({
      warehouseId: 'warehouse-2',
      locationCode: 'B-02',
      locationName: 'Aisle B',
      isDefault: true,
      remark: undefined
    })
    expect(onSuccess).toHaveBeenCalledWith('warehouseLocation.message.created')
    expect(onSubmitted).toHaveBeenCalledTimes(1)
    expect(formState.dialogVisible.value).toBe(false)
    expect(formState.saving.value).toBe(false)
  })

  it('updates without allowing warehouseId into the payload', async () => {
    const updateLocation = vi.fn(async (
      _id: string | number,
      _payload: LocationSaveRequest
    ) => ({}))
    const onSuccess = vi.fn()
    const onSubmitted = vi.fn(async () => {})
    const formState = createForm({ updateLocation, onSuccess, onSubmitted })
    formState.openEdit(location())
    Object.assign(formState.form, {
      warehouseId: 'attempted-move',
      locationCode: 'A-02',
      locationName: 'Aisle A updated',
      isDefault: false,
      remark: 'Updated'
    })

    expect(await formState.save()).toBe(true)

    expect(updateLocation).toHaveBeenCalledWith('location-1', {
      locationCode: 'A-02',
      locationName: 'Aisle A updated',
      isDefault: false,
      remark: 'Updated'
    })
    expect(updateLocation.mock.calls[0][1]).not.toHaveProperty('warehouseId')
    expect(onSuccess).toHaveBeenCalledWith('warehouseLocation.message.saved')
    expect(onSubmitted).toHaveBeenCalledTimes(1)
    expect(formState.dialogVisible.value).toBe(false)
  })

  it('releases saving state and reports API failures without closing', async () => {
    let rejectRequest: ((reason?: unknown) => void) | undefined
    const createLocation = vi.fn(() => new Promise((_, reject) => {
      rejectRequest = reject
    }))
    const onError = vi.fn()
    const onSubmitted = vi.fn()
    const formState = createForm({ createLocation, onError, onSubmitted })
    formState.openCreate('warehouse-1')
    formState.form.locationCode = 'A-03'
    formState.form.locationName = 'Aisle C'

    const saving = formState.save()
    expect(formState.saving.value).toBe(true)
    rejectRequest?.(new Error('request failed'))

    expect(await saving).toBe(false)
    expect(formState.saving.value).toBe(false)
    expect(formState.dialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('warehouseLocation.message.saveFailed')
    expect(onSubmitted).not.toHaveBeenCalled()
  })
})
