import { reactive, ref } from 'vue'

import type { Menu, MenuSaveRequest } from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface SystemMenuFormState {
  parentId?: string | number
  name: string
  path: string
  component: string
  icon: string
  orderNum: number
  type: string
  permission: string
  status: string
}

const emptyForm = (): SystemMenuFormState => ({
  parentId: undefined,
  name: '',
  path: '',
  component: '',
  icon: '',
  orderNum: 0,
  type: 'MENU',
  permission: '',
  status: 'ACTIVE'
})

/**
 * Create/edit dialog for system menus.
 * Element form validation stays on the page around submit.
 */
export const useSystemMenuForm = (
  t: Translate,
  options: {
    getMenu: (id: string | number) => Promise<Menu>
    createMenu: (data: MenuSaveRequest) => Promise<unknown>
    updateMenu: (id: string | number, data: MenuSaveRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<unknown>
  }
) => {
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const submitLoading = ref(false)
  const isEdit = ref(false)
  const currentId = ref<string | number>('')
  const formData = reactive<SystemMenuFormState>(emptyForm())

  const resetForm = () => {
    Object.assign(formData, emptyForm())
  }

  const handleCreate = (row: Menu | null) => {
    dialogTitle.value = t('systemMenu.create')
    isEdit.value = false
    currentId.value = ''
    resetForm()
    if (row) {
      formData.parentId = row.id
    }
    dialogVisible.value = true
  }

  const handleEdit = async (row: Menu) => {
    dialogTitle.value = t('systemMenu.editTitle')
    isEdit.value = true
    currentId.value = row.id
    try {
      const data = await options.getMenu(row.id)
      Object.assign(formData, {
        parentId: data.parentId,
        name: data.name || '',
        path: data.path || '',
        component: data.component || '',
        icon: data.icon || '',
        orderNum: data.orderNum ?? 0,
        type: data.type || 'MENU',
        permission: data.permission || '',
        status: data.status || 'ACTIVE'
      })
      dialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemMenu.message.detailLoadFailed'))
      return false
    }
  }

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      const payload: MenuSaveRequest = {
        parentId: formData.parentId,
        name: formData.name,
        path: formData.path,
        component: formData.component,
        icon: formData.icon,
        orderNum: formData.orderNum,
        type: formData.type,
        permission: formData.permission,
        status: formData.status
      }
      if (isEdit.value) {
        await options.updateMenu(currentId.value, payload)
      } else {
        await options.createMenu(payload)
      }
      options.onSuccess?.(t('systemMenu.message.saved'))
      dialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemMenu.message.saveFailed'))
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
