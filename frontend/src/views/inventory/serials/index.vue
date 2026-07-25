<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item :label="t('inventorySerial.keyword')">
          <el-input v-model="query.keyword" clearable @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item :label="t('inventorySerial.status')">
          <el-select v-model="query.status" clearable style="width: 140px">
            <el-option label="IN_STOCK" value="IN_STOCK" />
            <el-option label="ISSUED" value="ISSUED" />
            <el-option label="SCRAPPED" value="SCRAPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ t('inventorySerial.search') }}</el-button>
          <el-button v-permission="'inventory:serial:manage'" type="success" @click="openCreate">{{ t('inventorySerial.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="serialNo" :label="t('inventorySerial.serialNo')" min-width="160" />
        <el-table-column prop="productCode" :label="t('inventorySerial.productCode')" width="120" />
        <el-table-column prop="productName" :label="t('inventorySerial.productName')" min-width="140" />
        <el-table-column prop="status" :label="t('inventorySerial.status')" width="110" />
        <el-table-column prop="inboundBizNo" :label="t('inventorySerial.inboundBizNo')" min-width="140" />
        <el-table-column prop="outboundBizNo" :label="t('inventorySerial.outboundBizNo')" min-width="140" />
        <el-table-column :label="t('inventorySerial.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status==='IN_STOCK'" v-permission="'inventory:serial:manage'" link type="primary" @click="issue(row)">{{ t('inventorySerial.issue') }}</el-button>
            <el-button v-if="row.status==='IN_STOCK'" v-permission="'inventory:serial:manage'" link type="warning" @click="scrap(row)">{{ t('inventorySerial.scrap') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="t('inventorySerial.createTitle')" width="520px">
      <el-form label-width="110px">
        <el-form-item :label="t('inventorySerial.product')">
          <el-select v-model="form.productId" filterable style="width:100%">
            <el-option v-for="p in products" :key="p.id" :label="`${p.productCode} ${p.productName}`" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('inventorySerial.serialNo')"><el-input v-model="form.serialNo" /></el-form-item>
        <el-form-item :label="t('inventorySerial.inboundBizNo')"><el-input v-model="form.inboundBizNo" /></el-form-item>
        <el-form-item :label="t('inventorySerial.remark')"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">{{ t('inventorySerial.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('inventorySerial.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { createInventorySerial, getInventorySerials, issueInventorySerial, scrapInventorySerial, type InventorySerial } from '@/api/inventory'
import { getProducts, type Product } from '@/api/masterdata'
const { t } = useI18n()
const loading=ref(false); const saving=ref(false); const rows=ref<InventorySerial[]>([]); const products=ref<Product[]>([]); const dialogVisible=ref(false)
const query=reactive({keyword:'',status:'',pageNo:1,pageSize:50})
const form=reactive({productId:'',serialNo:'',inboundBizNo:'',remark:''})
const loadData=async()=>{ loading.value=true; try{ const page=await getInventorySerials(query); rows.value=page.records||[] } finally{ loading.value=false } }
const openCreate=async()=>{ const page=await getProducts({pageNo:1,pageSize:200,status:'ACTIVE'}); products.value=(page.records||[]).filter((p:any)=>p.serialControlled); form.productId=''; form.serialNo=''; form.inboundBizNo=''; form.remark=''; dialogVisible.value=true }
const save=async()=>{ if(!form.productId||!form.serialNo){ ElMessage.warning(t('inventorySerial.validation.required')); return } saving.value=true; try{ await createInventorySerial({productId:form.productId, serialNo:form.serialNo, inboundBizNo:form.inboundBizNo||undefined, remark:form.remark||undefined}); ElMessage.success(t('inventorySerial.message.created')); dialogVisible.value=false; await loadData() } finally{ saving.value=false } }
const issue=async(row:InventorySerial)=>{ await issueInventorySerial(row.id); ElMessage.success(t('inventorySerial.message.issued')); await loadData() }
const scrap=async(row:InventorySerial)=>{ await scrapInventorySerial(row.id); ElMessage.success(t('inventorySerial.message.scrapped')); await loadData() }
onMounted(loadData)
</script>
<style scoped>.page{display:flex;flex-direction:column;gap:12px}</style>
