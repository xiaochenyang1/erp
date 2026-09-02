import { reactive, ref } from 'vue'

import type { InventoryLotGenealogy, InventoryLotGenealogyQuery } from '@/api/inventory'

type Translate = (key: string, params?: Record<string, unknown>) => string

export const useInventoryLotGenealogyQuery = (
  t: Translate,
  options: {
    getInventoryLotGenealogy: (params: InventoryLotGenealogyQuery) => Promise<InventoryLotGenealogy>
    onError?: (message: string) => void
  }
) => {
  const loading = ref(false)
  const genealogy = ref<InventoryLotGenealogy | null>(null)
  let requestSeq = 0
  const form = reactive({
    productId: '' as string | number,
    lotNo: '',
    direction: 'BOTH' as 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH',
    maxDepth: 5
  })

  const load = async () => {
    const lotNo = form.lotNo.trim()
    if (form.productId === '' || form.productId == null || !lotNo) {
      options.onError?.(t('inventoryLotGenealogy.feedback.productAndLotRequired'))
      return
    }
    const seq = ++requestSeq
    loading.value = true
    try {
      const response = await options.getInventoryLotGenealogy({
        productId: form.productId,
        lotNo,
        direction: form.direction,
        maxDepth: form.maxDepth
      })
      if (seq === requestSeq) genealogy.value = response
    } catch {
      if (seq === requestSeq) options.onError?.(t('inventoryLotGenealogy.feedback.loadFailed'))
    } finally {
      if (seq === requestSeq) loading.value = false
    }
  }

  const reset = () => {
    form.productId = ''
    form.lotNo = ''
    form.direction = 'BOTH'
    form.maxDepth = 5
    genealogy.value = null
  }

  const applyFromRoute = (query: Record<string, unknown>) => {
    if (query.productId != null && query.productId !== '') form.productId = String(query.productId)
    if (query.lotNo != null && query.lotNo !== '') form.lotNo = String(query.lotNo)
  }

  return { form, loading, genealogy, load, reset, applyFromRoute }
}
