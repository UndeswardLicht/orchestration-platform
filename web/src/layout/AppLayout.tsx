import { AppShell, Badge, Burger, Button, Group, NavLink, Text, Title } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { NavLink as RouterNavLink, Outlet } from 'react-router'
import { useCurrentUser, useSignOut } from '../auth/session.ts'

const NAV_ITEMS = [
  { to: '/scenarios', label: 'Scenarios' },
  { to: '/runs', label: 'Runs' },
  { to: '/workers', label: 'Workers' },
]

/**
 * Responsive shell: navbar collapses behind a burger below the `sm` breakpoint (US-25, NFR-08).
 * Shown only to signed-in users (`RequireAuth`); the header names the user and role.
 */
export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure()
  const { data: user } = useCurrentUser()
  const signOut = useSignOut()

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 220, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md">
          <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Toggle navigation" />
          <Title order={1} size="h4">
            LTP
          </Title>
          <Group gap="sm" ml="auto" wrap="nowrap">
            {user && (
              <Group gap={6} wrap="nowrap" visibleFrom="xs">
                <Text size="sm" fw={500} truncate maw={160}>
                  {user.login}
                </Text>
                <Badge variant="light" size="sm">
                  {user.role}
                </Badge>
              </Group>
            )}
            <Button variant="default" size="xs" onClick={() => signOut.mutate()} loading={signOut.isPending}>
              Sign out
            </Button>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs" aria-label="Main navigation">
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} component={RouterNavLink} to={item.to} label={item.label} onClick={close} />
        ))}
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  )
}
