import createClient from 'openapi-fetch'
import type { paths } from './schema'

/**
 * Typed client for the Control Service. `schema.d.ts` is generated from the springdoc spec:
 * start the Control Service, then run `npm run api:gen`.
 *
 * Session cookie auth (US-23) works with native EventSource for SSE, hence `credentials: 'include'`.
 */
export const api = createClient<paths>({ baseUrl: '/', credentials: 'include' })
