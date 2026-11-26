import { Alert, Anchor, Badge, Box, Button, Center, Group, Loader, Stack, Text, Title } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../api/problem.ts'
import { canChange } from '../auth/roles.ts'
import { useCurrentUser } from '../auth/session.ts'
import { formatDateTime } from '../format.ts'
import {
  MAX_SCRIPT_BYTES,
  useSaveVersion,
  useScenario,
  useScenarioVersion,
  useScenarioVersions,
  utf8Length,
  type ScenarioVersion,
} from '../scenarios/api.ts'
import { ScriptEditor } from '../scenarios/ScriptEditor.tsx'
import { UnsavedChangesGuard } from '../scenarios/UnsavedChangesGuard.tsx'
import { VersionPicker } from '../scenarios/VersionPicker.tsx'

/** Mantine's `sm` breakpoint: version list next to the script from here up, a drop-down below (US-25). */
const WIDE = '(min-width: 48em)'

/**
 * One scenario (US-01) and its immutable versions (US-02): pick any version to read its script, or edit
 * a script and save it as the next version. Saved versions never change.
 */
export function ScenarioPage() {
  const id = Number(useParams().id)
  return Number.isSafeInteger(id) && id > 0 ? <ScenarioView id={id} /> : <NotFound />
}

function NotFound() {
  return (
    <Stack>
      <Title order={2}>Scenario not found</Title>
      <Text>There is no scenario at this address. It may have a different id.</Text>
      <BackLink />
    </Stack>
  )
}

function BackLink() {
  return (
    <Anchor component={Link} to="/scenarios" size="sm">
      ← All scenarios
    </Anchor>
  )
}

/** An edit in progress: the script being written, where it started, and the newest version seen. */
interface Edit {
  draft: string
  /** Version whose script the edit started from (may be an old one, to go back to it). */
  from: ScenarioVersion
  /** Newest version when the edit started; the server refuses the save if a newer one appears. */
  basedOnVersion: number
}

function ScenarioView({ id }: { id: number }) {
  const scenario = useScenario(id)
  const versions = useScenarioVersions(id)
  const { data: user } = useCurrentUser()
  const wide = useMediaQuery(WIDE, false, { getInitialValueInEffect: false })
  const [searchParams, setSearchParams] = useSearchParams()
  const [edit, setEdit] = useState<Edit | null>(null)
  const [scriptError, setScriptError] = useState<string>()
  const [conflict, setConflict] = useState<string>()
  const save = useSaveVersion(id)

  const latest = scenario.data?.latestVersion
  const requested = searchParams.has('version') ? Number(searchParams.get('version')) : undefined
  const selected = requested ?? latest?.version
  const older = useScenarioVersion(id, latest && selected !== latest.version ? selected : undefined)
  const dirty = edit !== null && edit.draft !== edit.from.script

  if (scenario.isPending) {
    return (
      <Center py="xl">
        <Loader aria-label="Loading the scenario" />
      </Center>
    )
  }
  if (scenario.isError) {
    if (scenario.error instanceof ApiError && scenario.error.status === 404) return <NotFound />
    return (
      <Stack>
        <Title order={2}>Scenario</Title>
        <Alert color="red" title="Could not load the scenario">
          {scenario.error.message}
        </Alert>
        <BackLink />
      </Stack>
    )
  }

  const { name, description, createdBy, createdAt, latestVersion } = scenario.data
  const shown: ScenarioVersion | undefined = selected === latestVersion.version ? latestVersion : older.data
  const mayEdit = canChange(user)

  function select(version: number) {
    setSearchParams(version === latestVersion.version ? {} : { version: String(version) })
  }

  function startEdit(from: ScenarioVersion) {
    setEdit({ draft: from.script, from, basedOnVersion: latestVersion.version })
    setScriptError(undefined)
    setConflict(undefined)
  }

  function stopEdit() {
    setEdit(null)
    setScriptError(undefined)
  }

  function loadLatest() {
    stopEdit()
    setConflict(undefined)
    setSearchParams({})
    void scenario.refetch()
    void versions.refetch()
  }

  function saveEdit(current: Edit) {
    if (current.draft.trim() === '') {
      setScriptError('The script cannot be empty.')
      return
    }
    if (utf8Length(current.draft) > MAX_SCRIPT_BYTES) {
      setScriptError('The script is larger than 1 MB.')
      return
    }
    save.mutate(
      { script: current.draft, basedOnVersion: current.basedOnVersion },
      {
        onSuccess: ({ created, version }) => {
          stopEdit()
          setSearchParams({})
          notifications.show(
            created
              ? { color: 'green', title: 'Version saved', message: `Saved as version ${version.version}.` }
              : { title: 'No changes', message: `The script equals version ${version.version}; nothing was saved.` },
          )
        },
        onError: (error) => {
          if (error instanceof ApiError && error.status === 409) {
            setConflict(error.message)
          } else {
            const field = error instanceof ApiError ? error.problem?.errors?.find((e) => e.field === 'script') : undefined
            setScriptError(field ? `Script ${field.message}.` : error.message)
          }
        },
      },
    )
  }

  // Hidden while editing: the edit belongs to one version, and switching would lose it
  const picker =
    edit !== null ? null : versions.data === undefined ? (
      versions.isError ? (
        <Alert color="red" title="Could not load the versions">
          {versions.error.message}
        </Alert>
      ) : (
        <Loader size="sm" aria-label="Loading the versions" />
      )
    ) : (
      <VersionPicker
        versions={versions.data}
        selected={selected ?? latestVersion.version}
        onSelect={select}
        layout={wide ? 'list' : 'select'}
      />
    )

  const unchanged = edit !== null && edit.draft === latestVersion.script

  const scriptPanel = (
    <Stack gap="xs" style={{ flex: 1, minWidth: 0 }}>
      {conflict && (
        <Alert color="red" title="A newer version exists" role="alert">
          <Stack gap="xs" align="flex-start">
            <Text size="sm">{conflict}</Text>
            <Text size="xs">Loading it discards your edit, so copy your changes first if you need them.</Text>
            <Button size="xs" onClick={loadLatest}>
              Load the latest version
            </Button>
          </Stack>
        </Alert>
      )}

      {edit === null && shown !== undefined && shown.version !== latestVersion.version && (
        <Alert color="blue" variant="light">
          <Stack gap="xs" align="flex-start">
            <Text size="sm">
              You are viewing version {shown.version}. The latest is version {latestVersion.version}.
            </Text>
            <Button size="xs" variant="default" onClick={() => select(latestVersion.version)}>
              Show the latest version
            </Button>
          </Stack>
        </Alert>
      )}

      {edit !== null ? (
        <ScriptEditor
          label={`Editing the script (from version ${edit.from.version})`}
          description="Saving creates a new version; earlier versions stay as they are."
          value={edit.draft}
          onChange={(draft) => setEdit({ ...edit, draft })}
          error={scriptError}
          height="480px"
        />
      ) : shown !== undefined ? (
        <ScriptEditor label={`Script of version ${shown.version}`} value={shown.script} readOnly height="480px" />
      ) : older.isError ? (
        <Alert color="red" title={`Version ${selected} cannot be shown`}>
          <Stack gap="xs" align="flex-start">
            <Text size="sm">{older.error.message}</Text>
            <Button size="xs" variant="default" onClick={() => select(latestVersion.version)}>
              Show the latest version
            </Button>
          </Stack>
        </Alert>
      ) : (
        <Center py="xl">
          <Loader aria-label={`Loading version ${selected}`} />
        </Center>
      )}

      {edit !== null ? (
        <Stack gap={4}>
          <Group gap="sm">
            <Button onClick={() => saveEdit(edit)} loading={save.isPending} disabled={unchanged}>
              Save as version {edit.basedOnVersion + 1}
            </Button>
            <Button variant="default" onClick={stopEdit} disabled={save.isPending}>
              Cancel
            </Button>
          </Group>
          {unchanged && (
            <Text size="xs" c="dimmed">
              Same as version {latestVersion.version} (latest): nothing to save yet.
            </Text>
          )}
        </Stack>
      ) : (
        mayEdit &&
        shown !== undefined && (
          <Group>
            <Button variant="default" onClick={() => startEdit(shown)}>
              {shown.version === latestVersion.version ? 'Edit script' : `Edit from version ${shown.version}`}
            </Button>
          </Group>
        )
      )}

      {shown !== undefined && edit === null && (
        <Text size="xs" c="dimmed" style={{ overflowWrap: 'anywhere' }}>
          Version {shown.version} saved by {shown.createdBy} on {formatDateTime(shown.createdAt)} · SHA-256:{' '}
          {shown.sha256}
        </Text>
      )}
    </Stack>
  )

  return (
    <Stack maw={1200}>
      <UnsavedChangesGuard when={dirty} />
      <BackLink />
      <Group gap="sm" wrap="wrap">
        <Title order={2} style={{ overflowWrap: 'anywhere' }}>
          {name}
        </Title>
        <Badge variant="light">
          v{latestVersion.version}
        </Badge>
      </Group>
      {description && <Text style={{ whiteSpace: 'pre-wrap' }}>{description}</Text>}
      <Text size="sm" c="dimmed">
        Created by {createdBy} on {formatDateTime(createdAt)}. {versions.data?.length ?? latestVersion.version}{' '}
        {latestVersion.version === 1 ? 'version' : 'versions'}; the latest was saved by {latestVersion.createdBy} on{' '}
        {formatDateTime(latestVersion.createdAt)}.
      </Text>

      {wide ? (
        <Group align="flex-start" wrap="nowrap" gap="md">
          {scriptPanel}
          {picker && (
            <Box w={260} style={{ flexShrink: 0 }}>
              {picker}
            </Box>
          )}
        </Group>
      ) : (
        <>
          {picker}
          {scriptPanel}
        </>
      )}
    </Stack>
  )
}
