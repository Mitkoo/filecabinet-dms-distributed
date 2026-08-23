import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Upload } from 'lucide-react'
import { api } from '@/lib/api'
import type { DocumentListItem, Paged } from '@/lib/types'
import { Button, buttonVariants } from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { cn } from '@/lib/utils'

export function Documents() {
  const [scope, setScope] = useState<'mine' | 'all'>('mine')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Paged<DocumentListItem> | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api
      .get<Paged<DocumentListItem>>(`/api/documents?scope=${scope}&page=${page}&size=25`)
      .then(setData)
      .finally(() => setLoading(false))
  }, [scope, page])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Documents</h1>
          <p className="text-sm text-muted-foreground">Browse and manage your document library</p>
        </div>
        <Link to="/upload" className={cn(buttonVariants(), 'gap-1.5')}>
          <Upload className="size-4" />
          Upload
        </Link>
      </div>

      <div className="flex gap-1 rounded-lg border border-border bg-card p-1 w-fit">
        {(['mine', 'all'] as const).map((s) => (
          <button
            key={s}
            onClick={() => {
              setScope(s)
              setPage(0)
            }}
            className={cn(
              'rounded-md px-3 py-1 text-sm font-medium transition-colors',
              scope === s ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {s === 'mine' ? 'My documents' : 'All documents'}
          </button>
        ))}
      </div>

      <div className="overflow-hidden rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-muted/40 text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-2.5 font-medium">Title</th>
              <th className="px-4 py-2.5 font-medium">Type</th>
              <th className="px-4 py-2.5 font-medium">Status</th>
              <th className="px-4 py-2.5 font-medium">Category</th>
              <th className="px-4 py-2.5 font-medium">Owner</th>
              <th className="px-4 py-2.5 font-medium">Uploaded</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">
                  Loading…
                </td>
              </tr>
            )}
            {!loading && data?.content.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">
                  No documents yet.
                </td>
              </tr>
            )}
            {!loading &&
              data?.content.map((doc) => (
                <tr key={doc.id} className="border-b border-border last:border-0 hover:bg-muted/30">
                  <td className="px-4 py-2.5">
                    <Link to={`/documents/${doc.id}`} className="font-medium text-primary hover:underline">
                      {doc.title}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5 text-muted-foreground">{doc.documentType}</td>
                  <td className="px-4 py-2.5">
                    <StatusBadge status={doc.status} />
                  </td>
                  <td className="px-4 py-2.5 text-muted-foreground">{doc.categoryName}</td>
                  <td className="px-4 py-2.5 text-muted-foreground">{doc.ownerUsername}</td>
                  <td className="px-4 py-2.5 text-muted-foreground">{new Date(doc.uploadedOn).toLocaleDateString()}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">
            Page {data.page + 1} of {data.totalPages} · {data.totalElements} total
          </span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={data.page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button variant="outline" size="sm" disabled={data.page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
