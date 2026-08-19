import { reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type {
  InventoryReservation,
  InventoryReservationCheckIssue,
  InventoryReservationDetail,
  InventoryReservationSource,
  InventoryReservationSummary,
  InventoryStock,
  InventoryStockQuery
} from '@/api/inventory'
import {
  useInventoryStockReservationList,
  type InventoryStockReservationListDependencies
} from './useInventoryStockReservationList'

const page = <T>(records: T[]) => ({
  records,
  total: records.length,
  pageNo: 1,
  pageSize: 10,
  pages: 1
})

const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

const reservation = {
  id: 'reservation-1',
  warehouseId: 'W-2',
  productId: 'P-2',
  sourceType: 'SALES_ORDER',
  sourceId: 'source-1',
  sourceNo: 'SO-1',
  reservedQty: 5,
  releasedQty: 1,
  remainingQty: 4,
  status: 'ACTIVE'
} as InventoryReservation
const reservationDetail = {
  reservation,
  events: []
} as InventoryReservationDetail
const reservationSource = {
  sourceType: reservation.sourceType,
  sourceId: reservation.sourceId,
  sourceNo: reservation.sourceNo,
  reservations: [reservationDetail]
} as InventoryReservationSource
const reservationSummary = {
  warehouseId: 'W-2',
  productId: 'P-2',
  status: 'ACTIVE',
  reservationCount: 1
} as InventoryReservationSummary
const checkIssue = {
  issueType: 'RESERVED_QUANTITY_MISMATCH',
  severity: 'ERROR',
  reservationId: reservation.id
} as InventoryReservationCheckIssue

const createDependencies = (): InventoryStockReservationListDependencies => ({
  checkReservations: vi.fn(async () => [checkIssue]),
  getReservation: vi.fn(async () => reservationDetail),
  getReservations: vi.fn(async () => page([reservation])),
  getReservationSource: vi.fn(async () => reservationSource),
  getReservationSummary: vi.fn(async () => [reservationSummary]),
  manualRelease: vi.fn(async () => reservationDetail)
})

const createHarness = () => {
  const stockQuery = reactive<InventoryStockQuery>({
    pageNo: 3,
    pageSize: 20,
    warehouseId: 'W-1',
    productId: 'P-1',
    locationId: 'L-1'
  })
  const dependencies = createDependencies()
  const onError = vi.fn()
  const onSuccess = vi.fn()
  const reloadStockList = vi.fn(async () => undefined)
  const list = useInventoryStockReservationList(stockQuery, {
    onError,
    onSuccess,
    reloadStockList,
    t: (key) => key
  }, dependencies)
  return { dependencies, list, onError, onSuccess, reloadStockList, stockQuery }
}

const installValidForm = (list: ReturnType<typeof useInventoryStockReservationList>) => {
  const clearValidate = vi.fn()
  const validate = vi.fn(async () => true)
  list.releaseFormRef.value = {
    clearValidate,
    validate
  } as unknown as typeof list.releaseFormRef.value
  return { clearValidate, validate }
}

describe('inventory stock reservation list', () => {
  it('opens reservations with row scope, active status, and the current page size', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.reservationQuery, {
      pageNo: 4,
      pageSize: 50,
      status: 'RELEASED',
      sourceNo: 'OLD'
    })

    await list.handleOpenReservations({
      warehouseId: 'W-2',
      productId: 'P-2',
      locationId: 'L-2'
    } as InventoryStock)

    expect(list.reservationDialogVisible.value).toBe(true)
    expect(list.reservationQuery).toMatchObject({
      pageNo: 1,
      pageSize: 50,
      warehouseId: 'W-2',
      productId: 'P-2',
      status: 'ACTIVE',
      sourceNo: undefined
    })
    expect(list.reservationQuery).not.toHaveProperty('locationId')
    expect(dependencies.getReservations).toHaveBeenCalledTimes(1)
    expect(dependencies.getReservationSummary).toHaveBeenCalledTimes(1)
    expect(list.reservationData.value).toEqual([reservation])
    expect(list.reservationTotal.value).toBe(1)
    expect(list.reservationSummaryData.value).toEqual([reservationSummary])
    expect(list.reservationLoading.value).toBe(false)
    expect(list.reservationSummaryLoading.value).toBe(false)
  })

  it('inherits the current stock filters when opening without a row', async () => {
    const { dependencies, list } = createHarness()

    await list.handleOpenReservations()

    expect(list.reservationQuery).toMatchObject({ warehouseId: 'W-1', productId: 'P-1' })
    expect(dependencies.getReservationSummary).toHaveBeenCalledWith({
      warehouseId: 'W-1',
      productId: 'P-1',
      status: 'ACTIVE'
    })
  })

  it('reports list and summary failures with their existing message keys', async () => {
    const { dependencies, list, onError } = createHarness()
    vi.mocked(dependencies.getReservations).mockRejectedValueOnce(new Error('list failed'))
    vi.mocked(dependencies.getReservationSummary).mockRejectedValueOnce(new Error('summary failed'))

    await Promise.all([list.loadReservations(), list.loadReservationSummary()])

    expect(onError).toHaveBeenCalledWith(
      'inventoryStocks.message.reservationsLoadFailed',
      expect.any(Error)
    )
    expect(onError).toHaveBeenCalledWith(
      'inventoryStocks.message.reservationSummaryLoadFailed',
      expect.any(Error)
    )
    expect(list.reservationLoading.value).toBe(false)
    expect(list.reservationSummaryLoading.value).toBe(false)
  })

  it('refreshes summary for filters and only the list for pagination', async () => {
    const { dependencies, list } = createHarness()
    Object.assign(list.reservationQuery, {
      pageNo: 5,
      pageSize: 20,
      status: 'RELEASED',
      sourceNo: 'SO-9'
    })

    await list.handleReservationQuery()
    expect(list.reservationQuery.pageNo).toBe(1)
    await list.handleReservationPageChange(3)
    expect(list.reservationQuery.pageNo).toBe(3)
    await list.handleReservationSizeChange(50)
    expect(list.reservationQuery).toMatchObject({ pageNo: 1, pageSize: 50 })
    await list.resetReservationQuery()

    expect(list.reservationQuery).toMatchObject({
      pageNo: 1,
      pageSize: 50,
      status: undefined,
      sourceNo: undefined
    })
    expect(dependencies.getReservations).toHaveBeenCalledTimes(4)
    expect(dependencies.getReservationSummary).toHaveBeenCalledTimes(2)
  })

  it('loads reservation detail with its own dialog and loading state', async () => {
    const { dependencies, list } = createHarness()
    const pending = deferred<InventoryReservationDetail>()
    vi.mocked(dependencies.getReservation).mockImplementationOnce(() => pending.promise)

    const load = list.handleViewReservation(reservation)
    expect(list.reservationDetailVisible.value).toBe(true)
    expect(list.reservationDetailLoading.value).toBe(true)
    expect(list.reservationSourceLoading.value).toBe(false)
    pending.resolve(reservationDetail)
    await load

    expect(dependencies.getReservation).toHaveBeenCalledWith('reservation-1')
    expect(list.reservationDetail.value).toEqual(reservationDetail)
    expect(list.reservationDetailLoading.value).toBe(false)
  })

  it('loads source detail with its own dialog and loading state', async () => {
    const { dependencies, list } = createHarness()
    const pending = deferred<InventoryReservationSource>()
    vi.mocked(dependencies.getReservationSource).mockImplementationOnce(() => pending.promise)

    const load = list.handleViewReservationSource(reservation)
    expect(list.reservationSourceVisible.value).toBe(true)
    expect(list.reservationSourceLoading.value).toBe(true)
    expect(list.reservationDetailLoading.value).toBe(false)
    pending.resolve(reservationSource)
    await load

    expect(dependencies.getReservationSource).toHaveBeenCalledWith({
      sourceType: 'SALES_ORDER',
      sourceId: 'source-1',
      sourceNo: 'SO-1'
    })
    expect(list.reservationSourceDetail.value).toEqual(reservationSource)
    expect(list.reservationSourceLoading.value).toBe(false)
  })

  it('closes detail dialogs and reports their existing failure keys', async () => {
    const { dependencies, list, onError } = createHarness()
    vi.mocked(dependencies.getReservation).mockRejectedValueOnce(new Error('detail failed'))
    vi.mocked(dependencies.getReservationSource).mockRejectedValueOnce(new Error('source failed'))

    await list.handleViewReservation(reservation)
    await list.handleViewReservationSource(reservation)

    expect(list.reservationDetailVisible.value).toBe(false)
    expect(list.reservationSourceVisible.value).toBe(false)
    expect(onError).toHaveBeenCalledWith(
      'inventoryStocks.message.reservationDetailLoadFailed',
      expect.any(Error)
    )
    expect(onError).toHaveBeenCalledWith(
      'inventoryStocks.message.sourceReservationLoadFailed',
      expect.any(Error)
    )
  })

  it('releases a reservation, updates detail, and refreshes all affected data', async () => {
    const { dependencies, list, onSuccess, reloadStockList } = createHarness()
    const form = installValidForm(list)
    list.openReleaseDialog(reservation)
    list.releaseForm.qty = 2
    list.releaseForm.reason = '  manual adjustment  '

    await list.submitManualRelease()

    expect(form.clearValidate).toHaveBeenCalled()
    expect(form.validate).toHaveBeenCalled()
    expect(dependencies.manualRelease).toHaveBeenCalledWith('reservation-1', {
      qty: 2,
      reason: 'manual adjustment'
    })
    expect(list.reservationDetail.value).toEqual(reservationDetail)
    expect(list.releaseDialogVisible.value).toBe(false)
    expect(onSuccess).toHaveBeenCalledWith('inventoryStocks.message.released')
    expect(dependencies.getReservations).toHaveBeenCalledTimes(1)
    expect(dependencies.getReservationSummary).toHaveBeenCalledTimes(1)
    expect(reloadStockList).toHaveBeenCalledTimes(1)
    expect(list.releasing.value).toBe(false)
  })

  it('does not overwrite or stop a newer detail request after releasing', async () => {
    const { dependencies, list } = createHarness()
    const release = deferred<InventoryReservationDetail>()
    const latestDetailRequest = deferred<InventoryReservationDetail>()
    const latestReservation = { ...reservation, id: 'reservation-2' } as InventoryReservation
    const latestDetail = { reservation: latestReservation, events: [] } as InventoryReservationDetail
    installValidForm(list)
    vi.mocked(dependencies.manualRelease).mockImplementationOnce(() => release.promise)
    vi.mocked(dependencies.getReservation).mockImplementationOnce(() => latestDetailRequest.promise)
    list.openReleaseDialog(reservation)
    list.releaseForm.reason = 'reason'

    const releaseSubmit = list.submitManualRelease()
    const detailLoad = list.handleViewReservation(latestReservation)
    release.resolve(reservationDetail)
    await releaseSubmit

    expect(list.reservationDetailLoading.value).toBe(true)
    expect(list.reservationDetail.value).toBeUndefined()
    latestDetailRequest.resolve(latestDetail)
    await detailLoad
    expect(list.reservationDetail.value).toEqual(latestDetail)
    expect(list.reservationDetailLoading.value).toBe(false)
  })

  it('keeps the release dialog open and reports a manual-release failure', async () => {
    const { dependencies, list, onError, reloadStockList } = createHarness()
    installValidForm(list)
    vi.mocked(dependencies.manualRelease).mockRejectedValueOnce(new Error('release failed'))
    list.openReleaseDialog(reservation)
    list.releaseForm.reason = 'reason'

    await list.submitManualRelease()

    expect(list.releaseDialogVisible.value).toBe(true)
    expect(onError).toHaveBeenCalledWith('inventoryStocks.message.releaseFailed', expect.any(Error))
    expect(dependencies.getReservations).not.toHaveBeenCalled()
    expect(dependencies.getReservationSummary).not.toHaveBeenCalled()
    expect(reloadStockList).not.toHaveBeenCalled()
    expect(list.releasing.value).toBe(false)
  })

  it('stops quietly when release form validation fails', async () => {
    const { dependencies, list, onError } = createHarness()
    list.openReleaseDialog(reservation)
    list.releaseFormRef.value = {
      clearValidate: vi.fn(),
      validate: vi.fn(async () => Promise.reject(new Error('invalid form')))
    } as unknown as typeof list.releaseFormRef.value

    await list.submitManualRelease()

    expect(dependencies.manualRelease).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
    expect(list.releaseDialogVisible.value).toBe(true)
    expect(list.releasing.value).toBe(false)
  })

  it('validates positive quantity, remaining quantity, and release reason rules', async () => {
    const { list } = createHarness()
    list.openReleaseDialog(reservation)
    const qtyRules = Array.isArray(list.releaseRules.qty)
      ? list.releaseRules.qty
      : [list.releaseRules.qty]
    const validator = qtyRules[0]?.validator as unknown as (
      rule: unknown,
      value: number,
      callback: (error?: Error) => void
    ) => void
    const validate = (value: number) => new Promise<Error | undefined>((resolve) => {
      validator({}, value, resolve)
    })

    expect((await validate(0))?.message).toBe('inventoryStocks.validation.releasePositive')
    expect((await validate(5))?.message).toBe('inventoryStocks.validation.releaseMaximum')
    expect(await validate(4)).toBeUndefined()

    const reasonRules = Array.isArray(list.releaseRules.reason)
      ? list.releaseRules.reason
      : [list.releaseRules.reason]
    expect(reasonRules).toEqual(expect.arrayContaining([
      expect.objectContaining({
        required: true,
        message: 'inventoryStocks.validation.releaseReason'
      }),
      expect.objectContaining({
        max: 255,
        message: 'inventoryStocks.validation.releaseReasonLength'
      })
    ]))
  })

  it('checks the current stock scope and reports the latest check failure', async () => {
    const { dependencies, list, onError, stockQuery } = createHarness()
    stockQuery.warehouseId = 'W-check'
    stockQuery.productId = 'P-check'

    await list.handleReservationCheck()
    expect(list.checkDialogVisible.value).toBe(true)
    expect(list.checkFailed.value).toBe(false)
    expect(dependencies.checkReservations).toHaveBeenCalledWith({
      warehouseId: 'W-check',
      productId: 'P-check'
    })
    expect(list.checkIssues.value).toEqual([checkIssue])

    vi.mocked(dependencies.checkReservations).mockRejectedValueOnce(new Error('check failed'))
    await list.handleReservationCheck()
    expect(onError).toHaveBeenCalledWith(
      'inventoryStocks.message.reservationCheckFailed',
      expect.any(Error)
    )
    expect(list.checkDialogVisible.value).toBe(true)
    expect(list.checkFailed.value).toBe(true)
    expect(list.checkLoading.value).toBe(false)

    await list.handleReservationCheck()
    expect(list.checkFailed.value).toBe(false)
    expect(list.checkIssues.value).toEqual([checkIssue])
  })

  it('keeps the latest list and summary results and ignores stale failures', async () => {
    const { dependencies, list, onError } = createHarness()
    const firstList = deferred<ReturnType<typeof page<InventoryReservation>>>()
    const secondList = deferred<ReturnType<typeof page<InventoryReservation>>>()
    const firstSummary = deferred<InventoryReservationSummary[]>()
    const secondSummary = deferred<InventoryReservationSummary[]>()
    const latestReservation = { ...reservation, id: 'reservation-2' } as InventoryReservation
    const latestSummary = { ...reservationSummary, reservationCount: 2 } as InventoryReservationSummary
    vi.mocked(dependencies.getReservations)
      .mockImplementationOnce(() => firstList.promise)
      .mockImplementationOnce(() => secondList.promise)
    vi.mocked(dependencies.getReservationSummary)
      .mockImplementationOnce(() => firstSummary.promise)
      .mockImplementationOnce(() => secondSummary.promise)

    const staleListLoad = list.loadReservations()
    const latestListLoad = list.loadReservations()
    const staleSummaryLoad = list.loadReservationSummary()
    const latestSummaryLoad = list.loadReservationSummary()
    secondList.resolve(page([latestReservation]))
    secondSummary.resolve([latestSummary])
    await Promise.all([latestListLoad, latestSummaryLoad])
    firstList.reject(new Error('stale list failure'))
    firstSummary.reject(new Error('stale summary failure'))
    await Promise.all([staleListLoad, staleSummaryLoad])

    expect(list.reservationData.value).toEqual([latestReservation])
    expect(list.reservationSummaryData.value).toEqual([latestSummary])
    expect(list.reservationLoading.value).toBe(false)
    expect(list.reservationSummaryLoading.value).toBe(false)
    expect(onError).not.toHaveBeenCalled()
  })

  it('keeps the latest reservation detail when requests finish out of order', async () => {
    const { dependencies, list, onError } = createHarness()
    const first = deferred<InventoryReservationDetail>()
    const second = deferred<InventoryReservationDetail>()
    const latestReservation = { ...reservation, id: 'reservation-2' } as InventoryReservation
    const latestDetail = { reservation: latestReservation, events: [] } as InventoryReservationDetail
    vi.mocked(dependencies.getReservation)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const staleLoad = list.handleViewReservation(reservation)
    const latestLoad = list.handleViewReservation(latestReservation)
    second.resolve(latestDetail)
    await latestLoad
    first.reject(new Error('stale detail failure'))
    await staleLoad

    expect(list.reservationDetail.value).toEqual(latestDetail)
    expect(list.reservationDetailVisible.value).toBe(true)
    expect(list.reservationDetailLoading.value).toBe(false)
    expect(onError).not.toHaveBeenCalled()
  })

  it('keeps the latest source detail when requests finish out of order', async () => {
    const { dependencies, list, onError } = createHarness()
    const first = deferred<InventoryReservationSource>()
    const second = deferred<InventoryReservationSource>()
    const latestReservation = {
      ...reservation,
      id: 'reservation-2',
      sourceId: 'source-2',
      sourceNo: 'SO-2'
    } as InventoryReservation
    const latestSource = {
      ...reservationSource,
      sourceId: 'source-2',
      sourceNo: 'SO-2'
    } as InventoryReservationSource
    vi.mocked(dependencies.getReservationSource)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const staleLoad = list.handleViewReservationSource(reservation)
    const latestLoad = list.handleViewReservationSource(latestReservation)
    second.resolve(latestSource)
    await latestLoad
    first.reject(new Error('stale source failure'))
    await staleLoad

    expect(list.reservationSourceDetail.value).toEqual(latestSource)
    expect(list.reservationSourceVisible.value).toBe(true)
    expect(list.reservationSourceLoading.value).toBe(false)
    expect(onError).not.toHaveBeenCalled()
  })

  it('keeps the latest reservation check when requests finish out of order', async () => {
    const { dependencies, list, onError } = createHarness()
    const first = deferred<InventoryReservationCheckIssue[]>()
    const second = deferred<InventoryReservationCheckIssue[]>()
    const latestIssue = { ...checkIssue, issueType: 'LATEST' } as InventoryReservationCheckIssue
    vi.mocked(dependencies.checkReservations)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)

    const staleLoad = list.handleReservationCheck()
    const latestLoad = list.handleReservationCheck()
    second.resolve([latestIssue])
    await latestLoad
    first.reject(new Error('stale check failure'))
    await staleLoad

    expect(list.checkIssues.value).toEqual([latestIssue])
    expect(list.checkDialogVisible.value).toBe(true)
    expect(list.checkLoading.value).toBe(false)
    expect(onError).not.toHaveBeenCalled()
  })
})
