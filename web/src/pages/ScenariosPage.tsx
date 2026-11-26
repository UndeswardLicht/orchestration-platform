import { Alert, Anchor, Badge, Button, Center, Group, Loader, Paper, Stack, Table, Text, Title } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { Link } from 'react-router'
import { canChange } from '../auth/roles.ts'
import { useCurrentUser } from '../auth/session.ts'
import { formatDateTime } from '../format.ts'
import { useScenarios, type ScenarioSummary } from '../scenarios/api.ts'

/** Mantine's `sm` breakpoint: tables from here up, cards below (US-25). */
const WIDE = '(min-width: 48em)'

/** The scenario list (US-01). Developers and admins can create scenarios from here. */
export function ScenariosPage() {
  const { data: user } = useCurrentUser()
  const scenarios = useScenarios()
  const wide = useMediaQuery(WIDE, false, { getInitialValueInEffect: false })
  const mayCreate = canChange(user)

  return (
    <Stack>
      <Group justify="space-between" wrap="wrap">
        <Title order={2}>Scenarios</Title>
        {mayCreate && scenarios.data && scenarios.data.length > 0 && (
          <Button component={Link} to="/scenarios/new">
            New scenario
          </Button>
        )}
      </Group>

      {scenarios.isPending && (
        <Center py="xl">
          <Loader aria-label="Loading scenarios" />
        </Center>
      )}

      {scenarios.isError && (
        <Alert color="red" title="Could not load the scenarios">
          <Stack gap="xs" align="flex-start">
            <Text size="sm">{scenarios.error.message}</Text>
            <Button size="xs" variant="default" onClick={() => void scenarios.refetch()}>
              Try again
            </Button>
          </Stack>
        </Alert>
      )}

      {scenarios.data?.length === 0 && <EmptyState mayCreate={mayCreate} />}

      {scenarios.data && scenarios.data.length > 0 &&
        (wide ? <ScenarioTable scenarios={scenarios.data} /> : <ScenarioCards scenarios={scenarios.data} />)}
    </Stack>
  )
}

/** NFR-07: a first-time user sees what to do next. */
function EmptyState({ mayCreate }: { mayCreate: boolean }) {
  return (
    <Paper withBorder p="xl">
      <Stack align="center" gap="sm" ta="center">
        <Text fw={600}>No scenarios yet</Text>
        <Text size="sm" c="dimmed" maw={420}>
          A scenario is a k6 script with saved versions. Paste a script, upload a .js file or start from the sample.
        </Text>
        {mayCreate ? (
          <Button component={Link} to="/scenarios/new">
            Create your first scenario
          </Button>
        ) : (
          <Text size="sm">Ask a developer or an admin to create one.</Text>
        )}
      </Stack>
    </Paper>
  )
}

function ScenarioLink({ scenario }: { scenario: ScenarioSummary }) {
  return (
    <Anchor component={Link} to={`/scenarios/${scenario.id}`} fw={600}>
      {scenario.name}
    </Anchor>
  )
}

function ScenarioTable({ scenarios }: { scenarios: ScenarioSummary[] }) {
  return (
    <Table striped highlightOnHover verticalSpacing="sm" aria-label="Scenarios">
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Name</Table.Th>
          <Table.Th>Description</Table.Th>
          <Table.Th>Version</Table.Th>
          <Table.Th>Created by</Table.Th>
          <Table.Th>Updated</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {scenarios.map((scenario) => (
          <Table.Tr key={scenario.id}>
            <Table.Td>
              <ScenarioLink scenario={scenario} />
            </Table.Td>
            <Table.Td maw={360}>
              <Text size="sm" lineClamp={2}>
                {scenario.description}
              </Text>
            </Table.Td>
            <Table.Td>
              <Badge variant="light">v{scenario.latestVersion}</Badge>
            </Table.Td>
            <Table.Td>{scenario.createdBy}</Table.Td>
            <Table.Td>{formatDateTime(scenario.updatedAt)}</Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}

function ScenarioCards({ scenarios }: { scenarios: ScenarioSummary[] }) {
  return (
    <Stack gap="sm" component="ul" p={0} m={0} style={{ listStyle: 'none' }} aria-label="Scenarios">
      {scenarios.map((scenario) => (
        <Paper key={scenario.id} withBorder p="md" component="li">
          <Stack gap={4}>
            <Group justify="space-between" wrap="nowrap" gap="xs">
              <ScenarioLink scenario={scenario} />
              <Badge variant="light">v{scenario.latestVersion}</Badge>
            </Group>
            {scenario.description && (
              <Text size="sm" lineClamp={2}>
                {scenario.description}
              </Text>
            )}
            <Text size="xs" c="dimmed">
              {scenario.createdBy} · updated {formatDateTime(scenario.updatedAt)}
            </Text>
          </Stack>
        </Paper>
      ))}
    </Stack>
  )
}
