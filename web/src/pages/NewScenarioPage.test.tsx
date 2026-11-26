import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { MAX_SCRIPT_BYTES, type ScenarioSummary } from '../scenarios/api.ts'
import { SAMPLE_SCRIPT } from '../scenarios/sampleScript.ts'
import { CSRF_TOKEN, DEVELOPER, json, problem, signedInAs, VIEWER } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'
import { CHECKOUT, CHECKOUT_SCRIPT, editorText } from '../test/scenarios.ts'

/** A Control Service that keeps created scenarios, so the list shows them afterwards. */
function fakeScenarioStore(answer?: (body: Record<string, unknown>) => Response | undefined) {
  const saved: ScenarioSummary[] = []
  const api = signedInAs(DEVELOPER, {
    'GET /api/v1/scenarios': () => json(200, saved),
    'POST /api/v1/scenarios': async (request) => {
      const body = (await request.json()) as Record<string, unknown>
      const custom = answer?.(body)
      if (custom) return custom
      const created = { ...CHECKOUT, id: 40 + saved.length, name: String(body.name), description: body.description }
      saved.push(created as ScenarioSummary)
      return json(201, created)
    },
  })
  return { api, saved }
}

function fileInput(): HTMLInputElement {
  return document.querySelector('input[type="file"]') as HTMLInputElement
}

describe('NewScenarioPage', () => {
  // US-01 acceptance criterion: submit a script with a name → version 1 is saved and shown in the list
  it('creates a scenario from the sample script and shows it in the list', async () => {
    const { api } = fakeScenarioStore()
    const { router } = renderRoute('/scenarios/new')
    const user = userEvent.setup()

    await user.type(await screen.findByRole('textbox', { name: /Name/ }), 'Checkout API')
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Browse and pay')
    await user.click(screen.getByRole('button', { name: 'Use sample script' }))
    expect(editorText()).toContain("import http from 'k6/http';")
    await user.click(screen.getByRole('button', { name: 'Create scenario' }))

    expect(await screen.findByRole('link', { name: 'Checkout API' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/scenarios')
    expect(screen.getByText('"Checkout API" is saved as version 1.')).toBeInTheDocument()

    const [post] = api.callsTo('POST /api/v1/scenarios')
    expect(post.headers.get('X-XSRF-TOKEN')).toBe(CSRF_TOKEN)
    expect(await post.json()).toEqual({ name: 'Checkout API', description: 'Browse and pay', script: SAMPLE_SCRIPT })
  })

  it('reads an uploaded .js file into the editor and names the scenario after it', async () => {
    const { api } = fakeScenarioStore()
    renderRoute('/scenarios/new')
    const user = userEvent.setup()
    await screen.findByRole('button', { name: 'Upload .js file' })

    await user.upload(fileInput(), new File([CHECKOUT_SCRIPT], 'checkout.js', { type: 'text/javascript' }))

    await waitFor(() => expect(editorText()).toContain("http.get(__ENV.BASE_URL + '/checkout');"))
    expect(screen.getByRole('textbox', { name: /Name/ })).toHaveValue('checkout')
    await user.click(screen.getByRole('button', { name: 'Create scenario' }))

    await screen.findByRole('link', { name: 'checkout' })
    const [post] = api.callsTo('POST /api/v1/scenarios')
    // Sent exactly as in the file, without a description
    expect(await post.json()).toEqual({ name: 'checkout', script: CHECKOUT_SCRIPT })
  })

  it('refuses a file larger than 1 MB before sending anything', async () => {
    const { api } = fakeScenarioStore()
    renderRoute('/scenarios/new')
    await screen.findByRole('button', { name: 'Upload .js file' })

    await userEvent.upload(fileInput(), new File(['x'.repeat(MAX_SCRIPT_BYTES + 1)], 'huge.js'))

    expect(await screen.findByText('huge.js is larger than 1 MB.')).toBeInTheDocument()
    expect(editorText()).toBe('')
    expect(api.callsTo('POST /api/v1/scenarios')).toHaveLength(0)
  })

  it('asks for a name and a script before sending', async () => {
    const { api } = fakeScenarioStore()
    renderRoute('/scenarios/new')

    await userEvent.click(await screen.findByRole('button', { name: 'Create scenario' }))

    expect(screen.getByText('Enter a name.')).toBeInTheDocument()
    expect(screen.getByText('Paste, upload or write a k6 script.')).toBeInTheDocument()
    expect(api.callsTo('POST /api/v1/scenarios')).toHaveLength(0)
  })

  it('shows a taken name next to the name field', async () => {
    fakeScenarioStore(() => problem(409, "A scenario named 'Checkout API' already exists"))
    const { router } = renderRoute('/scenarios/new')
    const user = userEvent.setup()

    await user.type(await screen.findByRole('textbox', { name: /Name/ }), 'Checkout API')
    await user.click(screen.getByRole('button', { name: 'Use sample script' }))
    await user.click(screen.getByRole('button', { name: 'Create scenario' }))

    expect(await screen.findByText("A scenario named 'Checkout API' already exists")).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Name/ })).toHaveAttribute('aria-invalid', 'true')
    expect(router.state.location.pathname).toBe('/scenarios/new')
  })

  it('shows field errors the server sends', async () => {
    fakeScenarioStore(
      () =>
        new Response(
          JSON.stringify({
            status: 400,
            detail: 'Validation failed',
            errors: [{ field: 'script', message: 'must be at most 1 MB (1048576 bytes in UTF-8)' }],
          }),
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
    )
    renderRoute('/scenarios/new')
    const user = userEvent.setup()

    await user.type(await screen.findByRole('textbox', { name: /Name/ }), 'Big')
    await user.click(screen.getByRole('button', { name: 'Use sample script' }))
    await user.click(screen.getByRole('button', { name: 'Create scenario' }))

    expect(await screen.findByText('Script must be at most 1 MB (1048576 bytes in UTF-8).')).toBeInTheDocument()
  })

  it('tells a viewer that the role cannot create scenarios', async () => {
    signedInAs(VIEWER)
    renderRoute('/scenarios/new')

    expect(await screen.findByText('Your role (VIEWER) can view scenarios but not create them.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create scenario' })).not.toBeInTheDocument()
  })
})
