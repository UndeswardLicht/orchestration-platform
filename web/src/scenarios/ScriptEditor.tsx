import { javascript } from '@codemirror/lang-javascript'
import { Box, Input, useComputedColorScheme } from '@mantine/core'
import CodeMirror, { EditorView } from '@uiw/react-codemirror'
import { useId, useMemo } from 'react'

function contentAttributes(labelledBy: string, describedBy: string, invalid: boolean, readOnly: boolean) {
  // In the tab order even when read-only, so keyboard users can reach a long script and scroll it
  // (WCAG 2.1.1; axe's scrollable-region-focusable rule does not count contenteditable as focusable).
  const attributes: Record<string, string> = { 'aria-labelledby': labelledBy, tabindex: '0' }
  if (describedBy) attributes['aria-describedby'] = describedBy
  if (invalid) attributes['aria-invalid'] = 'true'
  if (readOnly) attributes['aria-readonly'] = 'true'
  return attributes
}

interface ScriptEditorProps {
  label: string
  value: string
  onChange?: (value: string) => void
  readOnly?: boolean
  description?: string
  error?: string
  height?: string
}

/**
 * k6 script editor (CodeMirror 6 with JavaScript highlighting). The editable area is named by the
 * label and points at the error, like a normal form field, so screen readers announce both (NFR-08).
 */
export function ScriptEditor({ label, value, onChange, readOnly = false, description, error, height = '320px' }: ScriptEditorProps) {
  const id = useId()
  const colorScheme = useComputedColorScheme('light')
  const labelId = `${id}-label`
  const errorId = `${id}-error`
  const descriptionId = `${id}-description`
  const describedBy = [description && descriptionId, error && errorId].filter(Boolean).join(' ')

  const extensions = useMemo(
    () => [
      javascript(),
      EditorView.lineWrapping,
      EditorView.contentAttributes.of(contentAttributes(labelId, describedBy, error !== undefined, readOnly)),
    ],
    [labelId, describedBy, error, readOnly],
  )

  return (
    <Input.Wrapper
      label={label}
      labelProps={{ id: labelId, component: 'div' }}
      description={description}
      descriptionProps={{ id: descriptionId }}
      error={error}
      errorProps={{ id: errorId }}
    >
      <Box
        mt={4}
        style={{
          border: `1px solid ${error ? 'var(--mantine-color-error)' : 'var(--mantine-color-default-border)'}`,
          borderRadius: 'var(--mantine-radius-md)',
          overflow: 'hidden',
        }}
      >
        <CodeMirror
          value={value}
          onChange={onChange}
          readOnly={readOnly}
          editable={!readOnly}
          height={height}
          theme={colorScheme === 'dark' ? 'dark' : 'light'}
          extensions={extensions}
          basicSetup={{ foldGutter: false, highlightActiveLine: !readOnly }}
        />
      </Box>
    </Input.Wrapper>
  )
}
