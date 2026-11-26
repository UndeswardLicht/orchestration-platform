import { Button, Group, Modal, Text } from '@mantine/core'
import { useEffect } from 'react'
import { useBlocker } from 'react-router'

/**
 * Asks before leaving a page with unsaved edits: inside the app (links, back button) with a dialog,
 * and when closing or reloading the tab with the browser's own prompt.
 */
export function UnsavedChangesGuard({ when }: { when: boolean }) {
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => when && currentLocation.pathname !== nextLocation.pathname,
  )

  useEffect(() => {
    if (!when) return undefined
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [when])

  return (
    <Modal
      opened={blocker.state === 'blocked'}
      onClose={() => blocker.reset?.()}
      title="Discard your changes?"
      centered
    >
      <Text size="sm">The script has changes that are not saved as a version yet.</Text>
      <Group justify="flex-end" mt="md">
        <Button variant="default" onClick={() => blocker.reset?.()}>
          Keep editing
        </Button>
        <Button color="red" onClick={() => blocker.proceed?.()}>
          Discard changes
        </Button>
      </Group>
    </Modal>
  )
}
