<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-form :inline="true" :model="query">
          <el-form-item :label="t('purchaseRequisition.keyword')"><el-input v-model="query.keyword" clearable @keyup.enter="loadData" /></el-form-item>
          <el-form-item :label="t('purchaseRequisition.status')">
            <el-select v-model="query.status" clearable style="width:140px">
              <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadData">{{ t('purchaseRequisition.search') }}</el-button>
            <el-button v-permission="'purchase:requisition:manage'" type="success" @click="openCreate">{{ t('purchaseRequisition.create') }}</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="requisitionNo" :label="t('purchaseRequisition.no')" min-width="150" />
        <el-table-column prop="requisitionDate" :label="t('purchaseRequisition.date')" width="120" />
        <el-table-column prop="status" :label="t('purchaseRequisition.status')" width="120" />
        <el-table-column prop="approvalStatus" :label="t('purchaseRequisition.approvalStatus')" width="130" />
        <el-table-column prop="convertedOrderNo" :label="t('purchaseRequisition.convertedPo')" min-width="140" />
        <el-table-column prop="remark" :label="t('purchaseRequisition.remark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('purchaseRequisition.actions')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('purchaseRequisition.view') }}</el-button>
            <el-button v-if="['DRAFT','REJECTED'].includes(row.status)" v-permission="'purchase:requisition:manage'" link type="primary" @click="openEdit(row)">{{ t('purchaseRequisition.edit') }}</el-button>
            <el-button v-if="['DRAFT','REJECTED'].includes(row.status)" v-permission="'purchase:requisition:manage'" link type="success" @click="act(row,'submit')">{{ t('purchaseRequisition.submit') }}</el-button>
            <el-button v-if="row.status==='SUBMITTED'" v-permission="'purchase:requisition:manage'" link type="success" @click="act(row,'approve')">{{ t('purchaseRequisition.approve') }}</el-button>
            <el-button v-if="row.status==='SUBMITTED'" v-permission="'purchase:requisition:manage'" link type="warning" @click="act(row,'reject')">{{ t('purchaseRequisition.reject') }}</el-button>
            <el-button v-if="row.status==='APPROVED'" v-permission="'purchase:requisition:manage'" link type="primary" @click="act(row,'convert')">{{ t('purchaseRequisition.convert') }}</el-button>
            <el-button v-if="!['CONVERTED','CANCELLED'].includes(row.status)" v-permission="'purchase:requisition:manage'" link type="danger" @click="act(row,'cancel')">{{ t('purchaseRequisition.cancel') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? t('purchaseRequisition.editTitle') : t('purchaseRequisition.createTitle')" width="760px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item :label="t('purchaseRequisition.date')"><el-date-picker v-model="form.requisitionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="t('purchaseRequisition.neededDate')"><el-date-picker v-model="form.neededDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="t('purchaseRequisition.supplier')">
          <el-select v-model="form.supplierId" clearable filterable style="width:100%">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName || s.name" :value="String(s.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('purchaseRequisition.remark')"><el-input v-model="form.remark" /></el-form-item>
        <el-form-item :label="t('purchaseRequisition.lines')">
          <div style="width:100%">
            <el-button size="small" @click="addLine">{{ t('purchaseRequisition.addLine') }}</el-button>
            <el-table :data="form.lines" border style="margin-top:8px">
              <el-table-column :label="t('purchaseRequisition.product')" min-width="220">
                <template #default="{ row }">
                  <el-select v-model="row.productId" filterable style="width:100%">
                    <el-option v-for="p in products" :key="p.id" :label="`${p.productCode} ${p.productName}`" :value="String(p.id)" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="t('purchaseRequisition.qty')" width="140">
                <template #default="{ row }"><el-input-number v-model="row.qty" :min="0.0001" :controls="false" style="width:100%" /></template>
              </el-table-column>
              <el-table-column width="80">
                <template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index,1)">{{ t('purchaseRequisition.delete') }}</el-button></template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">{{ t('purchaseRequisition.close') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('purchaseRequisition.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { approvePurchaseRequisition, cancelPurchaseRequisition, convertPurchaseRequisition, createPurchaseRequisition, getPurchaseRequisition, getPurchaseRequisitions, rejectPurchaseRequisition, submitPurchaseRequisition, updatePurchaseRequisition, type PurchaseRequisition } from '@/api/purchase'
import { getProducts, getSuppliers, type Product, type Supplier } from '@/api/masterdata'
import { formatBusinessDate } from '@/utils/locale'
const { t } = useI18n()
const statuses=['DRAFT','SUBMITTED','APPROVED','REJECTED','CONVERTED','CANCELLED']
const loading=ref(false); const saving=ref(false); const rows=ref<PurchaseRequisition[]>([]); const products=ref<Product[]>([]); const suppliers=ref<Supplier[]>([])
const dialogVisible=ref(false); const editingId=ref<string|number|null>(null)
const query=reactive({keyword:'',status:'',pageNo:1,pageSize:20})
const form=reactive<{requisitionDate:string;neededDate:string;supplierId:string;remark:string;lines:Array<{productId:string;qty:number}>}>({requisitionDate:formatBusinessDate(), neededDate:'', supplierId:'', remark:'', lines:[]})
const loadData=async()=>{ loading.value=true; try{ const page=await getPurchaseRequisitions(query); rows.value=page.records||[] } finally{ loading.value=false } }
const loadOptions=async()=>{ const [p,s]=await Promise.all([getProducts({pageNo:1,pageSize:200,status:'ACTIVE'}), getSuppliers({pageNo:1,pageSize:200,status:'ACTIVE'})]); products.value=p.records||[]; suppliers.value=s.records||[] }
const openCreate=async()=>{ await loadOptions(); editingId.value=null; form.requisitionDate=formatBusinessDate(); form.neededDate=''; form.supplierId=''; form.remark=''; form.lines=[{productId:'',qty:1}]; dialogVisible.value=true }
const openEdit=async(row:PurchaseRequisition)=>{ await loadOptions(); const detail=await getPurchaseRequisition(row.id); editingId.value=detail.id; form.requisitionDate=detail.requisitionDate; form.neededDate=detail.neededDate||''; form.supplierId=detail.supplierId?String(detail.supplierId):''; form.remark=detail.remark||''; form.lines=(detail.lines||[]).map(l=>({productId:String(l.productId), qty:Number(l.qty||1)})); dialogVisible.value=true }
const openDetail=async(row:PurchaseRequisition)=>{ const detail=await getPurchaseRequisition(row.id); ElMessage.info(`${detail.requisitionNo} / ${detail.status} / lines=${detail.lines?.length||0}`) }
const addLine=()=>form.lines.push({productId:'',qty:1})
const save=async()=>{ if(!form.requisitionDate || !form.lines.length || form.lines.some(l=>!l.productId)){ ElMessage.warning(t('purchaseRequisition.validation.required')); return } saving.value=true; try{ const payload={ requisitionDate:form.requisitionDate, neededDate:form.neededDate||null, supplierId:form.supplierId||null, remark:form.remark||undefined, lines: form.lines.map(l=>({productId:l.productId, qty:l.qty})) }; if(editingId.value){ await updatePurchaseRequisition(editingId.value, payload); ElMessage.success(t('purchaseRequisition.message.saved')) } else { await createPurchaseRequisition(payload); ElMessage.success(t('purchaseRequisition.message.created')) } dialogVisible.value=false; await loadData() } finally{ saving.value=false } }
const act=async(row:PurchaseRequisition, type:string)=>{ const map:any={submit:submitPurchaseRequisition,approve:approvePurchaseRequisition,reject:rejectPurchaseRequisition,cancel:cancelPurchaseRequisition,convert:convertPurchaseRequisition}; await map[type](row.id); ElMessage.success(t('purchaseRequisition.message.done')); await loadData() }
onMounted(loadData)
</script>
<style scoped>.page{display:flex;flex-direction:column;gap:12px}.toolbar{display:flex;justify-content:space-between}</style>
