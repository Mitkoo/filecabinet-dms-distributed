import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { Layout } from '@/components/Layout'
import { Home } from '@/pages/Home'
import { Login } from '@/pages/Login'
import { Register } from '@/pages/Register'
import { Documents } from '@/pages/Documents'
import { UploadDocument } from '@/pages/Upload'
import { DocumentDetail } from '@/pages/DocumentDetail'
import { WorkflowInbox } from '@/pages/WorkflowInbox'
import { WorkflowDetail } from '@/pages/WorkflowDetail'
import { Profile } from '@/pages/Profile'
import { UserAdmin } from '@/pages/UserAdmin'
import type { ReactNode } from 'react'

function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth()
  return isAdmin ? <>{children}</> : <Navigate to="/documents" replace />
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/documents" element={<Documents />} />
        <Route path="/upload" element={<UploadDocument />} />
        <Route path="/documents/:id" element={<DocumentDetail />} />
        <Route path="/inbox" element={<WorkflowInbox />} />
        <Route path="/workflows/:id" element={<WorkflowDetail />} />
        <Route path="/profile" element={<Profile />} />
        <Route
          path="/users"
          element={
            <RequireAdmin>
              <UserAdmin />
            </RequireAdmin>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/documents" replace />} />
    </Routes>
  )
}
