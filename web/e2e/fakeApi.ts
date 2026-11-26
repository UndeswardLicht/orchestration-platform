import type { Page } from '@playwright/test'

/**
 * Fake Control Service answers for the e2e tests. CI runs these tests against the Vite dev server only,
 * with no backend, so every `/api` call is answered here in the browser. Sign-in, `me`, sign-out,
 * scenarios and their versions behave like the real endpoints, and the worker list comes from the
 * options; any other call gets 404, so a missing fake shows up.
 */
export const ADMIN = { id: 1, login: 'admin', role: 'ADMIN' }

export const FAKE_PASSWORD = 'correct-horse-battery-staple'

export const SAMPLE_SCENARIO_SCRIPT =
  "import http from 'k6/http';\nexport default function () {\n  http.get(__ENV.BASE_URL + '/');\n}\n"

interface FakeScenarioInput {
  name: string
  description?: string
  /** Version 1, 2, ... in order. */
  scripts: string[]
}

interface FakeScenario {
  id: number
  name: string
  description?: string
  versions: { version: number; script: string }[]
}

export interface FakeWorker {
  id: number
  name: string
  status: 'ONLINE' | 'BUSY' | 'LOST' | 'OFFLINE'
  cpus: number
  memMb: number
  version: string
  lastHeartbeatAt: string
  registeredAt: string
}

interface FakeApiOptions {
  signedIn: boolean
  /** Scenarios that exist from the start. */
  scenarios?: FakeScenarioInput[]
  /** What GET /api/v1/workers answers; a function is asked on every call (the page refreshes every 2 s). */
  workers?: FakeWorker[] | (() => FakeWorker[])
}

/** A worker as the Control Service lists it, with a heartbeat `secondsAgo` before now. */
export function fakeWorker(id: number, name: string, status: FakeWorker['status'], secondsAgo = 1): FakeWorker {
  return {
    id,
    name,
    status,
    cpus: 2,
    memMb: 4096,
    version: '0.1.0',
    lastHeartbeatAt: new Date(Date.now() - secondsAgo * 1000).toISOString(),
    registeredAt: '2026-11-27T08:00:00Z',
  }
}

const CREATED_AT = '2026-11-26T09:30:00Z'

/** Not a real SHA-256; the UI only shows it. */
const fakeHash = (script: string) => [...script].reduce((hash, c) => (hash * 31 + c.charCodeAt(0)) >>> 0, 7).toString(16).padStart(64, '0')

export async function fakeApi(page: Page, baseURL: string, { signedIn, scenarios = [], workers = [] }: FakeApiOptions) {
  let user: typeof ADMIN | null = signedIn ? ADMIN : null
  const saved: FakeScenario[] = scenarios.map(({ scripts, ...scenario }, index) => ({
    id: index + 1,
    ...scenario,
    versions: scripts.map((script, v) => ({ version: v + 1, script })),
  }))
  await page.context().addCookies([{ name: 'XSRF-TOKEN', value: 'e2e-csrf-token', url: baseURL }])

  const problem = (status: number, detail: string) => ({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', status, detail }),
  })
  const latestOf = (scenario: FakeScenario) => scenario.versions[scenario.versions.length - 1]
  const version = (scenario: FakeScenario, v: { version: number; script: string }) => ({
    id: scenario.id * 100 + v.version,
    version: v.version,
    script: v.script,
    sha256: fakeHash(v.script),
    createdBy: ADMIN.login,
    createdAt: CREATED_AT,
  })
  const summary = (scenario: FakeScenario) => ({
    id: scenario.id,
    name: scenario.name,
    description: scenario.description ?? null,
    latestVersion: latestOf(scenario).version,
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
    latestVersion: version(scenario, latestOf(scenario)),
  })

  // Only the Control Service paths: a glob like **/api/** would also catch Vite's /src/api/*.ts modules
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const key = `${request.method()} ${path}`

    const match = /^(GET|POST) \/api\/v1\/scenarios\/(\d+)(\/versions(?:\/(\d+))?)?$/.exec(key)
    if (match) {
      const [, method, id, versionsPath, versionNumber] = match
      const scenario = saved.find((candidate) => candidate.id === Number(id))
      if (!scenario) return route.fulfill(problem(404, `No scenario with id ${id}`))
      if (method === 'GET' && !versionsPath) return route.fulfill({ json: details(scenario) })
      if (method === 'GET' && versionNumber === undefined) {
        return route.fulfill({
          json: [...scenario.versions].reverse().map((v) => {
            const { script: _script, ...rest } = version(scenario, v)
            return rest
          }),
        })
      }
      if (method === 'GET') {
        const found = scenario.versions.find((v) => v.version === Number(versionNumber))
        return route.fulfill(
          found ? { json: version(scenario, found) } : problem(404, `Scenario ${id} has no version ${versionNumber}`),
        )
      }
      if (method === 'POST' && versionNumber === undefined) {
        const body = request.postDataJSON() as { script: string; basedOnVersion: number }
        const latest = latestOf(scenario)
        if (body.script === latest.script) return route.fulfill({ json: { created: false, version: version(scenario, latest) } })
        if (body.basedOnVersion !== latest.version) {
          return route.fulfill(
            problem(409, `Your changes are based on version ${body.basedOnVersion}, but version ${latest.version} is the latest. Load it and apply your changes again.`),
          )
        }
        const next = { version: latest.version + 1, script: body.script }
        scenario.versions.push(next)
        return route.fulfill({ status: 201, json: { created: true, version: version(scenario, next) } })
      }
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
      case 'GET /api/v1/workers':
        return route.fulfill({ json: typeof workers === 'function' ? workers() : workers })
      case 'GET /api/v1/scenarios':
        return route.fulfill({ json: [...saved].sort((a, b) => a.name.localeCompare(b.name)).map(summary) })
      case 'POST /api/v1/scenarios': {
        const body = request.postDataJSON() as { name: string; description?: string; script: string }
        if (saved.some((scenario) => scenario.name.toLowerCase() === body.name.toLowerCase())) {
          return route.fulfill(problem(409, `A scenario named '${body.name}' already exists`))
        }
        const created: FakeScenario = {
          id: saved.length + 1,
          name: body.name,
          description: body.description,
          versions: [{ version: 1, script: body.script }],
        }
        saved.push(created)
        return route.fulfill({ status: 201, json: summary(created) })
      }
      default:
        return route.fulfill(problem(404, `No fake answer for ${key}`))
    }
  })
}
