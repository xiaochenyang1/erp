<template>
  <div class="inventory-stocks-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
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
        <el-form-item label="产品">
          <el-select
            v-model="queryParams.productId"
            placeholder="请选择产品"
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
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button @click="handleOpenLotBalances()">批次库存</el-button>
          <el-button @click="handleOpenTransactions()">库存流水</el-button>
          <el-button :icon="Warning" @click="handleOpenLotAlerts()">效期预警</el-button>
          <el-button :icon="Warning" @click="handleReservationCheck">预留检查</el-button>
          <el-button :icon="Download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>库存查询</span>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="warehouseId" label="仓库" min-width="160">
          <template #default="{ row }">
            {{ warehouseName(row.warehouseId) }}
          </template>
        </el-table-column>
        <el-table-column prop="productId" label="产品" min-width="220">
          <template #default="{ row }">
            {{ productName(row.productId) }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="账面库存" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="reservedQuantity" label="已预留" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="availableQuantity" label="可用库存" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.availableQuantity < 0 }">
              {{ formatNumber(row.availableQuantity) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="amountOnHand" label="库存金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.amountOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="lastUpdated" label="更新时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewStock(row)">详情</el-button>
            <el-button link type="primary" @click="handleOpenReservations(row)">预留明细</el-button>
            <el-button link type="primary" @click="handleOpenLotBalances(row)">批次</el-button>
            <el-button link type="primary" @click="handleOpenTransactions(row)">流水</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNo"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="reservationDialogVisible" title="预留明细" width="1080px">
      <el-form :model="reservationQuery" inline>
        <el-form-item label="状态">
          <el-select
            v-model="reservationQuery.status"
            placeholder="全部状态"
            clearable
            style="width: 150px"
            @change="handleReservationQuery"
          >
            <el-option label="有效" value="ACTIVE" />
            <el-option label="已释放" value="RELEASED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input
            v-model="reservationQuery.sourceNo"
            placeholder="请输入来源单号"
            clearable
            style="width: 220px"
            @keyup.enter="handleReservationQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleReservationQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetReservationQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="dialog-section-header">
        <span>预留汇总</span>
      </div>
      <el-table v-loading="reservationSummaryLoading" :data="reservationSummaryData" border stripe class="summary-table">
        <el-table-column prop="sourceType" label="来源类型" min-width="130">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">{{ reservationStatusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="reservedQty" label="预留数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedQty) }}</template>
        </el-table-column>
        <el-table-column prop="releasedQty" label="已释放" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.releasedQty) }}</template>
        </el-table-column>
        <el-table-column prop="remainingQty" label="剩余预留" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.remainingQty) }}</template>
        </el-table-column>
        <el-table-column prop="reservationCount" label="预留笔数" width="110" align="right" />
        <el-table-column prop="qtyAvailable" label="可用库存" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
      </el-table>

      <el-table v-loading="reservationLoading" :data="reservationData" border stripe>
        <el-table-column prop="sourceType" label="来源类型" width="140">
          <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceNo" label="来源单号" min-width="160" />
        <el-table-column prop="reservedQty" label="预留数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedQty) }}</template>
        </el-table-column>
        <el-table-column prop="releasedQty" label="已释放" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.releasedQty) }}</template>
        </el-table-column>
        <el-table-column prop="remainingQty" label="剩余预留" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.remainingQty) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="reservationStatusType(row.status)">{{ reservationStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewReservation(row)">详情</el-button>
            <el-button link type="primary" @click="handleViewReservationSource(row)">来源</el-button>
            <el-button
              v-if="row.status === 'ACTIVE' && Number(row.remainingQty) > 0"
              v-permission="'inventory:reservation:release'"
              link
              type="warning"
              @click="openReleaseDialog(row)"
            >
              释放
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
        @size-change="handleReservationQuery"
        @current-change="loadReservations"
      />
    </el-dialog>

    <el-dialog v-model="reservationDetailVisible" title="预留详情" width="960px">
      <template v-if="reservationDetail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="仓库">
            {{ warehouseName(reservationDetail.reservation.warehouseId) }}
          </el-descriptions-item>
          <el-descriptions-item label="产品">
            {{ productName(reservationDetail.reservation.productId) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="reservationStatusType(reservationDetail.reservation.status)">
              {{ reservationStatusLabel(reservationDetail.reservation.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="来源类型">
            {{ sourceTypeLabel(reservationDetail.reservation.sourceType) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源单号">
            {{ reservationDetail.reservation.sourceNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="来源明细">
            {{ reservationDetail.reservation.sourceLineId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预留数量">
            {{ formatNumber(reservationDetail.reservation.reservedQty) }}
          </el-descriptions-item>
          <el-descriptions-item label="已释放">
            {{ formatNumber(reservationDetail.reservation.releasedQty) }}
          </el-descriptions-item>
          <el-descriptions-item label="剩余预留">
            {{ formatNumber(reservationDetail.reservation.remainingQty) }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">
            {{ reservationDetail.reservation.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="dialog-section-header">
          <span>事件记录</span>
          <el-button
            v-if="reservationDetail.reservation.status === 'ACTIVE' && Number(reservationDetail.reservation.remainingQty) > 0"
            v-permission="'inventory:reservation:release'"
            type="warning"
            @click="openReleaseDialog(reservationDetail.reservation)"
          >
            手工释放
          </el-button>
        </div>
        <el-table :data="reservationDetail.events" border stripe>
          <el-table-column prop="eventType" label="事件类型" width="130">
            <template #default="{ row }">{{ reservationEventLabel(row.eventType) }}</template>
          </el-table-column>
          <el-table-column prop="eventQty" label="事件数量" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.eventQty) }}</template>
          </el-table-column>
          <el-table-column prop="remainingQtyBefore" label="变更前" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.remainingQtyBefore) }}</template>
          </el-table-column>
          <el-table-column prop="remainingQtyAfter" label="变更后" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.remainingQtyAfter) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createdBy" label="操作人" width="120" />
          <el-table-column prop="createdTime" label="操作时间" width="180" />
        </el-table>
      </template>
    </el-dialog>

    <el-dialog v-model="releaseDialogVisible" title="手工释放预留" width="460px">
      <el-form ref="releaseFormRef" :model="releaseForm" :rules="releaseRules" label-width="90px">
        <el-form-item label="释放数量" prop="qty">
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
        <el-form-item label="释放原因" prop="reason">
          <el-input
            v-model="releaseForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请输入释放原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="releaseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="releasing" @click="submitManualRelease">确认释放</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkDialogVisible" title="预留检查" width="980px">
      <el-alert
        v-if="!checkIssues.length && !checkLoading"
        title="当前筛选范围内未发现预留异常"
        type="success"
        :closable="false"
        show-icon
      />
      <el-table v-else v-loading="checkLoading" :data="checkIssues" border stripe>
        <el-table-column prop="severity" label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'ERROR' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="issueType" label="异常类型" width="220" />
        <el-table-column prop="sourceNo" label="来源单号" width="160" />
        <el-table-column prop="expectedQty" label="期望值" width="120" align="right">
          <template #default="{ row }">{{ formatOptionalNumber(row.expectedQty) }}</template>
        </el-table-column>
        <el-table-column prop="actualQty" label="实际值" width="120" align="right">
          <template #default="{ row }">{{ formatOptionalNumber(row.actualQty) }}</template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <el-dialog v-model="lotBalanceDialogVisible" title="批次库存" width="1120px">
      <el-form :model="lotBalanceQuery" inline>
        <el-form-item label="批号">
          <el-input
            v-model="lotBalanceQuery.lotNo"
            clearable
            placeholder="请输入批号"
            style="width: 200px"
            @keyup.enter="handleLotBalanceQuery"
          />
        </el-form-item>
        <el-form-item label="临期天数">
          <el-input-number
            v-model="lotBalanceQuery.expiringWithinDays"
            :min="1"
            :max="3650"
            controls-position="right"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleLotBalanceQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetLotBalanceQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="lotBalanceLoading" :data="lotBalanceData" border stripe>
        <el-table-column prop="warehouseId" label="仓库" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" label="产品" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" min-width="140" />
        <el-table-column prop="productionDate" label="生产日期" width="120" />
        <el-table-column prop="expiryDate" label="失效日期" width="120" />
        <el-table-column prop="qtyOnHand" label="账面数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyOnHand) }}</template>
        </el-table-column>
        <el-table-column prop="qtyReserved" label="预留数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyReserved) }}</template>
        </el-table-column>
        <el-table-column prop="qtyAvailable" label="可用数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewLotBalance(row)">详情</el-button>
            <el-button link type="primary" @click="handleOpenLotTrace(row)">追踪</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="lotBalanceQuery.pageNo"
        v-model:page-size="lotBalanceQuery.pageSize"
        :total="lotBalanceTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleLotBalanceQuery"
        @current-change="loadLotBalances"
      />
    </el-dialog>

    <el-dialog v-model="transactionDialogVisible" title="库存流水" width="1120px">
      <el-form :model="transactionQuery" inline>
        <el-form-item label="业务单号">
          <el-input
            v-model="transactionQuery.bizNo"
            clearable
            placeholder="请输入业务单号"
            style="width: 200px"
            @keyup.enter="handleTransactionQuery"
          />
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="transactionQuery.direction" clearable placeholder="全部" style="width: 120px">
            <el-option label="入库" value="IN" />
            <el-option label="出库" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleTransactionQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetTransactionQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="transactionLoading" :data="transactionData" border stripe>
        <el-table-column prop="warehouseId" label="仓库" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" label="产品" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="140" />
        <el-table-column prop="bizNo" label="业务单号" min-width="150" />
        <el-table-column prop="direction" label="方向" width="90">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="occurredTime" label="发生时间" width="180" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewTransaction(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="transactionQuery.pageNo"
        v-model:page-size="transactionQuery.pageSize"
        :total="transactionTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleTransactionQuery"
        @current-change="loadTransactions"
      />
    </el-dialog>

    <el-dialog v-model="lotAlertDialogVisible" title="效期预警" width="1120px">
      <el-form :model="lotAlertQuery" inline>
        <el-form-item label="批号">
          <el-input
            v-model="lotAlertQuery.lotNo"
            clearable
            placeholder="请输入批号"
            style="width: 180px"
            @keyup.enter="handleLotAlertQuery"
          />
        </el-form-item>
        <el-form-item label="预警天数">
          <el-input-number
            v-model="lotAlertQuery.warningDays"
            :min="1"
            :max="3650"
            controls-position="right"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="lotAlertQuery.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="已过期" value="EXPIRED" />
            <el-option label="即将过期" value="EXPIRING" />
            <el-option label="正常" value="NORMAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleLotAlertQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetLotAlertQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="lotAlertLoading" :data="lotAlertData" border stripe>
        <el-table-column prop="warehouseId" label="仓库" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" label="产品" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" min-width="140" />
        <el-table-column prop="expiryDate" label="失效日期" width="120" />
        <el-table-column prop="daysToExpiry" label="剩余天数" width="110" align="right" />
        <el-table-column prop="expiryStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="expiryStatusType(row.expiryStatus)">{{ expiryStatusLabel(row.expiryStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qtyAvailable" label="可用数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qtyAvailable) }}</template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" />
      </el-table>
      <el-pagination
        v-model:current-page="lotAlertQuery.pageNo"
        v-model:page-size="lotAlertQuery.pageSize"
        :total="lotAlertTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleLotAlertQuery"
        @current-change="loadLotAlerts"
      />
    </el-dialog>

    <el-dialog v-model="lotTraceDialogVisible" title="批次追踪" width="1120px">
      <el-table v-loading="lotTraceLoading" :data="lotTraceData" border stripe>
        <el-table-column prop="warehouseId" label="仓库" min-width="150">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="productId" label="产品" min-width="200">
          <template #default="{ row }">{{ productName(row.productId) }}</template>
        </el-table-column>
        <el-table-column prop="lotNo" label="批号" min-width="140" />
        <el-table-column prop="bizType" label="业务类型" width="140" />
        <el-table-column prop="bizNo" label="业务单号" min-width="150" />
        <el-table-column prop="direction" label="方向" width="90">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.qty) }}</template>
        </el-table-column>
        <el-table-column prop="occurredTime" label="发生时间" width="180" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-model:current-page="lotTraceQuery.pageNo"
        v-model:page-size="lotTraceQuery.pageSize"
        :total="lotTraceTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadLotTrace"
        @current-change="loadLotTrace"
      />
    </el-dialog>

    <el-dialog v-model="stockDetailVisible" title="库存余额详情" width="760px">
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-descriptions v-else-if="selectedStock" :column="2" border>
        <el-descriptions-item label="仓库">{{ warehouseName(selectedStock.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ productName(selectedStock.productId) }}</el-descriptions-item>
        <el-descriptions-item label="账面库存">{{ formatNumber(selectedStock.quantity) }}</el-descriptions-item>
        <el-descriptions-item label="已预留">{{ formatNumber(selectedStock.reservedQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="可用库存">{{ formatNumber(selectedStock.availableQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="库存金额">{{ formatMoney(selectedStock.amountOnHand) }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ selectedStock.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ selectedStock.lastUpdated || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="lotBalanceDetailVisible" title="批次库存详情" width="760px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-descriptions v-else-if="selectedLotBalance" :column="2" border>
        <el-descriptions-item label="仓库">{{ warehouseName(selectedLotBalance.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ productName(selectedLotBalance.productId) }}</el-descriptions-item>
        <el-descriptions-item label="批号">{{ selectedLotBalance.lotNo }}</el-descriptions-item>
        <el-descriptions-item label="生产日期">{{ selectedLotBalance.productionDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="失效日期">{{ selectedLotBalance.expiryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="首次入库">{{ selectedLotBalance.firstInboundTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账面数量">{{ formatNumber(selectedLotBalance.qtyOnHand) }}</el-descriptions-item>
        <el-descriptions-item label="预留数量">{{ formatNumber(selectedLotBalance.qtyReserved) }}</el-descriptions-item>
        <el-descriptions-item label="可用数量">{{ formatNumber(selectedLotBalance.qtyAvailable) }}</el-descriptions-item>
        <el-descriptions-item label="库存金额">{{ formatMoney(selectedLotBalance.amountOnHand) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ selectedLotBalance.updatedTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="transactionDetailVisible" title="库存流水详情" width="760px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <el-descriptions v-else-if="selectedTransaction" :column="2" border>
        <el-descriptions-item label="仓库">{{ warehouseName(selectedTransaction.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ productName(selectedTransaction.productId) }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ selectedTransaction.bizType }}</el-descriptions-item>
        <el-descriptions-item label="业务单号">{{ selectedTransaction.bizNo }}</el-descriptions-item>
        <el-descriptions-item label="业务明细">{{ selectedTransaction.bizLineId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="方向">{{ directionLabel(selectedTransaction.direction) }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ formatNumber(selectedTransaction.qty) }}</el-descriptions-item>
        <el-descriptions-item label="单价">{{ formatMoney(selectedTransaction.unitCost) }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ formatMoney(selectedTransaction.amount) }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ selectedTransaction.occurredTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ selectedTransaction.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="reservationSourceVisible" title="来源预留详情" width="980px">
      <el-skeleton v-if="reservationSourceLoading" :rows="5" animated />
      <template v-else-if="reservationSourceDetail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="来源类型">{{ sourceTypeLabel(reservationSourceDetail.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="来源ID">{{ reservationSourceDetail.sourceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ reservationSourceDetail.sourceNo || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="reservationSourceDetail.reservations" border stripe class="detail-table">
          <el-table-column prop="reservation.warehouseId" label="仓库" min-width="140">
            <template #default="{ row }">{{ warehouseName(row.reservation.warehouseId) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.productId" label="产品" min-width="180">
            <template #default="{ row }">{{ productName(row.reservation.productId) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.reservedQty" label="预留数量" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.reservation.reservedQty) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.releasedQty" label="已释放" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.reservation.releasedQty) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.remainingQty" label="剩余预留" width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.reservation.remainingQty) }}</template>
          </el-table-column>
          <el-table-column prop="reservation.status" label="状态" width="110">
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Download, Refresh, Search, Warning } from '@element-plus/icons-vue'
import {
  checkInventoryReservations,
  exportInventoryStocks,
  getInventoryLotBalance,
  getInventoryLotBalances,
  getInventoryLotExpiryAlerts,
  getInventoryLotTrace,
  getInventoryReservation,
  getInventoryReservationSource,
  getInventoryReservationSummary,
  getInventoryReservations,
  getInventoryStock,
  getInventoryStocks,
  getInventoryTransaction,
  getInventoryTransactions,
  manualReleaseInventoryReservation,
  type InventoryLotBalance,
  type InventoryLotBalanceQuery,
  type InventoryLotExpiryAlert,
  type InventoryLotExpiryAlertQuery,
  type InventoryLotTrace,
  type InventoryLotTraceQuery,
  type InventoryReservation,
  type InventoryReservationCheckIssue,
  type InventoryReservationDetail,
  type InventoryReservationQuery,
  type InventoryReservationSource,
  type InventoryReservationSummary,
  type InventoryStock,
  type InventoryStockQuery,
  type InventoryTransaction,
  type InventoryTransactionQuery
} from '@/api/inventory'
import { getProducts, getWarehouses, type Product, type Warehouse } from '@/api/masterdata'
import { downloadBlob } from '@/utils/download'

const route = useRoute()

const queryParams = reactive<InventoryStockQuery>({
  pageNo: 1,
  pageSize: 20,
  warehouseId: route.query.warehouseId ? String(route.query.warehouseId) : undefined,
  productId: route.query.productId ? String(route.query.productId) : undefined
})

const loading = ref(false)
const tableData = ref<InventoryStock[]>([])
const total = ref(0)
const warehouses = ref<Warehouse[]>([])
const products = ref<Product[]>([])
const reservationDialogVisible = ref(false)
const reservationDetailVisible = ref(false)
const releaseDialogVisible = ref(false)
const checkDialogVisible = ref(false)
const lotBalanceDialogVisible = ref(false)
const transactionDialogVisible = ref(false)
const lotAlertDialogVisible = ref(false)
const lotTraceDialogVisible = ref(false)
const stockDetailVisible = ref(false)
const lotBalanceDetailVisible = ref(false)
const transactionDetailVisible = ref(false)
const reservationSourceVisible = ref(false)
const reservationLoading = ref(false)
const reservationSummaryLoading = ref(false)
const reservationSourceLoading = ref(false)
const checkLoading = ref(false)
const lotBalanceLoading = ref(false)
const transactionLoading = ref(false)
const lotAlertLoading = ref(false)
const lotTraceLoading = ref(false)
const detailLoading = ref(false)
const releasing = ref(false)
const reservationData = ref<InventoryReservation[]>([])
const reservationSummaryData = ref<InventoryReservationSummary[]>([])
const reservationTotal = ref(0)
const lotBalanceData = ref<InventoryLotBalance[]>([])
const lotBalanceTotal = ref(0)
const transactionData = ref<InventoryTransaction[]>([])
const transactionTotal = ref(0)
const lotAlertData = ref<InventoryLotExpiryAlert[]>([])
const lotAlertTotal = ref(0)
const lotTraceData = ref<InventoryLotTrace[]>([])
const lotTraceTotal = ref(0)
const reservationDetail = ref<InventoryReservationDetail>()
const reservationSourceDetail = ref<InventoryReservationSource>()
const selectedReservation = ref<InventoryReservation>()
const selectedStock = ref<InventoryStock>()
const selectedLotBalance = ref<InventoryLotBalance>()
const selectedTransaction = ref<InventoryTransaction>()
const checkIssues = ref<InventoryReservationCheckIssue[]>([])
const releaseFormRef = ref<FormInstance>()

const reservationQuery = reactive<InventoryReservationQuery>({
  pageNo: 1,
  pageSize: 10,
  warehouseId: undefined,
  productId: undefined,
  status: 'ACTIVE',
  sourceNo: undefined
})

const lotBalanceQuery = reactive<InventoryLotBalanceQuery>({
  pageNo: 1,
  pageSize: 10,
  warehouseId: undefined,
  productId: undefined,
  lotNo: undefined,
  expiringWithinDays: undefined
})

const transactionQuery = reactive<InventoryTransactionQuery>({
  pageNo: 1,
  pageSize: 10,
  warehouseId: undefined,
  productId: undefined,
  bizNo: undefined,
  direction: undefined
})

const lotAlertQuery = reactive<InventoryLotExpiryAlertQuery>({
  pageNo: 1,
  pageSize: 10,
  warehouseId: undefined,
  productId: undefined,
  lotNo: undefined,
  warningDays: 30,
  status: undefined
})

const lotTraceQuery = reactive<InventoryLotTraceQuery>({
  pageNo: 1,
  pageSize: 10,
  warehouseId: undefined,
  productId: undefined,
  lotNo: undefined,
  direction: undefined
})

const releaseForm = reactive({
  qty: 0,
  reason: ''
})

const validateReleaseQty = (_rule: unknown, value: number, callback: (error?: Error) => void) => {
  const qty = Number(value)
  if (!Number.isFinite(qty) || qty <= 0) {
    callback(new Error('释放数量必须大于 0'))
    return
  }
  if (selectedReservation.value && qty > Number(selectedReservation.value.remainingQty)) {
    callback(new Error('释放数量不能大于剩余预留'))
    return
  }
  callback()
}

const releaseRules: FormRules = {
  qty: [{ validator: validateReleaseQty, trigger: 'blur' }],
  reason: [
    { required: true, message: '请输入释放原因', trigger: 'blur' },
    { max: 255, message: '释放原因不能超过 255 个字符', trigger: 'blur' }
  ]
}

const warehouseMap = computed(() => new Map(warehouses.value.map((item) => [String(item.id), item.name])))
const productMap = computed(() => new Map(products.value.map((item) => [
  String(item.id),
  `${item.code || item.productCode || item.id} - ${item.name || item.productName || '-'}`
])))

const loadData = async () => {
  loading.value = true
  try {
    const page = await getInventoryStocks(queryParams)
    tableData.value = page.records
    total.value = page.total
  } catch (error) {
    console.error('加载库存余额失败:', error)
    ElMessage.error('加载库存余额失败')
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  const optionPageQuery = { pageNo: 1, pageSize: 200, status: 'ACTIVE' }
  const [warehousePage, productPage] = await Promise.all([
    getWarehouses(optionPageQuery),
    getProducts(optionPageQuery)
  ])
  warehouses.value = warehousePage.records
  products.value = productPage.records
}

const loadReservations = async () => {
  reservationLoading.value = true
  try {
    const page = await getInventoryReservations(reservationQuery)
    reservationData.value = page.records
    reservationTotal.value = page.total
  } catch (error) {
    ElMessage.error('加载预留明细失败')
  } finally {
    reservationLoading.value = false
  }
}

const loadReservationSummary = async () => {
  reservationSummaryLoading.value = true
  try {
    reservationSummaryData.value = await getInventoryReservationSummary({
      warehouseId: reservationQuery.warehouseId,
      productId: reservationQuery.productId,
      status: reservationQuery.status
    })
  } catch (error) {
    ElMessage.error('加载预留汇总失败')
  } finally {
    reservationSummaryLoading.value = false
  }
}

const loadLotBalances = async () => {
  lotBalanceLoading.value = true
  try {
    const page = await getInventoryLotBalances(lotBalanceQuery)
    lotBalanceData.value = page.records
    lotBalanceTotal.value = page.total
  } catch (error) {
    ElMessage.error('加载批次库存失败')
  } finally {
    lotBalanceLoading.value = false
  }
}

const loadTransactions = async () => {
  transactionLoading.value = true
  try {
    const page = await getInventoryTransactions(transactionQuery)
    transactionData.value = page.records
    transactionTotal.value = page.total
  } catch (error) {
    ElMessage.error('加载库存流水失败')
  } finally {
    transactionLoading.value = false
  }
}

const loadLotAlerts = async () => {
  lotAlertLoading.value = true
  try {
    const page = await getInventoryLotExpiryAlerts(lotAlertQuery)
    lotAlertData.value = page.records
    lotAlertTotal.value = page.total
  } catch (error) {
    ElMessage.error('加载效期预警失败')
  } finally {
    lotAlertLoading.value = false
  }
}

const loadLotTrace = async () => {
  lotTraceLoading.value = true
  try {
    const page = await getInventoryLotTrace(lotTraceQuery)
    lotTraceData.value = page.records
    lotTraceTotal.value = page.total
  } catch (error) {
    ElMessage.error('加载批次追踪失败')
  } finally {
    lotTraceLoading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

const handleReset = () => {
  queryParams.warehouseId = undefined
  queryParams.productId = undefined
  queryParams.pageNo = 1
  loadData()
}

const handleExport = async () => {
  try {
    const blob = await exportInventoryStocks(queryParams)
    downloadBlob(blob, `库存余额_${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const handleViewStock = async (row: InventoryStock) => {
  stockDetailVisible.value = true
  selectedStock.value = undefined
  detailLoading.value = true
  try {
    selectedStock.value = await getInventoryStock(row.id)
  } catch (error) {
    ElMessage.error('加载库存余额详情失败')
    stockDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleViewLotBalance = async (row: InventoryLotBalance) => {
  lotBalanceDetailVisible.value = true
  selectedLotBalance.value = undefined
  detailLoading.value = true
  try {
    selectedLotBalance.value = await getInventoryLotBalance(row.id)
  } catch (error) {
    ElMessage.error('加载批次库存详情失败')
    lotBalanceDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleViewTransaction = async (row: InventoryTransaction) => {
  transactionDetailVisible.value = true
  selectedTransaction.value = undefined
  detailLoading.value = true
  try {
    selectedTransaction.value = await getInventoryTransaction(row.id)
  } catch (error) {
    ElMessage.error('加载库存流水详情失败')
    transactionDetailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const applyStockScope = (
  target: { warehouseId?: string | number, productId?: string | number, pageNo?: number },
  row?: InventoryStock
) => {
  target.warehouseId = row?.warehouseId || queryParams.warehouseId
  target.productId = row?.productId || queryParams.productId
  target.pageNo = 1
}

const handleOpenLotBalances = (row?: InventoryStock) => {
  applyStockScope(lotBalanceQuery, row)
  lotBalanceQuery.lotNo = undefined
  lotBalanceDialogVisible.value = true
  loadLotBalances()
}

const handleLotBalanceQuery = () => {
  lotBalanceQuery.pageNo = 1
  loadLotBalances()
}

const resetLotBalanceQuery = () => {
  lotBalanceQuery.lotNo = undefined
  lotBalanceQuery.expiringWithinDays = undefined
  handleLotBalanceQuery()
}

const handleOpenTransactions = (row?: InventoryStock) => {
  applyStockScope(transactionQuery, row)
  transactionQuery.bizNo = undefined
  transactionQuery.direction = undefined
  transactionDialogVisible.value = true
  loadTransactions()
}

const handleTransactionQuery = () => {
  transactionQuery.pageNo = 1
  loadTransactions()
}

const resetTransactionQuery = () => {
  transactionQuery.bizNo = undefined
  transactionQuery.direction = undefined
  handleTransactionQuery()
}

const handleOpenLotAlerts = (row?: InventoryStock) => {
  applyStockScope(lotAlertQuery, row)
  lotAlertQuery.lotNo = undefined
  lotAlertDialogVisible.value = true
  loadLotAlerts()
}

const handleLotAlertQuery = () => {
  lotAlertQuery.pageNo = 1
  loadLotAlerts()
}

const resetLotAlertQuery = () => {
  lotAlertQuery.lotNo = undefined
  lotAlertQuery.warningDays = 30
  lotAlertQuery.status = undefined
  handleLotAlertQuery()
}

const handleOpenLotTrace = (row: InventoryLotBalance) => {
  Object.assign(lotTraceQuery, {
    pageNo: 1,
    pageSize: 10,
    warehouseId: row.warehouseId,
    productId: row.productId,
    lotNo: row.lotNo,
    direction: undefined
  })
  lotTraceDialogVisible.value = true
  loadLotTrace()
}

const handleOpenReservations = (row: InventoryStock) => {
  Object.assign(reservationQuery, {
    pageNo: 1,
    pageSize: 10,
    warehouseId: row.warehouseId,
    productId: row.productId,
    status: 'ACTIVE',
    sourceNo: undefined
  })
  reservationDialogVisible.value = true
  loadReservationSummary()
  loadReservations()
}

const handleReservationQuery = () => {
  reservationQuery.pageNo = 1
  loadReservationSummary()
  loadReservations()
}

const resetReservationQuery = () => {
  reservationQuery.pageNo = 1
  reservationQuery.status = undefined
  reservationQuery.sourceNo = undefined
  loadReservationSummary()
  loadReservations()
}

const handleViewReservation = async (row: InventoryReservation) => {
  try {
    reservationDetail.value = await getInventoryReservation(row.id)
    reservationDetailVisible.value = true
  } catch (error) {
    ElMessage.error('加载预留详情失败')
  }
}

const handleViewReservationSource = async (row: InventoryReservation) => {
  reservationSourceVisible.value = true
  reservationSourceDetail.value = undefined
  reservationSourceLoading.value = true
  try {
    reservationSourceDetail.value = await getInventoryReservationSource({
      sourceType: row.sourceType,
      sourceId: row.sourceId,
      sourceNo: row.sourceNo
    })
  } catch (error) {
    ElMessage.error('加载来源预留详情失败')
    reservationSourceVisible.value = false
  } finally {
    reservationSourceLoading.value = false
  }
}

const openReleaseDialog = (row: InventoryReservation) => {
  selectedReservation.value = row
  releaseForm.qty = Number(row.remainingQty)
  releaseForm.reason = ''
  releaseFormRef.value?.clearValidate()
  releaseDialogVisible.value = true
}

const submitManualRelease = async () => {
  if (!releaseFormRef.value || !selectedReservation.value) return
  await releaseFormRef.value.validate()

  releasing.value = true
  try {
    const detail = await manualReleaseInventoryReservation(selectedReservation.value.id, {
      qty: releaseForm.qty,
      reason: releaseForm.reason.trim()
    })
    reservationDetail.value = detail
    releaseDialogVisible.value = false
    ElMessage.success('释放成功')
    await loadReservations()
    loadData()
  } catch (error) {
    ElMessage.error('释放失败')
  } finally {
    releasing.value = false
  }
}

const handleReservationCheck = async () => {
  checkDialogVisible.value = true
  checkLoading.value = true
  try {
    checkIssues.value = await checkInventoryReservations({
      warehouseId: queryParams.warehouseId,
      productId: queryParams.productId
    })
  } catch (error) {
    ElMessage.error('预留检查失败')
  } finally {
    checkLoading.value = false
  }
}

const warehouseName = (id: string) => warehouseMap.value.get(String(id)) || `仓库 ${id}`
const productName = (id: string) => productMap.value.get(String(id)) || `产品 ${id}`
const formatNumber = (value?: number) => Number(value ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const formatOptionalNumber = (value?: number) => value == null ? '-' : formatNumber(value)
const formatMoney = (value?: number) => Number(value ?? 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})
const sourceTypeLabel = (value?: string) => {
  const labelMap: Record<string, string> = {
    SALES_ORDER: '销售订单'
  }
  return value ? labelMap[value] || value : '-'
}
const reservationStatusLabel = (value?: string) => {
  const labelMap: Record<string, string> = {
    ACTIVE: '有效',
    RELEASED: '已释放',
    CANCELLED: '已取消'
  }
  return value ? labelMap[value] || value : '-'
}
const reservationStatusType = (value?: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    ACTIVE: 'success',
    RELEASED: 'info',
    CANCELLED: 'warning'
  }
  return value ? typeMap[value] || 'info' : 'info'
}
const reservationEventLabel = (value?: string) => {
  const labelMap: Record<string, string> = {
    RESERVE: '预留',
    RELEASE: '释放',
    MANUAL_RELEASE: '手工释放'
  }
  return value ? labelMap[value] || value : '-'
}
const directionLabel = (value?: string) => {
  const labelMap: Record<string, string> = {
    IN: '入库',
    OUT: '出库'
  }
  return value ? labelMap[value] || value : '-'
}
const expiryStatusLabel = (value?: string) => {
  const labelMap: Record<string, string> = {
    EXPIRED: '已过期',
    EXPIRING: '即将过期',
    NORMAL: '正常'
  }
  return value ? labelMap[value] || value : '-'
}
const expiryStatusType = (value?: string) => {
  const typeMap: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    EXPIRED: 'danger',
    EXPIRING: 'warning',
    NORMAL: 'success'
  }
  return value ? typeMap[value] || 'info' : 'info'
}

onMounted(async () => {
  try {
    await loadOptions()
  } catch (error) {
    console.error('加载筛选项失败:', error)
  }
  loadData()
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
