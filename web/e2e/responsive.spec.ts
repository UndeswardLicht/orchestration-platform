import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

// Grows with the key pages of NFR-08: launch, live, verdict, compare, history
const PAGES = ['/scenarios', '/runs', '/workers']

for (const path of PAGES) {
  test.describe(path, () => {
    test('has no horizontal scrolling', async ({ page }) => {
      await page.goto(path)
      await expect(page.getByRole('heading', { level: 2 })).toBeVisible()
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow).toBeLessThanOrEqual(0)
    })

    test('has no WCAG 2 AA violations', async ({ page }) => {
      await page.goto(path)
      await expect(page.getByRole('heading', { level: 2 })).toBeVisible()
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
      expect(results.violations).toEqual([])
    })
  })
}
