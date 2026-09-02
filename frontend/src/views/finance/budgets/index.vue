<template>
  <div class="app-container budget-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item :label="$t('financeReportPages.budgets.year')">
          <el-input-number v-model="query.budgetYear" :min="2000" :max="2100" :controls="false" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.status')">
          <el-select v-model="query.status" clearable style="width: 150px">
            <el-option v-for="item in statuses" :key="item" :label="$t(`financeReportPages.budgets.status.${item.toLowerCase()}`)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="query.keyword" clearable :placeholder="$t('financeReportPages.common.keyword')" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadBudgets">{{ $t('financeReportPages.common.search') }}</el-button>
          <el-button :icon="Refresh" @click="resetQuery">{{ $t('financeReportPages.common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('financeReportPages.budgets.title') }}</span>
          <div>
            <el-button :icon="DataAnalysis" @click="executionVisible = true">{{ $t('financeReportPages.budgets.execution') }}</el-button>
            <el-button v-permission="'finance:budget:manage'" type="primary" :icon="Plus" @click="openCreate">{{ $t('financeReportPages.budgets.create') }}</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="budgetYear" :label="$t('financeReportPages.budgets.year')" width="90" align="center" />
        <el-table-column prop="budgetName" :label="$t('financeReportPages.budgets.name')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="controlPolicy" :label="$t('financeReportPages.budgets.policy')" width="110" align="center">
          <template #default="{ row }">{{ $t(`financeReportPages.budgets.policyValue.${row.controlPolicy.toLowerCase()}`) }}</template>
        </el-table-column>
        <el-table-column prop="totalBudgetAmount" :label="$t('financeReportPages.budgets.totalBudget')" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalBudgetAmount) }}</template>
        </el-table-column>
        <el-table-column prop="totalCommittedAmount" :label="$t('financeReportPages.budgets.committed')" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalCommittedAmount) }}</template>
        </el-table-column>
        <el-table-column prop="totalActualAmount" :label="$t('financeReportPages.budgets.actual')" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalActualAmount) }}</template>
        </el-table-column>
        <el-table-column prop="totalAvailableAmount" :label="$t('financeReportPages.budgets.available')" width="140" align="right">
          <template #default="{ row }"><span :class="{ negative: row.totalAvailableAmount < 0 }">{{ formatAmount(row.totalAvailableAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="115" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ $t(`financeReportPages.budgets.status.${row.status.toLowerCase()}`) }}</el-tag></template>
        </el-table-column>
        <el-table-column :label="$t('financeReportPages.common.actions')" width="390" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ $t('financeReportPages.common.view') }}</el-button>
            <el-button v-if="['DRAFT', 'SUBMITTED'].includes(row.status)" v-permission="'finance:budget:manage'" link type="primary" @click="openEdit(row)">{{ $t('financeReportPages.common.edit') }}</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'finance:budget:manage'" link type="success" @click="runAction(submitBudget, row, 'financeReportPages.common.submit')">{{ $t('financeReportPages.common.submit') }}</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="'finance:budget:approve'" link type="success" @click="runAction(approveBudget, row, 'financeReportPages.common.approve')">{{ $t('financeReportPages.common.approve') }}</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="'finance:budget:manage'" link type="warning" @click="runAction(closeBudget, row, 'financeReportPages.budgets.close')">{{ $t('financeReportPages.budgets.close') }}</el-button>
            <el-button v-if="['DRAFT', 'SUBMITTED'].includes(row.status)" v-permission="'finance:budget:manage'" link type="danger" @click="runAction(cancelBudget, row, 'financeReportPages.common.void')">{{ $t('financeReportPages.common.void') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pagination.pageNo" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" @size-change="loadBudgets" @current-change="loadBudgets" />
    </el-card>

    <el-dialog v-model="editorVisible" :title="editorId ? $t('financeReportPages.budgets.edit') : $t('financeReportPages.budgets.create')" width="1050px" destroy-on-close>
      <el-form :model="editor" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item :label="$t('financeReportPages.budgets.year')"><el-input-number v-model="editor.budgetYear" :disabled="Boolean(editorId)" :min="2000" :max="2100" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('financeReportPages.budgets.name')"><el-input v-model="editor.budgetName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('financeReportPages.budgets.policy')"><el-select v-model="editor.controlPolicy" style="width: 100%"><el-option :label="$t('financeReportPages.budgets.policyValue.reject')" value="REJECT" /><el-option :label="$t('financeReportPages.budgets.policyValue.approval')" value="APPROVAL" /></el-select></el-form-item></el-col>
        </el-row>
        <el-form-item :label="$t('financeReportPages.common.remark')"><el-input v-model="editor.remark" /></el-form-item>
        <el-table :data="editor.lines" border>
          <el-table-column :label="$t('financeReportPages.budgets.month')" width="130">
            <template #default="{ row }"><el-select v-model="row.periodMonth" style="width: 100%"><el-option :label="$t('financeReportPages.budgets.annual')" :value="0" /><el-option v-for="month in 12" :key="month" :label="`${month}${$t('financeReportPages.budgets.monthSuffix')}`" :value="month" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('financeReportPages.budgets.department')" min-width="180"><template #default="{ row }"><el-select v-model="row.deptId" clearable filterable style="width: 100%"><el-option v-for="dept in departments" :key="dept.id" :label="deptLabel(dept)" :value="dept.id" /></el-select></template></el-table-column>
          <el-table-column :label="$t('financeReportPages.budgets.subject')" min-width="240"><template #default="{ row }"><el-select v-model="row.subjectId" filterable style="width: 100%"><el-option v-for="subject in subjects" :key="subject.id" :label="subjectLabel(subject)" :value="subject.id" /></el-select></template></el-table-column>
          <el-table-column :label="$t('financeReportPages.budgets.amount')" width="160"><template #default="{ row }"><el-input-number v-model="row.budgetAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></template></el-table-column>
          <el-table-column :label="$t('financeReportPages.common.remark')" min-width="150"><template #default="{ row }"><el-input v-model="row.remark" /></template></el-table-column>
          <el-table-column width="80"><template #default="{ $index }"><el-button link type="danger" @click="editor.lines.splice($index, 1)">{{ $t('financeReportPages.common.delete') }}</el-button></template></el-table-column>
        </el-table>
        <el-button class="add-line" :icon="Plus" @click="addLine">{{ $t('financeReportPages.budgets.addLine') }}</el-button>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button><el-button type="primary" :loading="saving" @click="saveEditor">{{ $t('financeReportPages.common.save') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="$t('financeReportPages.budgets.detail')" width="1050px">
      <el-descriptions :column="4" border>
        <el-descriptions-item :label="$t('financeReportPages.budgets.year')">{{ selected?.budgetYear }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.budgets.name')">{{ selected?.budgetName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.budgets.policy')">{{ selected?.controlPolicy }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.status')">{{ selected?.status }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="selected?.lines || []" border stripe class="detail-table"><el-table-column prop="periodMonth" :label="$t('financeReportPages.budgets.month')" width="90" /><el-table-column :label="$t('financeReportPages.budgets.department')"><template #default="{ row }">{{ deptName(row.deptId) }}</template></el-table-column><el-table-column :label="$t('financeReportPages.budgets.subject')"><template #default="{ row }">{{ subjectName(row.subjectId) }}</template></el-table-column><el-table-column prop="budgetAmount" :label="$t('financeReportPages.budgets.amount')" align="right" /><el-table-column prop="committedAmount" :label="$t('financeReportPages.budgets.committed')" align="right" /><el-table-column prop="actualAmount" :label="$t('financeReportPages.budgets.actual')" align="right" /><el-table-column prop="availableAmount" :label="$t('financeReportPages.budgets.available')" align="right" /></el-table>
    </el-dialog>

    <el-dialog v-model="executionVisible" :title="$t('financeReportPages.budgets.execution')" width="720px">
      <el-form :model="executionQuery" label-width="120px">
        <el-form-item :label="$t('financeReportPages.budgets.year')"><el-input-number v-model="executionQuery.budgetYear" :min="2000" :max="2100" /></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.month')"><el-select v-model="executionQuery.periodMonth"><el-option v-for="month in 12" :key="month" :label="`${month}${$t('financeReportPages.budgets.monthSuffix')}`" :value="month" /></el-select></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.department')"><el-select v-model="executionQuery.deptId" clearable filterable><el-option v-for="dept in departments" :key="dept.id" :label="deptLabel(dept)" :value="dept.id" /></el-select></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.subject')"><el-select v-model="executionQuery.subjectId" filterable><el-option v-for="subject in subjects" :key="subject.id" :label="subjectLabel(subject)" :value="subject.id" /></el-select></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.amount')"><el-input-number v-model="executionQuery.amount" :min="0" :precision="2" /></el-form-item>
      </el-form>
      <el-alert v-if="execution" :type="execution.overrun ? 'warning' : 'success'" :title="execution.overrun ? $t('financeReportPages.budgets.overrun') : $t('financeReportPages.budgets.withinBudget')" show-icon :closable="false" />
      <el-descriptions v-if="execution" :column="2" border class="execution-result"><el-descriptions-item :label="$t('financeReportPages.budgets.periodSource')">{{ execution.periodSource }}</el-descriptions-item><el-descriptions-item :label="$t('financeReportPages.budgets.available')">{{ formatAmount(execution.availableAmount) }}</el-descriptions-item><el-descriptions-item :label="$t('financeReportPages.budgets.projectedAvailable')">{{ formatAmount(execution.projectedAvailableAmount) }}</el-descriptions-item><el-descriptions-item :label="$t('financeReportPages.budgets.policy')">{{ execution.controlPolicy || '-' }}</el-descriptions-item></el-descriptions>
      <template #footer><el-button @click="executionVisible = false">{{ $t('financeReportPages.common.close') }}</el-button><el-button type="primary" :loading="executionLoading" @click="loadExecution">{{ $t('financeReportPages.common.search') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { approveBudget, cancelBudget, closeBudget, createBudget, getBudget, getBudgetExecution, getBudgets, submitBudget, updateBudget, type Budget, type BudgetExecution, type BudgetLineSaveRequest } from '@/api/finance'
import { getAccountSubjectTree, type AccountSubject } from '@/api/finance'
import { getDeptTree, type Dept } from '@/api/system'
import { formatLocalizedNumber } from '@/utils/locale'

const { t } = useI18n()
const statuses = ['DRAFT', 'SUBMITTED', 'APPROVED', 'CLOSED', 'CANCELLED']
const query = reactive({ budgetYear: new Date().getFullYear(), status: '', keyword: '' })
const pagination = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const records = ref<Budget[]>([])
const loading = ref(false)
const editorVisible = ref(false)
const detailVisible = ref(false)
const executionVisible = ref(false)
const saving = ref(false)
const executionLoading = ref(false)
const editorId = ref<string>()
const selected = ref<Budget>()
const execution = ref<BudgetExecution>()
const subjects = ref<AccountSubject[]>([])
const departments = ref<Dept[]>([])
const editor = reactive({ budgetYear: new Date().getFullYear(), budgetName: '', controlPolicy: 'REJECT', remark: '', lines: [] as BudgetLineSaveRequest[] })
const executionQuery = reactive({ budgetYear: new Date().getFullYear(), periodMonth: new Date().getMonth() + 1, deptId: undefined as string | undefined, subjectId: '', amount: 0 })

const flatten = <T extends { children?: T[] }>(items: T[]): T[] => items.flatMap((item) => [item, ...flatten(item.children || [])])
const subjectLabel = (item: AccountSubject) => `${item.code || item.subjectCode} - ${item.name || item.subjectName}`
const deptLabel = (item: Dept) => `${item.code || item.deptCode || ''} ${item.name || item.deptName || ''}`.trim()
const subjectName = (id?: string | number) => subjects.value.find((item) => String(item.id) === String(id)) ? subjectLabel(subjects.value.find((item) => String(item.id) === String(id))!) : String(id || '-')
const deptName = (id?: string | number) => departments.value.find((item) => String(item.id) === String(id)) ? deptLabel(departments.value.find((item) => String(item.id) === String(id))!) : '-'
const formatAmount = (value?: number) => formatLocalizedNumber(Number(value || 0), { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const statusType = (status: string) => ({ DRAFT: 'info', SUBMITTED: 'warning', APPROVED: 'success', CLOSED: 'primary', CANCELLED: 'danger' }[status] || 'info') as 'success' | 'warning' | 'info' | 'danger' | 'primary'

const loadResources = async () => {
  const [subjectTree, deptTree] = await Promise.all([getAccountSubjectTree(), getDeptTree()])
  subjects.value = flatten(subjectTree)
  departments.value = flatten(deptTree)
}
const loadBudgets = async () => {
  loading.value = true
  try {
    const page = await getBudgets({ ...query, pageNo: pagination.pageNo, pageSize: pagination.pageSize, status: query.status || undefined, keyword: query.keyword || undefined })
    records.value = page.records
    pagination.total = page.total
  } catch { ElMessage.error(t('financeReportPages.budgets.message.loadFailed')) } finally { loading.value = false }
}
const resetQuery = () => { Object.assign(query, { budgetYear: new Date().getFullYear(), status: '', keyword: '' }); pagination.pageNo = 1; loadBudgets() }
const addLine = () => editor.lines.push({ periodMonth: 0, deptId: undefined, subjectId: '', budgetAmount: 0, remark: '' })
const openCreate = () => { editorId.value = undefined; Object.assign(editor, { budgetYear: new Date().getFullYear(), budgetName: '', controlPolicy: 'REJECT', remark: '', lines: [] }); addLine(); editorVisible.value = true }
const openEdit = async (row: Budget) => { const detail = await getBudget(row.id); editorId.value = detail.id; Object.assign(editor, { budgetYear: detail.budgetYear, budgetName: detail.budgetName, controlPolicy: detail.controlPolicy, remark: detail.remark || '', lines: detail.lines.map((line) => ({ periodMonth: line.periodMonth, deptId: line.deptId, subjectId: line.subjectId, budgetAmount: line.budgetAmount, remark: line.remark })) }); editorVisible.value = true }
const openDetail = async (row: Budget) => { selected.value = await getBudget(row.id); detailVisible.value = true }
const saveEditor = async () => { if (!editor.budgetName.trim() || !editor.lines.length || editor.lines.some((line) => !line.subjectId)) { ElMessage.warning(t('financeReportPages.budgets.validation.completeForm')); return } saving.value = true; try { const payload = { budgetYear: editor.budgetYear, budgetName: editor.budgetName, controlPolicy: editor.controlPolicy, remark: editor.remark, lines: editor.lines }; if (editorId.value) await updateBudget(editorId.value, payload); else await createBudget(payload); ElMessage.success(t('financeReportPages.budgets.message.saved')); editorVisible.value = false; await loadBudgets() } catch { ElMessage.error(t('financeReportPages.budgets.message.saveFailed')) } finally { saving.value = false } }
const runAction = async (action: (id: string) => Promise<Budget>, row: Budget, actionLabelKey: string) => { try { await ElMessageBox.confirm(t('financeReportPages.budgets.message.confirmAction', { action: t(actionLabelKey), name: row.budgetName }), t('financeReportPages.budgets.message.prompt'), { type: 'warning' }); await action(row.id); ElMessage.success(t('financeReportPages.budgets.message.actionDone')); await loadBudgets() } catch (error: any) { if (error !== 'cancel' && error !== 'close') ElMessage.error(t('financeReportPages.budgets.message.actionFailed')) } }
const loadExecution = async () => { if (!executionQuery.subjectId) { ElMessage.warning(t('financeReportPages.budgets.validation.subject')); return } executionLoading.value = true; try { execution.value = await getBudgetExecution({ ...executionQuery, amount: executionQuery.amount || undefined }) } catch { ElMessage.error(t('financeReportPages.budgets.message.executionLoadFailed')) } finally { executionLoading.value = false } }
onMounted(async () => { await loadResources(); await loadBudgets() })
</script>

<style scoped>
.budget-page { padding: 20px; }
.search-card, .table-card { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.detail-table, .execution-result { margin-top: 18px; }
.add-line { margin-top: 12px; }
.negative { color: var(--el-color-danger); }
.el-pagination { justify-content: flex-end; margin-top: 20px; }
</style>
