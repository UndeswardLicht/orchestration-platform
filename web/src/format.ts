const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** A server timestamp (ISO 8601) in the user's locale and time zone, e.g. "26 Nov 2026, 09:30". */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}
