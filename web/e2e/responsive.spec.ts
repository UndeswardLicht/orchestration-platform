import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { fakeApi } from './fakeApi.ts'

// Grows with the key pages of NFR-08: launch, live, verdict, compare, history
const PAGES = [
  { path: '/login', signedIn: false },
  { path: '/scenarios', signedIn: true },
  { path: '/runs', signedIn: true },
  { path: '/workers', signedIn: true },
]

async function open(page: Page, baseURL: string | undefined, path: string, signedIn: boolean) {
  await fakeApi(page, baseURL!, { signedIn })
  await page.goto(path)
  await expect(page.getByRole('heading', { level: 2 })).toBeVisible()
}

for (const { path, signedIn } of PAGES) {
  test.describe(path, () => {
    test('has no horizontal scrolling', async ({ page, baseURL }) => {
      await open(page, baseURL, path, signedIn)
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow).toBeLessThanOrEqual(0)
    })

    test('has no WCAG 2 AA violations', async ({ page, baseURL }) => {
      await open(page, baseURL, path, signedIn)
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
      expect(results.violations).toEqual([])
    })
  })
}
