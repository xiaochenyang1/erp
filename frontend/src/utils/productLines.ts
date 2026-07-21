export interface ProductLabelLine {
  productId: string | number
  productCode?: string
  productName?: string
}

export interface ProductLabels {
  productCode?: string
  productName?: string
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
    if (line.productCode && line.productName) {
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
        productName: line.productName || product.productName
      }
    } catch {
      return line
    }
  }))
}
