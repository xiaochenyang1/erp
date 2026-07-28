import { reactive, ref } from 'vue'

import type { Dept, DeptSaveRequest } from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface SystemDeptFormState {
  parentId?: string | number
  name: string
  code: string
  manager: string
  contact: string
  orderNum: number
  status: string
}

const emptyForm = (): SystemDeptFormState => ({
  parentId: undefined,
  name: '',
  code: '',
  manager: '',
  contact: '',
  orderNum: 0,
  status: 'ACTIVE'
})

/**
 * Create/edit dialog for system departments.
 * Element form validation stays on the page around submit.
 */
export const useSystemDeptForm = (
  t: Translate,
  options: {
    getDept: (id: string | number) => Promise<Dept>
    createDept: (data: DeptSaveRequest) => Promise<unknown>
    updateDept: (id: string | number, data: DeptSaveRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const submitLoading = ref(false)
  const isEdit = ref(false)
  const currentId = ref<string | number>('')
  const formData = reactive<SystemDeptFormState>(emptyForm())

  const resetForm = () => {
    Object.assign(formData, emptyForm())
  }

  const handleCreate = (row: Dept | null) => {
    dialogTitle.value = t('systemDept.create')
    isEdit.value = false
    currentId.value = ''
    resetForm()
    if (row) {
      formData.parentId = row.id
    }
    dialogVisible.value = true
  }

  const handleEdit = async (row: Dept) => {
    dialogTitle.value = t('systemDept.editTitle')
    isEdit.value = true
    currentId.value = row.id
    try {
      const data = await options.getDept(row.id)
      Object.assign(formData, {
        parentId: data.parentId,
        name: data.name || '',
        code: data.code || '',
        manager: data.manager || '',
        contact: data.contact || '',
        orderNum: data.orderNum ?? 0,
        status: data.status || 'ACTIVE'
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemDept.message.detailLoadFailed'))
      return false
    }
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      const payload: DeptSaveRequest = {
        parentId: formData.parentId,
        name: formData.name,
        code: formData.code,
        manager: formData.manager,
        contact: formData.contact,
        orderNum: formData.orderNum,
        status: formData.status
      }
      if (isEdit.value) {
        await options.updateDept(currentId.value, payload)
      } else {
        await options.createDept(payload)
      }
      options.onSuccess?.(t('systemDept.message.saved'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemDept.message.saveFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    currentId,
    dialogTitle,
    dialogVisible,
    formData,
    handleCreate,
    handleEdit,
    handleSubmit,
    isEdit,
    resetForm,
    submitLoading
  }
}
