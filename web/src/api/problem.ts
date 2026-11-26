/** RFC 9457 error body of the Control Service (`ProblemDetail`); `errors` names the wrong fields. */
export interface Problem {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: { field: string; message: string }[]
}

/** A failed API call, with the server's explanation when it sent one. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | undefined

  constructor(status: number, problem: Problem | undefined, fallback: string) {
    super(problem?.detail ?? fallback)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export function isProblem(value: unknown): value is Problem {
  return typeof value === 'object' && value !== null && ('detail' in value || 'status' in value)
}
