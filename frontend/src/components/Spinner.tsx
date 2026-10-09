export default function Spinner({ label = 'Loading…', className = '' }: { label?: string; className?: string }) {
  return (
    <div className={`flex items-center gap-3 text-sm text-slate-400 ${className}`} role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-600 border-t-cyan-400" />
      <span>{label}</span>
    </div>
  )
}
