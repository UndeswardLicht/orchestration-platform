import { AppShell, Burger, Group, NavLink, Title } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { NavLink as RouterNavLink, Outlet } from 'react-router'

const NAV_ITEMS = [
  { to: '/scenarios', label: 'Scenarios' },
  { to: '/runs', label: 'Runs' },
  { to: '/workers', label: 'Workers' },
]

/** Responsive shell: navbar collapses behind a burger below the `sm` breakpoint (US-25, NFR-08). */
export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure()

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
