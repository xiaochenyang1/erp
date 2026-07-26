import { computed, ref } from 'vue'

import type { DictItem, DictType, DictTypeQuery } from '@/api/system'
import type { PageResponse } from '@/types/common'
import { useSystemDictPresentation } from './useSystemDictPresentation'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

/**
 * Dictionary type list + item list, including enable/disable.
 * Create/edit dialogs live in useSystemDictForm.
 */
export const useSystemDictList = (
  t: Translate,
  options: {
    getDictTypes: (params: DictTypeQuery) => Promise<PageResponse<DictType>>
    getDictItems: (typeCode: string) => Promise<DictItem[]>
    deleteDictType: (id: string | number) => Promise<unknown>
    enableDictType: (id: string | number) => Promise<unknown>
    deleteDictItem: (id: string | number) => Promise<unknown>
    enableDictItem: (id: string | number) => Promise<unknown>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const { filterTypes } = useSystemDictPresentation(t)

  const typeLoading = ref(false)
  const typeList = ref<DictType[]>([])
  const currentType = ref<DictType | null>(null)
  const typeSearchText = ref('')

  const itemLoading = ref(false)
  const itemList = ref<DictItem[]>([])

  const filteredTypeList = computed(() =>
    filterTypes(typeList.value, typeSearchText.value)
  )

  const loadTypeList = async () => {
    typeLoading.value = true
    try {
      const res = await options.getDictTypes({ pageNo: 1, pageSize: 200 })
      typeList.value = res.records || []
    } catch {
      options.onError?.(t('systemDicts.message.loadTypesFailed'))
    } finally {
      typeLoading.value = false
    }
  }

  const loadItemList = async (typeCode: string) => {
    itemLoading.value = true
    try {
      itemList.value = (await options.getDictItems(typeCode)) || []
    } catch {
      options.onError?.(t('systemDicts.message.loadItemsFailed'))
    } finally {
      itemLoading.value = false
    }
  }

  const handleTypeSelect = async (row: DictType | null) => {
    currentType.value = row
    if (row) {
      await loadItemList(row.code)
    } else {
      itemList.value = []
    }
  }

  const refreshCurrentItems = async () => {
    if (currentType.value) {
      await loadItemList(currentType.value.code)
    }
  }

  const handleDisableType = async (row: DictType) => {
    try {
      await options.confirm(
        t('systemDicts.message.disableTypeConfirm', { name: row.name }),
        t('systemDicts.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.deleteDictType(row.id)
      options.onSuccess?.(t('systemDicts.message.disableSuccess'))
      await loadTypeList()
      if (currentType.value?.id === row.id) {
        currentType.value = null
        itemList.value = []
      }
      return true
    } catch {
      options.onError?.(t('systemDicts.message.disableFailed'))
      return false
    }
  }

  const handleEnableType = async (row: DictType) => {
    try {
      await options.confirm(
        t('systemDicts.message.enableTypeConfirm', { name: row.name }),
        t('systemDicts.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.enableDictType(row.id)
      options.onSuccess?.(t('systemDicts.message.enableSuccess'))
      await loadTypeList()
      return true
    } catch {
      options.onError?.(t('systemDicts.message.enableFailed'))
      return false
    }
  }

  const handleDisableItem = async (row: DictItem) => {
    try {
      await options.confirm(
        t('systemDicts.message.disableItemConfirm', { label: row.label }),
        t('systemDicts.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.deleteDictItem(row.id)
      options.onSuccess?.(t('systemDicts.message.disableSuccess'))
      await refreshCurrentItems()
      return true
    } catch {
      options.onError?.(t('systemDicts.message.disableFailed'))
      return false
    }
  }

  const handleEnableItem = async (row: DictItem) => {
    try {
      await options.confirm(
        t('systemDicts.message.enableItemConfirm', { label: row.label }),
        t('systemDicts.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.enableDictItem(row.id)
      options.onSuccess?.(t('systemDicts.message.enableSuccess'))
      await refreshCurrentItems()
      return true
    } catch {
      options.onError?.(t('systemDicts.message.enableFailed'))
      return false
    }
  }

  return {
    currentType,
    filteredTypeList,
    handleDisableItem,
    handleDisableType,
    handleEnableItem,
    handleEnableType,
    handleTypeSelect,
    itemList,
    itemLoading,
    loadItemList,
    loadTypeList,
    refreshCurrentItems,
    typeList,
    typeLoading,
    typeSearchText
  }
}
