import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { api } from '../api/client.ts'
import { ADMIN, CSRF_TOKEN, json, noContent, problem, signedInAs, fakeApi } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'

describe('RequireAuth', () => {
  it('ends the session and shows the reason when a later call answers 401', async () => {
    signedInAs(ADMIN, {
      'GET /api/v1/users': () => problem(401, 'Your access has changed. Sign in again.'),
    })
    const { router } = renderRoute('/workers')
    expect(await screen.findByRole('heading', { name: 'Workers' })).toBeInTheDocument()

    await api.GET('/api/v1/users')

    expect(await screen.findByRole('status')).toHaveTextContent('Your access has changed. Sign in again.')
    expect(router.state.location.pathname).toBe('/login')
  })

  it('explains a timed-out session in plain words', async () => {
    signedInAs(ADMIN, { 'GET /api/v1/users': () => problem(401, 'Sign in first.') })
    renderRoute('/workers')
    await screen.findByRole('heading', { name: 'Workers' })

    await api.GET('/api/v1/users')

    expect(await screen.findByRole('status')).toHaveTextContent('Your session has ended. Sign in again.')
  })

  it('returns to the same page after signing in again', async () => {
    let signedIn = true
    fakeApi({
      'GET /api/v1/auth/me': () => json(200, ADMIN),
      'GET /api/v1/users': () => (signedIn ? json(200, []) : problem(401, 'Sign in first.')),
      'POST /api/v1/auth/login': () => json(200, ADMIN),
    })
    const { router } = renderRoute('/workers')
    await screen.findByRole('heading', { name: 'Workers' })
    signedIn = false
    await api.GET('/api/v1/users')
    await screen.findByRole('heading', { name: 'Sign in' })

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Login'), 'admin')
    await user.type(screen.getByLabelText('Password'), 'correct-horse-battery-staple')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Workers' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/workers')
  })

  it('signs out with a CSRF token and goes to the login page', async () => {
    const calls = signedInAs(ADMIN, { 'POST /api/v1/auth/logout': noContent })
    const { router } = renderRoute('/scenarios')

    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    const [logout] = calls.callsTo('POST /api/v1/auth/logout')
    expect(logout.headers.get('X-XSRF-TOKEN')).toBe(CSRF_TOKEN)
  })

  it('offers a retry when the Control Service cannot be reached', async () => {
    let up = false
    fakeApi({ 'GET /api/v1/auth/me': () => (up ? json(200, ADMIN) : problem(503, 'Service Unavailable')) })
    renderRoute('/scenarios')

    expect(await screen.findByText('Cannot reach the Control Service')).toBeInTheDocument()
    up = true
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Scenarios' })).toBeInTheDocument())
  })
})
