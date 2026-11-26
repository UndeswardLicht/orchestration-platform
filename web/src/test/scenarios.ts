import type { ScenarioDetails, ScenarioSummary } from '../scenarios/api.ts'

export const CHECKOUT: ScenarioSummary = {
  id: 12,
  name: 'Checkout API',
  description: 'Browse, add to cart, pay',
  latestVersion: 1,
  createdBy: 'dana',
  createdAt: '2026-11-26T09:30:00Z',
  updatedAt: '2026-11-26T09:30:00Z',
}

export const CHECKOUT_SCRIPT = "import http from 'k6/http';\nexport default function () {\n  http.get(__ENV.BASE_URL + '/checkout');\n}\n"

export const CHECKOUT_DETAILS: ScenarioDetails = {
  id: 12,
  name: 'Checkout API',
  description: 'Browse, add to cart, pay',
  createdBy: 'dana',
  createdAt: '2026-11-26T09:30:00Z',
  latestVersion: {
    id: 31,
    version: 1,
    script: CHECKOUT_SCRIPT,
    sha256: 'b5af96aa2912f2e473d65d41c193309718c093667197a98c99048d97b482a240',
    createdBy: 'dana',
    createdAt: '2026-11-26T09:30:00Z',
  },
}

/** Text of a CodeMirror editor; its lines are separate elements, so this has no line breaks. */
export function editorText(index = 0): string {
  return document.querySelectorAll('.cm-content')[index]?.textContent ?? ''
}
