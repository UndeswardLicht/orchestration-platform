import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { ADMIN, signedInAs } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'

describe('AppLayout', () => {
  beforeEach(() => {
    signedInAs(ADMIN)
  })

  it('redirects / to the scenarios page', async () => {
    renderRoute('/')
    expect(await screen.findByRole('heading', { name: 'Scenarios' })).toBeInTheDocument()
  })

  it('navigates between sections', async () => {
    renderRoute('/scenarios')
    await userEvent.click(await screen.findByRole('link', { name: 'Workers' }))
    expect(await screen.findByRole('heading', { name: 'Workers' })).toBeInTheDocument()
  })

  it('shows a not-found page for unknown routes', async () => {
    renderRoute('/does-not-exist')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('names the signed-in user and role in the header', async () => {
    renderRoute('/scenarios')
    expect(await screen.findByText('admin')).toBeInTheDocument()
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })
})
