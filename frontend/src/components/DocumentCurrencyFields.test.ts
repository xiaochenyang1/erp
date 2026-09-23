import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createRequire } from 'node:module'
import type { FormInstance } from 'element-plus'
import { createI18n } from 'vue-i18n'
import { getBaseCurrency, getCurrencies, getExchangeRates, type ExchangeRate } from '@/api/finance'
import { currencyPageMessages } from '@/i18n/currency-pages'
import DocumentCurrencyFields from './DocumentCurrencyFields.vue'

// Node's external ESM loader does not unwrap async-validator's CJS default.
// Use Element Plus's Node entry so these tests exercise its real form validation.
const { default: ElementPlus, ElSelect } = createRequire(import.meta.url)('element-plus') as typeof import('element-plus')

vi.mock('@/api/finance', () => ({ getBaseCurrency: vi.fn(), getCurrencies: vi.fn(), getExchangeRates: vi.fn() }))
const wrappers: VueWrapper[] = []
const deferred = <T,>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  vi.mocked(getBaseCurrency).mockResolvedValue('USD')
  vi.mocked(getCurrencies).mockResolvedValue(['USD', 'CNY', 'EUR'].map(currencyCode => ({
    id: currencyCode, currencyCode, currencyName: currencyCode, status: 'ENABLED', decimalPlaces: 2
  })))
  vi.mocked(getExchangeRates).mockResolvedValue([])
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.resetAllMocks() })

function createForm(initial: { currencyCode?: string; exchangeRate?: number; disabled?: boolean } = {}) {
  const form = reactive({ currencyCode: initial.currencyCode, exchangeRate: initial.exchangeRate })
  const active = ref(true)
  const date = ref('2026-09-23')
  const field = ref<InstanceType<typeof DocumentCurrencyFields>>()
  const orderForm = ref<FormInstance>()
  const wrapper = mount(defineComponent({
    components: { DocumentCurrencyFields },
    setup: () => ({ form, active, date, field, orderForm, disabled: initial.disabled ?? false }),
    template: `<el-form ref="orderForm" :model="form">
      <DocumentCurrencyFields ref="field" v-model:currency-code="form.currencyCode"
        v-model:exchange-rate="form.exchangeRate" :business-date="date" :active="active" :disabled="disabled" />
    </el-form>`
  }), {
    global: { plugins: [ElementPlus, createI18n({ legacy: false, locale: 'zh-CN', messages: currencyPageMessages })] }
  })
  wrappers.push(wrapper)
  return { wrapper, form, active, date, field, orderForm }
}

const rate = (overrides: Partial<ExchangeRate> = {}): ExchangeRate => ({
  id: '1', fromCurrencyCode: 'CNY', toCurrencyCode: 'USD', rate: 0.14,
  effectiveFrom: '2026-09-01', status: 'ENABLED', ...overrides
})

describe('document currency fields', () => {
  it('uses the account-book base currency for new orders and after a reset', async () => {
    const { form } = createForm()
    await flushPromises()
    expect(form).toEqual({ currencyCode: 'USD', exchangeRate: 1 })
    form.currencyCode = 'CNY'
    form.exchangeRate = 0.14
    await flushPromises()
    form.currencyCode = undefined
    form.exchangeRate = undefined
    await flushPromises()
    expect(form).toEqual({ currencyCode: 'USD', exchangeRate: 1 })
  })

  it('preserves the saved snapshot when editing and never silently refreshes it', async () => {
    const { form, date } = createForm({ currencyCode: 'CNY', exchangeRate: 0.135 })
    await flushPromises()
    date.value = '2026-09-15'
    await flushPromises()
    expect(form.exchangeRate).toBe(0.135)
    expect(getExchangeRates).not.toHaveBeenCalled()
  })

  it('does not let delayed defaults overwrite an existing order loaded meanwhile', async () => {
    const base = deferred<string>()
    vi.mocked(getBaseCurrency).mockReturnValue(base.promise)
    const { form } = createForm()
    form.currencyCode = 'EUR'
    form.exchangeRate = 1.18
    base.resolve('USD')
    await flushPromises()
    expect(form).toEqual({ currencyCode: 'EUR', exchangeRate: 1.18 })
  })

  it('selects the latest enabled rate valid on the order date in the correct direction', async () => {
    const { wrapper, form } = createForm()
    await flushPromises()
    vi.mocked(getExchangeRates).mockResolvedValue([
      rate({ rate: 0.1 }), rate({ rate: 0.14, effectiveFrom: '2026-09-20' }),
      rate({ rate: 0.2, effectiveFrom: '2026-09-24' }),
      rate({ rate: 0.3, effectiveFrom: '2026-09-22', status: 'DISABLED' }),
      rate({ rate: 0.4, effectiveFrom: '2026-09-21', effectiveTo: '2026-09-22' }),
      rate({ rate: 7, effectiveFrom: '2026-09-23', fromCurrencyCode: 'USD', toCurrencyCode: 'CNY' })
    ])
    wrapper.findComponent(ElSelect).vm.$emit('change', 'CNY')
    await flushPromises()
    expect(getExchangeRates).toHaveBeenCalledWith({ from: 'CNY', to: 'USD' })
    expect(form).toEqual({ currencyCode: 'CNY', exchangeRate: 0.14 })
  })

  it('uses rate one for the actual base currency without querying rates', async () => {
    const { form, field } = createForm({ currencyCode: 'USD', exchangeRate: 7 })
    await flushPromises()
    await field.value!.refreshRate()
    await flushPromises()
    expect(form.exchangeRate).toBe(1)
    expect(getExchangeRates).not.toHaveBeenCalled()
  })

  it('clears an obsolete rate and prevents saving when the new date has no rate', async () => {
    const { form, field, orderForm, wrapper } = createForm({ currencyCode: 'CNY', exchangeRate: 0.14 })
    await flushPromises()
    await field.value!.refreshRate()
    await flushPromises()
    expect(form.exchangeRate).toBeUndefined()
    expect(wrapper.text()).toContain('当前订单日期没有有效汇率')
    await expect(orderForm.value!.validate()).rejects.toHaveProperty('exchangeRate')
  })

  it('ignores a stale rate response after the date changes and a newer request finishes', async () => {
    const first = deferred<ExchangeRate[]>()
    const { form, field, date } = createForm({ currencyCode: 'CNY', exchangeRate: 0.14 })
    await flushPromises()
    vi.mocked(getExchangeRates).mockReturnValueOnce(first.promise).mockResolvedValueOnce([rate({ rate: 0.15 })])
    const oldRequest = field.value!.refreshRate()
    date.value = '2026-09-24'
    await flushPromises()
    await field.value!.refreshRate()
    await flushPromises()
    first.resolve([rate({ rate: 0.12 })])
    await oldRequest
    await flushPromises()
    expect(form.exchangeRate).toBe(0.15)
  })

  it('does not apply a rate after the dialog closes', async () => {
    const pending = deferred<ExchangeRate[]>()
    const { form, field, active } = createForm({ currencyCode: 'CNY', exchangeRate: 0.14 })
    await flushPromises()
    vi.mocked(getExchangeRates).mockReturnValue(pending.promise)
    const request = field.value!.refreshRate()
    active.value = false
    await flushPromises()
    pending.resolve([rate({ rate: 0.12 })])
    await request
    expect(form.exchangeRate).toBeUndefined()
  })

  it('reports unavailable metadata without inventing a CNY default', async () => {
    vi.mocked(getBaseCurrency).mockRejectedValue(new Error('unavailable'))
    const { form, wrapper } = createForm()
    await flushPromises()
    expect(form.currencyCode).toBeUndefined()
    expect(form.exchangeRate).toBeUndefined()
    expect(wrapper.text()).toContain('无法读取账套币种或汇率')
  })

  it('view-only mode displays the saved currency without requesting reference permissions', async () => {
    const { form, wrapper } = createForm({ currencyCode: 'EUR', exchangeRate: 1.2, disabled: true })
    await flushPromises()
    expect(form.exchangeRate).toBe(1.2)
    expect(getBaseCurrency).not.toHaveBeenCalled()
    expect(wrapper.findComponent(ElSelect).props('disabled')).toBe(true)
  })
})
