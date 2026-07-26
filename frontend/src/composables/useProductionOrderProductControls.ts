import type { Ref } from 'vue'

import type { Product } from '@/api/masterdata'
import {
  hydrateProductLineLabels,
  type ProductLabels
} from '@/utils/productLines'

export interface ProductControlFlags {
  lotControlled?: boolean
  shelfLifeControlled?: boolean
  serialControlled?: boolean
  productCode?: string
  productName?: string
}

type LoadProduct = (productId: string | number) => Promise<ProductLabels & {
  code?: string
  name?: string
}>

export const useProductionOrderProductControls = (
  productOptions: Ref<Product[]>,
  loadProduct: LoadProduct
) => {
  const productControlFromOptions = (productId?: string | number): ProductControlFlags => {
    const product = productOptions.value.find((item) => String(item.id) === String(productId))
    if (!product) {
      return {
        lotControlled: undefined,
        shelfLifeControlled: undefined,
        serialControlled: undefined,
        productCode: undefined,
        productName: undefined
      }
    }
    return {
      lotControlled: Boolean(product.lotControlled),
      shelfLifeControlled: Boolean(product.shelfLifeControlled),
      serialControlled: Boolean(product.serialControlled),
      productCode: product.code || product.productCode,
      productName: product.name || product.productName
    }
  }

  const loadProductControlLabels = async (productId: string | number) => {
    const product = await loadProduct(productId)
    return {
      productCode: product.code || product.productCode,
      productName: product.name || product.productName,
      lotControlled: Boolean(product.lotControlled),
      shelfLifeControlled: Boolean(product.shelfLifeControlled),
      serialControlled: Boolean(product.serialControlled)
    }
  }

  const resolveProductControls = async (productId?: string | number): Promise<ProductControlFlags> => {
    if (productId == null || productId === '') {
      return productControlFromOptions(productId)
    }
    const cached = productControlFromOptions(productId)
    if (
      cached.lotControlled !== undefined
      && cached.shelfLifeControlled !== undefined
      && cached.serialControlled !== undefined
    ) {
      return cached
    }
    try {
      const [hydrated] = await hydrateProductLineLabels(
        [{
          productId,
          productCode: cached.productCode,
          productName: cached.productName,
          lotControlled: cached.lotControlled,
          shelfLifeControlled: cached.shelfLifeControlled,
          serialControlled: cached.serialControlled
        }],
        loadProductControlLabels
      )
      return {
        lotControlled: hydrated.lotControlled,
        shelfLifeControlled: hydrated.shelfLifeControlled,
        serialControlled: hydrated.serialControlled,
        productCode: hydrated.productCode,
        productName: hydrated.productName
      }
    } catch {
      return cached
    }
  }

  const hydrateMaterialControls = async <T extends {
    materialProductId?: string | number
    materialId?: string | number
    materialCode?: string
    materialName?: string
    productCode?: string
    productName?: string
    lotControlled?: boolean
    shelfLifeControlled?: boolean
    serialControlled?: boolean
  }>(materials: T[]): Promise<T[]> => {
    return hydrateProductLineLabels(
      materials.map((material) => ({
        ...material,
        productId: material.materialProductId ?? material.materialId as string | number,
        productCode: material.productCode || material.materialCode,
        productName: material.productName || material.materialName
      })),
      loadProductControlLabels
    ) as Promise<T[]>
  }

  return {
    hydrateMaterialControls,
    productControlFromOptions,
    resolveProductControls
  }
}
