import { Alert, Badge, Center, Code, Group, Loader, Paper, Stack, Table, Text, Title } from '@mantine/core'
import { useInterval, useMediaQuery } from '@mantine/hooks'
import { useState } from 'react'
import { formatAgo, formatDateTime, formatMemory } from '../format.ts'
import { useWorkers, type WorkerStatus, type WorkerSummary } from '../workers/api.ts'

/**
 * Mantine's `md` breakpoint: the six-column table from here up, cards below (US-25). The table never
 * scrolls sideways inside the page: a scrolling region would need keyboard focus (WCAG 2.1.1).
 */
const WIDE = '(min-width: 62em)'

export const START_WORKERS_COMMAND = 'docker compose -f deploy/docker-compose.yml --profile worker up -d'

const STATUS_ORDER: WorkerStatus[] = ['ONLINE', 'BUSY', 'LOST', 'OFFLINE']

/** Dark text on a light badge: every status keeps at least 4.5:1 contrast (NFR-08). */
const STATUS_COLOR: Record<WorkerStatus, string> = {
  ONLINE: 'green.9',
  BUSY: 'blue.9',
  LOST: 'red.9',
  OFFLINE: 'gray',
}

const STATUS_HINT: Record<WorkerStatus, string> = {
  ONLINE: 'Free for a run',
  BUSY: 'Running part of a test',
  LOST: 'No heartbeat for more than 15 s',
  OFFLINE: 'Stopped cleanly',
}

/** Registered workers and their status (US-06). Refreshes every 2 s; every signed-in role may look. */
export function WorkersPage() {
  const workers = useWorkers()
  const wide = useMediaQuery(WIDE, false, { getInitialValueInEffect: false })
  const now = useNow()

  return (
    <Stack>
      <Title order={2}>Workers</Title>

      {workers.isPending && (
        <Center py="xl">
          <Loader aria-label="Loading workers" />
        </Center>
      )}

      {workers.isError && (
        <Alert color={workers.data ? 'yellow' : 'red'} title="Could not load the workers">
          <Text size="sm">
            {workers.error.message}
            {workers.data ? ' The list shows the last known status; it retries every 2 s.' : ' Retrying every 2 s.'}
          </Text>
        </Alert>
      )}

      {workers.data?.length === 0 && <EmptyState />}

      {workers.data && workers.data.length > 0 && (
        <>
          <Capacity workers={workers.data} />
          {wide ? <WorkerTable workers={workers.data} now={now} /> : <WorkerCards workers={workers.data} now={now} />}
        </>
      )}
    </Stack>
  )
}

/** The clock for "8 s ago", ticking every second so a silent worker visibly ages. */
function useNow() {
  const [now, setNow] = useState(() => Date.now())
  useInterval(() => setNow(Date.now()), 1000, { autoInvoke: true })
  return now
}

/** "2 ONLINE · 1 LOST": the capacity at a glance (the story's "so that I know the available capacity"). */
function Capacity({ workers }: { workers: WorkerSummary[] }) {
  const counts = STATUS_ORDER.map((status) => [status, workers.filter((w) => w.status === status).length] as const).filter(
    ([, count]) => count > 0,
  )
  const cpus = workers.filter((w) => w.status === 'ONLINE').reduce((sum, w) => sum + w.cpus, 0)
  return (
    <Text size="sm" aria-live="polite">
      {counts.map(([status, count]) => `${count} ${status}`).join(' · ')}
      {cpus > 0 && ` — ${cpus} ${cpus === 1 ? 'CPU' : 'CPUs'} free for runs`}
    </Text>
  )
}

function StatusBadge({ status }: { status: WorkerStatus }) {
  return (
    <Badge variant="light" color={STATUS_COLOR[status]} title={STATUS_HINT[status]}>
      {status}
    </Badge>
  )
}

function LastHeartbeat({ worker, now }: { worker: WorkerSummary; now: number }) {
  return (
    <time dateTime={worker.lastHeartbeatAt} title={formatDateTime(worker.lastHeartbeatAt)}>
      {formatAgo(worker.lastHeartbeatAt, now)}
    </time>
  )
}

function cpuText(cpus: number) {
  return `${cpus} ${cpus === 1 ? 'CPU' : 'CPUs'}`
}

/** NFR-07: a first-time admin sees how to get workers. */
function EmptyState() {
  return (
    <Paper withBorder p="xl">
      <Stack align="center" gap="sm" ta="center">
        <Text fw={600}>No workers yet</Text>
        <Text size="sm" maw={480}>
          Workers run the load tests. Start two on this machine with Docker Compose; they appear here within seconds:
        </Text>
        <Code block style={{ maxWidth: '100%', whiteSpace: 'pre-wrap', wordBreak: 'break-all', textAlign: 'left' }}>
          {START_WORKERS_COMMAND}
        </Code>
      </Stack>
    </Paper>
  )
}

function WorkerTable({ workers, now }: { workers: WorkerSummary[]; now: number }) {
  return (
    <Table striped verticalSpacing="sm" aria-label="Workers">
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Name</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th>Last heartbeat</Table.Th>
            <Table.Th>Resources</Table.Th>
            <Table.Th>Version</Table.Th>
            <Table.Th>Registered</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {workers.map((worker) => (
            <Table.Tr key={worker.id}>
              <Table.Td fw={600} style={{ wordBreak: 'break-all' }}>
                {worker.name}
              </Table.Td>
              <Table.Td>
                <StatusBadge status={worker.status} />
              </Table.Td>
              <Table.Td>
                <LastHeartbeat worker={worker} now={now} />
              </Table.Td>
              <Table.Td>
                {cpuText(worker.cpus)}, {formatMemory(worker.memMb)}
              </Table.Td>
              <Table.Td>{worker.version}</Table.Td>
              <Table.Td>{formatDateTime(worker.registeredAt)}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
    </Table>
  )
}

function WorkerCards({ workers, now }: { workers: WorkerSummary[]; now: number }) {
  return (
    <Stack gap="sm" component="ul" p={0} m={0} style={{ listStyle: 'none' }} aria-label="Workers">
      {workers.map((worker) => (
        <Paper key={worker.id} withBorder p="md" component="li">
          <Stack gap={4}>
            <Group justify="space-between" wrap="nowrap" gap="xs">
              <Text fw={600} style={{ wordBreak: 'break-all' }}>
                {worker.name}
              </Text>
              <StatusBadge status={worker.status} />
            </Group>
            <Text size="sm">
              Last heartbeat <LastHeartbeat worker={worker} now={now} />
            </Text>
            <Text size="sm">
              {cpuText(worker.cpus)}, {formatMemory(worker.memMb)} · version {worker.version}
            </Text>
          </Stack>
        </Paper>
      ))}
    </Stack>
  )
}
