import { vi } from 'vitest'
import type { CurrentUser } from '../auth/session.ts'

type Handler = (request: Request) => Response | Promise<Response>

/** Handlers keyed by `"METHOD /path"`, for example `"GET /api/v1/auth/me"`. */
export type FakeRoutes = Record<string, Handler>

export const ADMIN: CurrentUser = { id: 1, login: 'admin', role: 'ADMIN' }

export const CSRF_TOKEN = 'test-csrf-token'

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export function problem(status: number, detail: string): Response {
  return new Response(JSON.stringify({ type: 'about:blank', status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

export function noContent(): Response {
  return new Response(null, { status: 204 })
}

/** What the Control Service answers to anonymous calls and to calls after the session ended. */
export const notSignedIn = () => problem(401, 'Sign in first.')

/** Like `GET /api/v1/auth/csrf`: sets the XSRF-TOKEN cookie. */
export function issueCsrfCookie(): Response {
  document.cookie = `XSRF-TOKEN=${CSRF_TOKEN}; path=/`
  return noContent()
}

/**
 * Replaces the global fetch with fixed answers. Unknown calls get 404, so a test fails visibly when
 * the app calls something unexpected. `calls` records every request in order.
 */
export function fakeApi(routes: FakeRoutes) {
  const calls: Request[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(input, init)
    calls.push(request.clone())
    const key = `${request.method} ${new URL(request.url).pathname}`
    const handler = routes[key] ?? (key === 'GET /api/v1/auth/csrf' ? issueCsrfCookie : undefined)
    return handler ? handler(request) : problem(404, `No fake answer for ${key}`)
  })
  vi.stubGlobal('fetch', fetchMock)

  return {
    calls,
    /** Requests to one `"METHOD /path"`. */
    callsTo: (key: string) => calls.filter((call) => `${call.method} ${new URL(call.url).pathname}` === key),
  }
}

/** The usual starting point: `me` answers with this user. */
export function signedInAs(user: CurrentUser, routes: FakeRoutes = {}) {
  return fakeApi({ 'GET /api/v1/auth/me': () => json(200, user), ...routes })
}

export function clearCookies() {
  for (const cookie of document.cookie.split('; ')) {
    const name = cookie.split('=')[0]
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
    }
  }
}
