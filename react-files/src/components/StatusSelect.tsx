import { DOCUMENT_STATUSES } from '@/lib/types'
import { cn } from '@/lib/utils'

export function statusChoices(role: string | undefined, current: string): string[] {
  if (role === 'ADMIN') return [...DOCUMENT_STATUSES]
  if (role === 'MANAGER') return Array.from(new Set([current, 'STRUCTURED', 'REJECTED']))
  return []
}

export function StatusSelect({
  value,
  options,
  onChange,
  disabled,
  className,
}: {
  value: string
  options: readonly string[]
  onChange: (status: string) => void
  disabled?: boolean
  className?: string
}) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
      className={cn(
        'rounded-md border border-input bg-background px-2 py-1 text-xs font-medium capitalize disabled:opacity-60 focus:border-ring focus:outline-none focus:ring-2 focus:ring-ring/30',
        className,
      )}
    >
      {options.map((status) => (
        <option key={status} value={status}>
          {status.replace(/_/g, ' ').toLowerCase()}
        </option>
      ))}
    </select>
  )
}
