import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
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
      <div>
        <h1 className="text-2xl font-semibold">Review inbox</h1>
        <p className="text-sm text-muted-foreground">Workflows you started or are a reviewer on</p>
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
            className="flex items-center justify-between rounded-xl border border-border bg-card px-5 py-4 transition-colors hover:bg-muted/40"
          >
            <div>
              <p className="font-medium">{wf.documentTitle}</p>
              <p className="text-sm text-muted-foreground">
                started by {wf.initiatorUsername} · {new Date(wf.createdOn).toLocaleDateString()}
              </p>
            </div>
            <StatusBadge status={wf.status} />
          </Link>
        ))}
      </div>
    </div>
  )
}
