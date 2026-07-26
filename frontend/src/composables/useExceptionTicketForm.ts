import { computed, reactive, ref } from 'vue'

import type {
  ExceptionTicket,
  ExceptionTicketActionRequest,
  ExceptionTicketAssignRequest,
  ExceptionTicketCreateRequest
} from '@/api/exceptionTicket'
import { normalizeOptionalId } from './useExceptionTicketList'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export type ExceptionTicketActionMode = 'assign' | 'start' | 'resolve' | 'close'

export const toIsoDateTime = (value?: string) =>
  value ? value.replace(' ', 'T') : undefined

const emptyCreateForm = (): ExceptionTicketCreateRequest => ({
  category: 'GENERAL',
  priority: 'HIGH',
  title: '',
  description: '',
  sourceType: '',
  sourceId: '',
  sourceNo: '',
  sourceRoute: '',
  assigneeUserId: undefined,
  dueTime: ''
})

/**
 * Create dialog and lifecycle action dialog for exception tickets.
 * Element form validation stays on the page around handleCreate.
 */
export const useExceptionTicketForm = (
  t: Translate,
  options: {
    createTicket: (data: ExceptionTicketCreateRequest) => Promise<unknown>
    assignTicket: (id: string | number, data: ExceptionTicketAssignRequest) => Promise<unknown>
    startTicket: (id: string | number, data: ExceptionTicketActionRequest) => Promise<unknown>
    resolveTicket: (id: string | number, data: ExceptionTicketActionRequest) => Promise<unknown>
    closeTicket: (id: string | number, data: ExceptionTicketActionRequest) => Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const submitLoading = ref(false)
  const createDialogVisible = ref(false)
  const createForm = reactive<ExceptionTicketCreateRequest>(emptyCreateForm())

  const actionDialogVisible = ref(false)
  const actionMode = ref<ExceptionTicketActionMode>('assign')
  const actionTarget = ref<ExceptionTicket>()
  const actionForm = reactive({
    assigneeUserId: undefined as string | undefined,
    comment: ''
  })

  const actionDialogTitle = computed(() => {
    const titleMap: Record<ExceptionTicketActionMode, string> = {
      assign: t('exceptionTicket.dialog.assign'),
      start: t('exceptionTicket.dialog.start'),
      resolve: t('exceptionTicket.dialog.resolve'),
      close: t('exceptionTicket.dialog.close')
    }
    const ticketNo = actionTarget.value?.ticketNo ? ` - ${actionTarget.value.ticketNo}` : ''
    return `${titleMap[actionMode.value]}${ticketNo}`
  })

  const actionPlaceholder = computed(() => {
    const placeholderMap: Record<ExceptionTicketActionMode, string> = {
      assign: t('exceptionTicket.actionPlaceholders.assign'),
      start: t('exceptionTicket.actionPlaceholders.start'),
      resolve: t('exceptionTicket.actionPlaceholders.resolve'),
      close: t('exceptionTicket.actionPlaceholders.close')
    }
    return placeholderMap[actionMode.value]
  })

  const resetCreateForm = () => {
    Object.assign(createForm, emptyCreateForm())
  }

  const openCreateDialog = () => {
    resetCreateForm()
    createDialogVisible.value = true
  }

  const handleCreate = async () => {
    submitLoading.value = true
    try {
      await options.createTicket({
        ...createForm,
        dueTime: toIsoDateTime(createForm.dueTime),
        sourceType: createForm.sourceType?.trim() || undefined,
        sourceId: createForm.sourceId?.trim() || undefined,
        sourceNo: createForm.sourceNo?.trim() || undefined,
        sourceRoute: createForm.sourceRoute?.trim() || undefined,
        assigneeUserId: normalizeOptionalId(createForm.assigneeUserId),
        description: createForm.description?.trim() || undefined
      })
      options.onSuccess?.(t('exceptionTicket.message.created'))
      createDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('exceptionTicket.message.createFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  const openActionDialog = (mode: ExceptionTicketActionMode, row: ExceptionTicket) => {
    actionMode.value = mode
    actionTarget.value = row
    actionForm.assigneeUserId = row.assigneeUserId
    actionForm.comment = ''
    actionDialogVisible.value = true
  }

  const handleAction = async () => {
    if (!actionTarget.value) return false
    submitLoading.value = true
    try {
      const id = actionTarget.value.id
      const comment = actionForm.comment?.trim() || undefined
      if (actionMode.value === 'assign') {
        await options.assignTicket(id, {
          assigneeUserId: normalizeOptionalId(actionForm.assigneeUserId),
          comment
        })
      } else if (actionMode.value === 'start') {
        await options.startTicket(id, { comment })
      } else if (actionMode.value === 'resolve') {
        await options.resolveTicket(id, { comment })
      } else {
        await options.closeTicket(id, { comment })
      }
      options.onSuccess?.(t('exceptionTicket.message.actionSubmitted'))
      actionDialogVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('exceptionTicket.message.actionFailed'))
      return false
    } finally {
      submitLoading.value = false
    }
  }

  return {
    actionDialogTitle,
    actionDialogVisible,
    actionForm,
    actionMode,
    actionPlaceholder,
    actionTarget,
    createDialogVisible,
    createForm,
    handleAction,
    handleCreate,
    openActionDialog,
    openCreateDialog,
    resetCreateForm,
    submitLoading
  }
}
