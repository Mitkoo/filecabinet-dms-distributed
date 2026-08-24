import { useState } from 'react'
import { api } from '@/lib/api'
import type { FieldBox, LineItem } from '@/lib/types'
import { cn, formatMoney } from '@/lib/utils'

const NUMERIC = new Set<keyof LineItem>(['quantity', 'unitPrice', 'vatRatePercent', 'totalAmount', 'lineNumber'])
const CELL =
  'w-full rounded border border-transparent bg-transparent px-1 py-0.5 hover:border-input focus:border-ring focus:outline-none focus:ring-1 focus:ring-ring/40'

export function LineItemsEditor({
  documentId,
  items,
  onHover,
  onRefresh,
}: {
  documentId: string
  items: LineItem[]
  onHover: (box: FieldBox | null) => void
  onRefresh: () => void
}) {
  const [drafts, setDrafts] = useState<Record<string, Partial<LineItem>>>({})
  const [focusedCell, setFocusedCell] = useState<string | null>(null)

  function value(item: LineItem, key: keyof LineItem): string {
    const draft = drafts[item.id]
    const current =
      draft && key in draft
        ? (draft as Record<string, unknown>)[key]
        : (item as unknown as Record<string, unknown>)[key]
    return current == null ? '' : String(current)
  }

  function edit(item: LineItem, key: keyof LineItem, raw: string) {
    const parsed = NUMERIC.has(key) ? (raw === '' ? null : Number(raw)) : raw
    setDrafts((prev) => ({ ...prev, [item.id]: { ...prev[item.id], [key]: parsed } }))
  }

  function moneyValue(item: LineItem, key: keyof LineItem): string {
    const raw = value(item, key)
    return focusedCell === `${item.id}:${key}` ? raw : formatMoney(raw)
  }

  async function save(item: LineItem) {
    const draft = drafts[item.id]
    if (!draft) return
    const merged = { ...item, ...draft }
    await api.put(`/api/documents/${documentId}/extraction/line-items/${item.id}`, {
      lineNumber: merged.lineNumber,
      description: merged.description,
      quantity: merged.quantity,
      unitPrice: merged.unitPrice,
      vatRatePercent: merged.vatRatePercent,
      totalAmount: merged.totalAmount,
      category: merged.category,
    })
    setDrafts((prev) => {
      const next = { ...prev }
      delete next[item.id]
      return next
    })
    onRefresh()
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="border-b border-border text-left text-muted-foreground">
          <tr>
            <th className="px-2 py-1.5 font-medium">#</th>
            <th className="px-2 py-1.5 font-medium">Description</th>
            <th className="px-2 py-1.5 text-right font-medium">Qty</th>
            <th className="px-2 py-1.5 text-right font-medium">Unit price</th>
            <th className="px-2 py-1.5 text-right font-medium">VAT %</th>
            <th className="px-2 py-1.5 text-right font-medium">Total</th>
            <th className="px-2 py-1.5 font-medium">Category</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr
              key={item.id}
              className="border-b border-border last:border-0 hover:bg-muted/30"
              onMouseEnter={() => onHover(item.box)}
              onMouseLeave={() => onHover(null)}
            >
              <td className="px-1 py-0.5 text-xs text-muted-foreground">
                {item.box ? <span className="font-medium text-primary">p{item.box.page}</span> : (item.lineNumber ?? '—')}
              </td>
              <td className="px-1 py-0.5 min-w-[16rem]">
                <input className={CELL} value={value(item, 'description')}
                  onChange={(e) => edit(item, 'description', e.target.value)} onBlur={() => save(item)} />
              </td>
              <td className="px-1 py-0.5">
                <input className={cn(CELL, 'text-right tabular-nums')} value={value(item, 'quantity')}
                  onChange={(e) => edit(item, 'quantity', e.target.value)} onBlur={() => save(item)} />
              </td>
              <td className="px-1 py-0.5">
                <input className={cn(CELL, 'text-right tabular-nums')} value={moneyValue(item, 'unitPrice')}
                  onFocus={() => setFocusedCell(`${item.id}:unitPrice`)}
                  onChange={(e) => edit(item, 'unitPrice', e.target.value)}
                  onBlur={() => { setFocusedCell(null); save(item) }} />
              </td>
              <td className="px-1 py-0.5">
                <input className={cn(CELL, 'text-right tabular-nums')} value={value(item, 'vatRatePercent')}
                  onChange={(e) => edit(item, 'vatRatePercent', e.target.value)} onBlur={() => save(item)} />
              </td>
              <td className="px-1 py-0.5">
                <input className={cn(CELL, 'text-right font-medium tabular-nums')} value={moneyValue(item, 'totalAmount')}
                  onFocus={() => setFocusedCell(`${item.id}:totalAmount`)}
                  onChange={(e) => edit(item, 'totalAmount', e.target.value)}
                  onBlur={() => { setFocusedCell(null); save(item) }} />
              </td>
              <td className="px-1 py-0.5 min-w-[10rem]">
                <input className={CELL} value={value(item, 'category')}
                  onChange={(e) => edit(item, 'category', e.target.value)} onBlur={() => save(item)} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
