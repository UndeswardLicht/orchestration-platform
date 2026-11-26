import createClient, { type Middleware } from 'openapi-fetch'
import { CSRF_HEADER, ensureCsrfToken } from './csrf.ts'
import { isProblem } from './problem.ts'
import type { paths } from './schema'

/**
 * Typed client for the Control Service. `schema.d.ts` is generated from the springdoc spec:
 * start the Control Service, then run `npm run api:gen`.
 *
 * Session cookie auth (US-23) works with native EventSource for SSE, hence `credentials: 'include'`.
 * The base URL is the page's own origin: the same as a relative URL in the browser (Vite and Caddy
 * proxy `/api`), and it also works in tests, where relative URLs are not allowed.
 */
export const api = createClient<paths>({
  baseUrl: window.location.origin,
  credentials: 'include',
  // Looked up on every call, so tests can replace the global fetch
  fetch: (request) => globalThis.fetch(request),
})

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** Calls to these paths answer 401 as a normal result (wrong password, not signed in yet). */
const AUTH_PATHS = '/api/v1/auth/'

type UnauthorizedListener = (detail: string | undefined) => void

const unauthorizedListeners = new Set<UnauthorizedListener>()

/**
 * Called when an API call answers 401: the session ended (timeout, restart of the Control Service,
 * or the user's role changed). `detail` is the server's explanation. Returns the unsubscribe function.
 */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

const csrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (SAFE_METHODS.has(request.method)) {
      return undefined
    }
    const token = await ensureCsrfToken()
    if (token !== undefined) {
      request.headers.set(CSRF_HEADER, token)
    }
    return request
  },
}

const sessionEndMiddleware: Middleware = {
  async onResponse({ schemaPath, response }) {
    if (response.status !== 401 || schemaPath.startsWith(AUTH_PATHS)) {
      return undefined
    }
    const body: unknown = await response
      .clone()
      .json()
      .catch(() => undefined)
    const detail = isProblem(body) ? body.detail : undefined
    unauthorizedListeners.forEach((listener) => listener(detail))
    return undefined
  },
}

api.use(csrfMiddleware, sessionEndMiddleware)
