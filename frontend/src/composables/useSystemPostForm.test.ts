import { describe, expect, it, vi } from 'vitest'

import type { Post } from '@/api/system'
import { useSystemPostForm } from './useSystemPostForm'

const t = (key: string) => key

const detail = {
  id: 'p1',
  deptId: '1',
  code: 'ENG',
  name: '工程师',
  orderNum: 2,
  status: 'ACTIVE',
  remark: '备注'
} as unknown as Post

const createForm = (overrides: Partial<Parameters<typeof useSystemPostForm>[1]> = {}) =>
  useSystemPostForm(t, {
    getPost: vi.fn(async () => detail),
    createPost: vi.fn(async () => ({})),
    updatePost: vi.fn(async () => ({})),
    onError: vi.fn(),
    onSuccess: vi.fn(),
    ...overrides
  })

describe('system post form', () => {
  it('opens create and edit dialogs', async () => {
    const form = createForm()
    form.handleAdd()
    expect(form.dialogVisible.value).toBe(true)
    expect(form.dialogTitle.value).toBe('systemPost.create')
    expect(form.formData.code).toBe('')

    expect(await form.handleEdit({ id: 'p1' } as Post)).toBe(true)
    expect(form.dialogTitle.value).toBe('systemPost.editTitle')
    expect(form.formData).toMatchObject({
      id: 'p1',
      deptId: '1',
      code: 'ENG',
      name: '工程师',
      orderNum: 2,
      status: 'ACTIVE',
      remark: '备注'
    })
  })

  it('creates and updates posts', async () => {
    const createPost = vi.fn(async () => ({}))
    const updatePost = vi.fn(async () => ({}))
    const onSubmitted = vi.fn()
    const form = createForm({ createPost, updatePost, onSubmitted })

    form.handleAdd()
    form.formData.deptId = '1'
    form.formData.code = 'SALES'
    form.formData.name = '销售'
    form.formData.orderNum = 3
    form.formData.status = 'ACTIVE'
    expect(await form.handleSubmit()).toBe(true)
    expect(createPost).toHaveBeenCalledWith({
      deptId: '1',
      code: 'SALES',
      name: '销售',
      orderNum: 3,
      status: 'ACTIVE',
      remark: ''
    })

    await form.handleEdit({ id: 'p1' } as Post)
    form.formData.name = '高级工程师'
    expect(await form.handleSubmit()).toBe(true)
    expect(updatePost).toHaveBeenCalledWith('p1', expect.objectContaining({
      name: '高级工程师'
    }))
    expect(onSubmitted).toHaveBeenCalled()
  })

  it('reports detail and save failures', async () => {
    const onError = vi.fn()
    const form = createForm({
      getPost: vi.fn(async () => {
        throw new Error('boom')
      }),
      createPost: vi.fn(async () => {
        throw new Error('boom')
      }),
      onError
    })

    expect(await form.handleEdit({ id: 'p1' } as Post)).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemPost.message.detailLoadFailed')

    form.handleAdd()
    form.formData.code = 'X'
    form.formData.name = 'X'
    expect(await form.handleSubmit()).toBe(false)
    expect(onError).toHaveBeenCalledWith('systemPost.message.saveFailed')
  })
})
