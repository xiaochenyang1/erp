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
            <h1 class="page-title">{{ texts.pageTitle }}</h1>
            <p class="page-subtitle">{{ texts.pageSubtitle }}</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">{{ texts.totalSuppliers }}</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ texts.activeSuppliers }}</span>
            <span class="stat-value active">{{ activeCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item :label="texts.supplierCode" prop="code">
        <el-input
          v-model="searchForm.code"
          :placeholder="texts.enterSupplierCode"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item :label="texts.supplierName" prop="name">
        <el-input
          v-model="searchForm.name"
          :placeholder="texts.enterSupplierName"
          clearable
          @keyup.enter="handleSearch"
        />
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
      class="supplier-table"
      @create="handleCreate"
      @export="handleExport"
      @refresh="loadData"
      @page-change="handlePageChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" :label="texts.supplierCode" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge supplier">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="texts.supplierName" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="supplier-name">
            <el-icon class="supplier-icon">
              <OfficeBuilding />
            </el-icon>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="contact" :label="texts.contact" width="120" />
      <el-table-column prop="mobile" :label="texts.phone" width="140" />
      <el-table-column prop="email" :label="texts.email" width="180" show-overflow-tooltip />
      <el-table-column prop="creditPeriod" :label="texts.creditPeriod" width="120" align="center">
        <template #default="{ row }">
          <span :class="hasCreditPeriod(row.creditPeriod) ? 'credit-period' : 'no-credit'">
            {{ formatCreditPeriod(row.creditPeriod) }}
          </span>
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
            <el-button v-permission="'masterdata:supplier:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              {{ texts.edit }}
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'masterdata:supplier:enable'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              <el-icon><CircleCheck /></el-icon>
              {{ texts.enable }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'masterdata:supplier:disable'"
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
        <el-form-item :label="texts.supplierCode" prop="code">
          <el-input v-model="formData.code" :placeholder="texts.enterSupplierCode" maxlength="50" />
        </el-form-item>
        <el-form-item :label="texts.supplierName" prop="name">
          <el-input v-model="formData.name" :placeholder="texts.enterSupplierName" maxlength="100" />
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
        <el-form-item :label="texts.settlementMethod" prop="settlementMethod">
          <el-select v-model="formData.settlementMethod" :placeholder="texts.selectSettlementMethod" style="width: 100%">
            <el-option
              v-for="option in settlementMethodOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="texts.creditPeriodDays" prop="creditPeriod">
          <el-input-number
            v-model="formData.creditPeriod"
            :min="0"
            :max="365"
            :controls="false"
            :placeholder="texts.enterCreditPeriod"
            style="width: 100%"
          />
          <span class="form-tip">{{ texts.creditHint }}</span>
        </el-form-item>
        <el-form-item :label="texts.address" prop="address" :style="{ gridColumn: '1 / -1' }">
          <el-input v-model="formData.address" :placeholder="texts.enterAddress" maxlength="200" />
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
      :title="texts.supplierDetail"
      width="750px"
      class="elegant-dialog supplier-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">
            <el-icon><DocumentCopy /></el-icon>
            {{ texts.basicInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.supplierCode }}</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.supplierName }}</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
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
            <el-icon><Calendar /></el-icon>
            {{ texts.financialInfo }}
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.settlementMethod }}</div>
              <div class="detail-value">{{ settlementMethodLabel(currentRow?.settlementMethod) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.creditPeriodDays }}</div>
              <div class="detail-value credit" :class="{ 'cash-only': !hasCreditPeriod(currentRow?.creditPeriod) }">
                {{ formatCreditPeriod(currentRow?.creditPeriod) }}
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.outstandingPayable }}</div>
              <div class="detail-value credit">{{ formatCurrency(payableExposure?.outstandingPayable) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.openPurchaseOrderAmount }}</div>
              <div class="detail-value credit">{{ formatCurrency(payableExposure?.openPurchaseOrderAmount) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.totalExposure }}</div>
              <div class="detail-value credit">{{ formatCurrency(payableExposure?.totalExposure) }}</div>
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
  getSupplierPayableExposure,
  createSupplier,
  updateSupplier,
  deleteSupplier,
  enableSupplier,
  exportSuppliers,
  type Supplier,
  type SupplierPayableExposure,
  type SupplierQuery,
  type SupplierSaveRequest
} from '@/api/masterdata'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { downloadBlob } from '@/utils/download'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { formatLocalizedCurrency, formatLocalizedDateTime } from '@/utils/locale'

const appStore = useAppStore()
const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('masterdata:supplier:create'))
const SUPPLIER_TEXTS = {
  'zh-CN': {
    pageTitle: '供应商管理',
    pageSubtitle: '管理供应商档案，维护供应链合作关系',
    totalSuppliers: '供应商总数',
    activeSuppliers: '合作中',
    supplierCode: '供应商编码',
    supplierName: '供应商名称',
    contact: '联系人',
    phone: '联系电话',
    email: '邮箱',
    creditPeriod: '账期',
    creditPeriodDays: '账期（天）',
    creditPeriodValue: '{days} 天',
    cashSettlement: '现结',
    status: '状态',
    actions: '操作',
    view: '查看',
    edit: '编辑',
    enable: '启用',
    delete: '删除',
    cancel: '取消',
    enterSupplierCode: '请输入供应商编码',
    enterSupplierName: '请输入供应商名称',
    selectStatus: '请选择状态',
    createSupplier: '新增供应商',
    editSupplier: '编辑供应商',
    supplierDetail: '供应商详情',
    enterContact: '请输入联系人姓名',
    enterPhone: '请输入联系电话',
    enterEmail: '请输入电子邮箱',
    settlementMethod: '结算方式',
    selectSettlementMethod: '请选择结算方式',
    bankTransfer: '银行转账',
    cash: '现金',
    monthly: '月结',
    cod: '货到付款',
    enterCreditPeriod: '请输入账期天数',
    creditHint: '留空表示现结',
    address: '联系地址',
    enterAddress: '请输入详细地址',
    active: '启用',
    inactive: '停用',
    remark: '备注',
    enterRemark: '请输入备注信息',
    basicInfo: '基本信息',
    contactInfo: '联系信息',
    financialInfo: '财务信息',
    otherInfo: '其他信息',
    outstandingPayable: '未结应付',
    openPurchaseOrderAmount: '未收货采购承诺',
    totalExposure: '应付敞口合计',
    createdTime: '创建时间',
    updatedTime: '更新时间',
    loadFailed: '加载数据失败',
    loadDetailFailed: '加载供应商详情失败',
    confirmTitle: '提示',
    confirmDelete: '确认删除供应商“{name}”吗？',
    confirmEnable: '确认启用供应商“{name}”吗？',
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
    exportFilename: '供应商列表',
    validationEnterCode: '请输入供应商编码',
    validationCodeLength: '长度在 2 到 50 个字符',
    validationEnterName: '请输入供应商名称',
    validationNameLength: '长度在 2 到 100 个字符',
    validationSettlementMethod: '请选择结算方式',
    validationMobile: '请输入正确的手机号码',
    validationEmail: '请输入正确的邮箱地址'
  },
  'en-US': {
    pageTitle: 'Supplier Management',
    pageSubtitle: 'Manage supplier records and maintain procurement partnerships',
    totalSuppliers: 'Total suppliers',
    activeSuppliers: 'Active',
    supplierCode: 'Supplier code',
    supplierName: 'Supplier name',
    contact: 'Contact',
    phone: 'Phone',
    email: 'Email',
    creditPeriod: 'Credit period',
    creditPeriodDays: 'Credit period (days)',
    creditPeriodValue: '{days} days',
    cashSettlement: 'Cash settlement',
    status: 'Status',
    actions: 'Actions',
    view: 'View',
    edit: 'Edit',
    enable: 'Enable',
    delete: 'Delete',
    cancel: 'Cancel',
    enterSupplierCode: 'Enter supplier code',
    enterSupplierName: 'Enter supplier name',
    selectStatus: 'Select status',
    createSupplier: 'Create supplier',
    editSupplier: 'Edit supplier',
    supplierDetail: 'Supplier details',
    enterContact: 'Enter contact name',
    enterPhone: 'Enter phone number',
    enterEmail: 'Enter email address',
    settlementMethod: 'Settlement method',
    selectSettlementMethod: 'Select settlement method',
    bankTransfer: 'Bank transfer',
    cash: 'Cash',
    monthly: 'Monthly',
    cod: 'Cash on delivery',
    enterCreditPeriod: 'Enter credit period in days',
    creditHint: 'Leave blank for cash settlement',
    address: 'Address',
    enterAddress: 'Enter address',
    active: 'Active',
    inactive: 'Inactive',
    remark: 'Remark',
    enterRemark: 'Enter remark',
    basicInfo: 'Basic information',
    contactInfo: 'Contact information',
    financialInfo: 'Financial information',
    otherInfo: 'Other information',
    outstandingPayable: 'Outstanding payables',
    openPurchaseOrderAmount: 'Open purchase commitments',
    totalExposure: 'Total payable exposure',
    createdTime: 'Created at',
    updatedTime: 'Updated at',
    loadFailed: 'Failed to load suppliers',
    loadDetailFailed: 'Failed to load supplier details',
    confirmTitle: 'Confirm',
    confirmDelete: 'Delete supplier "{name}"?',
    confirmEnable: 'Enable supplier "{name}"?',
    deleteSuccess: 'Supplier deleted',
    deleteFailed: 'Failed to delete supplier',
    enableSuccess: 'Supplier enabled',
    enableFailed: 'Failed to enable supplier',
    updateSuccess: 'Supplier updated',
    createSuccess: 'Supplier created',
    updateFailed: 'Failed to update supplier',
    createFailed: 'Failed to create supplier',
    exportSuccess: 'Export completed',
    exportFailed: 'Failed to export suppliers',
    exportFilename: 'supplier-list',
    validationEnterCode: 'Enter supplier code',
    validationCodeLength: 'Length must be between 2 and 50 characters',
    validationEnterName: 'Enter supplier name',
    validationNameLength: 'Length must be between 2 and 100 characters',
    validationSettlementMethod: 'Select a settlement method',
    validationMobile: 'Enter a valid mobile number',
    validationEmail: 'Enter a valid email address'
  }
} as const
const texts = computed(() => SUPPLIER_TEXTS[appStore.locale as keyof typeof SUPPLIER_TEXTS])
const displayPreferences = computed(() => ({
  locale: appStore.locale,
  timeZone: appStore.timeZone
}))
const interpolate = (template: string, params: Record<string, string | number>) =>
  template.replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))
const settlementMethodOptions = computed(() => ([
  { label: texts.value.bankTransfer, value: 'BANK_TRANSFER' },
  { label: texts.value.cash, value: 'CASH' },
  { label: texts.value.monthly, value: 'MONTHLY' },
  { label: texts.value.cod, value: 'COD' }
]))
const formatCurrency = (value?: number | string | null) => {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '-'
  return formatLocalizedCurrency(amount, {}, displayPreferences.value)
}
const formatDateTime = (value?: string | null) => (
  value ? formatLocalizedDateTime(value, {}, displayPreferences.value) || '-' : '-'
)
const hasCreditPeriod = (value?: number | string | null) => Number(value) > 0
const formatCreditPeriod = (value?: number | string | null) => (
  hasCreditPeriod(value)
    ? interpolate(texts.value.creditPeriodValue, { days: Number(value) })
    : texts.value.cashSettlement
)
const settlementMethodLabel = (value?: string | null) => (
  settlementMethodOptions.value.find((option) => option.value === value)?.label || value || '-'
)

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
const dialogTitle = computed(() => (formData.id ? texts.value.editSupplier : texts.value.createSupplier))
const submitting = ref(false)
const detailVisible = ref(false)
const currentRow = ref<Supplier>()
const payableExposure = ref<SupplierPayableExposure>()

// 表单数据
const formData = reactive<SupplierSaveRequest & { id?: string }>({
  code: '',
  name: '',
  contact: '',
  mobile: '',
  email: '',
  settlementMethod: 'BANK_TRANSFER',
  address: '',
  creditPeriod: undefined,
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
    settlementMethod: 'BANK_TRANSFER',
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
    settlementMethod: row.settlementMethod || 'BANK_TRANSFER',
    address: row.address,
    creditPeriod: hasCreditPeriod(row.creditPeriod) ? Number(row.creditPeriod) : undefined,
    status: row.status,
    remark: row.remark
  })
  dialogVisible.value = true
}

// 查看
const handleView = async (row: Supplier) => {
  try {
    currentRow.value = await getSupplier(row.id)
    payableExposure.value = await getSupplierPayableExposure(row.id)
    detailVisible.value = true
  } catch {
    ElMessage.error(texts.value.loadDetailFailed)
  }
}

// 删除
const handleDelete = async (row: Supplier) => {
  try {
    await ElMessageBox.confirm(
      interpolate(texts.value.confirmDelete, { name: row.name || row.supplierName || '' }),
      texts.value.confirmTitle,
      {
        confirmButtonText: texts.value.delete,
        cancelButtonText: texts.value.cancel,
        type: 'warning'
      }
    )

    await deleteSupplier(row.id)
    ElMessage.success(texts.value.deleteSuccess)
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(texts.value.deleteFailed)
    }
  }
}

const handleEnable = async (row: Supplier) => {
  try {
    await ElMessageBox.confirm(
      interpolate(texts.value.confirmEnable, { name: row.name || row.supplierName || '' }),
      texts.value.confirmTitle,
      {
        confirmButtonText: texts.value.enable,
        cancelButtonText: texts.value.cancel,
        type: 'warning'
      }
    )
    await enableSupplier(row.id)
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
      supplierCode: values.code,
      supplierName: values.name,
      contactName: values.contact,
      contactPhone: values.mobile,
      email: values.email,
      settlementMethod: values.settlementMethod || 'BANK_TRANSFER',
      creditPeriod: hasCreditPeriod(values.creditPeriod) ? Number(values.creditPeriod) : undefined,
      address: values.address,
      status: values.status,
      remark: values.remark
    }

    if (formData.id) {
      await updateSupplier(formData.id, payload)
      ElMessage.success(texts.value.updateSuccess)
    } else {
      await createSupplier(payload)
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
    const blob = await exportSuppliers(searchForm)
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
.supplier-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #f0fff4 0%, #e6f7ed 100%);
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
