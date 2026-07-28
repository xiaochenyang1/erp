import { reactive, ref } from 'vue'

import type { Dept, Post, PostQuery } from '@/api/system'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (
  message: string,
  title: string,
  options?: { type?: string }
) => Promise<unknown>

/**
 * Query, department options and enable/disable for system posts.
 * Create/edit dialog lives in useSystemPostForm.
 */
export const useSystemPostList = (
  t: Translate,
  options: {
    getPosts: (params: PostQuery) => Promise<PageResponse<Post>>
    getDeptTree: () => Promise<Dept[]>
    deletePost: (id: string | number) => Promise<unknown>
    enablePost: (id: string | number) => Promise<unknown>
    confirm: Confirm
    onError?: Notify
    onSuccess?: Notify
  }
) => {
  const queryForm = reactive({
    code: '',
    name: '',
    status: ''
  })

  const loading = ref(false)
  const tableData = ref<Post[]>([])
  const deptOptions = ref<Dept[]>([])
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const loadData = async () => {
    loading.value = true
    try {
      const res = await options.getPosts({
        pageNo: pagination.page,
        pageSize: pagination.size,
        code: queryForm.code || undefined,
        name: queryForm.name || undefined,
        status: queryForm.status || undefined
      })
      tableData.value = res.records || []
      pagination.total = res.total || 0
      return true
    } catch {
      options.onError?.(t('systemPost.message.loadFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  const loadDeptOptions = async () => {
    try {
      deptOptions.value = (await options.getDeptTree()) || []
      return true
    } catch {
      deptOptions.value = []
      options.onError?.(t('systemPost.message.optionsLoadFailed'))
      return false
    }
  }

  const handleQuery = async () => {
    pagination.page = 1
    return loadData()
  }

  const handleReset = async () => {
    queryForm.code = ''
    queryForm.name = ''
    queryForm.status = ''
    pagination.page = 1
    return loadData()
  }

  const handlePageChange = async (page: number) => {
    pagination.page = page
    return loadData()
  }

  const handleSizeChange = async (size: number) => {
    pagination.size = size
    pagination.page = 1
    return loadData()
  }

  const handleDisable = async (row: Post) => {
    try {
      await options.confirm(
        t('systemPost.message.disableConfirm', { name: row.name }),
        t('systemPost.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.deletePost(row.id)
      options.onSuccess?.(t('systemPost.message.disabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemPost.message.disableFailed'))
      return false
    }
  }

  const handleEnable = async (row: Post) => {
    try {
      await options.confirm(
        t('systemPost.message.enableConfirm', { name: row.name }),
        t('systemPost.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    try {
      await options.enablePost(row.id)
      options.onSuccess?.(t('systemPost.message.enabled'))
      await loadData()
      return true
    } catch {
      options.onError?.(t('systemPost.message.enableFailed'))
      return false
    }
  }

  return {
    deptOptions,
    handleDisable,
    handleEnable,
    handlePageChange,
    handleQuery,
    handleReset,
    handleSizeChange,
    loadData,
    loadDeptOptions,
    loading,
    pagination,
    queryForm,
    tableData
  }
}
