import { reactive, ref } from 'vue'

import type {
  DictItem,
  DictItemSaveRequest,
  DictType,
  DictTypeSaveRequest
} from '@/api/system'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface DictTypeFormState {
  id?: string | number
  code: string
  name: string
  status: string
  remark: string
}

export interface DictItemFormState {
  id?: string | number
  typeCode: string
  label: string
  value: string
  orderNum: number
  status: string
}

const emptyTypeForm = (): DictTypeFormState => ({
  id: undefined,
  code: '',
  name: '',
  status: 'ACTIVE',
  remark: ''
})

const emptyItemForm = (): DictItemFormState => ({
  id: undefined,
  typeCode: '',
  label: '',
  value: '',
  orderNum: 0,
  status: 'ACTIVE'
})

/**
 * Create/edit dialogs for dictionary types and items.
 * Element form validation stays on the page around submit handlers.
 */
export const useSystemDictForm = (
  t: Translate,
  options: {
    getDictType: (id: string | number) => Promise<DictType>
    createDictType: (data: DictTypeSaveRequest) => Promise<unknown>
    updateDictType: (id: string | number, data: DictTypeSaveRequest) => Promise<unknown>
    createDictItem: (data: DictItemSaveRequest) => Promise<unknown>
    updateDictItem: (id: string | number, data: DictItemSaveRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onTypeSubmitted?: () => void | Promise<void>
    onItemSubmitted?: () => void | Promise<void>
  }
) => {
  const typeDialogVisible = ref(false)
  const typeDialogTitle = ref('')
  const typeFormData = reactive<DictTypeFormState>(emptyTypeForm())

  const itemDialogVisible = ref(false)
  const itemDialogTitle = ref('')
  const itemFormData = reactive<DictItemFormState>(emptyItemForm())

  const submitLoading = ref(false)

  const resetTypeForm = () => {
    Object.assign(typeFormData, emptyTypeForm())
  }

  const resetItemForm = () => {
    Object.assign(itemFormData, emptyItemForm())
  }

  const handleAddType = () => {
    typeDialogTitle.value = t('systemDicts.dialog.addType')
    resetTypeForm()
    typeDialogVisible.value = true
  }

  const handleEditType = async (row: DictType) => {
    typeDialogTitle.value = t('systemDicts.dialog.editType')
    try {
      const res = await options.getDictType(row.id)
      Object.assign(typeFormData, {
        id: res.id,
        code: res.code,
        name: res.name,
        status: res.status,
        remark: res.remark || ''
      })
      typeDialogVisible.value = true
      return true
    } catch {
      options.onError?.(t('systemDicts.message.loadTypeDetailFailed'))
      return false
    }
  }

  const handleTypeSubmit = async () => {
    submitLoading.value = true
    try {
      const payload: DictTypeSaveRequest = {
        code: typeFormData.code,
        name: typeFormData.name,
        status: typeFormData.status,
        remark: typeFormData.remark
      }
      if (typeFormData.id != null && typeFormData.id !== '') {
        await options.updateDictType(typeFormData.id, payload)
        options.onSuccess?.(t('systemDicts.message.updateSuccess'))
      } else {
        await options.createDictType(payload)
        options.onSuccess?.(t('systemDicts.message.createSuccess'))
      }
      typeDialogVisible.value = false
      await options.onTypeSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemDicts.message.operationFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const handleAddItem = (typeCode: string) => {
    itemDialogTitle.value = t('systemDicts.dialog.addItem')
    resetItemForm()
    itemFormData.typeCode = typeCode
    itemDialogVisible.value = true
  }

  const handleEditItem = (row: DictItem) => {
    itemDialogTitle.value = t('systemDicts.dialog.editItem')
    Object.assign(itemFormData, {
      id: row.id,
      typeCode: row.typeCode,
      label: row.label,
      value: row.value,
      orderNum: row.orderNum,
      status: row.status
    })
    itemDialogVisible.value = true
  }

  const handleItemSubmit = async () => {
    submitLoading.value = true
    try {
      const payload: DictItemSaveRequest = {
        typeCode: itemFormData.typeCode,
        label: itemFormData.label,
        value: itemFormData.value,
        orderNum: itemFormData.orderNum,
        status: itemFormData.status
      }
      if (itemFormData.id != null && itemFormData.id !== '') {
        await options.updateDictItem(itemFormData.id, payload)
        options.onSuccess?.(t('systemDicts.message.updateSuccess'))
      } else {
        await options.createDictItem(payload)
        options.onSuccess?.(t('systemDicts.message.createSuccess'))
      }
      itemDialogVisible.value = false
      await options.onItemSubmitted?.()
      return true
    } catch {
      options.onError?.(t('systemDicts.message.operationFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    handleAddItem,
    handleAddType,
    handleEditItem,
    handleEditType,
    handleItemSubmit,
    handleTypeSubmit,
    itemDialogTitle,
    itemDialogVisible,
    itemFormData,
    resetItemForm,
    resetTypeForm,
    submitLoading,
    typeDialogTitle,
    typeDialogVisible,
    typeFormData
  }
}
