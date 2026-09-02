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
      @selection-change="handleSelectionChange"
      class="customer-table"
    >
      <template #toolbar-left>
        <el-button v-permission="'masterdata:customer:create'" type="primary" :icon="Plus" @click="handleCreate">
          {{ texts.createCustomer }}
        </el-button>
        <el-button
          v-permission="'masterdata:customer:enable'"
          :disabled="selectedRows.length === 0 || batchRunning"
          :loading="batchRunning"
          :icon="CircleCheck"
          @click="handleBatchEnable"
        >
          {{ labelWithCount(texts.batchEnable, selectedRows.length) }}
        </el-button>
        <el-button
          v-permission="'masterdata:customer:disable'"
          :disabled="selectedRows.length === 0 || batchRunning"
          :loading="batchRunning"
          :icon="Delete"
          @click="handleBatchDisable"
        >
          {{ labelWithCount(texts.batchDisable, selectedRows.length) }}
        </el-button>
        <el-button
          :disabled="selectedRows.length === 0"
          :icon="Download"
          @click="handleExportSelected"
        >
          {{ labelWithCount(texts.exportSelected, selectedRows.length) }}
        </el-button>
      </template>
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
      <el-table-column :label="texts.actions" width="260" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button link type="primary" @click="handleView(row)">
              <el-icon><View /></el-icon>
              {{ texts.view }}
            </el-button>
            <el-button link type="primary" @click="openRelations(row)">
              <el-icon><Goods /></el-icon>
              {{ texts.relations }}
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
        <el-form-item :label="texts.creditPeriodDays" prop="creditPeriod">
          <el-input-number
            v-model="formData.creditPeriod"
            :min="0"
            :precision="0"
            :controls="false"
            :placeholder="texts.enterCreditPeriod"
            style="width: 100%"
          />
          <span class="form-tip">{{ texts.creditPeriodHint }}</span>
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
              <div class="detail-label">{{ texts.creditPeriodDays }}</div>
              <div class="detail-value credit" :class="{ 'cash-only': !hasCreditPeriod(currentRow?.creditPeriod) }">
                {{ formatCreditPeriod(currentRow?.creditPeriod) }}
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

    <!-- 客户商品关系对话框 -->
    <el-dialog
      v-model="relationVisible"
      :title="relationDialogTitle"
      width="960px"
      :close-on-click-modal="false"
      class="elegant-dialog customer-dialog"
      @closed="closeRelations"
    >
      <div class="relation-panel">
        <div class="relation-toolbar">
          <span class="relation-hint">{{ texts.relationHint }}</span>
          <el-button
            v-permission="'masterdata:customer:update'"
            type="primary"
            :icon="Plus"
            @click="handleRelationCreate"
          >
            {{ texts.createRelation }}
          </el-button>
        </div>
        <el-table
          v-loading="relationLoading"
          :data="relationRows"
          border
          size="small"
          class="relation-table"
          :empty-text="texts.relationEmpty"
        >
          <el-table-column prop="productCode" :label="texts.productCode" width="140">
            <template #default="{ row }">
              <span v-if="row.productCode" class="code-badge customer">{{ row.productCode }}</span>
              <el-tag v-else type="warning" size="small">{{ texts.productMissing }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="productName" :label="texts.productName" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.productName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="customerProductCode" :label="texts.customerProductCode" width="140">
            <template #default="{ row }">{{ row.customerProductCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="customerProductName" :label="texts.customerProductName" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.customerProductName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="deliveryPreference" :label="texts.deliveryPreference" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.deliveryPreference || '-' }}</template>
          </el-table-column>
          <el-table-column prop="packagingPreference" :label="texts.packagingPreference" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.packagingPreference || '-' }}</template>
          </el-table-column>
          <el-table-column :label="texts.actions" width="140" align="center">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button v-permission="'masterdata:customer:update'" link type="primary" @click="handleRelationEdit(row)">
                  <el-icon><Edit /></el-icon>
                  {{ texts.edit }}
                </el-button>
                <el-button v-permission="'masterdata:customer:update'" link type="danger" @click="handleRelationDelete(row)">
                  <el-icon><Delete /></el-icon>
                  {{ texts.delete }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 客户商品关系表单 -->
    <el-dialog
      v-model="relationFormVisible"
      :title="relationFormTitle"
      width="560px"
      :close-on-click-modal="false"
      class="elegant-dialog customer-dialog"
    >
      <el-form :model="relationForm" :rules="relationFormRules" label-width="120px" class="relation-form">
        <el-form-item :label="texts.productName" prop="productId">
          <el-select
            v-model="relationForm.productId"
            :placeholder="texts.selectProduct"
            filterable
            :disabled="Boolean(relationForm.id)"
            style="width: 100%"
          >
            <el-option
              v-for="product in relationProducts"
              :key="product.id"
              :label="`${product.productCode} / ${product.productName}`"
              :value="String(product.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="texts.customerProductCode" prop="customerProductCode">
          <el-input v-model="relationForm.customerProductCode" :placeholder="texts.enterCustomerProductCode" maxlength="50" />
        </el-form-item>
        <el-form-item :label="texts.customerProductName" prop="customerProductName">
          <el-input v-model="relationForm.customerProductName" :placeholder="texts.enterCustomerProductName" maxlength="100" />
        </el-form-item>
        <el-form-item :label="texts.deliveryPreference" prop="deliveryPreference">
          <el-input v-model="relationForm.deliveryPreference" :placeholder="texts.enterDeliveryPreference" maxlength="100" />
        </el-form-item>
        <el-form-item :label="texts.packagingPreference" prop="packagingPreference">
          <el-input v-model="relationForm.packagingPreference" :placeholder="texts.enterPackagingPreference" maxlength="100" />
        </el-form-item>
        <el-form-item :label="texts.remark" prop="remark">
          <el-input
            v-model="relationForm.remark"
            type="textarea"
            :rows="2"
            :placeholder="texts.enterRemark"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationFormVisible = false">{{ texts.cancel }}</el-button>
        <el-button type="primary" :loading="relationSubmitting" @click="handleRelationSubmit">
          {{ texts.confirm }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
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
  Notebook,
  Plus,
  Download,
  Goods
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
  getProducts,
  getCustomerProductRelations,
  saveCustomerProductRelation,
  deleteCustomerProductRelation
} from '@/api/masterdata'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { useCustomerPresentation } from '@/composables/useCustomerPresentation'
import { useCustomerList } from '@/composables/useCustomerList'
import { useCustomerForm } from '@/composables/useCustomerForm'
import { useCustomerProductRelations } from '@/composables/useCustomerProductRelations'

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
    creditPeriod: '账期',
    creditPeriodDays: '账期（天）',
    creditPeriodValue: '{days} 天',
    cashSettlement: '现结',
    enterCreditPeriod: '请输入账期天数',
    creditPeriodHint: '0 或空表示现结',
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
    export: '导出',
    refresh: '刷新',
    confirm: '确定',
    cancel: '取消',
    batchEnable: '批量启用',
    batchDisable: '批量停用',
    exportSelected: '导出选中',
    selectedExportFilename: '客户_选中{count}条',
    batchEnableTitle: '批量启用',
    batchDisableTitle: '批量停用',
    batchEnableConfirm: '确认启用选中的 {count} 条客户吗？',
    batchDisableConfirm: '确认停用选中的 {count} 个客户吗？',
    batchEnableSuccess: '已启用 {count} 条',
    batchDisableSuccess: '已停用 {count} 条',
    batchEnablePartial: '已启用 {success} 条，失败 {failedCount} 条：{failed}',
    batchDisablePartial: '已停用 {success} 条，失败 {failedCount} 条：{failed}',
    validationEnterCode: '请输入客户编码',
    validationCodeLength: '长度在 2 到 50 个字符',
    validationEnterName: '请输入客户名称',
    validationNameLength: '长度在 2 到 100 个字符',
    validationSettlementMethod: '请选择结算方式',
    validationCreditLimit: '请输入信用额度（0=不限额）',
    validationMobile: '请输入正确的手机号码',
    validationEmail: '请输入正确的邮箱地址',
    relations: '商品',
    relationDialogTitle: '客户商品关系 - {name}',
    relationHint: '维护客户专属料号与交付偏好，销售单据和对外单据会引用这里的口径。',
    relationEmpty: '该客户暂无商品关系',
    createRelation: '新增商品关系',
    editRelation: '编辑商品关系',
    productCode: '商品编码',
    productName: '商品名称',
    productMissing: '商品已停用',
    customerProductCode: '客户料号',
    customerProductName: '客户品名',
    deliveryPreference: '交付偏好',
    packagingPreference: '包装偏好',
    selectProduct: '请选择商品',
    enterCustomerProductCode: '请输入客户料号',
    enterCustomerProductName: '请输入客户品名',
    enterDeliveryPreference: '例如：每周二送货',
    enterPackagingPreference: '例如：纸箱、托盘',
    relationLoadFailed: '加载客户商品关系失败',
    relationOptionsLoadFailed: '加载商品列表失败',
    relationSaveSuccess: '商品关系已保存',
    relationSaveFailed: '保存商品关系失败',
    relationDeleteSuccess: '商品关系已删除',
    relationDeleteFailed: '删除商品关系失败',
    confirmDeleteRelation: '确认删除商品关系“{product}”吗？',
    validationSelectProduct: '请选择商品'
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
    creditPeriod: 'Credit period',
    creditPeriodDays: 'Credit period (days)',
    creditPeriodValue: '{days} days',
    cashSettlement: 'Cash',
    enterCreditPeriod: 'Enter credit period days',
    creditPeriodHint: '0 or empty means cash terms',
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
    export: 'Export',
    refresh: 'Refresh',
    confirm: 'Confirm',
    cancel: 'Cancel',
    batchEnable: 'Enable selected',
    batchDisable: 'Disable selected',
    exportSelected: 'Export selected',
    selectedExportFilename: 'customers-selected-{count}',
    batchEnableTitle: 'Enable Selected',
    batchDisableTitle: 'Disable Selected',
    batchEnableConfirm: 'Enable {count} selected customers?',
    batchDisableConfirm: 'Disable {count} selected customers?',
    batchEnableSuccess: 'Enabled {count} customers',
    batchDisableSuccess: 'Disabled {count} customers',
    batchEnablePartial: 'Enabled {success} customers, failed {failedCount}: {failed}',
    batchDisablePartial: 'Disabled {success} customers, failed {failedCount}: {failed}',
    validationEnterCode: 'Enter customer code',
    validationCodeLength: 'Length must be between 2 and 50 characters',
    validationEnterName: 'Enter customer name',
    validationNameLength: 'Length must be between 2 and 100 characters',
    validationSettlementMethod: 'Select a settlement method',
    validationCreditLimit: 'Enter a credit limit (0 = unlimited)',
    validationMobile: 'Enter a valid mobile number',
    validationEmail: 'Enter a valid email address',
    relations: 'Products',
    relationDialogTitle: 'Customer product relations - {name}',
    relationHint: 'Maintain customer-specific part numbers and delivery preferences used by sales documents.',
    relationEmpty: 'No product relations for this customer yet',
    createRelation: 'Add relation',
    editRelation: 'Edit relation',
    productCode: 'Product code',
    productName: 'Product name',
    productMissing: 'Product inactive',
    customerProductCode: 'Customer part no.',
    customerProductName: 'Customer part name',
    deliveryPreference: 'Delivery preference',
    packagingPreference: 'Packaging preference',
    selectProduct: 'Select a product',
    enterCustomerProductCode: 'Enter customer part number',
    enterCustomerProductName: 'Enter customer part name',
    enterDeliveryPreference: 'For example: delivery every Tuesday',
    enterPackagingPreference: 'For example: carton, pallet',
    relationLoadFailed: 'Failed to load customer product relations',
    relationOptionsLoadFailed: 'Failed to load products',
    relationSaveSuccess: 'Product relation saved',
    relationSaveFailed: 'Failed to save product relation',
    relationDeleteSuccess: 'Product relation removed',
    relationDeleteFailed: 'Failed to remove product relation',
    confirmDeleteRelation: 'Remove product relation "{product}"?',
    validationSelectProduct: 'Select a product'
  }
} as const
const texts = computed(() => CUSTOMER_TEXTS[appStore.locale as keyof typeof CUSTOMER_TEXTS])
const displayPreferences = computed(() => ({
  locale: appStore.locale,
  timeZone: appStore.timeZone
}))

const {
  companyCount: countCompany,
  formatCreditLimit,
  formatCreditPeriod,
  formatCurrency,
  formatDateTime,
  hasCreditPeriod,
  individualCount: countIndividual,
  interpolate,
  joinNames,
  labelWithCount
} = useCustomerPresentation(texts, displayPreferences)

const {
  batchRunning,
  creditExposure,
  currentRow,
  detailVisible,
  handleBatchDisable,
  handleBatchEnable,
  handleDelete,
  handleEnable,
  handleExport,
  handleExportSelected,
  handlePageChange,
  handleReset,
  handleSearch,
  handleSelectionChange,
  handleView,
  loadData,
  loading,
  searchForm,
  selectedRows,
  tableData,
  total
} = useCustomerList(texts, {
  getCustomers,
  getCustomer,
  getCreditExposure: getCustomerCreditExposure,
  enableCustomer,
  deleteCustomer,
  exportCustomers,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  cancelLabel: () => (appStore.locale === 'en-US' ? 'Cancel' : '取消'),
  interpolate,
  joinNames: (items, locale) => joinNames(items, locale),
  formatCurrency,
  locale: computed(() => appStore.locale),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message)
})

const companyCount = computed(() => countCompany(tableData.value))
const individualCount = computed(() => countIndividual(tableData.value))

const {
  dialogTitle,
  dialogVisible,
  formData,
  formRules,
  handleCreate,
  handleEdit,
  handleSubmit,
  submitting
} = useCustomerForm(texts, {
  createCustomer,
  updateCustomer,
  onSuccess: (message) => ElMessage.success(message),
  onError: (message) => ElMessage.error(message),
  onCompleted: () => loadData()
})

const {
  closeRelations,
  handleRelationCreate,
  handleRelationDelete,
  handleRelationEdit,
  handleRelationSubmit,
  openRelations,
  relationForm,
  relationFormRules,
  relationFormTitle,
  relationFormVisible,
  relationLoading,
  relationOwner,
  relationProducts,
  relationRows,
  relationSubmitting,
  relationVisible
} = useCustomerProductRelations(texts, {
  getCustomerProductRelations,
  saveCustomerProductRelation,
  deleteCustomerProductRelation,
  getProducts,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts as any),
  interpolate,
  cancelLabel: () => texts.value.cancel,
  onSuccess: (message) => ElMessage.success(message),
  onError: (message) => ElMessage.error(message)
})

const relationDialogTitle = computed(() => interpolate(texts.value.relationDialogTitle, {
  name: relationOwner.value?.customerName || relationOwner.value?.name || ''
}))

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

.relation-panel {
  padding: 24px 32px;
}

.relation-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.relation-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}

.relation-table {
  width: 100%;
}

.relation-form {
  padding: 24px 32px 0;
}
</style>
