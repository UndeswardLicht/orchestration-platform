import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api } from '../api/client.ts'
import { ApiError, isProblem } from '../api/problem.ts'
import type { components } from '../api/schema'

export type Role = 'ADMIN' | 'DEVELOPER' | 'VIEWER'

/** The signed-in user (US-23). */
export interface CurrentUser {
  id: number
  login: string
  role: Role
}

/** Query key of the signed-in user: `CurrentUser`, or `null` when nobody is signed in. */
export const SESSION_KEY = ['session'] as const

type CurrentUserResponse = components['schemas']['CurrentUserResponse']

/** The generated type marks every field optional; the server always sends all three. */
function toCurrentUser(body: CurrentUserResponse | undefined, status: number): CurrentUser {
  if (body?.id === undefined || body.login === undefined || body.role === undefined) {
    throw new ApiError(status, undefined, 'The Control Service sent an unexpected answer.')
  }
  return { id: body.id, login: body.login, role: body.role }
}

async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const { data, response } = await api.GET('/api/v1/auth/me')
  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    throw new ApiError(response.status, undefined, `Could not load your session (HTTP ${response.status}).`)
  }
  return toCurrentUser(data, response.status)
}

/** Who is signed in. Loaded once per page load; a 401 from any later call resets it (`endSession`). */
export function useCurrentUser() {
  return useQuery({ queryKey: SESSION_KEY, queryFn: fetchCurrentUser, staleTime: Infinity, retry: false })
}

/**
 * Forgets all data of the previous session and sets the new signed-in user (or `null`). Only the
 * session query is kept, so the components that watch it keep working.
 */
export function replaceSession(queryClient: QueryClient, user: CurrentUser | null) {
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== SESSION_KEY[0] })
  queryClient.setQueryData(SESSION_KEY, user)
}

export interface Credentials {
  login: string
  password: string
}

export function useSignIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ login, password }: Credentials): Promise<CurrentUser> => {
      const { data, error, response } = await api.POST('/api/v1/auth/login', { body: { login, password } })
      if (!response.ok) {
        const problem = isProblem(error) ? error : undefined
        throw new ApiError(response.status, problem, `Sign-in failed (HTTP ${response.status}).`)
      }
      return toCurrentUser(data, response.status)
    },
    onSuccess: (user) => replaceSession(queryClient, user),
  })
}

export function useSignOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      await api.POST('/api/v1/auth/logout')
    },
    // Signed out locally even if the call failed: the user asked to leave
    onSettled: () => replaceSession(queryClient, null),
  })
}
