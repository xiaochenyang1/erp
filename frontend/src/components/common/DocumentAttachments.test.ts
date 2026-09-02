import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createI18n } from 'vue-i18n'

import type { Attachment } from '@/api/attachment'
import { documentAttachmentMessages } from '@/i18n/document-attachment'
import { useUserStore } from '@/store/modules/user'
import DocumentAttachments from './DocumentAttachments.vue'

const attachment = (overrides: Partial<Attachment> = {}): Attachment => ({
  id: '5',
  businessType: 'EXPENSE',
  businessId: '77',
  businessNo: 'EXP-0001',
  originalFilename: 'invoice.pdf',
  fileSize: 2048,
  contentType: 'application/pdf',
  checksumSha256: 'abc',
  createdTime: '2026-09-01 09:00:00',
  createdBy: '9501',
  ...overrides
})

const getAttachments = vi.fn(async (_params?: unknown) => ({ records: [attachment()], total: 1, current: 1, size: 100 }))

vi.mock('@/api/attachment', () => ({
  getAttachments: (params: unknown) => getAttachments(params as never),
  uploadAttachment: vi.fn(async () => attachment()),
  downloadAttachment: vi.fn(async () => new Blob(['x'])),
  deleteAttachment: vi.fn(async () => ({}))
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': documentAttachmentMessages['zh-CN'],
    'en-US': documentAttachmentMessages['en-US']
  }
})

const ALL_PERMISSIONS = ['system:attachment:view', 'system:attachment:manage', 'system:attachment:delete']

const grant = (permissions: string[] = ALL_PERMISSIONS) => {
  const store = useUserStore()
  store.permissions = permissions
  return store
}

const mountPanel = (props: Record<string, unknown> = {}) => mount(DocumentAttachments, {
  props: { businessType: 'EXPENSE', businessId: '77', ...props },
  global: { plugins: [ElementPlus, i18n] }
})

describe('DocumentAttachments', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getAttachments.mockClear()
  })

  it('loads the document attachments on mount', async () => {
    grant()
    const wrapper = mountPanel()
    await flushPromises()

    expect(getAttachments).toHaveBeenCalledWith({ businessType: 'EXPENSE', businessId: '77', pageNo: 1, pageSize: 100 })
    expect(wrapper.text()).toContain('invoice.pdf')
    expect(wrapper.text()).toContain('2.0 KB')
  })

  it('skips the request until the document has an id', async () => {
    grant()
    mountPanel({ businessId: null })
    await flushPromises()

    expect(getAttachments).not.toHaveBeenCalled()
  })

  it('reloads when the selected document changes', async () => {
    grant()
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.setProps({ businessId: '78' })
    await flushPromises()

    expect(getAttachments).toHaveBeenCalledTimes(2)
    expect(getAttachments).toHaveBeenLastCalledWith({ businessType: 'EXPENSE', businessId: '78', pageNo: 1, pageSize: 100 })
  })

  it('shows the gate warning only when the document requires an attachment', async () => {
    grant()
    const plain = mountPanel()
    await flushPromises()
    expect(plain.text()).not.toContain('必传')

    const gated = mountPanel({ required: true })
    await flushPromises()
    expect(gated.text()).toContain('必传')
    expect(gated.text()).toContain('提交/过账前必须至少上传一个附件')
  })

  it('hides upload and delete actions in readonly mode', async () => {
    grant()
    const wrapper = mountPanel({ readonly: true })
    await flushPromises()

    expect(wrapper.text()).not.toContain('上传附件')
    expect(wrapper.text()).toContain('下载')
    expect(wrapper.text()).not.toContain('删除')
  })

  it('emits the attachment count so hosts can gate their own actions', async () => {
    grant()
    const wrapper = mountPanel()
    await flushPromises()

    const changes = wrapper.emitted('change') || []
    expect(changes[changes.length - 1]).toEqual([1])
  })

  it('never requests the list without the attachment view permission', async () => {
    grant(['system:attachment:manage'])
    const wrapper = mountPanel()
    await flushPromises()

    expect(getAttachments).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('没有附件查看权限')
    expect(wrapper.text()).toContain('上传附件')
  })

  it('loads once the permissions arrive after mount', async () => {
    const store = grant([])
    const wrapper = mountPanel()
    await flushPromises()
    expect(getAttachments).not.toHaveBeenCalled()

    store.permissions = ALL_PERMISSIONS
    await flushPromises()

    expect(getAttachments).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('invoice.pdf')
  })

  it('hides upload without the attachment manage permission and delete without the delete permission', async () => {
    grant(['system:attachment:view'])
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).not.toContain('上传附件')
    expect(wrapper.text()).toContain('下载')
    expect(wrapper.text()).not.toContain('删除')
  })

  it('keeps delete available for a user holding only view and delete rights', async () => {
    grant(['system:attachment:view', 'system:attachment:delete'])
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).not.toContain('上传附件')
    expect(wrapper.text()).toContain('删除')
  })
})
