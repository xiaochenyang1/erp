import { describe, expect, it } from 'vitest'

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
  })
})
