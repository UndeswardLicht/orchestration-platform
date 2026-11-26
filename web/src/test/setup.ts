import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { clearCookies } from './fakeApi.ts'

// The first render of a page with Mantine and CodeMirror can take over a second while test files run in
// parallel; the default 1 s wait of findBy* is too short for that
configure({ asyncUtilTimeout: 5000 })

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  clearCookies()
})

// jsdom lacks these browser APIs; Mantine needs them
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
window.ResizeObserver = ResizeObserverStub

// CodeMirror measures text through Range rectangles, which jsdom does not lay out
const emptyRects = () => ({ length: 0, item: () => null, [Symbol.iterator]: [][Symbol.iterator] }) as unknown as DOMRectList
Range.prototype.getClientRects = emptyRects
Range.prototype.getBoundingClientRect = () => new DOMRect()

// Mantine's autosize Textarea listens for web fonts loading; jsdom has no FontFaceSet
Object.defineProperty(document, 'fonts', {
  configurable: true,
  value: { addEventListener: () => {}, removeEventListener: () => {}, ready: Promise.resolve() },
})
