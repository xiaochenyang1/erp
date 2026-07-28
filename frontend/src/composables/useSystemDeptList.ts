import { ref } from 'vue'

import type { Dept } from '@/api/system'

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
 * Department tree list with enable/disable.
 * Create/edit dialog lives in useSystemDeptForm.
 */
export const useSystemDeptList = (
  t: Translate,
  options: {
    getDeptTree: () => Promise<Dept[]>
    deleteDept: (id: string | number) => Promise<unknown>
    enableDept: (id: string | number) => Promise<unknown>
    buildParentTree: (data: Dept[]) => Dept[]
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const loading = ref(false)
  const tableData = ref<Dept[]>([])
  const deptTree = ref<Dept[]>([])

  const loadData = async () => {
    loading.value = true
    try {
      const data = (await options.getDeptTree()) || []
      tableData.value = data
      deptTree.value = options.buildParentTree(data)
      return true
    } catch {
      options.onError?.(t('systemDept.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const handleDisable = async (row: Dept) => {
    try {
      await options.confirm(
        t('systemDept.message.disableConfirm', { name: row.name }),
        t('systemDept.message.prompt'),
        {
          confirmButtonText: t('systemDept.message.confirm'),
          cancelButtonText: t('systemDept.message.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      await options.deleteDept(row.id)
      options.onSuccess?.(t('systemDept.message.disabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemDept.message.disableFailed'))
      return false
    }
  }

  const handleEnable = async (row: Dept) => {
    try {
      await options.confirm(
        t('systemDept.message.enableConfirm', { name: row.name }),
        t('systemDept.message.prompt'),
        {
          confirmButtonText: t('systemDept.message.confirm'),
          cancelButtonText: t('systemDept.message.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return false
    }
    try {
      await options.enableDept(row.id)
      options.onSuccess?.(t('systemDept.message.enabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemDept.message.enableFailed'))
      return false
    }
  }

  return {
    deptTree,
    handleDisable,
    handleEnable,
    loadData,
    loading,
    tableData
  }
}
