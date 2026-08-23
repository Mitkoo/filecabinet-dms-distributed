import { useState } from 'react'
import { Check, X } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/controls'

export function ReviewActions({
  workflowId,
  stepId,
  onDone,
}: {
  workflowId: string
  stepId: string
  onDone: () => void
}) {
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function decide(approve: boolean) {
    setError(null)
    setBusy(true)
    try {
      await api.post(`/api/workflows/${workflowId}/steps/${stepId}/decision`, { approve, comment })
      setComment('')
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit decision')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-3 rounded-lg border border-primary/30 bg-primary/5 p-4">
      <p className="text-sm font-medium">It is your turn to review.</p>
      <Textarea placeholder="Add a comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} />
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="flex gap-2">
        <Button variant="success" onClick={() => decide(true)} disabled={busy}>
          <Check className="size-4" />
          Approve
        </Button>
        <Button variant="destructive" onClick={() => decide(false)} disabled={busy}>
          <X className="size-4" />
          Reject
        </Button>
      </div>
    </div>
  )
}
