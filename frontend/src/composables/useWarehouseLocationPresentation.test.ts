import { describe, expect, it } from 'vitest'

import type { Warehouse } from '@/api/masterdata'
import { useWarehouseLocationPresentation } from './useWarehouseLocationPresentation'

const t = (key: string) => key

describe('warehouse location presentation', () => {
  const presentation = useWarehouseLocationPresentation(t)

  it('builds warehouse option labels with the same field precedence as the page', () => {
    expect(presentation.warehouseLabel({
      warehouseCode: 'WH-01',
      warehouseName: 'Main warehouse',
      name: 'Alias name'
    } as Warehouse)).toBe('WH-01 Main warehouse')

    expect(presentation.warehouseLabel({
      warehouseCode: 'WH-02',
      warehouseName: '',
      name: 'Spare warehouse'
    } as Warehouse)).toBe('WH-02 Spare warehouse')

    expect(presentation.warehouseLabel({
      warehouseCode: 'WH-03',
      warehouseName: ''
    } as Warehouse)).toBe('WH-03')

    expect(presentation.warehouseLabel({
      warehouseCode: '',
      warehouseName: 'Name only'
    } as Warehouse)).toBe('Name only')
  })

  it('returns an empty warehouse label when no display fields are available', () => {
    expect(presentation.warehouseLabel({
      warehouseCode: '',
      warehouseName: '',
      name: ''
    } as Warehouse)).toBe('')
    expect(presentation.warehouseLabel(undefined)).toBe('')
    expect(presentation.warehouseLabel(null)).toBe('')
  })

  it('maps only ACTIVE to the active label and success tag', () => {
    expect(presentation.isActive('ACTIVE')).toBe(true)
    expect(presentation.statusLabel('ACTIVE')).toBe('warehouseLocation.active')
    expect(presentation.statusTagType('ACTIVE')).toBe('success')

    expect(presentation.isActive('INACTIVE')).toBe(false)
    expect(presentation.statusLabel('INACTIVE')).toBe('warehouseLocation.inactive')
    expect(presentation.statusTagType('INACTIVE')).toBe('info')
  })

  it('keeps the page fallback for unknown and empty statuses', () => {
    for (const status of ['ARCHIVED', '', undefined, null]) {
      expect(presentation.isActive(status)).toBe(false)
      expect(presentation.statusLabel(status)).toBe('warehouseLocation.inactive')
      expect(presentation.statusTagType(status)).toBe('info')
    }
  })

  it('maps default-location values and treats empty values as not default', () => {
    expect(presentation.isDefaultLocation(true)).toBe(true)
    expect(presentation.defaultLabel(true)).toBe('warehouseLocation.yes')

    for (const isDefault of [false, undefined, null]) {
      expect(presentation.isDefaultLocation(isDefault)).toBe(false)
      expect(presentation.defaultLabel(isDefault)).toBe('warehouseLocation.no')
    }
  })
})
