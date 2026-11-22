import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderRoute } from '../test/render.tsx'

describe('AppLayout', () => {
  it('redirects / to the scenarios page', async () => {
    renderRoute('/')
    expect(await screen.findByRole('heading', { name: 'Scenarios' })).toBeInTheDocument()
  })

  it('navigates between sections', async () => {
    renderRoute('/scenarios')
    await userEvent.click(screen.getByRole('link', { name: 'Workers' }))
    expect(await screen.findByRole('heading', { name: 'Workers' })).toBeInTheDocument()
  })

  it('shows a not-found page for unknown routes', async () => {
    renderRoute('/does-not-exist')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
