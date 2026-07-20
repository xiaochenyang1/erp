<template>
  <div class="purchase-return-management">
    <!-- 页面标题 - 使用红橙色主题 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <div class="icon-alert">
            <el-icon class="header-icon">
              <WarningFilled />
            </el-icon>
            <div class="icon-alert-pulse"></div>
          </div>
          <div class="header-text">
            <h1 class="page-title">采购退货管理</h1>
            <p class="page-subtitle">处理采购退货，确保质量问题及时反馈</p>
          </div>
        </div>
        <div class="header-stats">
          <div class="stat-card">
            <span class="stat-label">退货总数</span>
            <span class="stat-value">{{ total }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">待过账</span>
            <span class="stat-value draft">{{ draftCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">已过账</span>
            <span class="stat-value completed">{{ completedCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索栏 -->
    <search-bar v-model="queryForm" @search="handleQuery" @reset="handleReset">
      <el-form-item label="退货单号" prop="returnNo">
        <el-input
          v-model="queryForm.returnNo"
          placeholder="请输入退货单号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="采购收货单" prop="receiptId">
        <el-input
          v-model="queryForm.receiptId"
          placeholder="请输入收货ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryForm.status" placeholder="请选择状态" clearable>
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已过账" value="POSTED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="退货日期" prop="dateRange">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="handleDateChange"
        />
      </el-form-item>
    </search-bar>

    <!-- 数据表格 -->
    <page-table
      :data="tableData"
      :loading="loading"
      :total="total"
      :page="queryForm.pageNo"
      :page-size="queryForm.pageSize"
      create-text="新增退货"
      :show-create="canCreate"
      @create="handleAdd"
      @export="handleExport"
      @refresh="handleQuery"
      @page-change="handlePageChange"
      class="return-table"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="returnNo" label="退货单号" width="160" fixed>
        <template #default="{ row }">
          <span class="return-no">{{ row.returnNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="receiptNo" label="采购收货单号" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="viewReceipt(row.receiptId)">{{ row.receiptNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="来源订单号" width="160" show-overflow-tooltip />
      <el-table-column prop="warehouseName" label="退货仓库" width="140" />
      <el-table-column prop="returnDate" label="退货日期" width="120" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <status-tag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button link type="primary" size="small" @click="handleView(row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:return:update'" link type="primary" size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:return:post'" link type="success" size="small" @click="handleComplete(row)">
              <el-icon><CircleCheck /></el-icon>
              过账
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase:return:cancel'" link type="danger" size="small" @click="handleCancel(row)">
              <el-icon><CircleClose /></el-icon>
              取消
            </el-button>
          </div>
        </template>
      </el-table-column>
    </page-table>

    <!-- 新增/编辑退货对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑采购退货' : '新增采购退货'"
      width="1000px"
      :close-on-click-modal="false"
      class="elegant-dialog return-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <div class="form-section">
          <div class="section-title">基本信息</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="采购收货单" prop="receiptId">
                <el-select
                  v-model="form.receiptId"
                  placeholder="请选择采购收货单"
                  style="width: 100%"
                  :disabled="!!editingId"
                  @change="handleReceiptChange"
                >
                  <el-option
                    v-for="receipt in availableReceipts"
                    :key="receipt.id"
                    :label="`${receipt.receiptNo} - ${receipt.orderNo}`"
                    :value="receipt.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="退货日期" prop="returnDate">
                <el-date-picker
                  v-model="form.returnDate"
                  type="date"
                  placeholder="选择日期"
                  style="width: 100%"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="退货仓库">
                <el-input :model-value="selectedReceipt?.warehouseName || ''" disabled />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="form-section" v-if="form.items.length > 0">
          <div class="section-title">退货明细</div>
          <el-table :data="form.items" border class="items-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column label="商品名称" prop="productName" min-width="180" />
            <el-table-column label="收货数量" prop="receiptQty" width="100" align="center" />
            <el-table-column label="可退数量" prop="availableReturnQty" width="100" align="center" />
            <el-table-column label="实际退货" width="140">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantity"
                  :min="0"
                  :max="row.availableReturnQty"
                  :controls="false"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="备注">
              <template #default="{ row }">
                <el-input v-model="row.remark" placeholder="选填" />
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息（选填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitForm">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情查看对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="退货单详情"
      width="850px"
      class="elegant-dialog return-dialog"
    >
      <detail-card v-if="currentRow" v-loading="detailLoading">
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Document /></el-icon>
            退货信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">退货单号</div>
              <div class="detail-value">{{ currentRow.returnNo }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">采购收货单</div>
              <div class="detail-value">
                <el-link type="primary" @click="viewReceipt(currentRow.receiptId)">{{ currentRow.receiptNo }}</el-link>
              </div>
            </div>
            <div class="detail-item">
              <div class="detail-label">来源订单</div>
              <div class="detail-value">{{ currentRow.orderNo || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">退货仓库</div>
              <div class="detail-value">{{ currentRow.warehouseName }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">退货日期</div>
              <div class="detail-value">{{ currentRow.returnDate }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <status-tag :status="currentRow.status" />
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><List /></el-icon>
            退货明细
          </div>
          <el-table :data="currentRow.items" border class="detail-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column prop="receiptQty" label="收货数量" width="100" align="center" />
            <el-table-column prop="quantity" label="退货数量" width="100" align="center" />
            <el-table-column prop="remark" label="备注" min-width="120" />
          </el-table>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            其他信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">创建人</div>
              <div class="detail-value">{{ currentRow.createdBy }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">创建时间</div>
              <div class="detail-value">{{ currentRow.createdAt }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">备注</div>
              <div class="detail-value">{{ currentRow.remark || '-' }}</div>
            </div>
          </div>
        </div>
      </detail-card>
    </el-dialog>

    <!-- 关联采购收货单详情 -->
    <el-dialog
      v-model="linkedReceiptVisible"
      title="采购收货单详情"
      width="900px"
      class="elegant-dialog return-dialog"
    >
      <detail-card v-if="linkedReceipt" v-loading="linkedReceiptLoading">
        <div class="detail-section">
          <div class="section-title">
            <el-icon><Document /></el-icon>
            收货信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">收货单号</div>
              <div class="detail-value">{{ linkedReceipt.receiptNo }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">采购订单</div>
              <div class="detail-value">{{ linkedReceipt.orderNo }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">供应商</div>
              <div class="detail-value">{{ linkedReceipt.supplierName || '-' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">入库仓库</div>
              <div class="detail-value">{{ linkedReceipt.warehouseName }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">收货日期</div>
              <div class="detail-value">{{ linkedReceipt.receiptDate }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">状态</div>
              <div class="detail-value">
                <status-tag :status="linkedReceipt.status" />
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><List /></el-icon>
            收货明细
          </div>
          <el-table :data="linkedReceipt.items" border class="detail-table">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column prop="orderedQuantity" label="订单数量" width="100" align="center" />
            <el-table-column prop="quantity" label="收货数量" width="100" align="center" />
            <el-table-column prop="returnedQty" label="已退数量" width="100" align="center" />
            <el-table-column prop="remark" label="备注" min-width="120" />
          </el-table>
        </div>

        <div class="detail-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            其他信息
          </div>
          <div class="detail-row">
            <div class="detail-item">
              <div class="detail-label">创建人</div>
              <div class="detail-value">{{ linkedReceipt.createdBy }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">创建时间</div>
              <div class="detail-value">{{ linkedReceipt.createdAt }}</div>
            </div>
            <div class="detail-item" style="grid-column: 1 / -1">
              <div class="detail-label">备注</div>
              <div class="detail-value">{{ linkedReceipt.remark || '-' }}</div>
            </div>
          </div>
        </div>
      </detail-card>
      <div v-else v-loading="linkedReceiptLoading" class="linked-detail-loading">
        加载采购收货单详情...
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  View,
  Edit,
  CircleCheck,
  CircleClose,
  Document,
  List,
  Clock
} from '@element-plus/icons-vue'
import {
  getPurchaseReturns,
  getPurchaseReturn,
  createPurchaseReturn,
  updatePurchaseReturn,
  postPurchaseReturn,
  cancelPurchaseReturn,
  exportPurchaseReturns,
  getPurchaseReceipts,
  getPurchaseReceipt,
  type PurchaseReturn,
  type PurchaseReturnQuery,
  type PurchaseReturnCreateRequest,
  type PurchaseReceipt
} from '@/api/purchase'
import { getProducts, type Product } from '@/api/masterdata'
import { PageTable, SearchBar, StatusTag, DetailCard } from '@/components/common'
import { downloadBlob } from '@/utils/download'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const canCreate = computed(() => userStore.hasPermission('purchase:return:create'))

// 查询表单
const queryForm = reactive<PurchaseReturnQuery>({
  pageNo: 1,
  pageSize: 20,
  returnNo: '',
  receiptId: undefined,
  status: '',
  startDate: '',
  endDate: ''
})

// 日期范围
const dateRange = ref<[string, string]>()

// 表格数据
const tableData = ref<PurchaseReturn[]>([])
const total = ref(0)
const loading = ref(false)
const draftCount = computed(() => tableData.value.filter(item => item.status === 'DRAFT').length)
const completedCount = computed(() => tableData.value.filter(item => item.status === 'POSTED' || item.status === 'COMPLETED').length)

// 对话框
const dialogVisible = ref(false)
const editingId = ref<string | number>('')
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentRow = ref<PurchaseReturn>()
const linkedReceiptVisible = ref(false)
const linkedReceiptLoading = ref(false)
const linkedReceipt = ref<PurchaseReceipt>()

// 可用收货单列表
const availableReceipts = ref<PurchaseReceipt[]>([])
const selectedReceipt = ref<PurchaseReceipt>()
const products = ref<Product[]>([])

// 表单数据
const form = reactive<PurchaseReturnCreateRequest>({
  receiptId: '',
  returnDate: '',
  items: [],
  remark: ''
})

// 表单验证规则
const formRules: FormRules = {
  receiptId: [{ required: true, message: '请选择采购收货单', trigger: 'change' }],
  returnDate: [{ required: true, message: '请选择退货日期', trigger: 'change' }]
}

// 查询数据
const handleQuery = async () => {
  loading.value = true
  try {
    const res = await getPurchaseReturns(queryForm)
    tableData.value = res.records
    total.value = res.total
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 重置查询
const handleReset = () => {
  queryForm.returnNo = ''
  queryForm.receiptId = undefined
  queryForm.status = ''
  queryForm.startDate = ''
  queryForm.endDate = ''
  queryForm.pageNo = 1
  dateRange.value = undefined
  handleQuery()
}

// 日期范围变化
const handleDateChange = (dates: [string, string] | null) => {
  if (dates) {
    queryForm.startDate = dates[0]
    queryForm.endDate = dates[1]
  } else {
    queryForm.startDate = ''
    queryForm.endDate = ''
  }
}

// 分页
const handlePageChange = (page: number, size: number) => {
  queryForm.pageNo = page
  queryForm.pageSize = size
  handleQuery()
}

// 新增
const handleAdd = async () => {
  // 加载已过账的采购收货单
  try {
    const receiptPageQuery = { pageNo: 1, pageSize: 200, status: 'POSTED' }
    const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
    const [res, productResponse] = await Promise.all([
      getPurchaseReceipts(receiptPageQuery),
      getProducts(optionPageQuery)
    ])
    availableReceipts.value = res.records
    products.value = productResponse.records

    if (availableReceipts.value.length === 0) {
      ElMessage.warning('暂无可退货的采购收货单')
      return
    }

    resetForm()
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载订单失败')
  }
}

// 收货单变更
const handleReceiptChange = async () => {
  const summary = availableReceipts.value.find(receipt => String(receipt.id) === String(form.receiptId))
  const receipt = summary?.items?.length ? summary : await getPurchaseReceipt(form.receiptId)
  if (receipt) {
    selectedReceipt.value = receipt
    form.items = receipt.items.map(item => ({
      ...productInfoById(item.productId),
      receiptLineId: item.id,
      orderLineId: item.orderLineId || item.orderItemId,
      productId: item.productId,
      productCode: item.productCode || productInfoById(item.productId).productCode,
      productName: item.productName || productInfoById(item.productId).productName,
      receiptQty: item.quantity,
      returnedQty: item.returnedQty || 0,
      availableReturnQty: item.availableReturnQty ?? item.quantity - (item.returnedQty || 0),
      quantity: item.quantity - (item.returnedQty || 0),
      price: item.price || 0,
      taxRate: item.taxRate || 0,
      amount: item.amount || 0,
      taxAmount: item.taxAmount || 0,
      remark: ''
    }))
  }
}

const productInfoById = (productId: string | number) => {
  const product = products.value.find(item => String(item.id) === String(productId))
  return {
    productCode: product?.code || product?.productCode || '',
    productName: product?.name || product?.productName || ''
  }
}

// 编辑草稿
const handleEdit = async (row: PurchaseReturn) => {
  try {
    const detail = await getPurchaseReturn(row.id)
    editingId.value = detail.id
    // 载入所属收货单，供只读展示（草稿不允许改收货单）
    const receipt = await getPurchaseReceipt(detail.receiptId)
    availableReceipts.value = [receipt]
    selectedReceipt.value = receipt
    form.receiptId = detail.receiptId
    form.returnDate = detail.returnDate
    form.remark = detail.remark || ''
    form.items = (detail.items || detail.lines || []).map(item => ({
      receiptLineId: item.receiptLineId,
      orderLineId: item.orderLineId,
      productId: item.productId,
      productCode: item.productCode,
      productName: item.productName,
      receiptQty: item.receiptQty,
      returnedQty: item.returnedQty || 0,
      // 可退数量需把本草稿已占用的数量加回，否则编辑时上限会偏小
      availableReturnQty: (item.availableReturnQty ?? 0) + Number(item.quantity ?? item.qty ?? 0),
      quantity: Number(item.quantity ?? item.qty ?? 0),
      price: item.price || 0,
      taxRate: item.taxRate || 0,
      amount: item.amount || 0,
      taxAmount: item.taxAmount || 0,
      lotNo: item.lotNo,
      productionDate: item.productionDate,
      expiryDate: item.expiryDate,
      remark: item.reason || item.remark || ''
    }))
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载退货单失败')
  }
}

// 查看
const handleView = async (row: PurchaseReturn) => {
  detailVisible.value = true
  currentRow.value = undefined
  detailLoading.value = true
  try {
    currentRow.value = await getPurchaseReturn(row.id)
  } catch (error) {
    ElMessage.error('加载采购退货详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// 查看收货单
const viewReceipt = async (receiptId: string | number) => {
  if (!receiptId) {
    ElMessage.warning('缺少采购收货单ID')
    return
  }

  linkedReceiptVisible.value = true
  linkedReceipt.value = undefined
  linkedReceiptLoading.value = true
  try {
    linkedReceipt.value = await getPurchaseReceipt(receiptId)
  } catch (error) {
    ElMessage.error('加载采购收货单详情失败')
    linkedReceiptVisible.value = false
  } finally {
    linkedReceiptLoading.value = false
  }
}

// 过账退货
const handleComplete = async (row: PurchaseReturn) => {
  try {
    await ElMessageBox.confirm(
      `确认过账此采购退货单吗？过账后将扣减库存并冲减应付。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'success'
      }
    )

    await postPurchaseReturn(row.id)
    ElMessage.success('退货已过账')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 取消退货
const handleCancel = async (row: PurchaseReturn) => {
  try {
    await ElMessageBox.confirm(
      `确认取消退货单"${row.returnNo}"吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await cancelPurchaseReturn(row.id)
    ElMessage.success('已取消')
    handleQuery()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 导出
const handleExport = async () => {
  try {
    const blob = await exportPurchaseReturns(queryForm)
    downloadBlob(blob, `采购退货_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

// 提交表单
const handleSubmitForm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (form.items.length === 0) {
      ElMessage.warning('请选择采购收货单')
      return
    }

    submitLoading.value = true
    try {
      if (editingId.value) {
        await updatePurchaseReturn(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await createPurchaseReturn(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      handleQuery()
    } catch (error) {
      ElMessage.error(editingId.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 重置表单
const resetForm = () => {
  editingId.value = ''
  form.receiptId = ''
  form.returnDate = ''
  form.items = []
  form.remark = ''
  selectedReceipt.value = undefined
  formRef.value?.resetFields()
}

// 初始化
onMounted(() => {
  handleQuery()
})
</script>

<style scoped>
.purchase-return-management {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
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
  background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
  border-radius: 16px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 8px 32px rgba(6, 182, 212, 0.3);
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  width: 100%;
  height: 100%;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 10px,
    rgba(255, 255, 255, 0.03) 10px,
    rgba(255, 255, 255, 0.03) 20px
  );
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.icon-cube {
  position: relative;
  width: 70px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 18px;
  border: 2px solid rgba(255, 255, 255, 0.25);
  transform-style: preserve-3d;
  animation: cube3D 4s ease-in-out infinite;
}

@keyframes cube3D {
  0%, 100% {
    transform: rotateY(0deg) rotateX(0deg);
  }
  50% {
    transform: rotateY(15deg) rotateX(15deg);
  }
}

.header-icon {
  font-size: 44px;
  color: #ffffff;
}

.icon-particles {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 18px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.3) 1px, transparent 1px);
  background-size: 8px 8px;
  animation: particles 3s linear infinite;
}

@keyframes particles {
  0% {
    background-position: 0 0;
    opacity: 0.3;
  }
  100% {
    background-position: 8px 8px;
    opacity: 0.1;
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
  min-width: 100px;
}

.stat-card:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-3px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
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

.stat-value.draft {
  color: #fcd34d;
  text-shadow: 0 0 10px rgba(252, 211, 77, 0.6);
}

.stat-value.completed {
  color: #a7f3d0;
  text-shadow: 0 0 10px rgba(167, 243, 208, 0.6);
}

.return-table {
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

.return-no {
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: #f97316;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 4px;
  justify-content: center;
  flex-wrap: wrap;
}

.action-buttons :deep(.el-button) {
  padding: 4px 8px;
  transition: all 0.2s ease;
}

.action-buttons :deep(.el-button:hover) {
  transform: translateY(-1px);
}

.return-dialog :deep(.el-dialog__header) {
  background: linear-gradient(to right, #fff7ed, #ffedd5);
  padding: 24px 32px;
  border-bottom: 1px solid #fed7aa;
}

.return-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 0.3px;
}

.return-dialog :deep(.el-dialog__body) {
  padding: 24px 32px;
}

.form-section {
  margin-bottom: 24px;
  padding: 20px;
  background: #fff7ed;
  border-radius: 8px;
  border: 1px solid #fed7aa;
}

.form-section .section-title {
  font-size: 15px;
  font-weight: 600;
  color: #f97316;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #f97316;
}

.items-table {
  margin-top: 12px;
}

.items-table :deep(.el-input-number) {
  width: 100%;
}

.detail-table {
  margin-top: 12px;
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
  border-bottom: 2px solid #f97316;
  letter-spacing: 0.3px;
}

.detail-section .section-title .el-icon {
  color: #f97316;
  font-size: 16px;
}

.linked-detail-loading {
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
}
</style>
