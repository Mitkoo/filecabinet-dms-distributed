import { useEffect, useState, type FormEvent } from 'react'
import { api } from '@/lib/api'
import type { Profile as ProfileData } from '@/lib/types'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Field, Input } from '@/components/ui/controls'

export function Profile() {
  const [profile, setProfile] = useState<ProfileData | null>(null)
  const [form, setForm] = useState({ fullName: '', phone: '', jobTitle: '', companyName: '', companyAddress: '' })
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.get<ProfileData>('/api/profile/me').then((p) => {
      setProfile(p)
      setForm({
        fullName: p.fullName ?? '',
        phone: p.phone ?? '',
        jobTitle: p.jobTitle ?? '',
        companyName: p.companyName ?? '',
        companyAddress: p.companyAddress ?? '',
      })
    })
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setSaved(false)
    try {
      const updated = await api.put<ProfileData>('/api/profile', form)
      setProfile(updated)
      setSaved(true)
    } finally {
      setBusy(false)
    }
  }

  if (!profile) return <p className="text-muted-foreground">Loading…</p>

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <h1 className="text-2xl font-semibold">Profile</h1>
      <Card>
        <CardHeader title={profile.username} description={`${profile.email} · ${profile.role}`} />
        <CardBody>
          <form onSubmit={save} className="space-y-4">
            <Field label="Full name">
              <Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </Field>
            <div className="grid grid-cols-2 gap-4">
              <Field label="Phone">
                <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </Field>
              <Field label="Job title">
                <Input value={form.jobTitle} onChange={(e) => setForm({ ...form, jobTitle: e.target.value })} />
              </Field>
            </div>
            <Field label="Company">
              <Input value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })} />
            </Field>
            <Field label="Company address">
              <Input value={form.companyAddress} onChange={(e) => setForm({ ...form, companyAddress: e.target.value })} />
            </Field>
            <div className="flex items-center gap-3">
              <Button type="submit" disabled={busy}>
                {busy ? 'Saving…' : 'Save changes'}
              </Button>
              {saved && <span className="text-sm text-success">Saved.</span>}
            </div>
          </form>
        </CardBody>
      </Card>
    </div>
  )
}
