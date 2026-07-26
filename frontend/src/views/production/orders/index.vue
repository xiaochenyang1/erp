<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('productionOrder.orderNo')">
          <el-input v-model="queryForm.orderNo" :placeholder="t('productionOrder.orderNoPlaceholder')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="t('productionOrder.product')">
          <el-select
            v-model="queryForm.productId"
            :placeholder="t('productionOrder.selectProduct')"
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
        <el-form-item :label="t('productionOrder.statusLabel')">
          <el-select v-model="queryForm.status" :placeholder="t('productionOrder.select')" clearable style="width: 120px">
            <el-option :label="t('productionOrder.status.draft')" value="DRAFT" />
            <el-option :label="t('productionOrder.status.released')" value="RELEASED" />
            <el-option :label="t('productionOrder.status.materialIssued')" value="MATERIAL_ISSUED" />
            <el-option :label="t('productionOrder.status.inProgress')" value="IN_PROGRESS" />
            <el-option :label="t('productionOrder.status.completed')" value="COMPLETED" />
            <el-option :label="t('productionOrder.status.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('productionOrder.priorityLabel')">
          <el-select v-model="queryForm.priority" :placeholder="t('productionOrder.select')" clearable style="width: 120px">
            <el-option :label="t('productionOrder.priority.low')" value="LOW" />
            <el-option :label="t('productionOrder.priority.normal')" value="NORMAL" />
            <el-option :label="t('productionOrder.priority.high')" value="HIGH" />
            <el-option :label="t('productionOrder.priority.urgent')" value="URGENT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ t('productionOrder.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ t('productionOrder.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('productionOrder.title') }}</span>
          <el-button v-permission="'production:order:create'" type="primary" :icon="Plus" @click="handleAdd">{{ t('productionOrder.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="orderNo" :label="t('productionOrder.orderNo')" width="160" />
        <el-table-column prop="productCode" :label="t('productionOrder.productCode')" width="120" />
        <el-table-column prop="productName" :label="t('productionOrder.productName')" width="180" />
        <el-table-column prop="planQuantity" :label="t('productionOrder.plannedQuantity')" width="100" align="right" />
        <el-table-column prop="completedQuantity" :label="t('productionOrder.completedQuantity')" width="100" align="right" />
        <el-table-column :label="t('productionOrder.completionRate')" width="100" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round((row.completedQuantity / row.planQuantity) * 100)"
              :color="getProgressColor(row.completedQuantity / row.planQuantity)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('productionOrder.materialWarehouse')" width="140">
          <template #default="{ row }">
            {{ warehouseLabel(row.materialWarehouseId) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('productionOrder.finishedWarehouse')" width="140">
          <template #default="{ row }">
            {{ warehouseLabel(row.finishedWarehouseId) }}
          </template>
        </el-table-column>
        <el-table-column prop="priority" :label="t('productionOrder.priorityLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getPriorityType(row.priority)" size="small">
              {{ getPriorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('productionOrder.statusLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="planStartDate" :label="t('productionOrder.plannedStart')" width="120" />
        <el-table-column prop="planEndDate" :label="t('productionOrder.plannedEnd')" width="120" />
        <el-table-column :label="t('productionOrder.actions')" width="460" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">{{ t('productionOrder.view') }}</el-button>
            <el-button type="primary" link @click="handlePrint(row)">{{ t('productionOrder.print') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'production:order:update'"
              type="primary"
              link
              @click="handleEdit(row)"
            >
              {{ t('productionOrder.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'production:order:release'"
              type="success"
              link
              @click="handleRelease(row)"
            >
              {{ t('productionOrder.release') }}
            </el-button>
            <el-button
              v-if="row.status === 'RELEASED' || row.status === 'MATERIAL_ISSUED'"
              v-permission="'production:order:issue'"
              type="primary"
              link
              @click="handleIssue(row)"
            >
              {{ t('productionOrder.issue') }}
            </el-button>
            <el-button
              v-if="row.status === 'RELEASED' || row.status === 'MATERIAL_ISSUED' || row.status === 'IN_PROGRESS'"
              v-permission="'production:order:report'"
              type="warning"
              link
              @click="openOperations(row)"
            >
              {{ t('productionOrder.operationReport') }}
            </el-button>
            <el-button
              v-if="row.status === 'MATERIAL_ISSUED' || row.status === 'IN_PROGRESS'"
              v-permission="'production:order:complete'"
              type="success"
              link
              @click="handleComplete(row)"
            >
              {{ t('productionOrder.complete') }}
            </el-button>
            <el-button
              v-if="canReverseCompletion(row)"
              v-permission="'production:order:reverse-completion'"
              type="warning"
              link
              @click="handleReverseCompletion(row)"
            >
              {{ t('productionOrder.reverse') }}
            </el-button>
            <el-button
              v-if="canReturnMaterials(row)"
              v-permission="'production:order:return'"
              type="warning"
              link
              @click="handleReturnMaterials(row)"
            >
              {{ t('productionOrder.returnMaterials') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'production:order:cancel'"
              type="danger"
              link
              @click="handleCancel(row)"
            >
              {{ t('productionOrder.cancel') }}
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
            <el-form-item :label="t('productionOrder.product')" prop="productId">
              <el-select
                v-model="formData.productId"
                :placeholder="t('productionOrder.selectProduct')"
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
            <el-form-item :label="t('productionOrder.bom')" prop="bomId">
              <el-select
                v-model="formData.bomId"
                :placeholder="t('productionOrder.selectBom')"
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
            <el-form-item :label="t('productionOrder.plannedQuantity')" prop="planQuantity">
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
            <el-form-item :label="t('productionOrder.materialWarehouse')" prop="materialWarehouseId">
              <el-select
                v-model="formData.materialWarehouseId"
                :placeholder="t('productionOrder.selectMaterialWarehouse')"
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
            <el-form-item :label="t('productionOrder.finishedWarehouse')" prop="finishedWarehouseId">
              <el-select
                v-model="formData.finishedWarehouseId"
                :placeholder="t('productionOrder.selectFinishedWarehouse')"
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
            <el-form-item :label="t('productionOrder.plannedStartDate')" prop="planStartDate">
              <el-date-picker
                v-model="formData.planStartDate"
                type="date"
                :placeholder="t('productionOrder.selectDate')"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('productionOrder.plannedEndDate')" prop="planEndDate">
              <el-date-picker
                v-model="formData.planEndDate"
                type="date"
                :placeholder="t('productionOrder.selectDate')"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="t('productionOrder.priorityLabel')" prop="priority">
          <el-radio-group v-model="formData.priority">
            <el-radio value="LOW">{{ t('productionOrder.priority.low') }}</el-radio>
            <el-radio value="NORMAL">{{ t('productionOrder.priority.normal') }}</el-radio>
            <el-radio value="HIGH">{{ t('productionOrder.priority.high') }}</el-radio>
            <el-radio value="URGENT">{{ t('productionOrder.priority.urgent') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item :label="t('productionOrder.remark')" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            :placeholder="t('productionOrder.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('productionOrder.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ t('productionOrder.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" :title="t('productionOrder.detailTitle')" width="1000px">
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('productionOrder.orderNo')">{{ viewData.orderNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.bomCode')">{{ viewData.bomCode || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.productCode')">{{ viewData.productCode }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.productName')">{{ viewData.productName }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.plannedQuantity')">{{ viewData.planQuantity }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.completedQuantity')">{{ viewData.completedQuantity }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.scrapQuantity')">{{ viewData.scrapQuantity }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.materialWarehouse')">{{ warehouseLabel(viewData.materialWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.finishedWarehouse')">{{ warehouseLabel(viewData.finishedWarehouseId) }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.priorityLabel')">
          <el-tag :type="getPriorityType(viewData.priority)">
            {{ getPriorityLabel(viewData.priority) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.statusLabel')">
          <el-tag :type="getStatusType(viewData.status)">
            {{ getStatusLabel(viewData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.plannedStart')">{{ viewData.planStartDate }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.plannedEnd')">{{ viewData.planEndDate }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.actualStart')">{{ viewData.actualStartDate || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.actualEnd')">{{ viewData.actualEndDate || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.createdBy')">{{ viewData.createdBy }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.createdAt')">{{ formatLocalizedDateTime(viewData.createdAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('productionOrder.remark')" :span="2">{{ viewData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider v-if="viewData.materials && viewData.materials.length > 0" />

      <h4 v-if="viewData.materials && viewData.materials.length > 0">{{ t('productionOrder.materialUsage') }}</h4>
      <el-table v-if="viewData.materials && viewData.materials.length > 0" :data="viewData.materials" border stripe style="margin-top: 10px">
        <el-table-column prop="materialCode" :label="t('productionOrder.materialCode')" width="150" />
        <el-table-column prop="materialName" :label="t('productionOrder.materialName')" width="200" />
        <el-table-column prop="requiredQuantity" :label="t('productionOrder.requiredQuantity')" width="120" align="right" />
        <el-table-column prop="issuedQuantity" :label="t('productionOrder.issuedQuantity')" width="120" align="right" />
        <el-table-column prop="returnedQuantity" :label="t('productionOrder.returnedQuantity')" width="120" align="right" />
        <el-table-column prop="unit" :label="t('productionOrder.unit')" width="80" />
      </el-table>

      <template #footer>
        <el-button @click="viewDialogVisible = false">{{ t('productionOrder.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 完工对话框 -->
    <el-dialog v-model="completeDialogVisible" :title="t('productionOrder.completionTitle')" width="600px">
      <el-form :model="completeForm" label-width="120px">
        <el-form-item :label="t('productionOrder.completionQuantity')" required>
          <el-input-number
            v-model="completeForm.completedQuantity"
            :min="1"
            :max="completeForm.maxQuantity"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
          <el-text type="info" size="small">{{ t('productionOrder.maxCompletion', { quantity: completeForm.maxQuantity }) }}</el-text>
        </el-form-item>
        <el-form-item :label="t('productionOrder.scrapQuantity')">
          <el-input-number
            v-model="completeForm.scrapQuantity"
            :min="0"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('productionOrder.completionDate')">
          <el-date-picker
            v-model="completeForm.completionDate"
            type="date"
            :placeholder="t('productionOrder.selectDate')"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('productionOrder.lotNo')">
          <el-input
            v-model="completeForm.lotNo"
            clearable
            :placeholder="completeProductControls.lotControlled
              ? t('productionOrder.lotNoPlaceholder')
              : t('productionOrder.remarkPlaceholder')"
            :disabled="completeProductControls.lotControlled === false"
          />
        </el-form-item>
        <el-form-item :label="t('productionOrder.productionDate')">
          <el-date-picker
            v-model="completeForm.productionDate"
            type="date"
            :placeholder="t('productionOrder.selectDate')"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :disabled="completeProductControls.lotControlled === false"
          />
        </el-form-item>
        <el-form-item :label="t('productionOrder.expiryDate')">
          <el-date-picker
            v-model="completeForm.expiryDate"
            type="date"
            :placeholder="t('productionOrder.selectDate')"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :disabled="completeProductControls.shelfLifeControlled === false && completeProductControls.lotControlled === false"
          />
        </el-form-item>
        <el-form-item :label="t('productionOrder.location')">
          <el-select
            v-model="completeForm.locationId"
            clearable
            filterable
            :placeholder="t('productionOrder.selectLocation')"
            style="width: 100%"
          >
            <el-option
              v-for="location in finishedLocations"
              :key="location.id"
              :label="`${location.locationCode} ${location.locationName}`"
              :value="location.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('productionOrder.serialNos')">
          <el-input
            v-model="completeForm.serialNos"
            type="textarea"
            :rows="completeProductControls.serialControlled ? 2 : 1"
            clearable
            :placeholder="completeProductControls.serialControlled
              ? t('productionOrder.serialNosPlaceholder')
              : t('productionOrder.remarkPlaceholder')"
            :disabled="completeProductControls.serialControlled === false"
          />
          <div
            v-if="completeProductControls.serialControlled"
            class="serial-progress"
            :class="{ 'serial-progress--ok': serialCaptureProgress(completeForm.serialNos, completeForm.completedQuantity).complete }"
          >
            {{ t('productionOrder.serialProgress', serialCaptureProgress(completeForm.serialNos, completeForm.completedQuantity)) }}
          </div>
        </el-form-item>
        <el-form-item :label="t('productionOrder.remark')">
          <el-input
            v-model="completeForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="t('productionOrder.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">{{ t('productionOrder.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirmComplete">
          {{ t('productionOrder.confirmCompletion') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 完工红冲对话框 -->
    <el-dialog v-model="reverseDialogVisible" :title="t('productionOrder.reversalTitle')" width="600px">
      <el-form :model="reverseForm" label-width="120px">
        <el-form-item :label="t('productionOrder.reversalQuantity')" required>
          <el-input-number
            v-model="reverseForm.reversedQty"
            :min="1"
            :max="reverseForm.maxQuantity"
            :precision="2"
            controls-position="right"
            style="width: 100%"
          />
          <el-text type="info" size="small">{{ t('productionOrder.maxReversal', { quantity: reverseForm.maxQuantity }) }}</el-text>
        </el-form-item>
        <el-form-item :label="t('productionOrder.reversalDate')">
          <el-date-picker
            v-model="reverseForm.reversalDate"
            type="date"
            :placeholder="t('productionOrder.selectDate')"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('productionOrder.remark')">
          <el-input
            v-model="reverseForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="t('productionOrder.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reverseDialogVisible = false">{{ t('productionOrder.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirmReverseCompletion">
          {{ t('productionOrder.confirmReversal') }}
        </el-button>
      </template>
    </el-dialog>


    <!-- 生产领料对话框 -->
    <el-dialog v-model="issueDialogVisible" :title="t('productionOrder.issueTitle')" width="980px">
      <el-form :model="issueForm" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('productionOrder.issueDate')">
              <el-date-picker
                v-model="issueForm.issueDate"
                type="date"
                :placeholder="t('productionOrder.selectDate')"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('productionOrder.remark')">
              <el-input v-model="issueForm.remark" :placeholder="t('productionOrder.remarkPlaceholder')" clearable />
            </el-form-item>
          </el-col>
        </el-row>

        <el-table :data="issueForm.materials" border stripe>
          <el-table-column prop="materialCode" :label="t('productionOrder.materialCode')" width="140" />
          <el-table-column prop="materialName" :label="t('productionOrder.materialName')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="requiredQuantity" :label="t('productionOrder.requiredQuantity')" width="110" align="right" />
          <el-table-column prop="issuedQuantity" :label="t('productionOrder.issuedQuantity')" width="110" align="right" />
          <el-table-column :label="t('productionOrder.remainingQuantity')" width="110" align="right">
            <template #default="{ row }">{{ row.remainingQty }}</template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.currentIssue')" width="170">
            <template #default="{ row }">
              <el-input-number
                v-model="row.issueQty"
                :min="0"
                :max="row.remainingQty"
                :precision="2"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.lotNo')" width="140">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                clearable
                :placeholder="row.lotControlled
                  ? t('productionOrder.lotNoPlaceholder')
                  : t('productionOrder.remarkPlaceholder')"
                :disabled="row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.location')" width="160">
            <template #default="{ row }">
              <el-select v-model="row.locationId" clearable filterable :placeholder="t('productionOrder.selectLocation')" style="width: 100%">
                <el-option
                  v-for="location in materialLocations"
                  :key="location.id"
                  :label="`${location.locationCode} ${location.locationName}`"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.serialNos')" min-width="200">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                type="textarea"
                :rows="row.serialControlled ? 2 : 1"
                clearable
                :placeholder="row.serialControlled
                  ? t('productionOrder.serialNosPlaceholder')
                  : t('productionOrder.remarkPlaceholder')"
                :disabled="row.serialControlled === false"
              />
              <div
                v-if="row.serialControlled"
                class="serial-progress"
                :class="{ 'serial-progress--ok': serialCaptureProgress(row.serialNos, row.issueQty).complete }"
              >
                {{ t('productionOrder.serialProgress', serialCaptureProgress(row.serialNos, row.issueQty)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.remark')" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.remark" clearable />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">{{ t('productionOrder.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirmIssueMaterials">
          {{ t('productionOrder.confirmIssue') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 生产退料对话框 -->
    <el-dialog v-model="returnDialogVisible" :title="t('productionOrder.materialReturnTitle')" width="980px">
      <el-form :model="returnForm" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('productionOrder.returnDate')">
              <el-date-picker
                v-model="returnForm.returnDate"
                type="date"
                :placeholder="t('productionOrder.selectDate')"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('productionOrder.remark')">
              <el-input v-model="returnForm.remark" :placeholder="t('productionOrder.remarkPlaceholder')" clearable />
            </el-form-item>
          </el-col>
        </el-row>

        <el-table :data="returnForm.materials" border stripe>
          <el-table-column prop="materialCode" :label="t('productionOrder.materialCode')" width="140" />
          <el-table-column prop="materialName" :label="t('productionOrder.materialName')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="issuedQuantity" :label="t('productionOrder.returnableQuantity')" width="110" align="right" />
          <el-table-column :label="t('productionOrder.currentReturn')" width="170">
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
          <el-table-column :label="t('productionOrder.lotNo')" width="140">
            <template #default="{ row }">
              <el-input
                v-model="row.lotNo"
                clearable
                :placeholder="row.lotControlled
                  ? t('productionOrder.lotNoPlaceholder')
                  : t('productionOrder.remarkPlaceholder')"
                :disabled="row.lotControlled === false"
              />
            </template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.location')" width="160">
            <template #default="{ row }">
              <el-select v-model="row.locationId" clearable filterable :placeholder="t('productionOrder.selectLocation')" style="width: 100%">
                <el-option
                  v-for="location in materialLocations"
                  :key="location.id"
                  :label="`${location.locationCode} ${location.locationName}`"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.serialNos')" min-width="200">
            <template #default="{ row }">
              <el-input
                v-model="row.serialNos"
                type="textarea"
                :rows="row.serialControlled ? 2 : 1"
                clearable
                :placeholder="row.serialControlled
                  ? t('productionOrder.serialNosPlaceholder')
                  : t('productionOrder.remarkPlaceholder')"
                :disabled="row.serialControlled === false"
              />
              <div
                v-if="row.serialControlled"
                class="serial-progress"
                :class="{ 'serial-progress--ok': serialCaptureProgress(row.serialNos, row.returnQty).complete }"
              >
                {{ t('productionOrder.serialProgress', serialCaptureProgress(row.serialNos, row.returnQty)) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('productionOrder.remark')" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.remark" clearable />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="returnDialogVisible = false">{{ t('productionOrder.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirmReturnMaterials">
          {{ t('productionOrder.confirmReturn') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 工序报工 -->
    <el-dialog v-model="opsDialogVisible" :title="t('productionOrder.operationsTitle', { orderNo: opsOrderNo })" width="920px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('productionOrder.operationsHelp')"
        style="margin-bottom: 12px"
      />
      <el-table v-loading="opsLoading" :data="operations" border stripe>
        <el-table-column prop="lineNo" :label="t('productionOrder.sequence')" width="70" />
        <el-table-column prop="operationCode" :label="t('productionOrder.operationCode')" width="100" />
        <el-table-column prop="operationName" :label="t('productionOrder.operationName')" min-width="120" />
        <el-table-column prop="workCenterName" :label="t('productionOrder.workCenter')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="plannedQty" :label="t('productionOrder.planned')" width="90" align="right" />
        <el-table-column prop="reportedQty" :label="t('productionOrder.reported')" width="90" align="right" />
        <el-table-column prop="qualifiedQty" :label="t('productionOrder.qualified')" width="90" align="right" />
        <el-table-column prop="scrapQty" :label="t('productionOrder.scrap')" width="90" align="right" />
        <el-table-column :label="t('productionOrder.statusLabel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="opStatusType(row.status)" size="small">{{ opStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('productionOrder.actions')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'DONE'"
              v-permission="'production:order:report'"
              link
              type="primary"
              @click="openReport(row)"
            >
              {{ t('productionOrder.report') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!opsLoading && operations.length === 0" :description="t('productionOrder.noOperations')" />
      <template #footer>
        <el-button @click="opsDialogVisible = false">{{ t('productionOrder.close') }}</el-button>
        <el-button type="primary" :loading="opsLoading" @click="loadOperations">{{ t('productionOrder.refresh') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportDialogVisible" :title="t('productionOrder.operationReport')" width="480px">
      <el-form :model="reportForm" label-width="100px">
        <el-form-item :label="t('productionOrder.operationName')">
          <el-input :model-value="reportForm.operationName" disabled />
        </el-form-item>
        <el-form-item :label="t('productionOrder.reportQuantity')" required>
          <el-input-number v-model="reportForm.reportQty" :min="0.0001" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('productionOrder.qualifiedQuantity')" required>
          <el-input-number v-model="reportForm.qualifiedQty" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('productionOrder.scrapQuantity')">
          <el-input-number v-model="reportForm.scrapQty" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('productionOrder.remark')">
          <el-input v-model="reportForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialogVisible = false">{{ t('productionOrder.cancel') }}</el-button>
        <el-button type="primary" :loading="reportLoading" @click="submitReport">{{ t('productionOrder.confirmReport') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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
  type ProductionOrderMaterial
} from '@/api/production'
import { getProducts, getProduct, getWarehouses, getLocations, type Product, type Warehouse, type Location } from '@/api/masterdata'
import { getBOMs, type BOM } from '@/api/production'
import { formatBusinessDate, formatLocalizedDateTime } from '@/utils/locale'
import { printProductionOrder } from '@/utils/bizPrint'
import {
  serialCaptureProgress,
  validateProductControlLines
} from '@/utils/productLines'
import { useProductionOrderPresentation } from '@/composables/useProductionOrderPresentation'
import { useProductionOrderProductControls } from '@/composables/useProductionOrderProductControls'
import { useProductionOrderOperations } from '@/composables/useProductionOrderOperations'

const { t } = useI18n()

interface ReturnMaterialRow extends ProductionOrderMaterial {
  returnQty: number
  lotNo?: string
  locationId?: string | number
  serialNos?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  productCode?: string
  productName?: string
  quantity?: number
  remark?: string
}

interface IssueMaterialRow extends ProductionOrderMaterial {
  remainingQty: number
  issueQty: number
  lotNo?: string
  locationId?: string | number
  serialNos?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  productCode?: string
  productName?: string
  quantity?: number
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
const finishedLocations = ref<Location[]>([])
const materialLocations = ref<Location[]>([])
const {
  getPriorityLabel,
  getPriorityType,
  getStatusLabel,
  getStatusType,
  opStatusText,
  opStatusType,
  warehouseLabel
} = useProductionOrderPresentation(warehouseOptions, t)
const {
  hydrateMaterialControls,
  productControlFromOptions,
  resolveProductControls
} = useProductionOrderProductControls(productOptions, getProduct)
const {
  loadOperations,
  openOperations,
  openReport,
  operations,
  opsDialogVisible,
  opsLoading,
  opsOrderId,
  opsOrderNo,
  reportDialogVisible,
  reportForm,
  reportLoading,
  submitReport
} = useProductionOrderOperations(t, {
  loadOperations: getProductionOrderOperations,
  reportOperation: reportProductionOperation,
  onError: (message) => ElMessage.error(message),
  onSuccess: (message) => ElMessage.success(message),
  onWarning: (message) => ElMessage.warning(message)
})
const completeProductControls = reactive({
  lotControlled: undefined as boolean | undefined,
  shelfLifeControlled: undefined as boolean | undefined,
  serialControlled: undefined as boolean | undefined,
  productCode: undefined as string | undefined,
  productName: undefined as string | undefined
})
const completeProductId = ref<string | number | undefined>()

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

const formRules = computed<FormRules>(() => ({
  productId: [{ required: true, message: t('productionOrder.validation.product'), trigger: 'change' }],
  bomId: [{ required: true, message: t('productionOrder.validation.bom'), trigger: 'change' }],
  planQuantity: [{ required: true, message: t('productionOrder.validation.quantity'), trigger: 'blur' }],
  materialWarehouseId: [{ required: true, message: t('productionOrder.validation.materialWarehouse'), trigger: 'change' }],
  finishedWarehouseId: [{ required: true, message: t('productionOrder.validation.finishedWarehouse'), trigger: 'change' }],
  planStartDate: [{ required: true, message: t('productionOrder.validation.startDate'), trigger: 'change' }],
  planEndDate: [{ required: true, message: t('productionOrder.validation.endDate'), trigger: 'change' }]
}))

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
  lotNo: '',
  productionDate: '',
  expiryDate: '',
  locationId: undefined as string | number | undefined,
  serialNos: '',
  maxQuantity: 0,
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

const issueDialogVisible = ref(false)
const issueForm = reactive({
  orderId: '' as string | number,
  issueDate: '',
  remark: '',
  materials: [] as IssueMaterialRow[]
})

const loadLocationsByWarehouse = async (warehouseId?: string | number) => {
  if (warehouseId == null || warehouseId === '') {
    return [] as Location[]
  }
  const page = await getLocations({
    pageNo: 1,
    pageSize: 500,
    status: 'ACTIVE',
    warehouseId
  })
  return page.records || []
}

const loadFinishedLocations = async (warehouseId?: string | number) => {
  try {
    finishedLocations.value = await loadLocationsByWarehouse(warehouseId)
  } catch (error) {
    finishedLocations.value = []
    console.error(t('productionOrder.message.optionsLoadFailed'), error)
  }
}

const loadMaterialLocations = async (warehouseId?: string | number) => {
  try {
    materialLocations.value = await loadLocationsByWarehouse(warehouseId)
  } catch (error) {
    materialLocations.value = []
    console.error(t('productionOrder.message.optionsLoadFailed'), error)
  }
}

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
    console.error(t('productionOrder.message.optionsLoadFailed'), error)
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
    console.error(t('productionOrder.message.orderLoadFailed'), error)
    ElMessage.error(t('productionOrder.message.loadFailed'))
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
  dialogTitle.value = t('productionOrder.dialog.create')
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
    dialogTitle.value = t('productionOrder.dialog.edit')
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('productionOrder.message.orderLoadFailed'))
  }
}

// 查看
const handleView = async (row: ProductionOrder) => {
  try {
    const res = await getProductionOrder(row.id)
    viewData.value = res
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error(t('productionOrder.message.detailLoadFailed'))
  }
}

const handlePrint = async (row: ProductionOrder) => {
  try {
    const order = await getProductionOrder(row.id)
    printProductionOrder(order)
  } catch {
    ElMessage.error(t('productionOrder.message.printLoadFailed'))
  }
}

// 下达
const handleRelease = async (row: ProductionOrder) => {
  try {
    await ElMessageBox.confirm(t('productionOrder.message.releaseConfirm', { orderNo: row.orderNo }), t('productionOrder.message.prompt'), {
      type: 'warning'
    })
    await releaseProductionOrder(row.id)
    ElMessage.success(t('productionOrder.message.released'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('productionOrder.message.releaseFailed'))
    }
  }
}

// 领料
const handleIssue = async (row: ProductionOrder) => {
  try {
    const order = await getProductionOrder(row.id)
    const issuableMaterials = await hydrateMaterialControls(
      (order.materials || [])
        .map((material) => {
          const remainingQty = Math.max(
            Number(material.requiredQuantity || 0) - Number(material.issuedQuantity || 0),
            0
          )
          return {
            ...material,
            remainingQty,
            issueQty: remainingQty,
            lotNo: '',
            locationId: undefined,
            serialNos: '',
            remark: '',
            productCode: material.materialCode,
            productName: material.materialName
          } as IssueMaterialRow
        })
        .filter((material) => Number(material.remainingQty || 0) > 0)
    )

    if (issuableMaterials.length === 0) {
      ElMessage.warning(t('productionOrder.message.noIssuableMaterials'))
      return
    }

    issueForm.orderId = order.id
    issueForm.issueDate = order.actualStartDate || order.planStartDate || formatBusinessDate()
    issueForm.remark = t('productionOrder.issueRemark')
    issueForm.materials = issuableMaterials
    issueDialogVisible.value = true
    void loadMaterialLocations(order.materialWarehouseId || order.warehouseId)
  } catch (error) {
    ElMessage.error(t('productionOrder.message.issuableLoadFailed'))
  }
}

const handleConfirmIssueMaterials = async () => {
  const selectedMaterials = issueForm.materials.filter((material) => Number(material.issueQty || 0) > 0)
  if (selectedMaterials.length === 0) {
    ElMessage.warning(t('productionOrder.validation.issueQuantity'))
    return
  }

  const controlIssues = validateProductControlLines(selectedMaterials.map((material) => ({
    productId: material.materialProductId ?? material.materialId,
    productCode: material.productCode || material.materialCode,
    productName: material.productName || material.materialName,
    quantity: material.issueQty,
    lotNo: material.lotNo,
    serialNos: material.serialNos,
    lotControlled: material.lotControlled,
    shelfLifeControlled: material.shelfLifeControlled,
    serialControlled: material.serialControlled
  })))
  if (controlIssues.length > 0) {
    const issue = controlIssues[0]
    const product = issue.productCode || issue.productName || String(issue.productId)
    ElMessage.warning(t(`productionOrder.validation.${issue.messageKey}`, {
      line: issue.index + 1,
      product,
      expected: issue.expectedSerialCount,
      actual: issue.actualSerialCount
    }))
    return
  }

  const lines = selectedMaterials.map((material) => ({
    orderMaterialId: material.id,
    issueQty: material.issueQty,
    lotNo: material.lotNo || undefined,
    locationId: material.locationId || undefined,
    serialNos: material.serialNos || undefined,
    remark: material.remark || undefined
  }))

  submitLoading.value = true
  try {
    await issueProductionOrder(issueForm.orderId, {
      issueDate: issueForm.issueDate,
      remark: issueForm.remark || undefined,
      lines
    })
    ElMessage.success(t('productionOrder.message.issued'))
    issueDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('productionOrder.message.issueFailed'))
  } finally {
    submitLoading.value = false
  }
}

// 完工
const handleComplete = async (row: ProductionOrder) => {
  completeForm.orderId = row.id
  completeForm.maxQuantity = row.planQuantity - row.completedQuantity
  completeForm.completedQuantity = completeForm.maxQuantity
  completeForm.scrapQuantity = 0
  completeForm.completionDate = formatBusinessDate()
  completeForm.lotNo = ''
  completeForm.productionDate = ''
  completeForm.expiryDate = ''
  completeForm.locationId = undefined
  completeForm.serialNos = ''
  completeForm.remark = ''
  completeProductId.value = row.productId
  Object.assign(completeProductControls, productControlFromOptions(row.productId))
  completeDialogVisible.value = true
  void loadFinishedLocations(row.finishedWarehouseId || row.warehouseId)
  Object.assign(completeProductControls, await resolveProductControls(row.productId))
}

// 确认完工
const handleConfirmComplete = async () => {
  if (!completeForm.completedQuantity) {
    ElMessage.warning(t('productionOrder.validation.completedQuantity'))
    return
  }

  Object.assign(completeProductControls, await resolveProductControls(completeProductId.value))
  const controlIssues = validateProductControlLines([{
    productId: completeProductId.value || completeForm.orderId,
    productCode: completeProductControls.productCode,
    productName: completeProductControls.productName,
    quantity: completeForm.completedQuantity,
    lotNo: completeForm.lotNo,
    expiryDate: completeForm.expiryDate,
    serialNos: completeForm.serialNos,
    lotControlled: completeProductControls.lotControlled,
    shelfLifeControlled: completeProductControls.shelfLifeControlled,
    serialControlled: completeProductControls.serialControlled
  }])
  if (controlIssues.length > 0) {
    const issue = controlIssues[0]
    const product = issue.productCode || issue.productName || String(issue.productId)
    ElMessage.warning(t(`productionOrder.validation.${issue.messageKey}`, {
      line: 1,
      product,
      expected: issue.expectedSerialCount,
      actual: issue.actualSerialCount
    }))
    return
  }

  submitLoading.value = true
  try {
    await completeProductionOrder(completeForm.orderId, {
      completedQuantity: completeForm.completedQuantity,
      scrapQuantity: completeForm.scrapQuantity,
      completionDate: completeForm.completionDate,
      lotNo: completeForm.lotNo || undefined,
      productionDate: completeForm.productionDate || undefined,
      expiryDate: completeForm.expiryDate || undefined,
      locationId: completeForm.locationId || undefined,
      serialNos: completeForm.serialNos || undefined,
      remark: completeForm.remark
    })
    ElMessage.success(t('productionOrder.message.completed'))
    completeDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('productionOrder.message.completeFailed'))
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
  reverseForm.reversalDate = row.actualEndDate || row.planEndDate || formatBusinessDate()
  reverseForm.remark = ''
  reverseDialogVisible.value = true
}

const handleConfirmReverseCompletion = async () => {
  if (!reverseForm.reversedQty) {
    ElMessage.warning(t('productionOrder.validation.reversalQuantity'))
    return
  }

  submitLoading.value = true
  try {
    await reverseProductionCompletion(reverseForm.orderId, {
      reversedQty: reverseForm.reversedQty,
      reversalDate: reverseForm.reversalDate,
      remark: reverseForm.remark
    })
    ElMessage.success(t('productionOrder.message.reversed'))
    reverseDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('productionOrder.message.reverseFailed'))
  } finally {
    submitLoading.value = false
  }
}

const handleReturnMaterials = async (row: ProductionOrder) => {
  try {
    const order = await getProductionOrder(row.id)
    const returnableMaterials = await hydrateMaterialControls(
      (order.materials || [])
        .filter(material => Number(material.issuedQuantity || 0) > 0)
        .map(material => ({
          ...material,
          returnQty: 0,
          lotNo: '',
          locationId: undefined,
          serialNos: '',
          remark: '',
          productCode: material.materialCode,
          productName: material.materialName
        } as ReturnMaterialRow))
    )

    if (returnableMaterials.length === 0) {
      ElMessage.warning(t('productionOrder.message.noReturnableMaterials'))
      return
    }

    returnForm.orderId = order.id
    returnForm.returnDate = order.actualStartDate || order.planStartDate || formatBusinessDate()
    returnForm.remark = ''
    returnForm.materials = returnableMaterials
    returnDialogVisible.value = true
    void loadMaterialLocations(order.materialWarehouseId || order.warehouseId)
  } catch (error) {
    ElMessage.error(t('productionOrder.message.returnableLoadFailed'))
  }
}

const handleConfirmReturnMaterials = async () => {
  const selectedMaterials = returnForm.materials.filter(material => Number(material.returnQty || 0) > 0)
  if (selectedMaterials.length === 0) {
    ElMessage.warning(t('productionOrder.validation.returnQuantity'))
    return
  }

  const controlIssues = validateProductControlLines(selectedMaterials.map((material) => ({
    productId: material.materialProductId ?? material.materialId,
    productCode: material.productCode || material.materialCode,
    productName: material.productName || material.materialName,
    quantity: material.returnQty,
    lotNo: material.lotNo,
    serialNos: material.serialNos,
    lotControlled: material.lotControlled,
    shelfLifeControlled: material.shelfLifeControlled,
    serialControlled: material.serialControlled
  })))
  if (controlIssues.length > 0) {
    const issue = controlIssues[0]
    const product = issue.productCode || issue.productName || String(issue.productId)
    ElMessage.warning(t(`productionOrder.validation.${issue.messageKey}`, {
      line: issue.index + 1,
      product,
      expected: issue.expectedSerialCount,
      actual: issue.actualSerialCount
    }))
    return
  }

  const lines = selectedMaterials.map(material => ({
    orderMaterialId: material.id,
    returnQty: material.returnQty,
    lotNo: material.lotNo || undefined,
    locationId: material.locationId || undefined,
    serialNos: material.serialNos || undefined,
    remark: material.remark || undefined
  }))

  submitLoading.value = true
  try {
    await returnProductionMaterials(returnForm.orderId, {
      returnDate: returnForm.returnDate,
      remark: returnForm.remark,
      lines
    })
    ElMessage.success(t('productionOrder.message.returned'))
    returnDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error(t('productionOrder.message.returnFailed'))
  } finally {
    submitLoading.value = false
  }
}

// 取消
const handleCancel = async (row: ProductionOrder) => {
  try {
    await ElMessageBox.confirm(t('productionOrder.message.cancelConfirm', { orderNo: row.orderNo }), t('productionOrder.message.prompt'), {
      type: 'warning'
    })
    await cancelProductionOrder(row.id)
    ElMessage.success(t('productionOrder.message.cancelled'))
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('productionOrder.message.cancelFailed'))
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
  return `${code} - ${t('productionOrder.baseQuantity', { quantity: bom.baseQty })} - ${bom.status}`
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
        ElMessage.success(t('productionOrder.message.updated'))
      } else {
        await createProductionOrder(formData)
        ElMessage.success(t('productionOrder.message.created'))
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      ElMessage.error(t(formData.id ? 'productionOrder.message.updateFailed' : 'productionOrder.message.createFailed'))
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

.serial-progress {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.2;
}

.serial-progress--ok {
  color: var(--el-color-success);
}
</style>
