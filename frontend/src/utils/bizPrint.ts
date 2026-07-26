import { printHtml, buildDocPrintHtml, money, qty, escapeHtml } from '@/utils/printDoc'

type LineLike = {
  productCode?: string
  productName?: string
  productId?: string | number
  quantity?: number
  qty?: number
  price?: number
  amount?: number
  taxRate?: number
  taxAmount?: number
  remark?: string
}

function lineRows(lines: LineLike[]) {
  return lines.map((line, index) => [
    String(index + 1),
    escapeHtml(line.productCode || line.productId || ''),
    escapeHtml(line.productName || ''),
    qty(line.quantity ?? line.qty),
    money(line.price),
    money(line.amount ?? Number(line.quantity ?? line.qty ?? 0) * Number(line.price ?? 0)),
    escapeHtml(line.remark || '')
  ])
}

export function printSalesOrder(order: any) {
  const lines = order.items || order.lines || []
  const html = buildDocPrintHtml({
    title: '销售订单',
    docNo: order.orderNo,
    fields: [
      ['客户', order.customerName || order.customerId || '-'],
      ['订单日期', order.orderDate || '-'],
      ['交付日期', order.deliveryDate || '-'],
      ['状态', order.status || '-'],
      ['备注', order.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '单价', '金额', '备注'],
    rows: lineRows(lines),
    totals: [
      ['数量合计', qty(order.totalQuantity)],
      ['金额合计', money(order.totalAmount)],
      ['税额合计', money(order.totalTaxAmount)]
    ]
  })
  printHtml(`销售订单 ${order.orderNo}`, html)
}

export function printSalesDelivery(doc: any) {
  const lines = doc.items || doc.lines || []
  const html = buildDocPrintHtml({
    title: '销售发货单',
    docNo: doc.deliveryNo,
    fields: [
      ['客户', doc.customerName || doc.customerId || '-'],
      ['发货日期', doc.deliveryDate || '-'],
      ['仓库', doc.warehouseName || doc.warehouseId || '-'],
      ['来源订单', doc.orderNo || doc.orderId || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '单价', '金额', '备注'],
    rows: lineRows(lines),
    totals: [['金额合计', money(doc.totalAmount)]]
  })
  printHtml(`销售发货单 ${doc.deliveryNo}`, html)
}

export function printPurchaseOrder(order: any) {
  const lines = order.items || order.lines || []
  const html = buildDocPrintHtml({
    title: '采购订单',
    docNo: order.orderNo,
    fields: [
      ['供应商', order.supplierName || order.supplierId || '-'],
      ['订单日期', order.orderDate || '-'],
      ['交货日期', order.deliveryDate || order.expectedDate || '-'],
      ['状态', order.status || '-'],
      ['备注', order.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '单价', '金额', '备注'],
    rows: lineRows(lines),
    totals: [
      ['数量合计', qty(order.totalQuantity)],
      ['金额合计', money(order.totalAmount)],
      ['税额合计', money(order.totalTaxAmount)]
    ]
  })
  printHtml(`采购订单 ${order.orderNo}`, html)
}

export function printPurchaseReceipt(doc: any) {
  const lines = doc.items || doc.lines || []
  const html = buildDocPrintHtml({
    title: '采购收货单',
    docNo: doc.receiptNo,
    fields: [
      ['供应商', doc.supplierName || doc.supplierId || '-'],
      ['收货日期', doc.receiptDate || '-'],
      ['仓库', doc.warehouseName || doc.warehouseId || '-'],
      ['来源订单', doc.orderNo || doc.orderId || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '单价', '金额', '备注'],
    rows: lineRows(lines),
    totals: [['数量合计', qty(doc.totalQuantity)]]
  })
  printHtml(`采购收货单 ${doc.receiptNo}`, html)
}

export function printSalesReturn(doc: any) {
  const lines = doc.items || doc.lines || []
  const html = buildDocPrintHtml({
    title: '销售退货单',
    docNo: doc.returnNo,
    fields: [
      ['客户', doc.customerName || doc.customerId || '-'],
      ['退货日期', doc.returnDate || '-'],
      ['退货仓库', doc.warehouseName || doc.warehouseId || '-'],
      ['来源发货', doc.deliveryNo || doc.deliveryId || '-'],
      ['来源订单', doc.orderNo || doc.orderId || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '单价', '金额', '备注'],
    rows: lineRows(lines),
    totals: [
      ['数量合计', qty(doc.totalQuantity)],
      ['金额合计', money(doc.totalAmount)]
    ]
  })
  printHtml(`销售退货单 ${doc.returnNo}`, html)
}

export function printPurchaseReturn(doc: any) {
  const lines = doc.items || doc.lines || []
  const html = buildDocPrintHtml({
    title: '采购退货单',
    docNo: doc.returnNo,
    fields: [
      ['供应商', doc.supplierName || doc.supplierId || '-'],
      ['退货日期', doc.returnDate || '-'],
      ['退货仓库', doc.warehouseName || doc.warehouseId || '-'],
      ['来源收货', doc.receiptNo || doc.receiptId || '-'],
      ['来源订单', doc.orderNo || doc.orderId || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '单价', '金额', '备注'],
    rows: lineRows(lines),
    totals: [
      ['数量合计', qty(doc.totalQuantity)],
      ['金额合计', money(doc.totalAmount)]
    ]
  })
  printHtml(`采购退货单 ${doc.returnNo}`, html)
}

export function printProductionOrder(order: any) {
  const materials = order.materials || order.lines || []
  const html = buildDocPrintHtml({
    title: '生产订单',
    docNo: order.orderNo,
    fields: [
      ['产品', order.productName || order.productCode || order.productId || '-'],
      ['BOM', order.bomCode || order.bomId || '-'],
      ['计划数量', qty(order.planQuantity ?? order.plannedQty)],
      ['完成数量', qty(order.completedQuantity ?? order.completedQty)],
      ['材料仓', order.materialWarehouseName || order.materialWarehouseId || order.warehouseName || order.warehouseId || '-'],
      ['成品仓', order.finishedWarehouseName || order.finishedWarehouseId || '-'],
      ['计划开始', order.planStartDate || order.plannedStartDate || '-'],
      ['计划结束', order.planEndDate || order.plannedFinishDate || '-'],
      ['状态', order.status || '-'],
      ['优先级', order.priority || '-'],
      ['备注', order.remark || '-']
    ],
    columns: ['行', '编码', '品名', '需求', '已领', '已退', '备注'],
    rows: materials.map((material: any, index: number) => [
      String(index + 1),
      escapeHtml(material.materialCode || material.productCode || material.materialProductId || material.materialId || ''),
      escapeHtml(material.materialName || material.productName || ''),
      qty(material.requiredQuantity ?? material.requiredQty),
      qty(material.issuedQuantity ?? material.issuedQty),
      qty(material.returnedQuantity),
      escapeHtml(material.remark || material.unit || '')
    ]),
    totals: [
      ['计划数量', qty(order.planQuantity ?? order.plannedQty)],
      ['完成数量', qty(order.completedQuantity ?? order.completedQty)]
    ]
  })
  printHtml(`生产订单 ${order.orderNo}`, html)
}

export function printProductionBom(bom: any) {
  const items = bom.items || bom.lines || []
  const html = buildDocPrintHtml({
    title: '生产BOM',
    docNo: bom.bomCode || bom.bomNo || bom.id || 'bom',
    fields: [
      ['BOM编码', bom.bomCode || bom.bomNo || bom.id || '-'],
      ['产品', bom.productName || bom.productCode || bom.productId || '-'],
      ['基准数量', qty(bom.baseQty ?? bom.quantity)],
      ['状态', bom.status || '-'],
      ['备注', bom.remark || '-']
    ],
    columns: ['行', '编码', '品名', '用量', '单位', '损耗率', '备注'],
    rows: items.map((item: any, index: number) => [
      String(index + 1),
      escapeHtml(item.materialCode || item.productCode || item.materialProductId || item.materialId || ''),
      escapeHtml(item.materialName || item.productName || ''),
      qty(item.quantity ?? item.qtyPer),
      escapeHtml(item.unit || item.materialUnit || ''),
      escapeHtml(item.scrapRate != null || item.lossRate != null
        ? `${item.scrapRate ?? item.lossRate}%`
        : '0%'),
      escapeHtml(item.remark || '')
    ]),
    totals: [
      ['基准数量', qty(bom.baseQty ?? bom.quantity)],
      ['物料行数', String(items.length)]
    ]
  })
  printHtml(`生产BOM ${bom.bomCode || bom.bomNo || bom.id || ''}`, html)
}

export function printProductionRouting(routing: any) {
  const operations = routing.operations || []
  const html = buildDocPrintHtml({
    title: '工艺路线',
    docNo: routing.routingCode || routing.id || 'routing',
    fields: [
      ['路线编码', routing.routingCode || routing.id || '-'],
      ['路线名称', routing.routingName || '-'],
      ['BOM', routing.bomNo || routing.bomId || '-'],
      ['状态', routing.status || '-'],
      ['工序数', String(operations.length)],
      ['备注', routing.remark || '-']
    ],
    columns: ['序', '工序编码', '工序名称', '工作中心', '标准工时(分)', '备注'],
    rows: operations.map((operation: any, index: number) => [
      String(operation.lineNo ?? index + 1),
      escapeHtml(operation.operationCode || ''),
      escapeHtml(operation.operationName || ''),
      escapeHtml(
        operation.workCenterName
          || operation.workCenterCode
          || operation.workCenterId
          || ''
      ),
      qty(operation.standardMinutes),
      escapeHtml(operation.remark || '')
    ]),
    totals: [
      ['工序数', String(operations.length)],
      ['总标准工时', qty(operations.reduce((sum: number, op: any) => sum + Number(op.standardMinutes || 0), 0))]
    ]
  })
  printHtml(`工艺路线 ${routing.routingCode || routing.id || ''}`, html)
}

export function printSalesQuote(quote: any) {
  const lines = quote.lines || quote.items || []
  const html = buildDocPrintHtml({
    title: '销售报价单',
    docNo: quote.quoteNo,
    fields: [
      ['客户', quote.customerName || quote.customerId || '-'],
      ['报价日期', quote.quoteDate || '-'],
      ['有效期至', quote.validUntil || '-'],
      ['状态', quote.status || '-'],
      ['备注', quote.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '单价', '金额', '备注'],
    rows: lineRows(lines.map((line: any) => ({
      ...line,
      quantity: line.quantity ?? line.qty,
      productCode: line.productCode || line.productId,
      productName: line.productName || ''
    }))),
    totals: [
      ['金额合计', money(quote.totalAmount)],
      ['税额合计', money(quote.totalTaxAmount)]
    ]
  })
  printHtml(`销售报价单 ${quote.quoteNo}`, html)
}

export function printPurchaseRequisition(doc: any) {
  const lines = doc.lines || doc.items || []
  const html = buildDocPrintHtml({
    title: '采购请购单',
    docNo: doc.requisitionNo,
    fields: [
      ['请购日期', doc.requisitionDate || '-'],
      ['需求日期', doc.neededDate || '-'],
      ['状态', doc.status || '-'],
      ['审批状态', doc.approvalStatus || '-'],
      ['供应商', doc.supplierName || doc.supplierId || '-'],
      ['转采购订单', doc.convertedOrderNo || doc.convertedOrderId || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '备注'],
    rows: lines.map((line: any, index: number) => [
      String(index + 1),
      escapeHtml(line.productCode || line.productId || ''),
      escapeHtml(line.productName || ''),
      qty(line.quantity ?? line.qty),
      escapeHtml(line.remark || '')
    ]),
    totals: [['明细行数', String(lines.length)]]
  })
  printHtml(`采购请购单 ${doc.requisitionNo}`, html)
}

export function printInventoryAdjustment(doc: any) {
  const lines = doc.items || doc.lines || []
  const html = buildDocPrintHtml({
    title: '库存调整单',
    docNo: doc.adjustmentNo,
    fields: [
      ['仓库', doc.warehouseName || doc.warehouseId || '-'],
      ['调整日期', doc.adjustmentDate || '-'],
      ['调整类型', doc.type || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '原因', '备注'],
    rows: lines.map((line: any, index: number) => [
      String(index + 1),
      escapeHtml(line.productCode || line.productId || ''),
      escapeHtml(line.productName || ''),
      qty(line.quantity ?? line.qty),
      escapeHtml(line.reason || ''),
      escapeHtml(line.remark || line.lotNo || '')
    ]),
    totals: [['明细行数', String(lines.length)]]
  })
  printHtml(`库存调整单 ${doc.adjustmentNo}`, html)
}

export function printInventoryTransfer(doc: any) {
  const lines = doc.items || doc.lines || []
  const html = buildDocPrintHtml({
    title: '库存调拨单',
    docNo: doc.transferNo,
    fields: [
      ['调出仓库', doc.fromWarehouseName || doc.fromWarehouseId || '-'],
      ['调入仓库', doc.toWarehouseName || doc.toWarehouseId || '-'],
      ['调拨日期', doc.transferDate || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '备注'],
    rows: lines.map((line: any, index: number) => [
      String(index + 1),
      escapeHtml(line.productCode || line.productId || ''),
      escapeHtml(line.productName || ''),
      qty(line.quantity ?? line.qty),
      escapeHtml(line.remark || line.lotNo || '')
    ]),
    totals: [['明细行数', String(lines.length)]]
  })
  printHtml(`库存调拨单 ${doc.transferNo}`, html)
}

export function printInventoryCheck(doc: any) {
  const lines = doc.items || doc.lines || []
  const html = buildDocPrintHtml({
    title: '库存盘点单',
    docNo: doc.checkNo,
    fields: [
      ['仓库', doc.warehouseName || doc.warehouseId || '-'],
      ['盘点日期', doc.checkDate || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '账面', '实盘', '差异', '备注'],
    rows: lines.map((line: any, index: number) => {
      const bookQty = Number(line.bookQuantity ?? line.bookQty ?? 0)
      const actualQty = Number(line.actualQuantity ?? line.actualQty ?? line.quantity ?? line.qty ?? 0)
      const diff = Number(line.difference ?? actualQty - bookQty)
      return [
        String(index + 1),
        escapeHtml(line.productCode || line.productId || ''),
        escapeHtml(line.productName || ''),
        qty(bookQty),
        qty(actualQty),
        qty(diff),
        escapeHtml(line.remark || line.lotNo || '')
      ]
    }),
    totals: [['明细行数', String(lines.length)]]
  })
  printHtml(`库存盘点单 ${doc.checkNo}`, html)
}

export function printQcInspection(doc: any) {
  const lines = doc.lines || doc.items || []
  const html = buildDocPrintHtml({
    title: '质检单',
    docNo: doc.inspectionNo,
    fields: [
      ['检验类型', doc.inspectionType || '-'],
      ['检验日期', doc.inspectionDate || '-'],
      ['状态', doc.status || '-'],
      ['来源收货', doc.receiptId || '-'],
      ['来源发货', doc.deliveryId || '-'],
      ['生产工单', doc.productionOrderId || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '商品ID', '检验数', '合格', '不合格', '原因', '备注'],
    rows: lines.map((line: any, index: number) => [
      String(line.lineNo ?? index + 1),
      escapeHtml(line.productCode || line.productId || ''),
      qty(line.inspectedQty ?? line.quantity ?? line.qty),
      qty(line.qualifiedQty),
      qty(line.unqualifiedQty),
      escapeHtml(line.defectReason || ''),
      escapeHtml(line.remark || '')
    ]),
    totals: [
      ['检验合计', qty(doc.totalQty)],
      ['合格合计', qty(doc.qualifiedQty)],
      ['不合格合计', qty(doc.unqualifiedQty)]
    ]
  })
  printHtml(`质检单 ${doc.inspectionNo}`, html)
}

export function printPurchaseInquiry(doc: any) {
  const lines = doc.lines || doc.items || []
  const html = buildDocPrintHtml({
    title: '采购询价单',
    docNo: doc.inquiryNo,
    fields: [
      ['标题', doc.title || '-'],
      ['询价日期', doc.inquiryDate || '-'],
      ['状态', doc.status || '-'],
      ['中标供应商', doc.selectedSupplierName || doc.selectedSupplierId || '-'],
      ['转采购订单', doc.convertedOrderNo || doc.convertedOrderId || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '编码', '品名', '数量', '备注'],
    rows: lines.map((line: any, index: number) => [
      String(index + 1),
      escapeHtml(line.productCode || line.productId || ''),
      escapeHtml(line.productName || ''),
      qty(line.quantity ?? line.qty),
      escapeHtml(line.remark || '')
    ]),
    totals: [['明细行数', String(lines.length)]]
  })
  printHtml(`采购询价单 ${doc.inquiryNo}`, html)
}

export function printExpense(doc: any) {
  const html = buildDocPrintHtml({
    title: '费用单',
    docNo: doc.expenseNo,
    fields: [
      ['费用日期', doc.expenseDate || '-'],
      ['费用科目', doc.subjectName || doc.subjectId || '-'],
      ['支付科目', doc.paymentSubjectName || doc.paymentSubjectId || '-'],
      ['状态', doc.status || '-'],
      ['凭证', doc.voucherNo || doc.voucherId || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '项目', '金额', '备注'],
    rows: [[
      '1',
      escapeHtml(doc.subjectName || doc.subjectId || '费用'),
      money(doc.amount),
      escapeHtml(doc.remark || '')
    ]],
    totals: [['金额合计', money(doc.amount)]]
  })
  printHtml(`费用单 ${doc.expenseNo}`, html)
}

export function printFinanceInvoice(doc: any) {
  const html = buildDocPrintHtml({
    title: '发票登记',
    docNo: doc.invoiceNo,
    fields: [
      ['发票类型', doc.invoiceType || '-'],
      ['往来单位', doc.partnerName || '-'],
      ['发票日期', doc.invoiceDate || '-'],
      ['状态', doc.status || '-'],
      ['关联业务', `${doc.relatedBizType || '-'} ${doc.relatedBizId || ''}`.trim()],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '项目', '金额', '税额', '备注'],
    rows: [[
      '1',
      escapeHtml(doc.partnerName || '发票'),
      money(doc.amount),
      money(doc.taxAmount),
      escapeHtml(doc.remark || '')
    ]],
    totals: [
      ['金额合计', money(doc.amount)],
      ['税额合计', money(doc.taxAmount)]
    ]
  })
  printHtml(`发票登记 ${doc.invoiceNo}`, html)
}

export function printReceipt(doc: any) {
  const allocations = doc.allocations || doc.lines || []
  const html = buildDocPrintHtml({
    title: '收款单',
    docNo: doc.receiptNo,
    fields: [
      ['客户', doc.customerName || doc.customerId || '-'],
      ['收款日期', doc.receiptDate || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '应收单', '核销金额', '备注'],
    rows: allocations.length
      ? allocations.map((line: any, index: number) => [
          String(index + 1),
          escapeHtml(line.receivableNo || line.receivableId || line.bizNo || line.bizId || ''),
          money(line.allocatedAmount ?? line.amount ?? line.qty),
          escapeHtml(line.remark || '')
        ])
      : [[
          '1',
          escapeHtml(doc.customerName || doc.customerId || '收款'),
          money(doc.receiptAmount ?? doc.amount),
          escapeHtml(doc.remark || '')
        ]],
    totals: [
      ['收款金额', money(doc.receiptAmount ?? doc.amount)],
      ['已核销', money(doc.allocatedAmount)]
    ]
  })
  printHtml(`收款单 ${doc.receiptNo}`, html)
}

export function printPayment(doc: any) {
  const allocations = doc.allocations || doc.lines || []
  const html = buildDocPrintHtml({
    title: '付款单',
    docNo: doc.paymentNo,
    fields: [
      ['供应商', doc.supplierName || doc.supplierId || '-'],
      ['付款日期', doc.paymentDate || '-'],
      ['状态', doc.status || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '应付单', '核销金额', '备注'],
    rows: allocations.length
      ? allocations.map((line: any, index: number) => [
          String(index + 1),
          escapeHtml(line.payableNo || line.payableId || line.bizNo || line.bizId || ''),
          money(line.allocatedAmount ?? line.amount ?? line.qty),
          escapeHtml(line.remark || '')
        ])
      : [[
          '1',
          escapeHtml(doc.supplierName || doc.supplierId || '付款'),
          money(doc.paymentAmount ?? doc.amount),
          escapeHtml(doc.remark || '')
        ]],
    totals: [
      ['付款金额', money(doc.paymentAmount ?? doc.amount)],
      ['已核销', money(doc.allocatedAmount)]
    ]
  })
  printHtml(`付款单 ${doc.paymentNo}`, html)
}

export function printVoucher(doc: any) {
  const entries = doc.entries || doc.lines || []
  const html = buildDocPrintHtml({
    title: '财务凭证',
    docNo: doc.voucherNo,
    fields: [
      ['来源', doc.sourceTypeLabel || doc.sourceType || '-'],
      ['凭证日期', doc.bizDate || doc.voucherDate || '-'],
      ['状态', doc.statusLabel || doc.status || '-'],
      ['来源单号', doc.sourceNo || doc.sourceId || '-'],
      ['备注', doc.remark || '-']
    ],
    columns: ['行', '科目编码', '科目名称', '借方', '贷方', '摘要'],
    rows: entries.map((line: any, index: number) => [
      String(line.lineNo ?? index + 1),
      escapeHtml(line.subjectCode || line.subjectId || ''),
      escapeHtml(line.subjectName || ''),
      money(line.debitAmount),
      money(line.creditAmount),
      escapeHtml(line.summary || line.remark || '')
    ]),
    totals: [
      ['凭证金额', money(doc.amount)],
      ['分录行数', String(entries.length)]
    ]
  })
  printHtml(`财务凭证 ${doc.voucherNo}`, html)
}

export function printPartnerStatement(doc: any) {
  const lines = doc.lines || []
  const html = buildDocPrintHtml({
    title: '往来对账单',
    docNo: doc.partnerName || doc.partnerId || 'statement',
    fields: [
      ['往来单位', doc.partnerName || doc.partnerId || '-'],
      ['往来类型', doc.partnerTypeLabel || doc.partnerType || '-'],
      ['期间', `${doc.dateFrom || '-'} ~ ${doc.dateTo || '-'}`],
      ['期初', money(doc.openingBalance)],
      ['增加', money(doc.totalIncrease)],
      ['减少', money(doc.totalDecrease)],
      ['期末', money(doc.closingBalance)]
    ],
    columns: ['日期', '单据类型', '单号', '方向', '金额', '余额', '备注'],
    rows: lines.map((line: any) => [
      escapeHtml(line.bizDate || ''),
      escapeHtml(line.docTypeLabel || line.docType || ''),
      escapeHtml(line.docNo || ''),
      escapeHtml(line.directionLabel || line.direction || ''),
      money(line.amount),
      money(line.balance),
      escapeHtml(line.remark || '')
    ]),
    totals: [
      ['期初', money(doc.openingBalance)],
      ['期末', money(doc.closingBalance)]
    ]
  })
  printHtml(`往来对账单 ${doc.partnerName || doc.partnerId || ''}`, html)
}

export function printSalesPrice(price: any) {
  const html = buildDocPrintHtml({
    title: '销售价目',
    docNo: price.id || price.productCode || 'sales-price',
    fields: [
      ['适用范围', price.customerId ? (price.customerName || price.customerId) : '商品通用价'],
      ['商品', `${price.productCode || ''} ${price.productName || price.productId || ''}`.trim()],
      ['标准价', money(price.listPrice)],
      ['最低价', money(price.minPrice)],
      ['生效日期', price.effectiveFrom || '-'],
      ['失效日期', price.effectiveTo || '长期'],
      ['状态', price.status || '-'],
      ['备注', price.remark || '-']
    ],
    columns: ['项目', '值'],
    rows: [
      ['客户', escapeHtml(price.customerId ? (price.customerName || price.customerId) : '全部客户')],
      ['商品编码', escapeHtml(price.productCode || price.productId || '')],
      ['商品名称', escapeHtml(price.productName || '')],
      ['标准价', money(price.listPrice)],
      ['最低价', money(price.minPrice)],
      ['生效区间', escapeHtml(`${price.effectiveFrom || '-'} ~ ${price.effectiveTo || '长期'}`)]
    ],
    totals: [['标准价', money(price.listPrice)]]
  })
  printHtml(`销售价目 ${price.productCode || price.id || ''}`, html)
}

export function printPurchasePrice(price: any) {
  const html = buildDocPrintHtml({
    title: '采购价目',
    docNo: price.id || price.productCode || 'purchase-price',
    fields: [
      ['适用范围', price.supplierId ? (price.supplierName || price.supplierId) : '商品通用价'],
      ['商品', `${price.productCode || ''} ${price.productName || price.productId || ''}`.trim()],
      ['标准价', money(price.listPrice)],
      ['最高价', money(price.maxPrice)],
      ['生效日期', price.effectiveFrom || '-'],
      ['失效日期', price.effectiveTo || '长期'],
      ['状态', price.status || '-'],
      ['备注', price.remark || '-']
    ],
    columns: ['项目', '值'],
    rows: [
      ['供应商', escapeHtml(price.supplierId ? (price.supplierName || price.supplierId) : '全部供应商')],
      ['商品编码', escapeHtml(price.productCode || price.productId || '')],
      ['商品名称', escapeHtml(price.productName || '')],
      ['标准价', money(price.listPrice)],
      ['最高价', money(price.maxPrice)],
      ['生效区间', escapeHtml(`${price.effectiveFrom || '-'} ~ ${price.effectiveTo || '长期'}`)]
    ],
    totals: [['标准价', money(price.listPrice)]]
  })
  printHtml(`采购价目 ${price.productCode || price.id || ''}`, html)
}
