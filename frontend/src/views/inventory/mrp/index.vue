<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <div><b>{{ t('inventoryMrp.title') }}</b><p class="tip">{{ t('inventoryMrp.description') }}</p></div>
        <div class="actions">
          <el-button @click="loadHistory">{{ t('inventoryMrp.history') }}</el-button>
          <el-button v-permission="'inventory:mrp:run'" type="primary" :loading="loading" :disabled="!!convertingId" @click="runMrp">
            {{ t('inventoryMrp.run') }}
          </el-button>
        </div>
      </div>
      <p class="tip">{{ t('inventoryMrp.scopeHint') }}</p>
    </el-card>

    <el-alert v-if="result" :title="t('inventoryMrp.summary', { date: result.asOfDate, purchaseCount: result.purchaseCount,
      productionCount: result.productionCount }) + (result.runNo ? ` · ${result.runNo}` : '')" type="success" :closable="false" show-icon />
    <el-alert v-if="legacy" :title="t('inventoryMrp.legacy')" type="warning" :closable="false" show-icon />
    <el-card v-if="warnings.length" shadow="never">
      <el-alert :title="t('inventoryMrp.warnings')" type="warning" :closable="false" show-icon />
      <el-table :data="warnings" class="warning-table">
        <el-table-column prop="documentNo" :label="t('inventoryMrp.document')" min-width="180" />
        <el-table-column :label="t('inventoryMrp.productName')" min-width="180">
          <template #default="{ row }">{{ nodes.find(n => String(n.productId) === String(row.productId))?.productName || row.productId }}</template>
        </el-table-column>
        <el-table-column prop="qty" :label="t('inventoryMrp.quantity')" width="120" />
        <el-table-column :label="t('inventoryMrp.actions')" width="180">
          <template #default="{ row }">
            <el-button v-permission="'purchase:order:update'" link type="primary" @click="openWarehouseAssignment(row)">{{ t('inventoryMrp.assignWarehouse') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div v-if="result" class="filters">
      <el-select v-model="warehouseFilter" clearable :placeholder="t('inventoryMrp.allWarehouses')" style="width: 240px">
        <el-option v-for="warehouse in planWarehouses" :key="warehouse.id" :value="warehouse.id" :label="warehouse.name" />
      </el-select>
      <el-checkbox v-if="nodes.length" v-model="showAllNodes">{{ t('inventoryMrp.allCalculations') }}</el-checkbox>
    </div>

    <el-card shadow="never">
      <template #header>{{ t('inventoryMrp.productionSuggestions') }}</template>
      <MrpSuggestionTable :lines="filterLines(result?.productionLines || [])" :blocked="blocked" :converting-id="convertingId"
        @explain="explainLine" @convert="openConversion" />
    </el-card>
    <el-card shadow="never">
      <template #header>{{ t('inventoryMrp.purchaseSuggestions') }}</template>
      <MrpSuggestionTable :lines="filterLines(result?.purchaseLines || [])" :blocked="blocked" :converting-id="convertingId"
        @explain="explainLine" @convert="openConversion" />
    </el-card>
    <el-card v-if="showAllNodes" shadow="never">
      <template #header>{{ t('inventoryMrp.calculations') }}</template>
      <el-table :data="nodes.filter(inWarehouse)" border max-height="420">
        <el-table-column prop="productCode" :label="t('inventoryMrp.productCode')" width="120" />
        <el-table-column prop="productName" :label="t('inventoryMrp.productName')" min-width="160" />
        <el-table-column prop="warehouseName" :label="t('inventoryMrp.warehouse')" min-width="150" />
        <el-table-column prop="level" :label="t('inventoryMrp.level')" width="100" />
        <el-table-column prop="demandQty" :label="t('inventoryMrp.demandQty')" width="110" />
        <el-table-column prop="netQty" :label="t('inventoryMrp.netQty')" width="110" />
        <el-table-column :label="t('inventoryMrp.actions')" width="120">
          <template #default="{ row }"><el-button link type="primary" @click="explainNode(row)">{{ t('inventoryMrp.explain') }}</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>{{ t('inventoryMrp.historyTitle') }}</template>
      <el-table v-loading="historyLoading" :data="history" border stripe>
        <el-table-column prop="runNo" :label="t('inventoryMrp.runNo')" min-width="170" />
        <el-table-column prop="asOfDate" :label="t('inventoryMrp.asOfDate')" width="130" />
        <el-table-column prop="status" :label="t('inventoryMrp.status')" width="110" />
        <el-table-column prop="purchaseCount" :label="t('inventoryMrp.purchaseCount')" width="120" align="right" />
        <el-table-column prop="productionCount" :label="t('inventoryMrp.productionCount')" width="120" align="right" />
        <el-table-column prop="createdTime" :label="t('inventoryMrp.createdTime')" min-width="180" />
        <el-table-column :label="t('inventoryMrp.actions')" width="100" fixed="right">
          <template #default="{ row }"><el-button link type="primary" :disabled="!!convertingId" @click="openRun(row.id)">{{ t('inventoryMrp.open') }}</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="historyPage" :page-size="20" :total="historyTotal" layout="prev, pager, next, total" @current-change="loadHistory" />
    </el-card>

    <el-drawer v-model="traceVisible" :title="t('inventoryMrp.explain')" size="min(980px, 96vw)" destroy-on-close>
      <template v-if="traceNode">
        <el-button v-if="traceStack.length > 1" @click="traceStack.pop()">{{ t('inventoryMrp.back') }}</el-button>
        <h3>{{ traceNode.productCode }} · {{ traceNode.productName }} · {{ traceNode.warehouseName }}</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('inventoryMrp.level')">{{ traceNode.level }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.materialWarehouse')">{{ traceNode.materialWarehouseName || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.bom')">{{ traceNode.bomNo || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.bomVersion')">{{ traceNode.bomVersion ?? '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.demandQty')">{{ traceNode.demandQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.onHandQty')">{{ traceNode.onHandQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.purchaseSupply')">{{ traceNode.purchaseSupplyQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.productionSupply')">{{ traceNode.productionSupplyQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.plannedSupply')">{{ traceNode.plannedSupplyQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.netQty')"><strong>{{ traceNode.netQty }}</strong></el-descriptions-item>
        </el-descriptions>
        <p class="formula">max({{ traceNode.demandQty }} − {{ traceNode.onHandQty }} − {{ traceNode.purchaseSupplyQty }} − {{ traceNode.productionSupplyQty }} − {{ traceNode.plannedSupplyQty }}, 0) = {{ traceNode.netQty }}</p>
        <p class="tip">{{ t('inventoryMrp.formulaHint') }}</p>
        <h4>{{ t('inventoryMrp.sources') }}</h4>
        <el-table :data="traceNode.sources" border :empty-text="t('inventoryMrp.noSources')">
          <el-table-column :label="t('inventoryMrp.sourceType')" width="140"><template #default="{ row }">{{ sourceType(row.type) }}</template></el-table-column>
          <el-table-column :label="t('inventoryMrp.document')" min-width="170"><template #default="{ row }">{{ row.documentNo || row.documentId || '—' }}</template></el-table-column>
          <el-table-column prop="lineId" :label="t('inventoryMrp.sourceLine')" min-width="160" />
          <el-table-column prop="qty" :label="t('inventoryMrp.quantity')" width="100" />
          <el-table-column :label="t('inventoryMrp.parent')" min-width="140">
            <template #default="{ row }"><el-button v-if="findParent(row)" link type="primary" @click="traceStack.push(findParent(row)!)">{{ t('inventoryMrp.viewParent') }} · {{ findParent(row)?.productCode }}</el-button></template>
          </el-table-column>
        </el-table>
        <p v-for="(source, index) in traceNode.sources.filter(s => s.type === 'BOM')" :key="index" class="formula">
          {{ source.documentNo }}: {{ source.parentNetQty }} × {{ source.qtyPer }} / {{ source.baseQty }} × (1 + {{ source.lossRate }}) = {{ source.qty }}
        </p>
        <h4>{{ t('inventoryMrp.supplies') }}</h4>
        <el-table :data="traceNode.supplies" border>
          <el-table-column :label="t('inventoryMrp.sourceType')" min-width="170"><template #default="{ row }">{{ sourceType(row.type) }}</template></el-table-column>
          <el-table-column prop="documentNo" :label="t('inventoryMrp.document')" min-width="170" />
          <el-table-column prop="qty" :label="t('inventoryMrp.quantity')" width="120" />
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="conversionVisible" :title="t('inventoryMrp.convertTitle')" width="580px" :close-on-click-modal="false">
      <template v-if="conversionLine">
        <el-alert :title="t('inventoryMrp.convertHint')" type="info" :closable="false" />
        <el-descriptions :column="1" border class="dialog-summary">
          <el-descriptions-item :label="t('inventoryMrp.productName')">{{ conversionLine.productCode }} · {{ conversionLine.productName }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.netQty')">{{ conversionLine.netQty }}</el-descriptions-item>
          <el-descriptions-item :label="t('inventoryMrp.warehouse')">{{ conversionLine.trace?.warehouseName }}</el-descriptions-item>
          <el-descriptions-item v-if="conversionLine.suggestionType === 'PRODUCTION'" :label="t('inventoryMrp.materialWarehouse')">{{ conversionLine.trace?.materialWarehouseName }}</el-descriptions-item>
          <el-descriptions-item v-if="conversionLine.bomId" :label="t('inventoryMrp.bom')">{{ conversionLine.trace?.bomNo }} · {{ t('inventoryMrp.bomVersion') }} {{ conversionLine.bomVersion }}</el-descriptions-item>
        </el-descriptions>
        <el-form v-if="conversionLine.suggestionType === 'PURCHASE'" label-position="top">
          <el-form-item :label="t('inventoryMrp.supplier')" required>
            <el-select v-model="supplierId" filterable remote :remote-method="loadSuppliers" :loading="suppliersLoading" :placeholder="t('inventoryMrp.selectSupplier')" style="width: 100%">
              <el-option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id" :label="`${supplier.supplierCode} · ${supplier.supplierName}`" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button :disabled="!!convertingId" @click="conversionVisible = false">{{ t('inventoryMrp.cancel') }}</el-button>
        <el-button type="primary" :loading="!!convertingId" :disabled="conversionLine?.suggestionType === 'PURCHASE' && !supplierId" @click="confirmConversion">{{ t('inventoryMrp.confirmConvert') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignmentVisible" :title="t('inventoryMrp.assignWarehouse')" width="480px">
      <p>{{ assignmentOrder?.documentNo }}</p>
      <el-form label-position="top"><el-form-item :label="t('inventoryMrp.plannedWarehouse')" required>
        <WarehouseSelect v-if="assignmentVisible" v-model="assignmentWarehouseId" :placeholder="t('inventoryMrp.selectWarehouse')" />
      </el-form-item></el-form>
      <template #footer><el-button type="primary" :loading="assigning" :disabled="!assignmentWarehouseId" @click="saveWarehouse">{{ t('inventoryMrp.save') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { convertMrpLine, getMrpRun, getMrpRuns, runMrpPlan, type MrpRunResult, type MrpRunSummary, type MrpSuggestionLine, type MrpNodeTrace, type MrpSource, type MrpWarning } from '@/api/inventory'
import { getSuppliers, type Supplier } from '@/api/masterdata'
import { assignPurchaseOrderWarehouse } from '@/api/purchase'
import WarehouseSelect from '@/components/WarehouseSelect.vue'
import MrpSuggestionTable from './MrpSuggestionTable.vue'

const { t } = useI18n()
const loading = ref(false)
const historyLoading = ref(false)
const convertingId = ref<string | null>(null)
const result = ref<MrpRunResult>()
const history = ref<MrpRunSummary[]>([])
const historyPage = ref(1)
const historyTotal = ref(0)
const warehouseFilter = ref('')
const showAllNodes = ref(false)
const nodes = computed(() => result.value?.calculationSnapshot?.nodes || [])
const warnings = computed(() => result.value?.calculationSnapshot?.warnings || [])
const legacy = computed(() => !!result.value && (result.value.algorithmVersion !== 2 || !result.value.calculationSnapshot))
const blocked = computed(() => legacy.value || warnings.value.length > 0 || loading.value || !!convertingId.value)
const planWarehouses = computed(() => [...new Map(nodes.value.map(n => [String(n.warehouseId), { id: String(n.warehouseId), name: n.warehouseName }])).values()])
const inWarehouse = (row: { warehouseId?: string | number | null }) => !warehouseFilter.value || String(row.warehouseId) === warehouseFilter.value
const filterLines = (rows: MrpSuggestionLine[]) => rows.filter(inWarehouse)
const setResult = (run: MrpRunResult) => { result.value = run; warehouseFilter.value = ''; traceVisible.value = false }

const runMrp = async () => {
  loading.value = true
  try { setResult(await runMrpPlan()); ElMessage.success(t('inventoryMrp.message.succeeded')); historyPage.value = 1; await loadHistory() }
  catch { /* API errors retain the actionable BOM or warehouse message. */ }
  finally { loading.value = false }
}
const loadHistory = async () => {
  historyLoading.value = true
  try { const page = await getMrpRuns({ pageNo: historyPage.value, pageSize: 20 }); history.value = page.records; historyTotal.value = Number(page.total) }
  catch { /* request interceptor shows the error */ }
  finally { historyLoading.value = false }
}
const openRun = async (id: string | number) => {
  try { setResult(await getMrpRun(id)) } catch { /* request interceptor shows the error */ }
}

const traceVisible = ref(false)
const traceStack = ref<MrpNodeTrace[]>([])
const traceNode = computed(() => traceStack.value[traceStack.value.length - 1])
const explainNode = (node: MrpNodeTrace) => { traceStack.value = [node]; traceVisible.value = true }
const explainLine = (line: MrpSuggestionLine) => { if (line.trace) explainNode(line.trace) }
const findParent = (source: MrpSource) => nodes.value.find(n => String(n.productId) === String(source.parentProductId) && String(n.warehouseId) === String(source.parentWarehouseId))
const sourceType = (type: string) => t(`inventoryMrp.sourceTypes.${type}`)

const conversionVisible = ref(false)
const conversionLine = ref<MrpSuggestionLine>()
const supplierId = ref<string>()
const suppliers = ref<Supplier[]>([])
const suppliersLoading = ref(false)
let supplierSearch = 0
const loadSuppliers = async (keyword = '') => {
  const version = ++supplierSearch
  suppliersLoading.value = true
  try { const page = await getSuppliers({ pageNo: 1, pageSize: 50, status: 'ACTIVE', keyword }); if (version === supplierSearch) suppliers.value = page.records }
  catch { /* request interceptor shows the error */ }
  finally { if (version === supplierSearch) suppliersLoading.value = false }
}
const openConversion = (line: MrpSuggestionLine) => {
  conversionLine.value = line; supplierId.value = undefined; conversionVisible.value = true
  if (line.suggestionType === 'PURCHASE') void loadSuppliers()
}
const confirmConversion = async () => {
  const runId = result.value?.id
  const line = conversionLine.value
  if (runId == null || line?.id == null || convertingId.value) return
  if (line.suggestionType === 'PURCHASE' && !supplierId.value) { ElMessage.warning(t('inventoryMrp.supplierRequired')); return }
  convertingId.value = String(line.id)
  try {
    const converted = await convertMrpLine(runId, line.id, line.suggestionType === 'PURCHASE' ? { supplierId: supplierId.value } : {})
    ElMessage.success(t(line.suggestionType === 'PURCHASE' ? 'inventoryMrp.message.convertedPo' : 'inventoryMrp.message.convertedMo', { orderNo: converted.convertedBizNo || '' }))
    conversionVisible.value = false; result.value = await getMrpRun(runId); await loadHistory()
  } catch { /* Keep the dialog and show the server's stale-plan explanation. */ }
  finally { convertingId.value = null }
}

const assignmentVisible = ref(false)
const assignmentOrder = ref<MrpWarning>()
const assignmentWarehouseId = ref<string | null>()
const assigning = ref(false)
const openWarehouseAssignment = (warning: MrpWarning) => { assignmentOrder.value = warning; assignmentWarehouseId.value = null; assignmentVisible.value = true }
const saveWarehouse = async () => {
  if (!assignmentOrder.value || !assignmentWarehouseId.value) return
  assigning.value = true
  try { await assignPurchaseOrderWarehouse(assignmentOrder.value.documentId, assignmentWarehouseId.value); assignmentVisible.value = false; ElMessage.success(t('inventoryMrp.assigned')) }
  catch { /* request interceptor shows the error */ }
  finally { assigning.value = false }
}
onMounted(loadHistory)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; padding: 16px; }
.toolbar, .actions, .filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.toolbar { justify-content: space-between; }
.tip { color: #606266; font-size: 13px; line-height: 1.7; margin: 8px 0 0; }
.formula { font-variant-numeric: tabular-nums; background: #f5f7fa; padding: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.dialog-summary, .warning-table { margin: 16px 0; }
.el-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
