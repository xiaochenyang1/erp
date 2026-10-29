<template>
  <div class="funds-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="$t('financeReportPages.funds.tabs.accounts')" name="accounts">
        <el-card shadow="never" class="search-card">
          <el-form :model="accountQuery" inline>
            <el-form-item :label="$t('financeReportPages.common.keyword')">
              <el-input v-model="accountQuery.keyword" :placeholder="$t('financeReportPages.funds.keywordPlaceholder')" clearable style="width: 220px" />
            </el-form-item>
            <el-form-item :label="$t('financeReportPages.funds.type')">
              <el-select v-model="accountQuery.accountType" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 140px">
                <el-option :label="$t('financeReportPages.funds.accountType.bank')" value="BANK" />
                <el-option :label="$t('financeReportPages.funds.accountType.cash')" value="CASH" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('financeReportPages.common.status')">
              <el-select v-model="accountQuery.status" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 140px">
                <el-option :label="$t('financeReportPages.funds.enabled')" value="ENABLED" />
                <el-option :label="$t('financeReportPages.funds.disabled')" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="searchAccounts">{{ $t('financeReportPages.common.search') }}</el-button>
              <el-button :icon="Refresh" @click="resetAccountQuery">{{ $t('financeReportPages.common.reset') }}</el-button>
              <el-button v-permission="'finance:fund:manage'" type="primary" :icon="Plus" @click="openAccountDialog">{{ $t('financeReportPages.funds.newAccount') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <span>{{ $t('financeReportPages.funds.reconciliation') }}</span>
          </template>
          <el-table v-loading="accountLoading" :data="accountData" border stripe>
            <el-table-column prop="accountCode" :label="$t('financeReportPages.funds.accountCode')" width="150" />
            <el-table-column prop="accountName" :label="$t('financeReportPages.funds.accountName')" min-width="180" />
            <el-table-column prop="accountType" :label="$t('financeReportPages.funds.type')" width="110">
              <template #default="{ row }">{{ accountTypeLabel(row.accountType) }}</template>
            </el-table-column>
            <el-table-column prop="bankName" :label="$t('financeReportPages.funds.bankName')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.bankName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="bankAccountNo" :label="$t('financeReportPages.funds.accountNo')" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.bankAccountNo || '-' }}</template>
            </el-table-column>
            <el-table-column prop="currencyCode" :label="$t('financeReportPages.funds.currency')" width="90" />
            <el-table-column prop="openingBalance" :label="$t('financeReportPages.funds.openingBalance')" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.openingBalance) }}</template>
            </el-table-column>
            <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
                  {{ row.status === 'ENABLED' ? $t('financeReportPages.funds.enabled') : $t('financeReportPages.funds.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" :label="$t('financeReportPages.common.createdTime')" width="170" />
            <el-table-column :label="$t('financeReportPages.common.actions')" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleViewAccount(row)">{{ $t('financeReportPages.common.view') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="accountQuery.pageNo"
            v-model:page-size="accountQuery.pageSize"
            :total="accountTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="searchAccounts"
            @current-change="loadAccounts"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="$t('financeReportPages.funds.tabs.statements')" name="statements">
        <el-card shadow="never" class="search-card">
          <el-form :model="statementQuery" inline>
            <el-form-item :label="$t('financeReportPages.funds.fundAccount')">
              <el-select v-model="statementQuery.fundAccountId" :placeholder="$t('financeReportPages.funds.selectAccount')" clearable filterable style="width: 220px">
                <el-option
                  v-for="account in allAccounts"
                  :key="account.id"
                  :label="`${account.accountCode} - ${account.accountName}`"
                  :value="account.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('financeReportPages.funds.direction')">
              <el-select v-model="statementQuery.direction" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 120px">
                <el-option :label="$t('financeReportPages.funds.income')" value="IN" />
                <el-option :label="$t('financeReportPages.funds.expense')" value="OUT" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('financeReportPages.common.status')">
              <el-select v-model="statementQuery.status" :placeholder="$t('financeReportPages.common.selectPlaceholder')" clearable style="width: 140px">
                <el-option :label="$t('financeReportPages.funds.unmatched')" value="UNMATCHED" />
                <el-option :label="$t('financeReportPages.funds.matched')" value="MATCHED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="searchStatements">{{ $t('financeReportPages.common.search') }}</el-button>
              <el-button :icon="Refresh" @click="resetStatementQuery">{{ $t('financeReportPages.common.reset') }}</el-button>
              <el-button v-permission="'finance:fund:manage'" type="primary" :icon="Plus" @click="openStatementDialog">{{ $t('financeReportPages.funds.newStatement') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="statementLoading" :data="statementData" border stripe>
            <el-table-column prop="statementNo" :label="$t('financeReportPages.funds.statementNo')" width="180" />
            <el-table-column prop="fundAccountId" :label="$t('financeReportPages.funds.fundAccount')" min-width="180">
              <template #default="{ row }">{{ accountName(row.fundAccountId) }}</template>
            </el-table-column>
            <el-table-column prop="transactionDate" :label="$t('financeReportPages.funds.transactionDate')" width="120" />
            <el-table-column prop="direction" :label="$t('financeReportPages.funds.direction')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'IN' ? 'success' : 'danger'">
                  {{ row.direction === 'IN' ? $t('financeReportPages.funds.income') : $t('financeReportPages.funds.expense') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="amount" :label="$t('financeReportPages.common.amount')" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="counterpartyName" :label="$t('financeReportPages.funds.counterpartyName')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.counterpartyName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="summary" :label="$t('financeReportPages.common.summary')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="status" :label="$t('financeReportPages.common.status')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'MATCHED' ? 'success' : 'warning'">
                  {{ row.status === 'MATCHED' ? $t('financeReportPages.funds.matched') : $t('financeReportPages.funds.unmatched') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="matchedBizNo" :label="$t('financeReportPages.funds.matchedDocument')" width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.matchedBizNo || '-' }}</template>
            </el-table-column>
            <el-table-column :label="$t('financeReportPages.common.actions')" width="220" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleViewStatement(row)">{{ $t('financeReportPages.common.view') }}</el-button>
                <el-button v-if="row.status === 'UNMATCHED'" v-permission="'finance:fund:reconcile'" type="primary" link @click="openMatchDialog(row)">
                  {{ $t('financeReportPages.funds.match') }}
                </el-button>
                <el-button v-if="row.status === 'MATCHED'" v-permission="'finance:fund:reconcile'" type="warning" link @click="handleUnmatch(row)">
                  {{ $t('financeReportPages.funds.unmatch') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="statementQuery.pageNo"
            v-model:page-size="statementQuery.pageSize"
            :total="statementTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="searchStatements"
            @current-change="loadStatements"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="accountDialogVisible" :title="$t('financeReportPages.funds.newAccountTitle')" width="640px" @close="resetAccountForm">
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-width="110px">
        <el-form-item :label="$t('financeReportPages.funds.accountCode')" prop="accountCode">
          <el-input v-model="accountForm.accountCode" :placeholder="$t('financeReportPages.funds.accountCodeExample')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.accountName')" prop="accountName">
          <el-input v-model="accountForm.accountName" :placeholder="$t('financeReportPages.funds.accountNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.accountTypeLabel')" prop="accountType">
          <el-select v-model="accountForm.accountType" style="width: 100%">
            <el-option :label="$t('financeReportPages.funds.accountType.bank')" value="BANK" />
            <el-option :label="$t('financeReportPages.funds.accountType.cash')" value="CASH" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.bankName')">
          <el-input v-model="accountForm.bankName" :placeholder="$t('financeReportPages.funds.bankOptional')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.bankAccountNo')">
          <el-input v-model="accountForm.bankAccountNo" :placeholder="$t('financeReportPages.funds.bankOptional')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.openingBalance')" prop="openingBalance">
          <el-input-number v-model="accountForm.openingBalance" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.remark')">
          <el-input v-model="accountForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="accountSubmitting" @click="submitAccount">{{ $t('financeReportPages.common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statementDialogVisible" :title="$t('financeReportPages.funds.newStatementTitle')" width="640px" @close="resetStatementForm">
      <el-form ref="statementFormRef" :model="statementForm" :rules="statementRules" label-width="110px">
        <el-form-item :label="$t('financeReportPages.funds.fundAccount')" prop="fundAccountId">
          <el-select v-model="statementForm.fundAccountId" :placeholder="$t('financeReportPages.funds.selectAccount')" filterable style="width: 100%">
            <el-option
              v-for="account in allAccounts"
              :key="account.id"
              :label="`${account.accountCode} - ${account.accountName}`"
              :value="account.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.externalTransactionNo')">
          <el-input v-model="statementForm.externalTxnNo" clearable />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.transactionDate')" prop="transactionDate">
          <el-date-picker v-model="statementForm.transactionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.direction')" prop="direction">
          <el-select v-model="statementForm.direction" style="width: 100%">
            <el-option :label="$t('financeReportPages.funds.income')" value="IN" />
            <el-option :label="$t('financeReportPages.funds.expense')" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.amount')" prop="amount">
          <el-input-number v-model="statementForm.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.counterpartyName')">
          <el-input v-model="statementForm.counterpartyName" clearable />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.summary')" prop="summary">
          <el-input v-model="statementForm.summary" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.remark')">
          <el-input v-model="statementForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statementDialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="statementSubmitting" @click="submitStatement">{{ $t('financeReportPages.common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="matchDialogVisible" :title="$t('financeReportPages.funds.matchDocumentTitle')" width="560px" @close="resetMatchForm">
      <el-form ref="matchFormRef" :model="matchForm" :rules="matchRules" label-width="110px">
        <el-form-item :label="$t('financeReportPages.funds.businessType')" prop="bizType">
          <el-select v-model="matchForm.bizType" style="width: 100%">
            <el-option :label="$t('financeReportPages.funds.receipt')" value="RECEIPT" />
            <el-option :label="$t('financeReportPages.funds.payment')" value="PAYMENT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.funds.businessId')" prop="bizId">
          <el-input v-model="matchForm.bizId" :placeholder="$t('financeReportPages.funds.businessIdPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('financeReportPages.common.remark')">
          <el-input v-model="matchForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="matchDialogVisible = false">{{ $t('financeReportPages.common.cancel') }}</el-button>
        <el-button type="primary" :loading="matchSubmitting" @click="submitMatch">{{ $t('financeReportPages.common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="accountDetailVisible" :title="$t('financeReportPages.funds.accountDetail')" width="720px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedAccount" :column="2" border>
        <el-descriptions-item :label="$t('financeReportPages.funds.accountCode')">{{ selectedAccount.accountCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.accountName')">{{ selectedAccount.accountName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.accountTypeLabel')">{{ accountTypeLabel(selectedAccount.accountType) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.status')">{{ accountStatusLabel(selectedAccount.status) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.bankName')">{{ selectedAccount.bankName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.bankAccountNo')">{{ selectedAccount.bankAccountNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.currency')">{{ selectedAccount.currencyCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.openingBalance')">{{ formatMoney(selectedAccount.openingBalance) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.createdTime')">{{ selectedAccount.createdTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.remark')">{{ selectedAccount.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="statementDetailVisible" :title="$t('financeReportPages.funds.statementDetail')" width="760px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-descriptions v-else-if="selectedStatementDetail" :column="2" border>
        <el-descriptions-item :label="$t('financeReportPages.funds.statementNo')">{{ selectedStatementDetail.statementNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.externalTransactionNo')">{{ selectedStatementDetail.externalTxnNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.fundAccount')">{{ accountName(selectedStatementDetail.fundAccountId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.transactionDate')">{{ selectedStatementDetail.transactionDate }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.direction')">{{ statementDirectionLabel(selectedStatementDetail.direction) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.amount')">{{ formatMoney(selectedStatementDetail.amount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.counterpartyName')">{{ selectedStatementDetail.counterpartyName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.status')">{{ statementStatusLabel(selectedStatementDetail.status) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.matchedBusinessType')">{{ businessTypeLabel(selectedStatementDetail.matchedBizType) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.matchedDocument')">{{ selectedStatementDetail.matchedBizNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.matchedBusinessId')">{{ selectedStatementDetail.matchedBizId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.matchedTime')">{{ selectedStatementDetail.matchedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.funds.unmatchReason')">{{ selectedStatementDetail.unmatchReason || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.summary')">{{ selectedStatementDetail.summary || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('financeReportPages.common.remark')" :span="2">{{ selectedStatementDetail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createBankStatement,
  createFundAccount,
  getBankStatement,
  getBankStatements,
  getFundAccount,
  getFundAccounts,
  matchBankStatement,
  unmatchBankStatement
} from '@/api/fund'
import { useFundPresentation } from '@/composables/useFundPresentation'
import { useFundList } from '@/composables/useFundList'
import { useFundForm } from '@/composables/useFundForm'

const { t } = useI18n()
const activeTab = ref('accounts')

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const accountFormRef = ref<FormInstance>()
const statementFormRef = ref<FormInstance>()
const matchFormRef = ref<FormInstance>()

const {
  accountData,
  accountDetailVisible,
  accountLoading,
  accountQuery,
  accountTotal,
  allAccounts,
  detailLoading,
  handleViewAccount,
  handleViewStatement,
  loadAccounts,
  loadStatementTab,
  loadStatements,
  resetAccountQuery,
  resetStatementQuery,
  searchAccounts,
  searchStatements,
  selectedAccount,
  selectedStatementDetail,
  statementData,
  statementDetailVisible,
  statementLoading,
  statementQuery,
  statementTotal
} = useFundList(t, {
  getAccounts: getFundAccounts,
  getAccount: getFundAccount,
  getStatements: getBankStatements,
  getStatement: getBankStatement,
  ...notify
})

const {
  accountName,
  accountStatusLabel,
  accountTypeLabel,
  businessTypeLabel,
  formatMoney,
  statementDirectionLabel,
  statementStatusLabel
} = useFundPresentation(t, allAccounts)

const {
  accountDialogVisible,
  accountForm,
  accountSubmitting,
  handleUnmatch,
  matchDialogVisible,
  matchForm,
  matchSubmitting,
  openAccountDialog,
  openMatchDialog,
  openStatementDialog,
  statementDialogVisible,
  statementForm,
  statementSubmitting,
  resetAccountForm: resetAccountFormState,
  resetMatchForm: resetMatchFormState,
  resetStatementForm: resetStatementFormState,
  submitAccount: submitAccountRequest,
  submitMatch: submitMatchRequest,
  submitStatement: submitStatementRequest
} = useFundForm(t, {
  createAccount: createFundAccount,
  createStatement: createBankStatement,
  matchStatement: matchBankStatement,
  unmatchStatement: unmatchBankStatement,
  prompt: (message, title, options) => ElMessageBox.prompt(message, title, options),
  onAccountSubmitted: loadAccounts,
  onStatementSubmitted: loadStatements,
  ...notify
})

/** Element form state lives with the template, so validation stays in the page. */
const resetAccountForm = () => {
  accountFormRef.value?.clearValidate()
  resetAccountFormState()
}

const resetStatementForm = () => {
  statementFormRef.value?.clearValidate()
  resetStatementFormState()
}

const resetMatchForm = () => {
  matchFormRef.value?.clearValidate()
  resetMatchFormState()
}

const submitAccount = async () => {
  if (!(await accountFormRef.value?.validate().catch(() => false))) return
  await submitAccountRequest()
}

const submitStatement = async () => {
  if (!(await statementFormRef.value?.validate().catch(() => false))) return
  await submitStatementRequest()
}

const submitMatch = async () => {
  if (!(await matchFormRef.value?.validate().catch(() => false))) return
  await submitMatchRequest()
}

const accountRules = computed<FormRules>(() => ({
  accountCode: [{ required: true, message: t('financeReportPages.funds.validation.accountCode'), trigger: 'blur' }],
  accountName: [{ required: true, message: t('financeReportPages.funds.validation.accountName'), trigger: 'blur' }],
  accountType: [{ required: true, message: t('financeReportPages.funds.validation.accountType'), trigger: 'change' }],
  openingBalance: [{ required: true, message: t('financeReportPages.funds.validation.openingBalance'), trigger: 'blur' }]
}))

const statementRules = computed<FormRules>(() => ({
  fundAccountId: [{ required: true, message: t('financeReportPages.funds.validation.fundAccount'), trigger: 'change' }],
  transactionDate: [{ required: true, message: t('financeReportPages.funds.validation.transactionDate'), trigger: 'change' }],
  direction: [{ required: true, message: t('financeReportPages.funds.validation.direction'), trigger: 'change' }],
  amount: [{ required: true, message: t('financeReportPages.funds.validation.amount'), trigger: 'blur' }],
  summary: [{ required: true, message: t('financeReportPages.funds.validation.summary'), trigger: 'blur' }]
}))

const matchRules = computed<FormRules>(() => ({
  bizType: [{ required: true, message: t('financeReportPages.funds.validation.businessType'), trigger: 'change' }],
  bizId: [{ required: true, message: t('financeReportPages.funds.validation.businessId'), trigger: 'blur' }]
}))

watch(activeTab, (tab) => {
  if (tab === 'accounts') {
    loadAccounts()
  } else {
    loadStatementTab()
  }
})

onMounted(() => {
  loadAccounts()
})
</script>

<style scoped>
.funds-container {
  padding: 20px;
}

.search-card,
.table-card {
  margin-bottom: 20px;
}
</style>
