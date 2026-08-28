<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item :label="$t('contractPage.keyword')"><el-input v-model="query.keyword" :placeholder="$t('contractPage.keywordPlaceholder')" clearable @keyup.enter="loadData" /></el-form-item>
        <el-form-item :label="$t('contractPage.type')"><el-select v-model="query.contractType" clearable style="width: 140px"><el-option :label="$t('contractPage.sales')" value="SALES" /><el-option :label="$t('contractPage.purchase')" value="PURCHASE" /></el-select></el-form-item>
        <el-form-item :label="$t('contractPage.status')"><el-select v-model="query.status" clearable style="width: 150px"><el-option v-for="status in statuses" :key="status" :label="$t(`contractPage.statusValue.${status.toLowerCase()}`)" :value="status" /></el-select></el-form-item>
        <el-form-item><el-button :icon="Search" type="primary" @click="loadData">{{ $t('contractPage.search') }}</el-button><el-button :icon="Refresh" @click="resetQuery">{{ $t('contractPage.reset') }}</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar"><span>{{ $t('contractPage.title') }}</span><span><el-button :icon="Download" @click="handleExport">{{ $t('contractPage.export') }}</el-button><el-button v-permission="'contract:manage'" :icon="Plus" type="primary" @click="openCreate">{{ $t('contractPage.create') }}</el-button></span></div>
      </template>
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="contractNo" :label="$t('contractPage.contractNo')" min-width="155" />
        <el-table-column prop="contractName" :label="$t('contractPage.contractName')" min-width="190" show-overflow-tooltip />
        <el-table-column :label="$t('contractPage.type')" width="110"><template #default="{ row }">{{ row.contractType === 'SALES' ? $t('contractPage.sales') : $t('contractPage.purchase') }}</template></el-table-column>
        <el-table-column :label="$t('contractPage.partner')" min-width="160"><template #default="{ row }">{{ row.customerName || row.supplierName || '-' }}</template></el-table-column>
        <el-table-column prop="signedDate" :label="$t('contractPage.signedDate')" width="125"><template #default="{ row }">{{ formatDate(row.signedDate) }}</template></el-table-column>
        <el-table-column :label="$t('contractPage.effectivePeriod')" width="220"><template #default="{ row }">{{ formatDate(row.effectiveFrom) }} ~ {{ row.effectiveTo ? formatDate(row.effectiveTo) : '-' }}</template></el-table-column>
        <el-table-column prop="totalAmount" :label="$t('contractPage.totalAmount')" width="140" align="right"><template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template></el-table-column>
        <el-table-column :label="$t('contractPage.status')" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ $t(`contractPage.statusValue.${row.status.toLowerCase()}`) }}</el-tag></template></el-table-column>
        <el-table-column :label="$t('contractPage.alert')" min-width="180"><template #default="{ row }"><el-tag v-for="alert in alertsFor(row.id)" :key="alert" size="small" type="warning" class="alert-tag">{{ alertText(alert) }}</el-tag><span v-if="!alertsFor(row.id).length">-</span></template></el-table-column>
        <el-table-column :label="$t('contractPage.actions')" width="390" fixed="right"><template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">{{ $t('contractPage.detail') }}</el-button>
          <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" v-permission="'contract:manage'" link type="primary" @click="openEdit(row)">{{ $t('contractPage.edit') }}</el-button>
          <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" v-permission="'contract:manage'" link type="success" @click="runAction(submitContract, row, 'contractPage.submit')">{{ $t('contractPage.submit') }}</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" v-permission="'contract:approve'" link type="success" @click="runAction(approveContract, row, 'contractPage.approve')">{{ $t('contractPage.approve') }}</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" v-permission="'contract:approve'" link type="warning" @click="runAction(rejectContract, row, 'contractPage.reject')">{{ $t('contractPage.reject') }}</el-button>
          <el-button v-if="row.status === 'ACTIVE'" v-permission="'contract:approve'" link type="warning" @click="runAction(closeContract, row, 'contractPage.close')">{{ $t('contractPage.close') }}</el-button>
          <el-button v-if="['DRAFT', 'SUBMITTED', 'REJECTED', 'ACTIVE'].includes(row.status)" v-permission="'contract:manage'" link type="danger" @click="runAction(cancelContract, row, 'contractPage.cancelContract')">{{ $t('contractPage.cancelContract') }}</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, sizes, prev, pager, next" :total="total" :current-page="query.pageNo" :page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" @current-change="(page) => { query.pageNo = page; loadData() }" @size-change="(size) => { query.pageSize = size; query.pageNo = 1; loadData() }" />
    </el-card>

    <el-dialog v-model="formVisible" :title="editingId ? $t('contractPage.editTitle') : $t('contractPage.createTitle')" width="1040px" destroy-on-close>
      <el-form label-width="105px">
        <el-row :gutter="12"><el-col :span="8"><el-form-item :label="$t('contractPage.type')"><el-select v-model="form.contractType" style="width: 100%"><el-option :label="$t('contractPage.sales')" value="SALES" /><el-option :label="$t('contractPage.purchase')" value="PURCHASE" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.customer')" v-if="form.contractType === 'SALES'"><el-select v-model="form.customerId" filterable style="width: 100%"><el-option v-for="item in customers" :key="item.id" :label="`${item.customerCode} ${item.customerName}`" :value="String(item.id)" /></el-select></el-form-item><el-form-item :label="$t('contractPage.supplier')" v-else><el-select v-model="form.supplierId" filterable style="width: 100%"><el-option v-for="item in suppliers" :key="item.id" :label="`${item.supplierCode} ${item.supplierName}`" :value="String(item.id)" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.contractName')"><el-input v-model="form.contractName" /></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :span="8"><el-form-item :label="$t('contractPage.signedDate')"><el-date-picker v-model="form.signedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.effectiveFrom')"><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.effectiveTo')"><el-date-picker v-model="form.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable style="width: 100%" /></el-form-item></el-col></el-row>
        <el-form-item :label="$t('contractPage.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <div class="line-toolbar"><b>{{ $t('contractPage.lines') }}</b><el-button link type="primary" :icon="Plus" @click="addLine">{{ $t('contractPage.addLine') }}</el-button></div>
        <el-table :data="form.lines" border size="small"><el-table-column :label="$t('contractPage.product')" min-width="300"><template #default="{ row }"><el-select v-model="row.productId" filterable style="width: 100%"><el-option v-for="item in products" :key="item.id" :label="`${item.productCode || item.code || ''} ${item.productName || item.name || ''}`.trim()" :value="String(item.id)" /></el-select></template></el-table-column><el-table-column :label="$t('contractPage.quantity')" width="145"><template #default="{ row }"><el-input-number v-model="row.quantity" :min="0.0001" :precision="4" :controls="false" style="width: 100%" /></template></el-table-column><el-table-column :label="$t('contractPage.unitPrice')" width="145"><template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" :controls="false" style="width: 100%" /></template></el-table-column><el-table-column :label="$t('contractPage.amount')" width="135" align="right"><template #default="{ row }">{{ formatMoney(Number(row.quantity || 0) * Number(row.unitPrice || 0)) }}</template></el-table-column><el-table-column width="80"><template #default="{ $index }"><el-button link type="danger" @click="removeLine($index)">{{ $t('contractPage.delete') }}</el-button></template></el-table-column></el-table>
      </el-form>
      <template #footer><el-button @click="formVisible = false">{{ $t('contractPage.cancel') }}</el-button><el-button type="primary" :loading="saving" @click="save">{{ $t('contractPage.save') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="$t('contractPage.detailTitle')" width="1160px"><el-descriptions v-if="selected" :column="3" border><el-descriptions-item :label="$t('contractPage.contractNo')">{{ selected.contractNo }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.contractName')">{{ selected.contractName }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.partner')">{{ selected.customerName || selected.supplierName }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.signedDate')">{{ formatDate(selected.signedDate) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.effectivePeriod')">{{ formatDate(selected.effectiveFrom) }} ~ {{ selected.effectiveTo ? formatDate(selected.effectiveTo) : '-' }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.totalAmount')">{{ formatMoney(selected.totalAmount) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.remark')" :span="3">{{ selected.remark || '-' }}</el-descriptions-item></el-descriptions><el-table v-if="selected" :data="selected.lines" border stripe class="detail-table"><el-table-column prop="productCode" :label="$t('contractPage.product')" min-width="240"><template #default="{ row }">{{ `${row.productCode || ''} ${row.productName || ''}`.trim() || row.productId }}</template></el-table-column><el-table-column prop="quantity" :label="$t('contractPage.quantity')" width="110" /><el-table-column prop="committedQuantity" :label="$t('contractPage.committedQuantity')" width="125" /><el-table-column :label="$t('contractPage.availableOrderQuantity')" width="125"><template #default="{ row }">{{ availableOrderQuantity(row) }}</template></el-table-column><el-table-column prop="fulfilledQuantity" :label="$t('contractPage.fulfilledQuantity')" width="110" /><el-table-column :label="$t('contractPage.availableFulfillmentQuantity')" width="135"><template #default="{ row }">{{ availableFulfillmentQuantity(row) }}</template></el-table-column><el-table-column :label="$t('contractPage.fulfillmentProgress')" min-width="170"><template #default="{ row }"><el-progress :percentage="fulfillmentPercentage(row)" :stroke-width="10" :format="(percentage: number) => `${percentage}%`" /></template></el-table-column><el-table-column prop="unitPrice" :label="$t('contractPage.unitPrice')" width="110" /><el-table-column prop="amount" :label="$t('contractPage.amount')" width="120" /></el-table><div class="attachment-panel"><div class="attachment-header"><b>{{ $t('contractPage.attachments') }}</b><input ref="attachmentInput" type="file" hidden @change="handleAttachmentUpload" /><el-button v-permission="'contract:manage'" size="small" type="primary" @click="attachmentInput?.click()">{{ $t('contractPage.uploadAttachment') }}</el-button></div><el-table :data="attachments" size="small" border><el-table-column prop="originalFilename" :label="$t('contractPage.filename')" /><el-table-column prop="fileSize" :label="$t('contractPage.fileSize')" width="100" /><el-table-column prop="createdTime" :label="$t('contractPage.uploadedAt')" width="180" /><el-table-column width="180"><template #default="{ row }"><el-button link type="primary" @click="downloadAttachmentRow(row)">{{ $t('contractPage.downloadAttachment') }}</el-button><el-button v-permission="'contract:manage'" link type="danger" @click="deleteAttachmentRow(row)">{{ $t('contractPage.deleteAttachment') }}</el-button></template></el-table-column></el-table></div></el-dialog>
    <el-card v-if="selected && versions.length" shadow="never" class="version-card"><template #header>{{ $t('contractPage.versions') }}</template><el-table :data="versions" size="small" border><el-table-column prop="versionNo" :label="$t('contractPage.versionNo')" width="80" /><el-table-column prop="eventType" :label="$t('contractPage.versionEvent')" width="130" /><el-table-column prop="status" :label="$t('contractPage.status')" width="110" /><el-table-column prop="createdTime" :label="$t('contractPage.versionTime')" width="180" /><el-table-column :label="$t('contractPage.changedFields')" min-width="230"><template #default="{ row }">{{ row.changedFields?.join(', ') || '-' }}</template></el-table-column><el-table-column width="150"><template #default="{ row }"><el-button link type="primary" @click="restoreVersionRow(row)">{{ $t('contractPage.restoreVersion') }}</el-button></template></el-table-column></el-table></el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { approveContract, cancelContract, closeContract, createContract, deleteContractAttachment, downloadContractAttachment, exportContracts, getContract, getContractAlerts, getContractAttachments, getContractVersions, getContracts, rejectContract, restoreContractVersion, submitContract, updateContract, uploadContractAttachment, type ContractAlertRecord, type ContractRecord, type ContractSaveRequest, type ContractVersionRecord } from '@/api/contracts'
import type { Attachment } from '@/api/attachment'
import { getCustomers, getProducts, getSuppliers, type Customer, type Product, type Supplier } from '@/api/masterdata'
import { downloadBlob } from '@/utils/download'
import { formatBusinessDate, formatLocalizedCurrency, formatLocalizedDate } from '@/utils/locale'

const { t } = useI18n()
const statuses = ['DRAFT', 'SUBMITTED', 'REJECTED', 'ACTIVE', 'CLOSED', 'CANCELLED']
const query = reactive<any>({ pageNo: 1, pageSize: 20, keyword: '', contractType: '', status: '' })
const rows = ref<ContractRecord[]>([]); const total = ref(0); const loading = ref(false)
const alerts = ref<ContractAlertRecord[]>([])
const customers = ref<Customer[]>([]); const suppliers = ref<Supplier[]>([]); const products = ref<Product[]>([])
const formVisible = ref(false); const detailVisible = ref(false); const saving = ref(false); const editingId = ref<string | null>(null); const selected = ref<ContractRecord | null>(null)
const attachments = ref<Attachment[]>([]); const versions = ref<ContractVersionRecord[]>([]); const attachmentInput = ref<HTMLInputElement>()
const form = reactive<ContractSaveRequest & { lines: Array<{ productId: string; quantity: number; unitPrice: number }> }>({ contractType: 'SALES', customerId: '', supplierId: undefined, contractName: '', signedDate: formatBusinessDate(), effectiveFrom: formatBusinessDate(), effectiveTo: '', remark: '', lines: [{ productId: '', quantity: 1, unitPrice: 0 }] })

const formatDate = (value?: string) => formatLocalizedDate(value) || '-'
const formatMoney = (value?: number) => formatLocalizedCurrency(Number(value || 0))
const availableOrderQuantity = (line: ContractRecord['lines'][number]) => Math.max(0, Number(line.quantity || 0) - Number(line.committedQuantity || 0))
const availableFulfillmentQuantity = (line: ContractRecord['lines'][number]) => Math.max(0, Number(line.quantity || 0) - Number(line.fulfilledQuantity || 0))
const fulfillmentPercentage = (line: ContractRecord['lines'][number]) => {
  const quantity = Number(line.quantity || 0)
  return quantity <= 0 ? 0 : Math.min(100, Math.round(Number(line.fulfilledQuantity || 0) / quantity * 100))
}
const statusType = (status: string) => status === 'ACTIVE' ? 'success' : status === 'CANCELLED' ? 'info' : status === 'REJECTED' ? 'danger' : status === 'SUBMITTED' ? 'warning' : 'primary'
const alertsFor = (contractId: string) => alerts.value.find((item) => item.contractId === String(contractId))?.alertTypes || []
const alertText = (type: string) => type === 'CONTRACT_EXPIRING' ? t('contractPage.expiringAlert') : t('contractPage.lowExecutionAlert')

const loadOptions = async () => {
  try {
    const [customerPage, supplierPage, productPage] = await Promise.all([
      getCustomers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
      getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }),
      getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
    ])
    customers.value = customerPage.records; suppliers.value = supplierPage.records; products.value = productPage.records
  } catch { ElMessage.error(t('contractPage.message.optionsFailed')) }
}

const loadData = async () => { loading.value = true; try { const page = await getContracts(query); rows.value = page.records; total.value = page.total } catch { ElMessage.error(t('contractPage.message.loadFailed')) } finally { loading.value = false } }
const resetQuery = () => { query.pageNo = 1; query.keyword = ''; query.contractType = ''; query.status = ''; loadData() }
const resetForm = () => { editingId.value = null; form.contractType = 'SALES'; form.customerId = ''; form.supplierId = undefined; form.contractName = ''; form.signedDate = formatBusinessDate(); form.effectiveFrom = formatBusinessDate(); form.effectiveTo = ''; form.remark = ''; form.lines = [{ productId: '', quantity: 1, unitPrice: 0 }] }
const addLine = () => form.lines.push({ productId: '', quantity: 1, unitPrice: 0 })
const removeLine = (index: number) => { if (form.lines.length > 1) form.lines.splice(index, 1) }
const openCreate = async () => { await loadOptions(); resetForm(); formVisible.value = true }
const openEdit = async (row: ContractRecord) => { await loadOptions(); try { const detail = await getContract(row.id); editingId.value = detail.id; Object.assign(form, { contractType: detail.contractType, customerId: detail.customerId || '', supplierId: detail.supplierId, contractName: detail.contractName, signedDate: detail.signedDate, effectiveFrom: detail.effectiveFrom, effectiveTo: detail.effectiveTo || '', remark: detail.remark || '', lines: detail.lines.map((line) => ({ productId: line.productId, quantity: line.quantity, unitPrice: line.unitPrice })) }); formVisible.value = true } catch { ElMessage.error(t('contractPage.message.detailFailed')) } }
const openDetail = async (row: ContractRecord) => { try { selected.value = await getContract(row.id); const [attachmentPage, versionRows] = await Promise.all([getContractAttachments(row.id), getContractVersions(row.id)]); attachments.value = attachmentPage.records; versions.value = versionRows; detailVisible.value = true } catch { ElMessage.error(t('contractPage.message.detailFailed')) } }
const restoreVersionRow = async (row: ContractVersionRecord) => { if (!selected.value) return; try { await ElMessageBox.confirm(t('contractPage.message.confirmRestore'), t('contractPage.message.prompt'), { type: 'warning' }); await restoreContractVersion(selected.value.id, row.id); ElMessage.success(t('contractPage.message.restored')); detailVisible.value = false; await loadData() } catch (error: any) { if (error !== 'cancel' && error !== 'close') ElMessage.error(t('contractPage.message.restoreFailed')) } }
const handleAttachmentUpload = async (event: Event) => { const file = (event.target as HTMLInputElement).files?.[0]; if (!file || !selected.value) return; try { await uploadContractAttachment(selected.value.id, file); attachments.value = (await getContractAttachments(selected.value.id)).records; ElMessage.success(t('contractPage.message.attachmentUploaded')) } catch { ElMessage.error(t('contractPage.message.attachmentFailed')) } finally { if (attachmentInput.value) attachmentInput.value.value = '' } }
const downloadAttachmentRow = async (row: Attachment) => { try { const blob = await downloadContractAttachment(selected.value!.id, row.id); downloadBlob(blob, row.originalFilename || `attachment-${row.id}`) } catch { ElMessage.error(t('contractPage.message.attachmentFailed')) } }
const deleteAttachmentRow = async (row: Attachment) => { try { await deleteContractAttachment(selected.value!.id, row.id); attachments.value = attachments.value.filter((item) => item.id !== row.id) } catch { ElMessage.error(t('contractPage.message.attachmentFailed')) } }
const save = async () => { if (!form.contractName.trim() || !(form.customerId || form.supplierId) || !form.signedDate || !form.effectiveFrom || form.lines.some((line) => !line.productId || line.quantity <= 0 || line.unitPrice < 0)) { ElMessage.warning(t('contractPage.message.completeForm')); return } saving.value = true; try { const payload: ContractSaveRequest = { ...form, customerId: form.contractType === 'SALES' ? form.customerId : undefined, supplierId: form.contractType === 'PURCHASE' ? form.supplierId : undefined, lines: form.lines }; if (editingId.value) await updateContract(editingId.value, payload); else await createContract(payload); ElMessage.success(t('contractPage.message.saved')); formVisible.value = false; await loadData() } catch { ElMessage.error(t('contractPage.message.saveFailed')) } finally { saving.value = false } }
const runAction = async (action: (id: string) => Promise<unknown>, row: ContractRecord, actionLabelKey: string) => { try { await ElMessageBox.confirm(t('contractPage.message.confirmAction', { action: t(actionLabelKey), name: row.contractName }), t('contractPage.message.prompt'), { type: 'warning' }); await action(row.id); ElMessage.success(t('contractPage.message.actionDone')); await loadData() } catch (error: any) { if (error !== 'cancel' && error !== 'close') ElMessage.error(t('contractPage.message.actionFailed')) } }
const handleExport = async () => { try { const blob = await exportContracts(query); downloadBlob(blob, t('contractPage.fileName')); ElMessage.success(t('contractPage.message.exported')) } catch { ElMessage.error(t('contractPage.message.actionFailed')) } }
onMounted(async () => { await loadOptions(); await loadData() })
const loadAlerts = async () => { try { alerts.value = await getContractAlerts() } catch { alerts.value = [] } }
onMounted(loadAlerts)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.toolbar, .line-toolbar { display: flex; align-items: center; justify-content: space-between; }
.line-toolbar { margin: 8px 0; }
.pager { margin-top: 12px; justify-content: flex-end; }
.detail-table { margin-top: 16px; }
.version-card { display: block !important; }
</style>
