import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { AppLayout } from './layout/AppLayout.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'
import { RunsPage } from './pages/RunsPage.tsx'
import { ScenariosPage } from './pages/ScenariosPage.tsx'
import { WorkersPage } from './pages/WorkersPage.tsx'

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
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
