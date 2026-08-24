import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, Inbox } from 'lucide-react'
import { api } from '@/lib/api'
import type { WorkflowSummary } from '@/lib/types'
import { StatusBadge } from '@/components/ui/StatusBadge'

export function WorkflowInbox() {
  const [items, setItems] = useState<WorkflowSummary[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<WorkflowSummary[]>('/api/workflows/inbox')
      .then(setItems)
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <span className="grid size-10 place-items-center rounded-xl bg-primary/10 text-primary">
          <Inbox className="size-5" />
        </span>
        <div>
          <h1 className="text-2xl font-semibold">Review inbox</h1>
          <p className="text-sm text-muted-foreground">Workflows you started or are a reviewer on</p>
        </div>
      </div>

      {loading && <p className="text-muted-foreground">Loading…</p>}
      {!loading && items.length === 0 && (
        <div className="rounded-xl border border-dashed border-border py-12 text-center text-muted-foreground">
          Nothing in your inbox.
        </div>
      )}

      <div className="grid gap-3">
        {items.map((wf) => (
          <Link
            key={wf.id}
            to={`/workflows/${wf.id}`}
            className="group flex items-center gap-4 rounded-xl border border-border bg-card px-5 py-4 transition-colors hover:bg-muted/40"
          >
            <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
              <Inbox className="size-5" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{wf.documentTitle}</p>
              <p className="text-sm text-muted-foreground">
                started by {wf.initiatorUsername} · {new Date(wf.createdOn).toLocaleDateString()}
              </p>
            </div>
            <StatusBadge status={wf.status} />
            <ChevronRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
          </Link>
        ))}
      </div>
    </div>
  )
}
