/**
 * JSX typing for the `<model-viewer>` custom element.
 *
 * React 19 ships no types for third-party custom elements, so we augment
 * `react`'s `JSX.IntrinsicElements` with the exact set of attributes this app
 * uses. Hyphenated attributes (`ar-modes`, `camera-controls`, …) are written by
 * React as plain DOM attributes on the custom element, which is what
 * model-viewer's Lit properties observe.
 *
 * No `any`, no `@ts-ignore` — every prop below is explicitly typed.
 */
import type { Key, ReactNode, Ref } from 'react'
import type { ModelViewerElement } from '@google/model-viewer'

export interface ModelViewerJsxProps {
  /** React reconciliation key — used to force a remount on Retry. */
  key?: Key
  /** glTF/GLB URL to display. */
  src?: string
  alt?: string
  poster?: string
  /** Enables model-viewer's built-in "View in AR" button. */
  ar?: boolean | string
  /** webxr (desktop) / scene-viewer (Android) / quick-look (iOS) — model-viewer picks. */
  'ar-modes'?: string
  'camera-controls'?: boolean | string
  'auto-rotate'?: boolean | string
  'shadow-intensity'?: string
  exposure?: string
  'environment-image'?: string
  'interaction-prompt'?: string
  loading?: string
  reveal?: string
  class?: string
  className?: string
  style?: string
  children?: ReactNode
  ref?: Ref<ModelViewerElement>
  onLoad?: (event: Event) => void
  onError?: (event: Event) => void
}

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'model-viewer': import('./model-viewer-jsx').ModelViewerJsxProps
    }
  }
}
