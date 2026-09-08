import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { createI18n } from 'vue-i18n'

import { deleteAttachment, downloadAttachment, getAttachments, uploadAttachment } from '@/api/attachment'
import { documentAttachmentMessages } from '@/i18n/document-attachment-pages'
import { downloadBlob } from '@/utils/download'
import DocumentAttachmentDialog from './DocumentAttachmentDialog.vue'

vi.mock('@/api/attachment', () => ({
  deleteAttachment: vi.fn(),
  downloadAttachment: vi.fn(),
  getAttachments: vi.fn(),
  uploadAttachment: vi.fn()
}))

vi.mock('@/utils/download', () => ({ downloadBlob: vi.fn() }))

const zh = documentAttachmentMessages['zh-CN'].documentAttachment

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: documentAttachmentMessages as unknown as Record<string, Record<string, unknown>>
})

const attachment = {
  id: 'a1',
  businessType: 'SALES_ORDER',
  businessId: '9007199254740993',
  originalFilename: 'order.pdf',
  fileSize: 2048,
  contentType: 'application/pdf',
  checksumSha256: 'sha',
  createdTime: '2026-09-01T10:00:00',
  createdBy: 'u1'
}

beforeEach(() => {
  vi.mocked(getAttachments).mockResolvedValue({ records: [attachment], total: 1, pageNo: 1, pageSize: 100 } as never)
  vi.mocked(uploadAttachment).mockResolvedValue(attachment as never)
  vi.mocked(downloadAttachment).mockResolvedValue(new Blob(['file']) as never)
  vi.mocked(deleteAttachment).mockResolvedValue(undefined as never)
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.clearAllMocks()
  document.body.innerHTML = ''
})

const openDialog = async (props: Record<string, unknown> = {}) => {
  const wrapper = mount(DocumentAttachmentDialog, {
    attachTo: document.body,
    props: {
      modelValue: true,
      businessType: 'SALES_ORDER',
      businessId: '9007199254740993',
      businessNo: 'SO-001',
      canUpload: true,
      canDelete: true,
      ...props
    },
    global: { plugins: [ElementPlus, i18n] }
  })
  await flushPromises()
  return wrapper
}

const clickText = async (wrapper: VueWrapper, label: string) => {
  const button = wrapper.findAll('button').find((item) => item.text() === label)
  expect(button, `button "${label}" is missing`).toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
}

describe('DocumentAttachmentDialog', () => {
  it('loads the document scope and labels the gated business type', async () => {
    const wrapper = await openDialog()

    expect(getAttachments).toHaveBeenCalledWith({
      businessType: 'SALES_ORDER',
      businessId: '9007199254740993',
      pageNo: 1,
      pageSize: 100
    })
    expect(wrapper.text()).toContain(zh.type.SALES_ORDER)
    expect(wrapper.text()).toContain('SO-001')
    expect(wrapper.text()).toContain('order.pdf')
    expect(wrapper.text()).toContain(zh.hint)
  })

  it('falls back to the raw type name when a gated type has no label yet', async () => {
    const wrapper = await openDialog({ businessType: 'FUTURE_DOCUMENT' })

    expect(wrapper.text()).toContain('FUTURE_DOCUMENT')
  })

  it('shows the document id when the row carries no document number', async () => {
    const wrapper = await openDialog({ businessNo: '' })

    expect(wrapper.text()).toContain('9007199254740993')
  })

  it('reloads on demand without reopening the dialog', async () => {
    const wrapper = await openDialog()

    await clickText(wrapper, zh.refresh)

    expect(getAttachments).toHaveBeenCalledTimes(2)
  })

  it('uploads the picked file against the open document', async () => {
    const wrapper = await openDialog()
    const file = new File(['data'], 'evidence.pdf')
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })

    await input.trigger('change')
    await flushPromises()

    expect(uploadAttachment).toHaveBeenCalledWith(file, 'SALES_ORDER', '9007199254740993', 'SO-001')
    expect(ElMessage.success).toHaveBeenCalledWith(zh.message.uploaded)
    expect(getAttachments).toHaveBeenCalledTimes(2)
  })

  it('downloads through the shared blob helper', async () => {
    const wrapper = await openDialog()

    await clickText(wrapper, zh.download)

    expect(downloadAttachment).toHaveBeenCalledWith('a1')
    expect(downloadBlob).toHaveBeenCalledWith(expect.any(Blob), 'order.pdf')
  })

  it('confirms before deleting an attachment', async () => {
    const wrapper = await openDialog()

    await clickText(wrapper, zh.delete)

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      zh.message.confirmDelete.replace('{filename}', 'order.pdf'),
      zh.message.prompt,
      { type: 'warning' }
    )
    expect(deleteAttachment).toHaveBeenCalledWith('a1')
    expect(ElMessage.success).toHaveBeenCalledWith(zh.message.deleted)
  })

  it('hides upload and delete for a view-only account', async () => {
    const wrapper = await openDialog({ canUpload: false, canDelete: false })

    expect(wrapper.text()).not.toContain(zh.upload)
    expect(wrapper.text()).not.toContain(zh.delete)
    expect(wrapper.text()).toContain(zh.readOnlyHint)
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
    expect(wrapper.text()).toContain(zh.download)
  })

  it('warns when the document has more attachments than the panel lists', async () => {
    vi.mocked(getAttachments).mockResolvedValue({ records: [attachment], total: 120, pageNo: 1, pageSize: 100 } as never)
    const wrapper = await openDialog()

    expect(wrapper.text()).toContain(zh.truncatedHint.replace('{count}', '1'))
  })

  it('reports a failed load and renders the empty state', async () => {
    vi.mocked(getAttachments).mockRejectedValueOnce(new Error('network'))
    const wrapper = await openDialog()

    expect(ElMessage.error).toHaveBeenCalledWith(zh.message.loadFailed)
    expect(wrapper.text()).toContain(zh.empty)
  })

  it('never queries the backend without a document id', async () => {
    await openDialog({ businessId: '' })

    expect(getAttachments).not.toHaveBeenCalled()
  })

  it('closes through the footer without touching the document', async () => {
    const wrapper = await openDialog()

    await clickText(wrapper, zh.close)

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(uploadAttachment).not.toHaveBeenCalled()
    expect(deleteAttachment).not.toHaveBeenCalled()
  })

  it('switches documents when the host page opens another row', async () => {
    const wrapper = await openDialog()

    await wrapper.setProps({ businessId: '77', businessNo: 'SO-002' })
    await flushPromises()

    expect(getAttachments).toHaveBeenLastCalledWith(expect.objectContaining({ businessId: '77' }))
    expect(wrapper.text()).toContain('SO-002')
  })
})
