import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, Check } from 'lucide-react'
import { api, getToken } from '@/lib/api'
import type { DocumentField, ExtractionJob, FieldBox } from '@/lib/types'
import { PdfPane } from './PdfPane'
import { LineItemsEditor } from './LineItemsEditor'
import { StatusBadge } from './ui/StatusBadge'
import { Button } from './ui/Button'

const HEADER_FIELDS: { name: string; label: string }[] = [
  { name: 'invoice_number', label: 'Invoice number' },
  { name: 'invoice_date', label: 'Invoice date' },
  { name: 'due_date', label: 'Due date' },
  { name: 'currency', label: 'Currency' },
  { name: 'supplier_legal_name', label: 'Supplier' },
  { name: 'supplier_vat_number', label: 'Supplier VAT' },
  { name: 'supplier_country', label: 'Supplier country' },
  { name: 'supplier_city', label: 'Supplier city' },
  { name: 'payment_terms', label: 'Payment terms' },
  { name: 'tax_rate_percent', label: 'Tax rate %' },
  { name: 'subtotal', label: 'Subtotal' },
  { name: 'total_discount', label: 'Discount' },
  { name: 'total_charges', label: 'Shipping / charges' },
  { name: 'total_net', label: 'Total net' },
  { name: 'total_tax', label: 'Total tax' },
  { name: 'total_gross', label: 'Total gross' },
]

export function ExtractionViewer({
  documentId,
  fileName,
  extraction,
  documentFields,
  onRefresh,
  onSaved,
}: {
  documentId: string
  fileName: string
  extraction: ExtractionJob | null
  documentFields: DocumentField[]
  onRefresh: () => void
  onSaved: () => void
}) {
  const [fileUrl, setFileUrl] = useState<string | null>(null)
  const [active, setActive] = useState<FieldBox | null>(null)
  const [page, setPage] = useState(1)
  const [edits, setEdits] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const extractionByName = useMemo(() => {
    const map: Record<string, ExtractionJob['fields'][number]> = {}
    extraction?.fields.forEach((f) => (map[f.fieldName] = f))
    return map
  }, [extraction])

  const docByName = useMemo(() => {
    const map: Record<string, string> = {}
    documentFields.forEach((f) => (map[f.fieldName] = f.fieldValue))
    return map
  }, [documentFields])

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false
    fetch(`/api/documents/${documentId}/file`, { headers: { Authorization: `Bearer ${getToken()}` } })
      .then((r) => (r.ok ? r.blob() : Promise.reject(new Error('file'))))
      .then((blob) => {
        if (!cancelled) {
          objectUrl = URL.createObjectURL(blob)
          setFileUrl(objectUrl)
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [documentId])

  function base(name: string): string {
    return docByName[name] ?? extractionByName[name]?.fieldValue ?? ''
  }
  function value(name: string): string {
    return name in edits ? edits[name] : base(name)
  }
  function locate(box: FieldBox | null | undefined) {
    setActive(box ?? null)
    if (box) setPage(box.page)
  }

  async function saveAll() {
    setSaving(true)
    setSaved(false)
    try {
      const fields: Record<string, string> = {}
      for (const { name } of HEADER_FIELDS) {
        const v = value(name).trim()
        if (v) fields[name] = v
      }
      await api.put(`/api/documents/${documentId}/fields`, { fields })
      setEdits({})
      setSaved(true)
      onSaved()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-5">
      {extraction?.needsReview && extraction.reviewNotes.length > 0 && (
        <div className="flex gap-3 rounded-xl border border-warning/40 bg-warning/10 px-4 py-3">
          <AlertTriangle className="mt-0.5 size-5 shrink-0 text-warning-foreground" />
          <div className="space-y-1">
            <p className="text-sm font-semibold text-warning-foreground">This invoice needs human review</p>
            <ul className="list-disc space-y-0.5 pl-5 text-sm text-muted-foreground">
              {extraction.reviewNotes.map((note, i) => (
                <li key={i}>{note}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[1.5fr_1fr] lg:h-[calc(100vh-13rem)]">
        <div className="min-h-[24rem] lg:h-full">
          {fileUrl ? (
            <PdfPane fileUrl={fileUrl} fileName={fileName} page={page} highlight={active} />
          ) : (
            <div className="grid h-full place-items-center rounded-xl border border-border bg-muted/30 text-sm text-muted-foreground">
              Loading document…
            </div>
          )}
        </div>

        <div className="flex min-h-0 flex-col overflow-hidden rounded-xl border border-border bg-card lg:h-full">
          <div className="flex shrink-0 items-center gap-2 border-b border-border px-4 py-2.5 text-sm">
            <span className="font-medium">Header details</span>
            {extraction ? (
              <span className="ml-auto flex items-center gap-2">
                <StatusBadge status={extraction.status} />
                <span className="text-xs text-muted-foreground">{extraction.provider}</span>
              </span>
            ) : (
              <span className="ml-auto text-xs text-muted-foreground">no extraction yet</span>
            )}
          </div>
          <div className="min-h-0 flex-1 divide-y divide-border overflow-y-auto">
            {HEADER_FIELDS.map(({ name, label }) => {
              const box = extractionByName[name]?.box ?? null
              return (
                <div
                  key={name}
                  className="px-4 py-2 transition-colors hover:bg-muted/40"
                  onMouseEnter={() => locate(box)}
                  onMouseLeave={() => locate(null)}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-medium text-muted-foreground">{label}</span>
                    {box && <span className="text-[10px] font-medium text-primary">page {box.page}</span>}
                  </div>
                  <input
                    value={value(name)}
                    placeholder="—"
                    onChange={(e) => setEdits((d) => ({ ...d, [name]: e.target.value }))}
                    className="mt-0.5 w-full rounded-md border border-transparent bg-transparent px-1.5 py-1 text-sm font-medium tabular-nums transition-colors hover:border-input focus:border-ring focus:bg-background focus:outline-none focus:ring-2 focus:ring-ring/30"
                  />
                </div>
              )
            })}
          </div>
          <p className="shrink-0 border-t border-border px-4 py-2 text-xs text-muted-foreground">
            Fill in or correct the header, then save it to the document.
          </p>
        </div>
      </div>

      {extraction && extraction.lineItems.length > 0 && (
        <div className="space-y-2">
          <h3 className="text-sm font-semibold">Line items</h3>
          <LineItemsEditor documentId={documentId} items={extraction.lineItems} onHover={locate} onRefresh={onRefresh} />
        </div>
      )}

      <div className="flex items-center justify-end gap-3 border-t border-border pt-3">
        {saved && <span className="text-sm text-success">Saved to document.</span>}
        <Button onClick={saveAll} disabled={saving}>
          <Check className="size-4" />
          {saving ? 'Saving…' : 'Save reviewed fields'}
        </Button>
      </div>
    </div>
  )
}
