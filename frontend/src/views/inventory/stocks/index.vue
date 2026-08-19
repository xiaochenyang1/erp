<template>
  <div class="inventory-stocks-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item :label="$t('inventoryStocks.warehouse')">
          <el-select
            v-model="queryParams.warehouseId"
            :placeholder="$t('inventoryStocks.placeholder.warehouse')"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="warehouse.name"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.product')">
          <el-select
            v-model="queryParams.productId"
            :placeholder="$t('inventoryStocks.placeholder.product')"
            clearable
            filterable
            style="width: 260px"
          >
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.code || product.productCode || product.id} - ${product.name || product.productName || '-'}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.location')">
          <el-select
            v-model="queryParams.locationId"
            :placeholder="$t('inventoryStocks.placeholder.location')"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="location in locationsForQuery"
              :key="location.id"
              :label="`${location.locationCode} ${location.locationName}`"
              :value="location.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">{{ $t('inventoryStocks.action.search') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('inventoryStocks.action.reset') }}</el-button>
          <el-button @click="handleOpenLotBalances()">{{ $t('inventoryStocks.action.lotStock') }}</el-button>
          <el-button @click="handleOpenTransactions()">{{ $t('inventoryStocks.action.transactions') }}</el-button>
          <el-button :icon="Warning" @click="handleOpenLotAlerts()">{{ $t('inventoryStocks.action.expiryAlerts') }}</el-button>
          <el-button v-permission="'inventory:reservation:check'" :icon="Warning" @click="handleReservationCheck">
            {{ $t('inventoryStocks.action.reservationCheck') }}
          </el-button>
          <el-button :icon="Download" @click="handleExport">{{ $t('inventoryStocks.action.export') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('inventoryStocks.stockQuery') }}</span>
          <el-button :icon="Refresh" @click="loadData">{{ $t('inventoryStocks.action.refresh') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="warehouseId" :label="$t('inventoryStocks.warehouse')" min-width="160">
          <template #default="{ row }">
            {{ warehouseName(row.warehouseId) }}
          </template>
        </el-table-column>
        <el-table-column prop="locationId" :label="$t('inventoryStocks.location')" min-width="160">
          <template #default="{ row }">
            {{ locationName(row.locationId) }}
          </template>
        </el-table-column>
        <el-table-column prop="productId" :label="$t('inventoryStocks.product')" min-width="220">
          <template #default="{ row }">
            {{ productName(row.productId) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" :label="$t('inventoryStocks.bookStock')" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="reservedQuantity" :label="$t('inventoryStocks.reserved')" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="availableQuantity" :label="$t('inventoryStocks.availableStock')" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.availableQuantity < 0 }">
              {{ formatNumber(row.availableQuantity) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="amountOnHand" :label="$t('inventoryStocks.stockAmount')" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.amountOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="lastUpdated" :label="$t('inventoryStocks.updatedTime')" width="180" />
        <el-table-column :label="$t('inventoryStocks.actions')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewStock(row)">{{ $t('inventoryStocks.action.view') }}</el-button>
            <el-button
              v-permission="'inventory:reservation:view'"
              link
              type="primary"
              @click="handleOpenReservations(row)"
            >
              {{ $t('inventoryStocks.action.reservationDetails') }}
            </el-button>
            <el-button link type="primary" @click="handleOpenLotBalances(row)">{{ $t('inventoryStocks.action.lots') }}</el-button>
            <el-button link type="primary" @click="handleOpenTransactions(row)">{{ $t('inventoryStocks.action.transaction') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="reservationDialogVisible" :title="$t('inventoryStocks.reservationDetails')" width="1080px">
      <el-form :model="reservationQuery" inline>
        <el-form-item :label="$t('inventoryStocks.statusLabel')">
          <el-select
            v-model="reservationQuery.status"
            :placeholder="$t('inventoryStocks.placeholder.allStatuses')"
            clearable
            style="width: 150px"
            @change="handleReservationQuery"
          >
            <el-option :label="$t('inventoryStocks.reservationStatus.active')" value="ACTIVE" />
            <el-option :label="$t('inventoryStocks.reservationStatus.released')" value="RELEASED" />
            <el-option :label="$t('inventoryStocks.reservationStatus.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.sourceNo')">
          <el-input
            v-model="reservationQuery.sourceNo"
            :placeholder="$t('inventoryStocks.placeholder.sourceNo')"
            clearable
            style="width: 220px"
            @keyup.enter="handleReservationQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleReservationQuery">{{ $t('inventoryStocks.action.search') }}</el-button>
          <el-button :icon="Refresh" @click="resetReservationQuery">{{ $t('inventoryStocks.action.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <div class="dialog-section-header">
        <span>{{ $t('inventoryStocks.reservationSummary') }}</span>
      </div>
      <el-table v-loading="reservationSummaryLoading" :data="reservationSummaryData" border stripe class="summary-table">
        <el-table-column prop="sourceType" :label="$t('inventoryStocks.sourceType')" min-width="130">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('inventoryStocks.statusLabel')" width="110">
          <template #default="{ row }">{{ reservationStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="reservedQty" :label="$t('inventoryStocks.reservedQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedQty) }}</template>
        </el-table-column>
        <el-table-column prop="releasedQty" :label="$t('inventoryStocks.releasedQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.releasedQty) }}</template>
        </el-table-column>
        <el-table-column prop="remainingQty" :label="$t('inventoryStocks.remainingReserved')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.remainingQty) }}</template>
        </el-table-column>
        <el-table-column prop="reservationCount" :label="$t('inventoryStocks.reservationCount')" width="110" align="right" />
        <el-table-column prop="qtyAvailable" :label="$t('inventoryStocks.availableStock')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
      </el-table>

      <el-table v-loading="reservationLoading" :data="reservationData" border stripe>
        <el-table-column prop="sourceType" :label="$t('inventoryStocks.sourceType')" width="140">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceNo" :label="$t('inventoryStocks.sourceNo')" min-width="160" />
        <el-table-column prop="reservedQty" :label="$t('inventoryStocks.reservedQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedQty) }}</template>
        </el-table-column>
        <el-table-column prop="releasedQty" :label="$t('inventoryStocks.releasedQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.releasedQty) }}</template>
        </el-table-column>
        <el-table-column prop="remainingQty" :label="$t('inventoryStocks.remainingReserved')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.remainingQty) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('inventoryStocks.statusLabel')" width="110">
          <template #default="{ row }">
            <el-tag :type="reservationStatusType(row.status)">{{ reservationStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" :label="$t('inventoryStocks.updatedTime')" width="180" />
        <el-table-column :label="$t('inventoryStocks.actions')" width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'inventory:reservation:view'"
              link
              type="primary"
              @click="handleViewReservation(row)"
            >
              {{ $t('inventoryStocks.action.view') }}
            </el-button>
            <el-button
              v-permission="'inventory:reservation:view'"
              link
              type="primary"
              @click="handleViewReservationSource(row)"
            >
              {{ $t('inventoryStocks.action.source') }}
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE' && Number(row.remainingQty) > 0"
              v-permission="'inventory:reservation:release'"
              link
              type="warning"
              @click="openReleaseDialog(row)"
            >
              {{ $t('inventoryStocks.action.release') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="reservationQuery.pageNo"
        v-model:page-size="reservationQuery.pageSize"
        :total="reservationTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleReservationSizeChange"
        @current-change="handleReservationPageChange"
      />
    </el-dialog>

    <el-dialog v-model="reservationDetailVisible" :title="$t('inventoryStocks.reservationDetail')" width="960px">
      <el-skeleton v-if="reservationDetailLoading" :rows="6" animated />
      <template v-else-if="reservationDetail">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="$t('inventoryStocks.warehouse')">
            {{ warehouseName(reservationDetail.reservation.warehouseId) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.product')">
            {{ productName(reservationDetail.reservation.productId) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.statusLabel')">
            <el-tag :type="reservationStatusType(reservationDetail.reservation.status)">
              {{ reservationStatusLabel(reservationDetail.reservation.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.sourceType')">
            {{ sourceTypeLabel(reservationDetail.reservation.sourceType) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.sourceNo')">
            {{ reservationDetail.reservation.sourceNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.sourceLine')">
            {{ reservationDetail.reservation.sourceLineId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.reservedQuantity')">
            {{ formatNumber(reservationDetail.reservation.reservedQty) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.releasedQuantity')">
            {{ formatNumber(reservationDetail.reservation.releasedQty) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.remainingReserved')">
            {{ formatNumber(reservationDetail.reservation.remainingQty) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.remark')" :span="3">
            {{ reservationDetail.reservation.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="dialog-section-header">
          <span>{{ $t('inventoryStocks.eventRecords') }}</span>
          <el-button
            v-if="reservationDetail.reservation.status === 'ACTIVE' && Number(reservationDetail.reservation.remainingQty) > 0"
            v-permission="'inventory:reservation:release'"
            type="warning"
            @click="openReleaseDialog(reservationDetail.reservation)"
          >
            {{ $t('inventoryStocks.action.manualRelease') }}
          </el-button>
        </div>
        <el-table :data="reservationDetail.events" border stripe>
          <el-table-column prop="eventType" :label="$t('inventoryStocks.eventType')" width="130">
            <template #default="{ row }">{{ reservationEventLabel(row.eventType) }}</template>
          </el-table-column>
          <el-table-column prop="eventQty" :label="$t('inventoryStocks.eventQuantity')" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.eventQty) }}</template>
          </el-table-column>
          <el-table-column prop="remainingQtyBefore" :label="$t('inventoryStocks.beforeChange')" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.remainingQtyBefore) }}</template>
          </el-table-column>
          <el-table-column prop="remainingQtyAfter" :label="$t('inventoryStocks.afterChange')" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.remainingQtyAfter) }}</template>
          </el-table-column>
          <el-table-column prop="reason" :label="$t('inventoryStocks.reason')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createdBy" :label="$t('inventoryStocks.operator')" width="120" />
          <el-table-column prop="createdTime" :label="$t('inventoryStocks.operationTime')" width="180" />
        </el-table>
      </template>
    </el-dialog>

    <el-dialog v-model="releaseDialogVisible" :title="$t('inventoryStocks.dialog.manualRelease')" width="460px">
      <el-form ref="releaseFormRef" :model="releaseForm" :rules="releaseRules" label-width="90px">
        <el-form-item :label="$t('inventoryStocks.releaseQuantity')" prop="qty">
          <el-input-number
            v-model="releaseForm.qty"
            :min="0.0001"
            :precision="4"
            :step="1"
            :max="selectedReservation?.remainingQty || undefined"
            controls-position="right"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.releaseReason')" prop="reason">
          <el-input
            v-model="releaseForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="$t('inventoryStocks.placeholder.releaseReason')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="releaseDialogVisible = false">{{ $t('inventoryStocks.action.cancel') }}</el-button>
        <el-button
          v-permission="'inventory:reservation:release'"
          type="primary"
          :loading="releasing"
          @click="submitManualRelease"
        >
          {{ $t('inventoryStocks.action.confirmRelease') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkDialogVisible" :title="$t('inventoryStocks.reservationCheck')" width="980px">
      <el-alert
        v-if="reservationCheckFailed"
        :title="$t('inventoryStocks.message.reservationCheckFailed')"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="!checkIssues.length && !reservationCheckLoading && !reservationCheckFailed"
        :title="$t('inventoryStocks.noReservationIssues')"
        type="success"
        :closable="false"
        show-icon
      />
      <el-table v-else v-loading="reservationCheckLoading" :data="checkIssues" border stripe>
        <el-table-column prop="severity" :label="$t('inventoryStocks.severity')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'ERROR' ? 'danger' : 'warning'">{{ severityLabel(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="issueType" :label="$t('inventoryStocks.issueType')" width="220" />
        <el-table-column prop="sourceNo" :label="$t('inventoryStocks.sourceNo')" width="160" />
        <el-table-column prop="expectedQty" :label="$t('inventoryStocks.expectedValue')" width="120" align="right">
          <template #default="{ row }">{{ formatOptionalNumber(row.expectedQty) }}</template>
        </el-table-column>
        <el-table-column prop="actualQty" :label="$t('inventoryStocks.actualValue')" width="120" align="right">
          <template #default="{ row }">{{ formatOptionalNumber(row.actualQty) }}</template>
        </el-table-column>
        <el-table-column prop="message" :label="$t('inventoryStocks.description')" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <el-dialog v-model="lotBalanceDialogVisible" :title="$t('inventoryStocks.lotStock')" width="1120px">
      <el-form :model="lotBalanceQuery" inline>
        <el-form-item :label="$t('inventoryStocks.lotNo')">
          <el-input
            v-model="lotBalanceQuery.lotNo"
            clearable
            :placeholder="$t('inventoryStocks.placeholder.lotNo')"
            style="width: 200px"
            @keyup.enter="handleLotBalanceQuery"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.expiringDays')">
          <el-input-number
            v-model="lotBalanceQuery.expiringWithinDays"
            :min="1"
            :max="3650"
            controls-position="right"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleLotBalanceQuery">{{ $t('inventoryStocks.action.search') }}</el-button>
          <el-button :icon="Refresh" @click="resetLotBalanceQuery">{{ $t('inventoryStocks.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="lotBalanceLoading" :data="lotBalanceData" border stripe>
        <el-table-column prop="warehouseId" :label="$t('inventoryStocks.warehouse')" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" :label="$t('inventoryStocks.product')" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" :label="$t('inventoryStocks.lotNo')" min-width="140" />
        <el-table-column prop="productionDate" :label="$t('inventoryStocks.productionDate')" width="120" />
        <el-table-column prop="expiryDate" :label="$t('inventoryStocks.expiryDate')" width="120" />
        <el-table-column prop="qtyOnHand" :label="$t('inventoryStocks.bookQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="qtyReserved" :label="$t('inventoryStocks.reservedQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyReserved) }}</template>
        </el-table-column>
        <el-table-column prop="qtyAvailable" :label="$t('inventoryStocks.availableQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
        <el-table-column :label="$t('inventoryStocks.actions')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewLotBalance(row)">{{ $t('inventoryStocks.action.view') }}</el-button>
            <el-button link type="primary" @click="handleOpenLotTrace(row)">{{ $t('inventoryStocks.action.trace') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="lotBalanceQuery.pageNo"
        v-model:page-size="lotBalanceQuery.pageSize"
        :total="lotBalanceTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleLotBalanceSizeChange"
        @current-change="handleLotBalancePageChange"
      />
    </el-dialog>

    <el-dialog v-model="transactionDialogVisible" :title="$t('inventoryStocks.transactions')" width="1120px">
      <el-form :model="transactionQuery" inline>
        <el-form-item :label="$t('inventoryStocks.businessNo')">
          <el-input
            v-model="transactionQuery.bizNo"
            clearable
            :placeholder="$t('inventoryStocks.placeholder.businessNo')"
            style="width: 200px"
            @keyup.enter="handleTransactionQuery"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.direction')">
          <el-select v-model="transactionQuery.direction" clearable :placeholder="$t('inventoryStocks.placeholder.all')" style="width: 120px">
            <el-option :label="$t('inventoryStocks.directionValue.inbound')" value="IN" />
            <el-option :label="$t('inventoryStocks.directionValue.outbound')" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleTransactionQuery">{{ $t('inventoryStocks.action.search') }}</el-button>
          <el-button :icon="Refresh" @click="resetTransactionQuery">{{ $t('inventoryStocks.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="transactionLoading" :data="transactionData" border stripe>
        <el-table-column prop="warehouseId" :label="$t('inventoryStocks.warehouse')" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" :label="$t('inventoryStocks.product')" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="bizType" :label="$t('inventoryStocks.businessType')" width="140" />
        <el-table-column prop="bizNo" :label="$t('inventoryStocks.businessNo')" min-width="150" />
        <el-table-column prop="direction" :label="$t('inventoryStocks.direction')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qty" :label="$t('inventoryStocks.quantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="amount" :label="$t('inventoryStocks.amount')" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="occurredTime" :label="$t('inventoryStocks.occurredTime')" width="180" />
        <el-table-column prop="remark" :label="$t('inventoryStocks.remark')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$t('inventoryStocks.actions')" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewTransaction(row)">{{ $t('inventoryStocks.action.view') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="transactionQuery.pageNo"
        v-model:page-size="transactionQuery.pageSize"
        :total="transactionTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleTransactionSizeChange"
        @current-change="handleTransactionPageChange"
      />
    </el-dialog>

    <el-dialog v-model="lotAlertDialogVisible" :title="$t('inventoryStocks.expiryAlerts')" width="1120px">
      <el-form :model="lotAlertQuery" inline>
        <el-form-item :label="$t('inventoryStocks.lotNo')">
          <el-input
            v-model="lotAlertQuery.lotNo"
            clearable
            :placeholder="$t('inventoryStocks.placeholder.lotNo')"
            style="width: 180px"
            @keyup.enter="handleLotAlertQuery"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.warningDays')">
          <el-input-number
            v-model="lotAlertQuery.warningDays"
            :min="1"
            :max="3650"
            controls-position="right"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item :label="$t('inventoryStocks.statusLabel')">
          <el-select v-model="lotAlertQuery.status" clearable :placeholder="$t('inventoryStocks.placeholder.all')" style="width: 140px">
            <el-option :label="$t('inventoryStocks.expiryStatus.expired')" value="EXPIRED" />
            <el-option :label="$t('inventoryStocks.expiryStatus.expiring')" value="EXPIRING" />
            <el-option :label="$t('inventoryStocks.expiryStatus.normal')" value="NORMAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleLotAlertQuery">{{ $t('inventoryStocks.action.search') }}</el-button>
          <el-button :icon="Refresh" @click="resetLotAlertQuery">{{ $t('inventoryStocks.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="lotAlertLoading" :data="lotAlertData" border stripe>
        <el-table-column prop="warehouseId" :label="$t('inventoryStocks.warehouse')" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" :label="$t('inventoryStocks.product')" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" :label="$t('inventoryStocks.lotNo')" min-width="140" />
        <el-table-column prop="expiryDate" :label="$t('inventoryStocks.expiryDate')" width="120" />
        <el-table-column prop="daysToExpiry" :label="$t('inventoryStocks.daysToExpiry')" width="110" align="right" />
        <el-table-column prop="expiryStatus" :label="$t('inventoryStocks.statusLabel')" width="120">
          <template #default="{ row }">
            <el-tag :type="expiryStatusType(row.expiryStatus)">{{ expiryStatusLabel(row.expiryStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qtyAvailable" :label="$t('inventoryStocks.availableQuantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
        <el-table-column prop="updatedTime" :label="$t('inventoryStocks.updatedTime')" width="180" />
      </el-table>
      <el-pagination
        v-model:current-page="lotAlertQuery.pageNo"
        v-model:page-size="lotAlertQuery.pageSize"
        :total="lotAlertTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleLotAlertSizeChange"
        @current-change="handleLotAlertPageChange"
      />
    </el-dialog>

    <el-dialog v-model="lotTraceDialogVisible" :title="$t('inventoryStocks.lotTrace')" width="1120px">
      <el-table v-loading="lotTraceLoading" :data="lotTraceData" border stripe>
        <el-table-column prop="warehouseId" :label="$t('inventoryStocks.warehouse')" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="locationId" :label="$t('inventoryStocks.location')" min-width="140">
          <template #default="{ row }">{{ locationName(row.locationId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" :label="$t('inventoryStocks.product')" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" :label="$t('inventoryStocks.lotNo')" min-width="140" />
        <el-table-column prop="bizType" :label="$t('inventoryStocks.businessType')" width="140" />
        <el-table-column prop="bizNo" :label="$t('inventoryStocks.businessNo')" min-width="150">
          <template #default="{ row }">
            <el-button
              v-if="row.documentRoute"
              link
              type="primary"
              @click="openTraceDocument(row.documentRoute)"
            >
              {{ row.bizNo }}
            </el-button>
            <span v-else>{{ row.bizNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="documentLabel" :label="$t('inventoryStocks.documentType')" min-width="120">
          <template #default="{ row }">{{ row.documentLabel || row.bizType }}</template>
        </el-table-column>
        <el-table-column :label="$t('inventoryStocks.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'inventory:lot:genealogy'"
              link
              type="primary"
              @click="router.push({ path: '/inventory/lot-genealogy', query: { productId: String(row.productId), lotNo: row.lotNo || '' } })"
            >
              {{ $t('inventoryStocks.action.viewGenealogy') }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="direction" :label="$t('inventoryStocks.direction')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qty" :label="$t('inventoryStocks.quantity')" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="occurredTime" :label="$t('inventoryStocks.occurredTime')" width="180" />
        <el-table-column prop="remark" :label="$t('inventoryStocks.remark')" min-width="160" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-model:current-page="lotTraceQuery.pageNo"
        v-model:page-size="lotTraceQuery.pageSize"
        :total="lotTraceTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleLotTraceSizeChange"
        @current-change="handleLotTracePageChange"
      />
    </el-dialog>

    <el-dialog v-model="stockDetailVisible" :title="$t('inventoryStocks.stockDetail')" width="760px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedStock" :column="2" border>
        <el-descriptions-item :label="$t('inventoryStocks.warehouse')">{{ warehouseName(selectedStock.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.location')">{{ locationName(selectedStock.locationId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.product')">{{ productName(selectedStock.productId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.bookStock')">{{ formatNumber(selectedStock.quantity) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.reserved')">{{ formatNumber(selectedStock.reservedQuantity) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.availableStock')">{{ formatNumber(selectedStock.availableQuantity) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.stockAmount')">{{ formatMoney(selectedStock.amountOnHand) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.unit')">{{ selectedStock.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.updatedTime')">{{ selectedStock.lastUpdated || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="lotBalanceDetailVisible" :title="$t('inventoryStocks.lotStockDetail')" width="760px">
      <el-skeleton v-if="lotBalanceDetailLoading" :rows="6" animated />
      <el-descriptions v-else-if="selectedLotBalance" :column="2" border>
        <el-descriptions-item :label="$t('inventoryStocks.warehouse')">{{ warehouseName(selectedLotBalance.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.product')">{{ productName(selectedLotBalance.productId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.lotNo')">{{ selectedLotBalance.lotNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.productionDate')">{{ selectedLotBalance.productionDate || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.expiryDate')">{{ selectedLotBalance.expiryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.firstInbound')">{{ selectedLotBalance.firstInboundTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.bookQuantity')">{{ formatNumber(selectedLotBalance.qtyOnHand) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.reservedQuantity')">{{ formatNumber(selectedLotBalance.qtyReserved) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.availableQuantity')">{{ formatNumber(selectedLotBalance.qtyAvailable) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.stockAmount')">{{ formatMoney(selectedLotBalance.amountOnHand) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.updatedTime')">{{ selectedLotBalance.updatedTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="transactionDetailVisible" :title="$t('inventoryStocks.transactionDetail')" width="760px">
      <el-skeleton v-if="transactionDetailLoading" :rows="6" animated />
      <el-descriptions v-else-if="selectedTransaction" :column="2" border>
        <el-descriptions-item :label="$t('inventoryStocks.warehouse')">{{ warehouseName(selectedTransaction.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.product')">{{ productName(selectedTransaction.productId) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.businessType')">{{ selectedTransaction.bizType }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.businessNo')">{{ selectedTransaction.bizNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.businessDetail')">{{ selectedTransaction.bizLineId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.direction')">{{ directionLabel(selectedTransaction.direction) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.quantity')">{{ formatNumber(selectedTransaction.qty) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.unitPrice')">{{ formatMoney(selectedTransaction.unitCost) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.amount')">{{ formatMoney(selectedTransaction.amount) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.occurredTime')">{{ selectedTransaction.occurredTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('inventoryStocks.remark')" :span="2">{{ selectedTransaction.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="reservationSourceVisible" :title="$t('inventoryStocks.sourceReservationDetail')" width="980px">
      <el-skeleton v-if="reservationSourceLoading" :rows="5" animated />
      <template v-else-if="reservationSourceDetail">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="$t('inventoryStocks.sourceType')">{{ sourceTypeLabel(reservationSourceDetail.sourceType) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.sourceId')">{{ reservationSourceDetail.sourceId || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('inventoryStocks.sourceNo')">{{ reservationSourceDetail.sourceNo || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="reservationSourceDetail.reservations" border stripe class="detail-table">
          <el-table-column prop="reservation.warehouseId" :label="$t('inventoryStocks.warehouse')" min-width="140">
            <template #default="{ row }">{{ warehouseName(row.reservation.warehouseId) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.productId" :label="$t('inventoryStocks.product')" min-width="180">
            <template #default="{ row }">{{ productName(row.reservation.productId) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.reservedQty" :label="$t('inventoryStocks.reservedQuantity')" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.reservation.reservedQty) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.releasedQty" :label="$t('inventoryStocks.releasedQuantity')" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.reservation.releasedQty) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.remainingQty" :label="$t('inventoryStocks.remainingReserved')" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.reservation.remainingQty) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.status" :label="$t('inventoryStocks.statusLabel')" width="110">
            <template #default="{ row }">
              <el-tag :type="reservationStatusType(row.reservation.status)">
                {{ reservationStatusLabel(row.reservation.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search, Warning } from '@element-plus/icons-vue'
import { useInventoryStockBalanceList } from '@/composables/useInventoryStockBalanceList'
import { useInventoryStockDetails } from '@/composables/useInventoryStockDetails'
import { useInventoryStockExpiryAlertList } from '@/composables/useInventoryStockExpiryAlertList'
import { useInventoryStockLotBalanceList } from '@/composables/useInventoryStockLotBalanceList'
import { useInventoryStockQueries } from '@/composables/useInventoryStockQueries'
import { useInventoryStockPresentation } from '@/composables/useInventoryStockPresentation'
import { useInventoryStockReservationList } from '@/composables/useInventoryStockReservationList'
import { useInventoryStockTransactionList } from '@/composables/useInventoryStockTransactionList'

const route = useRoute()
const { t } = useI18n()
const router = useRouter()

const { queryParams } = useInventoryStockQueries({
  warehouseId: route.query.warehouseId ? String(route.query.warehouseId) : undefined,
  productId: route.query.productId ? String(route.query.productId) : undefined,
  locationId: route.query.locationId ? String(route.query.locationId) : undefined
})

const {
  handleExport,
  handlePageChange,
  handleQuery,
  handleReset,
  handleSizeChange,
  loadData,
  loadOptions,
  loading,
  locations,
  locationsForQuery,
  products,
  tableData,
  total,
  warehouses
} = useInventoryStockBalanceList(queryParams, {
  t,
  onError: (messageKey, error) => {
    if (error) console.error(t(messageKey), error)
    ElMessage.error(t(messageKey))
  },
  onOptionsError: (messageKey, error) => console.error(t(messageKey), error),
  onSuccess: (messageKey) => ElMessage.success(t(messageKey))
})

const {
  detailLoading,
  handleViewStock,
  selectedStock,
  stockDetailVisible
} = useInventoryStockDetails((messageKey) => ElMessage.error(t(messageKey)))

const {
  checkDialogVisible,
  checkFailed: reservationCheckFailed,
  checkIssues,
  checkLoading: reservationCheckLoading,
  handleOpenReservations,
  handleReservationCheck,
  handleReservationPageChange,
  handleReservationQuery,
  handleReservationSizeChange,
  handleViewReservation,
  handleViewReservationSource,
  openReleaseDialog,
  releaseDialogVisible,
  releaseForm,
  releaseFormRef,
  releaseRules,
  releasing,
  reservationData,
  reservationDetail,
  reservationDetailLoading,
  reservationDetailVisible,
  reservationDialogVisible,
  reservationLoading,
  reservationQuery,
  reservationSourceDetail,
  reservationSourceLoading,
  reservationSourceVisible,
  reservationSummaryData,
  reservationSummaryLoading,
  reservationTotal,
  resetReservationQuery,
  selectedReservation,
  submitManualRelease
} = useInventoryStockReservationList(queryParams, {
  t,
  onError: (messageKey, error) => {
    if (error) console.error(t(messageKey), error)
    ElMessage.error(t(messageKey))
  },
  onSuccess: (messageKey) => ElMessage.success(t(messageKey)),
  reloadStockList: loadData
})

const {
  handleLotBalancePageChange,
  handleLotBalanceQuery,
  handleLotBalanceSizeChange,
  handleLotTracePageChange,
  handleLotTraceSizeChange,
  handleOpenLotBalances,
  handleOpenLotTrace,
  handleViewLotBalance,
  lotBalanceData,
  lotBalanceDetailLoading,
  lotBalanceDetailVisible,
  lotBalanceDialogVisible,
  lotBalanceLoading,
  lotBalanceQuery,
  lotBalanceTotal,
  lotTraceData,
  lotTraceDialogVisible,
  lotTraceLoading,
  lotTraceQuery,
  lotTraceTotal,
  resetLotBalanceQuery,
  selectedLotBalance
} = useInventoryStockLotBalanceList(queryParams, {
  onDetailError: (messageKey) => ElMessage.error(t(messageKey)),
  onListError: (messageKey, error) => {
    console.error(t(messageKey), error)
    ElMessage.error(t(messageKey))
  }
})

const {
  handleOpenTransactions,
  handleTransactionPageChange,
  handleTransactionQuery,
  handleTransactionSizeChange,
  handleViewTransaction,
  resetTransactionQuery,
  selectedTransaction,
  transactionData,
  transactionDetailLoading,
  transactionDetailVisible,
  transactionDialogVisible,
  transactionLoading,
  transactionQuery,
  transactionTotal
} = useInventoryStockTransactionList(queryParams, {
  onDetailError: (messageKey) => ElMessage.error(t(messageKey)),
  onListError: (messageKey, error) => {
    console.error(t(messageKey), error)
    ElMessage.error(t(messageKey))
  }
})

const {
  handleLotAlertPageChange,
  handleLotAlertQuery,
  handleLotAlertSizeChange,
  handleOpenLotAlerts,
  lotAlertData,
  lotAlertDialogVisible,
  lotAlertLoading,
  lotAlertQuery,
  lotAlertTotal,
  resetLotAlertQuery
} = useInventoryStockExpiryAlertList(queryParams, {
  onError: (messageKey, error) => {
    console.error(t(messageKey), error)
    ElMessage.error(t(messageKey))
  }
})

const {
  directionLabel,
  expiryStatusLabel,
  expiryStatusType,
  formatMoney,
  formatNumber,
  formatOptionalNumber,
  locationName,
  productName,
  reservationEventLabel,
  reservationStatusLabel,
  reservationStatusType,
  severityLabel,
  sourceTypeLabel,
  warehouseName
} = useInventoryStockPresentation(warehouses, products, t, locations)

const openTraceDocument = (route: string) => {
  if (!route) return
  router.push(route)
}

onMounted(async () => {
  await loadOptions()
  void loadData()
})
</script>

<style scoped lang="scss">
.inventory-stocks-container {
  padding: 20px;

  .search-card,
  .table-card {
    margin-bottom: 20px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .el-pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }

  .text-danger {
    color: #f56c6c;
    font-weight: 600;
  }

  .dialog-section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 16px 0 10px;
    font-weight: 600;
  }

  .summary-table,
  .detail-table {
    margin-top: 12px;
  }
}
</style>
