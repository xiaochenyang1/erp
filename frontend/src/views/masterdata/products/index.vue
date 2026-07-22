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
            <h1 class="page-title">产品管理</h1>
            <p class="page-subtitle">管理系统中的所有产品信息</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">总产品数</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">启用中</span>
            <span class="stat-value active">{{ activeCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="searchForm" @search="handleSearch" @reset="handleReset">
      <el-form-item label="产品编码" prop="code">
        <el-input
          v-model="searchForm.code"
          placeholder="请输入产品编码"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="产品名称" prop="name">
        <el-input
          v-model="searchForm.name"
          placeholder="请输入产品名称"
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
      :show-export="false"
      :data="tableData"
      :loading="loading"
      :total="total"
      :page="searchForm.pageNo"
      :page-size="searchForm.pageSize"
      @create="handleCreate"
      @export="handleExport"
      @refresh="loadData"
      @page-change="handlePageChange"
      @selection-change="handleSelectionChange"
      class="product-table"
    >
      <template #toolbar-left>
        <el-button v-permission="'masterdata:product:create'" type="primary" :icon="Plus" @click="handleCreate">
          新增产品
        </el-button>
        <el-button
          v-permission="'masterdata:product:enable'"
          :disabled="selectedRows.length === 0 || batchRunning"
          :loading="batchRunning"
          :icon="CircleCheck"
          @click="handleBatchEnable"
        >
          批量启用{{ selectedRows.length ? `(${selectedRows.length})` : '' }}
        </el-button>
        <el-button
          v-permission="'masterdata:product:disable'"
          :disabled="selectedRows.length === 0 || batchRunning"
          :loading="batchRunning"
          :icon="Delete"
          @click="handleBatchDisable"
        >
          批量停用{{ selectedRows.length ? `(${selectedRows.length})` : '' }}
        </el-button>
        <el-button
          :disabled="selectedRows.length === 0"
          :icon="Download"
          @click="handleExportSelected"
        >
          导出选中{{ selectedRows.length ? `(${selectedRows.length})` : '' }}
        </el-button>
      </template>
      <template #toolbar-right>
        <el-button :icon="Download" @click="handleExport">导出</el-button>
        <table-column-setting
          :columns="productColumns"
          :model-value="columnVisible"
          @update:model-value="handleColumnVisibleUpdate"
          @reset="resetColumns"
        />
        <el-button :icon="Refresh" circle title="刷新" @click="loadData" />
      </template>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="code" label="产品编码" width="140" fixed>
        <template #default="{ row }">
          <span class="code-badge">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="产品名称" min-width="180" show-overflow-tooltip />
      <el-table-column v-if="isColumnVisible('categoryName')" prop="categoryName" label="产品分类" width="140" />
      <el-table-column v-if="isColumnVisible('specifications')" prop="specifications" label="规格型号" width="140" show-overflow-tooltip />
      <el-table-column v-if="isColumnVisible('unit')" prop="unit" label="单位" width="80" align="center" />
      <el-table-column v-if="isColumnVisible('unitPrice')" prop="unitPrice" label="销售单价" width="120" align="right">
        <template #default="{ row }">
          <span class="price-value">¥{{ row.unitPrice?.toFixed(2) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('costPrice')" prop="costPrice" label="成本单价" width="120" align="right">
        <template #default="{ row }">
          <span class="price-value">¥{{ row.costPrice?.toFixed(2) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('status')" prop="status" label="状态" width="100" align="center">
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
            <el-button v-permission="'masterdata:product:update'" link type="primary" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              v-permission="'masterdata:product:enable'"
              link
              type="success"
              @click="handleEnable(row)"
            >
              <el-icon><CircleCheck /></el-icon>
              启用
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              v-permission="'masterdata:product:disable'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
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
        <el-form-item label="产品编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入产品编码" maxlength="50" />
        </el-form-item>
        <el-form-item label="产品名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入产品名称" maxlength="100" />
        </el-form-item>
        <el-form-item v-if="!formData.id" label="商品类型" prop="productType">
          <el-select v-model="formData.productType" placeholder="请选择商品类型">
            <el-option label="实物商品" value="PHYSICAL" />
            <el-option label="库存商品" value="GOODS" />
            <el-option label="服务" value="SERVICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品分类" prop="categoryName">
          <el-input v-model="formData.categoryName" placeholder="请输入产品分类" maxlength="100" />
        </el-form-item>
        <el-form-item label="规格型号" prop="specifications">
          <el-input v-model="formData.specifications" placeholder="请输入规格型号" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-select v-model="formData.unit" placeholder="请选择单位" clearable>
            <el-option label="个" value="个" />
            <el-option label="台" value="台" />
            <el-option label="件" value="件" />
            <el-option label="箱" value="箱" />
            <el-option label="kg" value="kg" />
            <el-option label="m" value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售单价" prop="unitPrice">
          <el-input-number
            v-model="formData.unitPrice"
            :min="0"
            :precision="2"
            :controls="false"
            placeholder="请输入销售单价"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="成本单价" prop="costPrice">
          <el-input-number
            v-model="formData.costPrice"
            :min="0"
            :precision="2"
            :controls="false"
            placeholder="请输入成本单价"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="税率（%）" prop="taxRate">
          <el-input-number
            v-model="formData.taxRate"
            :min="0"
            :max="100"
            :precision="2"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="条形码" prop="barcode">
          <el-input v-model="formData.barcode" placeholder="请输入条形码" />
        </el-form-item>
        <el-form-item label="状态" prop="status" :style="{ gridColumn: '1 / -1' }">
          <el-radio-group v-model="formData.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="来料需检验" prop="inspectionRequired" :style="{ gridColumn: '1 / -1' }">
          <el-switch v-model="formData.inspectionRequired" />
          <span class="form-tip">开启后，该商品的采购入库单在过账前必须先完成来料检验</span>
        </el-form-item>
        <el-form-item label="批次管理" prop="lotControlled">
          <el-switch v-model="formData.lotControlled" />
        </el-form-item>
        <el-form-item label="保质期管理" prop="shelfLifeControlled">
          <el-switch v-model="formData.shelfLifeControlled" :disabled="!formData.lotControlled" />
          <span class="form-tip">保质期管理必须同时开启批次管理</span>
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
      title="产品详情"
      width="700px"
      class="elegant-dialog"
    >
      <detail-card>
        <div class="detail-section">
          <div class="section-title">基本信息</div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">产品编码</div>
              <div class="detail-value">{{ currentRow?.code }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">产品名称</div>
              <div class="detail-value">{{ currentRow?.name }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">产品分类</div>
              <div class="detail-value">{{ currentRow?.categoryName || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">规格型号</div>
              <div class="detail-value">{{ currentRow?.specifications || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">单位</div>
              <div class="detail-value">{{ currentRow?.unit || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">条形码</div>
              <div class="detail-value">{{ currentRow?.barcode || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">价格信息</div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">销售单价</div>
              <div class="detail-value price">¥{{ currentRow?.unitPrice?.toFixed(2) || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">成本单价</div>
              <div class="detail-value price">¥{{ currentRow?.costPrice?.toFixed(2) || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">其他信息</div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <status-tag v-if="currentRow" :status="currentRow.status" />
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">来料需检验</div>
              <div class="detail-value">{{ currentRow?.inspectionRequired ? '是' : '否' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">创建时间</div>
              <div class="detail-value">{{ currentRow?.createdTime || '-' }}</div>
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
import { Box, View, Edit, Delete, CircleCheck, Download, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getProducts,
  getProduct,
  createProduct,
  updateProduct,
  deleteProduct,
  enableProduct,
  exportProducts,
  type Product,
  type ProductQuery,
  type ProductSaveRequest
} from '@/api/masterdata'
import { PageTable, PageForm, SearchBar, StatusTag, DetailCard, TableColumnSetting } from '@/components/common'
import { downloadBlob } from '@/utils/download'
import { useTablePreference } from '@/composables/useTablePreference'

// 列自定义 + 查询条件记忆（localStorage 持久化）。code/name/操作 为固定列，其余可显隐。
const productColumns = [
  { prop: 'categoryName', label: '产品分类' },
  { prop: 'specifications', label: '规格型号' },
  { prop: 'unit', label: '单位' },
  { prop: 'unitPrice', label: '销售单价' },
  { prop: 'costPrice', label: '成本单价' },
  { prop: 'status', label: '状态' }
]

const {
  searchForm,
  columnVisible,
  isColumnVisible,
  setColumnVisible,
  resetColumns
} = useTablePreference<ProductQuery>('masterdata:products', {
  defaultSearchForm: {
    pageNo: 1,
    pageSize: 20,
    code: '',
    name: '',
    status: ''
  },
  persistentSearchKeys: ['code', 'name', 'status', 'pageSize'],
  columns: productColumns
})

const handleColumnVisibleUpdate = (nextValue: Record<string, boolean>) => {
  for (const [prop, visible] of Object.entries(nextValue)) {
    setColumnVisible(prop, visible)
  }
}

// 表格数据
const tableData = ref<Product[]>([])
const total = ref(0)
const loading = ref(false)
const selectedRows = ref<Product[]>([])
const handleSelectionChange = (rows: Product[]) => {
  selectedRows.value = rows
}

type BatchActionOptions<T> = {
  actionLabel: string
  itemLabel: (item: T) => string
  confirmText?: (count: number) => string
  onDone?: () => void | Promise<void>
}

const useBatchAction = <T,>() => {
  const running = ref(false)

  const run = async (
    rows: T[],
    action: (row: T) => Promise<unknown>,
    options: BatchActionOptions<T>
  ) => {
    if (rows.length === 0 || running.value) return
    const message = options.confirmText?.(rows.length) ?? `确认${options.actionLabel}选中的 ${rows.length} 条数据吗？`
    await ElMessageBox.confirm(message, `批量${options.actionLabel}`, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    running.value = true
    let success = 0
    const failed: string[] = []
    try {
      for (const row of rows) {
        try {
          await action(row)
          success += 1
        } catch {
          failed.push(options.itemLabel(row))
        }
      }
      if (failed.length === 0) {
        ElMessage.success(`已${options.actionLabel} ${success} 条`)
      } else {
        ElMessage.warning(`已${options.actionLabel} ${success} 条，失败 ${failed.length} 条：${failed.join('、')}`)
      }
      await options.onDone?.()
    } finally {
      running.value = false
    }
  }

  return { running, run }
}

const exportSelectedRowsToCsv = (
  filename: string,
  headers: string[],
  rows: Array<Array<string | number>>
) => {
  const escapeCell = (value: string | number) => `"${String(value ?? '').replace(/"/g, '""')}"`
  const csv = [headers, ...rows].map((row) => row.map(escapeCell).join(',')).join('\r\n')
  downloadBlob(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }), `${filename}.csv`)
}

// 批量启用/停用：复用单条 enable/disable 端点循环执行，汇总成功/失败（后端暂无批量端点）
const { running: batchRunning, run: runBatch } = useBatchAction<Product>()

const handleBatchEnable = () => {
  runBatch(selectedRows.value, (row) => enableProduct(row.id), {
    actionLabel: '启用',
    itemLabel: (row) => row.name,
    onDone: loadData
  })
}

const handleBatchDisable = () => {
  runBatch(selectedRows.value, (row) => deleteProduct(row.id), {
    actionLabel: '停用',
    itemLabel: (row) => row.name,
    confirmText: (count) => `确认停用选中的 ${count} 个产品吗？`,
    onDone: loadData
  })
}

const handleExportSelected = () => {
  const rows = selectedRows.value
  if (rows.length === 0) return
  const headers = ['产品编码', '产品名称', '产品分类', '规格型号', '单位', '销售单价', '成本单价', '状态']
  const lines = rows.map((row) => [
    row.code,
    row.name,
    row.categoryName ?? '',
    row.specifications ?? '',
    row.unit ?? '',
    row.unitPrice ?? '',
    row.costPrice ?? '',
    row.status === 'ACTIVE' ? '启用' : '停用'
  ])
  exportSelectedRowsToCsv(`产品_选中${rows.length}条`, headers, lines)
}
const activeCount = computed(() => {
  if (!tableData.value || !Array.isArray(tableData.value)) return 0
  return tableData.value.filter(item => item.status === 'ACTIVE').length
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = computed(() => (formData.id ? '编辑产品' : '新增产品'))
const submitting = ref(false)
const detailVisible = ref(false)
const currentRow = ref<Product>()

// 表单数据
const formData = reactive<ProductSaveRequest & { id?: string }>({
  code: '',
  name: '',
  productType: 'PHYSICAL',
  categoryName: '',
  specifications: '',
  unit: '',
  unitPrice: undefined,
  costPrice: undefined,
  taxRate: 13,
  barcode: '',
  status: 'ACTIVE',
  inspectionRequired: false,
  lotControlled: false,
  shelfLifeControlled: false,
  remark: ''
})

// 表单验证规则
const formRules = {
  code: [
    { required: true, message: '请输入产品编码', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入产品名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  productType: [{ required: true, message: '请选择商品类型', trigger: 'change' }],
  categoryName: [{ required: true, message: '请输入产品分类', trigger: 'blur' }],
  unit: [{ required: true, message: '请选择单位', trigger: 'change' }],
  unitPrice: [{ required: true, message: '请输入销售单价', trigger: 'blur' }],
  costPrice: [{ required: true, message: '请输入成本单价', trigger: 'blur' }],
  taxRate: [{ required: true, message: '请输入税率', trigger: 'blur' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getProducts(searchForm)
    // 适配后端返回的数据结构
    const products = res.records.map(item => ({
      ...item,
      code: item.productCode,           // 产品编码
      name: item.productName,           // 产品名称
      specifications: item.specification, // 规格型号（注意单复数）
      unit: item.unitName,              // 单位
      unitPrice: item.salePrice,        // 销售单价
      costPrice: item.purchasePrice     // 成本单价（采购价）
    }))

    tableData.value = products
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
    productType: 'PHYSICAL',
    categoryName: '',
    specifications: '',
    unit: '',
    unitPrice: undefined,
    costPrice: undefined,
    taxRate: 13,
    barcode: '',
    status: 'ACTIVE',
    inspectionRequired: false,
    lotControlled: false,
    shelfLifeControlled: false,
    remark: ''
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: Product) => {
  Object.assign(formData, {
    id: row.id,
    code: row.productCode || row.code,
    name: row.productName || row.name,
    productType: row.productType,
    categoryName: row.categoryName,
    specifications: row.specification || row.specifications,  // 注意字段名
    unit: row.unitName || row.unit,
    unitPrice: row.salePrice || row.unitPrice,
    costPrice: row.purchasePrice || row.costPrice,
    taxRate: row.taxRate ?? 13,
    barcode: row.barcode || '',
    status: row.status,
    inspectionRequired: row.inspectionRequired ?? false,
    lotControlled: row.lotControlled ?? false,
    shelfLifeControlled: row.shelfLifeControlled ?? false,
    remark: row.remark
  })
  dialogVisible.value = true
}

// 查看
const handleView = async (row: Product) => {
  try {
    currentRow.value = await getProduct(row.id)
    detailVisible.value = true
  } catch {
    ElMessage.error('加载产品详情失败')
  }
}

// 删除
const handleDelete = async (row: Product) => {
  try {
    await ElMessageBox.confirm(
      `确认删除产品"${row.name}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteProduct(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleEnable = async (row: Product) => {
  try {
    await ElMessageBox.confirm(`确认启用产品"${row.name}"吗？`, '提示', { type: 'warning' })
    await enableProduct(row.id)
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
    if (formData.id) {
      await updateProduct(formData.id, values)
      ElMessage.success('更新成功')
    } else {
      await createProduct(values)
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
    const blob = await exportProducts(searchForm)
    downloadBlob(blob, `产品列表_${Date.now()}.csv`)
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

.pageNo-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: 0.5px;
}

.pageNo-subtitle {
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
