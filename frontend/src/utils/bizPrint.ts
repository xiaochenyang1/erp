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
