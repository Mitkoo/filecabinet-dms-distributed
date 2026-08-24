import { FileText, ExternalLink } from 'lucide-react'
import { PdfViewer } from './PdfViewer'
import type { FieldBox } from '@/lib/types'

export function PdfPane({
  fileUrl,
  fileName,
  page,
  highlight,
}: {
  fileUrl: string
  fileName: string
  page: number
  highlight: FieldBox | null
}) {
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-xl border border-border bg-muted/30">
      <div className="flex shrink-0 items-center justify-between gap-3 border-b border-border bg-card px-3 py-2.5">
        <span className="inline-flex items-center gap-1.5 text-sm font-medium">
          <FileText className="size-4 text-muted-foreground" />
          Source document
        </span>
        <div className="flex items-center gap-3">
          <span className="hidden max-w-[220px] truncate font-mono text-xs text-muted-foreground sm:inline">{fileName}</span>
          <a
            href={fileUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1.5 rounded-md border border-input bg-card px-2 py-1 text-xs font-medium text-foreground shadow-sm transition hover:border-ring hover:bg-accent"
          >
            <ExternalLink className="size-3.5" />
            Open
          </a>
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-auto bg-neutral-100 p-4 dark:bg-neutral-800/60">
        <div className="mx-auto w-full max-w-[1100px]">
          <PdfViewer fileUrl={fileUrl} page={page} highlight={highlight} />
        </div>
      </div>
    </div>
  )
}
