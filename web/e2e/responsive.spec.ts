import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { fakeApi, SAMPLE_SCENARIO_SCRIPT } from './fakeApi.ts'

/** Long texts, so the layout checks also cover wrapping at 360 px. */
const SCENARIOS = [
  {
    name: 'Checkout API with a rather long scenario name that has to wrap on phones',
    description: 'Browse the catalogue, add three items to the cart, pay by card and wait for the receipt e-mail.',
    script: SAMPLE_SCENARIO_SCRIPT + `// ${'a-very-long-comment-without-spaces-'.repeat(6)}\n`,
  },
  { name: 'Search', script: SAMPLE_SCENARIO_SCRIPT },
]

interface PageCase {
  title: string
  path: string
  signedIn: boolean
  withScenarios?: boolean
  /** Shown once the page has loaded its data. */
  ready: (page: Page) => ReturnType<Page['getByRole']>
}

const heading = (page: Page) => page.getByRole('heading', { level: 2 })

// Grows with the key pages of NFR-08: launch, live, verdict, compare, history
const PAGES: PageCase[] = [
  { title: '/login', path: '/login', signedIn: false, ready: heading },
  { title: '/scenarios (empty)', path: '/scenarios', signedIn: true, ready: (page) => page.getByRole('link', { name: 'Create your first scenario' }) },
  { title: '/scenarios (list)', path: '/scenarios', signedIn: true, withScenarios: true, ready: (page) => page.getByRole('link', { name: 'Search' }) },
  { title: '/scenarios/new', path: '/scenarios/new', signedIn: true, ready: (page) => page.getByRole('button', { name: 'Create scenario' }) },
  { title: '/scenarios/1', path: '/scenarios/1', signedIn: true, withScenarios: true, ready: (page) => page.getByRole('heading', { name: /Checkout API/ }) },
  { title: '/runs', path: '/runs', signedIn: true, ready: heading },
  { title: '/workers', path: '/workers', signedIn: true, ready: heading },
]

async function open(page: Page, baseURL: string | undefined, { path, signedIn, withScenarios, ready }: PageCase) {
  await fakeApi(page, baseURL!, { signedIn, scenarios: withScenarios ? SCENARIOS : [] })
  await page.goto(path)
  await expect(ready(page)).toBeVisible()
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
