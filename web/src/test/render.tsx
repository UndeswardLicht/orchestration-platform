import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider, type InitialEntry } from 'react-router'
import { AppProviders } from '../AppProviders.tsx'
import { routes } from '../router.tsx'

/**
 * Renders the real route tree at `entry` (a path, or a location with state) with all app providers.
 * Returns the router too, so tests can check where the app navigated.
 */
export function renderRoute(entry: InitialEntry) {
  const router = createMemoryRouter(routes, { initialEntries: [entry] })
  const view = render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  )
  return { ...view, router }
}
