import { describe, expect, it, vi } from 'vitest'

import type { Post } from '@/api/system'
import { useSystemPostList } from './useSystemPostList'

const t = (key: string, params?: Record<string, unknown>) =>
  params?.name != null ? `${key}:${params.name}` : key

const row = (overrides: Partial<Post> = {}) =>
  ({ id: 'p1', code: 'ENG', name: '工程师', status: 'ACTIVE', orderNum: 1, ...overrides }) as Post

const createList = (overrides: Partial<Parameters<typeof useSystemPostList>[1]> = {}) =>
  useSystemPostList(t, {
    getPosts: vi.fn(async () => ({ records: [row()], total: 1 } as any)),
    getDeptTree: vi.fn(async () => [{ id: '1', name: '总部' }] as any),
    deletePost: vi.fn(async () => ({})),
    enablePost: vi.fn(async () => ({})),
    confirm: vi.fn(async () => true),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system post list', () => {
  it('sends filled filters with pageNo/pageSize and resets paging on query', async () => {
    const getPosts = vi.fn(async () => ({ records: [], total: 4 } as any))
    const list = createList({ getPosts })

    list.pagination.page = 5
    list.queryForm.code = 'ENG'
    list.queryForm.name = '工程'
    list.queryForm.status = 'ACTIVE'
    await list.handleQuery()

    expect(getPosts).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      code: 'ENG',
      name: '工程',
      status: 'ACTIVE'
    })
    expect(list.pagination.total).toBe(4)
  })

  it('pages and resets independently', async () => {
    const getPosts = vi.fn(async () => ({ records: [], total: 0 } as any))
    const list = createList({ getPosts })

    await list.handlePageChange(3)
    expect(list.pagination.page).toBe(3)

    await list.handleSizeChange(50)
    expect(list.pagination.size).toBe(50)
    expect(list.pagination.page).toBe(1)

    list.queryForm.code = 'x'
    await list.handleReset()
    expect(list.queryForm.code).toBe('')
    expect(list.pagination.page).toBe(1)
  })

  it('loads department options and reports load failures', async () => {
    const list = createList()
    expect(await list.loadDeptOptions()).toBe(true)
    expect(list.deptOptions.value).toEqual([{ id: '1', name: '总部' }])

    const onError = vi.fn()
    const failing = createList({
      getPosts: vi.fn(async () => {
        throw new Error('boom')
      }),
      getDeptTree: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })
    expect(await failing.loadData()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemPost.message.loadFailed')
    expect(await failing.loadDeptOptions()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemPost.message.optionsLoadFailed')
  })

  it('disables and enables after confirmation', async () => {
    const deletePost = vi.fn(async () => ({}))
    const enablePost = vi.fn(async () => ({}))
    const confirm = vi.fn(async () => true)
    const onSuccess = vi.fn()
    const list = createList({ deletePost, enablePost, confirm, onSuccess })

    expect(await list.handleDisable(row())).toBe(true)
    expect(deletePost).toHaveBeenCalledWith('p1')
    expect(onSuccess).toHaveBeenCalledWith('systemPost.message.disabled')

    expect(await list.handleEnable(row({ status: 'INACTIVE' }))).toBe(true)
    expect(enablePost).toHaveBeenCalledWith('p1')
    expect(onSuccess).toHaveBeenCalledWith('systemPost.message.enabled')
  })

  it('aborts disable when confirmation is cancelled', async () => {
    const deletePost = vi.fn(async () => ({}))
    const list = createList({
      confirm: vi.fn(async () => {
        throw new Error('cancel')
      }),
      deletePost
    })
    expect(await list.handleDisable(row())).toBe(false)
    expect(deletePost).not.toHaveBeenCalled()
  })
})
