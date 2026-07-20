<template>
  <div class="sales-returns-container">
    <!-- 查询表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="退货单号">
          <el-input
            v-model="queryParams.returnNo"
            placeholder="请输入退货单号"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="销售发货单">
          <el-select
            v-model="queryParams.deliveryId"
            placeholder="请选择销售发货单"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="delivery in deliveries"
              :key="delivery.id"
              :label="deliveryLabel(delivery)"
              :value="delivery.id"
            />
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
            <el-option label="已过账" value="POSTED" />
            <el-option label="已完成" value="COMPLETED" />
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
      <el-button v-permission="'sales:return:create'" type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新增退货
      </el-button>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="returnNo" label="退货单号" width="180" />
        <el-table-column prop="deliveryId" label="销售发货单" width="180">
          <template #default="{ row }">
            {{ deliveryLabelById(row.deliveryId) || row.deliveryId }}
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" width="150" />
        <el-table-column prop="warehouseName" label="退货仓库" width="140" />
        <el-table-column prop="returnDate" label="退货日期" width="120" />
        <el-table-column prop="totalAmount" label="退货金额" width="120" align="right">
          <template #default="{ row }">
            ¥{{ row.totalAmount?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DRAFT'" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 'POSTED' || row.status === 'COMPLETED'" type="success">已过账</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="danger">已取消</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">
              查看
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:return:update'"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:return:post'"
              link
              type="success"
              @click="handlePost(row)"
            >
              过账
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'sales:return:cancel'"
              link
              type="danger"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>

    <!-- 新增/查看对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="85%"
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
            <el-form-item label="销售发货单" prop="deliveryId">
              <el-select
                v-model="formData.deliveryId"
                placeholder="请选择销售发货单"
                style="width: 100%"
                :disabled="isView || !!editingId"
                @change="handleDeliveryChange"
                filterable
              >
                <el-option
                  v-for="delivery in deliveries"
                  :key="delivery.id"
                  :label="deliveryLabel(delivery)"
                  :value="delivery.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户">
              <el-input :model-value="selectedDelivery?.customerName || '-'" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货仓库">
              <el-input :model-value="selectedDelivery?.warehouseName || '-'" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker
                v-model="formData.returnDate"
                type="date"
                placeholder="请选择退货日期"
                style="width: 100%"
                value-format="YYYY-MM-DD"
                :disabled="isView"
              />
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

        <!-- 退货明细 -->
        <el-divider content-position="left">退货明细</el-divider>
        <el-table :data="formData.items" border max-height="400">
          <el-table-column label="产品" prop="productName" width="250" />
          <el-table-column label="产品编码" prop="productCode" width="140" />
          <el-table-column label="产品名称" prop="productName" width="150" />
          <el-table-column label="退货数量" prop="quantity" width="130">
            <template #default="{ row, $index }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
                @change="handleQuantityChange($index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="退货单价" prop="price" width="130">
            <template #default="{ row, $index }">
              <el-input-number
                v-model="row.price"
                :min="0"
                :precision="2"
                :disabled="isView"
                style="width: 100%"
                @change="handleQuantityChange($index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="退货金额" prop="amount" width="130" align="right">
            <template #default="{ row }">
              ¥{{ row.amount?.toFixed(2) || '0.00' }}
            </template>
          </el-table-column>
          <el-table-column label="原因" prop="reason">
            <template #default="{ row }">
              <el-input
                v-model="row.reason"
                placeholder="请输入退货原因"
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

        <!-- 汇总信息 -->
        <div style="margin-top: 20px; text-align: right; font-size: 16px">
          <span style="margin-right: 20px">
            总数量: <strong>{{ totalQuantity }}</strong>
          </span>
          <span>
            总金额: <strong style="color: #f56c6c">¥{{ totalAmount.toFixed(2) }}</strong>
          </span>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="!isView" type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getSalesReturns,
  getSalesReturn,
  createSalesReturn,
  updateSalesReturn,
  postSalesReturn,
  cancelSalesReturn,
  getSalesDeliveries,
  getSalesDelivery,
  type SalesReturnQuery,
  type SalesReturnCreateRequest,
  type SalesReturn,
  type SalesDelivery
} from '@/api/sales'
import { getProducts, type Product } from '@/api/masterdata'

// 查询参数
const queryParams = reactive<SalesReturnQuery>({
  pageNo: 1,
  pageSize: 10,
  returnNo: '',
  deliveryId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const loading = ref(false)
const tableData = ref<SalesReturn[]>([])
const total = ref(0)

// 已过账销售发货单列表
const deliveries = ref<SalesDelivery[]>([])

// 产品列表仅用于补齐发货明细展示字段
const products = ref<Product[]>([])

// 对话框
const dialogVisible = ref(false)
const submitLoading = ref(false)
const dialogTitle = ref('')
const isView = ref(false)
const editingId = ref<string | number>('')
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<SalesReturnCreateRequest>({
  deliveryId: '',
  returnDate: '',
  items: [],
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  deliveryId: [{ required: true, message: '请选择销售发货单', trigger: 'change' }],
  returnDate: [{ required: true, message: '请选择退货日期', trigger: 'change' }]
}

const selectedDelivery = computed(() => {
  return deliveries.value.find(item => String(item.id) === String(formData.deliveryId))
})

// 计算总数量
const totalQuantity = computed(() => {
  return formData.items.reduce((sum, item) => sum + (item.quantity || 0), 0)
})

// 计算总金额
const totalAmount = computed(() => {
  return formData.items.reduce((sum, item) => sum + (item.amount || 0), 0)
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const response = await getSalesReturns(queryParams)
    tableData.value = response.records.map((item) => ({
      ...item,
      deliveryNo: item.deliveryNo || deliveryNoById(item.deliveryId),
      customerName: item.customerName || deliveryCustomerNameById(item.deliveryId),
      warehouseName: item.warehouseName || deliveryWarehouseNameById(item.deliveryId)
    }))
    total.value = response.total
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 加载已过账销售发货单
const loadDeliveries = async () => {
  try {
    const deliveryPageQuery = { pageNo: 1, pageSize: 200, status: 'POSTED' }
    const response = await getSalesDeliveries(deliveryPageQuery)
    deliveries.value = response.records
  } catch (error) {
    ElMessage.error('加载销售发货单失败')
  }
}

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
  queryParams.returnNo = ''
  queryParams.deliveryId = undefined
  queryParams.status = ''
  dateRange.value = null
  queryParams.startDate = ''
  queryParams.endDate = ''
  handleQuery()
}

// 新增
const handleCreate = () => {
  resetForm()
  dialogTitle.value = '新增销售退货'
  dialogVisible.value = true
}

// 查看
const handleView = async (row: SalesReturn) => {
  try {
    const data = await getSalesReturn(row.id)
    dialogTitle.value = '查看销售退货'
    isView.value = true
    editingId.value = ''
    Object.assign(formData, data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

// 编辑草稿
const handleEdit = async (row: SalesReturn) => {
  try {
    const detail = await getSalesReturn(row.id)
    dialogTitle.value = '编辑销售退货'
    isView.value = false
    editingId.value = detail.id
    // 载入所属发货单，供只读展示（草稿不允许改发货单）
    const existing = deliveries.value.find(d => String(d.id) === String(detail.deliveryId))
    if (!existing) {
      deliveries.value = [{
        id: detail.deliveryId,
        deliveryNo: detail.deliveryNo,
        customerName: detail.customerName,
        warehouseName: detail.warehouseName
      } as SalesDelivery, ...deliveries.value]
    }
    formData.deliveryId = detail.deliveryId
    formData.returnDate = detail.returnDate
    formData.remark = detail.remark || ''
    formData.items = (detail.items || detail.lines || []).map(item => ({
      deliveryLineId: item.deliveryLineId,
      orderLineId: item.orderLineId,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      quantity: Number(item.quantity ?? item.qty ?? 0),
      price: Number(item.price ?? 0),
      taxRate: Number(item.taxRate ?? 0),
      amount: Number(item.amount ?? 0),
      taxAmount: Number(item.taxAmount ?? 0),
      reason: item.reason || item.remark || ''
    }))
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载退货单失败')
  }
}

// 取消
const handleCancel = async (row: SalesReturn) => {
  try {
    await ElMessageBox.confirm('确认取消此退货单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelSalesReturn(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 过账
const handlePost = async (row: SalesReturn) => {
  try {
    await ElMessageBox.confirm('确认过账此销售退货单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await postSalesReturn(row.id)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 发货单变化
const handleDeliveryChange = async () => {
  if (!formData.deliveryId) {
    formData.items = []
    return
  }

  try {
    const delivery = await getSalesDelivery(formData.deliveryId)
    formData.items = delivery.items.map(item => ({
      ...productInfoById(item.productId),
      deliveryLineId: item.id,
      orderLineId: item.orderLineId,
      productId: item.productId,
      productCode: item.productCode || productInfoById(item.productId).productCode,
      productName: item.productName || productInfoById(item.productId).productName,
      quantity: item.quantity - (item.returnedQty || 0),
      price: item.price || 0,
      taxRate: item.taxRate || 0,
      amount: (item.quantity - (item.returnedQty || 0)) * (item.price || 0),
      taxAmount: 0,
      reason: ''
    })).filter(item => item.quantity > 0)
  } catch (error) {
    ElMessage.error('加载销售发货详情失败')
  }
}

// 删除明细
const handleDeleteItem = (index: number) => {
  formData.items.splice(index, 1)
}

// 数量/单价变化
const handleQuantityChange = (index: number) => {
  const item = formData.items[index]
  item.amount = (item.quantity || 0) * (item.price || 0)
}

const deliveryLabel = (delivery: SalesDelivery) => {
  return [delivery.deliveryNo, delivery.customerName, delivery.warehouseName].filter(Boolean).join(' - ') || `发货单${delivery.id}`
}

const deliveryById = (deliveryId: string | number | undefined) => {
  return deliveries.value.find(item => String(item.id) === String(deliveryId))
}

const deliveryLabelById = (deliveryId: string | number | undefined) => {
  const delivery = deliveryById(deliveryId)
  return delivery ? deliveryLabel(delivery) : ''
}

const deliveryNoById = (deliveryId: string | number | undefined) => {
  return deliveryById(deliveryId)?.deliveryNo || ''
}

const deliveryCustomerNameById = (deliveryId: string | number | undefined) => {
  return deliveryById(deliveryId)?.customerName || ''
}

const deliveryWarehouseNameById = (deliveryId: string | number | undefined) => {
  return deliveryById(deliveryId)?.warehouseName || ''
}

const productInfoById = (productId: string | number) => {
  const product = products.value.find(item => String(item.id) === String(productId))
  return {
    productCode: product?.code || product?.productCode || '',
    productName: product?.name || product?.productName || ''
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (formData.items.length === 0) {
        ElMessage.warning('请至少添加一条退货明细')
        return
      }

      // 检查退货数量
      const hasQuantity = formData.items.some(item => item.quantity > 0)
      if (!hasQuantity) {
        ElMessage.warning('请输入退货数量')
        return
      }

      submitLoading.value = true
      try {
        if (editingId.value) {
          await updateSalesReturn(editingId.value, formData)
        } else {
          await createSalesReturn(formData)
        }
        ElMessage.success('操作成功')
        dialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(editingId.value ? '更新失败' : '操作失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

// 重置表单（含编辑态，避免取消后下次新建误走 PUT）
const resetForm = () => {
  editingId.value = ''
  isView.value = false
  formData.deliveryId = ''
  formData.returnDate = new Date().toISOString().split('T')[0]
  formData.items = []
  formData.remark = ''
  formRef.value?.clearValidate()
}

onMounted(async () => {
  await Promise.all([
    loadDeliveries(),
    loadProducts()
  ])
  loadData()
})
</script>

<style scoped lang="scss">
.sales-returns-container {
  padding: 20px;

  .search-card,
  .toolbar-card,
  .table-card {
    margin-bottom: 20px;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
