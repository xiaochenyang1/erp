import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import ElementPlus from 'element-plus'
import { i18n } from '@/i18n'
import { useUserStore } from '@/store/modules/user'
import MrpSuggestionTable from './MrpSuggestionTable.vue'

const line = { id: '1', productId: '2', productCode: 'C', productName: '原料', suggestionType: 'PURCHASE', status: 'OPEN',
  demandQty: 48, onHandQty: 10, openSupplyQty: 0, netQty: 38, warehouseId: '3' }
const render = async (permissions: string[], blocked = false) => {
  const pinia = createPinia()
  useUserStore(pinia).permissions = permissions
  const wrapper = mount(MrpSuggestionTable, { props: { lines: [line], blocked, convertingId: null }, global: { plugins: [pinia, i18n, ElementPlus] } })
  await flushPromises()
  expect(wrapper.text()).toContain('原料')
  return wrapper
}
describe('MRP conversion controls', () => {
  it('requires both MRP conversion and target creation permissions', async () => {
    const wrapper = await render(['inventory:mrp:convert'])
    expect(wrapper.text()).not.toContain('转采购')
    wrapper.unmount()
  })
  it('blocks conversion when the calculation has unresolved warehouse warnings', async () => {
    const wrapper = await render(['inventory:mrp:convert', 'purchase:order:create'], true)
    const button = wrapper.findAll('button').find(b => b.text() === '转采购')!
    expect(button.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('opens review of the precise suggestion before calling the conversion endpoint', async () => {
    const wrapper = await render(['inventory:mrp:convert', 'purchase:order:create'])
    await wrapper.findAll('button').find(b => b.text() === '转采购')!.trigger('click')
    expect(wrapper.emitted('convert')?.[0][0]).toEqual(line)
    wrapper.unmount()
  })
})
