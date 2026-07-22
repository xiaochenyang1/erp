import { beforeEach, describe, expect, it } from 'vitest'

import {
  formatLocalizedDateTime,
  formatLocalizedNumber,
  parseApiDateTime,
  readDisplayPreferences
} from './locale'

describe('localized display preferences', () => {
  beforeEach(() => localStorage.clear())

  it('uses safe defaults for unknown stored values', () => {
    localStorage.setItem('locale', 'invalid')
    localStorage.setItem('timeZone', 'Mars/Olympus')
    expect(readDisplayPreferences()).toEqual({ locale: 'zh-CN', timeZone: 'Asia/Shanghai' })
  })

  it('interprets offset-less API timestamps as Asia/Shanghai', () => {
    expect(parseApiDateTime('2026-07-22T16:00:00').toISOString()).toBe('2026-07-22T08:00:00.000Z')
  })

  it('formats the same instant in the selected locale and time zone', () => {
    localStorage.setItem('locale', 'en-US')
    localStorage.setItem('timeZone', 'UTC')
    expect(formatLocalizedDateTime('2026-07-22T16:00:00')).toContain('08:00:00')
    expect(formatLocalizedNumber(1234.5, { minimumFractionDigits: 2 })).toBe('1,234.50')
  })

  it('returns invalid source text instead of an Invalid Date label', () => {
    expect(formatLocalizedDateTime('not-a-date')).toBe('not-a-date')
  })
})
