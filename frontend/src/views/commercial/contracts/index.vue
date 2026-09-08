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
        <el-table-column :label="$t('contractPage.type')" width="110"><template #default="{ row }">{{ contractTypeText(row.contractType) }}</template></el-table-column>
        <el-table-column :label="$t('contractPage.partner')" min-width="160"><template #default="{ row }">{{ partnerName(row) }}</template></el-table-column>
        <el-table-column prop="signedDate" :label="$t('contractPage.signedDate')" width="125"><template #default="{ row }">{{ formatDate(row.signedDate) }}</template></el-table-column>
        <el-table-column :label="$t('contractPage.effectivePeriod')" width="220"><template #default="{ row }">{{ effectivePeriodText(row.effectiveFrom, row.effectiveTo) }}</template></el-table-column>
        <el-table-column prop="totalAmount" :label="$t('contractPage.totalAmount')" width="140" align="right"><template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template></el-table-column>
        <el-table-column :label="$t('contractPage.status')" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template></el-table-column>
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
      <el-pagination class="pager" background layout="total, sizes, prev, pager, next" :total="total" :current-page="query.pageNo" :page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" @current-change="handlePageChange" @size-change="handleSizeChange" />
    </el-card>

    <el-dialog v-model="formVisible" :title="dialogTitle" width="1040px" destroy-on-close>
      <el-form label-width="105px">
        <el-row :gutter="12"><el-col :span="8"><el-form-item :label="$t('contractPage.type')"><el-select v-model="form.contractType" style="width: 100%"><el-option :label="$t('contractPage.sales')" value="SALES" /><el-option :label="$t('contractPage.purchase')" value="PURCHASE" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.customer')" v-if="form.contractType === 'SALES'"><el-select v-model="form.customerId" filterable style="width: 100%"><el-option v-for="item in customers" :key="item.id" :label="customerLabel(item)" :value="String(item.id)" /></el-select></el-form-item><el-form-item :label="$t('contractPage.supplier')" v-else><el-select v-model="form.supplierId" filterable style="width: 100%"><el-option v-for="item in suppliers" :key="item.id" :label="supplierLabel(item)" :value="String(item.id)" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.contractName')"><el-input v-model="form.contractName" /></el-form-item></el-col></el-row>
        <el-row :gutter="12"><el-col :span="8"><el-form-item :label="$t('contractPage.signedDate')"><el-date-picker v-model="form.signedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.effectiveFrom')"><el-date-picker v-model="form.effectiveFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col><el-col :span="8"><el-form-item :label="$t('contractPage.effectiveTo')"><el-date-picker v-model="form.effectiveTo" type="date" value-format="YYYY-MM-DD" clearable style="width: 100%" /></el-form-item></el-col></el-row>
        <el-form-item :label="$t('contractPage.remark')"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
        <div class="line-toolbar"><b>{{ $t('contractPage.lines') }}</b><el-button link type="primary" :icon="Plus" @click="addLine">{{ $t('contractPage.addLine') }}</el-button></div>
        <el-table :data="form.lines" border size="small"><el-table-column :label="$t('contractPage.product')" min-width="300"><template #default="{ row }"><el-select v-model="row.productId" filterable style="width: 100%"><el-option v-for="item in products" :key="item.id" :label="productLabel(item)" :value="String(item.id)" /></el-select></template></el-table-column><el-table-column :label="$t('contractPage.quantity')" width="145"><template #default="{ row }"><el-input-number v-model="row.quantity" :min="0.0001" :precision="4" :controls="false" style="width: 100%" /></template></el-table-column><el-table-column :label="$t('contractPage.unitPrice')" width="145"><template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" :controls="false" style="width: 100%" /></template></el-table-column><el-table-column :label="$t('contractPage.amount')" width="135" align="right"><template #default="{ row }">{{ lineAmount(row) }}</template></el-table-column><el-table-column width="80"><template #default="{ $index }"><el-button link type="danger" @click="removeLine($index)">{{ $t('contractPage.delete') }}</el-button></template></el-table-column></el-table>
      </el-form>
      <template #footer><el-button @click="formVisible = false">{{ $t('contractPage.cancel') }}</el-button><el-button type="primary" :loading="saving" @click="save">{{ $t('contractPage.save') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="$t('contractPage.detailTitle')" width="1160px" @closed="resetDetail"><el-descriptions v-if="selected" :column="3" border><el-descriptions-item :label="$t('contractPage.contractNo')">{{ selected.contractNo }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.contractName')">{{ selected.contractName }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.partner')">{{ partnerName(selected) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.signedDate')">{{ formatDate(selected.signedDate) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.effectivePeriod')">{{ effectivePeriodText(selected.effectiveFrom, selected.effectiveTo) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.totalAmount')">{{ formatMoney(selected.totalAmount) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.remark')" :span="3">{{ selected.remark || '-' }}</el-descriptions-item></el-descriptions><el-table v-if="selected" :data="selected.lines" border stripe class="detail-table"><el-table-column prop="productCode" :label="$t('contractPage.product')" min-width="240"><template #default="{ row }">{{ lineProductLabel(row) }}</template></el-table-column><el-table-column prop="quantity" :label="$t('contractPage.quantity')" width="110" /><el-table-column prop="committedQuantity" :label="$t('contractPage.committedQuantity')" width="125" /><el-table-column :label="$t('contractPage.availableOrderQuantity')" width="125"><template #default="{ row }">{{ availableOrderQuantity(row) }}</template></el-table-column><el-table-column prop="fulfilledQuantity" :label="$t('contractPage.fulfilledQuantity')" width="110" /><el-table-column :label="$t('contractPage.availableFulfillmentQuantity')" width="135"><template #default="{ row }">{{ availableFulfillmentQuantity(row) }}</template></el-table-column><el-table-column :label="$t('contractPage.fulfillmentProgress')" min-width="170"><template #default="{ row }"><el-progress :percentage="fulfillmentPercentage(row)" :stroke-width="10" :format="(percentage: number) => `${percentage}%`" /></template></el-table-column><el-table-column prop="unitPrice" :label="$t('contractPage.unitPrice')" width="110" /><el-table-column prop="amount" :label="$t('contractPage.amount')" width="120" /></el-table><div class="attachment-panel"><div class="attachment-header"><b>{{ $t('contractPage.attachments') }}</b><input ref="attachmentInput" type="file" hidden @change="handleAttachmentPick" /><el-button v-permission="'contract:manage'" size="small" type="primary" @click="attachmentInput?.click()">{{ $t('contractPage.uploadAttachment') }}</el-button></div><el-table :data="attachments" size="small" border><el-table-column prop="originalFilename" :label="$t('contractPage.filename')" /><el-table-column prop="fileSize" :label="$t('contractPage.fileSize')" width="100" /><el-table-column prop="createdTime" :label="$t('contractPage.uploadedAt')" width="180" /><el-table-column width="180"><template #default="{ row }"><el-button link type="primary" @click="downloadAttachment(row)">{{ $t('contractPage.downloadAttachment') }}</el-button><el-button v-permission="'contract:manage'" link type="danger" @click="deleteAttachment(row)">{{ $t('contractPage.deleteAttachment') }}</el-button></template></el-table-column></el-table></div><div class="version-panel"><b>{{ $t('contractPage.versions') }}</b><el-table :data="versions" size="small" border :empty-text="$t('contractPage.versionEmpty')"><el-table-column prop="versionNo" :label="$t('contractPage.versionNo')" width="80" /><el-table-column :label="$t('contractPage.versionEvent')" width="130"><template #default="{ row }">{{ versionEventText(row.eventType) }}</template></el-table-column><el-table-column :label="$t('contractPage.status')" width="110"><template #default="{ row }">{{ statusText(row.status) }}</template></el-table-column><el-table-column :label="$t('contractPage.versionTime')" width="180"><template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template></el-table-column><el-table-column :label="$t('contractPage.changedFields')" min-width="200"><template #default="{ row }">{{ changedFieldsText(row.changedFields) }}</template></el-table-column><el-table-column width="200"><template #default="{ row }"><el-button link type="primary" @click="openVersion(row)">{{ $t('contractPage.viewVersion') }}</el-button><el-button v-permission="'contract:manage'" link type="primary" @click="restoreVersion(row)">{{ $t('contractPage.restoreVersion') }}</el-button></template></el-table-column></el-table></div></el-dialog>

    <el-dialog v-model="versionVisible" :title="$t('contractPage.versionSnapshotTitle')" width="1040px" append-to-body @closed="resetVersion"><el-descriptions v-if="versionSnapshot" :column="3" border><el-descriptions-item :label="$t('contractPage.versionNo')">{{ versionSnapshot.versionNo }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.versionEvent')">{{ versionEventText(versionSnapshot.eventType) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.status')">{{ statusText(versionSnapshot.status) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.contractNo')">{{ versionSnapshot.header.contractNo }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.contractName')">{{ versionSnapshot.header.contractName }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.type')">{{ contractTypeText(versionSnapshot.header.contractType) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.signedDate')">{{ formatDate(versionSnapshot.header.signedDate) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.effectivePeriod')">{{ effectivePeriodText(versionSnapshot.header.effectiveFrom, versionSnapshot.header.effectiveTo) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.totalAmount')">{{ formatMoney(versionSnapshot.header.totalAmount) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.versionTime')">{{ formatDateTime(versionSnapshot.createdTime) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.versionCreatedBy')">{{ versionSnapshot.createdBy || '-' }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.changedFields')">{{ changedFieldsText(versionSnapshot.changedFields) }}</el-descriptions-item><el-descriptions-item :label="$t('contractPage.remark')" :span="3">{{ versionSnapshot.header.remark || '-' }}</el-descriptions-item></el-descriptions><el-table v-if="versionSnapshot" :data="versionSnapshot.lines" border stripe size="small" class="detail-table"><el-table-column prop="lineNo" :label="$t('contractPage.lineNo')" width="80" /><el-table-column :label="$t('contractPage.product')" min-width="240"><template #default="{ row }">{{ productLabelById(row.productId) }}</template></el-table-column><el-table-column prop="quantity" :label="$t('contractPage.quantity')" width="110" align="right" /><el-table-column prop="fulfilledQuantity" :label="$t('contractPage.fulfilledQuantity')" width="110" align="right" /><el-table-column :label="$t('contractPage.unitPrice')" width="120" align="right"><template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template></el-table-column><el-table-column :label="$t('contractPage.amount')" width="130" align="right"><template #default="{ row }">{{ formatMoney(row.amount) }}</template></el-table-column><el-table-column prop="remark" :label="$t('contractPage.remark')" min-width="150" show-overflow-tooltip /></el-table><template #footer><el-button @click="versionVisible = false">{{ $t('contractPage.cancel') }}</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { approveContract, cancelContract, closeContract, createContract, deleteContractAttachment, downloadContractAttachment, exportContracts, getContract, getContractAlerts, getContractAttachments, getContractVersion, getContractVersions, getContracts, rejectContract, restoreContractVersion, submitContract, updateContract, uploadContractAttachment } from '@/api/contracts'
import { getCustomers, getProducts, getSuppliers } from '@/api/masterdata'
import { downloadBlob } from '@/utils/download'
import { useContractDetail } from '@/composables/useContractDetail'
import { useContractForm } from '@/composables/useContractForm'
import { useContractList } from '@/composables/useContractList'
import { useContractPresentation } from '@/composables/useContractPresentation'

const { t } = useI18n()
const statuses = ['DRAFT', 'SUBMITTED', 'REJECTED', 'ACTIVE', 'CLOSED', 'CANCELLED']

const confirm = (message: string, title: string, options?: { type?: string }) => ElMessageBox.confirm(message, title, options as any)
const onError = (message: string) => ElMessage.error(message)
const onSuccess = (message: string) => ElMessage.success(message)
const onWarning = (message: string) => ElMessage.warning(message)

const { alerts, customers, handleExport, handlePageChange, handleSizeChange, loadAlerts, loadData, loadOptions, loading, products, query, resetQuery, rows, runAction, suppliers, total } = useContractList(t, { getContracts, getContractAlerts, exportContracts, getCustomers, getSuppliers, getProducts, downloadBlob, confirm, onError, onSuccess })

const { alertText, alertsFor, availableFulfillmentQuantity, availableOrderQuantity, changedFieldsText, contractTypeText, customerLabel, effectivePeriodText, formatDate, formatDateTime, formatMoney, fulfillmentPercentage, lineAmount, lineProductLabel, partnerName, productLabel, productLabelById, statusText, statusType, supplierLabel, versionEventText } = useContractPresentation(t, { alerts, products })

const reload = async () => { await loadData() }

const { addLine, dialogTitle, form, formVisible, openCreate, openEdit, removeLine, save, saving } = useContractForm(t, { getContract, createContract, updateContract, ensureOptions: loadOptions, onError, onSuccess, onWarning, onSubmitted: reload })

const { attachments, deleteAttachment, detailVisible, downloadAttachment, openDetail, openVersion, resetDetail, resetVersion, restoreVersion, selected, uploadAttachment, versionSnapshot, versionVisible, versions } = useContractDetail(t, { getContract, getContractAttachments, getContractVersions, getContractVersion, restoreContractVersion, uploadContractAttachment, downloadContractAttachment, deleteContractAttachment, downloadBlob, confirm, onError, onSuccess, onRestored: reload })

const attachmentInput = ref<HTMLInputElement>()

/** The hidden input belongs to the page, so it clears itself after every pick. */
const handleAttachmentPick = async (event: Event) => {
  const input = event.target as HTMLInputElement
  await uploadAttachment(input.files?.[0])
  input.value = ''
}

onMounted(async () => { await loadOptions(); await Promise.all([loadData(), loadAlerts()]) })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.toolbar, .line-toolbar { display: flex; align-items: center; justify-content: space-between; }
.line-toolbar { margin: 8px 0; }
.pager { margin-top: 12px; justify-content: flex-end; }
.detail-table { margin-top: 16px; }
.version-panel { margin-top: 16px; }
</style>
