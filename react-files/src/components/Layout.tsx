import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { FileStack, LayoutDashboard, Inbox, Upload, Users, UserCircle, LogOut } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/utils'

const links = [
  { to: '/documents', label: 'Documents', icon: LayoutDashboard },
  { to: '/upload', label: 'Upload', icon: Upload },
  { to: '/inbox', label: 'Inbox', icon: Inbox },
]

export function Layout() {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-10 border-b border-border bg-card/80 backdrop-blur">
        <div className="mx-auto flex h-14 w-full items-center gap-6 px-4 sm:px-6 lg:px-8">
          <NavLink to="/documents" className="flex items-center gap-2 font-semibold">
            <span className="grid size-7 place-items-center rounded-lg bg-primary text-primary-foreground">
              <FileStack className="size-4" />
            </span>
            FileCabinet
          </NavLink>

          <nav className="flex items-center gap-1">
            {links.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
                    isActive ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                <Icon className="size-4" />
                {label}
              </NavLink>
            ))}
            {isAdmin && (
              <NavLink
                to="/users"
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
                    isActive ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                <Users className="size-4" />
                Users
              </NavLink>
            )}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <NavLink to="/profile" className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
              <UserCircle className="size-5" />
              <span className="font-medium text-foreground">{user?.username}</span>
              <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{user?.role}</span>
            </NavLink>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              <LogOut className="size-4" />
              Logout
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full px-4 py-6 sm:px-6 lg:px-8">
        <Outlet />
      </main>
    </div>
  )
}
