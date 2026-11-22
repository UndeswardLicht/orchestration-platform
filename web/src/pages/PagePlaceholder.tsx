import { Stack, Text, Title } from '@mantine/core'

export function PagePlaceholder({ title, description }: { title: string; description: string }) {
  return (
    <Stack gap="xs">
      <Title order={2}>{title}</Title>
      <Text c="dimmed">{description}</Text>
    </Stack>
  )
}
