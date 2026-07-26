<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane :label="$t('systemLogs.tabs.operation')" name="operation">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="$t('systemLogs.module')">
          <el-input v-model="queryForm.module" :placeholder="$t('systemLogs.modulePlaceholder')" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item :label="$t('systemLogs.operation')">
          <el-input v-model="queryForm.operation" :placeholder="$t('systemLogs.operationPlaceholder')" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item :label="$t('systemLogs.businessNo')">
          <el-input v-model="queryForm.bizNo" :placeholder="$t('systemLogs.businessNoPlaceholder')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('systemLogs.operator')">
          <el-input v-model="queryForm.operatorName" :placeholder="$t('systemLogs.operatorPlaceholder')" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item :label="$t('systemLogs.status')">
          <el-select v-model="queryForm.status" :placeholder="$t('systemLogs.select')" clearable style="width: 120px">
            <el-option :label="$t('systemLogs.success')" value="SUCCESS" />
            <el-option :label="$t('systemLogs.failure')" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('systemLogs.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('systemLogs.rangeSeparator')"
            :start-placeholder="$t('systemLogs.startDate')"
            :end-placeholder="$t('systemLogs.endDate')"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('systemLogs.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('systemLogs.reset') }}</el-button>
          <el-button v-permission="'system:log:view'" :icon="Download" @click="handleExport">{{ $t('systemLogs.export') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <span>{{ $t('systemLogs.tabs.operation') }}</span>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-descriptions :column="1" border>
              <el-descriptions-item :label="$t('systemLogs.requestUrl')">
                {{ row.requestUrl }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('systemLogs.requestMethod')">
                {{ row.method }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('systemLogs.businessNo')" v-if="row.bizNo">
                {{ row.bizNo }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('systemLogs.logMessage')" v-if="row.message">
                {{ row.message }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('systemLogs.requestParams')" v-if="row.requestParams">
                <el-text tag="pre" style="max-height: 200px; overflow: auto">{{ formatJson(row.requestParams) }}</el-text>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('systemLogs.responseData')" v-if="row.responseData">
                <el-text tag="pre" style="max-height: 200px; overflow: auto">{{ formatJson(row.responseData) }}</el-text>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('systemLogs.userAgent')" v-if="row.userAgent">
                {{ row.userAgent }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('systemLogs.errorMessage')" v-if="row.errorMsg">
                <el-text type="danger">{{ row.errorMsg }}</el-text>
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </el-table-column>
        <el-table-column prop="module" :label="$t('systemLogs.module')" width="100" />
        <el-table-column prop="operation" :label="$t('systemLogs.operation')" width="120" />
        <el-table-column prop="operatorName" :label="$t('systemLogs.operator')" width="120" />
        <el-table-column prop="ipAddress" :label="$t('systemLogs.ipAddress')" width="140" />
        <el-table-column prop="status" :label="$t('systemLogs.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? $t('systemLogs.success') : $t('systemLogs.failure') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executionTime" :label="$t('systemLogs.executionTimeMs')" width="100" align="right">
          <template #default="{ row }">
            <el-text :type="getExecutionTimeType(row.executionTime)">
              {{ row.executionTime }}
            </el-text>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('systemLogs.operationTime')" width="190">
          <template #default="{ row }">{{ formatLocalizedDateTime(row.createdAt) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('systemLogs.operations')" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">
              {{ $t('systemLogs.view') }}
            </el-button>
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
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

      </el-tab-pane>

      <el-tab-pane :label="$t('systemLogs.tabs.login')" name="login">
        <el-card shadow="never" class="search-card">
          <el-form :model="loginQueryForm" inline>
            <el-form-item :label="$t('systemLogs.username')">
              <el-input v-model="loginQueryForm.username" :placeholder="$t('systemLogs.usernamePlaceholder')" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item :label="$t('systemLogs.result')">
              <el-select v-model="loginQueryForm.result" :placeholder="$t('systemLogs.select')" clearable style="width: 120px">
                <el-option :label="$t('systemLogs.success')" value="SUCCESS" />
                <el-option :label="$t('systemLogs.failure')" value="FAIL" />
                <el-option :label="$t('systemLogs.failure')" value="FAILURE" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('systemLogs.dateRange')">
              <el-date-picker
                v-model="loginDateRange"
                type="daterange"
                :range-separator="$t('systemLogs.rangeSeparator')"
                :start-placeholder="$t('systemLogs.startDate')"
                :end-placeholder="$t('systemLogs.endDate')"
                value-format="YYYY-MM-DD"
                style="width: 280px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleLoginQuery">{{ $t('systemLogs.search') }}</el-button>
              <el-button :icon="Refresh" @click="handleLoginReset">{{ $t('systemLogs.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <span>{{ $t('systemLogs.tabs.login') }}</span>
          </template>

          <el-table v-loading="loginLoading" :data="loginTableData" border stripe>
            <el-table-column prop="username" :label="$t('systemLogs.username')" width="140" />
            <el-table-column prop="userId" :label="$t('systemLogs.userId')" width="160" show-overflow-tooltip />
            <el-table-column prop="result" :label="$t('systemLogs.result')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="isSuccess(row.result) ? 'success' : 'danger'" size="small">
                  {{ isSuccess(row.result) ? $t('systemLogs.success') : $t('systemLogs.failure') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" :label="$t('systemLogs.messageLabel')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="loginIp" :label="$t('systemLogs.loginIp')" width="150" />
            <el-table-column prop="loginTime" :label="$t('systemLogs.loginTime')" width="170" />
            <el-table-column prop="userAgent" :label="$t('systemLogs.userAgent')" min-width="260" show-overflow-tooltip />
          </el-table>

          <el-pagination
            v-model:current-page="loginPagination.page"
            v-model:page-size="loginPagination.size"
            :total="loginPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleLoginSizeChange"
            @current-change="handleLoginPageChange"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="$t('systemLogs.tabs.audit')" name="audit">
        <el-card shadow="never" class="search-card">
          <el-form :model="auditQueryForm" inline>
            <el-form-item :label="$t('systemLogs.auditType')">
              <el-input v-model="auditQueryForm.auditType" :placeholder="$t('systemLogs.auditTypePlaceholder')" clearable style="width: 150px" />
            </el-form-item>
            <el-form-item :label="$t('systemLogs.businessType')">
              <el-input v-model="auditQueryForm.businessType" :placeholder="$t('systemLogs.businessTypePlaceholder')" clearable style="width: 150px" />
            </el-form-item>
            <el-form-item :label="$t('systemLogs.businessNo')">
              <el-input v-model="auditQueryForm.businessNo" :placeholder="$t('systemLogs.businessNoPlaceholder')" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item :label="$t('systemLogs.action')">
              <el-input v-model="auditQueryForm.action" :placeholder="$t('systemLogs.actionPlaceholder')" clearable style="width: 140px" />
            </el-form-item>
            <el-form-item :label="$t('systemLogs.operator')">
              <el-input v-model="auditQueryForm.operatorName" :placeholder="$t('systemLogs.operatorPlaceholder')" clearable style="width: 150px" />
            </el-form-item>
            <el-form-item :label="$t('systemLogs.dateRange')">
              <el-date-picker
                v-model="auditDateRange"
                type="daterange"
                :range-separator="$t('systemLogs.rangeSeparator')"
                :start-placeholder="$t('systemLogs.startDate')"
                :end-placeholder="$t('systemLogs.endDate')"
                value-format="YYYY-MM-DD"
                style="width: 280px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleAuditQuery">{{ $t('systemLogs.search') }}</el-button>
              <el-button :icon="Refresh" @click="handleAuditReset">{{ $t('systemLogs.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <span>{{ $t('systemLogs.tabs.audit') }}</span>
          </template>

          <el-table v-loading="auditLoading" :data="auditTableData" border stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <el-descriptions :column="1" border>
                  <el-descriptions-item :label="$t('systemLogs.snapshot')" v-if="row.snapshotJson">
                    <el-text tag="pre" style="max-height: 240px; overflow: auto">{{ formatJson(row.snapshotJson) }}</el-text>
                  </el-descriptions-item>
                  <el-descriptions-item :label="$t('systemLogs.messageLabel')" v-if="row.message">
                    {{ row.message }}
                  </el-descriptions-item>
                </el-descriptions>
              </template>
            </el-table-column>
            <el-table-column prop="auditType" :label="$t('systemLogs.auditType')" width="130" />
            <el-table-column prop="businessType" :label="$t('systemLogs.businessType')" width="140" />
            <el-table-column prop="businessNo" :label="$t('systemLogs.businessNo')" width="160" show-overflow-tooltip />
            <el-table-column prop="businessId" :label="$t('systemLogs.businessId')" width="160" show-overflow-tooltip />
            <el-table-column prop="action" :label="$t('systemLogs.action')" width="120" />
            <el-table-column prop="operatorName" :label="$t('systemLogs.operator')" width="140" />
            <el-table-column prop="operatorId" :label="$t('systemLogs.operatorId')" width="160" show-overflow-tooltip />
            <el-table-column prop="auditTime" :label="$t('systemLogs.auditTime')" width="170" />
            <el-table-column prop="message" :label="$t('systemLogs.messageLabel')" min-width="220" show-overflow-tooltip />
          </el-table>

          <el-pagination
            v-model:current-page="auditPagination.page"
            v-model:page-size="auditPagination.size"
            :total="auditPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleAuditSizeChange"
            @current-change="handleAuditPageChange"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" :title="$t('systemLogs.detailTitle')" width="900px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="$t('systemLogs.logId')">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.module')">{{ detailData.module }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.operation')">{{ detailData.operation }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.operator')">{{ detailData.operatorName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.ipAddress')">{{ detailData.ipAddress }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.status')">
          <el-tag :type="detailData.status === 'SUCCESS' ? 'success' : 'danger'">
            {{ detailData.status === 'SUCCESS' ? $t('systemLogs.success') : $t('systemLogs.failure') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.executionTime')">
          <el-text :type="getExecutionTimeType(detailData.executionTime)">
            {{ detailData.executionTime }} ms
          </el-text>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.operationTime')">{{ formatLocalizedDateTime(detailData.createdAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.requestUrl')" :span="2">{{ detailData.requestUrl }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.requestMethod')" :span="2">{{ detailData.method }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.businessNo')" :span="2">{{ detailData.bizNo || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.logMessage')" :span="2">{{ detailData.message || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('systemLogs.userAgent')" :span="2">{{ detailData.userAgent }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <h4>{{ $t('systemLogs.requestParams') }}</h4>
      <el-input
        v-model="detailData.requestParams"
        type="textarea"
        :rows="8"
        readonly
        style="margin-top: 10px"
      />

      <el-divider v-if="detailData.responseData" />

      <h4 v-if="detailData.responseData">{{ $t('systemLogs.responseData') }}</h4>
      <el-input
        v-if="detailData.responseData"
        v-model="detailData.responseData"
        type="textarea"
        :rows="8"
        readonly
        style="margin-top: 10px"
      />

      <el-divider v-if="detailData.errorMsg" />

      <h4 v-if="detailData.errorMsg">{{ $t('systemLogs.errorMessage') }}</h4>
      <el-alert
        v-if="detailData.errorMsg"
        :title="detailData.errorMsg"
        type="error"
        :closable="false"
        style="margin-top: 10px"
      />

      <template #footer>
        <el-button @click="detailDialogVisible = false">{{ $t('systemLogs.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search, View } from '@element-plus/icons-vue'
import { formatLocalizedDateTime } from '@/utils/locale'
import { downloadBlob } from '@/utils/download'
import {
  exportOperationLogs,
  getAuditLogs,
  getLoginLogs,
  getOperationLog,
  getOperationLogs
} from '@/api/system'
import { useSystemLogPresentation } from '@/composables/useSystemLogPresentation'
import { useSystemLogList } from '@/composables/useSystemLogList'

const { t } = useI18n()
const route = useRoute()
const readQueryString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : typeof value === 'string' ? value : ''
}

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const {
  activeTab,
  auditDateRange,
  auditLoading,
  auditPagination,
  auditQueryForm,
  auditTableData,
  dateRange,
  detailData,
  detailDialogVisible,
  handleAuditPageChange,
  handleAuditQuery,
  handleAuditReset,
  handleAuditSizeChange,
  handleExport,
  handleLoginPageChange,
  handleLoginQuery,
  handleLoginReset,
  handleLoginSizeChange,
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  handleTabChange,
  handleView,
  loadData,
  loading,
  loginDateRange,
  loginLoading,
  loginPagination,
  loginQueryForm,
  loginTableData,
  pagination,
  queryForm,
  tableData
} = useSystemLogList(t, {
  getOperationLogs,
  getOperationLog,
  exportOperationLogs,
  getLoginLogs,
  getAuditLogs,
  downloadBlob,
  initialBizNo: readQueryString('keyword'),
  initialAuditBusinessNo: readQueryString('keyword'),
  ...notify
})

// Keep the document-trace deep-link seed assignment on the page for contract scanning.
queryForm.bizNo = readQueryString('keyword')
auditQueryForm.businessNo = readQueryString('keyword')

const {
  formatJson,
  getExecutionTimeType,
  isSuccess
} = useSystemLogPresentation()

onMounted(() => {
  loadData()
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
