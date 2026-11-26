import { Alert, Anchor, Badge, Center, Group, Loader, Stack, Text, Title } from '@mantine/core'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/problem.ts'
import { formatDateTime } from '../format.ts'
import { useScenario } from '../scenarios/api.ts'
import { ScriptEditor } from '../scenarios/ScriptEditor.tsx'

/**
 * One scenario with the script of its newest version, read-only (US-01). US-02 adds saving a new
 * version and the list of versions.
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

function ScenarioView({ id }: { id: number }) {
  const scenario = useScenario(id)

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
  return (
    <Stack maw={960}>
      <BackLink />
      <Group gap="sm" wrap="wrap">
        <Title order={2} style={{ overflowWrap: 'anywhere' }}>
          {name}
        </Title>
        <Badge variant="light">v{latestVersion.version}</Badge>
      </Group>
      {description && <Text style={{ whiteSpace: 'pre-wrap' }}>{description}</Text>}
      <Text size="sm" c="dimmed">
        Created by {createdBy} on {formatDateTime(createdAt)}. Version {latestVersion.version} saved by{' '}
        {latestVersion.createdBy} on {formatDateTime(latestVersion.createdAt)}.
      </Text>
      <ScriptEditor label={`Script of version ${latestVersion.version}`} value={latestVersion.script} readOnly height="480px" />
      <Text size="xs" c="dimmed" style={{ overflowWrap: 'anywhere' }}>
        SHA-256: {latestVersion.sha256}
      </Text>
    </Stack>
  )
}
