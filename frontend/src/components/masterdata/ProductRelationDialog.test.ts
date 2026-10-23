import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { createI18n } from 'vue-i18n'

import {
  deleteCustomerProductRelation,
  deleteSupplierProductRelation,
  getCustomerProductRelations,
  getProducts,
  getSupplierProductRelations,
  saveCustomerProductRelation,
  saveSupplierProductRelation
} from '@/api/masterdata'
import { masterdataRelationMessages } from '@/i18n/masterdata-relation-pages'
import ProductRelationDialog from './ProductRelationDialog.vue'

vi.mock('@/api/masterdata', () => ({
  deleteCustomerProductRelation: vi.fn(),
  deleteSupplierProductRelation: vi.fn(),
  getCustomerProductRelations: vi.fn(),
  getProducts: vi.fn(),
  getSupplierProductRelations: vi.fn(),
  saveCustomerProductRelation: vi.fn(),
  saveSupplierProductRelation: vi.fn()
}))

const zh = masterdataRelationMessages['zh-CN'].productRelation

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: masterdataRelationMessages as unknown as Record<string, Record<string, unknown>>
})

const products = [
  { id: '11', productCode: 'P001', productName: 'Widget' },
  { id: '12', productCode: 'P002', productName: 'Gadget' }
]

const customerRelation = {
  id: 'r1',
  customerId: '77',
  productId: '11',
  productCode: 'P001',
  productName: 'Widget',
  customerProductCode: 'CUST-1',
  customerProductName: '客户侧名称',
  deliveryPreference: '分批交付',
  packagingPreference: '托盘',
  remark: 'note',
  status: 'ACTIVE' as const
}

const supplierRelation = {
  id: 'r9',
  supplierId: '88',
  productId: '11',
  productCode: 'P001',
  productName: 'Widget',
  supplierProductCode: 'SUP-1',
  supplierProductName: '供应商侧名称',
  minPurchaseQty: 5,
  leadTimeDays: 7,
  defaultSupplier: true,
  remark: 'note',
  status: 'ACTIVE' as const
}

beforeEach(() => {
  vi.mocked(getProducts).mockResolvedValue({ records: products, total: products.length } as never)
  vi.mocked(getCustomerProductRelations).mockResolvedValue([customerRelation] as never)
  vi.mocked(getSupplierProductRelations).mockResolvedValue([supplierRelation] as never)
  vi.mocked(saveCustomerProductRelation).mockResolvedValue(customerRelation as never)
  vi.mocked(saveSupplierProductRelation).mockResolvedValue(supplierRelation as never)
  vi.mocked(deleteCustomerProductRelation).mockResolvedValue(undefined as never)
  vi.mocked(deleteSupplierProductRelation).mockResolvedValue(undefined as never)
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
  vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
  vi.spyOn(ElMessage, 'success').mockImplementation(() => ({}) as never)
  vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({}) as never)
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.clearAllMocks()
  document.body.innerHTML = ''
})

const openDialog = async (props: Record<string, unknown> = {}) => {
  const wrapper = mount(ProductRelationDialog, {
    attachTo: document.body,
    props: { modelValue: true, mode: 'CUSTOMER', ownerId: '77', ownerLabel: 'C001 Acme', canWrite: true, ...props },
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

describe('ProductRelationDialog', () => {
  it('loads customer relations with the customer-only columns', async () => {
    const wrapper = await openDialog()

    expect(getCustomerProductRelations).toHaveBeenCalledWith('77')
    expect(getSupplierProductRelations).not.toHaveBeenCalled()
    expect(getProducts).toHaveBeenCalledWith({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    expect(wrapper.text()).toContain(zh.customerTitle)
    expect(wrapper.text()).toContain('C001 Acme')
    expect(wrapper.text()).toContain(zh.customerProductCode)
    expect(wrapper.text()).toContain(zh.deliveryPreference)
    expect(wrapper.text()).not.toContain(zh.minPurchaseQty)
    expect(wrapper.text()).toContain('CUST-1')
    expect(wrapper.text()).toContain('分批交付')
  })

  it('loads supplier relations with the sourcing columns', async () => {
    const wrapper = await openDialog({ mode: 'SUPPLIER', ownerId: '88', ownerLabel: 'S001 Globex' })

    expect(getSupplierProductRelations).toHaveBeenCalledWith('88')
    expect(getCustomerProductRelations).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain(zh.supplierTitle)
    expect(wrapper.text()).toContain(zh.supplierProductCode)
    expect(wrapper.text()).toContain(zh.minPurchaseQty)
    expect(wrapper.text()).toContain(zh.leadTimeDays)
    expect(wrapper.text()).not.toContain(zh.deliveryPreference)
    expect(wrapper.text()).toContain('SUP-1')
  })

  it('hides every write affordance without the update permission', async () => {
    const wrapper = await openDialog({ canWrite: false })

    expect(wrapper.text()).not.toContain(zh.add)
    expect(wrapper.text()).not.toContain(zh.actions)
    expect(wrapper.text()).not.toContain(zh.edit)
  })
  it('maps the shared editor onto the customer payload and drops blank text', async () => {
    const wrapper = await openDialog()

    await clickText(wrapper, zh.add)
    await wrapper.findComponent({ name: 'ElSelect' }).setValue('12')
    await wrapper.find(`input[placeholder="${zh.codePlaceholder}"]`).setValue('CUST-2')
    await wrapper.find(`input[placeholder="${zh.deliveryPreferencePlaceholder}"]`).setValue('  ')
    await clickText(wrapper, zh.save)

    expect(saveCustomerProductRelation).toHaveBeenCalledWith('77', {
      productId: '12',
      customerProductCode: 'CUST-2',
      customerProductName: undefined,
      deliveryPreference: undefined,
      packagingPreference: undefined,
      remark: undefined
    })
    expect(saveSupplierProductRelation).not.toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith(zh.message.saved)
    expect(getCustomerProductRelations).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain(zh.editorCreateTitle)
  })

  it('maps the shared editor onto the supplier payload with the sourcing fields', async () => {
    const wrapper = await openDialog({ mode: 'SUPPLIER', ownerId: '88' })

    await clickText(wrapper, zh.add)
    await wrapper.findComponent({ name: 'ElSelect' }).setValue('12')
    await wrapper.find(`input[placeholder="${zh.codePlaceholder}"]`).setValue('SUP-2')
    await wrapper.findAllComponents({ name: 'ElInputNumber' })[0].setValue(12)
    await wrapper.findAllComponents({ name: 'ElInputNumber' })[1].setValue(3)
    await wrapper.findComponent({ name: 'ElSwitch' }).setValue(true)
    await clickText(wrapper, zh.save)

    expect(saveSupplierProductRelation).toHaveBeenCalledWith('88', {
      productId: '12',
      supplierProductCode: 'SUP-2',
      supplierProductName: undefined,
      minPurchaseQty: 12,
      leadTimeDays: 3,
      defaultSupplier: true,
      remark: undefined
    })
    expect(saveCustomerProductRelation).not.toHaveBeenCalled()
  })

  it('pins the product and prefills the editor when an existing relation is edited', async () => {
    const wrapper = await openDialog()

    await clickText(wrapper, zh.edit)

    expect(wrapper.text()).toContain(zh.editorEditTitle)
    expect(wrapper.findComponent({ name: 'ElSelect' }).props('disabled')).toBe(true)
    expect((wrapper.find(`input[placeholder="${zh.codePlaceholder}"]`).element as HTMLInputElement).value).toBe('CUST-1')

    await clickText(wrapper, zh.save)
    expect(saveCustomerProductRelation).toHaveBeenCalledWith('77', expect.objectContaining({ productId: '11' }))
  })

  it('confirms the deletion and calls the mode-specific endpoint', async () => {
    const wrapper = await openDialog({ mode: 'SUPPLIER', ownerId: '88' })

    await clickText(wrapper, zh.delete)

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      zh.message.confirmDelete.replace('{product}', 'P001 Widget'),
      zh.message.prompt,
      { type: 'warning' }
    )
    expect(deleteSupplierProductRelation).toHaveBeenCalledWith('88', 'r9')
    expect(deleteCustomerProductRelation).not.toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith(zh.message.deleted)
  })

  it('reports a failed load instead of rendering a stale table', async () => {
    vi.mocked(getCustomerProductRelations).mockRejectedValueOnce(new Error('network'))
    const wrapper = await openDialog()

    expect(ElMessage.error).toHaveBeenCalledWith(zh.message.loadFailed)
    expect(wrapper.text()).toContain(zh.empty)
  })

  it('blocks the add button once every product is mapped', async () => {
    vi.mocked(getProducts).mockResolvedValue({ records: [products[0]], total: 1 } as never)
    const wrapper = await openDialog()

    expect(wrapper.text()).toContain(zh.productExhausted)
    const add = wrapper.findAll('button').find((item) => item.text() === zh.add)
    expect(add!.attributes('disabled')).toBeDefined()
  })

  it('closes through the footer without mutating anything', async () => {
    const wrapper = await openDialog()

    await clickText(wrapper, zh.close)

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(saveCustomerProductRelation).not.toHaveBeenCalled()
    expect(deleteCustomerProductRelation).not.toHaveBeenCalled()
  })
})
