import { Alert, Button, Center, Loader, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core'
import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ApiError } from '../api/problem.ts'
import type { LoginRedirectState } from '../auth/RequireAuth.tsx'
import { useCurrentUser, useSignIn } from '../auth/session.ts'

const DEFAULT_PAGE = '/scenarios'

/** Only paths inside the app, so a crafted link cannot send the user elsewhere after sign-in. */
function safeTarget(from: string | undefined): string {
  return from !== undefined && from.startsWith('/') && !from.startsWith('//') && !from.startsWith('/login')
    ? from
    : DEFAULT_PAGE
}

interface FieldErrors {
  login?: string
  password?: string
}

/** Sign-in page (US-23). After signing in, the user returns to the page they came from. */
export function LoginPage() {
  const location = useLocation()
  const session = useCurrentUser()
  const signIn = useSignIn()
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})

  const state = (location.state ?? {}) as LoginRedirectState
  const target = safeTarget(state.from)

  if (session.isPending) {
    return (
      <Center h="100dvh">
        <Loader aria-label="Loading your session" />
      </Center>
    )
  }

  // Signed in just now, or already (for example the login page opened from a bookmark)
  if (session.data) {
    return <Navigate to={target} replace />
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors: FieldErrors = {
      login: login.trim() === '' ? 'Enter your login' : undefined,
      password: password === '' ? 'Enter your password' : undefined,
    }
    setFieldErrors(errors)
    if (errors.login !== undefined || errors.password !== undefined) {
      return
    }
    signIn.mutate({ login: login.trim(), password })
  }

  const signInError = signIn.error instanceof ApiError ? signIn.error.message : signIn.error ? 'Sign-in failed.' : null
  // The reason a session ended matters only until the user tries again
  const sessionMessage = signIn.isIdle ? state.message : undefined

  return (
    <Center mih="100dvh" p="md">
      <Paper component="main" withBorder shadow="sm" p="xl" w="100%" maw={400}>
        <form onSubmit={handleSubmit} noValidate>
          <Stack>
            <div>
              <Title order={1} size="h3">
                LTP
              </Title>
              <Title order={2} size="h4" fw={500}>
                Sign in
              </Title>
            </div>

            {sessionMessage && (
              <Alert color="yellow" role="status">
                {sessionMessage}
              </Alert>
            )}
            {signInError && (
              <Alert color="red" role="alert">
                {signInError}
              </Alert>
            )}

            <TextInput
              label="Login"
              name="login"
              autoComplete="username"
              autoFocus
              value={login}
              onChange={(event) => setLogin(event.currentTarget.value)}
              error={fieldErrors.login}
            />
            <PasswordInput
              label="Password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.currentTarget.value)}
              error={fieldErrors.password}
            />
            <Button type="submit" loading={signIn.isPending} fullWidth>
              Sign in
            </Button>
            <Text size="xs" c="dimmed">
              Accounts are created by an administrator.
            </Text>
          </Stack>
        </form>
      </Paper>
    </Center>
  )
}
