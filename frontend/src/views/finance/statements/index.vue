<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item :label="t('financeStatement.partnerType')">
          <el-select v-model="partnerType" style="width: 140px">
            <el-option :label="t('financeStatement.customer')" value="CUSTOMER" />
            <el-option :label="t('financeStatement.supplier')" value="SUPPLIER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="partnerTypeLabel(partnerType)">
          <el-select v-model="partnerId" filterable clearable style="width: 220px" :placeholder="t('financeStatement.selectPartner')">
            <el-option
              v-for="p in partners"
              :key="p.id"
              :label="p.name || p.customerName || p.supplierName"
              :value="String(p.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('financeStatement.period')">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('financeStatement.startDate')"
            :end-placeholder="t('financeStatement.endDate')"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">{{ t('financeStatement.search') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="statement" shadow="never" class="mt">
      <div class="summary">
        <div><b>{{ statement.partnerName }}</b> {{ t('financeStatement.partnerTypeValue', { type: partnerTypeLabel(statement.partnerType) }) }}</div>
        <div>{{ t('financeStatement.periodValue', { from: formatDate(statement.dateFrom), to: formatDate(statement.dateTo) }) }}</div>
        <div>{{ t('financeStatement.openingValue', { amount: money(statement.openingBalance) }) }}</div>
        <div>{{ t('financeStatement.increaseValue', { amount: money(statement.totalIncrease) }) }}</div>
        <div>{{ t('financeStatement.decreaseValue', { amount: money(statement.totalDecrease) }) }}</div>
        <div>{{ t('financeStatement.closingValue', { amount: money(statement.closingBalance) }) }}</div>
      </div>
      <el-table :data="statement.lines" border stripe>
        <el-table-column prop="bizDate" :label="t('financeStatement.date')" width="120">
          <template #default="{ row }">{{ formatDate(row.bizDate) }}</template>
        </el-table-column>
        <el-table-column prop="docType" :label="t('financeStatement.docType')" width="120">
          <template #default="{ row }">{{ documentTypeLabel(row.docType) }}</template>
        </el-table-column>
        <el-table-column prop="docNo" :label="t('financeStatement.docNo')" min-width="160" />
        <el-table-column prop="direction" :label="t('financeStatement.direction')" width="110">
          <template #default="{ row }">{{ directionLabel(row.direction) }}</template>
        </el-table-column>
        <el-table-column prop="amount" :label="t('financeStatement.amount')" width="140" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="balance" :label="t('financeStatement.balance')" width="140" align="right">
          <template #default="{ row }">{{ money(row.balance) }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('financeStatement.remark')" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getPartnerStatement, type PartnerStatement } from '@/api/finance'
import { getCustomers, getSuppliers } from '@/api/masterdata'
import {
  formatLocalizedCurrency,
  formatLocalizedDate,
  getBusinessMonthDateRange
} from '@/utils/locale'

const { t } = useI18n()
type PartnerType = 'CUSTOMER' | 'SUPPLIER'
const partnerType = ref<PartnerType>('CUSTOMER')
const partnerId = ref('')
const range = ref<string[]>([])
const partners = ref<any[]>([])
const loading = ref(false)
const statement = ref<PartnerStatement>()

const money = (value?: number) => formatLocalizedCurrency(Number(value ?? 0))
const formatDate = (value?: string) => formatLocalizedDate(value) || '-'
const partnerTypeLabel = (value: string) => {
  const keyMap: Record<string, string> = {
    CUSTOMER: 'financeStatement.customer',
    SUPPLIER: 'financeStatement.supplier'
  }
  return keyMap[value] ? t(keyMap[value]) : value
}
const documentTypeLabel = (value: string) => {
  const keyMap: Record<string, string> = {
    RECEIVABLE: 'financeStatement.document.receivable',
    RECEIPT: 'financeStatement.document.receipt',
    PAYABLE: 'financeStatement.document.payable',
    PAYMENT: 'financeStatement.document.payment'
  }
  return keyMap[value] ? t(keyMap[value]) : value
}
const directionLabel = (value: string) => {
  const keyMap: Record<string, string> = {
    INCREASE: 'financeStatement.directionValue.increase',
    DECREASE: 'financeStatement.directionValue.decrease'
  }
  return keyMap[value] ? t(keyMap[value]) : value
}

const loadPartners = async () => {
  try {
    if (partnerType.value === 'CUSTOMER') {
      const page = await getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      partners.value = (page.records || []).map((c: any) => ({ ...c, name: c.customerName || c.name }))
    } else {
      const page = await getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      partners.value = (page.records || []).map((s: any) => ({ ...s, name: s.supplierName || s.name }))
    }
  } catch {
    partners.value = []
    ElMessage.error(t('financeStatement.message.optionsLoadFailed'))
  }
  partnerId.value = ''
  statement.value = undefined
}

const loadData = async () => {
  if (!partnerId.value || !range.value || range.value.length !== 2) {
    ElMessage.warning(t('financeStatement.message.selectPartnerAndRange'))
    return
  }
  loading.value = true
  try {
    statement.value = await getPartnerStatement({
      partnerType: partnerType.value,
      partnerId: partnerId.value,
      dateFrom: range.value[0],
      dateTo: range.value[1]
    })
  } catch {
    ElMessage.error(t('financeStatement.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

watch(partnerType, loadPartners)
onMounted(async () => {
  range.value = getBusinessMonthDateRange()
  await loadPartners()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.mt { margin-top: 0; }
.summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 12px; font-size: 13px; }
</style>
