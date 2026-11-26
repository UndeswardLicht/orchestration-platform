import { expect, test } from '@playwright/test'
import { FAKE_PASSWORD, fakeApi } from './fakeApi.ts'

// US-23 in the browser, at every viewport: sign in, land on the requested page, sign out
test('signs in, returns to the requested page and signs out', async ({ page, baseURL }) => {
  await fakeApi(page, baseURL!, { signedIn: false })

  await page.goto('/workers')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await expect(page).toHaveURL(/\/login$/)

  await page.getByLabel('Login', { exact: true }).fill('admin')
  await page.getByLabel('Password', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toHaveText('Invalid login or password')

  await page.getByLabel('Password', { exact: true }).fill(FAKE_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Workers' })).toBeVisible()
  await expect(page).toHaveURL(/\/workers$/)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
})
