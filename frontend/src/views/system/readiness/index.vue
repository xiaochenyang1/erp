<template>
  <div class="readiness-container">
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div>
          <div class="page-title">{{ $t('systemReadiness.title') }}</div>
          <div class="page-subtitle">{{ $t('systemReadiness.preflightTime', { time: formatLocalizedDateTime(preflight.checkedAt) || '-' }) }}</div>
        </div>
        <div class="header-actions">
          <el-tag :type="preflightTagType(preflight.overallStatus)" size="large">
            {{ preflightStatusLabel(preflight.overallStatus) }}
          </el-tag>
          <el-button :icon="Refresh" :loading="preflightLoading" @click="loadPreflight">{{ $t('systemReadiness.refreshPreflight') }}</el-button>
          <el-button v-permission="'system:readiness:manage'" type="primary" :icon="Plus" @click="openRunDialog">{{ $t('systemReadiness.newRun') }}</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="preflight-card">
      <template #header>
        <span>{{ $t('systemReadiness.preflightTitle') }}</span>
      </template>
      <el-table v-loading="preflightLoading" :data="preflight.items" border stripe>
        <el-table-column prop="code" :label="$t('systemReadiness.checkItem')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="severity" :label="$t('systemReadiness.level')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="priorityTagType(row.severity)">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('systemReadiness.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="preflightTagType(row.status)">{{ preflightStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" :label="$t('systemReadiness.count')" width="100" align="right" />
        <el-table-column prop="summary" :label="$t('systemReadiness.description')" min-width="260" show-overflow-tooltip />
        <el-table-column :label="$t('systemReadiness.sample')" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ sampleText(row.sample) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('systemReadiness.commit')">
          <el-input v-model="queryForm.releaseCommit" :placeholder="$t('systemReadiness.candidateCommitPlaceholder')" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.environment')">
          <el-input v-model="queryForm.environment" :placeholder="$t('systemReadiness.environmentPlaceholder')" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('systemReadiness.select')" clearable style="width: 150px">
            <el-option :label="$t('systemReadiness.statuses.draft')" value="DRAFT" />
            <el-option :label="$t('systemReadiness.statuses.inProgress')" value="IN_PROGRESS" />
            <el-option :label="$t('systemReadiness.statuses.passed')" value="PASSED" />
            <el-option :label="$t('systemReadiness.statuses.failed')" value="FAILED" />
            <el-option :label="$t('systemReadiness.statuses.blocked')" value="BLOCKED" />
            <el-option :label="$t('systemReadiness.statuses.noGo')" value="NO_GO" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.decision')">
          <el-select v-model="queryForm.decision" :placeholder="$t('systemReadiness.select')" clearable style="width: 130px">
            <el-option :label="$t('systemReadiness.decisions.pending')" value="PENDING" />
            <el-option :label="$t('systemReadiness.decisions.go')" value="GO" />
            <el-option :label="$t('systemReadiness.decisions.noGo')" value="NO_GO" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemReadiness.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('systemReadiness.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span>{{ $t('systemReadiness.runsTitle') }}</span>
      </template>
      <el-table v-loading="runLoading" :data="runData" border stripe>
        <el-table-column prop="runNo" :label="$t('systemReadiness.runNo')" width="190" show-overflow-tooltip />
        <el-table-column prop="releaseCommit" :label="$t('systemReadiness.commit')" min-width="190" show-overflow-tooltip />
        <el-table-column prop="releaseVersion" :label="$t('systemReadiness.version')" width="140">
          <template #default="{ row }">{{ row.releaseVersion || '-' }}</template>
        </el-table-column>
        <el-table-column prop="environment" :label="$t('systemReadiness.environment')" width="110" />
        <el-table-column prop="status" :label="$t('systemReadiness.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="runStatusTagType(row.status)">{{ runStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="decision" :label="$t('systemReadiness.decision')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="decisionTagType(row.decision)">{{ decisionLabel(row.decision) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="databaseInstance" :label="$t('systemReadiness.database')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.databaseInstance || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('systemReadiness.createdTime')" width="190">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.createdTime) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('systemReadiness.operations')" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="openDetail(row)">{{ $t('systemReadiness.detail') }}</el-button>
            <el-button
              v-permission="'system:readiness:manage'"
              type="success"
              link
              :icon="DocumentChecked"
              :disabled="isRunClosed(row)"
              @click="handleRecordPreflight(row)"
            >
              {{ $t('systemReadiness.recordPreflight') }}
            </el-button>
            <el-button
              v-permission="'system:readiness:decide'"
              type="warning"
              link
              :icon="CircleCheck"
              :disabled="isRunClosed(row)"
              @click="openDecisionDialog(row)"
            >
              {{ $t('systemReadiness.decision') }}
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
        <span>{{ selectedDetail ? $t('systemReadiness.runDetailWithNo', { runNo: selectedDetail.run.runNo }) : $t('systemReadiness.runDetail') }}</span>
      </template>
      <template v-if="selectedDetail">
        <el-descriptions :column="3" border class="detail-descriptions">
          <el-descriptions-item :label="$t('systemReadiness.commit')">{{ selectedDetail.run.releaseCommit }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemReadiness.environment')">{{ selectedDetail.run.environment }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemReadiness.status')">{{ runStatusLabel(selectedDetail.run.status) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemReadiness.version')">{{ selectedDetail.run.releaseVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemReadiness.database')">{{ selectedDetail.run.databaseInstance || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemReadiness.decision')">{{ decisionLabel(selectedDetail.run.decision) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemReadiness.remark')" :span="3">{{ selectedDetail.run.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('systemReadiness.decisionComment')" :span="3">
            {{ selectedDetail.run.decisionComment || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-toolbar">
          <div class="section-title">{{ $t('systemReadiness.items') }}</div>
          <el-button
            v-permission="'system:readiness:manage'"
            type="primary"
            :icon="Plus"
            :disabled="isRunClosed(selectedDetail.run)"
            @click="openItemDialog"
          >
            {{ $t('systemReadiness.addItem') }}
          </el-button>
        </div>

        <el-table :data="selectedDetail.items" border stripe>
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="evidence-panel">
                <el-empty v-if="row.evidence.length === 0" :description="$t('systemReadiness.noEvidence')" />
                <el-table v-else :data="row.evidence" size="small" border>
                  <el-table-column prop="evidenceType" :label="$t('systemReadiness.type')" width="120" />
                  <el-table-column prop="summary" :label="$t('systemReadiness.summary')" min-width="220" show-overflow-tooltip />
                  <el-table-column prop="requestUri" :label="$t('systemReadiness.endpoint')" min-width="220" show-overflow-tooltip>
                    <template #default="{ row: evidence }">{{ evidence.requestUri || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="businessNo" :label="$t('systemReadiness.businessNo')" width="160" show-overflow-tooltip>
                    <template #default="{ row: evidence }">{{ evidence.businessNo || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="recordedTime" :label="$t('systemReadiness.recordedTime')" width="190">
                    <template #default="{ row }">{{ formatLocalizedDateTime(row.recordedTime) || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="itemCode" :label="$t('systemReadiness.code')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="itemName" :label="$t('systemReadiness.name')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="category" :label="$t('systemReadiness.category')" width="130" />
          <el-table-column prop="priority" :label="$t('systemReadiness.level')" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="priorityTagType(row.priority)">{{ row.priority }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('systemReadiness.status')" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="itemStatusTagType(row.status)">{{ itemStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expectedResult" :label="$t('systemReadiness.expectedResult')" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.expectedResult || '-' }}</template>
          </el-table-column>
          <el-table-column :label="$t('systemReadiness.operations')" width="160" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-permission="'system:readiness:manage'"
                type="primary"
                link
                :disabled="isRunClosed(selectedDetail.run)"
                @click="openEvidenceDialog(row)"
              >
                {{ $t('systemReadiness.evidence') }}
              </el-button>
              <el-button
                v-permission="'system:readiness:manage'"
                type="success"
                link
                :disabled="isRunClosed(selectedDetail.run)"
                @click="openResultDialog(row)"
              >
                {{ $t('systemReadiness.result') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="runDialogVisible" :title="$t('systemReadiness.dialog.newRun')" width="680px" @close="resetRunForm">
      <el-form ref="runFormRef" :model="runForm" :rules="runRules" label-width="130px">
        <el-form-item :label="$t('systemReadiness.candidateCommit')" prop="releaseCommit">
          <el-input v-model="runForm.releaseCommit" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.version')">
          <el-input v-model="runForm.releaseVersion" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.environment')" prop="environment">
          <el-input v-model="runForm.environment" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.databaseInstance')">
          <el-input v-model="runForm.databaseInstance" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.redisInstance')">
          <el-input v-model="runForm.redisInstance" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.dockerProfile')">
          <el-input v-model="runForm.dockerProfile" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.defaultItems')">
          <el-switch v-model="runForm.generateDefaultItems" :active-text="$t('systemReadiness.generate')" :inactive-text="$t('systemReadiness.doNotGenerate')" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.recordPreflightEvidence')">
          <el-switch v-model="runForm.recordPreflightEvidence" :active-text="$t('systemReadiness.record')" :inactive-text="$t('systemReadiness.doNotRecord')" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.remark')">
          <el-input v-model="runForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialogVisible = false">{{ $t('systemReadiness.cancel') }}</el-button>
        <el-button type="primary" :loading="runSubmitting" @click="submitRun">{{ $t('systemReadiness.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" :title="$t('systemReadiness.dialog.addItem')" width="620px" @close="resetItemForm">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="110px">
        <el-form-item :label="$t('systemReadiness.code')" prop="itemCode">
          <el-input v-model="itemForm.itemCode" :placeholder="$t('systemReadiness.itemCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.name')" prop="itemName">
          <el-input v-model="itemForm.itemName" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.category')" prop="category">
          <el-input v-model="itemForm.category" :placeholder="$t('systemReadiness.categoryPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.level')" prop="priority">
          <el-select v-model="itemForm.priority" style="width: 100%">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.expectedResult')">
          <el-input v-model="itemForm.expectedResult" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">{{ $t('systemReadiness.cancel') }}</el-button>
        <el-button type="primary" :loading="itemSubmitting" @click="submitItem">{{ $t('systemReadiness.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evidenceDialogVisible" :title="$t('systemReadiness.dialog.addEvidence')" width="700px" @close="resetEvidenceForm">
      <el-form ref="evidenceFormRef" :model="evidenceForm" :rules="evidenceRules" label-width="110px">
        <el-form-item :label="$t('systemReadiness.evidenceType')" prop="evidenceType">
          <el-select v-model="evidenceForm.evidenceType" style="width: 100%">
            <el-option :label="$t('systemReadiness.evidenceTypes.api')" value="API" />
            <el-option :label="$t('systemReadiness.evidenceTypes.businessNo')" value="BUSINESS_NO" />
            <el-option :label="$t('systemReadiness.evidenceTypes.log')" value="LOG" />
            <el-option :label="$t('systemReadiness.evidenceTypes.screenshot')" value="SCREENSHOT" />
            <el-option :label="$t('systemReadiness.evidenceTypes.note')" value="NOTE" />
            <el-option :label="$t('systemReadiness.evidenceTypes.attachment')" value="ATTACHMENT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.summary')" prop="summary">
          <el-input v-model="evidenceForm.summary" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.requestMethod')">
          <el-input v-model="evidenceForm.requestMethod" placeholder="GET / POST" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.requestUri')">
          <el-input v-model="evidenceForm.requestUri" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.httpStatus')">
          <el-input-number v-model="evidenceForm.httpStatus" :min="100" :max="599" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.businessType')">
          <el-input v-model="evidenceForm.businessType" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.businessId')">
          <el-input v-model="evidenceForm.businessId" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.businessNo')">
          <el-input v-model="evidenceForm.businessNo" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.attachmentBusinessType')">
          <el-input v-model="evidenceForm.attachmentBusinessType" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.attachmentBusinessId')">
          <el-input v-model="evidenceForm.attachmentBusinessId" clearable />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.details')">
          <el-input v-model="evidenceForm.detail" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evidenceDialogVisible = false">{{ $t('systemReadiness.cancel') }}</el-button>
        <el-button type="primary" :loading="evidenceSubmitting" @click="submitEvidence">{{ $t('systemReadiness.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialogVisible" :title="$t('systemReadiness.dialog.recordResult')" width="620px" @close="resetResultForm">
      <el-form ref="resultFormRef" :model="resultForm" :rules="resultRules" label-width="110px">
        <el-form-item :label="$t('systemReadiness.status')" prop="status">
          <el-select v-model="resultForm.status" style="width: 100%">
            <el-option :label="$t('systemReadiness.statuses.passed')" value="PASSED" />
            <el-option :label="$t('systemReadiness.statuses.failed')" value="FAILED" />
            <el-option :label="$t('systemReadiness.statuses.blocked')" value="BLOCKED" />
            <el-option :label="$t('systemReadiness.statuses.skipped')" value="SKIPPED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.actualResult')">
          <el-input v-model="resultForm.actualResult" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.failureOrSkipReason')">
          <el-input v-model="resultForm.failureReason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultDialogVisible = false">{{ $t('systemReadiness.cancel') }}</el-button>
        <el-button type="primary" :loading="resultSubmitting" @click="submitResult">{{ $t('systemReadiness.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionDialogVisible" :title="$t('systemReadiness.dialog.releaseDecision')" width="560px" @close="resetDecisionForm">
      <el-form ref="decisionFormRef" :model="decisionForm" :rules="decisionRules" label-width="110px">
        <el-form-item :label="$t('systemReadiness.decision')" prop="decision">
          <el-select v-model="decisionForm.decision" style="width: 100%">
            <el-option :label="$t('systemReadiness.decisions.go')" value="GO" />
            <el-option :label="$t('systemReadiness.decisions.noGo')" value="NO_GO" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.runStatus')" prop="status">
          <el-select v-model="decisionForm.status" style="width: 100%">
            <el-option
              v-for="option in decisionStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemReadiness.decisionComment')">
          <el-input v-model="decisionForm.decisionComment" type="textarea" :rows="4" />
        </el-form-item>
        <el-alert
          v-if="decisionForm.decision === 'GO' && decisionBlockingItems.length > 0"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 8px"
          :title="$t('systemReadiness.message.decisionBlocked', { count: decisionBlockingItems.length })"
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
          :title="$t('systemReadiness.message.decisionReady')"
        />
      </el-form>
      <template #footer>
        <el-button @click="decisionDialogVisible = false">{{ $t('systemReadiness.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="decisionSubmitting"
          :disabled="decisionGoBlocked || decisionItemsLoading"
          @click="submitDecision"
        >{{ $t('systemReadiness.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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

const runRules = computed<FormRules>(() => ({
  releaseCommit: [{ required: true, message: t('systemReadiness.validation.candidateCommit'), trigger: 'blur' }],
  environment: [{ required: true, message: t('systemReadiness.validation.environment'), trigger: 'blur' }]
}))

const itemRules = computed<FormRules>(() => ({
  itemCode: [{ required: true, message: t('systemReadiness.validation.itemCode'), trigger: 'blur' }],
  itemName: [{ required: true, message: t('systemReadiness.validation.itemName'), trigger: 'blur' }],
  category: [{ required: true, message: t('systemReadiness.validation.category'), trigger: 'blur' }],
  priority: [{ required: true, message: t('systemReadiness.validation.level'), trigger: 'change' }]
}))

const evidenceRules = computed<FormRules>(() => ({
  evidenceType: [{ required: true, message: t('systemReadiness.validation.evidenceType'), trigger: 'change' }],
  summary: [{ required: true, message: t('systemReadiness.validation.summary'), trigger: 'blur' }]
}))

const resultRules = computed<FormRules>(() => ({
  status: [{ required: true, message: t('systemReadiness.validation.status'), trigger: 'change' }]
}))

const decisionRules = computed<FormRules>(() => ({
  decision: [{ required: true, message: t('systemReadiness.validation.decision'), trigger: 'change' }],
  status: [{ required: true, message: t('systemReadiness.validation.runStatus'), trigger: 'change' }]
}))

const decisionStatusOptions = computed(() => {
  if (decisionForm.decision === 'GO') {
    return [{ label: t('systemReadiness.statuses.passed'), value: 'PASSED' }]
  }
  return [
    { label: t('systemReadiness.statuses.failed'), value: 'FAILED' },
    { label: t('systemReadiness.statuses.blocked'), value: 'BLOCKED' },
    { label: t('systemReadiness.statuses.noGo'), value: 'NO_GO' }
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
    console.error(t('systemReadiness.message.loadPreflightFailed'), error)
    ElMessage.error(t('systemReadiness.message.loadPreflightFailed'))
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
    console.error(t('systemReadiness.message.loadRunsFailed'), error)
    ElMessage.error(t('systemReadiness.message.loadRunsFailed'))
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
      ElMessage.success(t('systemReadiness.message.runCreated'))
      runDialogVisible.value = false
      await loadRuns()
      await openDetail(run)
    } catch (error) {
      ElMessage.error(t('systemReadiness.message.createRunFailed'))
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
    ElMessage.error(t('systemReadiness.message.loadRunDetailFailed'))
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
    ElMessage.success(t('systemReadiness.message.preflightRecorded'))
    await loadRuns()
    if (selectedDetail.value?.run.id === row.id) {
      await refreshDetail()
    }
  } catch (error) {
    ElMessage.error(t('systemReadiness.message.recordPreflightFailed'))
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
      ElMessage.success(t('systemReadiness.message.itemAdded'))
      itemDialogVisible.value = false
      await refreshDetail()
      await loadRuns()
    } catch (error) {
      ElMessage.error(t('systemReadiness.message.addItemFailed'))
    } finally {
      itemSubmitting.value = false
    }
  })
}

const openEvidenceDialog = (row: ReadinessItem) => {
  selectedItem.value = row
  resetEvidenceForm()
  evidenceForm.summary = t('systemReadiness.defaultEvidenceSummary', { name: row.itemName })
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
      ElMessage.success(t('systemReadiness.message.evidenceAdded'))
      evidenceDialogVisible.value = false
      await refreshDetail()
    } catch (error) {
      ElMessage.error(t('systemReadiness.message.addEvidenceFailed'))
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
      ElMessage.success(t('systemReadiness.message.resultRecorded'))
      resultDialogVisible.value = false
      await refreshDetail()
    } catch (error) {
      ElMessage.error(t('systemReadiness.message.recordResultFailed'))
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
    ElMessage.warning(t('systemReadiness.message.loadItemsWarning'))
  } finally {
    decisionItemsLoading.value = false
  }
}

const submitDecision = async () => {
  if (!decisionFormRef.value || !selectedRun.value) return
  await decisionFormRef.value.validate(async (valid) => {
    if (!valid || !selectedRun.value) return
    if (decisionGoBlocked.value) {
      ElMessage.error(t('systemReadiness.message.decisionBlocked', { count: decisionBlockingItems.value.length }))
      return
    }
    decisionSubmitting.value = true
    try {
      await decideReadinessRun(selectedRun.value.id, decisionForm)
      ElMessage.success(t('systemReadiness.message.decisionSaved'))
      decisionDialogVisible.value = false
      await loadRuns()
      if (selectedDetail.value?.run.id === selectedRun.value.id) {
        await refreshDetail()
      }
    } catch (error) {
      ElMessage.error(t('systemReadiness.message.saveDecisionFailed'))
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
  const map: Record<string, string> = {
    PASS: t('systemReadiness.statuses.passed'),
    WARN: t('systemReadiness.statuses.warning'),
    FAIL: t('systemReadiness.statuses.failed')
  }
  return map[status] || status || t('systemReadiness.statuses.unchecked')
}

const runStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: t('systemReadiness.statuses.draft'),
    IN_PROGRESS: t('systemReadiness.statuses.inProgress'),
    PASSED: t('systemReadiness.statuses.passed'),
    FAILED: t('systemReadiness.statuses.failed'),
    BLOCKED: t('systemReadiness.statuses.blocked'),
    NO_GO: t('systemReadiness.statuses.noGo')
  }
  return map[status] || status
}

const itemStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: t('systemReadiness.statuses.pending'),
    PASSED: t('systemReadiness.statuses.passed'),
    FAILED: t('systemReadiness.statuses.failed'),
    BLOCKED: t('systemReadiness.statuses.blocked'),
    SKIPPED: t('systemReadiness.statuses.skipped')
  }
  return map[status] || status
}

const decisionLabel = (decision: string) => {
  const map: Record<string, string> = {
    PENDING: t('systemReadiness.decisions.pending'),
    GO: t('systemReadiness.decisions.go'),
    NO_GO: t('systemReadiness.decisions.noGo')
  }
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
  return sample.join(t('systemReadiness.listSeparator'))
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
