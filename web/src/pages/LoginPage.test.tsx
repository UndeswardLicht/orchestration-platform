import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ADMIN, CSRF_TOKEN, fakeApi, json, notSignedIn, problem, signedInAs } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'

async function fillAndSubmit(login: string, password: string) {
  const user = userEvent.setup()
  const submit = await screen.findByRole('button', { name: 'Sign in' })
  if (login) await user.type(screen.getByLabelText('Login'), login)
  if (password) await user.type(screen.getByLabelText('Password'), password)
  await user.click(submit)
}

describe('LoginPage', () => {
  it('sends a visitor who is not signed in to the login page', async () => {
    fakeApi({ 'GET /api/v1/auth/me': notSignedIn })
    const { router } = renderRoute('/runs')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    // No "session ended" message on a first visit
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('signs in with a CSRF token and returns to the page the user came from', async () => {
    const api = fakeApi({
      'GET /api/v1/auth/me': notSignedIn,
      'POST /api/v1/auth/login': () => json(200, ADMIN),
    })
    const { router } = renderRoute('/runs')

    await fillAndSubmit('  admin ', 'correct-horse-battery-staple')

    expect(await screen.findByRole('heading', { name: 'Runs' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/runs')

    // The CSRF cookie was fetched first, because there was none yet
    expect(api.callsTo('GET /api/v1/auth/csrf')).toHaveLength(1)
    const [login] = api.callsTo('POST /api/v1/auth/login')
    expect(login.headers.get('X-XSRF-TOKEN')).toBe(CSRF_TOKEN)
    expect(await login.json()).toEqual({ login: 'admin', password: 'correct-horse-battery-staple' })
  })

  it('shows the server message on a wrong login or password and stays on the page', async () => {
    fakeApi({
      'GET /api/v1/auth/me': notSignedIn,
      'POST /api/v1/auth/login': () => problem(401, 'Invalid login or password'),
    })
    const { router } = renderRoute('/login')

    await fillAndSubmit('admin', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid login or password')
    expect(router.state.location.pathname).toBe('/login')
  })

  it('asks for both fields before calling the server', async () => {
    const api = fakeApi({ 'GET /api/v1/auth/me': notSignedIn })
    renderRoute('/login')

    await fillAndSubmit('', '')

    expect(await screen.findByText('Enter your login')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(api.callsTo('POST /api/v1/auth/login')).toHaveLength(0)
  })

  it('sends a signed-in user straight on to the app', async () => {
    signedInAs(ADMIN)
    const { router } = renderRoute('/login')

    expect(await screen.findByRole('heading', { name: 'Scenarios' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/scenarios')
  })

  it('never returns to an address outside the app after sign-in', async () => {
    fakeApi({
      'GET /api/v1/auth/me': notSignedIn,
      'POST /api/v1/auth/login': () => json(200, ADMIN),
    })
    const { router } = renderRoute({ pathname: '/login', state: { from: '//evil.example/phish' } })

    await fillAndSubmit('admin', 'correct-horse-battery-staple')

    await waitFor(() => expect(router.state.location.pathname).toBe('/scenarios'))
  })
})
