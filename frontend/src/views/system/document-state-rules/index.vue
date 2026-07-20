<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="单据类型">
          <el-select v-model="queryForm.documentType" placeholder="全部" clearable filterable style="width: 220px">
            <el-option
              v-for="opt in documentTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="queryForm.keyword"
            placeholder="动作/权限/路径"
            clearable
            style="width: 200px"
            @keyup.enter="() => {}"
          />
        </el-form-item>
        <el-form-item>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>单据状态流转规则（只读）</span>
          <span class="hint">用于排查“为什么这张单据不能执行某动作”：展示各单据类型在每个动作下允许的源状态与目标状态。</span>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="filteredData"
        border
        stripe
        row-key="rowKey"
        :span-method="spanMethod"
      >
        <el-table-column prop="documentName" label="单据类型" width="150" />
        <el-table-column prop="actionName" label="动作" width="130">
          <template #default="{ row }">
            <div>{{ row.actionName || row.action }}</div>
            <div class="sub">{{ row.action }}</div>
          </template>
        </el-table-column>
        <el-table-column label="接口" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.method }}</el-tag>
            <span class="path">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="permission" label="所需权限" min-width="180" show-overflow-tooltip />
        <el-table-column label="允许源状态" min-width="180">
          <template #default="{ row }">
            <template v-if="row.allowedStatuses?.length">
              <el-tag v-for="s in row.allowedStatuses" :key="s" size="small" class="tag">{{ s }}</el-tag>
            </template>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="允许审批状态" min-width="160">
          <template #default="{ row }">
            <template v-if="row.allowedApprovalStatuses?.length">
              <el-tag v-for="s in row.allowedApprovalStatuses" :key="s" size="small" type="warning" class="tag">{{ s }}</el-tag>
            </template>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="目标状态" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.targetStatus" size="small" type="success">{{ row.targetStatus }}</el-tag>
            <el-tag v-if="row.targetApprovalStatus" size="small" type="success" class="tag">{{ row.targetApprovalStatus }}</el-tag>
            <span v-if="!row.targetStatus && !row.targetApprovalStatus" class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="执行状态约束" min-width="200">
          <template #default="{ row }">
            <div v-if="row.executionStatusField" class="sub">字段：{{ row.executionStatusField }}</div>
            <div v-if="row.allowedExecutionStatuses?.length">
              允许：
              <el-tag v-for="s in row.allowedExecutionStatuses" :key="s" size="small" class="tag">{{ s }}</el-tag>
            </div>
            <div v-if="row.blockedExecutionStatuses?.length">
              阻止：
              <el-tag v-for="s in row.blockedExecutionStatuses" :key="s" size="small" type="danger" class="tag">{{ s }}</el-tag>
            </div>
            <span v-if="!row.executionStatusField && !row.allowedExecutionStatuses?.length && !row.blockedExecutionStatuses?.length" class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getDocumentStateRules, type DocumentStateRule } from '@/api/system'

interface RuleRow extends DocumentStateRule {
  rowKey: string
}

const loading = ref(false)
const rawData = ref<RuleRow[]>([])

const queryForm = reactive({
  documentType: '',
  keyword: ''
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getDocumentStateRules()
    rawData.value = (res || []).map((rule, index) => ({
      ...rule,
      rowKey: `${rule.documentType}-${rule.action}-${index}`
    }))
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const documentTypeOptions = computed(() => {
  const map = new Map<string, string>()
  for (const rule of rawData.value) {
    if (!map.has(rule.documentType)) {
      map.set(rule.documentType, rule.documentName || rule.documentType)
    }
  }
  return [...map.entries()].map(([value, label]) => ({ value, label }))
})

const filteredData = computed(() => {
  const kw = queryForm.keyword.trim().toLowerCase()
  return rawData.value.filter((rule) => {
    if (queryForm.documentType && rule.documentType !== queryForm.documentType) return false
    if (kw) {
      const hay = `${rule.action} ${rule.actionName} ${rule.permission} ${rule.path}`.toLowerCase()
      if (!hay.includes(kw)) return false
    }
    return true
  })
})

// 合并同一单据类型的“单据类型”单元格
const spanMethod = ({ row, column, rowIndex }: { row: RuleRow; column: { property?: string }; rowIndex: number }) => {
  if (column.property !== 'documentName') return
  const data = filteredData.value
  if (rowIndex > 0 && data[rowIndex - 1].documentType === row.documentType) {
    return { rowspan: 0, colspan: 0 }
  }
  let span = 1
  for (let i = rowIndex + 1; i < data.length; i++) {
    if (data[i].documentType === row.documentType) span++
    else break
  }
  return { rowspan: span, colspan: 1 }
}

onMounted(loadData)
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.card-header .hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: normal;
}

.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.path {
  margin-left: 6px;
}

.tag {
  margin: 2px;
}

.muted {
  color: var(--el-text-color-placeholder);
}
</style>
