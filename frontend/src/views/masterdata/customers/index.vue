<template>
  <div class="customer-management">
    <!-- 页面标题 - 使用温暖的橙色调 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="icon-wrapper">
            <el-icon class="header-icon">
              <UserFilled />
            </el-icon>
            <div class="icon-ripple"></div>
          </div>
          <div class="header-text">
            <h1 class="page-title">客户管理</h1>
            <p class="page-subtitle">维护客户关系，管理客户档案与信用信息</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">客户总数</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">企业客户</span>
            <span class="stat-value company">{{ companyCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">个人客户</span>
            <span class="stat-value individual">{{ individualCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="客户编码" prop="code">
        <el-input
          v-model="searchForm.code"
          placeholder="请输入客户编码"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="客户名称" prop="name">
        <el-input
          v-model="searchForm.name"
          placeholder="请输入客户名称"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="客户类型" prop="type">
        <el-select v-model="searchForm.type" placeholder="请选择客户类型" clearable>
          <el-option label="企业" value="COMPANY" />
          <el-option label="个人" value="INDIVIDUAL" />
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
      class="customer-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" label="客户编码" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge customer">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="客户名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="customer-name">
            <el-icon class="customer-icon">
              <component :is="row.type === 'COMPANY' ? OfficeBuilding : User" />
            </el-icon>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="客户类型" width="110" align="center">
        <template #default="{ row }">
          <status-tag :status="row.type" />
        </template>
      </el-table-column>
      <el-table-column prop="contact" label="联系人" width="120" />
      <el-table-column prop="mobile" label="联系电话" width="140" />
      <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
      <el-table-column prop="creditLimit" label="信用额度" width="130" align="right">
        <template #default="{ row }">
          <span class="credit-value" v-if="row.creditLimit">
            ¥{{ row.creditLimit?.toLocaleString() }}
          </span>
          <span v-else class="no-limit">无限制</span>
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
            <el-button v-permission="'masterdata:customer:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button v-if="row.status !== 'ACTIVE'" v-permission="'masterdata:customer:enable'" link type="success" @click="handleEnable(row)">
              <el-icon><CircleCheck /></el-icon>
              启用
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'masterdata:customer:disable'" link type="danger" @click="handleDelete(row)">
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
      width="900px"
      :close-on-click-modal="false"
      class="elegant-dialog customer-dialog"
    >
      <page-form
        v-model="formData"
        :rules="formRules"
        :loading="submitting"
        :columns="2"
        @submit="handleSubmit"
        @cancel="dialogVisible = false"
      >
        <el-form-item label="客户编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入客户编码" maxlength="50" />
        </el-form-item>
        <el-form-item label="客户名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入客户名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="客户类型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio value="COMPANY">
              <el-icon><OfficeBuilding /></el-icon>
              企业
            </el-radio>
            <el-radio value="INDIVIDUAL">
              <el-icon><User /></el-icon>
              个人
            </el-radio>
          </el-radio-group>
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
        <el-form-item label="联系地址" prop="address" :style="{ gridColumn: '1 / -1' }">
          <el-input v-model="formData.address" placeholder="请输入详细地址" maxlength="200" />
        </el-form-item>
        <el-form-item label="结算方式" prop="settlementMethod">
          <el-select v-model="formData.settlementMethod" placeholder="请选择结算方式" style="width: 100%">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="月结" value="MONTHLY" />
            <el-option label="货到付款" value="COD" />
          </el-select>
        </el-form-item>
        <el-form-item label="信用额度" prop="creditLimit">
          <el-input-number
            v-model="formData.creditLimit"
            :min="0"
            :precision="2"
            :controls="false"
            placeholder="请输入信用额度（元）"
            style="width: 100%"
          />
          <span class="form-tip">填 0 表示不限额；大于 0 时审批将校验未结敞口</span>
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
      title="客户详情"
      width="750px"
      class="elegant-dialog customer-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Notebook /></el-icon>
            基本信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">客户编码</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">客户名称</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">客户类型</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.type" />
              </div>
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
            <el-icon><Wallet /></el-icon>
            财务信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">信用额度</div>
              <div class="detail-value credit">
                <template v-if="currentRow?.creditLimit">
                  ¥{{ currentRow.creditLimit.toLocaleString() }}
                </template>
                <template v-else>
                  <span class="unlimited">无限制</span>
                </template>
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">已用额度</div>
              <div class="detail-value">¥{{ Number(creditExposure?.totalExposure || 0).toLocaleString() }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">未结应收</div>
              <div class="detail-value">¥{{ Number(creditExposure?.outstandingReceivable || 0).toLocaleString() }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">在途订单</div>
              <div class="detail-value">¥{{ Number(creditExposure?.openOrderExposure || 0).toLocaleString() }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">可用额度</div>
              <div class="detail-value" :class="{ 'credit-exceeded': creditExposure?.exceeded }">
                {{ creditExposure?.unlimited ? '无限制' : `¥${Number(creditExposure?.availableCredit || 0).toLocaleString()}` }}
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
  UserFilled,
  OfficeBuilding,
  User,
  View,
  Edit,
  Delete,
  CircleCheck,
  Phone,
  Wallet,
  Clock,
  Notebook
} from '@element-plus/icons-vue'
import {
  getCustomers,
  getCustomer,
  getCustomerCreditExposure,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  enableCustomer,
  exportCustomers,
  type Customer,
  type CustomerCreditExposure,
  type CustomerQuery,
  type CustomerSaveRequest
} from '@/api/masterdata'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { downloadBlob } from '@/utils/download'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('masterdata:customer:create'))

// 搜索表单
const searchForm = reactive<CustomerQuery>({
  pageNo: 1,
  pageSize: 20,
  code: '',
  name: '',
  type: '',
  status: ''
})

// 表格数据
const tableData = ref<Customer[]>([])
const total = ref(0)
const loading = ref(false)
const companyCount = computed(() => tableData.value.filter(item => item.type === 'COMPANY').length)
const individualCount = computed(() => tableData.value.filter(item => item.type === 'INDIVIDUAL').length)

// 对话框
const dialogVisible = ref(false)
const dialogTitle = computed(() => (formData.id ? '编辑客户' : '新增客户'))
const submitting = ref(false)
const detailVisible = ref(false)
const currentRow = ref<Customer>()
const creditExposure = ref<CustomerCreditExposure>()

// 表单数据
const formData = reactive<CustomerSaveRequest & { id?: string }>({
  code: '',
  name: '',
  type: 'COMPANY',
  contact: '',
  mobile: '',
  email: '',
  address: '',
  settlementMethod: 'BANK_TRANSFER',
  creditLimit: 0,
  status: 'ACTIVE',
  remark: ''
})

// 表单验证规则
const formRules = {
  code: [
    { required: true, message: '请输入客户编码', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入客户名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  settlementMethod: [
    { required: true, message: '请选择结算方式', trigger: 'change' }
  ],
  creditLimit: [
    { required: true, message: '请输入信用额度（0=不限额）', trigger: 'change' }
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
    const res = await getCustomers(searchForm)

    // 适配后端返回的数据结构
    const customers = res.records.map(item => ({
      ...item,
      code: item.customerCode,
      name: item.customerName,
      contact: item.contactName,
      mobile: item.contactPhone
    }))

    tableData.value = customers
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
  searchForm.type = ''
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
    type: 'COMPANY',
    contact: '',
    mobile: '',
    email: '',
    address: '',
    settlementMethod: 'BANK_TRANSFER',
    creditLimit: 0,
    status: 'ACTIVE',
    remark: ''
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: Customer) => {
  Object.assign(formData, {
    id: row.id,
    code: row.customerCode || row.code,
    name: row.customerName || row.name,
    type: row.type,
    contact: row.contactName || row.contact,
    mobile: row.contactPhone || row.mobile,
    email: row.email,
    address: row.address,
    settlementMethod: row.settlementMethod || 'BANK_TRANSFER',
    creditLimit: row.creditLimit ?? 0,
    status: row.status,
    remark: row.remark
  })
  dialogVisible.value = true
}

// 查看
const handleView = async (row: Customer) => {
  try {
    currentRow.value = await getCustomer(row.id)
    creditExposure.value = await getCustomerCreditExposure(row.id)
    detailVisible.value = true
  } catch {
    ElMessage.error('加载客户详情失败')
  }
}

// 删除
const handleDelete = async (row: Customer) => {
  try {
    await ElMessageBox.confirm(
      `确认删除客户"${row.name}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteCustomer(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleEnable = async (row: Customer) => {
  try {
    await ElMessageBox.confirm(`确认启用客户"${row.name}"吗？`, '提示', { type: 'warning' })
    await enableCustomer(row.id)
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
      customerCode: values.code,
      customerName: values.name,
      contactName: values.contact,
      contactPhone: values.mobile,
      settlementMethod: values.settlementMethod || 'BANK_TRANSFER',
      creditLimit: values.creditLimit ?? 0,
      address: values.address,
      status: values.status,
      remark: values.remark
    }

    if (formData.id) {
      await updateCustomer(formData.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createCustomer(payload)
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
    const blob = await exportCustomers(searchForm)
    downloadBlob(blob, `客户列表_${Date.now()}.csv`)
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
.customer-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #fff5f5 0%, #fff0e6 100%);
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
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8px 32px rgba(255, 107, 53, 0.3);
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  top: -30%;
  left: -5%;
  width: 250px;
  height: 250px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.15) 0%, transparent 70%);
  border-radius: 50%;
  animation: floatLeft 5s ease-in-out infinite;
}

@keyframes floatLeft {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg);
  }
  50% {
    transform: translate(20px, -15px) rotate(180deg);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.icon-wrapper {
  position: relative;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-icon {
  font-size: 40px;
  color: #ffffff;
  position: relative;
  z-index: 2;
  animation: iconBounce 2s ease-in-out infinite;
}

@keyframes iconBounce {
  0%, 100% {
    transform: scale(1) translateY(0);
  }
  50% {
    transform: scale(1.1) translateY(-5px);
  }
}

.icon-ripple {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  animation: ripple 2s ease-out infinite;
}

@keyframes ripple {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(1.8);
    opacity: 0;
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
  min-width: 100px;
}

.stat-card:hover {
  background: rgba(255, 255, 255, 0.28);
  transform: translateY(-3px);
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

.stat-value.company {
  color: #4facfe;
  text-shadow: 0 0 10px rgba(79, 172, 254, 0.6);
}

.stat-value.individual {
  color: #43e97b;
  text-shadow: 0 0 10px rgba(67, 233, 123, 0.6);
}

.customer-table {
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

.code-badge.customer {
  background: linear-gradient(135deg, #ff6b35 0%, #f7931e 100%);
  display: inline-block;
  padding: 4px 10px;
  color: #ffffff;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  font-family: 'Courier New', monospace;
}

.customer-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.customer-icon {
  color: #ff6b35;
  font-size: 16px;
}

.credit-value {
  font-weight: 600;
  color: #ff6b35;
  font-size: 14px;
}

.no-limit,
.unlimited {
  color: #52c41a;
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

.customer-dialog :deep(.el-dialog__header) {
  background: linear-gradient(to right, #fff5f5, #fff0e6);
  padding: 24px 32px;
  border-bottom: 1px solid #ffe6d9;
}

.customer-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.customer-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.customer-dialog :deep(.el-radio) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
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
  border-bottom: 2px solid #ff6b35;
  letter-spacing: 0.3px;
}

.detail-section .section-title .el-icon {
  color: #ff6b35;
  font-size: 16px;
}

.detail-value.credit {
  font-size: 18px;
  font-weight: 700;
  color: #ff6b35;
}

.credit-exceeded {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
