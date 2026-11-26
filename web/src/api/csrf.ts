/**
 * CSRF double-submit cookie (ADR-0010): the Control Service sets the `XSRF-TOKEN` cookie, and every
 * change (POST, PUT, PATCH, DELETE), the login included, sends its value back in `X-XSRF-TOKEN`.
 */
export const CSRF_COOKIE = 'XSRF-TOKEN'
export const CSRF_HEADER = 'X-XSRF-TOKEN'
export const CSRF_PATH = '/api/v1/auth/csrf'

export function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const entry = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return entry === undefined ? undefined : decodeURIComponent(entry.slice(prefix.length))
}

/**
 * The current CSRF token. Without the cookie (first visit, or after sign-out) it asks the Control
 * Service for one. Returns `undefined` if the cookie still is not there (for example a Secure cookie on
 * plain HTTP); the request then goes out without the header and the server answers 403 with the reason.
 */
export async function ensureCsrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing !== undefined) {
    return existing
  }
  await fetch(new URL(CSRF_PATH, window.location.origin), { credentials: 'include' })
  return readCookie(CSRF_COOKIE)
}
