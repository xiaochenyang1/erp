import { beforeEach, describe, expect, it, vi } from 'vitest'
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/utils/request', () => ({ request: { get, post } }))
import { getMrpRun, runMrpPlan, convertMrpLine } from './mrp'

const rawNode = {
  productId: '9007199254740993', productCode: 'C', warehouseId: '9007199254740994', warehouseName: '材料仓',
  supplyType: 'PURCHASE', level: 2, demandQty: '48', onHandQty: '10', purchaseSupplyQty: '0',
  productionSupplyQty: '0', plannedSupplyQty: '0', netQty: '38',
  sources: [{ type: 'BOM', documentId: '9007199254740995', parentProductId: '9007199254740996', parentWarehouseId: '9007199254740994',
    qty: '48', parentNetQty: '16', qtyPer: '3', baseQty: '1', lossRate: '0' }], supplies: [], materials: []
}
beforeEach(() => vi.resetAllMocks())
describe('multilevel MRP API', () => {
  it('preserves long identifiers and normalizes the entire calculation trace including zero shortages', async () => {
    post.mockResolvedValue({ id: '10', purchaseCount: '1', productionCount: '2', purchaseLines: [{ id: '11', productId: rawNode.productId,
      warehouseId: rawNode.warehouseId, netQty: '38', trace: rawNode }], productionLines: [], algorithmVersion: 2,
    calculationSnapshot: { algorithmVersion: 2, nodes: [rawNode, { ...rawNode, productId: '2', netQty: '0' }], warnings: [] } })
    const result = await runMrpPlan()
    expect(result.purchaseLines[0].trace?.sources[0]).toMatchObject({ parentProductId: '9007199254740996', qty: 48, parentNetQty: 16 })
    expect(result.purchaseLines[0].warehouseId).toBe('9007199254740994')
    expect(result.calculationSnapshot?.nodes[1].netQty).toBe(0)
    expect(result.calculationSnapshot?.nodes[0].netQty).toBe(38)
  })
  it('loads legacy runs without manufacturing a trace', async () => {
    get.mockResolvedValue({ id: 1, purchaseCount: 0, productionCount: 0 })
    const run = await getMrpRun(1)
    expect(run.calculationSnapshot).toBeUndefined()
    expect(run.purchaseLines).toEqual([])
  })
  it('sends explicit supplier selection and propagates stale-plan failures', async () => {
    post.mockRejectedValue(new Error('需求已变化，请重新运行MRP'))
    await expect(convertMrpLine('10', '11', { supplierId: '9007199254740997' })).rejects.toThrow('重新运行MRP')
    expect(post).toHaveBeenCalledWith('/inventory/mrp/runs/10/lines/11/convert', { supplierId: '9007199254740997' })
  })
})
