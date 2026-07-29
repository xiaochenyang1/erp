<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item :label="t('warehouseLocation.warehouse')">
          <el-select v-model="query.warehouseId" clearable filterable style="width: 220px" :placeholder="t('warehouseLocation.selectWarehouse')">
            <el-option v-for="w in warehouses" :key="w.id" :label="warehouseLabel(w)" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.keyword')">
          <el-input v-model="query.keyword" clearable :placeholder="t('warehouseLocation.keywordPlaceholder')" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.status')">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option :label="t('warehouseLocation.active')" value="ACTIVE" />
            <el-option :label="t('warehouseLocation.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">{{ t('warehouseLocation.search') }}</el-button>
          <el-button @click="handleReset">{{ t('warehouseLocation.reset') }}</el-button>
          <el-button v-permission="'masterdata:location:manage'" type="success" @click="handleOpenCreate">{{ t('warehouseLocation.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="warehouseName" :label="t('warehouseLocation.warehouse')" min-width="160" />
        <el-table-column prop="locationCode" :label="t('warehouseLocation.code')" width="120" />
        <el-table-column prop="locationName" :label="t('warehouseLocation.name')" min-width="160" />
        <el-table-column :label="t('warehouseLocation.default')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="isDefaultLocation(row.isDefault)" type="success">{{ defaultLabel(row.isDefault) }}</el-tag>
            <span v-else>{{ defaultLabel(row.isDefault) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('warehouseLocation.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('warehouseLocation.remark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('warehouseLocation.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'masterdata:location:manage'" link type="primary" @click="openEdit(row)">{{ t('warehouseLocation.edit') }}</el-button>
            <el-button v-if="isActive(row.status)" v-permission="'masterdata:location:manage'" link type="warning" @click="toggle(row, false)">{{ t('warehouseLocation.disable') }}</el-button>
            <el-button v-else v-permission="'masterdata:location:manage'" link type="success" @click="toggle(row, true)">{{ t('warehouseLocation.enable') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100, 200]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEditing ? t('warehouseLocation.editTitle') : t('warehouseLocation.createTitle')" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item :label="t('warehouseLocation.warehouse')">
          <el-select v-model="form.warehouseId" :disabled="isEditing" filterable style="width: 100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="warehouseLabel(w)" :value="String(w.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.code')">
          <el-input v-model="form.locationCode" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.name')">
          <el-input v-model="form.locationName" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.default')">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item :label="t('warehouseLocation.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('warehouseLocation.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('warehouseLocation.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  createLocation,
  disableLocation,
  enableLocation,
  getLocations,
  getWarehouses,
  updateLocation
} from '@/api/masterdata'
import { useWarehouseLocationForm } from '@/composables/useWarehouseLocationForm'
import { useWarehouseLocationList } from '@/composables/useWarehouseLocationList'
import { useWarehouseLocationPresentation } from '@/composables/useWarehouseLocationPresentation'

const { t } = useI18n()

const {
  defaultLabel,
  isActive,
  isDefaultLocation,
  statusLabel,
  statusTagType,
  warehouseLabel
} = useWarehouseLocationPresentation(t)

const {
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  loadData,
  loadOptions,
  loading,
  query,
  rows,
  toggle,
  total,
  warehouses
} = useWarehouseLocationList(t, {
  getWarehouses,
  getLocations,
  enableLocation,
  disableLocation,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const {
  dialogVisible,
  form,
  isEditing,
  openCreate,
  openEdit,
  save,
  saving
} = useWarehouseLocationForm(t, {
  createLocation,
  updateLocation,
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  onError: (message) => ElMessage.error(message),
  onSubmitted: async () => {
    await loadData()
  }
})

const handleOpenCreate = () => {
  openCreate(query.warehouseId || warehouses.value[0]?.id)
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.pagination { margin-top: 20px; justify-content: flex-end; }
</style>
