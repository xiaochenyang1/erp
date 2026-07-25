import { beforeEach, describe, expect, it } from 'vitest'

import {
  formatBusinessDate,
  formatLocalizedCurrency,
  formatLocalizedDate,
  formatLocalizedDateTime,
  formatLocalizedNumber,
  getBusinessMonthDateRange,
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
    expect(formatLocalizedCurrency(1234.5)).toContain('1,234.50')
    expect(formatLocalizedCurrency(1234.5)).toMatch(/¥/)
  })

  it('returns invalid source text instead of an Invalid Date label', () => {
    expect(formatLocalizedDateTime('not-a-date')).toBe('not-a-date')
  })

  it('localizes a backend LocalDate without shifting its calendar day', () => {
    expect(formatLocalizedDate('2026-07-23', {}, {
      locale: 'en-US',
      timeZone: 'America/New_York'
    })).toBe('07/23/2026')
    expect(formatLocalizedDate('2026-07-23', {}, {
      locale: 'zh-CN',
      timeZone: 'Asia/Shanghai'
    })).toBe('2026/07/23')
  })

  it('formats LocalDate defaults in the configured time zone instead of UTC', () => {
    const instant = new Date('2026-07-22T16:30:00.000Z')
    expect(formatBusinessDate(instant, { locale: 'zh-CN', timeZone: 'Asia/Shanghai' })).toBe('2026-07-23')
    expect(formatBusinessDate(instant, { locale: 'zh-CN', timeZone: 'UTC' })).toBe('2026-07-22')
  })

  it('builds the current business month without crossing into the prior UTC month', () => {
    const shanghaiMonthStart = new Date('2026-06-30T16:30:00.000Z')
    expect(getBusinessMonthDateRange(shanghaiMonthStart, {
      locale: 'zh-CN',
      timeZone: 'Asia/Shanghai'
    })).toEqual(['2026-07-01', '2026-07-01'])
  })
})
