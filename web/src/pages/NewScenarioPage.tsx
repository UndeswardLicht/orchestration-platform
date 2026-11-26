import { Alert, Anchor, Button, FileButton, Group, Stack, Text, TextInput, Textarea, Title } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/problem.ts'
import { canChange } from '../auth/roles.ts'
import { useCurrentUser } from '../auth/session.ts'
import {
  MAX_DESCRIPTION_LENGTH,
  MAX_NAME_LENGTH,
  MAX_SCRIPT_BYTES,
  useCreateScenario,
  utf8Length,
} from '../scenarios/api.ts'
import { SAMPLE_SCRIPT } from '../scenarios/sampleScript.ts'
import { ScriptEditor } from '../scenarios/ScriptEditor.tsx'

type Field = 'name' | 'description' | 'script'
type FieldErrors = Partial<Record<Field, string>>

const TOO_LARGE = 'The script is larger than 1 MB.'

function validate(name: string, description: string, script: string): FieldErrors {
  const errors: FieldErrors = {}
  if (name.trim() === '') errors.name = 'Enter a name.'
  else if (name.trim().length > MAX_NAME_LENGTH) errors.name = `At most ${MAX_NAME_LENGTH} characters.`
  if (description.trim().length > MAX_DESCRIPTION_LENGTH) {
    errors.description = `At most ${MAX_DESCRIPTION_LENGTH} characters.`
  }
  if (script.trim() === '') errors.script = 'Paste, upload or write a k6 script.'
  else if (utf8Length(script) > MAX_SCRIPT_BYTES) errors.script = TOO_LARGE
  return errors
}

/** Field errors from the server: `errors[]` of a 400, or the name conflict of a 409. */
function serverFieldErrors(error: ApiError): FieldErrors {
  if (error.status === 409) {
    return { name: error.message }
  }
  const errors: FieldErrors = {}
  for (const { field, message } of error.problem?.errors ?? []) {
    if (field === 'name' || field === 'description' || field === 'script') {
      errors[field] = `${field[0].toUpperCase()}${field.slice(1)} ${message}.`
    }
  }
  return errors
}

/** File name without the extension, as a starting name for an uploaded script. */
function nameFromFile(fileName: string): string {
  return fileName.replace(/\.(m?js)$/i, '').slice(0, MAX_NAME_LENGTH)
}

/** Create a scenario from a k6 script (US-01): paste, upload a file, or start from the sample. */
export function NewScenarioPage() {
  const { data: user } = useCurrentUser()
  const navigate = useNavigate()
  const create = useCreateScenario()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [script, setScript] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})

  if (!canChange(user)) {
    return (
      <Stack>
        <Title order={2}>New scenario</Title>
        <Alert color="yellow">Your role ({user?.role}) can view scenarios but not create them.</Alert>
        <Anchor component={Link} to="/scenarios">
          Back to scenarios
        </Anchor>
      </Stack>
    )
  }

  async function handleFile(file: File | null) {
    if (!file) return
    if (file.size > MAX_SCRIPT_BYTES) {
      setErrors((current) => ({ ...current, script: `${file.name} is larger than 1 MB.` }))
      return
    }
    setScript(await file.text())
    if (name.trim() === '') setName(nameFromFile(file.name))
    setErrors((current) => ({ ...current, script: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validate(name, description, script)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    create.mutate(
      { name: name.trim(), description, script },
      {
        onSuccess: (created) => {
          notifications.show({
            color: 'green',
            title: 'Scenario created',
            message: `"${created.name}" is saved as version ${created.latestVersion}.`,
          })
          void navigate('/scenarios')
        },
        onError: (error) => {
          if (error instanceof ApiError) setErrors(serverFieldErrors(error))
        },
      },
    )
  }

  const formError =
    create.error && !(create.error instanceof ApiError && Object.keys(serverFieldErrors(create.error)).length > 0)
      ? create.error.message
      : undefined

  return (
    <form onSubmit={handleSubmit} noValidate>
      <Stack maw={960}>
        <Title order={2}>New scenario</Title>

        {formError && (
          <Alert color="red" role="alert">
            {formError}
          </Alert>
        )}

        <TextInput
          label="Name"
          description="Unique; used in lists, run history and CI"
          value={name}
          onChange={(event) => setName(event.currentTarget.value)}
          error={errors.name}
          maxLength={MAX_NAME_LENGTH + 50}
          withAsterisk
        />
        <Textarea
          label="Description"
          value={description}
          onChange={(event) => setDescription(event.currentTarget.value)}
          error={errors.description}
          autosize
          minRows={2}
          maxRows={6}
        />

        <Stack gap={6}>
          <ScriptEditor
            label="k6 script"
            description="JavaScript, at most 1 MB. The run's target URL is available as __ENV.BASE_URL."
            value={script}
            onChange={setScript}
            error={errors.script}
          />
          <Group gap="xs">
            <FileButton onChange={(file) => void handleFile(file)} accept=".js,.mjs,text/javascript">
              {(props) => (
                <Button {...props} variant="default" size="xs">
                  Upload .js file
                </Button>
              )}
            </FileButton>
            {script.trim() === '' && (
              <Button variant="default" size="xs" onClick={() => setScript(SAMPLE_SCRIPT)}>
                Use sample script
              </Button>
            )}
          </Group>
        </Stack>

        <Group justify="flex-end" gap="sm">
          <Button component={Link} to="/scenarios" variant="subtle">
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Create scenario
          </Button>
        </Group>
        <Text size="xs" c="dimmed">
          The script is saved exactly as written, as version 1. Later edits create new versions.
        </Text>
      </Stack>
    </form>
  )
}
