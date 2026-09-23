<template>
  <el-row :gutter="20">
    <el-col :span="12">
      <el-form-item prop="currencyCode" :label="t('documentCurrency.currency')" :rules="currencyRules">
        <el-select
          :model-value="currencyCode"
          :disabled="disabled || metadataLoading"
          :loading="metadataLoading"
          filterable
          style="width: 100%"
          @change="changeCurrency"
        >
          <el-option
              v-for="currency in currencyOptions"
              :key="currency.currencyCode"
              :value="currency.currencyCode"
              :label="`${currency.currencyCode} ${currency.currencyName}`"
              :disabled="currency.status !== 'ENABLED'"
          />
        </el-select>
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item prop="exchangeRate" :label="t('documentCurrency.rate')" :rules="rateRules">
        <el-input :model-value="exchangeRate == null ? '' : String(exchangeRate)" readonly>
          <template v-if="!disabled" #append>
            <el-button :loading="rateLoading" :disabled="!currencyCode" @click="refreshRate()">
              {{ t('documentCurrency.refresh') }}
            </el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-col>
    <el-col v-if="error" :span="24">
      <el-alert :title="error" type="warning" :closable="false" show-icon>
        <el-button link type="primary" @click="retry">{{ t('documentCurrency.retry') }}</el-button>
      </el-alert>
    </el-col>
    <el-col :span="24" class="currency-hint">
      <span v-if="baseCurrency" class="base-currency">{{ t('documentCurrency.base', { currency: baseCurrency }) }}</span>
      <span v-if="!disabled">{{ t('documentCurrency.hint') }}</span>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormItemRule } from 'element-plus'
import { getBaseCurrency, getCurrencies, getExchangeRates, type Currency } from '@/api/finance'

const props = withDefaults(defineProps<{
  currencyCode?: string
  exchangeRate?: number
  businessDate?: string
  active?: boolean
  disabled?: boolean
}>(), { active: true, disabled: false })
const emit = defineEmits<{
  'update:currencyCode': [value: string | undefined]
  'update:exchangeRate': [value: number | undefined]
}>()
const { t } = useI18n()
const baseCurrency = ref('')
const currencies = ref<Currency[]>([])
const metadataLoading = ref(false)
const rateLoading = ref(false)
const error = ref('')
let metadataRequest: Promise<void> | undefined
let rateRequestId = 0
let disposed = false

const currencyOptions = computed(() => {
  const options = currencies.value.filter(item => item.status === 'ENABLED' || item.currencyCode === props.currencyCode)
  if (props.currencyCode && !options.some(item => item.currencyCode === props.currencyCode)) {
    return [...options, { currencyCode: props.currencyCode, currencyName: '', status: 'DISABLED' }]
  }
  return options
})
const currencyRules = computed<FormItemRule[]>(() => [{
  required: true, message: t('documentCurrency.currencyRequired'), trigger: 'change'
}])
const rateRules = computed<FormItemRule[]>(() => [{
  required: true,
  type: 'number',
  min: Number.MIN_VALUE,
  message: t('documentCurrency.rateRequired'),
  trigger: 'change'
}])

function initializeCurrency() {
  if (!disposed && props.active && !props.disabled && !props.currencyCode && baseCurrency.value) {
    emit('update:currencyCode', baseCurrency.value)
    emit('update:exchangeRate', 1)
  }
}

async function loadMetadata() {
  if (metadataRequest) return metadataRequest
  metadataLoading.value = true
  metadataRequest = (async () => {
    try {
      const [base, available] = await Promise.all([getBaseCurrency(), getCurrencies()])
      if (disposed) return
      baseCurrency.value = base
      currencies.value = available
      error.value = ''
      initializeCurrency()
    } catch {
      if (!disposed && props.active) error.value = t('documentCurrency.loadFailed')
    } finally {
      metadataLoading.value = false
      metadataRequest = undefined
    }
  })()
  return metadataRequest
}

async function refreshRate() {
  if (!props.active || props.disabled || !props.currencyCode) return
  const requestId = ++rateRequestId
  const currency = props.currencyCode
  const businessDate = props.businessDate
  const isCurrent = () => !disposed && props.active && !props.disabled
    && requestId === rateRequestId && currency === props.currencyCode && businessDate === props.businessDate
  emit('update:exchangeRate', undefined)
  rateLoading.value = true
  error.value = ''
  try {
    if (!baseCurrency.value) await loadMetadata()
    if (!isCurrent()) return
    if (!baseCurrency.value) {
      error.value = t('documentCurrency.loadFailed')
      return
    }
    if (currency === baseCurrency.value) {
      emit('update:exchangeRate', 1)
      return
    }
    if (!businessDate) {
      error.value = t('documentCurrency.dateRequired')
      return
    }
    const rates = await getExchangeRates({ from: currency, to: baseCurrency.value })
    if (!isCurrent()) return
    const valid = rates.filter(rate => rate.status === 'ENABLED'
      && rate.fromCurrencyCode === currency && rate.toCurrencyCode === baseCurrency.value
      && rate.effectiveFrom <= businessDate && (!rate.effectiveTo || rate.effectiveTo >= businessDate)
      && Number.isFinite(Number(rate.rate)) && Number(rate.rate) > 0)
      .sort((left, right) => right.effectiveFrom.localeCompare(left.effectiveFrom))[0]
    if (!valid) {
      error.value = t('documentCurrency.missingRate')
      return
    }
    emit('update:exchangeRate', Number(valid.rate))
  } catch {
    if (isCurrent()) error.value = t('documentCurrency.loadFailed')
  } finally {
    if (isCurrent()) rateLoading.value = false
  }
}

async function changeCurrency(currency: string) {
  emit('update:currencyCode', currency)
  emit('update:exchangeRate', undefined)
  await nextTick()
  await refreshRate()
}

async function retry() {
  await loadMetadata()
  await nextTick()
  if (props.exchangeRate == null) await refreshRate()
}

// Only explicit edits refresh a rate. Loading an existing document preserves its snapshot.
watch(() => [props.active, props.disabled, props.currencyCode, props.businessDate], () => {
  rateRequestId++
  rateLoading.value = false
  error.value = ''
  initializeCurrency()
}, { flush: 'sync' })
watch(() => props.active, active => {
  if (active && !props.disabled) void loadMetadata()
}, { immediate: true })
watch(() => props.disabled, disabled => {
  if (!disabled && props.active) void loadMetadata()
})
onBeforeUnmount(() => { disposed = true; rateRequestId++ })
defineExpose({ refreshRate })
</script>

<style scoped>
.currency-hint { color: var(--el-text-color-secondary); font-size: 12px; margin-bottom: 16px; }
.base-currency { margin-right: 16px; }
</style>
