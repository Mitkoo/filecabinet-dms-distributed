import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Download, Trash2, Play, RefreshCw } from 'lucide-react'
import { api, ApiError, getToken } from '@/lib/api'
import type { DocumentDetail as Doc, ExtractionJob, ReviewerOption, WorkflowDetail } from '@/lib/types'
import { cn } from '@/lib/utils'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { Field, Textarea } from '@/components/ui/controls'
import { ExtractionViewer } from '@/components/ExtractionViewer'
import { StatusSelect, statusChoices } from '@/components/StatusSelect'

type ReviewerOpt = ReviewerOption

export function DocumentDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { isAdmin, user } = useAuth()
  const [doc, setDoc] = useState<Doc | null>(null)
  const [extraction, setExtraction] = useState<ExtractionJob | null>(null)
  const [workflow, setWorkflow] = useState<WorkflowDetail | null>(null)
  const [reviewers, setReviewers] = useState<ReviewerOpt[]>([])
  const [picked, setPicked] = useState<ReviewerOpt[]>([])
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)

  const loadDoc = useCallback(() => api.get<Doc>(`/api/documents/${id}`).then(setDoc), [id])
  const loadExtraction = useCallback(
    () =>
      api
        .get<ExtractionJob>(`/api/documents/${id}/extraction`)
        .then(setExtraction)
        .catch(() => setExtraction(null)),
    [id],
  )
  const loadWorkflow = useCallback(
    () =>
      api
        .get<WorkflowDetail>(`/api/workflows/by-document/${id}`)
        .then(setWorkflow)
        .catch(() => setWorkflow(null)),
    [id],
  )

  useEffect(() => {
    loadDoc()
    loadExtraction()
    loadWorkflow()
    api.get<ReviewerOpt[]>('/api/workflows/reviewers').then(setReviewers)
  }, [loadDoc, loadExtraction, loadWorkflow])

  useEffect(() => {
    if (extraction && (extraction.status === 'QUEUED' || extraction.status === 'PROCESSING')) {
      const t = setTimeout(loadExtraction, 3000)
      return () => clearTimeout(t)
    }
  }, [extraction, loadExtraction])

  async function viewFile() {
    const res = await fetch(`/api/documents/${id}/file`, { headers: { Authorization: `Bearer ${getToken()}` } })
    const blob = await res.blob()
    window.open(URL.createObjectURL(blob), '_blank')
  }

  async function runExtraction() {
    await api.post(`/api/documents/${id}/extract`)
    loadExtraction()
  }

  async function remove() {
    if (!confirm('Delete this document?')) return
    await api.del(`/api/documents/${id}`)
    navigate('/documents')
  }

  async function markPaid() {
    try {
      await api.post(`/api/documents/${id}/mark-paid`)
      loadDoc()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not mark as paid')
    }
  }

  async function changeStatus(status: string) {
    setError(null)
    try {
      await api.put(`/api/documents/${id}/status`, { status })
      loadDoc()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change status')
    }
  }

  async function startWorkflow() {
    setError(null)
    try {
      const created = await api.post<WorkflowDetail>('/api/workflows', {
        documentId: id,
        reviewerIds: picked.map((r) => r.id),
        message,
      })
      navigate(`/workflows/${created.id}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start review')
    }
  }

  if (!doc) return <p className="text-muted-foreground">Loading…</p>

  const canMarkPaid = doc.documentType === 'INVOICE' && doc.status === 'APPROVED' && (isAdmin || user?.role === 'ACCOUNTANT')
  const statusOptions = statusChoices(user?.role, doc.status)

  return (
    <div className="space-y-5">
      <Link to="/documents" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        Back to documents
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">{doc.title}</h1>
            <StatusBadge status={doc.status} />
          </div>
          <p className="text-sm text-muted-foreground">
            {doc.documentType} · {doc.categoryName} · owner {doc.ownerUsername} ·{' '}
            {new Date(doc.uploadedOn).toLocaleDateString()}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {statusOptions.length > 1 && (
            <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
              Status
              <StatusSelect value={doc.status} options={statusOptions} onChange={changeStatus} />
            </label>
          )}
          <Button variant="outline" size="sm" onClick={viewFile}>
            <Download className="size-4" />
            View file
          </Button>
          {canMarkPaid && (
            <Button variant="success" size="sm" onClick={markPaid}>
              Mark paid
            </Button>
          )}
          <Button variant="destructive" size="sm" onClick={remove}>
            <Trash2 className="size-4" />
            Delete
          </Button>
        </div>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <Card>
        <CardHeader
          title="Extraction"
          description="Fields pulled by the extraction microservice — hover to locate, click to correct"
          action={(() => {
            const running = extraction?.status === 'QUEUED' || extraction?.status === 'PROCESSING'
            const completed = extraction?.status === 'COMPLETED'
            const failed = extraction?.status === 'FAILED'
            return (
              <Button
                variant={completed ? 'success' : failed ? 'destructive' : 'outline'}
                size="sm"
                onClick={runExtraction}
                disabled={running}
              >
                <RefreshCw className={cn('size-4', running && 'animate-spin')} />
                {running ? 'Running…' : completed ? 'Extracted' : failed ? 'Retry' : 'Run'}
              </Button>
            )
          })()}
        />
        <CardBody>
          <ExtractionViewer
            documentId={doc.id}
            fileName={doc.filePath.split(/[\\/]/).pop() ?? 'document.pdf'}
            extraction={extraction}
            documentFields={doc.fields}
            onRefresh={loadExtraction}
            onSaved={loadDoc}
          />
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Review workflow" />
        <CardBody className="space-y-3">
          {workflow ? (
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm">
                <StatusBadge status={workflow.status} />
                <span className="text-muted-foreground">started by {workflow.initiatorUsername}</span>
              </div>
              <Link to={`/workflows/${workflow.id}`} className="text-sm font-medium text-primary hover:underline">
                Open workflow →
              </Link>
            </div>
          ) : doc.status === 'STRUCTURED' ? (
            <>
              <p className="text-sm text-muted-foreground">
                Add reviewers in order. Invoices require a buyer, then a manager, then an accountant.
              </p>
              <div className="flex flex-wrap gap-2">
                {reviewers.map((r) => (
                  <button
                    key={r.id}
                    type="button"
                    onClick={() => setPicked((p) => (p.find((x) => x.id === r.id) ? p : [...p, r]))}
                    className="rounded-full border border-border px-3 py-1 text-xs hover:bg-muted"
                  >
                    {r.username} <span className="text-muted-foreground">({r.role})</span>
                  </button>
                ))}
              </div>
              {picked.length > 0 && (
                <ol className="list-decimal space-y-1 pl-5 text-sm">
                  {picked.map((r, i) => (
                    <li key={r.id} className="flex items-center justify-between gap-2">
                      <span>
                        {r.username} <span className="text-muted-foreground">({r.role})</span>
                      </span>
                      <button
                        type="button"
                        className="text-xs text-destructive hover:underline"
                        onClick={() => setPicked((p) => p.filter((_, idx) => idx !== i))}
                      >
                        remove
                      </button>
                    </li>
                  ))}
                </ol>
              )}
              <Field label="Message (optional)">
                <Textarea value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Please review before month-end." />
              </Field>
              <Button onClick={startWorkflow} disabled={picked.length === 0}>
                <Play className="size-4" />
                Start review
              </Button>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">
              This document is <StatusBadge status={doc.status} className="mx-1" /> — add a field to structure it before starting a
              review.
            </p>
          )}
        </CardBody>
      </Card>
    </div>
  )
}
