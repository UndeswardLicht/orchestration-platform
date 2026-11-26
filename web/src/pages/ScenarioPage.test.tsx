import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { json, problem, signedInAs, VIEWER } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'
import { CHECKOUT_DETAILS, editorText } from '../test/scenarios.ts'

describe('ScenarioPage', () => {
  it('shows the scenario and its version 1 script, read-only', async () => {
    signedInAs(VIEWER, { 'GET /api/v1/scenarios/12': () => json(200, CHECKOUT_DETAILS) })
    renderRoute('/scenarios/12')

    expect(await screen.findByRole('heading', { name: 'Checkout API' })).toBeInTheDocument()
    expect(screen.getByText('v1')).toBeInTheDocument()
    expect(screen.getByText('Browse, add to cart, pay')).toBeInTheDocument()
    expect(editorText()).toContain("http.get(__ENV.BASE_URL + '/checkout');")
    const editor = document.querySelector('.cm-content')!
    expect(editor).toHaveAttribute('contenteditable', 'false')
    expect(editor).toHaveAttribute('aria-readonly', 'true')
  })

  it('says so when the scenario does not exist', async () => {
    signedInAs(VIEWER, { 'GET /api/v1/scenarios/99': () => problem(404, 'No scenario with id 99') })
    renderRoute('/scenarios/99')

    expect(await screen.findByRole('heading', { name: 'Scenario not found' })).toBeInTheDocument()
  })

  it('treats an address that is not an id as not found, without calling the server', async () => {
    const api = signedInAs(VIEWER)
    renderRoute('/scenarios/abc')

    expect(await screen.findByRole('heading', { name: 'Scenario not found' })).toBeInTheDocument()
    expect(api.calls.filter((call) => call.url.includes('/api/v1/scenarios'))).toHaveLength(0)
  })
})
