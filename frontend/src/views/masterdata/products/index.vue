<template>
  <div class="product-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <el-icon class="header-icon">
            <Box />
          </el-icon>
          <div class="header-text">
            <h1 class="page-title">{{ texts.pageTitle }}</h1>
            <p class="page-subtitle">{{ texts.pageSubtitle }}</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">{{ texts.totalProducts }}</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ texts.activeProducts }}</span>
            <span class="stat-value active">{{ activeCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item :label="texts.productCode" prop="code">
        <el-input
          v-model="searchForm.code"
          :placeholder="texts.enterProductCode"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item :label="texts.productName" prop="name">
        <el-input
          v-model="searchForm.name"
          :placeholder="texts.enterProductName"
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
      :show-export="false"
      :data="tableData"
      :loading="loading"
      :total="total"
      :page="searchForm.pageNo"
      :page-size="searchForm.pageSize"
      class="product-table"
      @create="handleCreate"
      @export="handleExport"
      @refresh="loadData"
      @page-change="handlePageChange"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-left>
        <el-button v-permission="'masterdata:product:create'" type="primary" :icon="Plus" @click="handleCreate">
          {{ texts.createProduct }}
        </el-button>
        <el-button
          v-permission="'masterdata:product:enable'"
          :disabled="selectedRows.length === 0 || batchRunning"
          :loading="batchRunning"
          :icon="CircleCheck"
          @click="handleBatchEnable"
        >
          {{ labelWithCount(texts.batchEnable, selectedRows.length) }}
        </el-button>
        <el-button
          v-permission="'masterdata:product:disable'"
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
      <template #toolbar-right>
        <el-button :icon="Download" @click="handleExport">{{ texts.export }}</el-button>
        <table-column-setting
          :columns="productColumnOptions"
          :model-value="columnVisible"
          @update:model-value="handleColumnVisibleUpdate"
          @reset="resetColumns"
        />
        <el-button :icon="Refresh" circle :title="texts.refresh" @click="loadData" />
      </template>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" :label="texts.productCode" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="texts.productName" min-width="180" show-overflow-tooltip />
      <el-table-column v-if="isColumnVisible('categoryName')" prop="categoryName" :label="texts.productCategory" width="140" />
      <el-table-column v-if="isColumnVisible('specifications')" prop="specifications" :label="texts.specification" width="140" show-overflow-tooltip />
      <el-table-column v-if="isColumnVisible('unit')" prop="unit" :label="texts.unit" width="100" align="center">
        <template #default="{ row }">
          <span>{{ formatUnit(row.unit) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('auxUnitName')" prop="auxUnitName" :label="texts.auxUnit" width="120" align="center">
        <template #default="{ row }">
          <span>{{ formatUnit(row.auxUnitName) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('conversionFactor')" prop="conversionFactor" :label="texts.conversionFactor" width="120" align="right">
        <template #default="{ row }">
          <span>{{ row.conversionFactor != null && row.auxUnitName ? row.conversionFactor : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('unitPrice')" prop="unitPrice" :label="texts.salePrice" width="140" align="right">
        <template #default="{ row }">
          <span class="price-value">{{ formatCurrency(row.unitPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('costPrice')" prop="costPrice" :label="texts.costPrice" width="140" align="right">
        <template #default="{ row }">
          <span class="price-value">{{ formatCurrency(row.costPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('status')" prop="status" :label="texts.status" width="100" align="center">
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
            <el-button v-permission="'masterdata:product:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              {{ texts.edit }}
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'masterdata:product:enable'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              <el-icon><CircleCheck /></el-icon>
              {{ texts.enable }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'masterdata:product:disable'"
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
      width="800px"
      :close-on-click-modal="false"
      class="elegant-dialog"
    >
      <page-form
        v-model="formData"
        :rules="formRules"
        :loading="submitting"
        :columns="2"
        @submit="handleSubmit"
        @cancel="dialogVisible = false"
      >
        <el-form-item :label="texts.productCode" prop="code">
          <el-input v-model="formData.code" :placeholder="texts.enterProductCode" maxlength="50" />
        </el-form-item>
        <el-form-item :label="texts.productName" prop="name">
          <el-input v-model="formData.name" :placeholder="texts.enterProductName" maxlength="100" />
        </el-form-item>
        <el-form-item v-if="!formData.id" :label="texts.productType" prop="productType">
          <el-select v-model="formData.productType" :placeholder="texts.selectProductType">
            <el-option v-for="option in productTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="texts.productCategory" prop="categoryName">
          <el-input v-model="formData.categoryName" :placeholder="texts.enterProductCategory" maxlength="100" />
        </el-form-item>
        <el-form-item :label="texts.specification" prop="specifications">
          <el-input v-model="formData.specifications" :placeholder="texts.enterSpecification" />
        </el-form-item>
        <el-form-item :label="texts.unit" prop="unit">
          <el-select v-model="formData.unit" :placeholder="texts.selectUnit" clearable>
            <el-option v-for="option in unitOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="texts.auxUnit" prop="auxUnitName">
          <el-select v-model="formData.auxUnitName" :placeholder="texts.selectAuxUnit" clearable>
            <el-option v-for="option in unitOptions" :key="`aux-${option.value}`" :label="option.label" :value="option.value" />
          </el-select>
          <span class="form-tip">{{ texts.auxUnitHint }}</span>
        </el-form-item>
        <el-form-item :label="texts.conversionFactor" prop="conversionFactor">
          <el-input-number
            v-model="formData.conversionFactor"
            :min="0"
            :precision="6"
            :controls="false"
            :disabled="!formData.auxUnitName"
            :placeholder="texts.enterConversionFactor"
            style="width: 100%"
          />
          <span class="form-tip">{{ texts.conversionFactorHint }}</span>
        </el-form-item>
        <el-form-item :label="texts.salePrice" prop="unitPrice">
          <el-input-number
            v-model="formData.unitPrice"
            :min="0"
            :precision="2"
            :controls="false"
            :placeholder="texts.enterSalePrice"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="texts.costPrice" prop="costPrice">
          <el-input-number
            v-model="formData.costPrice"
            :min="0"
            :precision="2"
            :controls="false"
            :placeholder="texts.enterCostPrice"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="texts.taxRate" prop="taxRate">
          <el-input-number
            v-model="formData.taxRate"
            :min="0"
            :max="100"
            :precision="2"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="texts.barcode" prop="barcode">
          <el-input v-model="formData.barcode" :placeholder="texts.enterBarcode" />
        </el-form-item>
        <el-form-item :label="texts.status" prop="status" :style="{ gridColumn: '1 / -1' }">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">{{ texts.active }}</el-radio>
            <el-radio value="INACTIVE">{{ texts.inactive }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="texts.inspectionRequired" prop="inspectionRequired" :style="{ gridColumn: '1 / -1' }">
          <el-switch v-model="formData.inspectionRequired" />
        </el-form-item>
        <el-form-item :label="texts.serialControlled" prop="serialControlled" :style="{ gridColumn: '1 / -1' }">
          <el-switch v-model="formData.serialControlled" />
          <span class="form-tip">{{ texts.inspectionHint }}</span>
        </el-form-item>
        <el-form-item :label="texts.lotControlled" prop="lotControlled">
          <el-switch v-model="formData.lotControlled" />
        </el-form-item>
        <el-form-item :label="texts.shelfLifeControlled" prop="shelfLifeControlled">
          <el-switch v-model="formData.shelfLifeControlled" :disabled="!formData.lotControlled" />
          <span class="form-tip">{{ texts.shelfLifeHint }}</span>
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
      :title="texts.productDetail"
      width="700px"
      class="elegant-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">{{ texts.basicInfo }}</div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.productCode }}</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.productName }}</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.productCategory }}</div>
              <div class="detail-value">{{ currentRow?.categoryName || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.specification }}</div>
              <div class="detail-value">{{ currentRow?.specifications || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.unit }}</div>
              <div class="detail-value">{{ formatUnit(currentRow?.unit) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.auxUnit }}</div>
              <div class="detail-value">{{ formatUnit(currentRow?.auxUnitName) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.conversionFactor }}</div>
              <div class="detail-value">{{ currentRow?.conversionFactor != null && currentRow?.auxUnitName ? currentRow.conversionFactor : '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.barcode }}</div>
              <div class="detail-value">{{ currentRow?.barcode || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">{{ texts.pricingInfo }}</div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.salePrice }}</div>
              <div class="detail-value price">{{ formatCurrency(currentRow?.unitPrice) }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.costPrice }}</div>
              <div class="detail-value price">{{ formatCurrency(currentRow?.costPrice) }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">{{ texts.stockOverview }}</div>
          <div class="detail-row">
            <div class="detail-item"><div class="detail-label">{{ texts.qtyOnHand }}</div><div class="detail-value">{{ formatNumber(stockSummary?.qtyOnHand) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.qtyReserved }}</div><div class="detail-value">{{ formatNumber(stockSummary?.qtyReserved) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.qtyAvailable }}</div><div class="detail-value">{{ formatNumber(stockSummary?.qtyAvailable) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.warehouseCount }}</div><div class="detail-value">{{ formatNumber(stockSummary?.warehouseCount) }}</div></div>
            <div class="detail-item"><div class="detail-label">{{ texts.amountOnHand }}</div><div class="detail-value price">{{ formatCurrency(stockSummary?.amountOnHand) }}</div></div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">{{ texts.otherInfo }}</div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">{{ texts.status }}</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.status" />
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.inspectionRequired }}</div>
              <div class="detail-value">{{ currentRow?.inspectionRequired ? texts.yes : texts.no }}</div>
              <div class="detail-label">{{ texts.serialControlled }}</div>
              <div class="detail-value">{{ currentRow?.serialControlled ? texts.yes : texts.no }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">{{ texts.createdTime }}</div>
              <div class="detail-value">{{ formatDateTime(currentRow?.createdTime) }}</div>
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
import { Box, View, Edit, Delete, CircleCheck, Download, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getProducts,
  getProduct,
  getProductStockSummary,
  createProduct,
  updateProduct,
  deleteProduct,
  enableProduct,
  exportProducts
} from '@/api/masterdata'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard, TableColumnSetting } from '@/components/common'
import { useTablePreference } from '@/composables/useTablePreference'
import { useAppStore } from '@/store/modules/app'
import { useProductPresentation } from '@/composables/useProductPresentation'
import { useProductList } from '@/composables/useProductList'
import { useProductForm } from '@/composables/useProductForm'

const appStore = useAppStore()
const PRODUCT_TEXTS = {
  'zh-CN': {
    pageTitle: '产品管理',
    pageSubtitle: '管理系统中的所有产品信息',
    totalProducts: '总产品数',
    activeProducts: '启用中',
    productCode: '产品编码',
    productName: '产品名称',
    productCategory: '产品分类',
    specification: '规格型号',
    unit: '单位',
    auxUnit: '辅单位',
    conversionFactor: '换算率',
    selectAuxUnit: '请选择辅单位',
    enterConversionFactor: '1 辅单位 = N 库存单位',
    auxUnitHint: '可选；用于箱/件等包装单位',
    conversionFactorHint: '例如 1 箱 = 12 件，则填 12',
    validationConversionFactor: '启用辅单位时换算率必须大于0',
    salePrice: '销售单价',
    costPrice: '成本单价',
    taxRate: '税率（%）',
    barcode: '条形码',
    status: '状态',
    actions: '操作',
    view: '查看',
    edit: '编辑',
    enable: '启用',
    disable: '停用',
    delete: '删除',
    export: '导出',
    refresh: '刷新',
    cancel: '取消',
    confirm: '确定',
    active: '启用',
    inactive: '停用',
    enterProductCode: '请输入产品编码',
    enterProductName: '请输入产品名称',
    selectStatus: '请选择状态',
    createProduct: '新增产品',
    editProduct: '编辑产品',
    batchEnable: '批量启用',
    batchDisable: '批量停用',
    exportSelected: '导出选中',
    productType: '商品类型',
    selectProductType: '请选择商品类型',
    physicalProduct: '实物商品',
    goodsProduct: '库存商品',
    serviceProduct: '服务',
    enterProductCategory: '请输入产品分类',
    enterSpecification: '请输入规格型号',
    selectUnit: '请选择单位',
    enterSalePrice: '请输入销售单价',
    enterCostPrice: '请输入成本单价',
    enterBarcode: '请输入条形码',
    inspectionRequired: '来料需检验',
    serialControlled: '序列号管理',
    inspectionHint: '开启后，该商品的采购入库单在过账前必须先完成来料检验',
    lotControlled: '批次管理',
    shelfLifeControlled: '保质期管理',
    shelfLifeHint: '保质期管理必须同时开启批次管理',
    remark: '备注',
    enterRemark: '请输入备注信息',
    productDetail: '产品详情',
    basicInfo: '基本信息',
    pricingInfo: '价格信息',
    stockOverview: '库存概览',
    otherInfo: '其他信息',
    qtyOnHand: '现存数量',
    qtyReserved: '预占数量',
    qtyAvailable: '可用数量',
    warehouseCount: '有库存仓库',
    amountOnHand: '库存金额',
    yes: '是',
    no: '否',
    createdTime: '创建时间',
    loadFailed: '加载数据失败',
    loadDetailFailed: '加载产品详情失败',
    confirmTitle: '提示',
    confirmDelete: '确认删除产品“{name}”吗？',
    confirmEnable: '确认启用产品“{name}”吗？',
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
    exportFilename: '产品列表',
    selectedExportFilename: '产品_选中{count}条',
    batchEnableTitle: '批量启用',
    batchDisableTitle: '批量停用',
    batchEnableConfirm: '确认启用选中的 {count} 条数据吗？',
    batchDisableConfirm: '确认停用选中的 {count} 个产品吗？',
    batchEnableSuccess: '已启用 {count} 条',
    batchDisableSuccess: '已停用 {count} 条',
    batchEnablePartial: '已启用 {success} 条，失败 {failedCount} 条：{failed}',
    batchDisablePartial: '已停用 {success} 条，失败 {failedCount} 条：{failed}',
    validationEnterCode: '请输入产品编码',
    validationCodeLength: '长度在 2 到 50 个字符',
    validationEnterName: '请输入产品名称',
    validationNameLength: '长度在 2 到 100 个字符',
    validationProductType: '请选择商品类型',
    validationCategory: '请输入产品分类',
    validationUnit: '请选择单位',
    validationSalePrice: '请输入销售单价',
    validationCostPrice: '请输入成本单价',
    validationTaxRate: '请输入税率',
    unitPiece: '个',
    unitMachine: '台',
    unitItem: '件',
    unitBox: '箱'
  },
  'en-US': {
    pageTitle: 'Product Management',
    pageSubtitle: 'Manage product records in the system',
    totalProducts: 'Total products',
    activeProducts: 'Active',
    productCode: 'Product code',
    productName: 'Product name',
    productCategory: 'Category',
    specification: 'Specification',
    unit: 'Unit',
    auxUnit: 'Aux unit',
    conversionFactor: 'Conversion factor',
    selectAuxUnit: 'Select aux unit',
    enterConversionFactor: '1 aux unit = N stock units',
    auxUnitHint: 'Optional packaging unit such as carton',
    conversionFactorHint: 'Example: 1 carton = 12 pcs → enter 12',
    validationConversionFactor: 'Conversion factor must be greater than 0 when aux unit is set',
    salePrice: 'Sale price',
    costPrice: 'Cost price',
    taxRate: 'Tax rate (%)',
    barcode: 'Barcode',
    status: 'Status',
    actions: 'Actions',
    view: 'View',
    edit: 'Edit',
    enable: 'Enable',
    disable: 'Disable',
    delete: 'Delete',
    export: 'Export',
    refresh: 'Refresh',
    cancel: 'Cancel',
    confirm: 'Confirm',
    active: 'Active',
    inactive: 'Inactive',
    enterProductCode: 'Enter product code',
    enterProductName: 'Enter product name',
    selectStatus: 'Select status',
    createProduct: 'Create product',
    editProduct: 'Edit product',
    batchEnable: 'Enable selected',
    batchDisable: 'Disable selected',
    exportSelected: 'Export selected',
    productType: 'Product type',
    selectProductType: 'Select product type',
    physicalProduct: 'Physical product',
    goodsProduct: 'Inventory goods',
    serviceProduct: 'Service',
    enterProductCategory: 'Enter category',
    enterSpecification: 'Enter specification',
    selectUnit: 'Select unit',
    enterSalePrice: 'Enter sale price',
    enterCostPrice: 'Enter cost price',
    enterBarcode: 'Enter barcode',
    inspectionRequired: 'Incoming inspection required',
    serialControlled: 'Serial controlled',
    inspectionHint: 'When enabled, purchase receipts for this product must complete incoming inspection before posting.',
    lotControlled: 'Lot controlled',
    shelfLifeControlled: 'Shelf-life controlled',
    shelfLifeHint: 'Shelf-life control requires lot control to be enabled.',
    remark: 'Remark',
    enterRemark: 'Enter remark',
    productDetail: 'Product details',
    basicInfo: 'Basic information',
    pricingInfo: 'Pricing information',
    stockOverview: 'Stock overview',
    otherInfo: 'Other information',
    qtyOnHand: 'On-hand quantity',
    qtyReserved: 'Reserved quantity',
    qtyAvailable: 'Available quantity',
    warehouseCount: 'Warehouses with stock',
    amountOnHand: 'Inventory amount',
    yes: 'Yes',
    no: 'No',
    createdTime: 'Created at',
    loadFailed: 'Failed to load products',
    loadDetailFailed: 'Failed to load product details',
    confirmTitle: 'Confirm',
    confirmDelete: 'Delete product "{name}"?',
    confirmEnable: 'Enable product "{name}"?',
    deleteSuccess: 'Product deleted',
    deleteFailed: 'Failed to delete product',
    enableSuccess: 'Product enabled',
    enableFailed: 'Failed to enable product',
    updateSuccess: 'Product updated',
    createSuccess: 'Product created',
    updateFailed: 'Failed to update product',
    createFailed: 'Failed to create product',
    exportSuccess: 'Export completed',
    exportFailed: 'Failed to export products',
    exportFilename: 'product-list',
    selectedExportFilename: 'products-selected-{count}',
    batchEnableTitle: 'Enable Selected',
    batchDisableTitle: 'Disable Selected',
    batchEnableConfirm: 'Enable {count} selected items?',
    batchDisableConfirm: 'Disable {count} selected products?',
    batchEnableSuccess: 'Enabled {count} items',
    batchDisableSuccess: 'Disabled {count} items',
    batchEnablePartial: 'Enabled {success} items, failed {failedCount}: {failed}',
    batchDisablePartial: 'Disabled {success} items, failed {failedCount}: {failed}',
    validationEnterCode: 'Enter product code',
    validationCodeLength: 'Length must be between 2 and 50 characters',
    validationEnterName: 'Enter product name',
    validationNameLength: 'Length must be between 2 and 100 characters',
    validationProductType: 'Select a product type',
    validationCategory: 'Enter a category',
    validationUnit: 'Select a unit',
    validationSalePrice: 'Enter a sale price',
    validationCostPrice: 'Enter a cost price',
    validationTaxRate: 'Enter a tax rate',
    unitPiece: 'Piece',
    unitMachine: 'Unit',
    unitItem: 'Item',
    unitBox: 'Box'
  }
} as const
const texts = computed(() => PRODUCT_TEXTS[appStore.locale as keyof typeof PRODUCT_TEXTS])
const displayPreferences = computed(() => ({
  locale: appStore.locale
}))

const {
  activeCount: countActive,
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatUnit,
  interpolate,
  joinNames,
  labelWithCount,
  productTypeOptions,
  unitOptions
} = useProductPresentation(texts)

const {
  batchRunning,
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
  stockSummary,
  tableData,
  total
} = useProductList(texts, {
  getProducts,
  getProduct,
  getStockSummary: getProductStockSummary,
  enableProduct,
  deleteProduct,
  exportProducts,
  confirm: (message, title, opts) => ElMessageBox.confirm(message, title, opts),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message),
  interpolate,
  joinNames: (items, locale) => joinNames(items, locale),
  formatUnit,
  formatCurrency,
  locale: computed(() => appStore.locale)
})

const activeCount = computed(() => countActive(tableData.value))

// 列自定义 + 查询条件记忆（localStorage 持久化）。code/name/操作 为固定列，其余可显隐。
const productColumns = [
  { prop: 'categoryName', labelKey: 'productCategory' },
  { prop: 'specifications', labelKey: 'specification' },
  { prop: 'unit', labelKey: 'unit' },
  { prop: 'auxUnitName', labelKey: 'auxUnit' },
  { prop: 'unitPrice', labelKey: 'salePrice' },
  { prop: 'costPrice', labelKey: 'costPrice' },
  { prop: 'status', labelKey: 'status' }
]
const productColumnOptions = computed(() => ([
  { prop: 'categoryName', label: texts.value.productCategory },
  { prop: 'specifications', label: texts.value.specification },
  { prop: 'unit', label: texts.value.unit },
  { prop: 'auxUnitName', label: texts.value.auxUnit },
  { prop: 'unitPrice', label: texts.value.salePrice },
  { prop: 'costPrice', label: texts.value.costPrice },
  { prop: 'status', label: texts.value.status }
]))
const {
  columnVisible,
  setColumnVisible,
  searchForm: preferredSearchForm
} = useTablePreference('masterdata.products', {
  defaultColumnVisible: Object.fromEntries(productColumns.map((column) => [column.prop, true])),
  defaultSearchForm: searchForm
})
Object.assign(searchForm, preferredSearchForm)

const handleColumnVisibleUpdate = (nextValue: Record<string, boolean>) => {
  for (const [prop, visible] of Object.entries(nextValue)) {
    setColumnVisible(prop, visible)
  }
}

const {
  dialogTitle,
  dialogVisible,
  formData,
  formRules,
  handleCreate,
  handleEdit,
  handleSubmit,
  submitting
} = useProductForm(texts, {
  createProduct,
  updateProduct,
  onSuccess: (message) => ElMessage.success(message),
  onError: (message) => ElMessage.error(message),
  onCompleted: () => loadData()
})

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.product-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.3);
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  border-radius: 50%;
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(-20px, 20px) scale(1.1);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.header-icon {
  font-size: 48px;
  color: #ffffff;
  animation: pulse 2s ease-in-out infinite;
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
  opacity: 0.9;
  font-weight: 400;
}

.header-stats {
  display: flex;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.stat-card {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.stat-card:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-2px);
}

.stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
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
  color: #52c41a;
  text-shadow: 0 0 10px rgba(82, 196, 26, 0.5);
}

.product-table {
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

.code-badge {
  display: inline-block;
  padding: 4px 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #ffffff;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  font-family: 'Courier New', monospace;
}

.price-value {
  font-weight: 600;
  color: #52c41a;
  font-size: 14px;
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

.elegant-dialog :deep(.el-dialog__header) {
  background: linear-gradient(to bottom, #fafbfc, #ffffff);
  padding: 24px 32px;
  border-bottom: 1px solid #e9ecef;
}

.elegant-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.elegant-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.detail-value.price {
  font-size: 18px;
  font-weight: 700;
  color: #52c41a;
}
</style>
