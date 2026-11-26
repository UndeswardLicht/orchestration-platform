import { Alert, Button, Center, Loader, Stack, Text } from '@mantine/core'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { onUnauthorized } from '../api/client.ts'
import { replaceSession, useCurrentUser } from './session.ts'

/** What the login page gets from a redirect: where to go back to, and why the session ended. */
export interface LoginRedirectState {
  from?: string
  message?: string
}

const SESSION_ENDED = 'Your session has ended. Sign in again.'

/** The server's message for any request without a session; a nicer text fits better after a timeout. */
const NOT_SIGNED_IN = 'Sign in first.'

/**
 * Shows its children only to a signed-in user; everybody else goes to the login page and comes back
 * here afterwards. A 401 from any API call later (timeout, restart, role change) ends the session
 * and goes to the login page with the reason.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const queryClient = useQueryClient()
  const session = useCurrentUser()
  const [endedMessage, setEndedMessage] = useState<string>()

  useEffect(
    () =>
      onUnauthorized((detail) => {
        // Message first, so the redirect below already carries it
        setEndedMessage(detail === undefined || detail === NOT_SIGNED_IN ? SESSION_ENDED : detail)
        replaceSession(queryClient, null)
      }),
    [queryClient],
  )

  if (session.isPending) {
    return (
      <Center h="100dvh">
        <Loader aria-label="Loading your session" />
      </Center>
    )
  }

  if (session.isError) {
    return (
      <Center h="100dvh" p="md">
        <Stack maw={420}>
          <Alert color="red" title="Cannot reach the Control Service">
            <Text size="sm">{session.error.message}</Text>
          </Alert>
          <Button onClick={() => void session.refetch()} loading={session.isFetching}>
            Try again
          </Button>
        </Stack>
      </Center>
    )
  }

  if (session.data === null) {
    const state: LoginRedirectState = {
      from: location.pathname + location.search + location.hash,
      message: endedMessage,
    }
    return <Navigate to="/login" replace state={state} />
  }

  return children
}
