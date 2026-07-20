<template>
  <div class="funds-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="资金账户" name="accounts">
        <el-card shadow="never" class="search-card">
          <el-form :model="accountQuery" inline>
            <el-form-item label="关键字">
              <el-input v-model="accountQuery.keyword" placeholder="编码/名称/银行" clearable style="width: 220px" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="accountQuery.accountType" placeholder="请选择" clearable style="width: 140px">
                <el-option label="银行账户" value="BANK" />
                <el-option label="现金账户" value="CASH" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="accountQuery.status" placeholder="请选择" clearable style="width: 140px">
                <el-option label="启用" value="ENABLED" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadAccounts">查询</el-button>
              <el-button :icon="Refresh" @click="resetAccountQuery">重置</el-button>
              <el-button v-permission="'finance:fund:manage'" type="primary" :icon="Plus" @click="openAccountDialog">新增账户</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <span>资金对账</span>
          </template>
          <el-table v-loading="accountLoading" :data="accountData" border stripe>
            <el-table-column prop="accountCode" label="账户编码" width="150" />
            <el-table-column prop="accountName" label="账户名称" min-width="180" />
            <el-table-column prop="accountType" label="类型" width="110">
              <template #default="{ row }">{{ accountTypeLabel(row.accountType) }}</template>
            </el-table-column>
            <el-table-column prop="bankName" label="开户行" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.bankName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="bankAccountNo" label="账号" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.bankAccountNo || '-' }}</template>
            </el-table-column>
            <el-table-column prop="currencyCode" label="币种" width="90" />
            <el-table-column prop="openingBalance" label="期初余额" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.openingBalance) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
                  {{ row.status === 'ENABLED' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleViewAccount(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="accountQuery.pageNo"
            v-model:page-size="accountQuery.pageSize"
            :total="accountTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadAccounts"
            @current-change="loadAccounts"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="银行流水" name="statements">
        <el-card shadow="never" class="search-card">
          <el-form :model="statementQuery" inline>
            <el-form-item label="资金账户">
              <el-select v-model="statementQuery.fundAccountId" placeholder="请选择账户" clearable filterable style="width: 220px">
                <el-option
                  v-for="account in allAccounts"
                  :key="account.id"
                  :label="`${account.accountCode} - ${account.accountName}`"
                  :value="account.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="方向">
              <el-select v-model="statementQuery.direction" placeholder="请选择" clearable style="width: 120px">
                <el-option label="收入" value="IN" />
                <el-option label="支出" value="OUT" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="statementQuery.status" placeholder="请选择" clearable style="width: 140px">
                <el-option label="未匹配" value="UNMATCHED" />
                <el-option label="已匹配" value="MATCHED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadStatements">查询</el-button>
              <el-button :icon="Refresh" @click="resetStatementQuery">重置</el-button>
              <el-button v-permission="'finance:fund:manage'" type="primary" :icon="Plus" @click="openStatementDialog">新增流水</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table v-loading="statementLoading" :data="statementData" border stripe>
            <el-table-column prop="statementNo" label="流水号" width="180" />
            <el-table-column prop="fundAccountId" label="资金账户" min-width="180">
              <template #default="{ row }">{{ accountName(row.fundAccountId) }}</template>
            </el-table-column>
            <el-table-column prop="transactionDate" label="交易日期" width="120" />
            <el-table-column prop="direction" label="方向" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'IN' ? 'success' : 'danger'">
                  {{ row.direction === 'IN' ? '收入' : '支出' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="counterpartyName" label="对方户名" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.counterpartyName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'MATCHED' ? 'success' : 'warning'">
                  {{ row.status === 'MATCHED' ? '已匹配' : '未匹配' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="matchedBizNo" label="匹配单据" width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.matchedBizNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleViewStatement(row)">查看</el-button>
                <el-button v-if="row.status === 'UNMATCHED'" v-permission="'finance:fund:reconcile'" type="primary" link @click="openMatchDialog(row)">
                  匹配
                </el-button>
                <el-button v-if="row.status === 'MATCHED'" v-permission="'finance:fund:reconcile'" type="warning" link @click="handleUnmatch(row)">
                  取消匹配
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
            @size-change="loadStatements"
            @current-change="loadStatements"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="accountDialogVisible" title="新增资金账户" width="640px" @close="resetAccountForm">
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-width="110px">
        <el-form-item label="账户编码" prop="accountCode">
          <el-input v-model="accountForm.accountCode" placeholder="如 ICBC001" />
        </el-form-item>
        <el-form-item label="账户名称" prop="accountName">
          <el-input v-model="accountForm.accountName" placeholder="请输入账户名称" />
        </el-form-item>
        <el-form-item label="账户类型" prop="accountType">
          <el-select v-model="accountForm.accountType" style="width: 100%">
            <el-option label="银行账户" value="BANK" />
            <el-option label="现金账户" value="CASH" />
          </el-select>
        </el-form-item>
        <el-form-item label="开户行">
          <el-input v-model="accountForm.bankName" placeholder="银行账户可填" />
        </el-form-item>
        <el-form-item label="银行账号">
          <el-input v-model="accountForm.bankAccountNo" placeholder="银行账户可填" />
        </el-form-item>
        <el-form-item label="期初余额" prop="openingBalance">
          <el-input-number v-model="accountForm.openingBalance" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="accountForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="accountSubmitting" @click="submitAccount">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statementDialogVisible" title="新增银行流水" width="640px" @close="resetStatementForm">
      <el-form ref="statementFormRef" :model="statementForm" :rules="statementRules" label-width="110px">
        <el-form-item label="资金账户" prop="fundAccountId">
          <el-select v-model="statementForm.fundAccountId" placeholder="请选择账户" filterable style="width: 100%">
            <el-option
              v-for="account in allAccounts"
              :key="account.id"
              :label="`${account.accountCode} - ${account.accountName}`"
              :value="account.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="外部流水号">
          <el-input v-model="statementForm.externalTxnNo" clearable />
        </el-form-item>
        <el-form-item label="交易日期" prop="transactionDate">
          <el-date-picker v-model="statementForm.transactionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-select v-model="statementForm.direction" style="width: 100%">
            <el-option label="收入" value="IN" />
            <el-option label="支出" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="statementForm.amount" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="对方户名">
          <el-input v-model="statementForm.counterpartyName" clearable />
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="statementForm.summary" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="statementForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statementDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="statementSubmitting" @click="submitStatement">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="matchDialogVisible" title="匹配业务单据" width="560px" @close="resetMatchForm">
      <el-form ref="matchFormRef" :model="matchForm" :rules="matchRules" label-width="110px">
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="matchForm.bizType" style="width: 100%">
            <el-option label="收款单" value="RECEIPT" />
            <el-option label="付款单" value="PAYMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务ID" prop="bizId">
          <el-input v-model="matchForm.bizId" placeholder="请输入已过账收/付款单 ID" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="matchForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="matchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="matchSubmitting" @click="submitMatch">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="accountDetailVisible" title="资金账户详情" width="720px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedAccount" :column="2" border>
        <el-descriptions-item label="账户编码">{{ selectedAccount.accountCode }}</el-descriptions-item>
        <el-descriptions-item label="账户名称">{{ selectedAccount.accountName }}</el-descriptions-item>
        <el-descriptions-item label="账户类型">{{ accountTypeLabel(selectedAccount.accountType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ accountStatusLabel(selectedAccount.status) }}</el-descriptions-item>
        <el-descriptions-item label="开户行">{{ selectedAccount.bankName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="银行账号">{{ selectedAccount.bankAccountNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ selectedAccount.currencyCode }}</el-descriptions-item>
        <el-descriptions-item label="期初余额">{{ formatMoney(selectedAccount.openingBalance) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ selectedAccount.createdTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ selectedAccount.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="statementDetailVisible" title="银行流水详情" width="760px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-descriptions v-else-if="selectedStatementDetail" :column="2" border>
        <el-descriptions-item label="流水号">{{ selectedStatementDetail.statementNo }}</el-descriptions-item>
        <el-descriptions-item label="外部流水号">{{ selectedStatementDetail.externalTxnNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资金账户">{{ accountName(selectedStatementDetail.fundAccountId) }}</el-descriptions-item>
        <el-descriptions-item label="交易日期">{{ selectedStatementDetail.transactionDate }}</el-descriptions-item>
        <el-descriptions-item label="方向">{{ statementDirectionLabel(selectedStatementDetail.direction) }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ formatMoney(selectedStatementDetail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="对方户名">{{ selectedStatementDetail.counterpartyName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statementStatusLabel(selectedStatementDetail.status) }}</el-descriptions-item>
        <el-descriptions-item label="匹配业务类型">{{ selectedStatementDetail.matchedBizType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="匹配单据">{{ selectedStatementDetail.matchedBizNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="匹配业务ID">{{ selectedStatementDetail.matchedBizId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="匹配时间">{{ selectedStatementDetail.matchedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="取消原因">{{ selectedStatementDetail.unmatchReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ selectedStatementDetail.summary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ selectedStatementDetail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createBankStatement,
  createFundAccount,
  getBankStatement,
  getBankStatements,
  getFundAccount,
  getFundAccounts,
  matchBankStatement,
  unmatchBankStatement,
  type BankStatement,
  type BankStatementQuery,
  type FundAccount,
  type FundAccountQuery
} from '@/api/fund'

const activeTab = ref('accounts')

const accountQuery = reactive<FundAccountQuery>({ pageNo: 1, pageSize: 20, keyword: '', accountType: '', status: '' })
const statementQuery = reactive<BankStatementQuery>({ pageNo: 1, pageSize: 20, fundAccountId: undefined, direction: '', status: '' })

const accountLoading = ref(false)
const statementLoading = ref(false)
const accountData = ref<FundAccount[]>([])
const allAccounts = ref<FundAccount[]>([])
const statementData = ref<BankStatement[]>([])
const accountTotal = ref(0)
const statementTotal = ref(0)

const accountDialogVisible = ref(false)
const statementDialogVisible = ref(false)
const matchDialogVisible = ref(false)
const accountSubmitting = ref(false)
const statementSubmitting = ref(false)
const matchSubmitting = ref(false)
const accountFormRef = ref<FormInstance>()
const statementFormRef = ref<FormInstance>()
const matchFormRef = ref<FormInstance>()
const selectedStatement = ref<BankStatement | null>(null)
const selectedAccount = ref<FundAccount>()
const selectedStatementDetail = ref<BankStatement>()
const accountDetailVisible = ref(false)
const statementDetailVisible = ref(false)
const detailLoading = ref(false)

const accountForm = reactive({
  accountCode: '',
  accountName: '',
  accountType: 'BANK',
  bankName: '',
  bankAccountNo: '',
  currencyCode: 'CNY',
  openingBalance: 0,
  remark: ''
})

const statementForm = reactive({
  fundAccountId: '' as string | number,
  externalTxnNo: '',
  transactionDate: '',
  direction: 'IN',
  amount: 0,
  counterpartyName: '',
  summary: '',
  remark: ''
})

const matchForm = reactive({
  bizType: 'RECEIPT',
  bizId: '',
  remark: ''
})

const accountRules: FormRules = {
  accountCode: [{ required: true, message: '请输入账户编码', trigger: 'blur' }],
  accountName: [{ required: true, message: '请输入账户名称', trigger: 'blur' }],
  accountType: [{ required: true, message: '请选择账户类型', trigger: 'change' }],
  openingBalance: [{ required: true, message: '请输入期初余额', trigger: 'blur' }]
}

const statementRules: FormRules = {
  fundAccountId: [{ required: true, message: '请选择资金账户', trigger: 'change' }],
  transactionDate: [{ required: true, message: '请选择交易日期', trigger: 'change' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  summary: [{ required: true, message: '请输入摘要', trigger: 'blur' }]
}

const matchRules: FormRules = {
  bizType: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
  bizId: [{ required: true, message: '请输入业务ID', trigger: 'blur' }]
}

const accountMap = computed(() => new Map(allAccounts.value.map((item) => [String(item.id), item])))

const loadAccounts = async () => {
  accountLoading.value = true
  try {
    const res = await getFundAccounts(accountQuery)
    accountData.value = res.records || []
    accountTotal.value = res.total || 0
    await loadAllAccounts()
  } catch (error) {
    ElMessage.error('加载资金账户失败')
  } finally {
    accountLoading.value = false
  }
}

const loadAllAccounts = async () => {
  const res = await getFundAccounts({ pageNo: 1, pageSize: 200, status: 'ENABLED' })
  allAccounts.value = res.records || []
}

const loadStatements = async () => {
  statementLoading.value = true
  try {
    const res = await getBankStatements(statementQuery)
    statementData.value = res.records || []
    statementTotal.value = res.total || 0
  } catch (error) {
    ElMessage.error('加载银行流水失败')
  } finally {
    statementLoading.value = false
  }
}

const resetAccountQuery = () => {
  Object.assign(accountQuery, { pageNo: 1, keyword: '', accountType: '', status: '' })
  loadAccounts()
}

const resetStatementQuery = () => {
  Object.assign(statementQuery, { pageNo: 1, fundAccountId: undefined, direction: '', status: '' })
  loadStatements()
}

const openAccountDialog = () => {
  resetAccountForm()
  accountDialogVisible.value = true
}

const openStatementDialog = () => {
  resetStatementForm()
  statementForm.transactionDate = today()
  statementDialogVisible.value = true
}

const openMatchDialog = (row: BankStatement) => {
  selectedStatement.value = row
  resetMatchForm()
  matchForm.bizType = row.direction === 'IN' ? 'RECEIPT' : 'PAYMENT'
  matchDialogVisible.value = true
}

const handleViewAccount = async (row: FundAccount) => {
  accountDetailVisible.value = true
  selectedAccount.value = undefined
  detailLoading.value = true
  try {
    selectedAccount.value = await getFundAccount(row.id)
  } catch (error) {
    ElMessage.error('加载资金账户详情失败')
    accountDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleViewStatement = async (row: BankStatement) => {
  statementDetailVisible.value = true
  selectedStatementDetail.value = undefined
  detailLoading.value = true
  try {
    selectedStatementDetail.value = await getBankStatement(row.id)
  } catch (error) {
    ElMessage.error('加载银行流水详情失败')
    statementDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const submitAccount = async () => {
  if (!accountFormRef.value) return
  await accountFormRef.value.validate(async (valid) => {
    if (!valid) return
    accountSubmitting.value = true
    try {
      await createFundAccount(accountForm)
      ElMessage.success('资金账户创建成功')
      accountDialogVisible.value = false
      loadAccounts()
    } catch (error) {
      ElMessage.error('创建资金账户失败')
    } finally {
      accountSubmitting.value = false
    }
  })
}

const submitStatement = async () => {
  if (!statementFormRef.value) return
  await statementFormRef.value.validate(async (valid) => {
    if (!valid) return
    statementSubmitting.value = true
    try {
      await createBankStatement(statementForm)
      ElMessage.success('银行流水创建成功')
      statementDialogVisible.value = false
      loadStatements()
    } catch (error) {
      ElMessage.error('创建银行流水失败')
    } finally {
      statementSubmitting.value = false
    }
  })
}

const submitMatch = async () => {
  if (!matchFormRef.value || !selectedStatement.value) return
  await matchFormRef.value.validate(async (valid) => {
    if (!valid || !selectedStatement.value) return
    matchSubmitting.value = true
    try {
      await matchBankStatement(selectedStatement.value.id, matchForm)
      ElMessage.success('银行流水匹配成功')
      matchDialogVisible.value = false
      loadStatements()
    } catch (error) {
      ElMessage.error('匹配银行流水失败')
    } finally {
      matchSubmitting.value = false
    }
  })
}

const handleUnmatch = async (row: BankStatement) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消匹配原因', '取消匹配', {
      inputValue: '前端取消匹配',
      inputPattern: /\S+/,
      inputErrorMessage: '原因不能为空'
    })
    await unmatchBankStatement(row.id, value)
    ElMessage.success('已取消匹配')
    loadStatements()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('取消匹配失败')
  }
}

const resetAccountForm = () => {
  accountFormRef.value?.clearValidate()
  Object.assign(accountForm, {
    accountCode: '',
    accountName: '',
    accountType: 'BANK',
    bankName: '',
    bankAccountNo: '',
    currencyCode: 'CNY',
    openingBalance: 0,
    remark: ''
  })
}

const resetStatementForm = () => {
  statementFormRef.value?.clearValidate()
  Object.assign(statementForm, {
    fundAccountId: '',
    externalTxnNo: '',
    transactionDate: '',
    direction: 'IN',
    amount: 0,
    counterpartyName: '',
    summary: '',
    remark: ''
  })
}

const resetMatchForm = () => {
  matchFormRef.value?.clearValidate()
  Object.assign(matchForm, { bizType: 'RECEIPT', bizId: '', remark: '' })
}

const accountName = (id: string | number) => {
  const account = accountMap.value.get(String(id))
  return account ? `${account.accountCode} - ${account.accountName}` : `账户 ${id}`
}

const accountTypeLabel = (type: string) => {
  const map: Record<string, string> = { BANK: '银行账户', CASH: '现金账户' }
  return map[type] || type
}

const accountStatusLabel = (status: string) => {
  const map: Record<string, string> = { ENABLED: '启用', DISABLED: '停用' }
  return map[status] || status
}

const statementDirectionLabel = (direction: string) => {
  const map: Record<string, string> = { IN: '收入', OUT: '支出' }
  return map[direction] || direction
}

const statementStatusLabel = (status: string) => {
  const map: Record<string, string> = { MATCHED: '已匹配', UNMATCHED: '未匹配' }
  return map[status] || status
}

const formatMoney = (value?: number) => Number(value ?? 0).toFixed(2)
const today = () => new Date().toISOString().slice(0, 10)

watch(activeTab, (tab) => {
  if (tab === 'accounts') {
    loadAccounts()
  } else {
    loadAllAccounts().then(loadStatements)
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
