import { beforeEach, describe, expect, it } from 'vitest'

import { useSystemUserSessionPresentation } from './useSystemUserSessionPresentation'

const t = (key: string) => key

describe('system user session presentation', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('locale', 'en-US')
    localStorage.setItem('timeZone', 'UTC')
  })

  it('maps known statuses and tag types', () => {
    const presentation = useSystemUserSessionPresentation(t)

    expect(presentation.isActive('ACTIVE')).toBe(true)
    expect(presentation.statusLabel('ACTIVE')).toBe('userSessions.statusValue.active')
    expect(presentation.statusTagType('ACTIVE')).toBe('success')

    expect(presentation.isActive('REVOKED')).toBe(false)
    expect(presentation.statusLabel('REVOKED')).toBe('userSessions.statusValue.revoked')
    expect(presentation.statusTagType('REVOKED')).toBe('info')
  })

  it('preserves unknown statuses and safely handles empty values', () => {
    const presentation = useSystemUserSessionPresentation(t)

    expect(presentation.statusLabel('EXPIRED')).toBe('EXPIRED')
    expect(presentation.statusTagType('EXPIRED')).toBe('info')
    expect(presentation.isActive('EXPIRED')).toBe(false)

    for (const status of ['', undefined, null]) {
      expect(presentation.statusLabel(status)).toBe('')
      expect(presentation.statusTagType(status)).toBe('info')
      expect(presentation.isActive(status)).toBe(false)
    }
  })

  it('formats timestamps with localized output and the original fallbacks', () => {
    const { formatDateTime } = useSystemUserSessionPresentation(t)

    expect(formatDateTime('2026-07-22T16:00:00')).toContain('08:00:00')
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
    expect(formatDateTime('')).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime(null)).toBe('-')
  })

  it('renders real names with a dash fallback', () => {
    const { realNameLabel } = useSystemUserSessionPresentation(t)

    expect(realNameLabel('Alice')).toBe('Alice')
    expect(realNameLabel('')).toBe('-')
    expect(realNameLabel(undefined)).toBe('-')
    expect(realNameLabel(null)).toBe('-')
  })

  it('prefers the username and falls back to the normalized user id', () => {
    const { sessionUserLabel } = useSystemUserSessionPresentation(t)

    expect(sessionUserLabel({ userId: '42', username: 'alice' })).toBe('alice')
    expect(sessionUserLabel({ userId: '42', username: '' })).toBe('42')
    expect(sessionUserLabel({ userId: '42' })).toBe('42')
    expect(sessionUserLabel({ userId: '' })).toBe('')
    expect(sessionUserLabel(undefined)).toBe('')
    expect(sessionUserLabel(null)).toBe('')
  })
})
