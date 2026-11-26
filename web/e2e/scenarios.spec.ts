import { expect, test } from '@playwright/test'
import { fakeApi } from './fakeApi.ts'

// US-01 in the browser, at every viewport: create a scenario, see it in the list, open it
test('creates a scenario and finds it in the list with version 1', async ({ page, baseURL }) => {
  await fakeApi(page, baseURL!, { signedIn: true })

  await page.goto('/scenarios')
  await page.getByRole('link', { name: 'Create your first scenario' }).click()
  await expect(page).toHaveURL(/\/scenarios\/new$/)

  await page.getByRole('button', { name: 'Create scenario' }).click()
  await expect(page.getByText('Enter a name.')).toBeVisible()

  await page.getByRole('textbox', { name: 'Name' }).fill('Checkout API')
  await page.getByRole('button', { name: 'Use sample script' }).click()
  await expect(page.getByRole('textbox', { name: 'k6 script' })).toContainText("import http from 'k6/http';")
  await page.getByRole('button', { name: 'Create scenario' }).click()

  await expect(page).toHaveURL(/\/scenarios$/)
  await expect(page.getByText('"Checkout API" is saved as version 1.')).toBeVisible()
  const link = page.getByRole('link', { name: 'Checkout API' })
  await expect(link).toBeVisible()
  await expect(page.getByText('v1')).toBeVisible()

  await link.click()
  await expect(page.getByRole('heading', { name: 'Checkout API' })).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Script of version 1' })).toContainText('__ENV.BASE_URL')
})

test('types a script into the editor', async ({ page, baseURL }) => {
  await fakeApi(page, baseURL!, { signedIn: true })
  await page.goto('/scenarios/new')

  await page.getByRole('textbox', { name: 'Name' }).fill('Typed')
  await page.getByRole('textbox', { name: 'k6 script' }).click()
  await page.keyboard.type('export default function () {}')
  await page.getByRole('button', { name: 'Create scenario' }).click()

  await expect(page.getByRole('link', { name: 'Typed' })).toBeVisible()
})
