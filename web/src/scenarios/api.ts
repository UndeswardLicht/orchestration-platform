import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.ts'
import { ApiError, isProblem } from '../api/problem.ts'
import type { components } from '../api/schema'

export type ScenarioSummary = components['schemas']['ScenarioSummary']
export type ScenarioDetails = components['schemas']['ScenarioDetails']
export type ScenarioVersion = components['schemas']['ScenarioVersionDetails']
export type ScenarioVersionSummary = components['schemas']['ScenarioVersionSummary']
export type SavedVersion = components['schemas']['SavedVersion']

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

/** Every version of a scenario, newest first, without scripts (US-02). */
export function useScenarioVersions(id: number) {
  return useQuery({
    queryKey: [...SCENARIOS_KEY, id, 'versions'],
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/v1/scenarios/{id}/versions', {
        params: { path: { id } },
      })
      if (!data) fail(response, error, `Could not load the versions (HTTP ${response.status}).`)
      return data
    },
    retry: retryOnce,
  })
}

/** One version with its script. Versions never change, so a loaded one is never fetched again. */
export function useScenarioVersion(id: number, version: number | undefined) {
  return useQuery({
    queryKey: [...SCENARIOS_KEY, id, 'versions', version],
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/v1/scenarios/{id}/versions/{version}', {
        params: { path: { id, version: version! } },
      })
      if (!data) fail(response, error, `Could not load version ${version} (HTTP ${response.status}).`)
      return data
    },
    enabled: version !== undefined,
    staleTime: Infinity,
    retry: retryOnce,
  })
}

export interface VersionToSave {
  script: string
  /** The newest version the user had when starting to edit; the server refuses the save if it is outdated. */
  basedOnVersion: number
}

export function useSaveVersion(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ script, basedOnVersion }: VersionToSave): Promise<SavedVersion> => {
      const { data, error, response } = await api.POST('/api/v1/scenarios/{id}/versions', {
        params: { path: { id } },
        body: { script, basedOnVersion },
      })
      if (!data) fail(response, error, `Could not save the script (HTTP ${response.status}).`)
      return data
    },
    // The scenario, its version list and the scenario list all show the newest version
    onSettled: () => queryClient.invalidateQueries({ queryKey: SCENARIOS_KEY }),
  })
}
