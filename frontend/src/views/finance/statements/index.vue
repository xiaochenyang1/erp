<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="往来类型">
          <el-select v-model="partnerType" style="width: 140px">
            <el-option label="客户" value="CUSTOMER" />
            <el-option label="供应商" value="SUPPLIER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="partnerType === 'CUSTOMER' ? '客户' : '供应商'">
          <el-select v-model="partnerId" filterable clearable style="width: 220px" placeholder="请选择">
            <el-option
              v-for="p in partners"
              :key="p.id"
              :label="p.name || p.customerName || p.supplierName"
              :value="String(p.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="statement" shadow="never" class="mt">
      <div class="summary">
        <div><b>{{ statement.partnerName }}</b>（{{ statement.partnerType }}）</div>
        <div>{{ statement.dateFrom }} ~ {{ statement.dateTo }}</div>
        <div>期初：{{ money(statement.openingBalance) }}</div>
        <div>增加：{{ money(statement.totalIncrease) }}</div>
        <div>减少：{{ money(statement.totalDecrease) }}</div>
        <div>期末：{{ money(statement.closingBalance) }}</div>
      </div>
      <el-table :data="statement.lines" border stripe>
        <el-table-column prop="bizDate" label="日期" width="120" />
        <el-table-column prop="docType" label="类型" width="120" />
        <el-table-column prop="docNo" label="单号" min-width="160" />
        <el-table-column prop="direction" label="方向" width="100" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="120" align="right">
          <template #default="{ row }">{{ money(row.balance) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPartnerStatement, type PartnerStatement } from '@/api/finance'
import { getCustomers, getSuppliers } from '@/api/masterdata'
import { formatLocalizedNumber } from '@/utils/locale'

const partnerType = ref('CUSTOMER')
const partnerId = ref('')
const range = ref<string[]>([])
const partners = ref<any[]>([])
const loading = ref(false)
const statement = ref<PartnerStatement>()

const money = (v: number) => formatLocalizedNumber(Number(v || 0), { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const loadPartners = async () => {
  if (partnerType.value === 'CUSTOMER') {
    const page = await getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    partners.value = (page.records || []).map((c: any) => ({ ...c, name: c.customerName || c.name }))
  } else {
    const page = await getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    partners.value = (page.records || []).map((s: any) => ({ ...s, name: s.supplierName || s.name }))
  }
  partnerId.value = ''
  statement.value = undefined
}

const loadData = async () => {
  if (!partnerId.value || !range.value || range.value.length !== 2) {
    ElMessage.warning('请选择往来单位和日期区间')
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
    ElMessage.error('查询对账单失败')
  } finally {
    loading.value = false
  }
}

watch(partnerType, loadPartners)
onMounted(async () => {
  const now = new Date()
  const from = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
  const to = now.toISOString().slice(0, 10)
  range.value = [from, to]
  await loadPartners()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.mt { margin-top: 0; }
.summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 12px; font-size: 13px; }
</style>
