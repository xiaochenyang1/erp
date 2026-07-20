<template>
  <div class="supplier-management">
    <!-- 页面标题 - 使用清新的绿色调 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="icon-container">
            <el-icon class="header-icon">
              <ShoppingBag />
            </el-icon>
            <div class="icon-glow"></div>
          </div>
          <div class="header-text">
            <h1 class="page-title">供应商管理</h1>
            <p class="page-subtitle">管理供应商档案，维护供应链合作关系</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">供应商总数</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">合作中</span>
            <span class="stat-value active">{{ activeCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="供应商编码" prop="code">
        <el-input
          v-model="searchForm.code"
          placeholder="请输入供应商编码"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="供应商名称" prop="name">
        <el-input
          v-model="searchForm.name"
          placeholder="请输入供应商名称"
          clearable
          @keyup.enter="handleSearch"
        />
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
      class="supplier-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" label="供应商编码" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge supplier">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="供应商名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="supplier-name">
            <el-icon class="supplier-icon">
              <OfficeBuilding />
            </el-icon>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="contact" label="联系人" width="120" />
      <el-table-column prop="mobile" label="联系电话" width="140" />
      <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
      <el-table-column prop="creditPeriod" label="账期" width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.creditPeriod" class="credit-period">
            {{ row.creditPeriod }} 天
          </span>
          <span v-else class="no-credit">现结</span>
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
            <el-button v-permission="'masterdata:supplier:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button v-if="row.status !== 'ACTIVE'" v-permission="'masterdata:supplier:enable'" link type="success" @click="handleEnable(row)">
              <el-icon><CircleCheck /></el-icon>
              启用
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'masterdata:supplier:disable'" link type="danger" @click="handleDelete(row)">
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
      class="elegant-dialog supplier-dialog"
    >
      <page-form
        v-model="formData"
        :rules="formRules"
        :loading="submitting"
        :columns="2"
        @submit="handleSubmit"
        @cancel="dialogVisible = false"
      >
        <el-form-item label="供应商编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入供应商编码" maxlength="50" />
        </el-form-item>
        <el-form-item label="供应商名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入供应商名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="联系人" prop="contact">
          <el-input v-model="formData.contact" placeholder="请输入联系人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="mobile">
          <el-input v-model="formData.mobile" placeholder="请输入联系电话" maxlength="20" />
        </el-form-item>
        <el-form-item label="电子邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入电子邮箱" maxlength="100" />
        </el-form-item>
        <el-form-item label="账期（天）" prop="creditPeriod">
          <el-input-number
            v-model="formData.creditPeriod"
            :min="0"
            :max="365"
            :controls="false"
            placeholder="请输入账期天数"
            style="width: 100%"
          />
          <span class="form-tip">留空表示现结</span>
        </el-form-item>
        <el-form-item label="联系地址" prop="address" :style="{ gridColumn: '1 / -1' }">
          <el-input v-model="formData.address" placeholder="请输入详细地址" maxlength="200" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
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
      title="供应商详情"
      width="750px"
      class="elegant-dialog supplier-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">
            <el-icon><DocumentCopy /></el-icon>
            基本信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">供应商编码</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">供应商名称</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.status" />
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Phone /></el-icon>
            联系信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">联系人</div>
              <div class="detail-value">{{ currentRow?.contact || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">联系电话</div>
              <div class="detail-value">{{ currentRow?.mobile || '-' }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">电子邮箱</div>
              <div class="detail-value">{{ currentRow?.email || '-' }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">联系地址</div>
              <div class="detail-value">{{ currentRow?.address || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Calendar /></el-icon>
            财务信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">账期天数</div>
              <div class="detail-value credit">
                <template v-if="currentRow?.creditPeriod">
                  {{ currentRow.creditPeriod }} 天
                </template>
                <template v-else>
                  <span class="cash-only">现结</span>
                </template>
              </div>
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
              <div class="detail-value">{{ currentRow?.createdTime }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">更新时间</div>
              <div class="detail-value">{{ currentRow?.updatedTime }}</div>
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
  ShoppingBag,
  OfficeBuilding,
  View,
  Edit,
  Delete,
  CircleCheck,
  Phone,
  Calendar,
  Clock,
  DocumentCopy
} from '@element-plus/icons-vue'
import {
  getSuppliers,
  getSupplier,
  createSupplier,
  updateSupplier,
  deleteSupplier,
  enableSupplier,
  exportSuppliers,
  type Supplier,
  type SupplierQuery,
  type SupplierSaveRequest
} from '@/api/masterdata'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { downloadBlob } from '@/utils/download'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('masterdata:supplier:create'))

// 搜索表单
const searchForm = reactive<SupplierQuery>({
  pageNo: 1,
  pageSize: 20,
  code: '',
  name: '',
  status: ''
})

// 表格数据
const tableData = ref<Supplier[]>([])
const total = ref(0)
const loading = ref(false)
const activeCount = computed(() => tableData.value.filter(item => item.status === 'ACTIVE').length)

// 对话框
const dialogVisible = ref(false)
const dialogTitle = computed(() => (formData.id ? '编辑供应商' : '新增供应商'))
const submitting = ref(false)
const detailVisible = ref(false)
const currentRow = ref<Supplier>()

// 表单数据
const formData = reactive<SupplierSaveRequest & { id?: string }>({
  code: '',
  name: '',
  contact: '',
  mobile: '',
  email: '',
  address: '',
  creditPeriod: undefined,
  status: 'ACTIVE',
  remark: ''
})

// 表单验证规则
const formRules = {
  code: [
    { required: true, message: '请输入供应商编码', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入供应商名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  mobile: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getSuppliers(searchForm)

    // 适配后端返回的数据结构
    const suppliers = res.records.map(item => ({
      ...item,
      code: item.supplierCode,
      name: item.supplierName,
      contact: item.contactName,
      mobile: item.contactPhone
    }))

    tableData.value = suppliers
    total.value = res.total
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
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
    contact: '',
    mobile: '',
    email: '',
    address: '',
    creditPeriod: undefined,
    status: 'ACTIVE',
    remark: ''
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: Supplier) => {
  Object.assign(formData, {
    id: row.id,
    code: row.supplierCode || row.code,
    name: row.supplierName || row.name,
    contact: row.contactName || row.contact,
    mobile: row.contactPhone || row.mobile,
    email: row.email,
    address: row.address,
    creditPeriod: row.creditPeriod,
    status: row.status,
    remark: row.remark
  })
  dialogVisible.value = true
}

// 查看
const handleView = async (row: Supplier) => {
  try {
    currentRow.value = await getSupplier(row.id)
    detailVisible.value = true
  } catch {
    ElMessage.error('加载供应商详情失败')
  }
}

// 删除
const handleDelete = async (row: Supplier) => {
  try {
    await ElMessageBox.confirm(
      `确认删除供应商"${row.name}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteSupplier(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleEnable = async (row: Supplier) => {
  try {
    await ElMessageBox.confirm(`确认启用供应商"${row.name}"吗？`, '提示', { type: 'warning' })
    await enableSupplier(row.id)
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
    // 转换字段名以适配后端
    const payload = {
      supplierCode: values.code,
      supplierName: values.name,
      contactName: values.contact,
      contactPhone: values.mobile,
      settlementMethod: values.settlementMethod || '',
      address: values.address,
      status: values.status,
      remark: values.remark
    }

    if (formData.id) {
      await updateSupplier(formData.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createSupplier(payload)
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
    const blob = await exportSuppliers(searchForm)
    downloadBlob(blob, `供应商列表_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.supplier-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #f0fff4 0%, #e6f7ed 100%);
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
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8px 32px rgba(16, 185, 129, 0.3);
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  bottom: -20%;
  right: -8%;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.12) 0%, transparent 70%);
  border-radius: 50%;
  animation: floatRight 6s ease-in-out infinite;
}

@keyframes floatRight {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(-15px, 10px) scale(1.15);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.icon-container {
  position: relative;
  width: 65px;
  height: 65px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 16px;
  backdrop-filter: blur(10px);
}

.header-icon {
  font-size: 42px;
  color: #ffffff;
  animation: iconRotate 3s ease-in-out infinite;
}

@keyframes iconRotate {
  0%, 100% {
    transform: rotate(0deg) scale(1);
  }
  50% {
    transform: rotate(5deg) scale(1.08);
  }
}

.icon-glow {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.25);
  filter: blur(12px);
  animation: glow 2.5s ease-in-out infinite;
}

@keyframes glow {
  0%, 100% {
    opacity: 0.3;
  }
  50% {
    opacity: 0.7;
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
  color: #d1fae5;
  text-shadow: 0 0 10px rgba(209, 250, 229, 0.6);
}

.supplier-table {
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

.code-badge.supplier {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  display: inline-block;
  padding: 4px 10px;
  color: #ffffff;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  font-family: 'Courier New', monospace;
}

.supplier-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.supplier-icon {
  color: #10b981;
  font-size: 16px;
}

.credit-period {
  font-weight: 600;
  color: #10b981;
  font-size: 13px;
}

.no-credit,
.cash-only {
  color: #f59e0b;
  font-weight: 600;
  font-size: 12px;
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

.supplier-dialog :deep(.el-dialog__header) {
  background: linear-gradient(to right, #f0fff4, #e6f7ed);
  padding: 24px 32px;
  border-bottom: 1px solid #d1fae5;
}

.supplier-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.supplier-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
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
  border-bottom: 2px solid #10b981;
  letter-spacing: 0.3px;
}

.detail-section .section-title .el-icon {
  color: #10b981;
  font-size: 16px;
}

.detail-value.credit {
  font-size: 16px;
  font-weight: 700;
  color: #10b981;
}
</style>
