import { expect, test } from '@playwright/test'
import { fakeApi, SAMPLE_SCENARIO_SCRIPT } from './fakeApi.ts'

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

const ONE_SCENARIO = [{ name: 'Checkout API', scripts: [SAMPLE_SCENARIO_SCRIPT] }]

// US-02 and the Sprint 1 exit demo, at every viewport: edit v1 into v2; v1 stays unchanged and selectable
test('saves an edited script as version 2 and still shows version 1 unchanged', async ({ page, baseURL }) => {
  await fakeApi(page, baseURL!, { signedIn: true, scenarios: ONE_SCENARIO })
  await page.goto('/scenarios/1')

  await page.getByRole('button', { name: 'Edit script' }).click()
  await expect(page.getByRole('button', { name: 'Save as version 2' })).toBeDisabled()
  await page.getByRole('textbox', { name: /^Editing the script/ }).click()
  await page.keyboard.press('ControlOrMeta+End')
  await page.keyboard.type('// version 2')
  await page.getByRole('button', { name: 'Save as version 2' }).click()

  await expect(page.getByText('Saved as version 2.')).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Script of version 2' })).toContainText('// version 2')

  const wide = (page.viewportSize()?.width ?? 0) >= 768
  if (wide) {
    await page.getByRole('navigation', { name: 'Versions' }).getByRole('button', { name: /v1/ }).click()
  } else {
    await page.getByRole('combobox', { name: 'Version' }).click()
    await page.getByRole('option', { name: /^v1 ·/ }).click()
  }

  await expect(page).toHaveURL(/\?version=1$/)
  const v1 = page.getByRole('textbox', { name: 'Script of version 1' })
  await expect(v1).toContainText("http.get(__ENV.BASE_URL + '/');")
  await expect(v1).not.toContainText('// version 2')
  await expect(page.getByText('You are viewing version 1. The latest is version 2.')).toBeVisible()
})

test('asks before leaving with unsaved changes', async ({ page, baseURL }) => {
  await fakeApi(page, baseURL!, { signedIn: true, scenarios: ONE_SCENARIO })
  await page.goto('/scenarios/1')

  await page.getByRole('button', { name: 'Edit script' }).click()
  await page.getByRole('textbox', { name: /^Editing the script/ }).click()
  await page.keyboard.type('// not saved')
  await page.getByRole('link', { name: '← All scenarios' }).click()

  const dialog = page.getByRole('dialog', { name: 'Discard your changes?' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Discard changes' }).click()
  await expect(page).toHaveURL(/\/scenarios$/)
})
