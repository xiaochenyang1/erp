import { describe, expect, it, vi } from 'vitest'

import type { ProductionOrder } from '@/api/production'
import { useProductionOrderMaterials } from './useProductionOrderMaterials'

const t = (key: string) => key

describe('production order materials', () => {
  it('opens issue dialog with remaining quantities and loads material locations', async () => {
    const loadOrder = vi.fn(async () => ({
      id: 'mo-1',
      materialWarehouseId: 'mw-1',
      planStartDate: '2026-07-20',
      materials: [
        {
          id: 'mat-1',
          materialCode: 'RM-1',
          materialName: 'Steel',
          requiredQuantity: 10,
          issuedQuantity: 4
        },
        {
          id: 'mat-2',
          materialCode: 'RM-2',
          materialName: 'Bolt',
          requiredQuantity: 5,
          issuedQuantity: 5
        }
      ]
    } as ProductionOrder))
    const hydrateMaterialControls = vi.fn(async (materials) => materials.map((material) => ({
      ...material,
      lotControlled: true
    })))
    const loadMaterialLocations = vi.fn()
    const materials = useProductionOrderMaterials(t, {
      loadOrder,
      issueOrder: vi.fn(),
      returnMaterials: vi.fn(),
      hydrateMaterialControls,
      loadMaterialLocations,
      formatBusinessDate: () => '2026-07-26'
    })

    await materials.handleIssue({ id: 'mo-1' } as ProductionOrder)

    expect(materials.issueDialogVisible.value).toBe(true)
    expect(materials.issueForm.materials).toHaveLength(1)
    expect(materials.issueForm.materials[0].remainingQty).toBe(6)
    expect(materials.issueForm.materials[0].issueQty).toBe(6)
    expect(materials.issueForm.issueDate).toBe('2026-07-20')
    expect(loadMaterialLocations).toHaveBeenCalledWith('mw-1')
  })

  it('blocks issue when required serial capture is missing', async () => {
    const onWarning = vi.fn()
    const issueOrder = vi.fn()
    const materials = useProductionOrderMaterials(t, {
      loadOrder: vi.fn(),
      issueOrder,
      returnMaterials: vi.fn(),
      hydrateMaterialControls: vi.fn(async (rows) => rows),
      loadMaterialLocations: vi.fn(),
      formatBusinessDate: () => '2026-07-26',
      onWarning
    })
    materials.issueForm.orderId = 'mo-2'
    materials.issueForm.materials = [{
      id: 'mat-3',
      materialProductId: 'p-1',
      productCode: 'RM-3',
      issueQty: 2,
      remainingQty: 2,
      serialControlled: true,
      serialNos: ''
    } as any]

    await materials.handleConfirmIssueMaterials()

    expect(onWarning).toHaveBeenCalled()
    expect(issueOrder).not.toHaveBeenCalled()
  })

  it('submits issue payload and closes dialog on success', async () => {
    const issueOrder = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const onCompleted = vi.fn()
    const materials = useProductionOrderMaterials(t, {
      loadOrder: vi.fn(),
      issueOrder,
      returnMaterials: vi.fn(),
      hydrateMaterialControls: vi.fn(async (rows) => rows),
      loadMaterialLocations: vi.fn(),
      formatBusinessDate: () => '2026-07-26',
      onSuccess,
      onCompleted
    })
    materials.issueDialogVisible.value = true
    materials.issueForm.orderId = 'mo-3'
    materials.issueForm.issueDate = '2026-07-26'
    materials.issueForm.remark = 'line remark'
    materials.issueForm.materials = [{
      id: 'mat-4',
      materialProductId: 'p-2',
      productCode: 'RM-4',
      issueQty: 3,
      remainingQty: 3,
      lotNo: 'L1',
      serialNos: 'S1,S2,S3'
    } as any]

    await materials.handleConfirmIssueMaterials()

    expect(issueOrder).toHaveBeenCalledWith('mo-3', expect.objectContaining({
      issueDate: '2026-07-26',
      remark: 'line remark',
      lines: [expect.objectContaining({
        orderMaterialId: 'mat-4',
        issueQty: 3,
        lotNo: 'L1',
        serialNos: 'S1,S2,S3'
      })]
    }))
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.issued')
    expect(materials.issueDialogVisible.value).toBe(false)
    expect(onCompleted).toHaveBeenCalled()
  })

  it('opens return dialog and posts selected return lines', async () => {
    const loadOrder = vi.fn(async () => ({
      id: 'mo-4',
      materialWarehouseId: 'mw-2',
      planStartDate: '2026-07-18',
      materials: [
        {
          id: 'mat-5',
          materialCode: 'RM-5',
          materialName: 'Paint',
          issuedQuantity: 4
        }
      ]
    } as ProductionOrder))
    const returnMaterials = vi.fn(async () => ({}))
    const onSuccess = vi.fn()
    const materials = useProductionOrderMaterials(t, {
      loadOrder,
      issueOrder: vi.fn(),
      returnMaterials,
      hydrateMaterialControls: vi.fn(async (rows) => rows),
      loadMaterialLocations: vi.fn(),
      formatBusinessDate: () => '2026-07-26',
      onSuccess
    })

    expect(materials.canReturnMaterials({ status: 'MATERIAL_ISSUED' } as ProductionOrder)).toBe(true)

    await materials.handleReturnMaterials({ id: 'mo-4' } as ProductionOrder)
    expect(materials.returnDialogVisible.value).toBe(true)
    expect(materials.returnForm.materials).toHaveLength(1)

    materials.returnForm.materials[0].returnQty = 2
    materials.returnForm.materials[0].lotNo = 'LOT-2'
    await materials.handleConfirmReturnMaterials()

    expect(returnMaterials).toHaveBeenCalledWith('mo-4', expect.objectContaining({
      returnDate: '2026-07-18',
      lines: [expect.objectContaining({
        orderMaterialId: 'mat-5',
        returnQty: 2,
        lotNo: 'LOT-2'
      })]
    }))
    expect(onSuccess).toHaveBeenCalledWith('productionOrder.message.returned')
  })
})
