import { ref } from 'vue'

import type { Menu } from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: {
    confirmButtonText?: string
    cancelButtonText?: string
    type?: string
  }
) => Promise<unknown>

/**
 * Menu tree list with enable/disable.
 * Create/edit dialog lives in useSystemMenuForm.
 */
export const useSystemMenuList = (
  t: Translate,
  options: {
    getMenuTree: () => Promise<Menu[]>
    deleteMenu: (id: string | number) => Promise<unknown>
    enableMenu: (id: string | number) => Promise<unknown>
    buildParentTree: (data: Menu[]) => Menu[]
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const tableData = ref<Menu[]>([])
  const menuTree = ref<Menu[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const data = (await options.getMenuTree()) || []
      tableData.value = data
      menuTree.value = options.buildParentTree(data)
      return true
    } catch {
      options.onError?.(t('systemMenu.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleDisable = async (row: Menu) => {
    try {
      await options.confirm(
        t('systemMenu.message.disableConfirm', { name: row.name }),
        t('systemMenu.message.prompt'),
        {
          confirmButtonText: t('systemMenu.message.confirm'),
          cancelButtonText: t('systemMenu.message.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      await options.deleteMenu(row.id)
      options.onSuccess?.(t('systemMenu.message.disabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemMenu.message.disableFailed'))
      return false
    }
  }

  const handleEnable = async (row: Menu) => {
    try {
      await options.confirm(
        t('systemMenu.message.enableConfirm', { name: row.name }),
        t('systemMenu.message.prompt'),
        {
          confirmButtonText: t('systemMenu.message.confirm'),
          cancelButtonText: t('systemMenu.message.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      await options.enableMenu(row.id)
      options.onSuccess?.(t('systemMenu.message.enabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemMenu.message.enableFailed'))
      return false
    }
  }

  return {
    handleDisable,
    handleEnable,
    loadData,
    loading,
    menuTree,
    tableData
  }
}
