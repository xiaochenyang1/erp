import { computed, type Ref } from 'vue'

import type { DictType } from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

/** Display helpers for dictionary types and items. */
export const useSystemDictPresentation = (t: Translate) => {
  const statusText = (status?: string) => {
    if (status === 'ACTIVE') return t('systemDicts.active')
    if (status === 'INACTIVE') return t('systemDicts.inactive')
    return status || ''
  }

  const statusType = (status?: string): TagType =>
    status === 'ACTIVE' ? 'success' : 'danger'

  const filterTypes = (types: DictType[], keyword: string) => {
    const text = keyword.trim().toLowerCase()
    if (!text) return types
    return types.filter(
      (item) =>
        item.code.toLowerCase().includes(text)
        || item.name.toLowerCase().includes(text)
    )
  }

  const useFilteredTypeList = (
    typeList: Ref<DictType[]>,
    typeSearchText: Ref<string>
  ) => computed(() => filterTypes(typeList.value, typeSearchText.value))

  return {
    filterTypes,
    statusText,
    statusType,
    useFilteredTypeList
  }
}
