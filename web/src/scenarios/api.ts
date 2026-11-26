import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.ts'
import { ApiError, isProblem } from '../api/problem.ts'
import type { components } from '../api/schema'

export type ScenarioSummary = components['schemas']['ScenarioSummary']
export type ScenarioDetails = components['schemas']['ScenarioDetails']

/** Same limits as the Control Service (US-01); checked here too, so the user learns it before sending. */
export const MAX_NAME_LENGTH = 200
export const MAX_DESCRIPTION_LENGTH = 2000
export const MAX_SCRIPT_BYTES = 1024 * 1024

export const SCENARIOS_KEY = ['scenarios'] as const

/** The server counts the script limit in UTF-8 bytes, not characters. */
export function utf8Length(text: string): number {
  return new TextEncoder().encode(text).length
}

function fail(response: Response, error: unknown, fallback: string): never {
  throw new ApiError(response.status, isProblem(error) ? error : undefined, fallback)
}

/** No retry for answers that will not change (404, 403); one retry for server or network trouble. */
function retryOnce(failureCount: number, error: Error): boolean {
  return failureCount < 1 && !(error instanceof ApiError && error.status < 500)
}

export function useScenarios() {
  return useQuery({
    queryKey: SCENARIOS_KEY,
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/v1/scenarios')
      if (!data) fail(response, error, `Could not load the scenarios (HTTP ${response.status}).`)
      return data
    },
    retry: retryOnce,
  })
}

export function useScenario(id: number) {
  return useQuery({
    queryKey: [...SCENARIOS_KEY, id],
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/v1/scenarios/{id}', { params: { path: { id } } })
      if (!data) fail(response, error, `Could not load the scenario (HTTP ${response.status}).`)
      return data
    },
    retry: retryOnce,
  })
}

export interface NewScenario {
  name: string
  description: string
  script: string
}

export function useCreateScenario() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ name, description, script }: NewScenario) => {
      const { data, error, response } = await api.POST('/api/v1/scenarios', {
        body: { name, description: description.trim() === '' ? undefined : description, script },
      })
      if (!data) fail(response, error, `Could not create the scenario (HTTP ${response.status}).`)
      return data
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SCENARIOS_KEY }),
  })
}
