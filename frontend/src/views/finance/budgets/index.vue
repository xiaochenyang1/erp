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
            <el-button :icon="DataAnalysis" @click="openExecution">{{ $t('financeReportPages.budgets.execution') }}</el-button>
            <el-button v-permission="'finance:budget:manage'" type="primary" :icon="Plus" @click="openCreate">{{ $t('financeReportPages.budgets.create') }}</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="budgetYear" :label="$t('financeReportPages.budgets.year')" width="90" align="center" />
        <el-table-column prop="budgetName" :label="$t('financeReportPages.budgets.name')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="controlPolicy" :label="$t('financeReportPages.budgets.policy')" width="110" align="center">
          <template #default="{ row }">{{ policyText(row.controlPolicy) }}</template>
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
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
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
      <el-pagination v-model:current-page="pagination.pageNo" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSizeChange" @current-change="handlePageChange" />
    </el-card>

    <el-dialog v-model="editorVisible" :title="dialogTitle" width="1050px" destroy-on-close>
      <el-form :model="editor" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item :label="$t('financeReportPages.budgets.year')"><el-input-number v-model="editor.budgetYear" :disabled="Boolean(editorId)" :min="2000" :max="2100" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('financeReportPages.budgets.name')"><el-input v-model="editor.budgetName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="$t('financeReportPages.budgets.policy')"><el-select v-model="editor.controlPolicy" style="width: 100%"><el-option :label="$t('financeReportPages.budgets.policyValue.reject')" value="REJECT" /><el-option :label="$t('financeReportPages.budgets.policyValue.approval')" value="APPROVAL" /></el-select></el-form-item></el-col>
        </el-row>
        <el-form-item :label="$t('financeReportPages.common.remark')"><el-input v-model="editor.remark" /></el-form-item>
        <el-table :data="editor.lines" border>
          <el-table-column :label="$t('financeReportPages.budgets.month')" width="130">
            <template #default="{ row }"><el-select v-model="row.periodMonth" style="width: 100%"><el-option :label="monthLabel(0)" :value="0" /><el-option v-for="month in 12" :key="month" :label="monthLabel(month)" :value="month" /></el-select></template>
          </el-table-column>
          <el-table-column :label="$t('financeReportPages.budgets.department')" min-width="180"><template #default="{ row }"><el-select v-model="row.deptId" clearable filterable style="width: 100%"><el-option v-for="dept in departments" :key="dept.id" :label="deptLabel(dept)" :value="dept.id" /></el-select></template></el-table-column>
          <el-table-column :label="$t('financeReportPages.budgets.subject')" min-width="240"><template #default="{ row }"><el-select v-model="row.subjectId" filterable style="width: 100%"><el-option v-for="subject in subjects" :key="subject.id" :label="subjectLabel(subject)" :value="subject.id" /></el-select></template></el-table-column>
          <el-table-column :label="$t('financeReportPages.budgets.amount')" width="160"><template #default="{ row }"><el-input-number v-model="row.budgetAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></template></el-table-column>
          <el-table-column :label="$t('financeReportPages.common.remark')" min-width="150"><template #default="{ row }"><el-input v-model="row.remark" /></template></el-table-column>
          <el-table-column width="80"><template #default="{ $index }"><el-button link type="danger" @click="removeLine($index)">{{ $t('financeReportPages.common.delete') }}</el-button></template></el-table-column>
        </el-table>
        <el-button class="add-line" :icon="Plus" @click="addLine">{{ $t('financeReportPages.budgets.addLine') }}</el-button>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button><el-button type="primary" :loading="saving" @click="save">{{ $t('financeReportPages.common.save') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="$t('financeReportPages.budgets.detail')" width="1050px" @closed="resetDetail">
      <el-descriptions :column="4" border>
        <el-descriptions-item :label="$t('financeReportPages.budgets.year')">{{ selected?.budgetYear }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.budgets.name')">{{ selected?.budgetName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.budgets.policy')">{{ policyText(selected?.controlPolicy) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.status')">{{ statusText(selected?.status) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="selected?.lines || []" border stripe class="detail-table"><el-table-column :label="$t('financeReportPages.budgets.month')" width="110"><template #default="{ row }">{{ monthLabel(row.periodMonth) }}</template></el-table-column><el-table-column :label="$t('financeReportPages.budgets.department')"><template #default="{ row }">{{ deptName(row.deptId) }}</template></el-table-column><el-table-column :label="$t('financeReportPages.budgets.subject')"><template #default="{ row }">{{ subjectName(row.subjectId) }}</template></el-table-column><el-table-column :label="$t('financeReportPages.budgets.amount')" align="right"><template #default="{ row }">{{ formatAmount(row.budgetAmount) }}</template></el-table-column><el-table-column :label="$t('financeReportPages.budgets.committed')" align="right"><template #default="{ row }">{{ formatAmount(row.committedAmount) }}</template></el-table-column><el-table-column :label="$t('financeReportPages.budgets.actual')" align="right"><template #default="{ row }">{{ formatAmount(row.actualAmount) }}</template></el-table-column><el-table-column :label="$t('financeReportPages.budgets.available')" align="right"><template #default="{ row }"><span :class="{ negative: row.availableAmount < 0 }">{{ formatAmount(row.availableAmount) }}</span></template></el-table-column></el-table>
    </el-dialog>

    <el-dialog v-model="executionVisible" :title="$t('financeReportPages.budgets.execution')" width="720px" @closed="resetExecution">
      <el-form :model="executionQuery" label-width="120px">
        <el-form-item :label="$t('financeReportPages.budgets.year')"><el-input-number v-model="executionQuery.budgetYear" :min="2000" :max="2100" /></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.month')"><el-select v-model="executionQuery.periodMonth"><el-option v-for="month in 12" :key="month" :label="monthLabel(month)" :value="month" /></el-select></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.department')"><el-select v-model="executionQuery.deptId" clearable filterable><el-option v-for="dept in departments" :key="dept.id" :label="deptLabel(dept)" :value="dept.id" /></el-select></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.subject')"><el-select v-model="executionQuery.subjectId" filterable><el-option v-for="subject in subjects" :key="subject.id" :label="subjectLabel(subject)" :value="subject.id" /></el-select></el-form-item>
        <el-form-item :label="$t('financeReportPages.budgets.amount')"><el-input-number v-model="executionQuery.amount" :min="0" :precision="2" /></el-form-item>
      </el-form>
      <el-alert v-if="execution" :type="execution.overrun ? 'warning' : 'success'" :title="execution.overrun ? $t('financeReportPages.budgets.overrun') : $t('financeReportPages.budgets.withinBudget')" show-icon :closable="false" />
      <el-descriptions v-if="execution" :column="2" border class="execution-result"><el-descriptions-item :label="$t('financeReportPages.budgets.periodSource')">{{ periodSourceText(execution.periodSource) }}</el-descriptions-item><el-descriptions-item :label="$t('financeReportPages.budgets.available')">{{ formatAmount(execution.availableAmount) }}</el-descriptions-item><el-descriptions-item :label="$t('financeReportPages.budgets.projectedAvailable')">{{ formatAmount(execution.projectedAvailableAmount) }}</el-descriptions-item><el-descriptions-item :label="$t('financeReportPages.budgets.policy')">{{ policyText(execution.controlPolicy) }}</el-descriptions-item></el-descriptions>
      <template #footer><el-button @click="executionVisible = false">{{ $t('financeReportPages.common.close') }}</el-button><el-button type="primary" :loading="executionLoading" @click="loadExecution">{{ $t('financeReportPages.common.search') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { approveBudget, cancelBudget, closeBudget, createBudget, getAccountSubjectTree, getBudget, getBudgetExecution, getBudgets, submitBudget, updateBudget } from '@/api/finance'
import { getDeptTree } from '@/api/system'
import { useBudgetExecution } from '@/composables/useBudgetExecution'
import { useBudgetForm } from '@/composables/useBudgetForm'
import { useBudgetList } from '@/composables/useBudgetList'
import { useBudgetPresentation } from '@/composables/useBudgetPresentation'

const { t } = useI18n()
const statuses = ['DRAFT', 'SUBMITTED', 'APPROVED', 'CLOSED', 'CANCELLED']

const confirm = (message: string, title: string, options?: { type?: string }) => ElMessageBox.confirm(message, title, options as any)
const onError = (message: string) => ElMessage.error(message)
const onSuccess = (message: string) => ElMessage.success(message)
const onWarning = (message: string) => ElMessage.warning(message)

const { departments, detailVisible, handlePageChange, handleSizeChange, loadBudgets, loadResources, loading, openDetail, pagination, query, records, resetDetail, resetQuery, runAction, selected, subjects } = useBudgetList(t, { getBudgets, getBudget, getAccountSubjectTree, getDeptTree, confirm, onError, onSuccess })

const { deptLabel, deptName, formatAmount, monthLabel, periodSourceText, policyText, statusText, statusType, subjectLabel, subjectName } = useBudgetPresentation(t, { subjects, departments })

const reload = async () => { await loadBudgets() }

const { addLine, dialogTitle, editor, editorId, editorVisible, openCreate, openEdit, removeLine, save, saving } = useBudgetForm(t, { getBudget, createBudget, updateBudget, onError, onSuccess, onWarning, onSubmitted: reload })

const { execution, executionLoading, executionQuery, executionVisible, loadExecution, openExecution, resetExecution } = useBudgetExecution(t, { getBudgetExecution, onError, onWarning })

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
