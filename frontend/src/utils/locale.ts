import { DEFAULT_LOCALE, isSupportedLocale, type SupportedLocale } from '@/i18n'

export const SUPPORTED_TIME_ZONES = ['Asia/Shanghai', 'UTC', 'America/New_York', 'Europe/London'] as const
export type SupportedTimeZone = typeof SUPPORTED_TIME_ZONES[number]
export const DEFAULT_TIME_ZONE: SupportedTimeZone = 'Asia/Shanghai'
export interface DisplayPreferences {
  locale: SupportedLocale
  timeZone: SupportedTimeZone
}

export const isSupportedTimeZone = (value: unknown): value is SupportedTimeZone =>
  typeof value === 'string' && SUPPORTED_TIME_ZONES.includes(value as SupportedTimeZone)

export const readDisplayPreferences = (): DisplayPreferences => {
  const locale = localStorage.getItem('locale')
  const timeZone = localStorage.getItem('timeZone')
  return {
    locale: isSupportedLocale(locale) ? locale : DEFAULT_LOCALE,
    timeZone: isSupportedTimeZone(timeZone) ? timeZone : DEFAULT_TIME_ZONE
  }
}

const resolveDisplayPreferences = (preferences?: DisplayPreferences): DisplayPreferences => (
  preferences ?? readDisplayPreferences()
)

// 后端 LocalDateTime 按系统时区 Asia/Shanghai 输出；无 offset 时显式补 +08:00，
// 避免浏览器按本机时区解释后再产生二次偏移。
export const parseApiDateTime = (value: string | number | Date): Date => {
  if (value instanceof Date || typeof value === 'number') return new Date(value)
  const normalized = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(value)
    ? `${value.replace(' ', 'T')}+08:00`
    : value
  return new Date(normalized)
}

export const formatLocalizedDateTime = (
  value?: string | number | Date | null,
  options: Intl.DateTimeFormatOptions = {},
  preferences?: DisplayPreferences
): string => {
  if (value === undefined || value === null || value === '') return ''
  const date = parseApiDateTime(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const { locale, timeZone } = resolveDisplayPreferences(preferences)
  return new Intl.DateTimeFormat(locale, {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false,
    ...options
  }).format(date)
}

/**
 * Format a calendar date without interpreting a backend LocalDate as an
 * instant. Date-only strings stay on the same calendar day in every zone.
 */
export const formatLocalizedDate = (
  value?: string | number | Date | null,
  options: Intl.DateTimeFormatOptions = {},
  preferences?: DisplayPreferences
): string => {
  if (value === undefined || value === null || value === '') return ''
  const isLocalDate = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
  const date = isLocalDate ? new Date(`${value}T00:00:00.000Z`) : parseApiDateTime(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const { locale, timeZone } = resolveDisplayPreferences(preferences)
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    ...options,
    timeZone: isLocalDate ? 'UTC' : timeZone
  }).format(date)
}

/**
 * Return the calendar date for a specific instant in the user's configured
 * business/display time zone. This must be used for LocalDate form defaults;
 * slicing Date#toISOString() would silently use UTC and can select yesterday.
 */
export const formatBusinessDate = (
  value: Date | number = new Date(),
  preferences?: DisplayPreferences
): string => {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const { timeZone } = resolveDisplayPreferences(preferences)
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(date)
  const part = (type: Intl.DateTimeFormatPartTypes) => (
    parts.find((item) => item.type === type)?.value || ''
  )
  return `${part('year')}-${part('month')}-${part('day')}`
}

export const getBusinessMonthDateRange = (
  value: Date | number = new Date(),
  preferences?: DisplayPreferences
): [string, string] => {
  const endDate = formatBusinessDate(value, preferences)
  if (!endDate) return ['', '']
  return [`${endDate.slice(0, 7)}-01`, endDate]
}

export const formatLocalizedNumber = (
  value: number,
  options: Intl.NumberFormatOptions = {},
  preferences?: DisplayPreferences
): string => {
  const { locale } = resolveDisplayPreferences(preferences)
  return new Intl.NumberFormat(locale, options).format(Number(value || 0))
}

export const formatLocalizedCurrency = (
  value: number,
  options: Intl.NumberFormatOptions = {},
  preferences?: DisplayPreferences
): string => {
  const { locale } = resolveDisplayPreferences(preferences)
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    ...options
  }).format(Number(value || 0))
}
