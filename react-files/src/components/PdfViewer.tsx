import { useEffect, useRef, useState } from 'react'
import * as pdfjs from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import type { FieldBox } from '@/lib/types'

pdfjs.GlobalWorkerOptions.workerSrc = workerUrl

export function PdfViewer({
  fileUrl,
  page,
  highlight,
}: {
  fileUrl: string
  page: number
  highlight: FieldBox | null
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [size, setSize] = useState<{ w: number; h: number } | null>(null)
  const [scale, setScale] = useState(1)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')

  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    let cancelled = false
    let renderTask: { cancel: () => void; promise: Promise<unknown> } | null = null
    let lastWidth = 0

    async function render(width: number) {
      try {
        setStatus('loading')
        const doc = await pdfjs.getDocument(fileUrl).promise
        if (cancelled) return
        const pdfPage = await doc.getPage(page)
        if (cancelled) return
        const dpr = Math.min(window.devicePixelRatio || 1, 2)
        const base = pdfPage.getViewport({ scale: 1 })
        const cssScale = width / base.width
        const renderViewport = pdfPage.getViewport({ scale: cssScale * dpr })
        const canvas = canvasRef.current
        if (!canvas) return
        const ctx = canvas.getContext('2d')
        if (!ctx) return
        canvas.width = Math.floor(renderViewport.width)
        canvas.height = Math.floor(renderViewport.height)
        renderTask = pdfPage.render({ canvasContext: ctx, viewport: renderViewport })
        await renderTask.promise
        if (cancelled) return
        setScale(cssScale)
        setSize({ w: width, h: base.height * cssScale })
        setStatus('ready')
      } catch (err) {
        if (!cancelled && (err as { name?: string })?.name !== 'RenderingCancelledException') {
          setStatus('error')
        }
      }
    }

    const observer = new ResizeObserver((entries) => {
      const width = Math.floor(entries[0].contentRect.width)
      if (width > 0 && Math.abs(width - lastWidth) > 1) {
        lastWidth = width
        renderTask?.cancel()
        render(width)
      }
    })
    observer.observe(container)
    return () => {
      cancelled = true
      renderTask?.cancel()
      observer.disconnect()
    }
  }, [fileUrl, page])

  const showHighlight = highlight && highlight.page === page && highlight.pageWidth > 0

  return (
    <div ref={containerRef} className="relative w-full">
      <canvas
        ref={canvasRef}
        className="block w-full rounded-md bg-white ring-1 ring-black/5"
        style={size ? { height: size.h } : undefined}
        aria-label="Document PDF"
      />
      {showHighlight && (
        <div
          className="pointer-events-none absolute rounded-sm bg-highlight/30 ring-2 ring-highlight transition-all duration-200"
          style={{
            left: highlight.x * scale,
            top: highlight.y * scale,
            width: highlight.width * scale,
            height: highlight.height * scale,
          }}
        />
      )}
      {status === 'loading' && (
        <p className="absolute inset-x-0 top-4 text-center text-xs text-muted-foreground">Loading PDF…</p>
      )}
      {status === 'error' && (
        <p className="absolute inset-x-0 top-4 text-center text-xs text-destructive">Could not render PDF.</p>
      )}
    </div>
  )
}
