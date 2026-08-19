<template>
  <div class="inventory-lot-genealogy">
    <el-card shadow="never" class="query-card">
      <el-form :model="form" inline @submit.prevent="load">
        <el-form-item :label="$t('inventoryLotGenealogy.field.product')">
          <el-select
            v-model="form.productId"
            filterable
            clearable
            :placeholder="$t('inventoryLotGenealogy.placeholder.product')"
            style="width: 280px"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.code || product.productCode || product.id} - ${product.name || product.productName || '-'}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryLotGenealogy.field.lotNo')">
          <el-input
            v-model="form.lotNo"
            clearable
            :placeholder="$t('inventoryLotGenealogy.placeholder.lotNo')"
            style="width: 220px"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryLotGenealogy.field.direction')">
          <el-select v-model="form.direction" :placeholder="$t('inventoryLotGenealogy.placeholder.direction')" style="width: 160px">
            <el-option :label="$t('inventoryLotGenealogy.direction.both')" value="BOTH" />
            <el-option :label="$t('inventoryLotGenealogy.direction.upstream')" value="UPSTREAM" />
            <el-option :label="$t('inventoryLotGenealogy.direction.downstream')" value="DOWNSTREAM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryLotGenealogy.field.maxDepth')">
          <el-input-number v-model="form.maxDepth" :min="1" :max="10" controls-position="right" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="load">
            {{ $t('inventoryLotGenealogy.action.search') }}
          </el-button>
          <el-button :icon="Refresh" @click="reset">
            {{ $t('inventoryLotGenealogy.action.reset') }}
          </el-button>
          <el-button :icon="Download" :disabled="!genealogy?.downstream" @click="exportRecall">
            {{ $t('inventoryLotGenealogy.action.exportRecall') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="genealogy && truncationBanner(genealogy.limits)"
      class="notice"
      type="warning"
      :title="truncationBanner(genealogy.limits) || ''"
      show-icon
      :closable="false"
    />
    <el-alert
      v-if="genealogy && scopeBanner(genealogy.limits)"
      class="notice"
      type="info"
      :title="scopeBanner(genealogy.limits) || ''"
      show-icon
      :closable="false"
    />

    <el-empty v-if="!genealogy && !loading" :description="$t('inventoryLotGenealogy.empty')" />

    <div v-else-if="genealogy" class="genealogy-grid">
      <el-card shadow="never" class="tree-card">
        <template #header>
          <div class="card-header">
            <span>{{ $t('inventoryLotGenealogy.upstream') }}</span>
            <el-tag type="info">{{ genealogy.root.lotNo || $t('inventoryLotGenealogy.noLot') }}</el-tag>
          </div>
        </template>
        <el-tree
          :data="toTreeData(genealogy.upstream, 'UPSTREAM')"
          node-key="id"
          default-expand-all
          :empty-text="$t('inventoryLotGenealogy.empty')"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <div class="tree-node-main">
                <span>{{ data.label }}</span>
                <el-tag v-if="data.reason" size="small" :type="data.reasonType">{{ data.reason }}</el-tag>
              </div>
              <div v-if="data.detail" class="tree-node-detail">{{ data.detail }}</div>
              <el-button
                v-if="data.route"
                link
                type="primary"
                size="small"
                @click="openDocument(data.route)"
              >
                {{ $t('inventoryLotGenealogy.action.openDocument') }}
              </el-button>
            </div>
          </template>
        </el-tree>
      </el-card>

      <el-card shadow="never" class="tree-card">
        <template #header>
          <div class="card-header">
            <span>{{ $t('inventoryLotGenealogy.downstream') }}</span>
            <el-tag type="warning">{{ genealogy.root.lotNo || $t('inventoryLotGenealogy.noLot') }}</el-tag>
          </div>
        </template>
        <el-tree
          :data="toTreeData(genealogy.downstream, 'DOWNSTREAM')"
          node-key="id"
          default-expand-all
          :empty-text="$t('inventoryLotGenealogy.empty')"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <div class="tree-node-main">
                <span>{{ data.label }}</span>
                <el-tag v-if="data.reason" size="small" :type="data.reasonType">{{ data.reason }}</el-tag>
              </div>
              <div v-if="data.detail" class="tree-node-detail">{{ data.detail }}</div>
              <el-button
                v-if="data.route"
                link
                type="primary"
                size="small"
                @click="openDocument(data.route)"
              >
                {{ $t('inventoryLotGenealogy.action.openDocument') }}
              </el-button>
            </div>
          </template>
        </el-tree>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { getInventoryLotGenealogy } from '@/api/inventory'
import { getProducts, type Product } from '@/api/masterdata'
import { useInventoryLotGenealogyPresentation } from '@/composables/useInventoryLotGenealogyPresentation'
import { useInventoryLotGenealogyQuery } from '@/composables/useInventoryLotGenealogyQuery'
import { useInventoryLotGenealogyTree } from '@/composables/useInventoryLotGenealogyTree'
import { downloadBlob } from '@/utils/download'
import { formatLocalizedDateTime, formatLocalizedNumber, readDisplayPreferences } from '@/utils/locale'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const products = ref<Product[]>([])
const preferences = readDisplayPreferences()

const { form, loading, genealogy, load, reset, applyFromRoute } = useInventoryLotGenealogyQuery(t, {
  getInventoryLotGenealogy,
  onError: (message) => ElMessage.error(message)
})

const presentation = useInventoryLotGenealogyPresentation(t, {
  formatNumber: (value) => formatLocalizedNumber(value, { maximumFractionDigits: 4 }, preferences),
  formatDateTime: (value) => formatLocalizedDateTime(value, {}, preferences)
})

const { truncationBanner, scopeBanner } = presentation
const { toTreeData, recallRows, recallHeaders } = useInventoryLotGenealogyTree(t, presentation)

const loadProducts = async () => {
  try {
    products.value = (await getProducts({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })).records
  } catch {
    ElMessage.error(t('inventoryLotGenealogy.feedback.productsLoadFailed'))
  }
}

const openDocument = (routePath: string | null) => {
  if (routePath) router.push(routePath)
}

const exportRecall = () => {
  const rows = recallRows(genealogy.value?.downstream)
  if (!rows.length) {
    ElMessage.warning(t('inventoryLotGenealogy.feedback.exportEmpty'))
    return
  }
  const escapeCell = (value: string | number) => `"${String(value ?? '').replace(/"/g, '""')}"`
  const csv = [recallHeaders(), ...rows].map((row) => row.map(escapeCell).join(',')).join('\r\n')
  downloadBlob(new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' }), t('inventoryLotGenealogy.recall.filename'))
}

onMounted(async () => {
  await loadProducts()
  applyFromRoute(route.query)
  if (form.productId && form.lotNo) await load()
})
</script>

<style scoped>
.inventory-lot-genealogy {
  padding: 16px;
}

.query-card,
.tree-card {
  border: 1px solid var(--el-border-color-light);
}

.notice {
  margin-top: 12px;
}

.genealogy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tree-node {
  min-width: 0;
  padding: 4px 0;
}

.tree-node-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.tree-node-detail {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

@media (max-width: 960px) {
  .genealogy-grid {
    grid-template-columns: 1fr;
  }
}
</style>
