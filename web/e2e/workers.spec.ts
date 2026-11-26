import { expect, test } from '@playwright/test'
import { fakeApi, fakeWorker, type FakeWorker } from './fakeApi.ts'

// US-06 and the Sprint 1 exit demo ("start 2 workers with Compose → both ONLINE in the UI"), at every
// viewport: workers that register while the page is open show ONLINE without reloading
test('shows two newly started workers as ONLINE without reloading', async ({ page, baseURL }) => {
  let workers: FakeWorker[] = []
  await fakeApi(page, baseURL!, { signedIn: true, workers: () => workers })

  await page.goto('/workers')
  await expect(page.getByText('No workers yet')).toBeVisible()

  workers = [fakeWorker(1, 'worker-1', 'ONLINE'), fakeWorker(2, 'worker-2', 'ONLINE')]

  // The page asks every 2 s; the story allows 10 s
  await expect(page.getByText('worker-1')).toBeVisible({ timeout: 10_000 })
  await expect(page.getByText('worker-2')).toBeVisible()
  await expect(page.getByText('ONLINE', { exact: true })).toHaveCount(2)
  await expect(page.getByText('2 ONLINE — 4 CPUs free for runs')).toBeVisible()
})

test('shows a worker that stops sending heartbeats as LOST', async ({ page, baseURL }) => {
  let workers = [fakeWorker(1, 'worker-1', 'ONLINE')]
  await fakeApi(page, baseURL!, { signedIn: true, workers: () => workers })

  await page.goto('/workers')
  await expect(page.getByText('ONLINE', { exact: true })).toBeVisible()

  workers = [fakeWorker(1, 'worker-1', 'LOST', 16)]

  await expect(page.getByText('LOST', { exact: true })).toBeVisible({ timeout: 10_000 })
  await expect(page.getByText('16 s ago').or(page.getByText('17 s ago')).or(page.getByText('18 s ago'))).toBeVisible()
})
