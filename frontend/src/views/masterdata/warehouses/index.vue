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
            <h1 class="page-title">仓库管理</h1>
            <p class="page-subtitle">统一管理仓储资源，优化库存布局</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">仓库总数</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">运营中</span>
            <span class="stat-value active">{{ activeCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="仓库编码" prop="code">
        <el-input
          v-model="searchForm.code"
          placeholder="请输入仓库编码"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="仓库名称" prop="name">
        <el-input
          v-model="searchForm.name"
          placeholder="请输入仓库名称"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="所属部门" prop="deptId">
        <el-tree-select
          v-model="searchForm.deptId"
          :data="deptOptions"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择所属部门"
          clearable
          check-strictly
        />
      </el-form-item>
      <el-form-item label="管理员" prop="managerUserId">
        <el-select v-model="searchForm.managerUserId" placeholder="请选择管理员" clearable filterable>
          <el-option
            v-for="user in userOptions"
            :key="user.id"
            :label="userLabel(user)"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="INACTIVE" />
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
      @create="handleCreate"
      :show-create="canCreate"
      @export="handleExport"
      @refresh="loadData"
      @page-change="handlePageChange"
      class="warehouse-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" label="仓库编码" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge warehouse">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="仓库名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="warehouse-name">
            <el-icon class="warehouse-icon">
              <HomeFilled />
            </el-icon>
            <span>{{ row.warehouseName || row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="deptId" label="所属部门" width="140">
        <template #default="{ row }">
          <span>{{ deptLabel(row.deptId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="address" label="仓库地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="managerUserId" label="管理员" width="140">
        <template #default="{ row }">
          <span>{{ managerLabel(row.managerUserId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <status-tag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button link type="primary" @click="handleView(row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button v-permission="'masterdata:warehouse:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button v-if="row.status !== 'ACTIVE'" v-permission="'masterdata:warehouse:enable'" link type="success" @click="handleEnable(row)">
              <el-icon><CircleCheck /></el-icon>
              启用
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'masterdata:warehouse:disable'" link type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
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
        <el-form-item label="仓库编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入仓库编码" maxlength="50" />
        </el-form-item>
        <el-form-item label="仓库名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入仓库名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-tree-select
            v-model="formData.deptId"
            :data="deptOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择所属部门"
            clearable
            check-strictly
          />
        </el-form-item>
        <el-form-item label="管理员" prop="managerUserId">
          <el-select v-model="formData.managerUserId" placeholder="请选择管理员" filterable clearable>
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="userLabel(user)"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="仓库地址" prop="address" :style="{ gridColumn: '1 / -1' }">
          <el-input v-model="formData.address" placeholder="请输入仓库详细地址" maxlength="200" />
        </el-form-item>
        <el-form-item label="备注" prop="remark" :style="{ gridColumn: '1 / -1' }">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </page-form>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="仓库详情"
      width="750px"
      class="elegant-dialog warehouse-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Box /></el-icon>
            库存概览
          </div>
          <div class="detail-row">
            <div class="detail-item"><div class="detail-label">库存 SKU</div><div class="detail-value">{{ stockSummary?.skuCount ?? 0 }}</div></div>
            <div class="detail-item"><div class="detail-label">现存数量</div><div class="detail-value">{{ stockSummary?.qtyOnHand ?? 0 }}</div></div>
            <div class="detail-item"><div class="detail-label">预占数量</div><div class="detail-value">{{ stockSummary?.qtyReserved ?? 0 }}</div></div>
            <div class="detail-item"><div class="detail-label">可用数量</div><div class="detail-value">{{ stockSummary?.qtyAvailable ?? 0 }}</div></div>
            <div class="detail-item"><div class="detail-label">库存金额</div><div class="detail-value">¥{{ Number(stockSummary?.amountOnHand || 0).toFixed(2) }}</div></div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Memo /></el-icon>
            基本信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">仓库编码</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">仓库名称</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">所属部门</div>
              <div class="detail-value">{{ deptLabel(currentRow?.deptId) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.status" />
              </div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">仓库地址</div>
              <div class="detail-value">{{ currentRow?.address || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><UserFilled /></el-icon>
            管理信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">管理员</div>
              <div class="detail-value">{{ managerLabel(currentRow?.managerUserId) }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            其他信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">创建时间</div>
              <div class="detail-value">{{ currentRow?.createdTime || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">更新时间</div>
              <div class="detail-value">{{ currentRow?.updatedTime || '-' }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">备注</div>
              <div class="detail-value">{{ currentRow?.remark || '-' }}</div>
            </div>
          </div>
        </div>
      </detail-card>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
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
  exportWarehouses,
  type Warehouse,
  type WarehouseStockSummary,
  type WarehouseQuery,
  type WarehouseSaveRequest
} from '@/api/masterdata'
import {
  getDeptTree,
  getUsers,
  type Dept,
  type User
} from '@/api/system'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { downloadBlob } from '@/utils/download'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('masterdata:warehouse:create'))

// 搜索表单
const searchForm = reactive<WarehouseQuery>({
  pageNo: 1,
  pageSize: 20,
  code: '',
  name: '',
  deptId: undefined,
  managerUserId: undefined,
  status: ''
})

// 表格数据
const tableData = ref<Warehouse[]>([])
const total = ref(0)
const loading = ref(false)
const activeCount = computed(() => tableData.value.filter(item => item.status === 'ACTIVE').length)
const deptOptions = ref<Dept[]>([])
const userOptions = ref<User[]>([])

// 对话框
const dialogVisible = ref(false)
const dialogTitle = computed(() => (formData.id ? '编辑仓库' : '新增仓库'))
const submitting = ref(false)
const detailVisible = ref(false)
const currentRow = ref<Warehouse>()
const stockSummary = ref<WarehouseStockSummary>()

// 表单数据
const formData = reactive<WarehouseSaveRequest & { id?: string }>({
  code: '',
  name: '',
  deptId: undefined as string | undefined,
  managerUserId: undefined as string | undefined,
  address: '',
  status: 'ACTIVE',
  remark: ''
})

// 表单验证规则
const formRules = {
  code: [
    { required: true, message: '请输入仓库编码', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入仓库名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  deptId: [
    { required: true, message: '请选择所属部门', trigger: 'change' }
  ],
  managerUserId: [
    { required: true, message: '请选择管理员', trigger: 'change' }
  ]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getWarehouses(searchForm)

    // 适配后端返回的数据结构
    const warehouses = res.records.map(item => ({
      ...item,
      code: item.warehouseCode,
      name: item.warehouseName
    }))

    tableData.value = warehouses
    total.value = res.total
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const flattenDepts = (items: Dept[]): Dept[] => {
  return items.flatMap(item => [item, ...flattenDepts(item.children || [])])
}

const userLabel = (user: User) => {
  return user.realName || user.username
}

const deptLabel = (id?: string | number) => {
  if (id == null || id === '') return '-'
  const dept = flattenDepts(deptOptions.value).find(item => item.id === String(id))
  return dept?.name || String(id)
}

const managerLabel = (id?: string | number) => {
  if (id == null || id === '') return '-'
  const user = userOptions.value.find(item => item.id === String(id))
  return user ? userLabel(user) : String(id)
}

const loadOptions = async () => {
  try {
    const [depts, users] = await Promise.all([
      getDeptTree(),
      getUsers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    ])
    deptOptions.value = depts
    userOptions.value = users.records
  } catch {
    ElMessage.error('加载仓库选项失败')
  }
}

// 搜索
const handleSearch = () => {
  searchForm.pageNo = 1
  loadData()
}

// 重置
const handleReset = () => {
  searchForm.code = ''
  searchForm.name = ''
  searchForm.deptId = undefined
  searchForm.managerUserId = undefined
  searchForm.status = ''
  searchForm.pageNo = 1
  loadData()
}

// 分页
const handlePageChange = (page: number, size: number) => {
  searchForm.pageNo = page
  searchForm.pageSize = size
  loadData()
}

// 新增
const handleCreate = () => {
  Object.assign(formData, {
    id: undefined,
    code: '',
    name: '',
    deptId: undefined as string | undefined,
    managerUserId: undefined as string | undefined,
    address: '',
    status: 'ACTIVE',
    remark: ''
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: Warehouse) => {
  Object.assign(formData, {
    id: row.id,
    code: row.warehouseCode || row.code,
    name: row.warehouseName || row.name,
    deptId: row.deptId,
    managerUserId: row.managerUserId,
    address: row.address,
    status: row.status,
    remark: row.remark
  })
  dialogVisible.value = true
}

// 查看
const handleView = async (row: Warehouse) => {
  try {
    currentRow.value = await getWarehouse(row.id)
    stockSummary.value = await getWarehouseStockSummary(row.id)
    detailVisible.value = true
  } catch {
    ElMessage.error('加载仓库详情失败')
  }
}

// 删除
const handleDelete = async (row: Warehouse) => {
  try {
    await ElMessageBox.confirm(
      `确认删除仓库"${row.warehouseName || row.name}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteWarehouse(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleEnable = async (row: Warehouse) => {
  try {
    await ElMessageBox.confirm(`确认启用仓库"${row.warehouseName || row.name}"吗？`, '提示', { type: 'warning' })
    await enableWarehouse(row.id)
    ElMessage.success('启用成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('启用失败')
    }
  }
}

// 提交
const handleSubmit = async (values: any) => {
  submitting.value = true
  try {
    const payload = {
      warehouseCode: values.code,
      warehouseName: values.name,
      deptId: values.deptId,
      managerUserId: values.managerUserId,
      address: values.address,
      status: values.status,
      remark: values.remark
    }

    if (formData.id) {
      await updateWarehouse(formData.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createWarehouse(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error(formData.id ? '更新失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

// 导出
const handleExport = async () => {
  try {
    const blob = await exportWarehouses(searchForm)
    downloadBlob(blob, `仓库列表_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

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

.pageNo-header {
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

.pageNo-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: 0.5px;
}

.pageNo-subtitle {
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
