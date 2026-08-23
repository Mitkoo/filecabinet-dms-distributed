import { Link } from 'react-router-dom'
import {
  FileStack,
  ShieldCheck,
  Workflow,
  ScanText,
  Archive,
  Upload,
  PenLine,
  CheckCircle2,
  ArrowRight,
} from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { buttonVariants } from '@/components/ui/Button'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { cn } from '@/lib/utils'

const features = [
  {
    icon: Archive,
    title: 'A digital archive, not a drawer',
    body: 'Every invoice, contract and receipt lives in one searchable place instead of a folder or a filing cabinet.',
  },
  {
    icon: ScanText,
    title: 'Automatic extraction',
    body: 'Upload an invoice and the extraction service reads it and pulls out the fields and line items for you to review.',
  },
  {
    icon: Workflow,
    title: 'Paperless approvals',
    body: 'Swap routing slips and wet signatures for an ordered review — buyer, then manager, then accountant.',
  },
  {
    icon: ShieldCheck,
    title: 'Secure by design',
    body: 'Stateless JWT security and role-based access keep every record visible to the right people only.',
  },
]

const steps = [
  { icon: Upload, title: 'Upload', body: 'Add an invoice, contract or receipt instead of filing it away on paper.' },
  { icon: PenLine, title: 'Review', body: 'The service extracts the fields — check them against the document and correct anything.' },
  { icon: CheckCircle2, title: 'Approve', body: 'Send it through an ordered review workflow until it is approved and paid.' },
]

export function Home() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-10 border-b border-border bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <span className="flex items-center gap-2 font-semibold">
            <span className="grid size-7 place-items-center rounded-lg bg-primary text-primary-foreground">
              <FileStack className="size-4" />
            </span>
            FileCabinet
          </span>
          <nav className="ml-auto flex items-center gap-2">
            <a href="#features" className="hidden rounded-lg px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground sm:block">
              Features
            </a>
            <a href="#how" className="hidden rounded-lg px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground sm:block">
              How it works
            </a>
            {isAuthenticated ? (
              <Link to="/documents" className={cn(buttonVariants({ size: 'sm' }), 'gap-1.5')}>
                Go to app
                <ArrowRight className="size-4" />
              </Link>
            ) : (
              <>
                <Link to="/login" className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }))}>
                  Sign in
                </Link>
                <Link to="/register" className={cn(buttonVariants({ size: 'sm' }))}>
                  Create account
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 sm:px-6">
        <section className="grid gap-10 py-16 lg:grid-cols-2 lg:items-center lg:py-24">
          <div>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">
              <span className="size-1.5 rounded-full bg-primary" />
              Distributed document management
            </span>
            <h1 className="mt-5 text-4xl font-semibold tracking-tight sm:text-5xl">
              Retire the filing cabinet. Go fully digital.
            </h1>
            <p className="mt-4 max-w-xl text-lg text-muted-foreground">
              FileCabinet turns invoices, contracts and receipts into structured digital records — extracted,
              reviewed and searchable the moment they are approved.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to={isAuthenticated ? '/documents' : '/register'} className={cn(buttonVariants({ size: 'lg' }), 'gap-1.5')}>
                {isAuthenticated ? 'Go to your documents' : 'Get started'}
                <ArrowRight className="size-4" />
              </Link>
              {!isAuthenticated && (
                <Link to="/login" className={cn(buttonVariants({ variant: 'outline', size: 'lg' }))}>
                  Sign in
                </Link>
              )}
            </div>
            <ul className="mt-8 flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
              <li className="flex items-center gap-1.5"><ShieldCheck className="size-4 text-success" /> Secure JWT access</li>
              <li className="flex items-center gap-1.5"><ScanText className="size-4 text-success" /> Automatic extraction</li>
              <li className="flex items-center gap-1.5"><Workflow className="size-4 text-success" /> Ordered approvals</li>
            </ul>
          </div>

          <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
            <div className="flex items-center justify-between border-b border-border pb-3">
              <span className="text-sm font-semibold">My documents</span>
              <span className="rounded-full bg-warning/15 px-2 py-0.5 text-xs font-medium text-warning-foreground">3 need review</span>
            </div>
            <ul className="divide-y divide-border">
              {[
                { t: 'Q3 Vendor Invoice — Acme Corp', s: 'APPROVED' },
                { t: 'Lease Agreement — Downtown Office', s: 'STRUCTURED' },
                { t: 'Consulting Agreement — Nova LLC', s: 'REJECTED' },
                { t: 'Receipt — Office Supplies', s: 'UPLOADED' },
              ].map((d) => (
                <li key={d.t} className="flex items-center justify-between gap-3 py-3">
                  <span className="truncate text-sm">{d.t}</span>
                  <StatusBadge status={d.s} />
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="features" className="border-t border-border py-16">
          <h2 className="text-2xl font-semibold tracking-tight">Everything you need to go paperless</h2>
          <p className="mt-2 text-muted-foreground">From a drawer full of paper to a digital record, without the guesswork.</p>
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {features.map(({ icon: Icon, title, body }) => (
              <div key={title} className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <span className="grid size-9 place-items-center rounded-lg bg-accent text-accent-foreground">
                  <Icon className="size-5" />
                </span>
                <h3 className="mt-4 font-medium">{title}</h3>
                <p className="mt-1.5 text-sm text-muted-foreground">{body}</p>
              </div>
            ))}
          </div>
        </section>

        <section id="how" className="border-t border-border py-16">
          <h2 className="text-2xl font-semibold tracking-tight">From paper to a digital record, in three steps</h2>
          <div className="mt-8 grid gap-4 md:grid-cols-3">
            {steps.map(({ icon: Icon, title, body }, i) => (
              <div key={title} className="relative rounded-xl border border-border bg-card p-6 shadow-sm">
                <span className="absolute right-5 top-5 font-mono text-3xl font-semibold text-muted/60">{i + 1}</span>
                <span className="grid size-10 place-items-center rounded-lg bg-primary text-primary-foreground">
                  <Icon className="size-5" />
                </span>
                <h3 className="mt-4 text-lg font-medium">{title}</h3>
                <p className="mt-1.5 text-sm text-muted-foreground">{body}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="border-t border-border py-16">
          <div className="rounded-2xl border border-border bg-primary/5 px-6 py-10 text-center">
            <h2 className="text-2xl font-semibold tracking-tight">Ready to go paperless?</h2>
            <p className="mx-auto mt-2 max-w-md text-muted-foreground">
              Create an account and turn your first document into a structured, reviewable record.
            </p>
            <div className="mt-6 flex justify-center gap-3">
              <Link to={isAuthenticated ? '/documents' : '/register'} className={cn(buttonVariants({ size: 'lg' }), 'gap-1.5')}>
                {isAuthenticated ? 'Open the app' : 'Create your account'}
                <ArrowRight className="size-4" />
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-border py-8">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-2 px-4 text-sm text-muted-foreground sm:flex-row sm:px-6">
          <span className="flex items-center gap-2">
            <FileStack className="size-4" />
            FileCabinet DMS
          </span>
          <span>Distributed document management</span>
        </div>
      </footer>
    </div>
  )
}
