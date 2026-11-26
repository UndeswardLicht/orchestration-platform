import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { json, problem, signedInAs, VIEWER, ADMIN } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'
import type { WorkerSummary } from '../workers/api.ts'
import { START_WORKERS_COMMAND } from './WorkersPage.tsx'

const narrowScreen = window.matchMedia

function wideScreen() {
  window.matchMedia = (query: string) => ({ ...narrowScreen(query), matches: true })
}

afterEach(() => {
  window.matchMedia = narrowScreen
})

const secondsAgo = (seconds: number) => new Date(Date.now() - seconds * 1000).toISOString()

function worker(overrides: Partial<WorkerSummary>): WorkerSummary {
  return {
    id: 3,
    name: 'worker-1',
    status: 'ONLINE',
    cpus: 2,
    memMb: 4096,
    version: '0.1.0',
    lastHeartbeatAt: secondsAgo(1),
    registeredAt: '2026-11-27T08:00:00Z',
    ...overrides,
  }
}

/** A heartbeat from the last few seconds. */
const FRESH = /^(just now|\d+ s ago)$/

const ONLINE = worker({})
const LOST = worker({ id: 4, name: 'worker-2', status: 'LOST', lastHeartbeatAt: secondsAgo(125) })
const OFFLINE = worker({ id: 5, name: 'worker-3', status: 'OFFLINE', cpus: 1, memMb: 512, lastHeartbeatAt: secondsAgo(3 * 3600) })

describe('WorkersPage', () => {
  it('shows each worker with its status, last heartbeat and resources', async () => {
    signedInAs(ADMIN, { 'GET /api/v1/workers': () => json(200, [ONLINE, LOST, OFFLINE]) })
    renderRoute('/workers')

    const list = await screen.findByRole('list', { name: 'Workers' })
    const [first, second, third] = within(list).getAllByRole('listitem')
    expect(within(first!).getByText('worker-1')).toBeInTheDocument()
    expect(within(first!).getByText('ONLINE')).toBeInTheDocument()
    // Fixtures are built when the file loads, so a slow first render may add a few seconds
    expect(within(first!).getByText(FRESH)).toBeInTheDocument()
    expect(within(first!).getByText('2 CPUs, 4 GB · version 0.1.0')).toBeInTheDocument()
    expect(within(second!).getByText('LOST')).toBeInTheDocument()
    expect(within(second!).getByText('2 min ago')).toBeInTheDocument()
    expect(within(third!).getByText('OFFLINE')).toBeInTheDocument()
    expect(within(third!).getByText('3 h ago')).toBeInTheDocument()
    expect(within(third!).getByText('1 CPU, 512 MB · version 0.1.0')).toBeInTheDocument()
  })

  it('sums up the available capacity', async () => {
    signedInAs(ADMIN, {
      'GET /api/v1/workers': () => json(200, [ONLINE, worker({ id: 6, name: 'worker-4', cpus: 4 }), LOST]),
    })
    renderRoute('/workers')

    expect(await screen.findByText('2 ONLINE · 1 LOST — 6 CPUs free for runs')).toBeInTheDocument()
  })

  it('shows a table on wide screens', async () => {
    wideScreen()
    signedInAs(ADMIN, { 'GET /api/v1/workers': () => json(200, [ONLINE, LOST]) })
    renderRoute('/workers')

    const table = await screen.findByRole('table', { name: 'Workers' })
    const rows = within(table).getAllByRole('row')
    expect(rows.map((row) => row.textContent)).toEqual([
      'NameStatusLast heartbeatResourcesVersionRegistered',
      expect.stringMatching(/^worker-1ONLINE(just now|\d+ s ago)2 CPUs, 4 GB0\.1\.0/),
      expect.stringMatching(/^worker-2LOST2 min ago2 CPUs, 4 GB0\.1\.0/),
    ])
    expect(within(rows[2]!).getByTitle('No heartbeat for more than 15 s')).toHaveTextContent('LOST')
  })

  it('is open to every signed-in role, viewers included', async () => {
    signedInAs(VIEWER, { 'GET /api/v1/workers': () => json(200, [ONLINE]) })
    renderRoute('/workers')

    expect(await screen.findByText('worker-1')).toBeInTheDocument()
  })

  it('explains how to start workers when there are none', async () => {
    signedInAs(ADMIN, { 'GET /api/v1/workers': () => json(200, []) })
    renderRoute('/workers')

    expect(await screen.findByText('No workers yet')).toBeInTheDocument()
    expect(screen.getByText(START_WORKERS_COMMAND)).toBeInTheDocument()
  })

  // US-06 acceptance criterion in the UI: a started worker shows ONLINE within 10 s, without reloading
  it('shows a newly registered worker and status changes without reloading', async () => {
    let answers = [[], [ONLINE], [{ ...ONLINE, status: 'LOST' as const }]]
    signedInAs(ADMIN, {
      'GET /api/v1/workers': () => {
        const [next, ...rest] = answers
        if (rest.length > 0) answers = rest
        return json(200, next)
      },
    })
    renderRoute('/workers')

    expect(await screen.findByText('No workers yet')).toBeInTheDocument()
    expect(await screen.findByText('ONLINE')).toBeInTheDocument()
    expect(await screen.findByText('LOST')).toBeInTheDocument()
  }, 20000)

  it('keeps the last known list and says so when a refresh fails', async () => {
    let first = true
    signedInAs(ADMIN, {
      'GET /api/v1/workers': () => {
        if (first) {
          first = false
          return json(200, [ONLINE])
        }
        return problem(503, 'Service Unavailable')
      },
    })
    renderRoute('/workers')

    expect(await screen.findByText('worker-1')).toBeInTheDocument()
    expect(await screen.findByText('Could not load the workers')).toBeInTheDocument()
    expect(screen.getByText(/The list shows the last known status/)).toBeInTheDocument()
    expect(screen.getByText('worker-1')).toBeInTheDocument()
  }, 20000)

  it('says so when the list cannot be loaded at all', async () => {
    signedInAs(ADMIN, { 'GET /api/v1/workers': () => problem(500, 'Internal Server Error') })
    renderRoute('/workers')

    expect(await screen.findByText('Could not load the workers')).toBeInTheDocument()
    expect(screen.getByText(/Retrying every 2 s/)).toBeInTheDocument()
  })
})
