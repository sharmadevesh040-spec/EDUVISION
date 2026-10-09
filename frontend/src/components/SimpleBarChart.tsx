/**
 * Dependency-free SVG bar chart used by the analytics page.
 * Keeps the bundle tiny (no chart library needed for a handful of bars).
 */
export interface BarDatum {
  label: string
  value: number
  hint?: string
}

interface Props {
  data: BarDatum[]
  height?: number
  color?: string
  valueFormatter?: (v: number) => string
}

export default function SimpleBarChart({
  data,
  height = 180,
  color = '#22d3ee',
  valueFormatter = (v) => `${Math.round(v)}%`,
}: Props) {
  if (data.length === 0) return null
  const max = Math.max(1, ...data.map((d) => d.value))
  const width = 640
  const pad = 30
  const innerH = height - pad
  const barW = Math.max(6, Math.min(44, (width - 60) / data.length - 8))

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      className="w-full"
      style={{ maxHeight: height }}
      aria-label="Bar chart"
    >
      {[0, 25, 50, 75, 100].map((g) => {
        const y = pad + (innerH * (100 - g)) / 100
        return (
          <g key={g}>
            <line x1={0} x2={width - 20} y1={y} y2={y} stroke="#1e293b" strokeWidth={1} />
            <text x={width - 14} y={y + 4} fontSize={10} fill="#475569" textAnchor="end">
              {g}
            </text>
          </g>
        )
      })}
      {data.map((d, i) => {
        const h = (d.value / max) * innerH
        const x = 20 + i * ((width - 60) / data.length)
        const y = pad + (innerH - h)
        return (
          <g key={`${d.label}-${i}`}>
            <rect x={x} y={y} width={barW} height={Math.max(2, h)} rx={4} fill={color} opacity={0.9}>
              <title>{`${d.label}: ${valueFormatter(d.value)}`}</title>
            </rect>
            <text
              x={x + barW / 2}
              y={height - 8}
              fontSize={10}
              fill="#94a3b8"
              textAnchor="middle"
            >
              {d.label}
            </text>
            <text x={x + barW / 2} y={y - 5} fontSize={10} fill="#e2e8f0" textAnchor="middle">
              {valueFormatter(d.value)}
            </text>
          </g>
        )
      })}
    </svg>
  )
}