import { computed, reactive, ref } from 'vue'

import type { ContractRecord, ContractSaveRequest } from '@/api/contracts'
import { formatBusinessDate } from '@/utils/locale'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void

export interface ContractFormLine {
  productId: string
  quantity: number
  unitPrice: number
}

export type ContractFormState = ContractSaveRequest & { lines: ContractFormLine[] }

const blankLine = (): ContractFormLine => ({ productId: '', quantity: 1, unitPrice: 0 })

/** A fresh contract is dated today in the business timezone and starts with one line. */
const blankForm = (): ContractFormState => ({
  contractType: 'SALES',
  customerId: '',
  supplierId: undefined,
  contractName: '',
  signedDate: formatBusinessDate(),
  effectiveFrom: formatBusinessDate(),
  effectiveTo: '',
  remark: '',
  lines: [blankLine()]
})

/**
 * Create and edit form for one contract, including the line editor.
 *
 * `ensureOptions` lets the page load its customer, supplier and product pickers
 * before the dialog opens, so the selects never render empty.
 */
export const useContractForm = (
  t: Translate,
  options: {
    getContract: (id: string | number) => Promise<ContractRecord>
    createContract: (data: ContractSaveRequest) => Promise<unknown>
    updateContract: (id: string, data: ContractSaveRequest) => Promise<unknown>
    ensureOptions?: () => unknown | Promise<unknown>
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
    onSubmitted?: () => void | Promise<void>
  }
) => {
  const formVisible = ref(false)
  const saving = ref(false)
  const editingId = ref<string | null>(null)
  const form = reactive<ContractFormState>(blankForm())

  const dialogTitle = computed(() => (editingId.value
    ? t('contractPage.editTitle')
    : t('contractPage.createTitle')))

  const resetForm = () => {
    editingId.value = null
    Object.assign(form, blankForm())
  }

  const addLine = () => form.lines.push(blankLine())

  /** The last line stays so the editor never renders an empty table. */
  const removeLine = (index: number) => {
    if (form.lines.length > 1) form.lines.splice(index, 1)
  }

  const openCreate = async () => {
    await options.ensureOptions?.()
    resetForm()
    formVisible.value = true
    return true
  }

  /** Editing refetches the detail so the form never edits a stale list row. */
  const openEdit = async (row: ContractRecord) => {
    await options.ensureOptions?.()
    try {
      const detail = await options.getContract(row.id)
      editingId.value = detail.id
      Object.assign(form, {
        contractType: detail.contractType,
        customerId: detail.customerId || '',
        supplierId: detail.supplierId,
        contractName: detail.contractName,
        signedDate: detail.signedDate,
        effectiveFrom: detail.effectiveFrom,
        effectiveTo: detail.effectiveTo || '',
        remark: detail.remark || '',
        lines: detail.lines.map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          unitPrice: line.unitPrice
        }))
      })
      formVisible.value = true
      return true
    } catch {
      options.onError?.(t('contractPage.message.detailFailed'))
      return false
    }
  }
  /** A contract needs a name, a partner, both dates and priced, non-empty lines. */
  const isComplete = () => Boolean(form.contractName.trim())
    && Boolean(form.customerId || form.supplierId)
    && Boolean(form.signedDate)
    && Boolean(form.effectiveFrom)
    && form.lines.length > 0
    && form.lines.every((line) => Boolean(line.productId) && line.quantity > 0 && line.unitPrice >= 0)

  const save = async () => {
    if (!isComplete()) {
      options.onWarning?.(t('contractPage.message.completeForm'))
      return false
    }
    saving.value = true
    try {
      /** Only the side matching the contract type is sent, so the other stays null. */
      const payload: ContractSaveRequest = {
        ...form,
        customerId: form.contractType === 'SALES' ? form.customerId : undefined,
        supplierId: form.contractType === 'PURCHASE' ? form.supplierId : undefined,
        lines: form.lines
      }
      if (editingId.value) {
        await options.updateContract(editingId.value, payload)
      } else {
        await options.createContract(payload)
      }
      options.onSuccess?.(t('contractPage.message.saved'))
      formVisible.value = false
      await options.onSubmitted?.()
      return true
    } catch {
      options.onError?.(t('contractPage.message.saveFailed'))
      return false
    } finally {
      saving.value = false
    }
  }

  return {
    addLine,
    dialogTitle,
    editingId,
    form,
    formVisible,
    openCreate,
    openEdit,
    removeLine,
    resetForm,
    save,
    saving
  }
}
