import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { i18n } from '@/i18n'
import { useAppStore } from './app'

describe('app display preferences', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('persists locale and updates vue-i18n immediately', () => {
    const store = useAppStore()
    store.setLocale('en-US')
    expect(store.locale).toBe('en-US')
    expect(localStorage.getItem('locale')).toBe('en-US')
    expect(i18n.global.locale.value).toBe('en-US')
    expect(document.documentElement.lang).toBe('en-US')
  })

  it('persists the selected display time zone', () => {
    const store = useAppStore()
    store.setTimeZone('UTC')
    expect(store.timeZone).toBe('UTC')
    expect(localStorage.getItem('timeZone')).toBe('UTC')
  })
})
