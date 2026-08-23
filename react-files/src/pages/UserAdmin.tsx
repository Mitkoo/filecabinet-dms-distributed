import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { UserSummary } from '@/lib/types'
import { useAuth } from '@/auth/AuthContext'
import { Select } from '@/components/ui/controls'

const ROLES = ['CLERK', 'MANAGER', 'ADMIN', 'ACCOUNTANT', 'BUYER', 'SALES_REP', 'DEMO']

export function UserAdmin() {
  const { user } = useAuth()
  const [users, setUsers] = useState<UserSummary[]>([])
  const [error, setError] = useState<string | null>(null)

  const load = () => api.get<UserSummary[]>('/api/users').then(setUsers)
  useEffect(() => {
    load()
  }, [])

  async function changeRole(id: string, role: string) {
    setError(null)
    try {
      await api.put(`/api/users/${id}/role`, { role })
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change role')
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Users</h1>
        <p className="text-sm text-muted-foreground">Manage roles across the organisation</p>
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="overflow-hidden rounded-xl border border-border bg-card">
        <table className="w-full text-sm">
          <thead className="border-b border-border bg-muted/40 text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-2.5 font-medium">Username</th>
              <th className="px-4 py-2.5 font-medium">Email</th>
              <th className="px-4 py-2.5 font-medium">Full name</th>
              <th className="px-4 py-2.5 font-medium">Role</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-b border-border last:border-0">
                <td className="px-4 py-2.5 font-medium">{u.username}</td>
                <td className="px-4 py-2.5 text-muted-foreground">{u.email}</td>
                <td className="px-4 py-2.5 text-muted-foreground">{u.fullName ?? '—'}</td>
                <td className="px-4 py-2.5">
                  <Select
                    className="w-40"
                    value={u.role}
                    disabled={u.username === user?.username}
                    onChange={(e) => changeRole(u.id, e.target.value)}
                  >
                    {ROLES.map((r) => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </Select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
