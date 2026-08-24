import { cn } from '@/lib/utils'

type Tone = 'success' | 'warning' | 'primary' | 'violet' | 'destructive' | 'muted'

const toneStyles: Record<Tone, string> = {
  success: 'bg-success/12 text-success ring-success/30',
  warning: 'bg-warning/15 text-warning-foreground ring-warning/30',
  primary: 'bg-primary/10 text-primary ring-primary/25',
  violet: 'bg-violet-500/12 text-violet-700 ring-violet-500/30 dark:text-violet-300',
  destructive: 'bg-destructive/12 text-destructive ring-destructive/30',
  muted: 'bg-muted text-muted-foreground ring-border',
}

const statusTone: Record<string, Tone> = {
  UPLOADED: 'muted',
  STRUCTURED: 'primary',
  IN_REVIEW: 'warning',
  IN_PROGRESS: 'warning',
  PROCESSING: 'primary',
  QUEUED: 'muted',
  PENDING: 'warning',
  APPROVED: 'success',
  COMPLETED: 'success',
  PAID: 'violet',
  REJECTED: 'destructive',
  FAILED: 'destructive',
  CANCELLED: 'muted',
  SKIPPED: 'muted',
  ARCHIVED: 'muted',
}

export function StatusBadge({ status, className }: { status: string; className?: string }) {
  const tone = statusTone[status] ?? 'muted'
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset',
        toneStyles[tone],
        className,
      )}
    >
      <span className="size-1.5 rounded-full bg-current" aria-hidden />
      {status.replace(/_/g, ' ').toLowerCase()}
    </span>
  )
}
