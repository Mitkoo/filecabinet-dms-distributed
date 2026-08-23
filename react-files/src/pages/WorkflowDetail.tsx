import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, MessageSquare, Ban } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type { WorkflowDetail as Workflow } from '@/lib/types'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { Textarea } from '@/components/ui/controls'
import { ReviewActions } from '@/components/ReviewActions'
import { cn } from '@/lib/utils'

export function WorkflowDetail() {
  const { id = '' } = useParams()
  const { user, isAdmin } = useAuth()
  const [wf, setWf] = useState<Workflow | null>(null)
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => api.get<Workflow>(`/api/workflows/${id}`).then(setWf), [id])
  useEffect(() => {
    load()
  }, [load])

  if (!wf) return <p className="text-muted-foreground">Loading…</p>

  const currentStep = wf.steps.find((s) => s.status === 'PENDING')
  const isMyTurn = wf.status === 'IN_PROGRESS' && currentStep?.reviewerUsername === user?.username
  const canCancel = wf.status === 'IN_PROGRESS' && (isAdmin || wf.initiatorUsername === user?.username)

  async function submitComment(e: FormEvent) {
    e.preventDefault()
    if (!comment.trim()) return
    setError(null)
    try {
      await api.post(`/api/workflows/${id}/comments`, { message: comment })
      setComment('')
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add comment')
    }
  }

  async function cancel() {
    if (!confirm('Cancel this workflow?')) return
    await api.post(`/api/workflows/${id}/cancel`)
    load()
  }

  return (
    <div className="space-y-5">
      <Link to="/inbox" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        Back to inbox
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">Review workflow</h1>
            <StatusBadge status={wf.status} />
          </div>
          <p className="text-sm text-muted-foreground">
            <Link to={`/documents/${wf.documentId}`} className="text-primary hover:underline">
              {wf.documentTitle}
            </Link>{' '}
            · started by {wf.initiatorUsername}
          </p>
          {wf.message && <p className="text-sm text-muted-foreground italic">“{wf.message}”</p>}
        </div>
        {canCancel && (
          <Button variant="destructive" size="sm" onClick={cancel}>
            <Ban className="size-4" />
            Cancel workflow
          </Button>
        )}
      </div>

      {isMyTurn && currentStep && <ReviewActions workflowId={wf.id} stepId={currentStep.id} onDone={load} />}

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader title="Steps" />
          <CardBody>
            <ol className="space-y-2">
              {wf.steps.map((s) => (
                <li
                  key={s.id}
                  className={cn(
                    'flex items-start justify-between gap-3 rounded-lg border px-3 py-2 text-sm',
                    s.id === currentStep?.id ? 'border-primary/40 bg-primary/5' : 'border-border',
                  )}
                >
                  <div>
                    <p className="font-medium">
                      {s.stepOrder}. {s.reviewerUsername}
                    </p>
                    {s.comment && <p className="text-muted-foreground">{s.comment}</p>}
                  </div>
                  <StatusBadge status={s.status} />
                </li>
              ))}
            </ol>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Activity" />
          <CardBody className="space-y-3">
            <ul className="space-y-2 text-sm">
              {wf.events.map((ev) => (
                <li key={ev.id} className="flex gap-2">
                  <span className="mt-1 size-1.5 shrink-0 rounded-full bg-primary" />
                  <div>
                    <p>
                      <span className="font-medium">{ev.actorUsername ?? 'system'}</span>{' '}
                      <span className="text-muted-foreground">{ev.eventType.replace(/_/g, ' ').toLowerCase()}</span>
                    </p>
                    {ev.message && <p className="text-muted-foreground">{ev.message}</p>}
                    <p className="text-xs text-muted-foreground">{new Date(ev.createdOn).toLocaleString()}</p>
                  </div>
                </li>
              ))}
            </ul>
            {wf.status === 'IN_PROGRESS' && (
              <form onSubmit={submitComment} className="space-y-2 border-t border-border pt-3">
                <Textarea placeholder="Add a comment" value={comment} onChange={(e) => setComment(e.target.value)} />
                {error && <p className="text-sm text-destructive">{error}</p>}
                <Button type="submit" size="sm" variant="outline">
                  <MessageSquare className="size-4" />
                  Comment
                </Button>
              </form>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  )
}
