import { EditorView } from '@uiw/react-codemirror'
import type { ScenarioDetails, ScenarioSummary, ScenarioVersion, ScenarioVersionSummary } from '../scenarios/api.ts'

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

export const CHECKOUT_V1: ScenarioVersion = CHECKOUT_DETAILS.latestVersion

export const CHECKOUT_V2_SCRIPT = CHECKOUT_SCRIPT.replace("'/checkout'", "'/cart'")

export const CHECKOUT_V2: ScenarioVersion = {
  id: 32,
  version: 2,
  script: CHECKOUT_V2_SCRIPT,
  sha256: 'ef8e567b99d3f4406f705cf9b59d5abc716d50c7abc4a0a7b603bbafeec1c32e',
  createdBy: 'vera',
  createdAt: '2026-11-27T10:15:00Z',
}

/** Checkout with two versions: the scenario, its version list and each version's own answer. */
export const CHECKOUT_WITH_V2 = {
  details: { ...CHECKOUT_DETAILS, latestVersion: CHECKOUT_V2 } satisfies ScenarioDetails,
  versions: [CHECKOUT_V2, CHECKOUT_V1].map(({ script: _script, ...summary }) => summary) satisfies ScenarioVersionSummary[],
}

/** Replaces the text of a CodeMirror editor the way typing would (jsdom cannot type into it). */
export function setEditorText(text: string, index = 0) {
  const element = document.querySelectorAll<HTMLElement>('.cm-editor')[index]
  const view = element ? EditorView.findFromDOM(element) : null
  if (!view) throw new Error('No CodeMirror editor on the page')
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } })
}
