<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('productionRouting.keyword')">
          <el-input
            v-model="queryForm.keyword"
            :placeholder="t('productionRouting.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item :label="t('productionRouting.statusLabel')">
          <el-select v-model="queryForm.status" :placeholder="t('productionRouting.all')" clearable style="width: 120px">
            <el-option :label="t('productionRouting.status.active')" value="ACTIVE" />
            <el-option :label="t('productionRouting.status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('productionRouting.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('productionRouting.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('productionRouting.title') }}</span>
          <el-button
            v-permission="'production:routing:create'"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >{{ t('productionRouting.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="routingCode" :label="t('productionRouting.code')" width="170" />
        <el-table-column prop="routingName" :label="t('productionRouting.name')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('productionRouting.bom')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bomNo || row.bomId }}</template>
        </el-table-column>
        <el-table-column :label="t('productionRouting.operationCount')" width="90" align="right">
          <template #default="{ row }">{{ row.operations?.length || 0 }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('productionRouting.statusLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('productionRouting.remark')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="t('productionRouting.actions')" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">{{ t('productionRouting.view') }}</el-button>
            <el-button type="primary" link @click="handlePrint(row)">{{ t('productionRouting.print') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:routing:update'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >{{ t('productionRouting.edit') }}</el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'production:routing:enable'"
              type="success"
              link
              @click="handleEnable(row)"
            >{{ t('productionRouting.enable') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:routing:disable'"
              type="danger"
              link
              @click="handleDisable(row)"
            >{{ t('productionRouting.disable') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1000px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('productionRouting.routingCode')" prop="routingCode">
              <el-input
                v-model="formData.routingCode"
                :placeholder="t('productionRouting.codePlaceholder')"
                :disabled="isEdit"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('productionRouting.routingName')" prop="routingName">
              <el-input v-model="formData.routingName" :placeholder="t('productionRouting.namePlaceholder')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="t('productionRouting.bom')" prop="bomId">
          <el-select
            v-model="formData.bomId"
            :placeholder="t('productionRouting.selectBom')"
            filterable
            :disabled="isEdit"
            style="width: 100%"
          >
            <el-option
              v-for="item in bomOptions"
              :key="item.id"
              :label="bomLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">{{ t('productionRouting.operationList') }}</el-divider>

        <el-form-item :label="t('productionRouting.operationDetails')" required>
          <el-table :data="formData.operations" border style="width: 100%">
            <el-table-column type="index" :label="t('productionRouting.sequence')" width="50" />
            <el-table-column :label="t('productionRouting.operationCode')" width="150">
              <template #default="{ row }">
                <el-input v-model="row.operationCode" :placeholder="t('productionRouting.operationCode')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('productionRouting.operationName')" width="160">
              <template #default="{ row }">
                <el-input v-model="row.operationName" :placeholder="t('productionRouting.operationName')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('productionRouting.workCenter')" min-width="200">
              <template #default="{ row }">
                <el-select v-model="row.workCenterId" :placeholder="t('productionRouting.selectWorkCenter')" filterable style="width: 100%">
                  <el-option
                    v-for="wc in workCenterOptions"
                    :key="wc.id"
                    :label="`${wc.workCenterCode} - ${wc.workCenterName}`"
                    :value="wc.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('productionRouting.standardMinutes')" width="140">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.standardMinutes"
                  :min="0.01"
                  :precision="2"
                  :controls="false"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column :label="t('productionRouting.remark')" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.remark" :placeholder="t('productionRouting.optional')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('productionRouting.actions')" width="70" align="center" fixed="right">
              <template #default="{ $index }">
                <el-button type="danger" link :icon="Delete" @click="handleDeleteOperation($index)">{{ t('productionRouting.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" :icon="Plus" style="margin-top: 10px" @click="handleAddOperation">{{ t('productionRouting.addOperation') }}</el-button>
        </el-form-item>

        <el-form-item :label="t('productionRouting.remark')" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="2" :placeholder="t('productionRouting.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('productionRouting.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ t('productionRouting.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" :title="t('productionRouting.detailTitle')" width="900px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('productionRouting.routingCode')">{{ viewData.routingCode }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionRouting.routingName')">{{ viewData.routingName }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionRouting.bom')">{{ viewData.bomNo || viewData.bomId }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionRouting.statusLabel')">
          <el-tag :type="getStatusType(viewData.status)">{{ getStatusLabel(viewData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('productionRouting.remark')" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <h4>{{ t('productionRouting.operationList') }}</h4>
      <el-table :data="viewData.operations || []" border stripe style="margin-top: 10px">
        <el-table-column prop="lineNo" :label="t('productionRouting.sequence')" width="60" />
        <el-table-column prop="operationCode" :label="t('productionRouting.operationCode')" width="140" />
        <el-table-column prop="operationName" :label="t('productionRouting.operationName')" min-width="150" />
        <el-table-column :label="t('productionRouting.workCenter')" min-width="180">
          <template #default="{ row }">{{ workCenterLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="standardMinutes" :label="t('productionRouting.standardMinutes')" width="130" align="right" />
        <el-table-column prop="remark" :label="t('productionRouting.remark')" min-width="120" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <el-button @click="viewDialogVisible = false">{{ t('productionRouting.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit, View, Delete } from '@element-plus/icons-vue'
import {
  getRoutings,
  getRouting,
  createRouting,
  updateRouting,
  enableRouting,
  disableRouting,
  getWorkCenters,
  getBOMs,
  type Routing
} from '@/api/production'
import { printProductionRouting } from '@/utils/bizPrint'
import { useProductionRoutingPresentation } from '@/composables/useProductionRoutingPresentation'
import { useProductionRoutingList } from '@/composables/useProductionRoutingList'
import { useProductionRoutingForm } from '@/composables/useProductionRoutingForm'

const { t } = useI18n()

const formRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message),
  onWarning: (message: string) => ElMessage.warning(message)
}

const {
  bomOptions,
  handleDisable,
  handleEnable,
  handlePageChange,
  handlePrint,
  handleQuery,
  handleReset,
  handleSizeChange,
  handleView,
  loadData,
  loadOptions,
  loading,
  pagination,
  queryForm,
  tableData,
  viewData,
  viewDialogVisible,
  workCenterOptions
} = useProductionRoutingList(t, {
  getRoutings,
  getRouting,
  getWorkCenters,
  getBOMs,
  enableRouting,
  disableRouting,
  printRouting: printProductionRouting,
  decoratePrint: (detail) => ({
    ...detail,
    operations: (detail.operations || []).map((operation) => ({
      ...operation,
      workCenterName: operation.workCenterName || workCenterLabel(operation)
    }))
  } as Routing),
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  onError: notify.onError,
  onSuccess: notify.onSuccess
})

const {
  bomLabel,
  getStatusLabel,
  getStatusType,
  workCenterLabel
} = useProductionRoutingPresentation(t, { workCenters: workCenterOptions })

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleAdd,
  handleAddOperation,
  handleDeleteOperation,
  handleEdit,
  handleSubmit: submit,
  isEdit,
  resetForm,
  submitLoading
} = useProductionRoutingForm(t, {
  getRouting,
  createRouting,
  updateRouting,
  onSubmitted: loadData,
  ...notify
})

const formRules = computed<FormRules>(() => ({
  routingCode: [{ required: true, message: t('productionRouting.validation.code'), trigger: 'blur' }],
  routingName: [{ required: true, message: t('productionRouting.validation.name'), trigger: 'blur' }],
  bomId: [{ required: true, message: t('productionRouting.validation.bom'), trigger: 'change' }]
}))

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await submit()
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

onMounted(() => {
  loadOptions()
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
