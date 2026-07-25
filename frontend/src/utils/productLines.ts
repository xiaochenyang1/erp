export interface ProductLabelLine {
  productId: string | number
  productCode?: string
  productName?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
}

export interface ProductLabels {
  productCode?: string
  productName?: string
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
}

/**
 * Enrich order-derived lines without making the editor depend on optional
 * display fields in document responses. Requests are shared by product id.
 */
export async function hydrateProductLineLabels<T extends ProductLabelLine>(
  lines: T[],
  loadProduct: (productId: string | number) => Promise<ProductLabels>
): Promise<T[]> {
  const requests = new Map<string, Promise<ProductLabels>>()

  return Promise.all(lines.map(async (line) => {
    const needsLabels = !(line.productCode && line.productName)
    // Keep fully labeled lines unchanged to avoid extra masterdata lookups.
    // Control flags are filled only when we already need to load the product.
    if (!needsLabels) {
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
      return {
        ...line,
        productCode: line.productCode || product.productCode,
        productName: line.productName || product.productName,
        lotControlled: line.lotControlled ?? Boolean(product.lotControlled),
        shelfLifeControlled: line.shelfLifeControlled ?? Boolean(product.shelfLifeControlled),
        serialControlled: line.serialControlled ?? Boolean(product.serialControlled)
      }
    } catch {
      return line
    }
  }))
}
