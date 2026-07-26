import { describe, expect, it, vi } from 'vitest'

import type { DictItem, DictType } from '@/api/system'
import { useSystemDictList } from './useSystemDictList'

const t = (key: string, params?: Record<string, unknown>) =>
  params ? `${key}:${JSON.stringify(params)}` : key

const type = (overrides: Partial<DictType> = {}) =>
  ({ id: 't1', code: 'GENDER', name: '性别', status: 'ACTIVE', ...overrides }) as DictType

const item = (overrides: Partial<DictItem> = {}) =>
  ({
    id: 'i1',
    typeCode: 'GENDER',
    label: '男',
    value: 'M',
    orderNum: 1,
    status: 'ACTIVE',
    ...overrides
  }) as DictItem

const createList = (overrides: Partial<Parameters<typeof useSystemDictList>[1]> = {}) =>
  useSystemDictList(t, {
    getDictTypes: vi.fn(async () => ({ records: [type()], total: 1 } as any)),
    getDictItems: vi.fn(async () => [item()]),
    deleteDictType: vi.fn(async () => ({})),
    enableDictType: vi.fn(async () => ({})),
    deleteDictItem: vi.fn(async () => ({})),
    enableDictItem: vi.fn(async () => ({})),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system dict list', () => {
  it('loads types and items, filtering types client-side', async () => {
    const getDictTypes = vi.fn(async () => ({
      records: [type(), type({ id: 't2', code: 'STATUS', name: '状态' })],
      total: 2
    } as any))
    const getDictItems = vi.fn(async () => [item()])
    const list = createList({ getDictTypes, getDictItems })

    await list.loadTypeList()
    expect(getDictTypes).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200 })
    list.typeSearchText.value = 'status'
    expect(list.filteredTypeList.value.map((item) => item.code)).toEqual(['STATUS'])

    await list.handleTypeSelect(type())
    expect(getDictItems).toHaveBeenCalledWith('GENDER')
    expect(list.itemList.value).toHaveLength(1)
  })

  it('disables and enables types/items after confirmation', async () => {
    const deleteDictType = vi.fn(async () => ({}))
    const enableDictType = vi.fn(async () => ({}))
    const deleteDictItem = vi.fn(async () => ({}))
    const enableDictItem = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const list = createList({
      deleteDictType,
      enableDictType,
      deleteDictItem,
      enableDictItem,
      onSuccess
    })
    await list.loadTypeList()
    await list.handleTypeSelect(type())

    expect(await list.handleDisableType(type())).toBe(true)
    expect(deleteDictType).toHaveBeenCalledWith('t1')
    expect(list.currentType.value).toBeNull()

    expect(await list.handleEnableType(type({ status: 'INACTIVE' }))).toBe(true)
    expect(enableDictType).toHaveBeenCalledWith('t1')

    await list.handleTypeSelect(type())
    expect(await list.handleDisableItem(item())).toBe(true)
    expect(deleteDictItem).toHaveBeenCalledWith('i1')
    expect(await list.handleEnableItem(item({ status: 'INACTIVE' }))).toBe(true)
    expect(enableDictItem).toHaveBeenCalledWith('i1')
    expect(onSuccess).toHaveBeenCalled()
  })

  it('reports load failures', async () => {
    const onError = vi.fn()
    const list = createList({
      getDictTypes: vi.fn(async () => { throw new Error('boom') }),
      getDictItems: vi.fn(async () => { throw new Error('boom') }),
      onError
    })
    await list.loadTypeList()
    expect(onError).toHaveBeenCalledWith('systemDicts.message.loadTypesFailed')
    await list.loadItemList('GENDER')
    expect(onError).toHaveBeenCalledWith('systemDicts.message.loadItemsFailed')
  })
})
