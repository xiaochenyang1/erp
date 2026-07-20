<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="会计科目">
          <el-tree-select
            v-model="queryForm.subjectId"
            :data="subjectOptions"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择会计科目"
            clearable
            filterable
            check-strictly
            style="width: 250px"
          />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'finance:ledger:view'" :icon="Download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 标签页 -->
    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 总账 -->
        <el-tab-pane label="总账查询" name="general">
          <el-table
            v-loading="generalLoading"
            :data="generalLedger"
            border
            stripe
            :summary-method="getGeneralSummary"
            show-summary
          >
            <el-table-column prop="subjectCode" label="科目编码" width="150" />
            <el-table-column prop="subjectName" label="科目名称" width="200" />
            <el-table-column prop="debitAmount" label="借方金额" width="150" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.debitAmount) }}
              </template>
            </el-table-column>
            <el-table-column prop="creditAmount" label="贷方金额" width="150" align="right">
              <template #default="{ row }">
                {{ formatAmount(row.creditAmount) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleViewDetail(row)">
                  查看明细
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 明细账 -->
        <el-tab-pane label="明细账查询" name="detail">
          <el-table
            v-loading="detailLoading"
            :data="detailLedger"
            border
            stripe
          >
            <el-table-column prop="bizDate" label="业务日期" width="120" />
            <el-table-column prop="voucherId" label="凭证ID" width="180" />
            <el-table-column prop="lineNo" label="行号" width="80" align="center" />
            <el-table-column prop="subjectCode" label="科目编码" width="120" />
            <el-table-column prop="subjectName" label="科目名称" width="150" />
            <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
            <el-table-column prop="debitAmount" label="借方金额" width="130" align="right">
              <template #default="{ row }">
                {{ row.debitAmount ? formatAmount(row.debitAmount) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="creditAmount" label="贷方金额" width="130" align="right">
              <template #default="{ row }">
                {{ row.creditAmount ? formatAmount(row.creditAmount) : '-' }}
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleQuery"
            @current-change="handleQuery"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import { downloadBlob } from '@/utils/download'
import {
  getAccountSubjectTree,
  getLedgerEntries,
  getLedgerSummary,
  exportLedger,
  type AccountSubject,
  type LedgerEntry,
  type LedgerSummary
} from '@/api/finance'

// 标签页
const activeTab = ref('general')

// 查询表单
const queryForm = reactive({
  subjectId: undefined as string | number | undefined,
  startDate: '',
  endDate: ''
})

const dateRange = ref<string[]>([])

// 科目选项
const subjectOptions = ref<AccountSubject[]>([])

// 总账数据
const generalLoading = ref(false)
const generalLedger = ref<LedgerSummary[]>([])

// 明细账数据
const detailLoading = ref(false)
const detailLedger = ref<LedgerEntry[]>([])

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 加载科目选项
const loadSubjects = async () => {
  try {
    const subjects = await getAccountSubjectTree()
    subjectOptions.value = subjects || []
  } catch (error) {
    console.error('加载科目失败:', error)
  }
}

// 加载总账
const loadGeneralLedger = async () => {
  generalLoading.value = true
  try {
    const params = buildLedgerQueryParams()
    const res = await getLedgerSummary(params)
    generalLedger.value = res || []
  } catch (error) {
    console.error('加载总账失败:', error)
    ElMessage.error('加载总账数据失败')
  } finally {
    generalLoading.value = false
  }
}

// 加载明细账
const loadDetailLedger = async () => {
  detailLoading.value = true
  try {
    const entries = await getLedgerEntries(buildLedgerQueryParams())
    const start = (pagination.page - 1) * pagination.size
    detailLedger.value = entries.slice(start, start + pagination.size)
    pagination.total = entries.length
  } catch (error) {
    console.error('加载明细账失败:', error)
    ElMessage.error('加载明细账数据失败')
  } finally {
    detailLoading.value = false
  }
}

// 查询
const handleQuery = () => {
  syncDateRange()

  if (activeTab.value === 'general') {
    loadGeneralLedger()
  } else {
    loadDetailLedger()
  }
}

// 重置
const handleReset = () => {
  queryForm.subjectId = undefined
  queryForm.startDate = ''
  queryForm.endDate = ''
  dateRange.value = []
  pagination.page = 1
  handleQuery()
}

// 标签页切换
const handleTabChange = (tabName: string) => {
  if (tabName === 'general') {
    loadGeneralLedger()
  } else {
    loadDetailLedger()
  }
}

// 查看明细
const handleViewDetail = (row: LedgerSummary) => {
  // 切换到明细账标签页，并设置科目过滤
  const subject = findSubjectByCode(row.subjectCode)
  if (subject) {
    queryForm.subjectId = subject.id
  }
  activeTab.value = 'detail'
  loadDetailLedger()
}

// 递归查找科目
const findSubjectByCode = (code: string): AccountSubject | null => {
  const search = (subjects: AccountSubject[]): AccountSubject | null => {
    for (const subject of subjects) {
      if (subject.code === code) return subject
      if (subject.children) {
        const found = search(subject.children)
        if (found) return found
      }
    }
    return null
  }
  return search(subjectOptions.value)
}

const findSubjectById = (id?: string | number): AccountSubject | null => {
  if (!id) return null
  const search = (subjects: AccountSubject[]): AccountSubject | null => {
    for (const subject of subjects) {
      if (String(subject.id) === String(id)) return subject
      if (subject.children) {
        const found = search(subject.children)
        if (found) return found
      }
    }
    return null
  }
  return search(subjectOptions.value)
}

const selectedSubjectCode = () => {
  const subject = findSubjectById(queryForm.subjectId)
  return subject?.code || subject?.subjectCode || undefined
}

const syncDateRange = () => {
  if (dateRange.value && dateRange.value.length === 2) {
    queryForm.startDate = dateRange.value[0]
    queryForm.endDate = dateRange.value[1]
  } else {
    queryForm.startDate = ''
    queryForm.endDate = ''
  }
}

const buildLedgerQueryParams = () => ({
  subjectCode: selectedSubjectCode(),
  startDate: queryForm.startDate,
  endDate: queryForm.endDate
})

// 导出
const handleExport = async () => {
  try {
    syncDateRange()
    const blob = await exportLedger(buildLedgerQueryParams())
    downloadBlob(blob, `总账_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// 格式化金额
const formatAmount = (amount: number) => {
  return amount?.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }) || '0.00'
}

// 总账合计行
const getGeneralSummary = (param: any) => {
  const { columns, data } = param
  const sums: string[] = []
  columns.forEach((column: any, index: number) => {
    if (index === 0) {
      sums[index] = '合计'
      return
    }
    if (index === 1) {
      sums[index] = ''
      return
    }
    const values = data.map((item: any) => Number(item[column.property]))
    if (!values.every((value: number) => isNaN(value))) {
      const total = values.reduce((prev: number, curr: number) => {
        const value = Number(curr)
        if (!isNaN(value)) {
          return prev + curr
        }
        return prev
      }, 0)
      sums[index] = formatAmount(total)
    } else {
      sums[index] = ''
    }
  })
  return sums
}

onMounted(() => {
  loadSubjects()
  loadGeneralLedger()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>
