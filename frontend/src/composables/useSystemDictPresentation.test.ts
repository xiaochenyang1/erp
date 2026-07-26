import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import type { DictType } from '@/api/system'
import { useSystemDictPresentation } from './useSystemDictPresentation'

const t = (key: string) => key

describe('system dict presentation', () => {
  it('maps status labels and filters types by keyword', () => {
    const presentation = useSystemDictPresentation(t)
    expect(presentation.statusText('ACTIVE')).toBe('systemDicts.active')
    expect(presentation.statusType('ACTIVE')).toBe('success')
    expect(presentation.statusText('INACTIVE')).toBe('systemDicts.inactive')
    expect(presentation.statusType('INACTIVE')).toBe('danger')

    const types = [
      { id: '1', code: 'GENDER', name: '性别' },
      { id: '2', code: 'STATUS', name: '状态' }
    ] as DictType[]
    expect(presentation.filterTypes(types, 'gen').map((item) => item.code)).toEqual(['GENDER'])
    expect(presentation.filterTypes(types, '状').map((item) => item.code)).toEqual(['STATUS'])
    expect(presentation.filterTypes(types, '').length).toBe(2)

    const list = presentation.useFilteredTypeList(ref(types), ref('status'))
    expect(list.value.map((item) => item.code)).toEqual(['STATUS'])
  })
})
