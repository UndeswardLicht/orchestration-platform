import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { DEVELOPER, json, problem, signedInAs, VIEWER } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'
import { CHECKOUT } from '../test/scenarios.ts'

describe('ScenariosPage', () => {
  it('lists the scenarios with their newest version and creator', async () => {
    signedInAs(DEVELOPER, { 'GET /api/v1/scenarios': () => json(200, [CHECKOUT]) })
    const { router } = renderRoute('/scenarios')

    const link = await screen.findByRole('link', { name: 'Checkout API' })
    expect(screen.getByText('v1')).toBeInTheDocument()
    expect(screen.getByText('Browse, add to cart, pay')).toBeInTheDocument()
    expect(screen.getByText(/^dana · updated/)).toBeInTheDocument()

    await userEvent.click(link)
    expect(router.state.location.pathname).toBe('/scenarios/12')
  })

  it('invites a developer to create the first scenario', async () => {
    signedInAs(DEVELOPER, { 'GET /api/v1/scenarios': () => json(200, []) })
    const { router } = renderRoute('/scenarios')

    expect(await screen.findByText('No scenarios yet')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Create your first scenario' }))
    expect(router.state.location.pathname).toBe('/scenarios/new')
  })

  it('offers no create action to a viewer', async () => {
    signedInAs(VIEWER, { 'GET /api/v1/scenarios': () => json(200, []) })
    renderRoute('/scenarios')

    expect(await screen.findByText('Ask a developer or an admin to create one.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /create/i })).not.toBeInTheDocument()
  })

  it('shows "New scenario" to a developer when scenarios exist, but not to a viewer', async () => {
    signedInAs(DEVELOPER, { 'GET /api/v1/scenarios': () => json(200, [CHECKOUT]) })
    const developerView = renderRoute('/scenarios')
    expect(await screen.findByRole('link', { name: 'New scenario' })).toBeInTheDocument()
    developerView.unmount()

    signedInAs(VIEWER, { 'GET /api/v1/scenarios': () => json(200, [CHECKOUT]) })
    renderRoute('/scenarios')
    await screen.findByRole('link', { name: 'Checkout API' })
    expect(screen.queryByRole('link', { name: 'New scenario' })).not.toBeInTheDocument()
  })

  it('explains a failed load and offers a retry', async () => {
    let up = false
    signedInAs(DEVELOPER, {
      'GET /api/v1/scenarios': () => (up ? json(200, [CHECKOUT]) : problem(503, 'Database is starting')),
    })
    renderRoute('/scenarios')

    expect(await screen.findByText('Database is starting', {}, { timeout: 3000 })).toBeInTheDocument()
    up = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('link', { name: 'Checkout API' })).toBeInTheDocument()
  })
})
