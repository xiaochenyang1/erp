export interface ScannableLine {
  productId: string | number
  quantity: number
}

export type ScanIncrementResult =
  | { status: 'incremented'; index: number; quantity: number }
  | { status: 'not-found' }
  | { status: 'at-maximum'; index: number; quantity: number }

export const incrementScannedLine = <T extends ScannableLine>(
  lines: T[],
  scannedProductId: string | number,
  maximumFor: (line: T) => number
): ScanIncrementResult => {
  const index = lines.findIndex((line) => String(line.productId) === String(scannedProductId))
  if (index < 0) {
    return { status: 'not-found' }
  }

  const line = lines[index]
  const quantity = Number(line.quantity ?? 0)
  const maximum = Math.max(0, Number(maximumFor(line) ?? 0))
  if (quantity >= maximum) {
    return { status: 'at-maximum', index, quantity }
  }

  line.quantity = Math.min(maximum, quantity + 1)
  return { status: 'incremented', index, quantity: line.quantity }
}
