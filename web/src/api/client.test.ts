import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeApi, json, noContent, problem } from '../test/fakeApi.ts'
import { api, onUnauthorized } from './client.ts'

describe('api client', () => {
  const unsubscribers: (() => void)[] = []
  afterEach(() => unsubscribers.splice(0).forEach((unsubscribe) => unsubscribe()))

  it('sends no CSRF header on reads', async () => {
    const calls = fakeApi({ 'GET /api/v1/users': () => json(200, []) })

    await api.GET('/api/v1/users')

    expect(calls.callsTo('GET /api/v1/users')[0].headers.has('X-XSRF-TOKEN')).toBe(false)
    expect(calls.callsTo('GET /api/v1/auth/csrf')).toHaveLength(0)
  })

  it('sends the existing CSRF cookie on changes without asking for a new one', async () => {
    document.cookie = 'XSRF-TOKEN=existing-token; path=/'
    const calls = fakeApi({ 'POST /api/v1/auth/logout': noContent })

    await api.POST('/api/v1/auth/logout')

    expect(calls.callsTo('POST /api/v1/auth/logout')[0].headers.get('X-XSRF-TOKEN')).toBe('existing-token')
    expect(calls.callsTo('GET /api/v1/auth/csrf')).toHaveLength(0)
  })

  it('reports a 401 with the server reason', async () => {
    fakeApi({ 'GET /api/v1/users': () => problem(401, 'Your access has changed. Sign in again.') })
    const listener = vi.fn()
    unsubscribers.push(onUnauthorized(listener))

    await api.GET('/api/v1/users')

    expect(listener).toHaveBeenCalledWith('Your access has changed. Sign in again.')
  })

  it('does not report 401 answers of the sign-in endpoints', async () => {
    fakeApi({
      'GET /api/v1/auth/me': () => problem(401, 'Sign in first.'),
      'POST /api/v1/auth/login': () => problem(401, 'Invalid login or password'),
    })
    const listener = vi.fn()
    unsubscribers.push(onUnauthorized(listener))

    await api.GET('/api/v1/auth/me')
    await api.POST('/api/v1/auth/login', { body: { login: 'admin', password: 'wrong' } })

    expect(listener).not.toHaveBeenCalled()
  })
})
