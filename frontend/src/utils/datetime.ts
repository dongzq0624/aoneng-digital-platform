/**
 * Render API timestamps consistently in Beijing time without exposing a timezone suffix.
 * ISO timestamps with a Z/offset are converted. Values without an offset are interpreted
 * as Beijing local time so the display does not depend on the browser's locale.
 */
export function formatBeijingTime(value: unknown, fallback = '-'): string {
  if (!value) return fallback
  const raw = String(value).trim()
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw)
  const normalized = value instanceof Date || hasTimezone
    ? value
    : /^\d{4}-\d{2}-\d{2}(?:[T ]\d{2}:\d{2}(?::\d{2}(?:\.\d{1,}))?)?$/.test(raw)
      ? `${raw.replace(' ', 'T')}${raw.length === 10 ? 'T00:00:00' : ''}+08:00`
      : raw
  const date = normalized instanceof Date ? normalized : new Date(String(normalized))
  if (Number.isNaN(date.getTime())) return String(value)

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const values = Object.fromEntries(
    parts.filter(part => part.type !== 'literal').map(part => [part.type, part.value]),
  ) as Record<string, string>
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`
}
