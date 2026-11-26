import { Badge, Group, NavLink, Paper, Select, Stack, Text, Title } from '@mantine/core'
import { formatDateTime } from '../format.ts'
import type { ScenarioVersionSummary } from './api.ts'

interface VersionPickerProps {
  versions: ScenarioVersionSummary[]
  selected: number
  onSelect: (version: number) => void
  /** Wide screens get a list next to the script, phones a drop-down above it (US-25). */
  layout: 'list' | 'select'
}

/** Pick any version of a scenario to read its script (US-02: "version 1 stays selectable"). */
export function VersionPicker({ versions, selected, onSelect, layout }: VersionPickerProps) {
  const latest = versions[0]?.version

  if (layout === 'select') {
    return (
      <Select
        label="Version"
        value={String(selected)}
        onChange={(value) => value !== null && onSelect(Number(value))}
        data={versions.map((version) => ({
          value: String(version.version),
          label: `v${version.version}${version.version === latest ? ' (latest)' : ''} · ${version.createdBy}, ${formatDateTime(version.createdAt)}`,
        }))}
        allowDeselect={false}
        comboboxProps={{ withinPortal: true }}
      />
    )
  }

  return (
    <Paper withBorder p="xs" component="nav" aria-label="Versions">
      <Stack gap={2}>
        <Title order={3} size="h6" px="xs" py={4}>
          Versions
        </Title>
        {versions.map((version) => (
          <NavLink
            key={version.version}
            component="button"
            type="button"
            active={version.version === selected}
            // Gray: the default indigo highlight leaves the small date text under 4.5:1 contrast (NFR-08)
            color="gray"
            aria-current={version.version === selected ? 'true' : undefined}
            onClick={() => onSelect(version.version)}
            label={
              <Group gap={6} wrap="nowrap">
                <Text size="sm" fw={600}>
                  v{version.version}
                </Text>
                {version.version === latest && (
                  <Badge size="xs" variant="light">
                    latest
                  </Badge>
                )}
              </Group>
            }
            description={`${version.createdBy} · ${formatDateTime(version.createdAt)}`}
          />
        ))}
      </Stack>
    </Paper>
  )
}
