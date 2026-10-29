import type { LotGenealogyCounterparty, LotGenealogyLink, LotGenealogyNode } from '@/api/inventory'

type Translate = (key: string, params?: Record<string, unknown>) => string

export type GenealogyTreeNode = {
  id: string
  label: string
  detail: string
  reason: string
  reasonType: string
  route: string | null
  children: GenealogyTreeNode[]
}

export const useInventoryLotGenealogyTree = (
  t: Translate,
  options: {
    bizTypeLabel: (link: Pick<LotGenealogyLink, 'bizType' | 'bizLabel'>) => string
    terminalReasonLabel: (reason?: string | null) => string
    terminalReasonType: (reason?: string | null) => string
    counterpartyLabel: (counterparty?: LotGenealogyCounterparty | null) => string
    productLabel: (node: LotGenealogyNode) => string
    lotLabel: (lotNo?: string | null) => string
    formatQty: (value?: string | number | null) => string
    formatDateTime: (value?: string | null) => string
  }
) => {
  const linkDetail = (link: LotGenealogyLink) => [
    options.counterpartyLabel(link.counterparty),
    options.formatQty(link.qty),
    options.formatDateTime(link.occurredTime),
    link.warehouseName || ''
  ].filter((part) => part && part !== '-').join(' · ')

  const mapNode = (node: LotGenealogyNode, direction: string, depth: number): GenealogyTreeNode[] => [{
    id: `${direction}-${depth}-${node.productId}-${node.lotNo ?? ''}`,
    label: `${options.productLabel(node)} ${options.lotLabel(node.lotNo)}`,
    detail: '',
    reason: '',
    reasonType: 'info',
    route: null,
    children: node.links.map((link, index) => ({
      id: `${direction}-${depth}-${link.bizType}-${link.bizNo ?? ''}-${index}`,
      label: [options.bizTypeLabel(link), link.bizNo].filter(Boolean).join(' '),
      detail: linkDetail(link),
      reason: options.terminalReasonLabel(link.terminalReason),
      reasonType: options.terminalReasonType(link.terminalReason),
      route: link.documentRoute ?? null,
      children: link.node ? mapNode(link.node, direction, depth + 1) : []
    }))
  }]

  const toTreeData = (node: LotGenealogyNode | null | undefined, direction: string) =>
    node ? mapNode(node, direction, 1) : []

  const recallHeaders = () => [
    t('inventoryLotGenealogy.recall.product'),
    t('inventoryLotGenealogy.recall.lotNo'),
    t('inventoryLotGenealogy.recall.bizType'),
    t('inventoryLotGenealogy.recall.bizNo'),
    t('inventoryLotGenealogy.recall.orderNo'),
    t('inventoryLotGenealogy.recall.counterpartyCode'),
    t('inventoryLotGenealogy.recall.counterpartyName'),
    t('inventoryLotGenealogy.recall.qty'),
    t('inventoryLotGenealogy.recall.occurredTime')
  ]

  const recallRows = (node: LotGenealogyNode | null | undefined): string[][] => {
    if (!node) return []
    const rows: string[][] = []
    const walk = (current: LotGenealogyNode) => {
      current.links.forEach((link) => {
        if (link.counterparty) {
          rows.push([
            options.productLabel(current),
            current.lotNo || '',
            options.bizTypeLabel(link),
            link.bizNo || '',
            link.counterparty.documentNo || '',
            link.counterparty.code || '',
            link.counterparty.name || '',
            options.formatQty(link.qty),
            options.formatDateTime(link.occurredTime)
          ])
        }
        if (link.node) walk(link.node)
      })
    }
    walk(node)
    return rows
  }

  return { toTreeData, recallRows, recallHeaders }
}
