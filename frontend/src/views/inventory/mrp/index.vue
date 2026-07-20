<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <b>轻量 MRP</b>
          <span class="tip">独立需求=销售未发货+安全库存；供应=现存量+在途采购+在制；有BOM建议生产并展开材料采购</span>
        </div>
        <el-button v-permission="'inventory:mrp:run'" type="primary" :loading="loading" @click="runMrp">
          运行计划
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="result"
      :title="`运行日 ${result.asOfDate} · 采购建议 ${result.purchaseCount} · 生产建议 ${result.productionCount}`"
      type="success"
      :closable="false"
      show-icon
    />

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>生产建议</template>
          <el-table :data="result?.productionLines || []" border stripe max-height="480">
            <el-table-column prop="productCode" label="编码" width="110" />
            <el-table-column prop="productName" label="品名" min-width="120" />
            <el-table-column prop="demandQty" label="需求" width="90" align="right" />
            <el-table-column prop="onHandQty" label="现存量" width="90" align="right" />
            <el-table-column prop="openSupplyQty" label="在途/在制" width="100" align="right" />
            <el-table-column prop="netQty" label="净需求" width="90" align="right" />
            <el-table-column prop="bomId" label="BOM" width="120" />
            <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>采购建议</template>
          <el-table :data="result?.purchaseLines || []" border stripe max-height="480">
            <el-table-column prop="productCode" label="编码" width="110" />
            <el-table-column prop="productName" label="品名" min-width="120" />
            <el-table-column prop="demandQty" label="需求" width="90" align="right" />
            <el-table-column prop="onHandQty" label="现存量" width="90" align="right" />
            <el-table-column prop="openSupplyQty" label="在途/在制" width="100" align="right" />
            <el-table-column prop="netQty" label="净需求" width="90" align="right" />
            <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { runMrpPlan, type MrpRunResult } from '@/api/inventory'

const loading = ref(false)
const result = ref<MrpRunResult>()

const runMrp = async () => {
  loading.value = true
  try {
    result.value = await runMrpPlan()
    ElMessage.success('MRP 运行完成')
  } catch {
    ElMessage.error('MRP 运行失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.tip { margin-left: 12px; color: #6b7280; font-size: 12px; }
</style>
