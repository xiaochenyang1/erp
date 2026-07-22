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
            <h1 class="page-title">{{ texts.pageTitle }}</h1>
            <p class="page-subtitle">{{ texts.pageSubtitle }}</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">{{ texts.totalCustomers }}</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ texts.companyCustomers }}</span>
            <span class="stat-value company">{{ companyCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ texts.individualCustomers }}</span>
            <span class="stat-value individual">{{ individualCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item :label="texts.customerCode" prop="code">
        <el-input
          v-model="searchForm.code"
          :placeholder="texts.enterCustomerCode"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item :label="texts.customerName" prop="name">
        <el-input
          v-model="searchForm.name"
          :placeholder="texts.enterCustomerName"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item :label="texts.customerType" prop="type">
        <el-select v-model="searchForm.type" :placeholder="texts.selectCustomerType" clearable>
          <el-option :label="texts.company" value="COMPANY" />
          <el-option :label="texts.individual" value="INDIVIDUAL" />
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
      @create="handleCreate"
      :show-create="canCreate"
      @export="handleExport"
      @refresh="loadData"
      @page-change="handlePageChange"
      class="customer-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" :label="texts.customerCode" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge customer">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="texts.customerName" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="customer-name">
            <el-icon class="customer-icon">
              <component :is="row.type === 'COMPANY' ? OfficeBuilding : User" />
            </el-icon>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="type" :label="texts.customerType" width="110" align="center">
        <template #default="{ row }">
          <status-tag :status="row.type" />
        </template>
      </el-table-column>
      <el-table-column prop="contact" :label="texts.contact" width="120" />
      <el-table-column prop="mobile" :label="texts.phone" width="140" />
      <el-table-column prop="email" :label="texts.email" width="180" show-overflow-tooltip />
      <el-table-column prop="creditLimit" :label="texts.creditLimit" width="130" align="right">
        <template #default="{ row }">
          <span class="credit-value" v-if="row.creditLimit">
            {{ formatCurrency(row.creditLimit) }}
          </span>
          <span v-else class="no-limit">{{ texts.noLimit }}</span>
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
            <el-button v-permission="'masterdata:customer:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              {{ texts.edit }}
            </el-button>
            <el-button v-if="row.status !== 'ACTIVE'" v-permission="'masterdata:customer:enable'" link type="success" @click="handleEnable(row)">
              <el-icon><CircleCheck /></el-icon>
              {{ texts.enable }}
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'masterdata:customer:disable'" link type="danger" @click="handleDelete(row)">
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
        <el-form-item :label="texts.customerCode" prop="code">
          <el-input v-model="formData.code" :placeholder="texts.enterCustomerCode" maxlength="50" />
        </el-form-item>
        <el-form-item :label="texts.customerName" prop="name">
          <el-input v-model="formData.name" :placeholder="texts.enterCustomerName" maxlength="100" />
        </el-form-item>
        <el-form-item :label="texts.customerType" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio value="COMPANY">
              <el-icon><OfficeBuilding /></el-icon>
              {{ texts.company }}
            </el-radio>
            <el-radio value="INDIVIDUAL">
              <el-icon><User /></el-icon>
              {{ texts.individual }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="texts.contact" prop="contact">
          <el-input v-model="formData.contact" :placeholder="texts.enterContact" />
        </el-form-item>
        <el-form-item :label="texts.phone" prop="mobile">
          <el-input v-model="formData.mobile" :placeholder="texts.enterPhone" maxlength="20" />
        </el-form-item>
        <el-form-item :label="texts.email" prop="email">
          <el-input v-model="formData.email" :placeholder="texts.enterEmail" maxlength="100" />
        </el-form-item>
        <el-form-item :label="texts.address" prop="address" :style="{ gridColumn: '1 / -1' }">
          <el-input v-model="formData.address" :placeholder="texts.enterAddress" maxlength="200" />
        </el-form-item>
        <el-form-item :label="texts.settlementMethod" prop="settlementMethod">
          <el-select v-model="formData.settlementMethod" :placeholder="texts.selectSettlementMethod" style="width: 100%">
            <el-option :label="texts.bankTransfer" value="BANK_TRANSFER" />
            <el-option :label="texts.cash" value="CASH" />
            <el-option :label="texts.monthly" value="MONTHLY" />
            <el-option :label="texts.cod" value="COD" />
          </el-select>
        </el-form-item>
        <el-form-item :label="texts.creditLimit" prop="creditLimit">
          <el-input-number
            v-model="formData.creditLimit"
            :min="0"
            :precision="2"
            :controls="false"
            :placeholder="texts.enterCreditLimit"
            style="width: 100%"
          />
          <span class="form-tip">{{ texts.creditHint }}</span>
        </el-form-item>
        <el-form-item :label="texts.status" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">{{ texts.active }}</el-radio>
            <el-radio value="INACTIVE">{{ texts.inactive }}</el-radio>
          </el-radio-group>
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
      :title="texts.customerDetail"
      width="750px"
      class="elegant-dialog customer-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Notebook /></el-icon>
            {{ texts.basicInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.customerCode }}</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.customerName }}</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.customerType }}</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.type" />
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.status }}</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.status" />
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Phone /></el-icon>
            {{ texts.contactInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.contact }}</div>
              <div class="detail-value">{{ currentRow?.contact || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.phone }}</div>
              <div class="detail-value">{{ currentRow?.mobile || '-' }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">{{ texts.email }}</div>
              <div class="detail-value">{{ currentRow?.email || '-' }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">{{ texts.address }}</div>
              <div class="detail-value">{{ currentRow?.address || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Wallet /></el-icon>
            {{ texts.financialInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.creditLimit }}</div>
              <div class="detail-value credit">
                <span :class="{ unlimited: !currentRow?.creditLimit }">{{ formatCreditLimit(currentRow?.creditLimit) }}</span>
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.usedCredit }}</div>
              <div class="detail-value">{{ formatCurrency(creditExposure?.totalExposure) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.outstandingReceivable }}</div>
              <div class="detail-value">{{ formatCurrency(creditExposure?.outstandingReceivable) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.openOrders }}</div>
              <div class="detail-value">{{ formatCurrency(creditExposure?.openOrderExposure) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.availableCredit }}</div>
              <div class="detail-value" :class="{ 'credit-exceeded': creditExposure?.exceeded }">
                {{ creditExposure?.unlimited ? texts.noLimit : formatCurrency(creditExposure?.availableCredit) }}
              </div>
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
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { formatLocalizedCurrency, formatLocalizedDateTime } from '@/utils/locale'

const appStore = useAppStore()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('masterdata:customer:create'))
const CUSTOMER_TEXTS = {
  'zh-CN': {
    pageTitle: '客户管理',
    pageSubtitle: '维护客户关系，管理客户档案与信用信息',
    totalCustomers: '客户总数',
    companyCustomers: '企业客户',
    individualCustomers: '个人客户',
    customerCode: '客户编码',
    customerName: '客户名称',
    customerType: '客户类型',
    company: '企业',
    individual: '个人',
    contact: '联系人',
    phone: '联系电话',
    email: '邮箱',
    creditLimit: '信用额度',
    noLimit: '无限制',
    status: '状态',
    actions: '操作',
    view: '查看',
    edit: '编辑',
    enable: '启用',
    delete: '删除',
    enterCustomerCode: '请输入客户编码',
    enterCustomerName: '请输入客户名称',
    selectCustomerType: '请选择客户类型',
    selectStatus: '请选择状态',
    createCustomer: '新增客户',
    editCustomer: '编辑客户',
    customerDetail: '客户详情',
    enterContact: '请输入联系人姓名',
    enterPhone: '请输入联系电话',
    enterEmail: '请输入电子邮箱',
    address: '联系地址',
    enterAddress: '请输入详细地址',
    settlementMethod: '结算方式',
    selectSettlementMethod: '请选择结算方式',
    bankTransfer: '银行转账',
    cash: '现金',
    monthly: '月结',
    cod: '货到付款',
    enterCreditLimit: '请输入信用额度（元）',
    creditHint: '填 0 表示不限额；大于 0 时审批将校验未结敞口',
    active: '启用',
    inactive: '停用',
    remark: '备注',
    enterRemark: '请输入备注信息',
    basicInfo: '基本信息',
    contactInfo: '联系信息',
    financialInfo: '财务信息',
    otherInfo: '其他信息',
    usedCredit: '已用额度',
    outstandingReceivable: '未结应收',
    openOrders: '在途订单',
    availableCredit: '可用额度',
    createdTime: '创建时间',
    updatedTime: '更新时间',
    loadFailed: '加载数据失败',
    loadDetailFailed: '加载客户详情失败',
    confirmTitle: '提示',
    confirmDelete: '确认删除客户“{name}”吗？',
    confirmEnable: '确认启用客户“{name}”吗？',
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
    exportFilename: '客户列表',
    validationEnterCode: '请输入客户编码',
    validationCodeLength: '长度在 2 到 50 个字符',
    validationEnterName: '请输入客户名称',
    validationNameLength: '长度在 2 到 100 个字符',
    validationSettlementMethod: '请选择结算方式',
    validationCreditLimit: '请输入信用额度（0=不限额）',
    validationMobile: '请输入正确的手机号码',
    validationEmail: '请输入正确的邮箱地址'
  },
  'en-US': {
    pageTitle: 'Customer Management',
    pageSubtitle: 'Maintain customer records, relationships, and credit exposure',
    totalCustomers: 'Total customers',
    companyCustomers: 'Company customers',
    individualCustomers: 'Individual customers',
    customerCode: 'Customer code',
    customerName: 'Customer name',
    customerType: 'Customer type',
    company: 'Company',
    individual: 'Individual',
    contact: 'Contact',
    phone: 'Phone',
    email: 'Email',
    creditLimit: 'Credit limit',
    noLimit: 'Unlimited',
    status: 'Status',
    actions: 'Actions',
    view: 'View',
    edit: 'Edit',
    enable: 'Enable',
    delete: 'Delete',
    enterCustomerCode: 'Enter customer code',
    enterCustomerName: 'Enter customer name',
    selectCustomerType: 'Select customer type',
    selectStatus: 'Select status',
    createCustomer: 'Create customer',
    editCustomer: 'Edit customer',
    customerDetail: 'Customer details',
    enterContact: 'Enter contact name',
    enterPhone: 'Enter phone number',
    enterEmail: 'Enter email address',
    address: 'Address',
    enterAddress: 'Enter address',
    settlementMethod: 'Settlement method',
    selectSettlementMethod: 'Select settlement method',
    bankTransfer: 'Bank transfer',
    cash: 'Cash',
    monthly: 'Monthly',
    cod: 'Cash on delivery',
    enterCreditLimit: 'Enter credit limit',
    creditHint: 'Use 0 for no limit; positive values are checked against open exposure during approval.',
    active: 'Active',
    inactive: 'Inactive',
    remark: 'Remark',
    enterRemark: 'Enter remark',
    basicInfo: 'Basic information',
    contactInfo: 'Contact information',
    financialInfo: 'Financial information',
    otherInfo: 'Other information',
    usedCredit: 'Used credit',
    outstandingReceivable: 'Open receivables',
    openOrders: 'Open orders',
    availableCredit: 'Available credit',
    createdTime: 'Created at',
    updatedTime: 'Updated at',
    loadFailed: 'Failed to load customers',
    loadDetailFailed: 'Failed to load customer details',
    confirmTitle: 'Confirm',
    confirmDelete: 'Delete customer "{name}"?',
    confirmEnable: 'Enable customer "{name}"?',
    deleteSuccess: 'Customer deleted',
    deleteFailed: 'Failed to delete customer',
    enableSuccess: 'Customer enabled',
    enableFailed: 'Failed to enable customer',
    updateSuccess: 'Customer updated',
    createSuccess: 'Customer created',
    updateFailed: 'Failed to update customer',
    createFailed: 'Failed to create customer',
    exportSuccess: 'Export completed',
    exportFailed: 'Failed to export customers',
    exportFilename: 'customer-list',
    validationEnterCode: 'Enter customer code',
    validationCodeLength: 'Length must be between 2 and 50 characters',
    validationEnterName: 'Enter customer name',
    validationNameLength: 'Length must be between 2 and 100 characters',
    validationSettlementMethod: 'Select a settlement method',
    validationCreditLimit: 'Enter a credit limit (0 = unlimited)',
    validationMobile: 'Enter a valid mobile number',
    validationEmail: 'Enter a valid email address'
  }
} as const
const texts = computed(() => CUSTOMER_TEXTS[appStore.locale as keyof typeof CUSTOMER_TEXTS])
const displayPreferences = computed(() => ({
  locale: appStore.locale,
  timeZone: appStore.timeZone
}))
const interpolate = (template: string, params: Record<string, string | number>) =>
  template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))
const formatCurrency = (value?: number | string | null) => {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '-'
  return formatLocalizedCurrency(amount, {}, displayPreferences.value)
}
const formatDateTime = (value?: string | null) => (
  value ? formatLocalizedDateTime(value, {}, displayPreferences.value) || '-' : '-'
)
const formatCreditLimit = (value?: number | null) => (
  value ? formatCurrency(value) : texts.value.noLimit
)

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
const dialogTitle = computed(() => (formData.id ? texts.value.editCustomer : texts.value.createCustomer))
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
const formRules = computed(() => ({
  code: [
    { required: true, message: texts.value.validationEnterCode, trigger: 'blur' },
    { min: 2, max: 50, message: texts.value.validationCodeLength, trigger: 'blur' }
  ],
  name: [
    { required: true, message: texts.value.validationEnterName, trigger: 'blur' },
    { min: 2, max: 100, message: texts.value.validationNameLength, trigger: 'blur' }
  ],
  settlementMethod: [
    { required: true, message: texts.value.validationSettlementMethod, trigger: 'change' }
  ],
  creditLimit: [
    { required: true, message: texts.value.validationCreditLimit, trigger: 'change' }
  ],
  mobile: [
    { pattern: /^1[3-9]\d{9}$/, message: texts.value.validationMobile, trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: texts.value.validationEmail, trigger: 'blur' }
  ]
}))

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
    ElMessage.error(texts.value.loadFailed)
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
    ElMessage.error(texts.value.loadDetailFailed)
  }
}

// 删除
const handleDelete = async (row: Customer) => {
  try {
    await ElMessageBox.confirm(
      interpolate(texts.value.confirmDelete, { name: row.name }),
      texts.value.confirmTitle,
      {
        confirmButtonText: texts.value.delete,
        cancelButtonText: appStore.locale === 'en-US' ? 'Cancel' : '取消',
        type: 'warning'
      }
    )

    await deleteCustomer(row.id)
    ElMessage.success(texts.value.deleteSuccess)
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(texts.value.deleteFailed)
    }
  }
}

const handleEnable = async (row: Customer) => {
  try {
    await ElMessageBox.confirm(
      interpolate(texts.value.confirmEnable, { name: row.name }),
      texts.value.confirmTitle,
      {
        confirmButtonText: texts.value.enable,
        cancelButtonText: appStore.locale === 'en-US' ? 'Cancel' : '取消',
        type: 'warning'
      }
    )
    await enableCustomer(row.id)
    ElMessage.success(texts.value.enableSuccess)
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(texts.value.enableFailed)
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
      customerType: values.type,
      contactName: values.contact,
      contactPhone: values.mobile,
      email: values.email,
      settlementMethod: values.settlementMethod || 'BANK_TRANSFER',
      creditLimit: values.creditLimit ?? 0,
      address: values.address,
      status: values.status,
      remark: values.remark
    }

    if (formData.id) {
      await updateCustomer(formData.id, payload)
      ElMessage.success(texts.value.updateSuccess)
    } else {
      await createCustomer(payload)
      ElMessage.success(texts.value.createSuccess)
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error(formData.id ? texts.value.updateFailed : texts.value.createFailed)
  } finally {
    submitting.value = false
  }
}

// 导出
const handleExport = async () => {
  try {
    const blob = await exportCustomers(searchForm)
    downloadBlob(blob, `${texts.value.exportFilename}_${Date.now()}.csv`)
    ElMessage.success(texts.value.exportSuccess)
  } catch (error) {
    ElMessage.error(texts.value.exportFailed)
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
