<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="1000px"
    class="product-relation-dialog"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @closed="reset"
  >
    <div class="relation-toolbar">
      <div class="owner-info">
        <el-tag size="small" type="info">{{ ownerTypeLabel }}</el-tag>
        <span class="owner-label">{{ ownerLabel }}</span>
      </div>
      <div class="toolbar-actions">
        <el-button size="small" :icon="Refresh" :loading="loading" @click="loadRows">
          {{ t('productRelation.refresh') }}
        </el-button>
        <el-button
          v-if="canWrite"
          size="small"
          type="primary"
          :icon="Plus"
          :disabled="!canAddMore"
          @click="openCreate"
        >
          {{ t('productRelation.add') }}
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="canWrite && !canAddMore"
      :title="t('productRelation.productExhausted')"
      type="info"
      show-icon
      :closable="false"
      class="relation-alert"
    />

    <el-card v-if="editorVisible" shadow="never" class="relation-editor">
      <template #header>{{ editingId ? t('productRelation.editorEditTitle') : t('productRelation.editorCreateTitle') }}</template>
      <el-form :model="form" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('productRelation.product')">
              <el-select
                v-model="form.productId"
                filterable
                :disabled="Boolean(editingId)"
                :placeholder="t('productRelation.productPlaceholder')"
                style="width: 100%"
              >
                <el-option
                  v-for="product in selectableProducts"
                  :key="product.id"
                  :label="productLabel(product)"
                  :value="String(product.id)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="ownerProductCodeLabel">
              <el-input v-model="form.ownerProductCode" :placeholder="t('productRelation.codePlaceholder')" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="ownerProductNameLabel">
              <el-input v-model="form.ownerProductName" :placeholder="t('productRelation.namePlaceholder')" maxlength="128" />
            </el-form-item>
          </el-col>
          <template v-if="isSupplier">
            <el-col :span="12">
              <el-form-item :label="t('productRelation.minPurchaseQty')">
                <el-input-number v-model="form.minPurchaseQty" :min="0" :precision="4" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('productRelation.leadTimeDays')">
                <el-input-number v-model="form.leadTimeDays" :min="0" :precision="0" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('productRelation.defaultSupplier')">
                <el-switch v-model="form.defaultSupplier" />
              </el-form-item>
            </el-col>
          </template>
          <template v-else>
            <el-col :span="12">
              <el-form-item :label="t('productRelation.deliveryPreference')">
                <el-input v-model="form.deliveryPreference" :placeholder="t('productRelation.deliveryPreferencePlaceholder')" maxlength="128" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('productRelation.packagingPreference')">
                <el-input v-model="form.packagingPreference" :placeholder="t('productRelation.packagingPreferencePlaceholder')" maxlength="128" />
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="24">
            <el-form-item :label="t('productRelation.remark')">
              <el-input v-model="form.remark" :placeholder="t('productRelation.remarkPlaceholder')" maxlength="255" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="editor-actions">
          <el-button size="small" @click="closeEditor">{{ t('productRelation.cancel') }}</el-button>
          <el-button size="small" type="primary" :loading="saving" @click="submit">{{ t('productRelation.save') }}</el-button>
        </div>
      </el-form>
    </el-card>

    <el-table v-loading="loading" :data="rows" border stripe :empty-text="t('productRelation.empty')">
      <el-table-column :label="t('productRelation.product')" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ relationProductLabel(row) }}</template>
      </el-table-column>
      <el-table-column :label="ownerProductCodeLabel" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.ownerProductCode || '-' }}</template>
      </el-table-column>
      <el-table-column :label="ownerProductNameLabel" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.ownerProductName || '-' }}</template>
      </el-table-column>
      <template v-if="isSupplier">
        <el-table-column :label="t('productRelation.minPurchaseQty')" width="140" align="right">
          <template #default="{ row }">{{ formatQuantity(row.minPurchaseQty) }}</template>
        </el-table-column>
        <el-table-column :label="t('productRelation.leadTimeDays')" width="130" align="center">
          <template #default="{ row }">{{ Number(row.leadTimeDays || 0) }}</template>
        </el-table-column>
        <el-table-column :label="t('productRelation.defaultSupplier')" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.defaultSupplier ? 'success' : 'info'">
              {{ row.defaultSupplier ? t('productRelation.yes') : t('productRelation.no') }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
      <template v-else>
        <el-table-column :label="t('productRelation.deliveryPreference')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deliveryPreference || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('productRelation.packagingPreference')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.packagingPreference || '-' }}</template>
        </el-table-column>
      </template>
      <el-table-column :label="t('productRelation.remark')" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="canWrite" :label="t('productRelation.actions')" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">{{ t('productRelation.edit') }}</el-button>
          <el-button link type="danger" @click="remove(row)">{{ t('productRelation.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('productRelation.close') }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  deleteCustomerProductRelation,
  deleteSupplierProductRelation,
  getCustomerProductRelations,
  getProducts,
  getSupplierProductRelations,
  saveCustomerProductRelation,
  saveSupplierProductRelation
} from '@/api/masterdata'
import {
  useProductRelationPanel,
  type ProductRelationForm,
  type ProductRelationMode,
  type ProductRelationRow
} from '@/composables/useProductRelationPanel'
import { formatLocalizedNumber } from '@/utils/locale'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    mode: ProductRelationMode
    ownerId: string
    ownerLabel?: string
    canWrite?: boolean
  }>(),
  { ownerLabel: '', canWrite: false }
)

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const { t } = useI18n()

const isSupplier = computed(() => props.mode === 'SUPPLIER')
const dialogTitle = computed(() => t(isSupplier.value ? 'productRelation.supplierTitle' : 'productRelation.customerTitle'))
const ownerTypeLabel = computed(() => t(isSupplier.value ? 'productRelation.supplierOwner' : 'productRelation.customerOwner'))
const ownerProductCodeLabel = computed(() => t(isSupplier.value ? 'productRelation.supplierProductCode' : 'productRelation.customerProductCode'))
const ownerProductNameLabel = computed(() => t(isSupplier.value ? 'productRelation.supplierProductName' : 'productRelation.customerProductName'))

const formatQuantity = (value?: number) =>
  formatLocalizedNumber(Number(value || 0), { minimumFractionDigits: 0, maximumFractionDigits: 4 })

/** Blank inputs are sent as undefined so the backend stores NULL instead of an empty string. */
const optionalText = (value: string) => value.trim() || undefined

const loadRelations = async (ownerId: string): Promise<ProductRelationRow[]> => {
  if (isSupplier.value) {
    const relations = await getSupplierProductRelations(ownerId)
    return relations.map((row) => ({
      id: row.id,
      productId: row.productId,
      productCode: row.productCode,
      productName: row.productName,
      ownerProductCode: row.supplierProductCode,
      ownerProductName: row.supplierProductName,
      minPurchaseQty: row.minPurchaseQty,
      leadTimeDays: row.leadTimeDays,
      defaultSupplier: row.defaultSupplier,
      remark: row.remark,
      status: row.status
    }))
  }
  const relations = await getCustomerProductRelations(ownerId)
  return relations.map((row) => ({
    id: row.id,
    productId: row.productId,
    productCode: row.productCode,
    productName: row.productName,
    ownerProductCode: row.customerProductCode,
    ownerProductName: row.customerProductName,
    deliveryPreference: row.deliveryPreference,
    packagingPreference: row.packagingPreference,
    remark: row.remark,
    status: row.status
  }))
}

const saveRelation = (ownerId: string, values: ProductRelationForm) => {
  if (isSupplier.value) {
    return saveSupplierProductRelation(ownerId, {
      productId: values.productId,
      supplierProductCode: optionalText(values.ownerProductCode),
      supplierProductName: optionalText(values.ownerProductName),
      minPurchaseQty: Number(values.minPurchaseQty || 0),
      leadTimeDays: Number(values.leadTimeDays || 0),
      defaultSupplier: Boolean(values.defaultSupplier),
      remark: optionalText(values.remark)
    })
  }
  return saveCustomerProductRelation(ownerId, {
    productId: values.productId,
    customerProductCode: optionalText(values.ownerProductCode),
    customerProductName: optionalText(values.ownerProductName),
    deliveryPreference: optionalText(values.deliveryPreference),
    packagingPreference: optionalText(values.packagingPreference),
    remark: optionalText(values.remark)
  })
}

const removeRelation = (ownerId: string, id: string) => (
  isSupplier.value ? deleteSupplierProductRelation(ownerId, id) : deleteCustomerProductRelation(ownerId, id)
)

const {
  closeEditor,
  editingId,
  editorVisible,
  form,
  loadRows,
  loading,
  open,
  openCreate,
  openEdit,
  productLabel,
  products,
  relationProductLabel,
  remove,
  reset,
  rows,
  saving,
  selectableProducts,
  submit
} = useProductRelationPanel(t, {
  loadRelations,
  saveRelation,
  removeRelation,
  loadProducts: () => getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' }).then((page) => page.records),
  confirm: (message, title) => ElMessageBox.confirm(message, title, { type: 'warning' }),
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message)
})

const canAddMore = computed(() => products.value.some(
  (product) => !rows.value.some((row) => String(row.productId) === String(product.id))
))

watch(
  () => [props.modelValue, props.ownerId] as const,
  ([visible, ownerId]) => {
    if (visible && ownerId) void open(ownerId)
  },
  { immediate: true }
)
</script>

<style scoped>
.relation-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.owner-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.owner-label {
  font-weight: 600;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.relation-alert,
.relation-editor {
  margin-bottom: 12px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
