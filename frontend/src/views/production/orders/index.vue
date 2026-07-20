<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item label="订单号">
          <el-input v-model="queryForm.orderNo" placeholder="请输入订单号" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="产品">
          <el-select
            v-model="queryForm.productId"
            placeholder="请选择产品"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="item in productOptions"
              :key="item.id"
              :label="item.productName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已下达" value="RELEASED" />
            <el-option label="已领料" value="MATERIAL_ISSUED" />
            <el-option label="生产中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="queryForm.priority" placeholder="请选择" clearable style="width: 120px">
            <el-option label="低" value="LOW" />
            <el-option label="普通" value="NORMAL" />
            <el-option label="高" value="HIGH" />
            <el-option label="紧急" value="URGENT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>生产订单管理</span>
          <el-button v-permission="'production:order:create'" type="primary" :icon="Plus" @click="handleAdd">新增订单</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="orderNo" label="订单号" width="160" />
        <el-table-column prop="productCode" label="产品编码" width="120" />
        <el-table-column prop="productName" label="产品名称" width="180" />
        <el-table-column prop="planQuantity" label="计划数量" width="100" align="right" />
        <el-table-column prop="completedQuantity" label="完成数量" width="100" align="right" />
        <el-table-column label="完成率" width="100" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round((row.completedQuantity / row.planQuantity) * 100)"
              :color="getProgressColor(row.completedQuantity / row.planQuantity)"
            />
          </template>
        </el-table-column>
        <el-table-column label="材料出库仓" width="140">
          <template #default="{ row }">
            {{ warehouseLabel(row.materialWarehouseId) }}
          </template>
        </el-table-column>
        <el-table-column label="成品入库仓" width="140">
          <template #default="{ row }">
            {{ warehouseLabel(row.finishedWarehouseId) }}
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getPriorityType(row.priority)" size="small">
              {{ getPriorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="planStartDate" label="计划开始" width="120" />
        <el-table-column prop="planEndDate" label="计划结束" width="120" />
        <el-table-column label="操作" width="420" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'production:order:update'"
              type="primary"
              link
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'production:order:release'"
              type="success"
              link
              @click="handleRelease(row)"
            >
              下达
            </el-button>
            <el-button
              v-if="row.status === 'RELEASED'"
              v-permission="'production:order:issue'"
              type="primary"
              link
              @click="handleIssue(row)"
            >
              领料
            </el-button>
            <el-button
              v-if="row.status === 'RELEASED' || row.status === 'MATERIAL_ISSUED' || row.status === 'IN_PROGRESS'"
              v-permission="'production:order:report'"
              type="warning"
              link
              @click="openOperations(row)"
            >
              工序报工
            </el-button>
            <el-button
              v-if="row.status === 'MATERIAL_ISSUED' || row.status === 'IN_PROGRESS'"
              v-permission="'production:order:complete'"
              type="success"
              link
              @click="handleComplete(row)"
            >
              完工
            </el-button>
            <el-button
              v-if="canReverseCompletion(row)"
              v-permission="'production:order:reverse-completion'"
              type="warning"
              link
              @click="handleReverseCompletion(row)"
            >
              红冲
            </el-button>
            <el-button
              v-if="canReturnMaterials(row)"
              v-permission="'production:order:return'"
              type="warning"
              link
              @click="handleReturnMaterials(row)"
            >
              退料
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'production:order:cancel'"
              type="danger"
              link
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      :close-on-click-modal="false"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <el-select
                v-model="formData.productId"
                placeholder="请选择产品"
                filterable
                :disabled="Boolean(formData.id)"
                style="width: 100%"
                @change="handleProductChange"
              >
                <el-option
                  v-for="item in productOptions"
                  :key="item.id"
                  :label="`${item.productCode} - ${item.productName}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="BOM" prop="bomId">
              <el-select
                v-model="formData.bomId"
                placeholder="请选择BOM"
                clearable
                filterable
                :disabled="Boolean(formData.id)"
                style="width: 100%"
              >
                <el-option
                  v-for="item in bomOptions"
                  :key="item.id"
                  :label="bomLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划数量" prop="planQuantity">
              <el-input-number
                v-model="formData.planQuantity"
                :min="1"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="材料出库仓" prop="materialWarehouseId">
              <el-select
                v-model="formData.materialWarehouseId"
                placeholder="请选择材料出库仓"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="item in warehouseOptions"
                  :key="item.id"
                  :label="warehouseLabel(item.id)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="成品入库仓" prop="finishedWarehouseId">
              <el-select
                v-model="formData.finishedWarehouseId"
                placeholder="请选择成品入库仓"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="item in warehouseOptions"
                  :key="item.id"
                  :label="warehouseLabel(item.id)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始日期" prop="planStartDate">
              <el-date-picker
                v-model="formData.planStartDate"
                type="date"
                placeholder="请选择日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划结束日期" prop="planEndDate">
              <el-date-picker
                v-model="formData.planEndDate"
                type="date"
                placeholder="请选择日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="formData.priority">
            <el-radio value="LOW">低</el-radio>
            <el-radio value="NORMAL">普通</el-radio>
            <el-radio value="HIGH">高</el-radio>
            <el-radio value="URGENT">紧急</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" title="生产订单详情" width="1000px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ viewData.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="BOM编码">{{ viewData.bomCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ viewData.productCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ viewData.productName }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ viewData.planQuantity }}</el-descriptions-item>
        <el-descriptions-item label="完成数量">{{ viewData.completedQuantity }}</el-descriptions-item>
        <el-descriptions-item label="报废数量">{{ viewData.scrapQuantity }}</el-descriptions-item>
        <el-descriptions-item label="材料出库仓">{{ warehouseLabel(viewData.materialWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="成品入库仓">{{ warehouseLabel(viewData.finishedWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="getPriorityType(viewData.priority)">
            {{ getPriorityLabel(viewData.priority) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(viewData.status)">
            {{ getStatusLabel(viewData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ viewData.planStartDate }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ viewData.planEndDate }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ viewData.actualStartDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{ viewData.actualEndDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ viewData.createdBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewData.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider v-if="viewData.materials && viewData.materials.length > 0" />

      <h4 v-if="viewData.materials && viewData.materials.length > 0">物料使用情况</h4>
      <el-table v-if="viewData.materials && viewData.materials.length > 0" :data="viewData.materials" border stripe style="margin-top: 10px">
        <el-table-column prop="materialCode" label="物料编码" width="150" />
        <el-table-column prop="materialName" label="物料名称" width="200" />
        <el-table-column prop="requiredQuantity" label="需求数量" width="120" align="right" />
        <el-table-column prop="issuedQuantity" label="已领数量" width="120" align="right" />
        <el-table-column prop="returnedQuantity" label="已退数量" width="120" align="right" />
        <el-table-column prop="unit" label="单位" width="80" />
      </el-table>

      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 完工对话框 -->
    <el-dialog v-model="completeDialogVisible" title="生产完工" width="600px">
      <el-form :model="completeForm" label-width="120px">
        <el-form-item label="完工数量" required>
          <el-input-number
            v-model="completeForm.completedQuantity"
            :min="1"
            :max="completeForm.maxQuantity"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
          <el-text type="info" size="small">最大可完工: {{ completeForm.maxQuantity }}</el-text>
        </el-form-item>
        <el-form-item label="报废数量">
          <el-input-number
            v-model="completeForm.scrapQuantity"
            :min="0"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="完工日期">
          <el-date-picker
            v-model="completeForm.completionDate"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="completeForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirmComplete">
          确定完工
        </el-button>
      </template>
    </el-dialog>

    <!-- 完工红冲对话框 -->
    <el-dialog v-model="reverseDialogVisible" title="完工红冲" width="600px">
      <el-form :model="reverseForm" label-width="120px">
        <el-form-item label="红冲数量" required>
          <el-input-number
            v-model="reverseForm.reversedQty"
            :min="1"
            :max="reverseForm.maxQuantity"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
          <el-text type="info" size="small">最大可红冲: {{ reverseForm.maxQuantity }}</el-text>
        </el-form-item>
        <el-form-item label="红冲日期">
          <el-date-picker
            v-model="reverseForm.reversalDate"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="reverseForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reverseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirmReverseCompletion">
          确定红冲
        </el-button>
      </template>
    </el-dialog>

    <!-- 生产退料对话框 -->
    <el-dialog v-model="returnDialogVisible" title="生产退料" width="980px">
      <el-form :model="returnForm" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="退料日期">
              <el-date-picker
                v-model="returnForm.returnDate"
                type="date"
                placeholder="请选择日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="returnForm.remark" placeholder="请输入备注" clearable />
            </el-form-item>
          </el-col>
        </el-row>

        <el-table :data="returnForm.materials" border stripe>
          <el-table-column prop="materialCode" label="物料编码" width="140" />
          <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="issuedQuantity" label="可退数量" width="110" align="right" />
          <el-table-column label="本次退料" width="170">
            <template #default="{ row }">
              <el-input-number
                v-model="row.returnQty"
                :min="0"
                :max="row.issuedQuantity"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="批次号" width="160">
            <template #default="{ row }">
              <el-input v-model="row.lotNo" clearable />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.remark" clearable />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="returnDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirmReturnMaterials">
          确定退料
        </el-button>
      </template>
    </el-dialog>

    <!-- 工序报工 -->
    <el-dialog v-model="opsDialogVisible" :title="`工序报工 · ${opsOrderNo}`" width="920px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="若工单 BOM 绑定了启用中的工艺路线，下达后会自动生成工序。无工序时完工不校验；有工序时须全部报工完成且合格量足够。"
        style="margin-bottom: 12px"
      />
      <el-table v-loading="opsLoading" :data="operations" border stripe>
        <el-table-column prop="lineNo" label="序号" width="70" />
        <el-table-column prop="operationCode" label="工序码" width="100" />
        <el-table-column prop="operationName" label="工序名称" min-width="120" />
        <el-table-column prop="workCenterName" label="工作中心" min-width="120" show-overflow-tooltip />
        <el-table-column prop="plannedQty" label="计划" width="90" align="right" />
        <el-table-column prop="reportedQty" label="已报" width="90" align="right" />
        <el-table-column prop="qualifiedQty" label="合格" width="90" align="right" />
        <el-table-column prop="scrapQty" label="报废" width="90" align="right" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="opStatusType(row.status)" size="small">{{ opStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'DONE'"
              v-permission="'production:order:report'"
              link
              type="primary"
              @click="openReport(row)"
            >
              报工
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!opsLoading && operations.length === 0" description="暂无工序（未绑定工艺路线或下达前无快照）" />
      <template #footer>
        <el-button @click="opsDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="opsLoading" @click="loadOperations">刷新</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportDialogVisible" title="工序报工" width="480px">
      <el-form :model="reportForm" label-width="100px">
        <el-form-item label="工序">
          <el-input :model-value="reportForm.operationName" disabled />
        </el-form-item>
        <el-form-item label="报工数量" required>
          <el-input-number v-model="reportForm.reportQty" :min="0.0001" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="合格数量" required>
          <el-input-number v-model="reportForm.qualifiedQty" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="报废数量">
          <el-input-number v-model="reportForm.scrapQty" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reportForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reportLoading" @click="submitReport">确认报工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, View } from '@element-plus/icons-vue'
import {
  getProductionOrders,
  getProductionOrder,
  createProductionOrder,
  updateProductionOrder,
  releaseProductionOrder,
  issueProductionOrder,
  completeProductionOrder,
  reverseProductionCompletion,
  returnProductionMaterials,
  cancelProductionOrder,
  getProductionOrderOperations,
  reportProductionOperation,
  type ProductionOrder,
  type ProductionOrderMaterial,
  type ProductionOrderOperation
} from '@/api/production'
import { getProducts, type Product } from '@/api/masterdata'
import { getWarehouses, type Warehouse } from '@/api/masterdata'
import { getBOMs, type BOM } from '@/api/production'

interface ReturnMaterialRow extends ProductionOrderMaterial {
  returnQty: number
  lotNo?: string
  remark?: string
}

// 查询表单
const queryForm = reactive({
  orderNo: '',
  productId: undefined as string | number | undefined,
  status: '',
  priority: ''
})

// 表格数据
const loading = ref(false)
const tableData = ref<ProductionOrder[]>([])

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 选项数据
const productOptions = ref<Product[]>([])
const warehouseOptions = ref<Warehouse[]>([])
const allBomOptions = ref<BOM[]>([])
const bomOptions = ref<BOM[]>([])

// 新增/编辑对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as string | number | undefined,
  productId: undefined as string | number | undefined,
  bomId: undefined as string | number | undefined,
  planQuantity: 1,
  materialWarehouseId: undefined as string | number | undefined,
  finishedWarehouseId: undefined as string | number | undefined,
  planStartDate: '',
  planEndDate: '',
  priority: 'NORMAL',
  remark: ''
})

const formRules: FormRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  bomId: [{ required: true, message: '请选择BOM', trigger: 'change' }],
  planQuantity: [{ required: true, message: '请输入计划数量', trigger: 'blur' }],
  materialWarehouseId: [{ required: true, message: '请选择材料出库仓', trigger: 'change' }],
  finishedWarehouseId: [{ required: true, message: '请选择成品入库仓', trigger: 'change' }],
  planStartDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  planEndDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

// 查看对话框
const viewDialogVisible = ref(false)
const viewData = ref<ProductionOrder>({} as ProductionOrder)

// 完工对话框
const completeDialogVisible = ref(false)
const completeForm = reactive({
  orderId: '' as string | number,
  completedQuantity: 0,
  scrapQuantity: 0,
  completionDate: '',
  maxQuantity: 0,
  remark: ''
})

// 工序报工
const opsDialogVisible = ref(false)
const opsLoading = ref(false)
const opsOrderId = ref<string | number>('')
const opsOrderNo = ref('')
const operations = ref<ProductionOrderOperation[]>([])
const reportDialogVisible = ref(false)
const reportLoading = ref(false)
const reportForm = reactive({
  operationId: '' as string | number,
  operationName: '',
  reportQty: 1,
  qualifiedQty: 1,
  scrapQty: 0,
  remark: ''
})

// 完工红冲对话框
const reverseDialogVisible = ref(false)
const reverseForm = reactive({
  orderId: '' as string | number,
  reversedQty: 0,
  reversalDate: '',
  maxQuantity: 0,
  remark: ''
})

// 生产退料对话框
const returnDialogVisible = ref(false)
const returnForm = reactive({
  orderId: '' as string | number,
  returnDate: '',
  remark: '',
  materials: [] as ReturnMaterialRow[]
})

// 加载选项数据
const loadOptions = async () => {
  try {
    const optionPageQuery = { pageNo: 1, pageSize: 200 }
    const [products, warehouses, boms] = await Promise.all([
      getProducts(optionPageQuery),
      getWarehouses(optionPageQuery),
      getBOMs(optionPageQuery)
    ])
    productOptions.value = products.records || []
    warehouseOptions.value = warehouses.records || []
    allBomOptions.value = boms.records || []
    bomOptions.value = allBomOptions.value
  } catch (error) {
    console.error('加载选项数据失败:', error)
  }
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      ...queryForm,
      page: pagination.page,
      size: pagination.size
    }
    const res = await getProductionOrders(params)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载生产订单失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  pagination.page = 1
  loadData()
}

// 重置
const handleReset = () => {
  Object.assign(queryForm, {
    orderNo: '',
    productId: undefined,
    status: '',
    priority: ''
  })
  pagination.page = 1
  loadData()
}

const resetFormData = () => {
  Object.assign(formData, {
    id: undefined,
    productId: undefined,
    bomId: undefined,
    planQuantity: 1,
    materialWarehouseId: undefined,
    finishedWarehouseId: undefined,
    planStartDate: '',
    planEndDate: '',
    priority: 'NORMAL',
    remark: ''
  })
  bomOptions.value = allBomOptions.value
}

// 新增
const handleAdd = () => {
  resetFormData()
  dialogTitle.value = '新增生产订单'
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row: ProductionOrder) => {
  try {
    const order = await getProductionOrder(row.id)
    Object.assign(formData, {
      id: order.id,
      productId: order.productId,
      bomId: order.bomId,
      planQuantity: order.planQuantity,
      materialWarehouseId: order.materialWarehouseId,
      finishedWarehouseId: order.finishedWarehouseId,
      planStartDate: order.planStartDate,
      planEndDate: order.planEndDate,
      priority: order.priority,
      remark: order.remark || ''
    })
    bomOptions.value = allBomOptions.value.filter(b => String(b.productId) === String(order.productId))
    dialogTitle.value = '编辑生产订单'
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载生产订单失败')
  }
}

// 查看
const handleView = async (row: ProductionOrder) => {
  try {
    const res = await getProductionOrder(row.id)
    viewData.value = res
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载订单详情失败')
  }
}

// 下达
const handleRelease = async (row: ProductionOrder) => {
  try {
    await ElMessageBox.confirm(`确定要下达生产订单"${row.orderNo}"吗？`, '提示', {
      type: 'warning'
    })
    await releaseProductionOrder(row.id)
    ElMessage.success('下达成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('下达失败')
    }
  }
}

// 领料
const handleIssue = async (row: ProductionOrder) => {
  try {
    await ElMessageBox.confirm(`确定按剩余需求领料生产订单"${row.orderNo}"吗？`, '提示', {
      type: 'warning'
    })
    await issueProductionOrder(row.id, {
      issueDate: row.planStartDate || new Date().toISOString().split('T')[0],
      remark: '前端生产订单页确认领料'
    })
    ElMessage.success('领料成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('领料失败')
    }
  }
}

// 完工
const handleComplete = (row: ProductionOrder) => {
  completeForm.orderId = row.id
  completeForm.maxQuantity = row.planQuantity - row.completedQuantity
  completeForm.completedQuantity = completeForm.maxQuantity
  completeForm.scrapQuantity = 0
  completeForm.completionDate = new Date().toISOString().split('T')[0]
  completeForm.remark = ''
  completeDialogVisible.value = true
}

const openOperations = async (row: ProductionOrder) => {
  opsOrderId.value = row.id
  opsOrderNo.value = row.orderNo
  opsDialogVisible.value = true
  await loadOperations()
}

const loadOperations = async () => {
  if (!opsOrderId.value) return
  opsLoading.value = true
  try {
    operations.value = await getProductionOrderOperations(opsOrderId.value)
  } catch {
    ElMessage.error('加载工序失败')
  } finally {
    opsLoading.value = false
  }
}

const openReport = (row: ProductionOrderOperation) => {
  reportForm.operationId = row.id
  reportForm.operationName = `${row.operationCode} ${row.operationName}`
  const remain = Math.max(Number(row.plannedQty) - Number(row.reportedQty), 0.0001)
  reportForm.reportQty = remain
  reportForm.qualifiedQty = remain
  reportForm.scrapQty = 0
  reportForm.remark = ''
  reportDialogVisible.value = true
}

const submitReport = async () => {
  if (reportForm.qualifiedQty > reportForm.reportQty) {
    ElMessage.warning('合格数量不能大于报工数量')
    return
  }
  reportLoading.value = true
  try {
    await reportProductionOperation(opsOrderId.value, reportForm.operationId, {
      reportQty: reportForm.reportQty,
      qualifiedQty: reportForm.qualifiedQty,
      scrapQty: reportForm.scrapQty,
      remark: reportForm.remark || undefined
    })
    ElMessage.success('报工成功')
    reportDialogVisible.value = false
    await loadOperations()
  } catch {
    // interceptor
  } finally {
    reportLoading.value = false
  }
}

const opStatusText = (status: string) =>
  ({ PENDING: '待报工', IN_PROGRESS: '进行中', DONE: '已完成' }[status] || status)

const opStatusType = (status: string) =>
  ({ PENDING: 'info', IN_PROGRESS: 'warning', DONE: 'success' }[status] || 'info') as
    | 'info'
    | 'warning'
    | 'success'

// 确认完工
const handleConfirmComplete = async () => {
  if (!completeForm.completedQuantity) {
    ElMessage.warning('请输入完工数量')
    return
  }

  submitLoading.value = true
  try {
    await completeProductionOrder(completeForm.orderId, {
      completedQuantity: completeForm.completedQuantity,
      scrapQuantity: completeForm.scrapQuantity,
      completionDate: completeForm.completionDate,
      remark: completeForm.remark
    })
    ElMessage.success('完工成功')
    completeDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('完工失败')
  } finally {
    submitLoading.value = false
  }
}

const canReverseCompletion = (row: ProductionOrder) => {
  return row.status !== 'CANCELLED' && Number(row.completedQuantity || 0) > 0
}

const canReturnMaterials = (row: ProductionOrder) => {
  return ['MATERIAL_ISSUED', 'IN_PROGRESS'].includes(row.status)
}

const handleReverseCompletion = (row: ProductionOrder) => {
  const completedQuantity = Number(row.completedQuantity || 0)
  reverseForm.orderId = row.id
  reverseForm.maxQuantity = completedQuantity
  reverseForm.reversedQty = completedQuantity
  reverseForm.reversalDate = row.actualEndDate || row.planEndDate || new Date().toISOString().split('T')[0]
  reverseForm.remark = ''
  reverseDialogVisible.value = true
}

const handleConfirmReverseCompletion = async () => {
  if (!reverseForm.reversedQty) {
    ElMessage.warning('请输入红冲数量')
    return
  }

  submitLoading.value = true
  try {
    await reverseProductionCompletion(reverseForm.orderId, {
      reversedQty: reverseForm.reversedQty,
      reversalDate: reverseForm.reversalDate,
      remark: reverseForm.remark
    })
    ElMessage.success('红冲成功')
    reverseDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('红冲失败')
  } finally {
    submitLoading.value = false
  }
}

const handleReturnMaterials = async (row: ProductionOrder) => {
  try {
    const order = await getProductionOrder(row.id)
    const returnableMaterials = (order.materials || [])
      .filter(material => Number(material.issuedQuantity || 0) > 0)
      .map(material => ({
        ...material,
        returnQty: 0,
        lotNo: '',
        remark: ''
      }))

    if (returnableMaterials.length === 0) {
      ElMessage.warning('当前工单没有可退物料')
      return
    }

    returnForm.orderId = order.id
    returnForm.returnDate = order.actualStartDate || order.planStartDate || new Date().toISOString().split('T')[0]
    returnForm.remark = ''
    returnForm.materials = returnableMaterials
    returnDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载可退物料失败')
  }
}

const handleConfirmReturnMaterials = async () => {
  const lines = returnForm.materials
    .filter(material => Number(material.returnQty || 0) > 0)
    .map(material => ({
      orderMaterialId: material.id,
      returnQty: material.returnQty,
      lotNo: material.lotNo || undefined,
      remark: material.remark || undefined
    }))

  if (lines.length === 0) {
    ElMessage.warning('请输入本次退料数量')
    return
  }

  submitLoading.value = true
  try {
    await returnProductionMaterials(returnForm.orderId, {
      returnDate: returnForm.returnDate,
      remark: returnForm.remark,
      lines
    })
    ElMessage.success('退料成功')
    returnDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('退料失败')
  } finally {
    submitLoading.value = false
  }
}

// 取消
const handleCancel = async (row: ProductionOrder) => {
  try {
    await ElMessageBox.confirm(`确定要取消生产订单"${row.orderNo}"吗？`, '提示', {
      type: 'warning'
    })
    await cancelProductionOrder(row.id)
    ElMessage.success('取消成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

// 产品变化
const handleProductChange = (productId: string | number) => {
  formData.bomId = undefined
  bomOptions.value = allBomOptions.value.filter(b => String(b.productId) === String(productId))
}

const bomLabel = (bom: BOM) => {
  const code = bom.bomCode || bom.bomNo || `BOM${bom.id}`
  return `${code} - 基准数量 ${bom.baseQty} - ${bom.status}`
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (formData.id) {
        await updateProductionOrder(formData.id, formData)
        ElMessage.success('更新成功')
      } else {
        await createProductionOrder(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error(formData.id ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 对话框关闭
const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetFormData()
}

// 获取状态标签
const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    RELEASED: '已下达',
    MATERIAL_ISSUED: '已领料',
    IN_PROGRESS: '生产中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

// 获取状态类型
const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    DRAFT: 'info',
    RELEASED: 'warning',
    MATERIAL_ISSUED: 'primary',
    IN_PROGRESS: 'primary',
    COMPLETED: 'success',
    CANCELLED: 'danger'
  }
  return map[status] || undefined
}

// 获取优先级标签
const getPriorityLabel = (priority: string) => {
  const map: Record<string, string> = {
    LOW: '低',
    NORMAL: '普通',
    HIGH: '高',
    URGENT: '紧急'
  }
  return map[priority] || priority
}

// 获取优先级类型
const getPriorityType = (priority: string) => {
  const map: Record<string, any> = {
    LOW: 'info',
    NORMAL: 'info',
    HIGH: 'warning',
    URGENT: 'danger'
  }
  return map[priority] || undefined
}

const warehouseLabel = (warehouseId?: string | number) => {
  if (warehouseId == null || warehouseId === '') return '-'
  const warehouse = warehouseOptions.value.find(item => String(item.id) === String(warehouseId))
  return warehouse?.warehouseName || warehouse?.name || `仓库 ${warehouseId}`
}

// 获取进度条颜色
const getProgressColor = (rate: number) => {
  if (rate < 0.3) return '#909399'
  if (rate < 0.7) return '#e6a23c'
  if (rate < 1) return '#409eff'
  return '#67c23a'
}

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
