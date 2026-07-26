import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

import type {
  InventoryAlert,
  InventoryAlertHandleRequest,
  InventoryAlertQuery,
  InventoryAlertRule,
  InventoryAlertRuleCreateRequest,
  InventoryAlertRuleUpdateRequest
} from '@/api/inventory'
import type { Product, Supplier, Warehouse } from '@/api/masterdata'
import type { PageResponse } from '@/types/common'

type Translate = (key: string, params?: Record<string, unknown>) => string
type Notify = (message: string) => void
type Confirm = (message: string, title?: string, options?: Record<string, unknown>) => Promise<unknown>
type Prompt = (
  message: string,
  title?: string,
  options?: Record<string, unknown>
) => Promise<{ value: string }>
type PageQuery = {
  pageNo?: number
  pageSize?: number
  status?: string
  warehouseId?: string | number
  productId?: string | number
}

export const useInventoryAlertList = (
  t: Translate,
  options: {
    getAlerts: (params: InventoryAlertQuery) => Promise<PageResponse<InventoryAlert>>
    getRules: (params?: { warehouseId?: string | number; productId?: string | number }) => Promise<InventoryAlertRule[]>
    createRule: (payload: InventoryAlertRuleCreateRequest) => Promise<unknown>
    updateRule: (id: string | number, payload: InventoryAlertRuleUpdateRequest) => Promise<unknown>
    enableRule: (id: string | number) => Promise<unknown>
    disableRule: (id: string | number) => Promise<unknown>
    ignoreAlert: (payload: InventoryAlertHandleRequest) => Promise<unknown>
    resolveAlert: (payload: InventoryAlertHandleRequest) => Promise<unknown>
    reactivateAlert: (payload: InventoryAlertHandleRequest) => Promise<unknown>
    createSuggestion: (payload: any) => Promise<unknown>
    getWarehouses: (params: PageQuery) => Promise<PageResponse<Warehouse>>
    getProducts: (params: PageQuery) => Promise<PageResponse<Product>>
    getSuppliers: (params: PageQuery) => Promise<PageResponse<Supplier>>
    confirm: Confirm
    prompt: Prompt
    onError?: Notify
    onSuccess?: Notify
    onWarning?: Notify
  }
) => {
  const queryParams = reactive<InventoryAlertQuery>({
    pageNo: 1,
    pageSize: 10,
    warehouseId: undefined,
    productId: undefined,
    alertType: '',
    status: 'ACTIVE'
  })
  const loading = ref(false)
  const tableData = ref<InventoryAlert[]>([])
  const total = ref(0)
  const warehouses = ref<Warehouse[]>([])
  const products = ref<Product[]>([])
  const suppliers = ref<Supplier[]>([])
  const statistics = reactive({
    total: 0,
    outOfStock: 0,
    lowStock: 0,
    shortageQty: 0
  })

  const ruleDialogVisible = ref(false)
  const ruleSubmitLoading = ref(false)
  const ruleFormRef = ref<FormInstance>()
  const ruleForm = reactive<InventoryAlertRuleCreateRequest>({
    warehouseId: '',
    productId: '',
    minQty: 0,
    remark: ''
  })
  const rulesDrawerVisible = ref(false)
  const rulesLoading = ref(false)
  const ruleRows = ref<InventoryAlertRule[]>([])
  const editingRuleId = ref('')
  const ruleRules: FormRules = {
    warehouseId: [{ required: true, message: t('inventoryAlerts.validation.warehouse'), trigger: 'change' }],
    productId: [{ required: true, message: t('inventoryAlerts.validation.product'), trigger: 'change' }],
    minQty: [{ required: true, message: t('inventoryAlerts.validation.minimumStock'), trigger: 'blur' }]
  }

  const suggestionDialogVisible = ref(false)
  const suggestionSubmitLoading = ref(false)
  const suggestionFormRef = ref<FormInstance>()
  const currentAlert = ref<InventoryAlert>()
  const suggestionForm = reactive({
    ruleId: '',
    warehouseId: '',
    productId: '',
    supplierId: undefined as string | undefined,
    suggestedQty: 0,
    expectedArrivalDate: '',
    remark: ''
  })
  const suggestionRules: FormRules = {
    suggestedQty: [{ required: true, message: t('inventoryAlerts.validation.suggestedQuantity'), trigger: 'blur' }]
  }

  const loadStatistics = async () => {
    try {
      const allResponse = await options.getAlerts({ pageNo: 1, pageSize: 1000 })
      statistics.total = allResponse.total || 0
      statistics.outOfStock = (allResponse.records || []).filter((item) => item.alertType === 'OUT_OF_STOCK').length
      statistics.lowStock = (allResponse.records || []).filter((item) => item.alertType === 'LOW_STOCK').length
      statistics.shortageQty = (allResponse.records || []).reduce(
        (sum, item) => sum + Number(item.shortageQty || 0),
        0
      )
    } catch {
      options.onWarning?.(t('inventoryAlerts.message.statisticsLoadFailed'))
    }
  }

  const loadData = async () => {
    loading.value = true
    try {
      const response = await options.getAlerts(queryParams)
      tableData.value = response.records || []
      total.value = response.total || 0
      await loadStatistics()
    } catch {
      options.onError?.(t('inventoryAlerts.message.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  const loadWarehouses = async () => {
    try {
      const response = await options.getWarehouses({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
      warehouses.value = response.records || []
    } catch {
      options.onError?.(t('inventoryAlerts.message.warehousesLoadFailed'))
    }
  }

  const loadProducts = async () => {
    try {
      const response = await options.getProducts({ pageNo: 1, pageSize: 1000, status: 'ACTIVE' })
      products.value = response.records || []
    } catch {
      options.onError?.(t('inventoryAlerts.message.productsLoadFailed'))
    }
  }

  const loadSuppliers = async () => {
    try {
      const response = await options.getSuppliers({ pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      suppliers.value = response.records || []
    } catch {
      options.onError?.(t('inventoryAlerts.message.suppliersLoadFailed'))
    }
  }

  const handleQuery = () => {
    queryParams.pageNo = 1
    void loadData()
  }

  const handleReset = () => {
    queryParams.warehouseId = undefined
    queryParams.productId = undefined
    queryParams.alertType = ''
    queryParams.status = 'ACTIVE'
    handleQuery()
  }

  const resetRuleForm = () => {
    ruleFormRef.value?.clearValidate()
    editingRuleId.value = ''
    Object.assign(ruleForm, {
      warehouseId: '',
      productId: '',
      minQty: 0,
      remark: ''
    })
  }

  const handleCreateRule = async () => {
    if (warehouses.value.length === 0) await loadWarehouses()
    if (products.value.length === 0) await loadProducts()
    resetRuleForm()
    ruleDialogVisible.value = true
  }

  const loadRules = async () => {
    rulesLoading.value = true
    try {
      ruleRows.value = await options.getRules({
        warehouseId: queryParams.warehouseId,
        productId: queryParams.productId
      })
    } catch {
      options.onError?.(t('inventoryAlerts.message.rulesLoadFailed'))
    } finally {
      rulesLoading.value = false
    }
  }

  const openRulesDrawer = async () => {
    rulesDrawerVisible.value = true
    await loadRules()
  }

  const submitRule = async () => {
    if (!ruleFormRef.value) return
    await ruleFormRef.value.validate(async (valid) => {
      if (!valid) return
      ruleSubmitLoading.value = true
      try {
        if (editingRuleId.value) {
          await options.updateRule(editingRuleId.value, {
            minQty: Number(ruleForm.minQty),
            remark: ruleForm.remark
          })
          options.onSuccess?.(t('inventoryAlerts.message.ruleUpdated'))
        } else {
          await options.createRule(ruleForm)
          options.onSuccess?.(t('inventoryAlerts.message.ruleCreated'))
        }
        ruleDialogVisible.value = false
        await loadData()
        if (rulesDrawerVisible.value) await loadRules()
      } catch {
        options.onError?.(
          editingRuleId.value
            ? t('inventoryAlerts.message.ruleUpdateFailed')
            : t('inventoryAlerts.message.ruleCreateFailed')
        )
      } finally {
        ruleSubmitLoading.value = false
      }
    })
  }

  const handleEditRule = async (row: InventoryAlertRule) => {
    if (warehouses.value.length === 0) await loadWarehouses()
    if (products.value.length === 0) await loadProducts()
    editingRuleId.value = String(row.id)
    Object.assign(ruleForm, {
      warehouseId: row.warehouseId,
      productId: row.productId,
      minQty: Number(row.minQty || 0),
      remark: row.remark || ''
    })
    ruleDialogVisible.value = true
  }

  const handleToggleRule = async (row: InventoryAlertRule, enable: boolean) => {
    try {
      if (enable) {
        await options.enableRule(row.id)
        options.onSuccess?.(t('inventoryAlerts.message.ruleEnabled'))
      } else {
        await options.disableRule(row.id)
        options.onSuccess?.(t('inventoryAlerts.message.ruleDisabled'))
      }
      await loadRules()
      await loadData()
    } catch {
      options.onError?.(
        enable
          ? t('inventoryAlerts.message.ruleEnableFailed')
          : t('inventoryAlerts.message.ruleDisableFailed')
      )
    }
  }

  const handleCreateSuggestion = async (row: InventoryAlert) => {
    if (suppliers.value.length === 0) await loadSuppliers()
    currentAlert.value = row
    Object.assign(suggestionForm, {
      ruleId: row.ruleId,
      warehouseId: row.warehouseId,
      productId: row.productId,
      supplierId: undefined,
      suggestedQty: Number(row.shortageQty || 0),
      expectedArrivalDate: '',
      remark: ''
    })
    suggestionDialogVisible.value = true
  }

  const submitSuggestion = async () => {
    if (!suggestionFormRef.value) return
    await suggestionFormRef.value.validate(async (valid) => {
      if (!valid) return
      suggestionSubmitLoading.value = true
      try {
        await options.createSuggestion({
          ruleId: suggestionForm.ruleId,
          warehouseId: suggestionForm.warehouseId,
          productId: suggestionForm.productId,
          supplierId: suggestionForm.supplierId || undefined,
          suggestedQty: suggestionForm.suggestedQty,
          expectedArrivalDate: suggestionForm.expectedArrivalDate || undefined,
          remark: suggestionForm.remark || undefined
        })
        options.onSuccess?.(t('inventoryAlerts.message.suggestionCreated'))
        suggestionDialogVisible.value = false
        await loadData()
      } catch {
        options.onError?.(t('inventoryAlerts.message.suggestionCreateFailed'))
      } finally {
        suggestionSubmitLoading.value = false
      }
    })
  }

  const resetSuggestionForm = () => {
    suggestionFormRef.value?.clearValidate()
    currentAlert.value = undefined
    Object.assign(suggestionForm, {
      ruleId: '',
      warehouseId: '',
      productId: '',
      supplierId: undefined,
      suggestedQty: 0,
      expectedArrivalDate: '',
      remark: ''
    })
  }

  const handleDispose = async (row: InventoryAlert, status: 'IGNORED' | 'RESOLVED') => {
    const isIgnore = status === 'IGNORED'
    try {
      const { value } = await options.prompt(
        t(isIgnore ? 'inventoryAlerts.message.ignoreConfirm' : 'inventoryAlerts.message.resolveConfirm', {
          warehouse: row.warehouseName,
          product: row.productName
        }),
        t(isIgnore ? 'inventoryAlerts.dialog.ignore' : 'inventoryAlerts.dialog.resolve'),
        {
          confirmButtonText: t('inventoryAlerts.action.confirm'),
          cancelButtonText: t('inventoryAlerts.action.cancel'),
          inputType: 'textarea',
          inputPlaceholder: t('inventoryAlerts.placeholder.dispositionRemark'),
          inputValue: ''
        }
      )
      const payload = {
        warehouseId: row.warehouseId,
        productId: row.productId,
        remark: value || undefined
      }
      if (status === 'IGNORED') {
        await options.ignoreAlert(payload)
      } else {
        await options.resolveAlert(payload)
      }
      options.onSuccess?.(t(isIgnore ? 'inventoryAlerts.message.ignored' : 'inventoryAlerts.message.resolved'))
      await loadData()
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t(isIgnore ? 'inventoryAlerts.message.ignoreFailed' : 'inventoryAlerts.message.resolveFailed'))
      }
    }
  }

  const handleReactivate = async (row: InventoryAlert) => {
    try {
      await options.confirm(
        t('inventoryAlerts.message.reactivateConfirm', {
          warehouse: row.warehouseName,
          product: row.productName
        }),
        t('inventoryAlerts.dialog.reactivate'),
        {
          type: 'warning',
          confirmButtonText: t('inventoryAlerts.action.reactivate'),
          cancelButtonText: t('inventoryAlerts.action.cancel')
        }
      )
      await options.reactivateAlert({
        warehouseId: row.warehouseId,
        productId: row.productId
      })
      options.onSuccess?.(t('inventoryAlerts.message.reactivated'))
      await loadData()
    } catch (error) {
      if (error !== 'cancel') {
        options.onError?.(t('inventoryAlerts.message.reactivateFailed'))
      }
    }
  }

  return {
    currentAlert,
    editingRuleId,
    handleCreateRule,
    handleCreateSuggestion,
    handleDispose,
    handleEditRule,
    handleQuery,
    handleReactivate,
    handleReset,
    handleToggleRule,
    loadData,
    loadProducts,
    loadRules,
    loadSuppliers,
    loadWarehouses,
    loading,
    openRulesDrawer,
    products,
    queryParams,
    resetRuleForm,
    resetSuggestionForm,
    ruleDialogVisible,
    ruleForm,
    ruleFormRef,
    ruleRows,
    ruleRules,
    ruleSubmitLoading,
    rulesDrawerVisible,
    rulesLoading,
    statistics,
    submitRule,
    submitSuggestion,
    suggestionDialogVisible,
    suggestionForm,
    suggestionFormRef,
    suggestionRules,
    suggestionSubmitLoading,
    suppliers,
    tableData,
    total,
    warehouses
  }
}
