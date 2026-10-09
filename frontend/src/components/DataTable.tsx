import type { ReactNode } from 'react'

export interface Column<T> {
  key: string
  header: string
  render: (row: T) => ReactNode
  align?: 'left' | 'right'
  className?: string
}

/** Plain typed table — no external UI kit. */
export default function DataTable<T>({
  columns,
  rows,
  rowKey,
  empty,
}: {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T, index: number) => string | number
  empty?: ReactNode
}) {
  if (rows.length === 0) return <>{empty ?? null}</>

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-left text-sm">
        <thead>
          <tr className="border-b border-slate-800 text-[11px] uppercase tracking-wider text-slate-500">
            {columns.map((c) => (
              <th
                key={c.key}
                className={`px-3 py-2.5 font-medium ${c.align === 'right' ? 'text-right' : ''} ${c.className ?? ''}`}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/70">
          {rows.map((row, i) => (
            <tr key={rowKey(row, i)} className="transition hover:bg-slate-800/40">
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={`px-3 py-2.5 text-slate-300 ${c.align === 'right' ? 'text-right tabular-nums' : ''} ${
                    c.className ?? ''
                  }`}
                >
                  {c.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
