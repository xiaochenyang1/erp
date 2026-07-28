import { reactive, ref } from 'vue'

import type { Post, PostSaveRequest } from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface SystemPostFormState {
  id?: string
  deptId?: string
  code: string
  name: string
  orderNum: number
  status: string
  remark: string
}

const emptyForm = (): SystemPostFormState => ({
  id: undefined,
  deptId: undefined as string | undefined,
  code: '',
  name: '',
  orderNum: 0,
  status: 'ACTIVE',
  remark: ''
})

/**
 * Create/edit dialog for system posts.
 * Element form validation stays on the page around submit.
 */
export const useSystemPostForm = (
  t: Translate,
  options: {
    getPost: (id: string | number) => Promise<Post>
    createPost: (data: PostSaveRequest) => Promise<unknown>
    updatePost: (id: string | number, data: PostSaveRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const submitLoading = ref(false)
  const formData = reactive<SystemPostFormState>(emptyForm())

  const resetForm = () => {
    Object.assign(formData, emptyForm())
  }

  const handleAdd = () => {
    dialogTitle.value = t('systemPost.create')
    resetForm()
    dialogVisible.value = true
  }

  const handleEdit = async (row: Post) => {
    dialogTitle.value = t('systemPost.editTitle')
    try {
      const res = await options.getPost(row.id)
      Object.assign(formData, {
        id: res.id,
        deptId: res.deptId,
        code: res.code,
        name: res.name,
        orderNum: res.orderNum ?? 0,
        status: res.status || 'ACTIVE',
        remark: res.remark || ''
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemPost.message.detailLoadFailed'))
      return false
    }
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      const payload: PostSaveRequest = {
        deptId: formData.deptId,
        code: formData.code,
        name: formData.name,
        orderNum: formData.orderNum,
        status: formData.status,
        remark: formData.remark
      }
      if (formData.id) {
        await options.updatePost(formData.id, payload)
        options.onSuccess?.(t('systemPost.message.updated'))
      } else {
        await options.createPost(payload)
        options.onSuccess?.(t('systemPost.message.created'))
      }
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemPost.message.saveFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    dialogTitle,
    dialogVisible,
    formData,
    handleAdd,
    handleEdit,
    handleSubmit,
    resetForm,
    submitLoading
  }
}
