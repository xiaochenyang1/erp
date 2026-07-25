export interface ProductLabelLine {
  productId: string | number
  productCode?: string
  productName?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  auxUnitName?: string | null
  conversionFactor?: number | null
  lotNo?: string | null
  expiryDate?: string | null
  serialNos?: string | null
  quantity?: number | null
  qty?: number | null
}

export interface ProductLabels {
  productCode?: string
  productName?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  auxUnitName?: string | null
  conversionFactor?: number | null
}

export interface ProductControlValidationIssue {
  index: number
  productId: string | number
  productCode?: string
  productName?: string
  field: 'lotNo' | 'expiryDate' | 'serialNos'
  messageKey:
    | 'lotRequired'
    | 'expiryRequired'
    | 'serialRequired'
    | 'serialCountMismatch'
  expectedSerialCount?: number
  actualSerialCount?: number
}

/**
 * Enrich order-derived lines without making the editor depend on optional
 * display fields in document responses. Requests are shared by product id.
 *
 * Always hydrates lot/shelf/serial control flags even when labels already exist,
 * so document UIs can enforce required lot/serial capture before post.
 */
export async function hydrateProductLineLabels<T extends ProductLabelLine>(
  lines: T[],
  loadProduct: (productId: string | number) => Promise<ProductLabels>
): Promise<T[]> {
  const requests = new Map<string, Promise<ProductLabels>>()

  return Promise.all(lines.map(async (line) => {
    const needsLabels = !(line.productCode && line.productName)
    const needsControlFlags =
      line.lotControlled === undefined
      || line.shelfLifeControlled === undefined
      || line.serialControlled === undefined
    const needsAux =
      (line.auxUnitName == null || line.auxUnitName === '')
      || line.conversionFactor == null

    if (!needsLabels && !needsControlFlags && !needsAux) {
      return line
    }

    const key = String(line.productId)
    let request = requests.get(key)
    if (!request) {
      request = loadProduct(line.productId)
      requests.set(key, request)
    }

    try {
      const product = await request
      const factor = product.conversionFactor != null ? Number(product.conversionFactor) : null
      return {
        ...line,
        productCode: line.productCode || product.productCode,
        productName: line.productName || product.productName,
        lotControlled: line.lotControlled ?? Boolean(product.lotControlled),
        shelfLifeControlled: line.shelfLifeControlled ?? Boolean(product.shelfLifeControlled),
        serialControlled: line.serialControlled ?? Boolean(product.serialControlled),
        auxUnitName: line.auxUnitName || product.auxUnitName || undefined,
        conversionFactor: line.conversionFactor ?? (factor != null && factor > 0 ? factor : undefined)
      }
    } catch {
      return line
    }
  }))
}

/** Convert base-unit quantity to auxiliary packaging quantity when conversion is known. */
export function formatAuxQuantity(
  baseQty?: number | null,
  conversionFactor?: number | null,
  auxUnitName?: string | null
): string {
  const qty = Number(baseQty ?? 0)
  const factor = Number(conversionFactor ?? 0)
  if (!(factor > 0) || !auxUnitName) {
    return '-'
  }
  const aux = Number((qty / factor).toFixed(4))
  return `${aux} ${auxUnitName}`
}

/** Compact serial capture progress for document line editors. */
export function serialCaptureProgress(
  serialNos?: string | null,
  quantity?: number | null
): { count: number; expected: number; complete: boolean } {
  const count = parseSerialNos(serialNos).length
  const expected = Math.max(0, Math.round(Number(quantity ?? 0)))
  return {
    count,
    expected,
    complete: expected > 0 && count === expected
  }
}

/** Split free-text serial capture into unique serial numbers. */
export function parseSerialNos(value?: string | null): string[] {
  if (!value) return []
  const seen = new Set<string>()
  const serials: string[] = []
  for (const part of value.split(/[\s,;，；、]+/)) {
    const serial = part.trim()
    if (!serial || seen.has(serial)) continue
    seen.add(serial)
    serials.push(serial)
  }
  return serials
}

/**
 * Client-side preflight for lot/serial-controlled product lines.
 * Returns issues for lines with positive quantity that miss required capture.
 */
export function validateProductControlLines<T extends ProductLabelLine>(
  lines: T[]
): ProductControlValidationIssue[] {
  const issues: ProductControlValidationIssue[] = []

  lines.forEach((line, index) => {
    const qty = Number(line.qty ?? line.quantity ?? 0)
    if (!(qty > 0)) return

    if (line.lotControlled && !String(line.lotNo || '').trim()) {
      issues.push({
        index,
        productId: line.productId,
        productCode: line.productCode,
        productName: line.productName,
        field: 'lotNo',
        messageKey: 'lotRequired'
      })
    }

    if (line.shelfLifeControlled && !String(line.expiryDate || '').trim()) {
      issues.push({
        index,
        productId: line.productId,
        productCode: line.productCode,
        productName: line.productName,
        field: 'expiryDate',
        messageKey: 'expiryRequired'
      })
    }

    if (line.serialControlled) {
      const serials = parseSerialNos(line.serialNos)
      const expected = Math.round(qty)
      if (serials.length === 0) {
        issues.push({
          index,
          productId: line.productId,
          productCode: line.productCode,
          productName: line.productName,
          field: 'serialNos',
          messageKey: 'serialRequired',
          expectedSerialCount: expected,
          actualSerialCount: 0
        })
      } else if (serials.length !== expected) {
        issues.push({
          index,
          productId: line.productId,
          productCode: line.productCode,
          productName: line.productName,
          field: 'serialNos',
          messageKey: 'serialCountMismatch',
          expectedSerialCount: expected,
          actualSerialCount: serials.length
        })
      }
    }
  })

  return issues
}
