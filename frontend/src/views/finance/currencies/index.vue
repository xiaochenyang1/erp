<template><div class="app-container"><el-card><template #header><div class="header"><span>{{ t('currencyPages.title') }}</span><el-button v-permission="'masterdata:currency:manage'" type="primary" @click="dialog=true">{{ t('currencyPages.add') }}</el-button></div></template><el-table :data="rates" v-loading="loading" border><el-table-column prop="fromCurrencyCode" :label="t('currencyPages.from')"/><el-table-column prop="toCurrencyCode" :label="t('currencyPages.to')"/><el-table-column prop="rate" :label="t('currencyPages.rate')"/><el-table-column prop="effectiveFrom" :label="t('currencyPages.start')"/><el-table-column prop="effectiveTo" :label="t('currencyPages.end')"/></el-table></el-card><el-dialog v-model="dialog" :title="t('currencyPages.add')" width="520px"><el-form :model="form" label-width="110px"><el-form-item :label="t('currencyPages.from')"><el-input v-model="form.fromCurrencyCode"/></el-form-item><el-form-item :label="t('currencyPages.to')"><el-input v-model="form.toCurrencyCode"/></el-form-item><el-form-item :label="t('currencyPages.rate')"><el-input-number v-model="form.rate" :min="0.000000000001" :precision="12"/></el-form-item><el-form-item :label="t('currencyPages.start')"><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD"/></el-form-item><el-form-item :label="t('currencyPages.end')"><el-date-picker v-model="form.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable/></el-form-item></el-form><template #footer><el-button @click="dialog=false">{{ t('currencyPages.cancel') }}</el-button><el-button type="primary" @click="save">{{ t('currencyPages.save') }}</el-button></template></el-dialog></div></template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { createExchangeRate, getExchangeRates, type ExchangeRate, type ExchangeRateRequest } from '@/api/finance'

const { t } = useI18n()
const rates = ref<ExchangeRate[]>([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive<ExchangeRateRequest>({
  fromCurrencyCode: 'USD', toCurrencyCode: 'CNY', rate: 1,
  effectiveFrom: new Date().toISOString().slice(0, 10)
})

async function load() {
  loading.value = true
  try { rates.value = await getExchangeRates() } finally { loading.value = false }
}
async function save() {
  try {
    await createExchangeRate(form)
    ElMessage.success(t('currencyPages.saved'))
    dialog.value = false
    await load()
  } catch { ElMessage.error(t('currencyPages.failed')) }
}
onMounted(load)
</script>
<style scoped>.header{display:flex;justify-content:space-between;align-items:center}</style>
