import { MantineProvider } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { cssVariablesResolver, theme } from './theme.ts'

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } }),
  )

  return (
    <QueryClientProvider client={queryClient}>
      <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver} defaultColorScheme="auto">
        <Notifications />
        {children}
      </MantineProvider>
    </QueryClientProvider>
  )
}
