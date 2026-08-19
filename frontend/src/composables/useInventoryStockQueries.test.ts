import { describe, expect, it } from 'vitest'

import type { InventoryStock } from '@/api/inventory'
import { useInventoryStockQueries } from './useInventoryStockQueries'

describe('inventory stock query state', () => {
  it('initializes the main query from route scope and dialog queries with safe defaults', () => {
    const queries = useInventoryStockQueries({ warehouseId: 'W-1', productId: 'P-1' })

    expect(queries.queryParams).toMatchObject({
      pageNo: 1,
      pageSize: 20,
      warehouseId: 'W-1',
      productId: 'P-1'
    })
    expect(queries.reservationQuery).toMatchObject({ pageNo: 1, pageSize: 10, status: 'ACTIVE' })
    expect(queries.lotBalanceQuery.pageSize).toBe(10)
    expect(queries.lotTraceQuery.pageSize).toBe(10)
  })

  it('applies the current main scope and resets target pagination', () => {
    const queries = useInventoryStockQueries({ warehouseId: 'W-1', productId: 'P-1' })
    queries.lotBalanceQuery.pageNo = 5

    queries.applyStockScope(queries.lotBalanceQuery)

    expect(queries.lotBalanceQuery).toMatchObject({
      warehouseId: 'W-1',
      productId: 'P-1',
      pageNo: 1
    })
  })

  it('uses a selected inventory row in preference to the current filters', () => {
    const queries = useInventoryStockQueries({ warehouseId: 'W-1', productId: 'P-1' })
    const row = { warehouseId: 'W-2', productId: 'P-2' } as InventoryStock

    queries.applyStockScope(queries.lotBalanceQuery, row)

    expect(queries.lotBalanceQuery).toMatchObject({
      warehouseId: 'W-2',
      productId: 'P-2',
      pageNo: 1
    })
  })
})
