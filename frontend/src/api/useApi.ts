import { useCallback, useEffect, useState } from 'react'
import { apiFetch } from './client'

export interface ApiState<T> {
  data: T | null
  error: Error | null
  loading: boolean
  reload: () => void
}

/**
 * Tiny data-fetching hook: one GET per call, `null` path = idle.
 * Every dashboard number comes from here — nothing is fabricated client-side.
 */
export function useApi<T>(path: string | null): ApiState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const [loading, setLoading] = useState<boolean>(path !== null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (path === null) {
      setData(null)
      setError(null)
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    apiFetch<T>(path)
      .then((body) => {
        if (!cancelled) {
          setData(body)
          setLoading(false)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err : new Error(String(err)))
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [path, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { data, error, loading, reload }
}
