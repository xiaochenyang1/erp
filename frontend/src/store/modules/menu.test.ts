import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const getRuntimeMenuTree = vi.hoisted(() => vi.fn())

vi.mock('@/api/auth', () => ({
  getRuntimeMenuTree
}))

import { useMenuStore } from './menu'

describe('runtime menu store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getRuntimeMenuTree.mockReset()
  })

  it('treats a successful empty tree as loaded', async () => {
    getRuntimeMenuTree.mockResolvedValue([])
    const store = useMenuStore()

    await expect(store.loadMenus()).resolves.toBe(true)

    expect(store.loaded).toBe(true)
    expect(store.visiblePaths.size).toBe(0)
  })

  it('shares an in-flight load and keeps failure distinguishable', async () => {
    let resolveRequest: (value: []) => void = () => {}
    getRuntimeMenuTree.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve }))
    const store = useMenuStore()

    const first = store.loadMenus()
    const second = store.loadMenus()
    expect(getRuntimeMenuTree).toHaveBeenCalledTimes(1)
    resolveRequest([])
    await expect(Promise.all([first, second])).resolves.toEqual([true, true])

    getRuntimeMenuTree.mockRejectedValueOnce(new Error('unavailable'))
    store.reset()
    await expect(store.loadMenus()).resolves.toBe(false)
    expect(store.loaded).toBe(false)
  })

  it('discards a previous account menu response after reset', async () => {
    let resolveRequest: (value: Array<{ path: string }>) => void = () => {}
    getRuntimeMenuTree.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve }))
    const store = useMenuStore()

    const pending = store.loadMenus()
    store.reset()
    resolveRequest([{ path: '/finance/vouchers' }])

    await expect(pending).resolves.toBe(false)
    expect(store.loaded).toBe(false)
    expect(store.menuTree).toEqual([])
    expect(store.visiblePaths.size).toBe(0)
  })
})
