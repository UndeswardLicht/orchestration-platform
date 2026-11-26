import type { Page } from '@playwright/test'

/**
 * Fake Control Service answers for the e2e tests. CI runs these tests against the Vite dev server only,
 * with no backend, so every `/api` call is answered here in the browser. Sign-in, `me`, sign-out and
 * scenarios behave like the real endpoints; any other call gets 404, so a missing fake shows up.
 */
export const ADMIN = { id: 1, login: 'admin', role: 'ADMIN' }

export const FAKE_PASSWORD = 'correct-horse-battery-staple'

export const SAMPLE_SCENARIO_SCRIPT =
  "import http from 'k6/http';\nexport default function () {\n  http.get(__ENV.BASE_URL + '/');\n}\n"

interface FakeScenario {
  id: number
  name: string
  description?: string
  script: string
}

interface FakeApiOptions {
  signedIn: boolean
  /** Scenarios that exist from the start. */
  scenarios?: Omit<FakeScenario, 'id'>[]
}

const CREATED_AT = '2026-11-26T09:30:00Z'

export async function fakeApi(page: Page, baseURL: string, { signedIn, scenarios = [] }: FakeApiOptions) {
  let user: typeof ADMIN | null = signedIn ? ADMIN : null
  const saved: FakeScenario[] = scenarios.map((scenario, index) => ({ id: index + 1, ...scenario }))
  await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'e2e-csrf-token', url: baseURL }])

  const problem = (status: number, detail: string) => ({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', status, detail }),
  })
  const summary = (scenario: FakeScenario) => ({
    id: scenario.id,
    name: scenario.name,
    description: scenario.description ?? null,
    latestVersion: 1,
    createdBy: ADMIN.login,
    createdAt: CREATED_AT,
    updatedAt: CREATED_AT,
  })
  const details = (scenario: FakeScenario) => ({
    id: scenario.id,
    name: scenario.name,
    description: scenario.description ?? null,
    createdBy: ADMIN.login,
    createdAt: CREATED_AT,
    latestVersion: {
      id: 100 + scenario.id,
      version: 1,
      script: scenario.script,
      sha256: '0'.repeat(64),
      createdBy: ADMIN.login,
      createdAt: CREATED_AT,
    },
  })

  // Only the Control Service paths: a glob like **/api/** would also catch Vite's /src/api/*.ts modules
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const key = `${request.method()} ${path}`

    const scenarioId = /^GET \/api\/v1\/scenarios\/(\d+)$/.exec(key)?.[1]
    if (scenarioId !== undefined) {
      const found = saved.find((scenario) => scenario.id === Number(scenarioId))
      return route.fulfill(found ? { json: details(found) } : problem(404, `No scenario with id ${scenarioId}`))
    }

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
      case 'GET /api/v1/scenarios':
        return route.fulfill({ json: [...saved].sort((a, b) => a.name.localeCompare(b.name)).map(summary) })
      case 'POST /api/v1/scenarios': {
        const body = request.postDataJSON() as Omit<FakeScenario, 'id'>
        if (saved.some((scenario) => scenario.name.toLowerCase() === body.name.toLowerCase())) {
          return route.fulfill(problem(409, `A scenario named '${body.name}' already exists`))
        }
        const created = { id: saved.length + 1, ...body }
        saved.push(created)
        return route.fulfill({ status: 201, json: summary(created) })
      }
      default:
        return route.fulfill(problem(404, `No fake answer for ${key}`))
    }
  })
}
