import { describe, expect, it } from 'vitest'
import { formatAgo, formatMemory } from './format.ts'

const NOW = Date.parse('2026-11-27T10:00:00Z')
const ago = (seconds: number) => new Date(NOW - seconds * 1000).toISOString()

describe('formatAgo', () => {
  it.each([
    [0, 'just now'],
    [1, 'just now'],
    [8, '8 s ago'],
    [59, '59 s ago'],
    [60, '1 min ago'],
    [3599, '59 min ago'],
    [7200, '2 h ago'],
    [86400, '1 day ago'],
    [4 * 86400, '4 days ago'],
  ])('%i s → %s', (seconds, text) => {
    expect(formatAgo(ago(seconds), NOW)).toBe(text)
  })

  it('treats a time slightly ahead of the browser clock as just now', () => {
    expect(formatAgo(ago(-3), NOW)).toBe('just now')
  })
})

describe('formatMemory', () => {
  it.each([
    [512, '512 MB'],
    [1024, '1 GB'],
    [1536, '1.5 GB'],
    [4096, '4 GB'],
  ])('%i MB → %s', (mb, text) => {
    expect(formatMemory(mb)).toBe(text)
  })
})
