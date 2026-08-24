import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setToken, getToken } from '@/lib/api'
import type { AuthResponse } from '@/lib/types'

interface AuthUser {
  username: string
  role: string
}

interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isAdmin: boolean
  login: (username: string, password: string) => Promise<void>
  demoLogin: () => Promise<void>
  register: (username: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const USER_KEY = 'filecabinet.user'
const AuthContext = createContext<AuthContextValue | null>(null)

function loadUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => (getToken() ? loadUser() : null))

  useEffect(() => {
    try {
      if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
      else localStorage.removeItem(USER_KEY)
    } catch {}
  }, [user])

  function apply(response: AuthResponse) {
    setToken(response.token)
    setUser({ username: response.username, role: response.role })
  }

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: !!user,
      isAdmin: user?.role === 'ADMIN',
      login: async (username, password) => {
        apply(await api.post<AuthResponse>('/api/auth/login', { username, password }))
      },
      demoLogin: async () => {
        apply(await api.post<AuthResponse>('/api/auth/demo'))
      },
      register: async (username, email, password) => {
        apply(await api.post<AuthResponse>('/api/auth/register', { username, email, password }))
      },
      logout: () => {
        setToken(null)
        setUser(null)
      },
    }),
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
