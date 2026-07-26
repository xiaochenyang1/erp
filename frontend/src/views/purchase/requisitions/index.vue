<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item :label="t('purchaseRequisition.keyword')">
          <el-input v-model="query.keyword" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.status')">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option
              v-for="s in statuses"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('purchaseRequisition.search') }}</el-button>
          <el-button @click="handleReset">{{ t('purchaseRequisition.reset') }}</el-button>
          <el-button v-permission="'purchase:requisition:manage'" type="success" @click="openCreate">
            {{ t('purchaseRequisition.create') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="requisitionNo" :label="t('purchaseRequisition.no')" min-width="150" />
        <el-table-column prop="requisitionDate" :label="t('purchaseRequisition.date')" width="120" />
        <el-table-column prop="neededDate" :label="t('purchaseRequisition.neededDate')" width="120">
          <template #default="{ row }">{{ row.neededDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('purchaseRequisition.status')" width="120">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="approvalStatus" :label="t('purchaseRequisition.approvalStatus')" width="130">
          <template #default="{ row }">{{ approvalLabel(row.approvalStatus) }}</template>
        </el-table-column>
        <el-table-column prop="convertedOrderNo" :label="t('purchaseRequisition.convertedPo')" min-width="140">
          <template #default="{ row }">{{ row.convertedOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('purchaseRequisition.remark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('purchaseRequisition.actions')" width="400" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('purchaseRequisition.view') }}</el-button>
            <el-button link type="primary" @click="handlePrint(row)">{{ t('purchaseRequisition.print') }}</el-button>
            <el-button
              v-if="['DRAFT', 'REJECTED'].includes(row.status)"
              v-permission="'purchase:requisition:manage'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              {{ t('purchaseRequisition.edit') }}
            </el-button>
            <el-button
              v-if="['DRAFT', 'REJECTED'].includes(row.status)"
              v-permission="'purchase:requisition:manage'"
              link
              type="success"
              @click="act(row, 'submit')"
            >
              {{ t('purchaseRequisition.submit') }}
            </el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:requisition:manage'"
              link
              type="success"
              @click="act(row, 'approve')"
            >
              {{ t('purchaseRequisition.approve') }}
            </el-button>
            <el-button
              v-if="row.status === 'SUBMITTED'"
              v-permission="'purchase:requisition:manage'"
              link
              type="warning"
              @click="act(row, 'reject')"
            >
              {{ t('purchaseRequisition.reject') }}
            </el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              v-permission="'purchase:requisition:manage'"
              link
              type="primary"
              @click="act(row, 'convert')"
            >
              {{ t('purchaseRequisition.convert') }}
            </el-button>
            <el-button
              v-if="!['CONVERTED', 'CANCELLED'].includes(row.status)"
              v-permission="'purchase:requisition:manage'"
              link
              type="danger"
              @click="act(row, 'cancel')"
            >
              {{ t('purchaseRequisition.cancel') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('purchaseRequisition.editTitle') : t('purchaseRequisition.createTitle')"
      width="860px"
      destroy-on-close
    >
      <el-form label-width="110px">
        <el-form-item :label="t('purchaseRequisition.date')">
          <el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.neededDate')">
          <el-date-picker v-model="form.neededDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.supplier')">
          <el-select v-model="form.supplierId" clearable filterable style="width: 100%">
            <el-option
              v-for="s in suppliers"
              :key="s.id"
              :label="s.supplierName || s.name"
              :value="String(s.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.lines')">
          <div style="width: 100%">
            <el-button size="small" @click="addLine">{{ t('purchaseRequisition.addLine') }}</el-button>
            <el-table :data="form.lines" border style="margin-top: 8px">
              <el-table-column :label="t('purchaseRequisition.product')" min-width="220">
                <template #default="{ row }">
                  <el-select v-model="row.productId" filterable style="width: 100%">
                    <el-option
                      v-for="p in products"
                      :key="p.id"
                      :label="`${p.productCode || p.code || ''} ${p.productName || p.name || ''}`"
                      :value="String(p.id)"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="t('purchaseRequisition.qty')" width="140">
                <template #default="{ row }">
                  <el-input-number v-model="row.qty" :min="0.0001" :controls="false" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column :label="t('purchaseRequisition.lineRemark')" min-width="160">
                <template #default="{ row }">
                  <el-input v-model="row.remark" :placeholder="t('purchaseRequisition.lineRemarkPlaceholder')" />
                </template>
              </el-table-column>
              <el-table-column width="80">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="form.lines.splice($index, 1)">
                    {{ t('purchaseRequisition.delete') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('purchaseRequisition.close') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('purchaseRequisition.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="t('purchaseRequisition.detailTitle')"
      width="860px"
      destroy-on-close
    >
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('purchaseRequisition.no')">{{ detail.requisitionNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.date')">{{ detail.requisitionDate }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.neededDate')">{{ detail.neededDate || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.status')">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.approvalStatus')">{{ approvalLabel(detail.approvalStatus) }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.convertedPo')">{{ detail.convertedOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.supplier')">{{ supplierLabel(detail.supplierId) }}</el-descriptions-item>
          <el-descriptions-item :label="t('purchaseRequisition.remark')">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-lines-title">{{ t('purchaseRequisition.lines') }}</div>
        <el-table :data="detail.lines || []" border stripe>
          <el-table-column type="index" width="60" :label="t('purchaseRequisition.sequence')" />
          <el-table-column prop="productCode" :label="t('purchaseRequisition.productCode')" width="140" />
          <el-table-column prop="productName" :label="t('purchaseRequisition.productName')" min-width="180" />
          <el-table-column prop="qty" :label="t('purchaseRequisition.qty')" width="120" align="right" />
          <el-table-column prop="remark" :label="t('purchaseRequisition.lineRemark')" min-width="160" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">{{ t('purchaseRequisition.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  approvePurchaseRequisition,
  cancelPurchaseRequisition,
  convertPurchaseRequisition,
  createPurchaseRequisition,
  getPurchaseRequisition,
  getPurchaseRequisitions,
  rejectPurchaseRequisition,
  submitPurchaseRequisition,
  updatePurchaseRequisition,
  type PurchaseRequisition
} from '@/api/purchase'
import { getProducts, getSuppliers, type Product, type Supplier } from '@/api/masterdata'
import { formatBusinessDate } from '@/utils/locale'
import { printPurchaseRequisition } from '@/utils/bizPrint'

const { t } = useI18n()

const statuses = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CONVERTED', 'CANCELLED'] as const

const loading = ref(false)
const saving = ref(false)
const detailLoading = ref(false)
const rows = ref<PurchaseRequisition[]>([])
const total = ref(0)
const products = ref<Product[]>([])
const suppliers = ref<Supplier[]>([])
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<string | number | null>(null)
const detail = ref<PurchaseRequisition | null>(null)

const query = reactive({
  keyword: '',
  status: '',
  pageNo: 1,
  pageSize: 20
})

type LineForm = { productId: string; qty: number; remark: string }

const form = reactive<{
  requisitionDate: string
  neededDate: string
  supplierId: string
  remark: string
  lines: LineForm[]
}>({
  requisitionDate: formatBusinessDate(),
  neededDate: '',
  supplierId: '',
  remark: '',
  lines: []
})

const statusLabel = (status?: string) => {
  if (!status) return '-'
  const key = `purchaseRequisition.statusValue.${status.toLowerCase()}`
  const translated = t(key)
  return translated === key ? status : translated
}

const approvalLabel = (status?: string | null) => {
  if (!status) return '-'
  const key = `purchaseRequisition.approvalValue.${status.toLowerCase()}`
  const translated = t(key)
  return translated === key ? status : translated
}

const supplierLabel = (supplierId?: string | number | null) => {
  if (supplierId == null || supplierId === '') return '-'
  const supplier = suppliers.value.find((item) => String(item.id) === String(supplierId))
  return supplier ? (supplier.supplierName || supplier.name || String(supplierId)) : String(supplierId)
}

const loadData = async () => {
  loading.value = true
  try {
    const page = await getPurchaseRequisitions({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    })
    rows.value = page.records || []
    total.value = Number(page.total || 0)
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  const [productPage, supplierPage] = await Promise.all([
    getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
    getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
  ])
  products.value = productPage.records || []
  suppliers.value = supplierPage.records || []
}

const handleSearch = async () => {
  query.pageNo = 1
  await loadData()
}

const handleReset = async () => {
  query.keyword = ''
  query.status = ''
  query.pageNo = 1
  await loadData()
}

const handleSizeChange = async () => {
  query.pageNo = 1
  await loadData()
}

const openCreate = async () => {
  await loadOptions()
  editingId.value = null
  form.requisitionDate = formatBusinessDate()
  form.neededDate = ''
  form.supplierId = ''
  form.remark = ''
  form.lines = [{ productId: '', qty: 1, remark: '' }]
  dialogVisible.value = true
}

const openEdit = async (row: PurchaseRequisition) => {
  await loadOptions()
  const detailData = await getPurchaseRequisition(row.id)
  editingId.value = detailData.id
  form.requisitionDate = detailData.requisitionDate
  form.neededDate = detailData.neededDate || ''
  form.supplierId = detailData.supplierId != null ? String(detailData.supplierId) : ''
  form.remark = detailData.remark || ''
  form.lines = (detailData.lines || []).map((line) => ({
    productId: String(line.productId),
    qty: Number(line.qty || 1),
    remark: line.remark || ''
  }))
  if (form.lines.length === 0) {
    form.lines = [{ productId: '', qty: 1, remark: '' }]
  }
  dialogVisible.value = true
}

const openDetail = async (row: PurchaseRequisition) => {
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    if (suppliers.value.length === 0) {
      await loadOptions()
    }
    detail.value = await getPurchaseRequisition(row.id)
  } catch {
    ElMessage.error(t('purchaseRequisition.message.detailLoadFailed'))
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handlePrint = async (row: PurchaseRequisition) => {
  try {
    if (products.value.length === 0 || suppliers.value.length === 0) {
      await loadOptions()
    }
    const detailData = await getPurchaseRequisition(row.id)
    const productMap = new Map(products.value.map((product) => [String(product.id), product]))
    printPurchaseRequisition({
      ...detailData,
      supplierName: supplierLabel(detailData.supplierId),
      lines: (detailData.lines || []).map((line) => {
        const product = productMap.get(String(line.productId))
        return {
          ...line,
          productCode: line.productCode || product?.productCode || product?.code || line.productId,
          productName: line.productName || product?.productName || product?.name || ''
        }
      })
    })
  } catch {
    ElMessage.error(t('purchaseRequisition.message.printLoadFailed'))
  }
}

const addLine = () => {
  form.lines.push({ productId: '', qty: 1, remark: '' })
}

const save = async () => {
  if (!form.requisitionDate || !form.lines.length || form.lines.some((line) => !line.productId || !line.qty)) {
    ElMessage.warning(t('purchaseRequisition.validation.required'))
    return
  }
  saving.value = true
  try {
    const payload = {
      requisitionDate: form.requisitionDate,
      neededDate: form.neededDate || null,
      supplierId: form.supplierId || null,
      remark: form.remark || undefined,
      lines: form.lines.map((line) => ({
        productId: line.productId,
        qty: line.qty,
        remark: line.remark || undefined
      }))
    }
    if (editingId.value) {
      await updatePurchaseRequisition(editingId.value, payload)
      ElMessage.success(t('purchaseRequisition.message.saved'))
    } else {
      await createPurchaseRequisition(payload)
      ElMessage.success(t('purchaseRequisition.message.created'))
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

const act = async (row: PurchaseRequisition, type: 'submit' | 'approve' | 'reject' | 'cancel' | 'convert') => {
  const confirmKey: Record<typeof type, string> = {
    submit: 'purchaseRequisition.message.submitConfirm',
    approve: 'purchaseRequisition.message.approveConfirm',
    reject: 'purchaseRequisition.message.rejectConfirm',
    cancel: 'purchaseRequisition.message.cancelConfirm',
    convert: 'purchaseRequisition.message.convertConfirm'
  }
  try {
    await ElMessageBox.confirm(
      t(confirmKey[type], { no: row.requisitionNo }),
      t('purchaseRequisition.prompt'),
      {
        confirmButtonText: t('purchaseRequisition.confirm'),
        cancelButtonText: t('purchaseRequisition.close'),
        type: 'warning'
      }
    )
  } catch {
    return
  }

  const map = {
    submit: submitPurchaseRequisition,
    approve: approvePurchaseRequisition,
    reject: rejectPurchaseRequisition,
    cancel: cancelPurchaseRequisition,
    convert: convertPurchaseRequisition
  }
  try {
    await map[type](row.id)
    ElMessage.success(t('purchaseRequisition.message.done'))
    await loadData()
  } catch {
    ElMessage.error(t('purchaseRequisition.message.failed'))
  }
}

onMounted(loadData)
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.detail-lines-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
</style>
