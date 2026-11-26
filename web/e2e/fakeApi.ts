import type { Page } from '@playwright/test'

/**
 * Fake Control Service answers for the e2e tests. CI runs these tests against the Vite dev server only,
 * with no backend, so every `/api` call is answered here in the browser. Sign-in, `me` and
 * sign-out behave like the real endpoints; any other call gets 404, so a missing fake shows up.
 */
export const ADMIN = { id: 1, login: 'admin', role: 'ADMIN' }

export const FAKE_PASSWORD = 'correct-horse-battery-staple'

export async function fakeApi(page: Page, baseURL: string, { signedIn }: { signedIn: boolean }) {
  let user: typeof ADMIN | null = signedIn ? ADMIN : null
  await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'e2e-csrf-token', url: baseURL }])

  const problem = (status: number, detail: string) => ({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', status, detail }),
  })

  // Only the Control Service paths: a glob like **/api/** would also catch Vite's /src/api/*.ts modules
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const key = `${request.method()} ${new URL(request.url()).pathname}`
    switch (key) {
      case 'GET /api/v1/auth/csrf':
        return route.fulfill({ status: 204 })
      case 'GET /api/v1/auth/me':
        return route.fulfill(user ? { json: user } : problem(401, 'Sign in first.'))
      case 'POST /api/v1/auth/login': {
        const body = request.postDataJSON() as { login?: string; password?: string }
        if (body.login !== ADMIN.login || body.password !== FAKE_PASSWORD) {
          return route.fulfill(problem(401, 'Invalid login or password'))
        }
        user = ADMIN
        return route.fulfill({ json: ADMIN })
      }
      case 'POST /api/v1/auth/logout':
        user = null
        return route.fulfill({ status: 204 })
      default:
        return route.fulfill(problem(404, `No fake answer for ${key}`))
    }
  })
}
