import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client.ts'
import { ApiError, isProblem } from '../api/problem.ts'
import type { components } from '../api/schema'

export type WorkerSummary = components['schemas']['WorkerSummary']
export type WorkerStatus = WorkerSummary['status']

export const WORKERS_KEY = ['workers'] as const

/** Heartbeats come every 2 s, so the page asks as often while it is visible (US-06: ONLINE within 10 s). */
export const WORKERS_REFRESH_MS = 2000

export function useWorkers() {
  return useQuery({
    queryKey: WORKERS_KEY,
    queryFn: async () => {
      const { data, error, response } = await api.GET('/api/v1/workers')
      if (!data) {
        throw new ApiError(
          response.status,
          isProblem(error) ? error : undefined,
          `Could not load the workers (HTTP ${response.status}).`,
        )
      }
      return data
    },
    refetchInterval: WORKERS_REFRESH_MS,
    retry: false,
  })
}
