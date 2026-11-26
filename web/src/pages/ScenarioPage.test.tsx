import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { CSRF_TOKEN, DEVELOPER, json, problem, signedInAs, VIEWER, type FakeRoutes } from '../test/fakeApi.ts'
import { renderRoute } from '../test/render.tsx'
import {
  CHECKOUT_DETAILS,
  CHECKOUT_SCRIPT,
  CHECKOUT_V1,
  CHECKOUT_V2,
  CHECKOUT_V2_SCRIPT,
  CHECKOUT_WITH_V2,
  editorText,
  setEditorText,
} from '../test/scenarios.ts'

const narrowScreen = window.matchMedia

/** Pretends the screen is at least 768 px wide (the version list instead of the drop-down). */
function wideScreen() {
  window.matchMedia = (query: string) => ({ ...narrowScreen(query), matches: true })
}

afterEach(() => {
  window.matchMedia = narrowScreen
})

/** Checkout with only version 1. */
const ONE_VERSION: FakeRoutes = {
  'GET /api/v1/scenarios/12': () => json(200, CHECKOUT_DETAILS),
  'GET /api/v1/scenarios/12/versions': () =>
    json(200, [{ id: 31, version: 1, sha256: CHECKOUT_V1.sha256, createdBy: 'dana', createdAt: CHECKOUT_V1.createdAt }]),
}

/** Checkout with versions 1 and 2. */
const TWO_VERSIONS: FakeRoutes = {
  'GET /api/v1/scenarios/12': () => json(200, CHECKOUT_WITH_V2.details),
  'GET /api/v1/scenarios/12/versions': () => json(200, CHECKOUT_WITH_V2.versions),
  'GET /api/v1/scenarios/12/versions/1': () => json(200, CHECKOUT_V1),
}

describe('ScenarioPage', () => {
  it('shows the scenario and the script of its latest version, read-only', async () => {
    signedInAs(VIEWER, ONE_VERSION)
    renderRoute('/scenarios/12')

    expect(await screen.findByRole('heading', { name: 'Checkout API' })).toBeInTheDocument()
    expect(screen.getByText('Browse, add to cart, pay')).toBeInTheDocument()
    expect(editorText()).toContain("http.get(__ENV.BASE_URL + '/checkout');")
    const editor = document.querySelector('.cm-content')!
    expect(editor).toHaveAttribute('contenteditable', 'false')
    expect(editor).toHaveAttribute('aria-readonly', 'true')
  })

  // NFR-08: a long read-only script must be reachable by keyboard to scroll it
  it('lets keyboard users reach the read-only script with Tab', async () => {
    signedInAs(VIEWER, ONE_VERSION)
    renderRoute('/scenarios/12')
    const script = await screen.findByRole('textbox', { name: 'Script of version 1' })

    const user = userEvent.setup()
    for (let presses = 0; presses < 20 && document.activeElement !== script; presses++) await user.tab()
    expect(script).toHaveFocus()
  })

  // US-02 acceptance criterion, in the UI: version 1 stays selectable after version 2 exists
  it('lets the user pick version 1 after version 2 was saved', async () => {
    signedInAs(VIEWER, TWO_VERSIONS)
    const { router } = renderRoute('/scenarios/12')
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: 'Checkout API' })
    await waitFor(() => expect(editorText()).toContain("'/cart'"))

    await user.click(await screen.findByRole('combobox', { name: 'Version' }))
    // jsdom never finishes the drop-down's open transition, so its options count as hidden there;
    // the Playwright test checks the real drop-down
    await user.click(await screen.findByRole('option', { name: /^v1 · dana/, hidden: true }))

    await waitFor(() => expect(editorText()).toContain("'/checkout'"))
    expect(router.state.location.search).toBe('?version=1')
    expect(screen.getByText(/You are viewing version 1\. The latest is version 2\./)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Show the latest version' }))
    await waitFor(() => expect(editorText()).toContain("'/cart'"))
    expect(router.state.location.search).toBe('')
  })

  it('lists the versions next to the script on wide screens', async () => {
    wideScreen()
    signedInAs(VIEWER, TWO_VERSIONS)
    const { router } = renderRoute('/scenarios/12')

    const list = await screen.findByRole('navigation', { name: 'Versions' })
    expect(within(list).getByText('latest')).toBeInTheDocument()
    await userEvent.click(within(list).getByRole('button', { name: /v1/ }))

    await waitFor(() => expect(editorText()).toContain("'/checkout'"))
    expect(router.state.location.search).toBe('?version=1')
    expect(within(list).getByRole('button', { name: /v1/ })).toHaveAttribute('aria-current', 'true')
  })

  it('opens a version from a shared link', async () => {
    signedInAs(VIEWER, TWO_VERSIONS)
    renderRoute('/scenarios/12?version=1')

    await waitFor(() => expect(editorText()).toContain("'/checkout'"))
    await waitFor(() =>
      expect((screen.getByRole('combobox', { name: 'Version' }) as HTMLInputElement).value).toMatch(/^v1 · dana/),
    )
  })

  it('says so when the linked version does not exist', async () => {
    signedInAs(VIEWER, { ...ONE_VERSION, 'GET /api/v1/scenarios/12/versions/9': () => problem(404, 'Scenario 12 has no version 9') })
    renderRoute('/scenarios/12?version=9')

    expect(await screen.findByText('Version 9 cannot be shown')).toBeInTheDocument()
    expect(screen.getByText('Scenario 12 has no version 9')).toBeInTheDocument()
  })

  it('saves an edited script as the next version', async () => {
    const api = signedInAs(DEVELOPER, {
      ...ONE_VERSION,
      'POST /api/v1/scenarios/12/versions': () => json(201, { created: true, version: CHECKOUT_V2 }),
    })
    renderRoute('/scenarios/12')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit script' }))
    const save = screen.getByRole('button', { name: 'Save as version 2' })
    expect(save).toBeDisabled()
    expect(screen.getByText('Same as version 1 (latest): nothing to save yet.')).toBeInTheDocument()

    setEditorText(CHECKOUT_V2_SCRIPT)
    await waitFor(() => expect(save).toBeEnabled())
    await user.click(save)

    expect(await screen.findByText('Saved as version 2.')).toBeInTheDocument()
    const [post] = api.callsTo('POST /api/v1/scenarios/12/versions')
    expect(post.headers.get('X-XSRF-TOKEN')).toBe(CSRF_TOKEN)
    expect(await post.json()).toEqual({ script: CHECKOUT_V2_SCRIPT, basedOnVersion: 1 })
    // Back to reading, and the page reloaded the scenario to show the new version
    expect(screen.queryByRole('button', { name: /Save as version/ })).not.toBeInTheDocument()
    expect(api.callsTo('GET /api/v1/scenarios/12').length).toBeGreaterThan(1)
  })

  it('goes back to an old script by editing from that version', async () => {
    const api = signedInAs(DEVELOPER, {
      ...TWO_VERSIONS,
      'POST /api/v1/scenarios/12/versions': () =>
        json(201, { created: true, version: { ...CHECKOUT_V1, id: 33, version: 3 } }),
    })
    renderRoute('/scenarios/12?version=1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit from version 1' }))
    // Different from the latest (v2), so it can be saved at once
    await user.click(screen.getByRole('button', { name: 'Save as version 3' }))

    expect(await screen.findByText('Saved as version 3.')).toBeInTheDocument()
    const [post] = api.callsTo('POST /api/v1/scenarios/12/versions')
    expect(await post.json()).toEqual({ script: CHECKOUT_SCRIPT, basedOnVersion: 2 })
  })

  it('reports when the server found nothing to save', async () => {
    signedInAs(DEVELOPER, {
      ...ONE_VERSION,
      'POST /api/v1/scenarios/12/versions': () => json(200, { created: false, version: CHECKOUT_V1 }),
    })
    renderRoute('/scenarios/12')

    await userEvent.click(await screen.findByRole('button', { name: 'Edit script' }))
    setEditorText(CHECKOUT_SCRIPT + ' ')
    await userEvent.click(await screen.findByRole('button', { name: 'Save as version 2' }))

    expect(await screen.findByText('The script equals version 1; nothing was saved.')).toBeInTheDocument()
  })

  it('explains a conflict when a colleague saved a newer version meanwhile', async () => {
    const detail = 'Your changes are based on version 1, but version 2 is the latest. Load it and apply your changes again.'
    let latest = CHECKOUT_DETAILS
    signedInAs(DEVELOPER, {
      'GET /api/v1/scenarios/12': () => json(200, latest),
      'GET /api/v1/scenarios/12/versions': () => json(200, CHECKOUT_WITH_V2.versions),
      'POST /api/v1/scenarios/12/versions': () => {
        latest = CHECKOUT_WITH_V2.details
        return problem(409, detail)
      },
    })
    renderRoute('/scenarios/12')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit script' }))
    setEditorText('// my change\n' + CHECKOUT_SCRIPT)
    await user.click(await screen.findByRole('button', { name: 'Save as version 2' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('A newer version exists')
    expect(alert).toHaveTextContent(detail)

    await user.click(within(alert).getByRole('button', { name: 'Load the latest version' }))
    await waitFor(() => expect(editorText()).toContain("'/cart'"))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit script' })).toBeInTheDocument()
  })

  it('refuses an empty script before sending', async () => {
    const api = signedInAs(DEVELOPER, ONE_VERSION)
    renderRoute('/scenarios/12')

    await userEvent.click(await screen.findByRole('button', { name: 'Edit script' }))
    setEditorText('   \n')
    await userEvent.click(await screen.findByRole('button', { name: 'Save as version 2' }))

    expect(await screen.findByText('The script cannot be empty.')).toBeInTheDocument()
    expect(api.callsTo('POST /api/v1/scenarios/12/versions')).toHaveLength(0)
  })

  it('offers no editing to a viewer', async () => {
    signedInAs(VIEWER, ONE_VERSION)
    renderRoute('/scenarios/12')

    await screen.findByRole('heading', { name: 'Checkout API' })
    await waitFor(() => expect(editorText()).toContain('/checkout'))
    expect(screen.queryByRole('button', { name: /Edit/ })).not.toBeInTheDocument()
  })

  it('asks before leaving the page with unsaved changes', async () => {
    signedInAs(DEVELOPER, { ...ONE_VERSION, 'GET /api/v1/scenarios': () => json(200, []) })
    const { router } = renderRoute('/scenarios/12')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit script' }))
    setEditorText('// unsaved\n')
    await user.click(screen.getByRole('link', { name: '← All scenarios' }))

    const dialog = await screen.findByRole('dialog', { name: 'Discard your changes?' })
    await user.click(within(dialog).getByRole('button', { name: 'Keep editing' }))
    expect(router.state.location.pathname).toBe('/scenarios/12')

    await user.click(screen.getByRole('link', { name: '← All scenarios' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Discard changes' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/scenarios'))
  })

  it('says so when the scenario does not exist', async () => {
    signedInAs(VIEWER, {
      'GET /api/v1/scenarios/99': () => problem(404, 'No scenario with id 99'),
      'GET /api/v1/scenarios/99/versions': () => problem(404, 'No scenario with id 99'),
    })
    renderRoute('/scenarios/99')

    expect(await screen.findByRole('heading', { name: 'Scenario not found' })).toBeInTheDocument()
  })

  it('treats an address that is not an id as not found, without calling the server', async () => {
    const api = signedInAs(VIEWER)
    renderRoute('/scenarios/abc')

    expect(await screen.findByRole('heading', { name: 'Scenario not found' })).toBeInTheDocument()
    expect(api.calls.filter((call) => call.url.includes('/api/v1/scenarios'))).toHaveLength(0)
  })
})
