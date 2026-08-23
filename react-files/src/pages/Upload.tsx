import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { Category, DocumentDetail } from '@/lib/types'
import { Button } from '@/components/ui/Button'
import { Field, Input, Select } from '@/components/ui/controls'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'

const DOCUMENT_TYPES = ['INVOICE', 'CONTRACT', 'RECEIPT', 'OTHER']

export function UploadDocument() {
  const navigate = useNavigate()
  const [categories, setCategories] = useState<Category[]>([])
  const [title, setTitle] = useState('')
  const [documentType, setDocumentType] = useState('INVOICE')
  const [categoryId, setCategoryId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.get<Category[]>('/api/categories').then((list) => {
      setCategories(list)
      if (list.length) setCategoryId(list[0].id)
    })
  }, [])

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!file) {
      setError('Please choose a file to upload.')
      return
    }
    setError(null)
    setBusy(true)
    try {
      const form = new FormData()
      form.append('title', title)
      form.append('documentType', documentType)
      form.append('categoryId', categoryId)
      form.append('file', file)
      const created = await api.upload<DocumentDetail>('/api/documents', form)
      navigate(`/documents/${created.id}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Upload failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <h1 className="text-2xl font-semibold">Upload document</h1>
      <Card>
        <CardHeader title="Document details" description="Invoices are sent to the extraction service automatically." />
        <CardBody>
          <form onSubmit={onSubmit} className="space-y-4">
            <Field label="Title">
              <Input value={title} onChange={(e) => setTitle(e.target.value)} required placeholder="Q3 Vendor Invoice" />
            </Field>
            <div className="grid grid-cols-2 gap-4">
              <Field label="Type">
                <Select value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
                  {DOCUMENT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Category">
                <Select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
            <Field label="File (PDF)">
              <Input type="file" accept="application/pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
            </Field>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => navigate('/documents')}>
                Cancel
              </Button>
              <Button type="submit" disabled={busy}>
                {busy ? 'Uploading…' : 'Upload'}
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </div>
  )
}
