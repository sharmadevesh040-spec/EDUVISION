/**
 * Every dashboard section renders one of these when its API call fails.
 * - 403 (or a denial the backend currently reports as 500) -> friendly "not permitted" card
 * - anything else -> compact error banner with the server's `message`
 * The UI never crashes on a failed endpoint.
 */
export function NotPermittedCard({ title = 'Not permitted', hint }: { title?: string; hint?: string }) {
  return (
    <div className="rounded-2xl border border-amber-500/30 bg-amber-500/5 p-6 text-center">
      <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-amber-500/15 text-lg">
        🔒
      </div>
      <p className="text-sm font-semibold text-amber-200">{title}</p>
      <p className="mt-1 text-xs text-amber-200/70">
        {hint ?? 'Your account does not have access to this data.'}
      </p>
    </div>
  )
}

function messageOf(error: unknown): string {
  if (error instanceof Error) return error.message
  if (error && typeof error === 'object' && 'message' in error) {
    const m = (error as { message: unknown }).message
    if (typeof m === 'string') return m
  }
  return 'Unexpected error'
}

function statusOf(error: unknown): number | null {
  if (error && typeof error === 'object' && 'status' in error) {
    const s = (error as { status: unknown }).status
    if (typeof s === 'number') return s
  }
  return null
}

export default function ErrorBanner({
  error,
  title = 'Could not load data',
}: {
  error: unknown
  title?: string
}) {
  const status = statusOf(error)

  // The backend currently answers @PreAuthorize denials with 500 + "Unexpected server
  // error" (see report); treat that exact message as a permission problem too.
  const message = messageOf(error)
  const looksLikePermission =
    status === 403 || (status !== null && status >= 500 && message === 'Unexpected server error')

  if (looksLikePermission) {
    return <NotPermittedCard hint={status === 403 ? message : undefined} />
  }

  return (
    <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
      <span className="font-semibold">{title}:</span> {message}
      {status !== null && <span className="ml-2 text-rose-300/70">(HTTP {status})</span>}
    </div>
  )
}
