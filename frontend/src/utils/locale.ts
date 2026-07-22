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
