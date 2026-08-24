import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  ArrowUpRight,
  Ban,
  Bell,
  Check,
  CircleSlash,
  Clock,
  Flag,
  MessageSquare,
  Signpost,
  X,
  type LucideIcon,
} from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type { WorkflowDetail as Workflow } from '@/lib/types'
import { useAuth } from '@/auth/AuthContext'
import { Button, buttonVariants } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { Textarea } from '@/components/ui/controls'
import { ReviewActions } from '@/components/ReviewActions'
import { cn } from '@/lib/utils'

const EVENT_META: Record<string, { icon: LucideIcon; ring: string; verb: string }> = {
  STARTED: { icon: Signpost, ring: 'bg-primary/10 text-primary', verb: 'started this review' },
  STEP_APPROVED: { icon: Check, ring: 'bg-success/12 text-success', verb: 'approved a step' },
  STEP_REJECTED: { icon: X, ring: 'bg-destructive/12 text-destructive', verb: 'rejected a step' },
  COMMENT: { icon: MessageSquare, ring: 'bg-muted text-muted-foreground', verb: 'left a comment' },
  REMINDER_SENT: { icon: Bell, ring: 'bg-warning/15 text-warning-foreground', verb: 'sent a reminder' },
  COMPLETED: { icon: Flag, ring: 'bg-success/12 text-success', verb: 'finished the review' },
  CANCELLED: { icon: CircleSlash, ring: 'bg-muted text-muted-foreground', verb: 'cancelled the review' },
}
const FALLBACK_EVENT = { icon: Clock, ring: 'bg-muted text-muted-foreground', verb: 'updated the review' }

const STEP_ICON: Record<string, LucideIcon> = {
  APPROVED: Check,
  REJECTED: X,
  SKIPPED: CircleSlash,
  CANCELLED: CircleSlash,
}
const STEP_RING: Record<string, string> = {
  APPROVED: 'bg-success/12 text-success',
  REJECTED: 'bg-destructive/12 text-destructive',
  SKIPPED: 'bg-muted text-muted-foreground',
  CANCELLED: 'bg-muted text-muted-foreground',
  PENDING: 'bg-muted text-muted-foreground',
}

function fmt(value: string): string {
  return new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function WorkflowDetail() {
  const { id = '' } = useParams()
  const { user, isAdmin } = useAuth()
  const [wf, setWf] = useState<Workflow | null>(null)
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [reminded, setReminded] = useState(false)

  const load = useCallback(() => api.get<Workflow>(`/api/workflows/${id}`).then(setWf), [id])
  useEffect(() => {
    load()
  }, [load])

  if (!wf) return <p className="text-muted-foreground">Loading…</p>

  const currentStep = wf.steps.find((s) => s.status === 'PENDING')
  const isMyTurn = wf.status === 'IN_PROGRESS' && currentStep?.reviewerUsername === user?.username
  const canManage = wf.status === 'IN_PROGRESS' && (isAdmin || wf.initiatorUsername === user?.username)
  const canMarkPaid =
    (isAdmin || user?.role === 'ACCOUNTANT') && wf.documentType === 'INVOICE' && wf.documentStatus === 'APPROVED'
  const decided = wf.steps.filter((s) => s.status === 'APPROVED' || s.status === 'REJECTED').length
  const progress = wf.steps.length ? Math.round((decided / wf.steps.length) * 100) : 0

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

  async function remind() {
    setError(null)
    try {
      await api.post(`/api/workflows/${id}/remind`)
      setReminded(true)
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send reminder')
    }
  }

  async function cancel() {
    if (!confirm('Cancel this workflow?')) return
    await api.post(`/api/workflows/${id}/cancel`)
    load()
  }

  async function markPaid() {
    setError(null)
    try {
      await api.post(`/api/documents/${wf!.documentId}/mark-paid`)
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not mark as paid')
    }
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
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
            <Link to={`/documents/${wf.documentId}`} className="font-medium text-primary hover:underline">
              {wf.documentTitle}
            </Link>
            <StatusBadge status={wf.documentStatus} />
            <span>· started by {wf.initiatorUsername} · {fmt(wf.createdOn)}</span>
          </p>
          {wf.message && <p className="text-sm italic text-muted-foreground">“{wf.message}”</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            to={`/documents/${wf.documentId}`}
            className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'gap-1.5')}
          >
            <ArrowUpRight className="size-4" />
            Open document
          </Link>
          {canMarkPaid && (
            <Button variant="success" size="sm" onClick={markPaid}>
              Mark paid
            </Button>
          )}
          {canManage && currentStep && (
            <Button variant="outline" size="sm" onClick={remind} disabled={reminded}>
              <Bell className="size-4" />
              {reminded ? 'Reminder sent' : 'Send reminder'}
            </Button>
          )}
          {canManage && (
            <Button variant="destructive" size="sm" onClick={cancel}>
              <Ban className="size-4" />
              Cancel
            </Button>
          )}
        </div>
      </div>

      {isMyTurn && currentStep && <ReviewActions workflowId={wf.id} stepId={currentStep.id} onDone={load} />}

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader
            title="Reviewers"
            action={
              <span className="text-xs font-medium text-muted-foreground">
                {decided} of {wf.steps.length} decided
              </span>
            }
          />
          <div className="h-1 w-full bg-muted">
            <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
          </div>
          <CardBody>
            <ol className="relative space-y-4 before:absolute before:bottom-3 before:left-4 before:top-3 before:w-px before:bg-border">
              {wf.steps.map((step) => {
                const isCurrent = step.id === currentStep?.id
                const Icon = STEP_ICON[step.status]
                return (
                  <li key={step.id} className="relative flex gap-3">
                    <span
                      className={cn(
                        'z-[1] grid size-8 shrink-0 place-items-center rounded-full text-xs font-semibold ring-4 ring-card',
                        isCurrent
                          ? 'bg-primary text-primary-foreground'
                          : STEP_RING[step.status] ?? 'bg-muted text-muted-foreground',
                      )}
                    >
                      {Icon ? <Icon className="size-4" /> : step.stepOrder}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-medium">{step.reviewerUsername}</span>
                        {isCurrent && (
                          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-primary">
                            Up next
                          </span>
                        )}
                        <StatusBadge status={step.status} className="ml-auto" />
                      </div>
                      {step.decidedOn && (
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {step.status === 'APPROVED' ? 'Approved' : step.status === 'REJECTED' ? 'Rejected' : 'Decided'} ·{' '}
                          {fmt(step.decidedOn)}
                        </p>
                      )}
                      {step.comment && (
                        <p className="mt-1 rounded-md bg-muted/50 px-2 py-1 text-sm text-muted-foreground">“{step.comment}”</p>
                      )}
                    </div>
                  </li>
                )
              })}
            </ol>
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Activity"
            description={`${wf.events.length} update${wf.events.length === 1 ? '' : 's'}`}
          />
          <CardBody className="space-y-4">
            {wf.events.length === 0 ? (
              <p className="text-sm text-muted-foreground">No activity yet.</p>
            ) : (
              <ol className="relative space-y-4 before:absolute before:bottom-3 before:left-4 before:top-3 before:w-px before:bg-border">
                {wf.events.map((ev) => {
                  const meta = EVENT_META[ev.eventType] ?? FALLBACK_EVENT
                  const Icon = meta.icon
                  return (
                    <li key={ev.id} className="relative flex gap-3">
                      <span className={cn('z-[1] grid size-8 shrink-0 place-items-center rounded-full ring-4 ring-card', meta.ring)}>
                        <Icon className="size-4" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm">
                          <span className="font-medium">{ev.actorUsername ?? 'System'}</span>{' '}
                          <span className="text-muted-foreground">{meta.verb}</span>
                        </p>
                        {ev.message && (
                          <p className="mt-1 rounded-md bg-muted/50 px-2 py-1 text-sm text-muted-foreground">{ev.message}</p>
                        )}
                        <p className="mt-0.5 text-xs text-muted-foreground">{fmt(ev.createdOn)}</p>
                      </div>
                    </li>
                  )
                })}
              </ol>
            )}
            {wf.status === 'IN_PROGRESS' && (
              <form onSubmit={submitComment} className="space-y-2 border-t border-border pt-4">
                <Textarea placeholder="Add a comment…" value={comment} onChange={(e) => setComment(e.target.value)} />
                {error && <p className="text-sm text-destructive">{error}</p>}
                <Button type="submit" size="sm" variant="outline">
                  <MessageSquare className="size-4" />
                  Post comment
                </Button>
              </form>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  )
}
