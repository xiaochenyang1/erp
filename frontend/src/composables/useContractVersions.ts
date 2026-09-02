import { computed, ref } from 'vue'

import {
  getContractVersion,
  getContractVersions,
  restoreContractVersion,
  type ContractVersionRecord
} from '@/api/contracts'

export interface ContractVersionDependencies {
  getVersion: typeof getContractVersion
  getVersions: typeof getContractVersions
  restoreVersion: typeof restoreContractVersion
}

export interface ContractVersionCallbacks {
  confirm: (message: string, title: string, options?: Record<string, unknown>) => Promise<unknown>
  onError: (messageKey: string) => void
  onSuccess: (messageKey: string) => void
  onRestored: () => Promise<void> | void
  t: (key: string, params?: Record<string, string | number>) => string
}

const defaultDependencies: ContractVersionDependencies = {
  getVersion: getContractVersion,
  getVersions: getContractVersions,
  restoreVersion: restoreContractVersion
}

/**
 * 合同版本历史：列表与单版本快照各自带请求序号，避免连续查看不同合同或版本时旧响应覆盖新数据。
 * 版本加载失败只清空版本区并提示，不阻塞合同详情本身；恢复按后端语义另建草稿，不原地改写当前合同，
 * 因此确认文案带版本号并要求 `contract:manage`。
 */
export const useContractVersions = (
  callbacks: ContractVersionCallbacks,
  dependencies: ContractVersionDependencies = defaultDependencies
) => {
  const versions = ref<ContractVersionRecord[]>([])
  const versionsLoading = ref(false)
  const snapshot = ref<ContractVersionRecord | null>(null)
  const snapshotVisible = ref(false)
  const snapshotLoading = ref(false)
  const restoring = ref(false)
  let listToken = 0
  let snapshotToken = 0

  const snapshotTitle = computed(() => (
    snapshot.value
      ? callbacks.t('contractPage.versionSnapshotTitle', { versionNo: snapshot.value.versionNo })
      : callbacks.t('contractPage.versionSnapshot')
  ))

  const loadVersions = async (contractId: string | number) => {
    const token = ++listToken
    versionsLoading.value = true
    try {
      const rows = await dependencies.getVersions(contractId)
      if (token !== listToken) {
        return false
      }
      versions.value = rows || []
      return true
    } catch {
      if (token === listToken) {
        versions.value = []
        callbacks.onError('contractPage.message.versionsFailed')
      }
      return false
    } finally {
      if (token === listToken) {
        versionsLoading.value = false
      }
    }
  }

  const clearVersions = () => {
    listToken += 1
    snapshotToken += 1
    versions.value = []
    versionsLoading.value = false
    snapshot.value = null
    snapshotVisible.value = false
    snapshotLoading.value = false
  }

  const closeSnapshot = () => {
    snapshotToken += 1
    snapshotVisible.value = false
    snapshot.value = null
    snapshotLoading.value = false
  }

  const openSnapshot = async (contractId: string | number, row: ContractVersionRecord) => {
    const token = ++snapshotToken
    snapshot.value = null
    snapshotLoading.value = true
    snapshotVisible.value = true
    try {
      const record = await dependencies.getVersion(contractId, row.id)
      if (token !== snapshotToken) {
        return false
      }
      snapshot.value = record
      return true
    } catch {
      if (token === snapshotToken) {
        snapshotVisible.value = false
        callbacks.onError('contractPage.message.versionSnapshotFailed')
      }
      return false
    } finally {
      if (token === snapshotToken) {
        snapshotLoading.value = false
      }
    }
  }

  const restore = async (contractId: string | number, row: ContractVersionRecord) => {
    try {
      await callbacks.confirm(
        callbacks.t('contractPage.message.confirmRestoreVersion', { versionNo: row.versionNo }),
        callbacks.t('contractPage.message.prompt'),
        { type: 'warning' }
      )
    } catch {
      return false
    }
    restoring.value = true
    try {
      await dependencies.restoreVersion(contractId, row.id)
      callbacks.onSuccess('contractPage.message.restored')
      closeSnapshot()
      await callbacks.onRestored()
      return true
    } catch {
      callbacks.onError('contractPage.message.restoreFailed')
      return false
    } finally {
      restoring.value = false
    }
  }

  return {
    clearVersions,
    closeSnapshot,
    loadVersions,
    openSnapshot,
    restore,
    restoring,
    snapshot,
    snapshotLoading,
    snapshotTitle,
    snapshotVisible,
    versions,
    versionsLoading
  }
}
