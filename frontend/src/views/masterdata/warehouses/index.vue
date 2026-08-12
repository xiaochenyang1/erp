<template>
  <div class="warehouse-management">
    <!-- 页面标题 - 使用专业的蓝色调 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="icon-box">
            <el-icon class="header-icon">
              <Opportunity />
            </el-icon>
            <div class="icon-shadow"></div>
          </div>
          <div class="header-text">
            <h1 class="page-title">{{ texts.pageTitle }}</h1>
            <p class="page-subtitle">{{ texts.pageSubtitle }}</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">{{ texts.totalWarehouses }}</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ texts.activeWarehouses }}</span>
            <span class="stat-value active">{{ activeCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item :label="texts.warehouseCode" prop="code">
        <el-input
          v-model="searchForm.code"
          :placeholder="texts.enterWarehouseCode"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item :label="texts.warehouseName" prop="name">
        <el-input
          v-model="searchForm.name"
          :placeholder="texts.enterWarehouseName"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item :label="texts.department" prop="deptId">
        <el-tree-select
          v-model="searchForm.deptId"
          :data="deptOptions"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          :placeholder="texts.selectDepartment"
          clearable
          check-strictly
        />
      </el-form-item>
      <el-form-item :label="texts.manager" prop="managerUserId">
        <el-select v-model="searchForm.managerUserId" :placeholder="texts.selectManager" clearable filterable>
          <el-option
            v-for="user in userOptions"
            :key="user.id"
            :label="userLabel(user)"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="texts.status" prop="status">
        <el-select v-model="searchForm.status" :placeholder="texts.selectStatus" clearable>
          <el-option :label="texts.active" value="ACTIVE" />
          <el-option :label="texts.inactive" value="INACTIVE" />
        </el-select>
      </el-form-item>
    </search-bar>

    <!-- 数据表格 -->
    <page-table
      :data="tableData"
      :loading="loading"
      :total="total"
      :page="searchForm.pageNo"
      :page-size="searchForm.pageSize"
      :show-create="canCreate"
      class="warehouse-table"
      @create="handleCreate"
      @export="handleExport"
      @refresh="loadData"
      @page-change="handlePageChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" :label="texts.warehouseCode" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge warehouse">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="texts.warehouseName" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="warehouse-name">
            <el-icon class="warehouse-icon">
              <HomeFilled />
            </el-icon>
            <span>{{ row.warehouseName || row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="deptId" :label="texts.department" width="140">
        <template #default="{ row }">
          <span>{{ deptLabel(row.deptId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="address" :label="texts.address" min-width="200" show-overflow-tooltip />
      <el-table-column prop="managerUserId" :label="texts.manager" width="140">
        <template #default="{ row }">
          <span>{{ managerLabel(row.managerUserId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="texts.status" width="100" align="center">
        <template #default="{ row }">
          <status-tag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column :label="texts.actions" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button link type="primary" @click="handleView(row)">
              <el-icon><View /></el-icon>
              {{ texts.view }}
            </el-button>
            <el-button v-permission="'masterdata:warehouse:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              {{ texts.edit }}
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'masterdata:warehouse:enable'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              <el-icon><CircleCheck /></el-icon>
              {{ texts.enable }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'masterdata:warehouse:disable'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              <el-icon><Delete /></el-icon>
              {{ texts.delete }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </page-table>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="850px"
      :close-on-click-modal="false"
      class="elegant-dialog warehouse-dialog"
    >
      <page-form
        v-model="formData"
        :rules="formRules"
        :loading="submitting"
        :columns="2"
        @submit="handleSubmit"
        @cancel="dialogVisible = false"
      >
        <el-form-item :label="texts.warehouseCode" prop="code">
          <el-input v-model="formData.code" :placeholder="texts.enterWarehouseCode" maxlength="50" />
        </el-form-item>
        <el-form-item :label="texts.warehouseName" prop="name">
          <el-input v-model="formData.name" :placeholder="texts.enterWarehouseName" maxlength="100" />
        </el-form-item>
        <el-form-item :label="texts.department" prop="deptId">
          <el-tree-select
            v-model="formData.deptId"
            :data="deptOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            :placeholder="texts.selectDepartment"
            clearable
            check-strictly
          />
        </el-form-item>
        <el-form-item :label="texts.manager" prop="managerUserId">
          <el-select v-model="formData.managerUserId" :placeholder="texts.selectManager" filterable clearable>
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="userLabel(user)"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="texts.status" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">{{ texts.active }}</el-radio>
            <el-radio value="INACTIVE">{{ texts.inactive }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="texts.address" prop="address" :style="{ gridColumn: '1 / -1' }">
          <el-input v-model="formData.address" :placeholder="texts.enterAddress" maxlength="200" />
        </el-form-item>
        <el-form-item :label="texts.remark" prop="remark" :style="{ gridColumn: '1 / -1' }">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            :placeholder="texts.enterRemark"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </page-form>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      :title="texts.warehouseDetail"
      width="750px"
      class="elegant-dialog warehouse-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Box /></el-icon>
            {{ texts.stockOverview }}
          </div>
          <div class="detail-row">
            <div class="detail-item"><div class="detail-label">{{ texts.skuCount }}</div><div class="detail-value">{{ formatNumber(stockSummary?.skuCount) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.qtyOnHand }}</div><div class="detail-value">{{ formatNumber(stockSummary?.qtyOnHand) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.qtyReserved }}</div><div class="detail-value">{{ formatNumber(stockSummary?.qtyReserved) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.qtyAvailable }}</div><div class="detail-value">{{ formatNumber(stockSummary?.qtyAvailable) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.amountOnHand }}</div><div class="detail-value">{{ formatCurrency(stockSummary?.amountOnHand) }}</div></div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Memo /></el-icon>
            {{ texts.basicInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.warehouseCode }}</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.warehouseName }}</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.department }}</div>
              <div class="detail-value">{{ deptLabel(currentRow?.deptId) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.status }}</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.status" />
              </div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">{{ texts.address }}</div>
              <div class="detail-value">{{ currentRow?.address || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><UserFilled /></el-icon>
            {{ texts.managementInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.manager }}</div>
              <div class="detail-value">{{ managerLabel(currentRow?.managerUserId) }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            {{ texts.otherInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.createdTime }}</div>
              <div class="detail-value">{{ formatDateTime(currentRow?.createdTime) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.updatedTime }}</div>
              <div class="detail-value">{{ formatDateTime(currentRow?.updatedTime) }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">{{ texts.remark }}</div>
              <div class="detail-value">{{ currentRow?.remark || '-' }}</div>
            </div>
          </div>
        </div>
      </detail-card>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Opportunity,
  HomeFilled,
  View,
  Edit,
  Delete,
  CircleCheck,
  Clock,
  UserFilled,
  Memo,
  Box
} from '@element-plus/icons-vue'
import {
  getWarehouses,
  getWarehouse,
  getWarehouseStockSummary,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
  enableWarehouse,
  exportWarehouses
} from '@/api/masterdata'
import {
  getDeptTree,
  getUsers
} from '@/api/system'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { useWarehousePresentation } from '@/composables/useWarehousePresentation'
import { useWarehouseList } from '@/composables/useWarehouseList'
import { useWarehouseForm } from '@/composables/useWarehouseForm'

const appStore = useAppStore()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('masterdata:warehouse:create'))
const WAREHOUSE_TEXTS = {
  'zh-CN': {
    pageTitle: '仓库管理',
    pageSubtitle: '统一管理仓储资源，优化库存布局',
    totalWarehouses: '仓库总数',
    activeWarehouses: '运营中',
    warehouseCode: '仓库编码',
    warehouseName: '仓库名称',
    department: '所属部门',
    manager: '管理员',
    status: '状态',
    address: '仓库地址',
    actions: '操作',
    view: '查看',
    edit: '编辑',
    enable: '启用',
    delete: '删除',
    cancel: '取消',
    active: '启用',
    inactive: '停用',
    enterWarehouseCode: '请输入仓库编码',
    enterWarehouseName: '请输入仓库名称',
    selectDepartment: '请选择所属部门',
    selectManager: '请选择管理员',
    selectStatus: '请选择状态',
    enterAddress: '请输入仓库详细地址',
    remark: '备注',
    enterRemark: '请输入备注信息',
    createWarehouse: '新增仓库',
    editWarehouse: '编辑仓库',
    warehouseDetail: '仓库详情',
    stockOverview: '库存概览',
    skuCount: '库存 SKU',
    qtyOnHand: '现存数量',
    qtyReserved: '预占数量',
    qtyAvailable: '可用数量',
    amountOnHand: '库存金额',
    basicInfo: '基本信息',
    managementInfo: '管理信息',
    otherInfo: '其他信息',
    createdTime: '创建时间',
    updatedTime: '更新时间',
    loadFailed: '加载数据失败',
    loadOptionsFailed: '加载仓库选项失败',
    loadDetailFailed: '加载仓库详情失败',
    confirmTitle: '提示',
    confirmDelete: '确认删除仓库“{name}”吗？',
    confirmEnable: '确认启用仓库“{name}”吗？',
    deleteSuccess: '删除成功',
    deleteFailed: '删除失败',
    enableSuccess: '启用成功',
    enableFailed: '启用失败',
    updateSuccess: '更新成功',
    createSuccess: '创建成功',
    updateFailed: '更新失败',
    createFailed: '创建失败',
    exportSuccess: '导出成功',
    exportFailed: '导出失败',
    exportFilename: '仓库列表',
    validationEnterCode: '请输入仓库编码',
    validationCodeLength: '长度在 2 到 50 个字符',
    validationEnterName: '请输入仓库名称',
    validationNameLength: '长度在 2 到 100 个字符',
    validationDepartment: '请选择所属部门',
    validationManager: '请选择管理员'
  },
  'en-US': {
    pageTitle: 'Warehouse Management',
    pageSubtitle: 'Manage warehouse resources and optimize inventory layout',
    totalWarehouses: 'Total warehouses',
    activeWarehouses: 'Operating',
    warehouseCode: 'Warehouse code',
    warehouseName: 'Warehouse name',
    department: 'Department',
    manager: 'Manager',
    status: 'Status',
    address: 'Warehouse address',
    actions: 'Actions',
    view: 'View',
    edit: 'Edit',
    enable: 'Enable',
    delete: 'Delete',
    cancel: 'Cancel',
    active: 'Active',
    inactive: 'Inactive',
    enterWarehouseCode: 'Enter warehouse code',
    enterWarehouseName: 'Enter warehouse name',
    selectDepartment: 'Select department',
    selectManager: 'Select manager',
    selectStatus: 'Select status',
    enterAddress: 'Enter warehouse address',
    remark: 'Remark',
    enterRemark: 'Enter remark',
    createWarehouse: 'Create warehouse',
    editWarehouse: 'Edit warehouse',
    warehouseDetail: 'Warehouse details',
    stockOverview: 'Stock overview',
    skuCount: 'Stocked SKUs',
    qtyOnHand: 'On-hand quantity',
    qtyReserved: 'Reserved quantity',
    qtyAvailable: 'Available quantity',
    amountOnHand: 'Inventory amount',
    basicInfo: 'Basic information',
    managementInfo: 'Management information',
    otherInfo: 'Other information',
    createdTime: 'Created at',
    updatedTime: 'Updated at',
    loadFailed: 'Failed to load warehouses',
    loadOptionsFailed: 'Failed to load warehouse options',
    loadDetailFailed: 'Failed to load warehouse details',
    confirmTitle: 'Confirm',
    confirmDelete: 'Delete warehouse "{name}"?',
    confirmEnable: 'Enable warehouse "{name}"?',
    deleteSuccess: 'Warehouse deleted',
    deleteFailed: 'Failed to delete warehouse',
    enableSuccess: 'Warehouse enabled',
    enableFailed: 'Failed to enable warehouse',
    updateSuccess: 'Warehouse updated',
    createSuccess: 'Warehouse created',
    updateFailed: 'Failed to update warehouse',
    createFailed: 'Failed to create warehouse',
    exportSuccess: 'Export completed',
    exportFailed: 'Failed to export warehouses',
    exportFilename: 'warehouse-list',
    validationEnterCode: 'Enter warehouse code',
    validationCodeLength: 'Length must be between 2 and 50 characters',
    validationEnterName: 'Enter warehouse name',
    validationNameLength: 'Length must be between 2 and 100 characters',
    validationDepartment: 'Select a department',
    validationManager: 'Select a manager'
  }
} as const
const texts = computed(() => WAREHOUSE_TEXTS[appStore.locale as keyof typeof WAREHOUSE_TEXTS])
const displayPreferences = computed(() => ({
  locale: appStore.locale,
  timeZone: appStore.timeZone
}))

const {
  activeCount: countActive,
  deptLabel: resolveDeptFromOptions,
  formatCurrency,
  formatDateTime,
  formatNumber,
  interpolate,
  managerLabel: resolveManagerFromOptions,
  userLabel
} = useWarehousePresentation(displayPreferences)

const {
  currentRow,
  deptOptions,
  detailVisible,
  handleDelete,
  handleEnable,
  handleExport,
  handlePageChange,
  handleReset,
  handleSearch,
  handleView,
  loadData,
  loadOptions,
  loading,
  searchForm,
  stockSummary,
  tableData,
  total,
  userOptions
} = useWarehouseList(texts, {
  getWarehouses,
  getWarehouse,
  getStockSummary: getWarehouseStockSummary,
  enableWarehouse,
  deleteWarehouse,
  exportWarehouses,
  getDeptTree,
  getUsers,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  interpolate,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message)
})

const activeCount = computed(() => countActive(tableData.value))
const deptLabel = (id?: string | number) => resolveDeptFromOptions(deptOptions.value, id)
const managerLabel = (id?: string | number) => resolveManagerFromOptions(userOptions.value, id)

const {
  dialogTitle,
  dialogVisible,
  formData,
  formRules,
  handleCreate,
  handleEdit,
  handleSubmit,
  submitting
} = useWarehouseForm(texts, {
  createWarehouse,
  updateWarehouse,
  onSuccess: (message) => ElMessage.success(message),
  onError: (message) => ElMessage.error(message),
  onCompleted: () => loadData()
})

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped>
.warehouse-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #eff6ff 0%, #e0f2fe 100%);
  font-family: 'Plus Jakarta Sans', 'Segoe UI', system-ui, -apple-system, sans-serif;
}

.page-header {
  margin-bottom: 24px;
  animation: slideDown 0.4s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.header-content {
  background: linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8px 32px rgba(14, 165, 233, 0.3);
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  animation: pulse 4s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.5;
  }
  50% {
    transform: translate(-50%, -50%) scale(1.2);
    opacity: 0.8;
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.icon-box {
  position: relative;
  width: 70px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 18px;
  backdrop-filter: blur(10px);
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.header-icon {
  font-size: 44px;
  color: #ffffff;
  animation: iconFloat 3s ease-in-out infinite;
}

@keyframes iconFloat {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-6px) rotate(2deg);
  }
}

.icon-shadow {
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  width: 60%;
  height: 8px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 50%;
  filter: blur(6px);
  animation: shadowPulse 3s ease-in-out infinite;
}

@keyframes shadowPulse {
  0%, 100% {
    transform: translateX(-50%) scale(1);
    opacity: 0.3;
  }
  50% {
    transform: translateX(-50%) scale(1.2);
    opacity: 0.5;
  }
}

.header-text {
  color: #ffffff;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: 0.5px;
}

.page-subtitle {
  font-size: 14px;
  margin: 0;
  opacity: 0.95;
  font-weight: 400;
}

.header-stats {
  display: flex;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.stat-card {
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 12px;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  transition: all 0.3s ease;
  min-width: 110px;
}

.stat-card:hover {
  background: rgba(255, 255, 255, 0.28);
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.15);
}

.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.95);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 24px;
  color: #ffffff;
  font-weight: 700;
}

.stat-value.active {
  color: #bfdbfe;
  text-shadow: 0 0 10px rgba(191, 219, 254, 0.6);
}

.warehouse-table {
  animation: fadeIn 0.5s ease-out 0.1s both;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.code-badge.warehouse {
  background: linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%);
  display: inline-block;
  padding: 4px 10px;
  color: #ffffff;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  font-family: 'Courier New', monospace;
}

.warehouse-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.warehouse-icon {
  color: #0ea5e9;
  font-size: 16px;
}

.action-buttons {
  display: flex;
  gap: 4px;
  justify-content: center;
}

.action-buttons :deep(.el-button) {
  padding: 4px 8px;
  transition: all 0.2s ease;
}

.action-buttons :deep(.el-button:hover) {
  transform: translateY(-1px);
}

.warehouse-dialog :deep(.el-dialog__header) {
  background: linear-gradient(to right, #eff6ff, #e0f2fe);
  padding: 24px 32px;
  border-bottom: 1px solid #bfdbfe;
}

.warehouse-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.warehouse-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.detail-section .section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #495057;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #0ea5e9;
  letter-spacing: 0.3px;
}

.detail-section .section-title .el-icon {
  color: #0ea5e9;
  font-size: 16px;
}
</style>
