import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { RequireAuth } from './auth/RequireAuth.tsx'
import { AppLayout } from './layout/AppLayout.tsx'
import { LoginPage } from './pages/LoginPage.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'
import { RunsPage } from './pages/RunsPage.tsx'
import { ScenariosPage } from './pages/ScenariosPage.tsx'
import { WorkersPage } from './pages/WorkersPage.tsx'

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    // Every page except the login needs a signed-in user (US-23)
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/scenarios" replace /> },
      { path: 'scenarios', element: <ScenariosPage /> },
      { path: 'runs', element: <RunsPage /> },
      { path: 'workers', element: <WorkersPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
