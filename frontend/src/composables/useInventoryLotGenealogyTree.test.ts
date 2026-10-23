import { describe, expect, it } from 'vitest'

import type { LotGenealogyNode } from '@/api/inventory'
import { useInventoryLotGenealogyTree } from './useInventoryLotGenealogyTree'

const soldNode: LotGenealogyNode = {
  productId: '7001',
  productCode: 'P-7001',
  productName: 'Finished product',
  lotNo: 'LOT-F',
  depth: 0,
  links: [{
    bizType: 'SALES_DELIVERY',
    bizNo: 'SD-1',
    bizLabel: 'Sales delivery',
    documentRoute: '/sales/deliveries?keyword=SD-1',
    occurredTime: '2026-08-01T10:00:00',
    qty: 10,
    warehouseName: 'Main warehouse',
    counterparty: { type: 'CUSTOMER', id: '601', code: 'C-601', name: 'Customer A', documentNo: 'SO-1' },
    terminalReason: 'SOLD',
    node: null
  }]
}

describe('useInventoryLotGenealogyTree', () => {
  const tree = () => useInventoryLotGenealogyTree((key) => key, {
    bizTypeLabel: (link) => link.bizType,
    terminalReasonLabel: (reason) => reason || '',
    terminalReasonType: () => 'info',
    counterpartyLabel: (counterparty) => counterparty?.name || '-',
    productLabel: (node) => node.productCode || node.productId,
    lotLabel: (lotNo) => lotNo || '-',
    formatQty: (value) => String(value ?? '-'),
    formatDateTime: (value) => value || '-'
  })

  it('maps genealogy records into stable tree nodes', () => {
    const data = tree().toTreeData(soldNode, 'DOWNSTREAM')

    expect(data[0].id).toBe('DOWNSTREAM-1-7001-LOT-F')
    expect(data[0].children[0].id).toBe('DOWNSTREAM-1-SALES_DELIVERY-SD-1-0')
    expect(data[0].children[0].route).toBe('/sales/deliveries?keyword=SD-1')
  })

  it('exports customer-facing rows only', () => {
    const { recallRows, recallHeaders } = tree()
    const rows = recallRows(soldNode)

    expect(rows).toHaveLength(1)
    expect(rows[0]).toContain('Customer A')
    expect(recallHeaders()).toHaveLength(rows[0].length)
  })
})
