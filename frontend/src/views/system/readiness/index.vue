<template>
  <div class="readiness-container">
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div>
          <div class="page-title">预生产验收</div>
          <div class="page-subtitle">预检时间：{{ formatLocalizedDateTime(preflight.checkedAt) || '-' }}</div>
        </div>
        <div class="header-actions">
          <el-tag :type="preflightTagType(preflight.overallStatus)" size="large">
            {{ preflightStatusLabel(preflight.overallStatus) }}
          </el-tag>
          <el-button :icon="Refresh" :loading="preflightLoading" @click="loadPreflight">刷新预检</el-button>
          <el-button v-permission="'system:readiness:manage'" type="primary" :icon="Plus" @click="openRunDialog">新建运行单</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="preflight-card">
      <template #header>
        <span>迁移前健康检查</span>
      </template>
      <el-table v-loading="preflightLoading" :data="preflight.items" border stripe>
        <el-table-column prop="code" label="检查项" min-width="220" show-overflow-tooltip />
        <el-table-column prop="severity" label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="priorityTagType(row.severity)">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="preflightTagType(row.status)">{{ preflightStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="数量" width="100" align="right" />
        <el-table-column prop="summary" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="样例" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ sampleText(row.sample) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="Commit">
          <el-input v-model="queryForm.releaseCommit" placeholder="候选 commit" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item label="环境">
          <el-input v-model="queryForm.environment" placeholder="如 LOCAL" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="通过" value="PASSED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="阻塞" value="BLOCKED" />
            <el-option label="不发布" value="NO_GO" />
          </el-select>
        </el-form-item>
        <el-form-item label="决策">
          <el-select v-model="queryForm.decision" placeholder="请选择" clearable style="width: 130px">
            <el-option label="待决策" value="PENDING" />
            <el-option label="Go" value="GO" />
            <el-option label="No-Go" value="NO_GO" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>验收运行单</span>
      </template>
      <el-table v-loading="runLoading" :data="runData" border stripe>
        <el-table-column prop="runNo" label="运行单号" width="190" show-overflow-tooltip />
        <el-table-column prop="releaseCommit" label="Commit" min-width="190" show-overflow-tooltip />
        <el-table-column prop="releaseVersion" label="版本" width="140">
          <template #default="{ row }">{{ row.releaseVersion || '-' }}</template>
        </el-table-column>
        <el-table-column prop="environment" label="环境" width="110" />
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="runStatusTagType(row.status)">{{ runStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="decision" label="决策" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="decisionTagType(row.decision)">{{ decisionLabel(row.decision) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="databaseInstance" label="数据库" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.databaseInstance || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="190">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.createdTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button
              v-permission="'system:readiness:manage'"
              type="success"
              link
              :icon="DocumentChecked"
              :disabled="isRunClosed(row)"
              @click="handleRecordPreflight(row)"
            >
              记录预检
            </el-button>
            <el-button
              v-permission="'system:readiness:decide'"
              type="warning"
              link
              :icon="CircleCheck"
              :disabled="isRunClosed(row)"
              @click="openDecisionDialog(row)"
            >
              决策
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="queryForm.pageNo"
        v-model:page-size="queryForm.pageSize"
        :total="runTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadRuns"
        @current-change="loadRuns"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <el-drawer v-model="detailVisible" size="78%">
      <template #header>
        <span>运行单详情{{ selectedDetail ? `：${selectedDetail.run.runNo}` : '' }}</span>
      </template>
      <template v-if="selectedDetail">
        <el-descriptions :column="3" border class="detail-descriptions">
          <el-descriptions-item label="Commit">{{ selectedDetail.run.releaseCommit }}</el-descriptions-item>
          <el-descriptions-item label="环境">{{ selectedDetail.run.environment }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ runStatusLabel(selectedDetail.run.status) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ selectedDetail.run.releaseVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数据库">{{ selectedDetail.run.databaseInstance || '-' }}</el-descriptions-item>
          <el-descriptions-item label="决策">{{ decisionLabel(selectedDetail.run.decision) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ selectedDetail.run.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="决策说明" :span="3">
            {{ selectedDetail.run.decisionComment || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-toolbar">
          <div class="section-title">验收项</div>
          <el-button
            v-permission="'system:readiness:manage'"
            type="primary"
            :icon="Plus"
            :disabled="isRunClosed(selectedDetail.run)"
            @click="openItemDialog"
          >
            新增验收项
          </el-button>
        </div>

        <el-table :data="selectedDetail.items" border stripe>
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="evidence-panel">
                <el-empty v-if="row.evidence.length === 0" description="暂无证据" />
                <el-table v-else :data="row.evidence" size="small" border>
                  <el-table-column prop="evidenceType" label="类型" width="120" />
                  <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
                  <el-table-column prop="requestUri" label="接口" min-width="220" show-overflow-tooltip>
                    <template #default="{ row: evidence }">{{ evidence.requestUri || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="businessNo" label="业务编号" width="160" show-overflow-tooltip>
                    <template #default="{ row: evidence }">{{ evidence.businessNo || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="recordedTime" label="记录时间" width="190">
                    <template #default="{ row }">{{ formatLocalizedDateTime(row.recordedTime) || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="itemCode" label="编码" min-width="180" show-overflow-tooltip />
          <el-table-column prop="itemName" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="130" />
          <el-table-column prop="priority" label="级别" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="priorityTagType(row.priority)">{{ row.priority }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="itemStatusTagType(row.status)">{{ itemStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expectedResult" label="预期结果" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.expectedResult || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-permission="'system:readiness:manage'"
                type="primary"
                link
                :disabled="isRunClosed(selectedDetail.run)"
                @click="openEvidenceDialog(row)"
              >
                证据
              </el-button>
              <el-button
                v-permission="'system:readiness:manage'"
                type="success"
                link
                :disabled="isRunClosed(selectedDetail.run)"
                @click="openResultDialog(row)"
              >
                结果
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="runDialogVisible" title="新建验收运行单" width="680px" @close="resetRunForm">
      <el-form ref="runFormRef" :model="runForm" :rules="runRules" label-width="130px">
        <el-form-item label="候选 Commit" prop="releaseCommit">
          <el-input v-model="runForm.releaseCommit" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="runForm.releaseVersion" clearable />
        </el-form-item>
        <el-form-item label="环境" prop="environment">
          <el-input v-model="runForm.environment" />
        </el-form-item>
        <el-form-item label="数据库实例">
          <el-input v-model="runForm.databaseInstance" clearable />
        </el-form-item>
        <el-form-item label="Redis 实例">
          <el-input v-model="runForm.redisInstance" clearable />
        </el-form-item>
        <el-form-item label="Docker profile">
          <el-input v-model="runForm.dockerProfile" clearable />
        </el-form-item>
        <el-form-item label="默认验收项">
          <el-switch v-model="runForm.generateDefaultItems" active-text="生成" inactive-text="不生成" />
        </el-form-item>
        <el-form-item label="记录预检证据">
          <el-switch v-model="runForm.recordPreflightEvidence" active-text="记录" inactive-text="不记录" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="runForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="runSubmitting" @click="submitRun">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" title="新增验收项" width="620px" @close="resetItemForm">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="110px">
        <el-form-item label="编码" prop="itemCode">
          <el-input v-model="itemForm.itemCode" placeholder="如 DATA_RECONCILE" />
        </el-form-item>
        <el-form-item label="名称" prop="itemName">
          <el-input v-model="itemForm.itemName" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="itemForm.category" placeholder="如 FINANCE" />
        </el-form-item>
        <el-form-item label="级别" prop="priority">
          <el-select v-model="itemForm.priority" style="width: 100%">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
          </el-select>
        </el-form-item>
        <el-form-item label="预期结果">
          <el-input v-model="itemForm.expectedResult" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitting" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evidenceDialogVisible" title="登记验收证据" width="700px" @close="resetEvidenceForm">
      <el-form ref="evidenceFormRef" :model="evidenceForm" :rules="evidenceRules" label-width="110px">
        <el-form-item label="证据类型" prop="evidenceType">
          <el-select v-model="evidenceForm.evidenceType" style="width: 100%">
            <el-option label="接口" value="API" />
            <el-option label="业务单号" value="BUSINESS_NO" />
            <el-option label="日志" value="LOG" />
            <el-option label="截图" value="SCREENSHOT" />
            <el-option label="备注" value="NOTE" />
            <el-option label="附件" value="ATTACHMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要" prop="summary">
          <el-input v-model="evidenceForm.summary" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-input v-model="evidenceForm.requestMethod" placeholder="GET / POST" clearable />
        </el-form-item>
        <el-form-item label="请求地址">
          <el-input v-model="evidenceForm.requestUri" clearable />
        </el-form-item>
        <el-form-item label="HTTP 状态">
          <el-input-number v-model="evidenceForm.httpStatus" :min="100" :max="599" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="evidenceForm.businessType" clearable />
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input v-model="evidenceForm.businessId" clearable />
        </el-form-item>
        <el-form-item label="业务编号">
          <el-input v-model="evidenceForm.businessNo" clearable />
        </el-form-item>
        <el-form-item label="附件业务类型">
          <el-input v-model="evidenceForm.attachmentBusinessType" clearable />
        </el-form-item>
        <el-form-item label="附件业务ID">
          <el-input v-model="evidenceForm.attachmentBusinessId" clearable />
        </el-form-item>
        <el-form-item label="详情">
          <el-input v-model="evidenceForm.detail" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evidenceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="evidenceSubmitting" @click="submitEvidence">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialogVisible" title="记录验收结果" width="620px" @close="resetResultForm">
      <el-form ref="resultFormRef" :model="resultForm" :rules="resultRules" label-width="110px">
        <el-form-item label="状态" prop="status">
          <el-select v-model="resultForm.status" style="width: 100%">
            <el-option label="通过" value="PASSED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="阻塞" value="BLOCKED" />
            <el-option label="跳过" value="SKIPPED" />
          </el-select>
        </el-form-item>
        <el-form-item label="实际结果">
          <el-input v-model="resultForm.actualResult" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="失败/跳过原因">
          <el-input v-model="resultForm.failureReason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resultSubmitting" @click="submitResult">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionDialogVisible" title="发布决策" width="560px" @close="resetDecisionForm">
      <el-form ref="decisionFormRef" :model="decisionForm" :rules="decisionRules" label-width="110px">
        <el-form-item label="决策" prop="decision">
          <el-select v-model="decisionForm.decision" style="width: 100%">
            <el-option label="Go" value="GO" />
            <el-option label="No-Go" value="NO_GO" />
          </el-select>
        </el-form-item>
        <el-form-item label="运行单状态" prop="status">
          <el-select v-model="decisionForm.status" style="width: 100%">
            <el-option
              v-for="option in decisionStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="决策说明">
          <el-input v-model="decisionForm.decisionComment" type="textarea" :rows="4" />
        </el-form-item>
        <el-alert
          v-if="decisionForm.decision === 'GO' && decisionBlockingItems.length > 0"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 8px"
          :title="`存在 ${decisionBlockingItems.length} 个未通过的 P0/P1 验收项，不能标记发布通过`"
        >
          <div class="decision-blocking-list">
            <div v-for="item in decisionBlockingItems" :key="item.id" class="decision-blocking-item">
              <el-tag :type="item.priority === 'P0' ? 'danger' : 'warning'" size="small">{{ item.priority }}</el-tag>
              <span>{{ item.itemCode }} {{ item.itemName }}</span>
              <el-tag type="info" size="small">{{ itemStatusLabel(item.status) }}</el-tag>
            </div>
          </div>
        </el-alert>
        <el-alert
          v-else-if="decisionForm.decision === 'GO' && !decisionItemsLoading"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 8px"
          title="全部 P0/P1 验收项已通过，可以标记发布通过"
        />
      </el-form>
      <template #footer>
        <el-button @click="decisionDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="decisionSubmitting"
          :disabled="decisionGoBlocked || decisionItemsLoading"
          @click="submitDecision"
        >保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, DocumentChecked, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { formatLocalizedDateTime } from '@/utils/locale'
import {
  addReadinessEvidence,
  addReadinessItem,
  createReadinessRun,
  decideReadinessRun,
  getReadinessPreflight,
  getReadinessRunDetail,
  getReadinessRuns,
  markReadinessItemResult,
  recordReadinessPreflightEvidence,
  type ReadinessItem,
  type ReadinessRun,
  type ReadinessRunDetail,
  type ReadinessRunQuery
} from '@/api/readiness'

const queryForm = reactive<ReadinessRunQuery>({
  pageNo: 1,
  pageSize: 20,
  releaseCommit: '',
  environment: '',
  status: '',
  decision: ''
})

const preflight = ref({
  overallStatus: '',
  checkedAt: '',
  items: [] as Array<{ code: string; status: string; severity: string; summary: string; count: number; sample: string[] }>
})
const preflightLoading = ref(false)
const runLoading = ref(false)
const runData = ref<ReadinessRun[]>([])
const runTotal = ref(0)

const detailVisible = ref(false)
const selectedDetail = ref<ReadinessRunDetail | null>(null)
const selectedRun = ref<ReadinessRun | null>(null)
const selectedItem = ref<ReadinessItem | null>(null)

const runDialogVisible = ref(false)
const itemDialogVisible = ref(false)
const evidenceDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const decisionDialogVisible = ref(false)
const runSubmitting = ref(false)
const itemSubmitting = ref(false)
const evidenceSubmitting = ref(false)
const resultSubmitting = ref(false)
const decisionSubmitting = ref(false)

const runFormRef = ref<FormInstance>()
const itemFormRef = ref<FormInstance>()
const evidenceFormRef = ref<FormInstance>()
const resultFormRef = ref<FormInstance>()
const decisionFormRef = ref<FormInstance>()

const runForm = reactive({
  releaseCommit: '',
  releaseVersion: '',
  environment: 'LOCAL',
  databaseInstance: 'erp_codex_runtime',
  redisInstance: '',
  dockerProfile: 'local',
  generateDefaultItems: true,
  recordPreflightEvidence: true,
  remark: ''
})

const itemForm = reactive({
  itemCode: '',
  itemName: '',
  category: '',
  priority: 'P1',
  expectedResult: ''
})

const evidenceForm = reactive({
  evidenceType: 'NOTE',
  requestMethod: '',
  requestUri: '',
  httpStatus: undefined as number | undefined,
  businessType: '',
  businessId: '',
  businessNo: '',
  summary: '',
  detail: '',
  attachmentBusinessType: '',
  attachmentBusinessId: ''
})

const resultForm = reactive({
  status: 'PASSED',
  actualResult: '',
  failureReason: ''
})

const decisionForm = reactive({
  decision: 'GO',
  status: 'PASSED',
  decisionComment: ''
})

const runRules: FormRules = {
  releaseCommit: [{ required: true, message: '请输入候选 commit', trigger: 'blur' }],
  environment: [{ required: true, message: '请输入验收环境', trigger: 'blur' }]
}

const itemRules: FormRules = {
  itemCode: [{ required: true, message: '请输入验收项编码', trigger: 'blur' }],
  itemName: [{ required: true, message: '请输入验收项名称', trigger: 'blur' }],
  category: [{ required: true, message: '请输入分类', trigger: 'blur' }],
  priority: [{ required: true, message: '请选择级别', trigger: 'change' }]
}

const evidenceRules: FormRules = {
  evidenceType: [{ required: true, message: '请选择证据类型', trigger: 'change' }],
  summary: [{ required: true, message: '请输入证据摘要', trigger: 'blur' }]
}

const resultRules: FormRules = {
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const decisionRules: FormRules = {
  decision: [{ required: true, message: '请选择发布决策', trigger: 'change' }],
  status: [{ required: true, message: '请选择运行单状态', trigger: 'change' }]
}

const decisionStatusOptions = computed(() => {
  if (decisionForm.decision === 'GO') {
    return [{ label: '通过', value: 'PASSED' }]
  }
  return [
    { label: '失败', value: 'FAILED' },
    { label: '阻塞', value: 'BLOCKED' },
    { label: '不发布', value: 'NO_GO' }
  ]
})

watch(
  () => decisionForm.decision,
  (decision) => {
    decisionForm.status = decision === 'GO' ? 'PASSED' : 'NO_GO'
  }
)

const loadPreflight = async () => {
  preflightLoading.value = true
  try {
    preflight.value = await getReadinessPreflight()
  } catch (error) {
    console.error('加载预检失败:', error)
    ElMessage.error('加载预检失败')
  } finally {
    preflightLoading.value = false
  }
}

const loadRuns = async () => {
  runLoading.value = true
  try {
    const res = await getReadinessRuns(queryForm)
    runData.value = res.records || []
    runTotal.value = res.total || 0
  } catch (error) {
    console.error('加载验收运行单失败:', error)
    ElMessage.error('加载验收运行单失败')
  } finally {
    runLoading.value = false
  }
}

const handleQuery = () => {
  queryForm.pageNo = 1
  loadRuns()
}

const handleReset = () => {
  Object.assign(queryForm, { pageNo: 1, releaseCommit: '', environment: '', status: '', decision: '' })
  loadRuns()
}

const openRunDialog = () => {
  resetRunForm()
  runForm.releaseCommit = `local-${Date.now()}`
  runDialogVisible.value = true
}

const submitRun = async () => {
  if (!runFormRef.value) return
  await runFormRef.value.validate(async (valid) => {
    if (!valid) return
    runSubmitting.value = true
    try {
      const run = await createReadinessRun(runForm)
      ElMessage.success('验收运行单创建成功')
      runDialogVisible.value = false
      await loadRuns()
      await openDetail(run)
    } catch (error) {
      ElMessage.error('创建验收运行单失败')
    } finally {
      runSubmitting.value = false
    }
  })
}

const openDetail = async (row: ReadinessRun) => {
  try {
    selectedDetail.value = await getReadinessRunDetail(row.id)
    selectedRun.value = selectedDetail.value.run
    detailVisible.value = true
  } catch (error) {
    ElMessage.error('加载运行单详情失败')
  }
}

const refreshDetail = async () => {
  if (!selectedDetail.value) return
  selectedDetail.value = await getReadinessRunDetail(selectedDetail.value.run.id)
  selectedRun.value = selectedDetail.value.run
}

const handleRecordPreflight = async (row: ReadinessRun) => {
  try {
    await recordReadinessPreflightEvidence(row.id)
    ElMessage.success('预检证据已记录')
    await loadRuns()
    if (selectedDetail.value?.run.id === row.id) {
      await refreshDetail()
    }
  } catch (error) {
    ElMessage.error('记录预检证据失败')
  }
}

const openItemDialog = () => {
  resetItemForm()
  itemDialogVisible.value = true
}

const submitItem = async () => {
  if (!itemFormRef.value || !selectedDetail.value) return
  await itemFormRef.value.validate(async (valid) => {
    if (!valid || !selectedDetail.value) return
    itemSubmitting.value = true
    try {
      await addReadinessItem(selectedDetail.value.run.id, itemForm)
      ElMessage.success('验收项已新增')
      itemDialogVisible.value = false
      await refreshDetail()
      await loadRuns()
    } catch (error) {
      ElMessage.error('新增验收项失败')
    } finally {
      itemSubmitting.value = false
    }
  })
}

const openEvidenceDialog = (row: ReadinessItem) => {
  selectedItem.value = row
  resetEvidenceForm()
  evidenceForm.summary = `${row.itemName} 验收证据`
  evidenceDialogVisible.value = true
}

const submitEvidence = async () => {
  if (!evidenceFormRef.value || !selectedItem.value) return
  await evidenceFormRef.value.validate(async (valid) => {
    if (!valid || !selectedItem.value) return
    evidenceSubmitting.value = true
    try {
      await addReadinessEvidence(selectedItem.value.id, {
        ...evidenceForm,
        httpStatus: evidenceForm.httpStatus || undefined,
        businessId: evidenceForm.businessId || undefined,
        attachmentBusinessId: evidenceForm.attachmentBusinessId || undefined
      })
      ElMessage.success('验收证据已登记')
      evidenceDialogVisible.value = false
      await refreshDetail()
    } catch (error) {
      ElMessage.error('登记验收证据失败')
    } finally {
      evidenceSubmitting.value = false
    }
  })
}

const openResultDialog = (row: ReadinessItem) => {
  selectedItem.value = row
  resetResultForm()
  resultForm.status = row.status === 'PENDING' ? 'PASSED' : row.status
  resultForm.actualResult = row.actualResult || ''
  resultForm.failureReason = row.failureReason || ''
  resultDialogVisible.value = true
}

const submitResult = async () => {
  if (!resultFormRef.value || !selectedItem.value) return
  await resultFormRef.value.validate(async (valid) => {
    if (!valid || !selectedItem.value) return
    resultSubmitting.value = true
    try {
      await markReadinessItemResult(selectedItem.value.id, resultForm)
      ElMessage.success('验收结果已记录')
      resultDialogVisible.value = false
      await refreshDetail()
    } catch (error) {
      ElMessage.error('记录验收结果失败')
    } finally {
      resultSubmitting.value = false
    }
  })
}

const decisionItems = ref<ReadinessItem[]>([])
const decisionItemsLoading = ref(false)

// Go 决策阻塞项：P0/P1 且未通过（对齐后端 hasUnpassedP0P1Items 门禁）
const decisionBlockingItems = computed(() =>
  decisionItems.value.filter(
    (item) => (item.priority === 'P0' || item.priority === 'P1') && item.status !== 'PASSED'
  )
)

const decisionGoBlocked = computed(
  () => decisionForm.decision === 'GO' && decisionBlockingItems.value.length > 0
)

const openDecisionDialog = async (row: ReadinessRun) => {
  selectedRun.value = row
  resetDecisionForm()
  decisionDialogVisible.value = true
  // 拉取该运行单验收项，用于 Go 门禁预检（列表直接打开时 selectedDetail 可能是别的运行单）
  decisionItemsLoading.value = true
  try {
    if (selectedDetail.value?.run.id === row.id) {
      decisionItems.value = selectedDetail.value.items
    } else {
      const detail = await getReadinessRunDetail(row.id)
      decisionItems.value = detail.items
    }
  } catch {
    decisionItems.value = []
    ElMessage.warning('加载验收项失败，Go 门禁预检不可用，提交仍会由后端校验')
  } finally {
    decisionItemsLoading.value = false
  }
}

const submitDecision = async () => {
  if (!decisionFormRef.value || !selectedRun.value) return
  await decisionFormRef.value.validate(async (valid) => {
    if (!valid || !selectedRun.value) return
    if (decisionGoBlocked.value) {
      ElMessage.error(`存在 ${decisionBlockingItems.value.length} 个未通过的 P0/P1 验收项，不能标记发布通过`)
      return
    }
    decisionSubmitting.value = true
    try {
      await decideReadinessRun(selectedRun.value.id, decisionForm)
      ElMessage.success('发布决策已保存')
      decisionDialogVisible.value = false
      await loadRuns()
      if (selectedDetail.value?.run.id === selectedRun.value.id) {
        await refreshDetail()
      }
    } catch (error) {
      ElMessage.error('保存发布决策失败')
    } finally {
      decisionSubmitting.value = false
    }
  })
}

const resetRunForm = () => {
  runFormRef.value?.clearValidate()
  Object.assign(runForm, {
    releaseCommit: '',
    releaseVersion: '',
    environment: 'LOCAL',
    databaseInstance: 'erp_codex_runtime',
    redisInstance: '',
    dockerProfile: 'local',
    generateDefaultItems: true,
    recordPreflightEvidence: true,
    remark: ''
  })
}

const resetItemForm = () => {
  itemFormRef.value?.clearValidate()
  Object.assign(itemForm, { itemCode: '', itemName: '', category: '', priority: 'P1', expectedResult: '' })
}

const resetEvidenceForm = () => {
  evidenceFormRef.value?.clearValidate()
  Object.assign(evidenceForm, {
    evidenceType: 'NOTE',
    requestMethod: '',
    requestUri: '',
    httpStatus: undefined,
    businessType: '',
    businessId: '',
    businessNo: '',
    summary: '',
    detail: '',
    attachmentBusinessType: '',
    attachmentBusinessId: ''
  })
}

const resetResultForm = () => {
  resultFormRef.value?.clearValidate()
  Object.assign(resultForm, { status: 'PASSED', actualResult: '', failureReason: '' })
}

const resetDecisionForm = () => {
  decisionFormRef.value?.clearValidate()
  Object.assign(decisionForm, { decision: 'GO', status: 'PASSED', decisionComment: '' })
}

const isRunClosed = (run: ReadinessRun) => ['PASSED', 'FAILED', 'BLOCKED', 'NO_GO'].includes(run.status)

const preflightStatusLabel = (status: string) => {
  const map: Record<string, string> = { PASS: '通过', WARN: '预警', FAIL: '失败' }
  return map[status] || status || '未检查'
}

const runStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    IN_PROGRESS: '进行中',
    PASSED: '通过',
    FAILED: '失败',
    BLOCKED: '阻塞',
    NO_GO: '不发布'
  }
  return map[status] || status
}

const itemStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待执行',
    PASSED: '通过',
    FAILED: '失败',
    BLOCKED: '阻塞',
    SKIPPED: '跳过'
  }
  return map[status] || status
}

const decisionLabel = (decision: string) => {
  const map: Record<string, string> = { PENDING: '待决策', GO: 'Go', NO_GO: 'No-Go' }
  return map[decision] || decision
}

const preflightTagType = (status: string) => {
  if (status === 'PASS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'WARN') return 'warning'
  return 'info'
}

const runStatusTagType = (status: string) => {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED' || status === 'NO_GO') return 'danger'
  if (status === 'BLOCKED') return 'warning'
  if (status === 'IN_PROGRESS') return 'primary'
  return 'info'
}

const itemStatusTagType = (status: string) => {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'BLOCKED') return 'warning'
  if (status === 'SKIPPED') return 'info'
  return 'primary'
}

const decisionTagType = (decision: string) => {
  if (decision === 'GO') return 'success'
  if (decision === 'NO_GO') return 'danger'
  return 'info'
}

const priorityTagType = (priority: string) => {
  if (priority === 'P0') return 'danger'
  if (priority === 'P1') return 'warning'
  return 'info'
}

const sampleText = (sample?: string[]) => {
  if (!sample || sample.length === 0) return '-'
  return sample.join('；')
}

onMounted(() => {
  loadPreflight()
  loadRuns()
})
</script>

<style scoped>
.readiness-container {
  padding: 20px;
}

.decision-blocking-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.decision-blocking-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-card,
.preflight-card,
.search-card,
.table-card {
  margin-bottom: 20px;
}

.header-row,
.header-actions,
.detail-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-row {
  justify-content: space-between;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
}

.page-subtitle {
  margin-top: 6px;
  color: #606266;
}

.detail-descriptions {
  margin-bottom: 18px;
}

.detail-toolbar {
  justify-content: space-between;
  margin: 8px 0 14px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
}

.evidence-panel {
  padding: 12px 24px;
  background: #fafafa;
}

@media (max-width: 768px) {
  .header-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    flex-wrap: wrap;
  }
}
</style>
