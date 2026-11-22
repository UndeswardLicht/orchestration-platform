import { createTheme, type CSSVariablesResolver } from '@mantine/core'

export const theme = createTheme({
  primaryColor: 'indigo',
  defaultRadius: 'md',
  // Keep text contrast >= 4.5:1 (NFR-08): use shade 7+ for primary text on light backgrounds
  primaryShade: { light: 7, dark: 4 },
})

/**
 * Mantine's default `dimmed` text (gray.6 on white) is only 3.3:1, below WCAG AA (NFR-08).
 * gray.7 on white is about 7.5:1; dark.1 on dark.7 is about 7:1.
 */
export const cssVariablesResolver: CSSVariablesResolver = (t) => ({
  variables: {},
  light: { '--mantine-color-dimmed': t.colors.gray[7] },
  dark: { '--mantine-color-dimmed': t.colors.dark[1] },
})
