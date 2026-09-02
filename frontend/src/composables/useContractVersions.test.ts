import { describe, expect, it, vi } from 'vitest'

import type { ContractVersionRecord } from '@/api/contracts'
import { useContractVersions } from './useContractVersions'

const version = (overrides: Partial<ContractVersionRecord> = {}): ContractVersionRecord => ({
  id: '11',
  contractId: '7',
  versionNo: 2,
  eventType: 'UPDATE',
  status: 'DRAFT',
  header: {
    contractNo: 'CT-2026-0001',
    contractType: 'SALES',
    customerId: '31',
    contractName: 'Annual supply',
    signedDate: '2026-01-05',
    effectiveFrom: '2026-01-10',
    effectiveTo: '2026-12-31',
    totalAmount: 1200,
    remark: 'note'
  },
  lines: [{ lineNo: 1, productId: '41', quantity: 10, fulfilledQuantity: 2, unitPrice: 120, amount: 1200 }],
  changedFields: ['totalAmount'],
  createdBy: '9501',
  createdTime: '2026-02-01 09:30:00',
  ...overrides
})

const setup = (overrides: Record<string, unknown> = {}) => {
  const getVersions = vi.fn(async () => [version()])
  const getVersion = vi.fn(async () => version({ versionNo: 3 }))
  const restoreVersion = vi.fn(async () => ({}))
  const confirm = vi.fn(async () => true)
  const onError = vi.fn()
  const onSuccess = vi.fn()
  const onRestored = vi.fn(async () => {})
  const t = vi.fn((key: string, params?: Record<string, string | number>) => (
    params ? `${key}:${Object.values(params).join(',')}` : key
  ))
  const dependencies = { getVersion, getVersions, restoreVersion, ...overrides } as never
  const callbacks = { confirm, onError, onSuccess, onRestored, t, ...overrides } as never
  return {
    confirm,
    getVersion,
    getVersions,
    onError,
    onRestored,
    onSuccess,
    restoreVersion,
    versions: useContractVersions(callbacks, dependencies)
  }
}

describe('contract versions', () => {
  it('loads the version list for a contract', async () => {
    const { getVersions, versions } = setup()

    const loaded = await versions.loadVersions('7')

    expect(loaded).toBe(true)
    expect(getVersions).toHaveBeenCalledWith('7')
    expect(versions.versions.value).toHaveLength(1)
    expect(versions.versionsLoading.value).toBe(false)
  })

  it('reports a list failure and clears versions without blocking the detail dialog', async () => {
    const { onError, versions } = setup({
      getVersions: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    const loaded = await versions.loadVersions('7')

    expect(loaded).toBe(false)
    expect(versions.versions.value).toEqual([])
    expect(onError).toHaveBeenCalledWith('contractPage.message.versionsFailed')
  })

  it('ignores a stale version list response', async () => {
    let resolveFirst: ((rows: ContractVersionRecord[]) => void) | undefined
    const getVersions = vi
      .fn()
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve as (rows: ContractVersionRecord[]) => void
      }))
      .mockImplementationOnce(async () => [version({ id: '22', versionNo: 9 })])
    const { versions } = setup({ getVersions })

    const first = versions.loadVersions('7')
    const second = versions.loadVersions('8')
    resolveFirst?.([version({ id: '11', versionNo: 1 })])
    await Promise.all([first, second])

    expect(versions.versions.value.map((row) => row.versionNo)).toEqual([9])
    expect(versions.versionsLoading.value).toBe(false)
  })

  it('opens a version snapshot through the detail endpoint', async () => {
    const { getVersion, versions } = setup()

    const opened = await versions.openSnapshot('7', version())

    expect(opened).toBe(true)
    expect(getVersion).toHaveBeenCalledWith('7', '11')
    expect(versions.snapshotVisible.value).toBe(true)
    expect(versions.snapshot.value?.versionNo).toBe(3)
    expect(versions.snapshotTitle.value).toBe('contractPage.versionSnapshotTitle:3')
    expect(versions.snapshotLoading.value).toBe(false)
  })

  it('closes the snapshot dialog when its request fails', async () => {
    const { onError, versions } = setup({
      getVersion: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    const opened = await versions.openSnapshot('7', version())

    expect(opened).toBe(false)
    expect(versions.snapshotVisible.value).toBe(false)
    expect(versions.snapshot.value).toBeNull()
    expect(onError).toHaveBeenCalledWith('contractPage.message.versionSnapshotFailed')
  })

  it('ignores a stale snapshot response', async () => {
    let resolveFirst: ((record: ContractVersionRecord) => void) | undefined
    const getVersion = vi
      .fn()
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve as (record: ContractVersionRecord) => void
      }))
      .mockImplementationOnce(async () => version({ versionNo: 5 }))
    const { versions } = setup({ getVersion })

    const first = versions.openSnapshot('7', version())
    const second = versions.openSnapshot('7', version({ id: '12' }))
    resolveFirst?.(version({ versionNo: 1 }))
    await Promise.all([first, second])

    expect(versions.snapshot.value?.versionNo).toBe(5)
  })

  it('confirms with the version number before restoring and reloads afterwards', async () => {
    const { confirm, onRestored, onSuccess, restoreVersion, versions } = setup()

    const restored = await versions.restore('7', version())

    expect(restored).toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      'contractPage.message.confirmRestoreVersion:2',
      'contractPage.message.prompt',
      expect.objectContaining({ type: 'warning' })
    )
    expect(restoreVersion).toHaveBeenCalledWith('7', '11')
    expect(onSuccess).toHaveBeenCalledWith('contractPage.message.restored')
    expect(onRestored).toHaveBeenCalledTimes(1)
    expect(versions.restoring.value).toBe(false)
  })

  it('keeps the version when the confirm dialog is cancelled', async () => {
    const { onRestored, restoreVersion, versions } = setup({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      })
    })

    const restored = await versions.restore('7', version())

    expect(restored).toBe(false)
    expect(restoreVersion).not.toHaveBeenCalled()
    expect(onRestored).not.toHaveBeenCalled()
  })

  it('surfaces a restore failure without reloading the list', async () => {
    const { onError, onRestored, versions } = setup({
      restoreVersion: vi.fn(async () => {
        throw new Error('boom')
      })
    })

    const restored = await versions.restore('7', version())

    expect(restored).toBe(false)
    expect(onError).toHaveBeenCalledWith('contractPage.message.restoreFailed')
    expect(onRestored).not.toHaveBeenCalled()
    expect(versions.restoring.value).toBe(false)
  })

  it('drops every version and snapshot when the detail dialog closes', async () => {
    const { versions } = setup()
    await versions.loadVersions('7')
    await versions.openSnapshot('7', version())

    versions.clearVersions()

    expect(versions.versions.value).toEqual([])
    expect(versions.snapshot.value).toBeNull()
    expect(versions.snapshotVisible.value).toBe(false)
  })

  it('ignores an in-flight snapshot that resolves after the dialog closed', async () => {
    let resolveSnapshot: ((record: ContractVersionRecord) => void) | undefined
    const getVersion = vi.fn(() => new Promise((resolve) => {
      resolveSnapshot = resolve as (record: ContractVersionRecord) => void
    }))
    const { versions } = setup({ getVersion })

    const pending = versions.openSnapshot('7', version())
    versions.closeSnapshot()
    resolveSnapshot?.(version({ versionNo: 4 }))
    await pending

    expect(versions.snapshot.value).toBeNull()
    expect(versions.snapshotVisible.value).toBe(false)
  })
})
