<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('productionWorkCenter.keyword')">
          <el-input
            v-model="queryForm.keyword"
            :placeholder="t('productionWorkCenter.keywordPlaceholder')"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item :label="t('productionWorkCenter.statusLabel')">
          <el-select v-model="queryForm.status" :placeholder="t('productionWorkCenter.all')" clearable style="width: 120px">
            <el-option :label="t('productionWorkCenter.status.active')" value="ACTIVE" />
            <el-option :label="t('productionWorkCenter.status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('productionWorkCenter.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('productionWorkCenter.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('productionWorkCenter.title') }}</span>
          <el-button
            v-permission="'production:work-center:create'"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >{{ t('productionWorkCenter.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="workCenterCode" :label="t('productionWorkCenter.code')" width="180" />
        <el-table-column prop="workCenterName" :label="t('productionWorkCenter.name')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('productionWorkCenter.statusLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('productionWorkCenter.remark')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('productionWorkCenter.actions')" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePrint(row)">{{ t('productionWorkCenter.print') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:work-center:update'"
              type="primary"
              link
              :icon="Edit"
              @click="handleEdit(row)"
            >{{ t('productionWorkCenter.edit') }}</el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'production:work-center:enable'"
              type="success"
              link
              @click="handleEnable(row)"
            >{{ t('productionWorkCenter.enable') }}</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'production:work-center:disable'"
              type="danger"
              link
              @click="handleDisable(row)"
            >{{ t('productionWorkCenter.disable') }}</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item :label="t('productionWorkCenter.code')" prop="workCenterCode">
          <el-input
            v-model="formData.workCenterCode"
            :placeholder="t('productionWorkCenter.codePlaceholder')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('productionWorkCenter.name')" prop="workCenterName">
          <el-input v-model="formData.workCenterName" :placeholder="t('productionWorkCenter.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('productionWorkCenter.remark')" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" :placeholder="t('productionWorkCenter.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('productionWorkCenter.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ t('productionWorkCenter.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import {
  getWorkCenters,
  getWorkCenter,
  createWorkCenter,
  updateWorkCenter,
  enableWorkCenter,
  disableWorkCenter
} from '@/api/production'
import { printProductionWorkCenter } from '@/utils/bizPrint'
import { useProductionWorkCenterPresentation } from '@/composables/useProductionWorkCenterPresentation'
import { useProductionWorkCenterList } from '@/composables/useProductionWorkCenterList'
import { useProductionWorkCenterForm } from '@/composables/useProductionWorkCenterForm'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const notify = {
  onError: (message: string) => ElMessage.error(message),
  onSuccess: (message: string) => ElMessage.success(message)
}

const {
  handleDisable,
  handleEnable,
  handlePageChange,
  handlePrint,
  handleQuery,
  handleReset,
  handleSizeChange,
  loadData,
  loading,
  pagination,
  queryForm,
  tableData
} = useProductionWorkCenterList(t, {
  getWorkCenters,
  getWorkCenter,
  enableWorkCenter,
  disableWorkCenter,
  printWorkCenter: printProductionWorkCenter,
  confirm: (message, title, options) => ElMessageBox.confirm(message, title, options as any),
  ...notify
})

const {
  getStatusLabel,
  getStatusType
} = useProductionWorkCenterPresentation(t)

const {
  dialogTitle,
  dialogVisible,
  formData,
  handleAdd,
  handleEdit,
  handleSubmit: submit,
  isEdit,
  resetForm,
  submitLoading
} = useProductionWorkCenterForm(t, {
  createWorkCenter,
  updateWorkCenter,
  onSubmitted: loadData,
  ...notify
})

const formRules = computed<FormRules>(() => ({
  workCenterCode: [{ required: true, message: t('productionWorkCenter.validation.code'), trigger: 'blur' }],
  workCenterName: [{ required: true, message: t('productionWorkCenter.validation.name'), trigger: 'blur' }]
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

onMounted(loadData)
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
