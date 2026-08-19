import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import type {
  InventoryReservation,
  InventoryReservationCheckIssue,
  InventoryReservationDetail,
  InventoryStock
} from '@/api/inventory'
import { useInventoryStockQueries } from './useInventoryStockQueries'
import { useInventoryStockActions } from './useInventoryStockActions'

describe('inventory stock actions', () => {
  const createHarness = () => {
    const queries = useInventoryStockQueries({ warehouseId: 'W-1', productId: 'P-1', locationId: 'L-1' })
    const loaders = {
      loadData: vi.fn(async () => undefined),
      loadReservations: vi.fn(async () => undefined),
      loadReservationSummary: vi.fn(async () => undefined)
    }
    const checkReservations = vi.fn(async () => ([{ id: 'issue-1' }] as unknown as InventoryReservationCheckIssue[]))
    const manualRelease = vi.fn(async () => ({ id: 'detail-1' } as unknown as InventoryReservationDetail))
    const reservationDetail = ref<InventoryReservationDetail | undefined>()
    const onError = vi.fn()
    const onSuccess = vi.fn()
    const actions = useInventoryStockActions(queries, loaders, {
      checkReservations,
      manualRelease,
      onError,
      onSuccess,
      reservationDetail,
      t: (key) => key
    })
    return { actions, checkReservations, loaders, manualRelease, onError, onSuccess, queries, reservationDetail }
  }

  it('opens reservations scoped to the selected stock row', () => {
    const { actions, loaders, queries } = createHarness()

    actions.handleOpenReservations({ warehouseId: 'W-9', productId: 'P-9' } as InventoryStock)

    expect(queries.reservationQuery).toMatchObject({
      warehouseId: 'W-9',
      productId: 'P-9',
      status: 'ACTIVE',
      sourceNo: undefined,
      pageNo: 1
    })
    expect(actions.reservationDialogVisible.value).toBe(true)
    expect(loaders.loadReservationSummary).toHaveBeenCalledTimes(1)
    expect(loaders.loadReservations).toHaveBeenCalledTimes(1)
  })

  it('releases a reservation, stores the detail, and reloads lists', async () => {
    const { actions, loaders, manualRelease, onSuccess, reservationDetail } = createHarness()
    actions.openReleaseDialog({
      id: 'res-1',
      remainingQty: 5
    } as InventoryReservation)
    actions.releaseForm.qty = 2
    actions.releaseForm.reason = 'adjust'
    actions.releaseFormRef.value = {
      clearValidate: vi.fn(),
      validate: vi.fn(async () => true)
    } as unknown as typeof actions.releaseFormRef.value

    await actions.submitManualRelease()

    expect(manualRelease).toHaveBeenCalledWith('res-1', { qty: 2, reason: 'adjust' })
    expect(reservationDetail.value).toMatchObject({ id: 'detail-1' })
    expect(actions.releaseDialogVisible.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('inventoryStocks.message.released')
    expect(loaders.loadReservations).toHaveBeenCalled()
    expect(loaders.loadData).toHaveBeenCalled()
  })

  it('loads reservation check issues for the current stock filters', async () => {
    const { actions, checkReservations, queries } = createHarness()
    queries.queryParams.warehouseId = 'W-check'
    queries.queryParams.productId = 'P-check'

    await actions.handleReservationCheck()

    expect(actions.checkDialogVisible.value).toBe(true)
    expect(checkReservations).toHaveBeenCalledWith({ warehouseId: 'W-check', productId: 'P-check' })
    expect(actions.checkIssues.value[0]).toMatchObject({ id: 'issue-1' })
    expect(actions.checkLoading.value).toBe(false)
  })

  it('reports release failures without closing the dialog', async () => {
    const { actions, manualRelease, onError } = createHarness()
    manualRelease.mockRejectedValueOnce(new Error('boom'))
    actions.openReleaseDialog({ id: 'res-2', remainingQty: 1 } as InventoryReservation)
    actions.releaseForm.qty = 1
    actions.releaseForm.reason = 'fail'
    actions.releaseFormRef.value = {
      clearValidate: vi.fn(),
      validate: vi.fn(async () => true)
    } as unknown as typeof actions.releaseFormRef.value

    await actions.submitManualRelease()

    expect(actions.releaseDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('inventoryStocks.message.releaseFailed', expect.any(Error))
    expect(actions.releasing.value).toBe(false)
  })
})
