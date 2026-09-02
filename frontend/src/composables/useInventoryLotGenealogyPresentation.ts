import type { GenealogyLimits, LotGenealogyCounterparty, LotGenealogyLink, LotGenealogyNode } from '@/api/inventory'

type Translate = (key: string, params?: Record<string, unknown>) => string
type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'

const BIZ_TYPES: Record<string, string> = {
  PURCHASE_RECEIPT: 'purchaseReceipt',
  PURCHASE_RETURN: 'purchaseReturn',
  SALES_DELIVERY: 'salesDelivery',
  SALES_RETURN: 'salesReturn',
  PRODUCTION_ISSUE: 'productionIssue',
  PRODUCTION_COMPLETION: 'productionCompletion',
  PRODUCTION_COMPLETION_REVERSAL: 'productionCompletionReversal',
  PRODUCTION_RETURN: 'productionReturn',
  INVENTORY_ADJUSTMENT: 'inventoryAdjustment',
  INVENTORY_TRANSFER: 'inventoryTransfer',
  INVENTORY_CHECK: 'inventoryCheck',
  OPENING_INVENTORY: 'openingBalance',
  OPENING_BALANCE: 'openingBalance'
}

const REASONS: Record<string, string> = {
  PURCHASED: 'purchased',
  SOLD: 'sold',
  RETURNED_BY_CUSTOMER: 'returnedByCustomer',
  RETURNED_TO_SUPPLIER: 'returnedToSupplier',
  MOVED_INTERNALLY: 'movedInternally',
  ADJUSTED: 'adjusted',
  OPENING_BALANCE: 'openingBalance',
  REVERSED: 'reversed',
  IN_PRODUCTION: 'inProduction',
  NO_MATERIAL_ISSUED: 'noMaterialIssued',
  MATERIAL_NOT_LOT_CONTROLLED: 'materialNotLotControlled',
  OUTPUT_NOT_LOT_CONTROLLED: 'outputNotLotControlled',
  ALREADY_VISITED: 'alreadyVisited',
  MAX_DEPTH: 'maxDepth',
  NODE_LIMIT_PER_LEVEL: 'nodeLimitPerLevel',
  NODE_LIMIT_TOTAL: 'nodeLimitTotal',
  UNKNOWN_SOURCE: 'unknownSource',
  UNKNOWN_DESTINATION: 'unknownDestination'
}

const TAG_TYPES: Record<string, TagType> = {
  PURCHASED: 'success',
  RETURNED_BY_CUSTOMER: 'success',
  SOLD: 'danger',
  RETURNED_TO_SUPPLIER: 'danger',
  MAX_DEPTH: 'warning',
  NODE_LIMIT_PER_LEVEL: 'warning',
  NODE_LIMIT_TOTAL: 'warning',
  IN_PRODUCTION: 'warning',
  UNKNOWN_SOURCE: 'warning',
  UNKNOWN_DESTINATION: 'warning'
}

export const useInventoryLotGenealogyPresentation = (
  t: Translate,
  options: {
    formatNumber: (value: number) => string
    formatDateTime: (value: string) => string
  }
) => {
  const bizTypeLabel = (link: Pick<LotGenealogyLink, 'bizType' | 'bizLabel'>) => {
    const key = BIZ_TYPES[String(link.bizType || '').toUpperCase()]
    return key ? t(`inventoryLotGenealogy.bizType.${key}`) : link.bizLabel || link.bizType || '-'
  }

  const terminalReasonLabel = (reason?: string | null) => {
    if (!reason) return ''
    const key = REASONS[reason]
    return key ? t(`inventoryLotGenealogy.reason.${key}`) : reason
  }

  const terminalReasonType = (reason?: string | null): TagType => TAG_TYPES[String(reason || '')] || 'info'

  const counterpartyLabel = (counterparty?: LotGenealogyCounterparty | null) => {
    if (!counterparty) return '-'
    return [counterparty.code, counterparty.name].filter(Boolean).join(' ') || '-'
  }

  const productLabel = (node: Pick<LotGenealogyNode, 'productId' | 'productCode' | 'productName'>) =>
    [node.productCode, node.productName].filter(Boolean).join(' ') || String(node.productId)

  const lotLabel = (lotNo?: string | null) => lotNo || t('inventoryLotGenealogy.noLot')
  const formatQty = (value?: string | number | null) => value == null || value === '' ? '-' : options.formatNumber(Number(value))
  const formatDateTime = (value?: string | null) => value ? options.formatDateTime(value) : '-'

  const truncationBanner = (limits: Pick<GenealogyLimits, 'truncated' | 'truncationReasons'>) => {
    if (!limits.truncated || !limits.truncationReasons.length) return null
    const reasons = limits.truncationReasons.map(terminalReasonLabel).join('、')
    return t('inventoryLotGenealogy.banner.truncated', { reasons })
  }

  const scopeBanner = (limits: Pick<GenealogyLimits, 'scopeLimited'>) =>
    limits.scopeLimited ? t('inventoryLotGenealogy.banner.scopeLimited') : null

  return {
    bizTypeLabel,
    terminalReasonLabel,
    terminalReasonType,
    counterpartyLabel,
    productLabel,
    lotLabel,
    formatQty,
    formatDateTime,
    truncationBanner,
    scopeBanner
  }
}
