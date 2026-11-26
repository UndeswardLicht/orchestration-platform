const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** A server timestamp (ISO 8601) in the user's locale and time zone, e.g. "26 Nov 2026, 09:30". */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

/** How long ago a server timestamp was, e.g. "just now", "8 s ago", "3 min ago", "2 h ago", "4 days ago". */
export function formatAgo(iso: string, now: number): string {
  const seconds = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 1000))
  if (seconds < 2) return 'just now'
  if (seconds < 60) return `${seconds} s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.floor(hours / 24)
  return `${days} ${days === 1 ? 'day' : 'days'} ago`
}

/** Memory in MB as people read it: "512 MB", "4 GB", "1.5 GB". */
export function formatMemory(mb: number): string {
  if (mb < 1024) return `${mb} MB`
  const gb = Math.round((mb / 1024) * 10) / 10
  return `${gb} GB`
}
