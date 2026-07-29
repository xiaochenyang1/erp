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
          <el-select v-model="partnerId" filterable clearable style="width: 220px" :placeholder="t('financeStatement.selectPartner')" @change="clearStatement">
            <el-option
              v-for="p in partners"
              :key="p.id"
              :label="partnerLabel(p)"
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
            @change="clearStatement"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">{{ t('financeStatement.search') }}</el-button>
          <el-button :disabled="!statement" @click="handlePrint">{{ t('financeStatement.print') }}</el-button>
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
import { onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getPartnerStatement } from '@/api/finance'
import { getCustomers, getSuppliers } from '@/api/masterdata'
import { getBusinessMonthDateRange } from '@/utils/locale'
import { printPartnerStatement } from '@/utils/bizPrint'
import { useFinanceStatementPresentation } from '@/composables/useFinanceStatementPresentation'
import { useFinanceStatementQuery } from '@/composables/useFinanceStatementQuery'

const { t } = useI18n()

const {
  directionLabel,
  documentTypeLabel,
  formatDate,
  money,
  partnerLabel,
  partnerTypeLabel,
  toPrintDto
} = useFinanceStatementPresentation(t)

const {
  clearStatement,
  handlePartnerTypeChange,
  loadData,
  loadPartners,
  loading,
  partnerId,
  partners,
  partnerType,
  range,
  statement
} = useFinanceStatementQuery(t, {
  getCustomers,
  getSuppliers,
  getPartnerStatement,
  getBusinessMonthDateRange,
  onError: (message) => ElMessage.error(message),
  onWarning: (message) => ElMessage.warning(message)
})

const handlePrint = () => {
  if (!statement.value) {
    ElMessage.warning(t('financeStatement.message.selectPartnerAndRange'))
    return false
  }
  try {
    printPartnerStatement(toPrintDto(statement.value))
    return true
  } catch {
    ElMessage.error(t('financeStatement.message.printLoadFailed'))
    return false
  }
}

watch(partnerType, (type) => {
  void handlePartnerTypeChange(type)
})
onMounted(async () => {
  await loadPartners()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.mt { margin-top: 0; }
.summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 12px; font-size: 13px; }
</style>
