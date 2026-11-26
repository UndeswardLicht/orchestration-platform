import type { CurrentUser } from './session.ts'

/**
 * May create and edit scenarios, rules and runs (US-23: DEVELOPER and ADMIN). The UI uses it only to
 * hide actions; the Control Service checks the role on every change anyway.
 */
export function canChange(user: CurrentUser | null | undefined): boolean {
  return user?.role === 'ADMIN' || user?.role === 'DEVELOPER'
}
