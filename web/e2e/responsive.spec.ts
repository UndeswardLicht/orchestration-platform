import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { fakeApi, fakeWorker, SAMPLE_SCENARIO_SCRIPT, type FakeWorker } from './fakeApi.ts'

/**
 * Long texts and several versions, so the layout checks also cover wrapping at 360 px. The latest
 * script is taller than the editor at every viewport, so the axe check always meets a scrolling editor.
 */
const SCENARIOS = [
  {
    name: 'Checkout API with a rather long scenario name that has to wrap on phones',
    description: 'Browse the catalogue, add three items to the cart, pay by card and wait for the receipt e-mail.',
    scripts: [
      SAMPLE_SCENARIO_SCRIPT,
      SAMPLE_SCENARIO_SCRIPT.replace("'/'", "'/cart'"),
      SAMPLE_SCENARIO_SCRIPT.replace("'/'", "'/pay'") +
        `// ${'a-very-long-comment-without-spaces-'.repeat(6)}\n` +
        Array.from({ length: 40 }, (_, step) => `// step ${step + 1}\n`).join(''),
    ],
  },
  { name: 'Search', scripts: [SAMPLE_SCENARIO_SCRIPT] },
]

/** Every status, and a long container-ID-like name, so wrapping and badge contrast are checked too. */
const WORKERS: FakeWorker[] = [
  fakeWorker(1, 'worker-1', 'ONLINE'),
  fakeWorker(2, 'worker-2', 'BUSY'),
  fakeWorker(3, '3f9c2a7b1e4d-a-rather-long-worker-name-from-a-container-host-name', 'LOST', 125),
  fakeWorker(4, 'worker-4', 'OFFLINE', 3 * 3600),
]

interface PageCase {
  title: string
  path: string
  signedIn: boolean
  withScenarios?: boolean
  withWorkers?: boolean
  /** Shown once the page has loaded its data. */
  ready: (page: Page) => ReturnType<Page['getByRole']>
  /** Brings the page into the state to check, after it is ready. */
  prepare?: (page: Page) => Promise<void>
}

const heading = (page: Page) => page.getByRole('heading', { level: 2 })

// Grows with the key pages of NFR-08: launch, live, verdict, compare, history
const PAGES: PageCase[] = [
  { title: '/login', path: '/login', signedIn: false, ready: heading },
  {
    title: '/scenarios (empty)',
    path: '/scenarios',
    signedIn: true,
    ready: (page) => page.getByRole('link', { name: 'Create your first scenario' }),
  },
  {
    title: '/scenarios (list)',
    path: '/scenarios',
    signedIn: true,
    withScenarios: true,
    ready: (page) => page.getByRole('link', { name: 'Search' }),
  },
  {
    title: '/scenarios/new',
    path: '/scenarios/new',
    signedIn: true,
    ready: (page) => page.getByRole('button', { name: 'Create scenario' }),
  },
  {
    title: '/scenarios/1',
    path: '/scenarios/1',
    signedIn: true,
    withScenarios: true,
    ready: (page) => page.getByRole('button', { name: 'Edit script' }),
  },
  {
    title: '/scenarios/1 (old version)',
    path: '/scenarios/1?version=1',
    signedIn: true,
    withScenarios: true,
    ready: (page) => page.getByText('You are viewing version 1. The latest is version 3.'),
  },
  {
    title: '/scenarios/1 (editing)',
    path: '/scenarios/1',
    signedIn: true,
    withScenarios: true,
    ready: (page) => page.getByRole('button', { name: 'Edit script' }),
    prepare: async (page) => {
      await page.getByRole('button', { name: 'Edit script' }).click()
      await expect(page.getByRole('button', { name: 'Save as version 4' })).toBeVisible()
    },
  },
  { title: '/runs', path: '/runs', signedIn: true, ready: heading },
  {
    title: '/workers (empty)',
    path: '/workers',
    signedIn: true,
    ready: (page) => page.getByText('No workers yet'),
  },
  {
    title: '/workers (list)',
    path: '/workers',
    signedIn: true,
    withWorkers: true,
    ready: (page) => page.getByText('worker-4'),
  },
]

async function open(page: Page, baseURL: string | undefined, pageCase: PageCase) {
  const { path, signedIn, withScenarios, withWorkers, ready, prepare } = pageCase
  await fakeApi(page, baseURL!, {
    signedIn,
    scenarios: withScenarios ? SCENARIOS : [],
    workers: withWorkers ? WORKERS : [],
  })
  await page.goto(path)
  await expect(ready(page)).toBeVisible()
  await prepare?.(page)
}

for (const pageCase of PAGES) {
  test.describe(pageCase.title, () => {
    test('has no horizontal scrolling', async ({ page, baseURL }) => {
      await open(page, baseURL, pageCase)
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow).toBeLessThanOrEqual(0)
    })

    test('has no WCAG 2 AA violations', async ({ page, baseURL }) => {
      await open(page, baseURL, pageCase)
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
      expect(results.violations).toEqual([])
    })
  })
}
