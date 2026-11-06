<template>
  <el-table :data="lines" border stripe max-height="480" row-key="id">
    <el-table-column prop="productCode" :label="t('inventoryMrp.productCode')" width="120" />
    <el-table-column prop="productName" :label="t('inventoryMrp.productName')" min-width="160" />
    <el-table-column prop="netQty" :label="t('inventoryMrp.netQty')" width="110" align="right" />
    <el-table-column :label="t('inventoryMrp.warehouse')" min-width="140">
      <template #default="{ row }">{{ row.trace?.warehouseName || row.warehouseId || '—' }}</template>
    </el-table-column>
    <el-table-column prop="bomLevel" :label="t('inventoryMrp.level')" width="90" align="center" />
    <el-table-column :label="t('inventoryMrp.lineStatus')" width="110">
      <template #default="{ row }">{{ t(`inventoryMrp.lineStates.${row.status || 'OPEN'}`) }}</template>
    </el-table-column>
    <el-table-column prop="convertedBizNo" :label="t('inventoryMrp.convertedDoc')" min-width="150" />
    <el-table-column :label="t('inventoryMrp.actions')" width="210" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" :disabled="!row.trace" @click="emit('explain', row)">{{ t('inventoryMrp.explain') }}</el-button>
        <el-button v-if="row.status === 'OPEN' && canConvert(row)" link type="primary"
          :disabled="blocked" :loading="convertingId === String(row.id)" @click="emit('convert', row)">
          {{ t(row.suggestionType === 'PRODUCTION' ? 'inventoryMrp.convertMo' : 'inventoryMrp.convertPo') }}
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/modules/user'
import type { MrpSuggestionLine } from '@/api/inventory'
defineProps<{ lines: MrpSuggestionLine[]; blocked: boolean; convertingId: string | null }>()
const emit = defineEmits<{ explain: [line: MrpSuggestionLine]; convert: [line: MrpSuggestionLine] }>()
const { t } = useI18n()
const user = useUserStore()
const canConvert = (line: MrpSuggestionLine) => user.hasPermission('inventory:mrp:convert')
  && user.hasPermission(line.suggestionType === 'PRODUCTION' ? 'production:order:create' : 'purchase:order:create')
</script>
