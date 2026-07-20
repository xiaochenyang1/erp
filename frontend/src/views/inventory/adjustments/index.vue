<template>
  <div class="inventory-adjustments-container page-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="调整单号">
          <el-input
            v-model="queryParams.adjustmentNo"
            placeholder="请输入调整单号"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouse.name"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整类型">
          <el-select
            v-model="queryParams.type"
            placeholder="请选择调整类型"
            clearable
            style="width: 150px"
          >
            <el-option label="盘盈" value="GAIN" />
            <el-option label="盘亏" value="LOSS" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 150px"
          >
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已完成" value="POSTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="btn-group">
        <el-button v-permission="'inventory:adjustment:create'" type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          新增调整
        </el-button>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <!-- 骨架屏 -->
      <div v-if="loading && tableData.length === 0" class="skeleton-wrapper">
        <el-skeleton :rows="8" animated />
      </div>

      <!-- 数据表格 -->
      <el-table
        v-else
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="adjustmentNo" label="调整单号" width="180" />
        <el-table-column prop="warehouseName" label="仓库" width="150" />
        <el-table-column prop="adjustmentDate" label="调整日期" width="120" />
        <el-table-column prop="type" label="调整类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'GAIN'" type="success">盘盈</el-tag>
            <el-tag v-else-if="row.type === 'LOSS'" type="danger">盘亏</el-tag>
            <el-tag v-else type="info">其他</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 'COMPLETED'" type="success">已完成</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">已取消</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              查看
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:adjustment:post'"
              link
              type="success"
              @click="handleComplete(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'inventory:adjustment:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && tableData.length === 0"
        description="暂无数据"
        :image-size="120"
      />

      <!-- 分页 -->
      <el-pagination
        v-if="tableData.length > 0"
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="80%"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select
                v-model="formData.warehouseId"
                placeholder="请选择仓库"
                style="width: 100%"
                :disabled="isView"
              >
                <el-option
                  v-for="warehouse in warehouses"
                  :key="warehouse.id"
                  :label="warehouse.name"
                  :value="warehouse.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调整日期" prop="adjustmentDate">
              <el-date-picker
                v-model="formData.adjustmentDate"
                type="date"
                placeholder="请选择调整日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调整类型" prop="type">
              <el-select
                v-model="formData.type"
                placeholder="请选择调整类型"
                style="width: 100%"
                :disabled="isView"
              >
                <el-option label="盘盈" value="GAIN" />
                <el-option label="盘亏" value="LOSS" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
            :disabled="isView"
          />
        </el-form-item>

        <!-- 调整明细 -->
        <el-divider content-position="left">调整明细</el-divider>
        <el-button
          v-if="!isView"
          type="primary"
          size="small"
          @click="handleAddItem"
          class="mb-sm"
        >
          <el-icon><Plus /></el-icon>
          添加产品
        </el-button>
        <el-table :data="formData.items" border>
          <el-table-column label="产品" prop="productId" width="250">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.productId"
                placeholder="请选择产品"
                filterable
                style="width: 100%"
                :disabled="isView"
                @change="handleProductChange($index)"
              >
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :label="productLabel(product)"
                  :value="product.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="产品编码" prop="productCode" width="150" />
          <el-table-column label="产品名称" prop="productName" width="150" />
          <el-table-column label="调整数量" prop="quantity" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="原因" prop="reason">
            <template #default="{ row }">
              <el-input
                v-model="row.reason"
                placeholder="请输入原因"
                :disabled="isView"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="!isView" label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                @click="handleDeleteItem($index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <div class="btn-group">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getInventoryAdjustments,
  getInventoryAdjustment,
  createInventoryAdjustment,
  completeInventoryAdjustment,
  cancelInventoryAdjustment,
  type InventoryAdjustmentQuery,
  type InventoryAdjustmentCreateRequest,
  type InventoryAdjustment
} from '@/api/inventory'
import { getWarehouses, type Warehouse } from '@/api/masterdata'
import { getProducts, type Product } from '@/api/masterdata'

// 查询参数
const queryParams = reactive<InventoryAdjustmentQuery>({
  pageNo: 1,
  pageSize: 10,
  adjustmentNo: '',
  warehouseId: undefined,
  type: '',
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<InventoryAdjustment[]>([])
const total = ref(0)

// 仓库列表
const warehouses = ref<Warehouse[]>([])

// 产品列表
const products = ref<Product[]>([])

// 对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isView = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<InventoryAdjustmentCreateRequest>({
  warehouseId: 0,
  adjustmentDate: '',
  type: 'GAIN',
  items: [],
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  adjustmentDate: [{ required: true, message: '请选择调整日期', trigger: 'change' }],
  type: [{ required: true, message: '请选择调整类型', trigger: 'change' }]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getInventoryAdjustments(queryParams)
    tableData.value = response.records
    total.value = response.total
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const response = await getWarehouses(optionPageQuery)
    warehouses.value = response.records
  } catch (error) {
    ElMessage.error('加载仓库列表失败')
  }
}

// 加载产品列表
const loadProducts = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const response = await getProducts(optionPageQuery)
    products.value = response.records
  } catch (error) {
    ElMessage.error('加载产品列表失败')
  }
}

// 查询
const handleQuery = () => {
  if (dateRange.value) {
    queryParams.startDate = dateRange.value[0]
    queryParams.endDate = dateRange.value[1]
  } else {
    queryParams.startDate = ''
    queryParams.endDate = ''
  }
  queryParams.pageNo = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryParams.adjustmentNo = ''
  queryParams.warehouseId = undefined
  queryParams.type = ''
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  dialogTitle.value = '新增库存调整'
  isView.value = false
  resetForm()
  dialogVisible.value = true
}

// 查看
const handleView = async (row: InventoryAdjustment) => {
  dialogTitle.value = '查看库存调整'
  isView.value = true
  try {
    const data = await getInventoryAdjustment(row.id)
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 过账
const handleComplete = async (row: InventoryAdjustment) => {
  try {
    await ElMessageBox.confirm('确认过账此库存调整吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await completeInventoryAdjustment(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 取消
const handleCancel = async (row: InventoryAdjustment) => {
  try {
    await ElMessageBox.confirm('确认取消此库存调整吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelInventoryAdjustment(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 添加明细
const handleAddItem = () => {
  formData.items.push({
    productId: 0,
    productCode: '',
    productName: '',
    quantity: 0,
    unitCost: 0,
    reason: ''
  })
}

// 删除明细
const handleDeleteItem = (index: number) => {
  formData.items.splice(index, 1)
}

// 产品变化
const handleProductChange = (index: number) => {
  const item = formData.items[index]
  const product = products.value.find(p => String(p.id) === String(item.productId))
  if (product) {
    item.productCode = product.code || product.productCode || ''
    item.productName = product.name || product.productName || ''
    item.unitCost = product.purchasePrice ?? item.unitCost ?? 0
  }
}

const productLabel = (product: Product) => {
  const code = product.code || product.productCode || ''
  const name = product.name || product.productName || ''
  return code && name ? `${code} - ${name}` : name || code || `产品${product.id}`
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.items.length === 0) {
        ElMessage.warning('请至少添加一条调整明细')
        return
      }

      submitLoading.value = true
      try {
        await createInventoryAdjustment(formData)
        ElMessage.success('操作成功')
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error('操作失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单
const resetForm = () => {
  formData.warehouseId = 0
  formData.adjustmentDate = new Date().toISOString().split('T')[0]
  formData.type = 'GAIN'
  formData.items = []
  formData.remark = ''
  formRef.value?.clearValidate()
}

onMounted(() => {
  loadData()
  loadWarehouses()
  loadProducts()
})
</script>
