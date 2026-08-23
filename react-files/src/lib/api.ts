const TOKEN_KEY = 'filecabinet.token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {}
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function parseError(response: Response): Promise<string> {
  try {
    const body = await response.json()
    return body.message || response.statusText
  } catch {
    return response.statusText
  }
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  const response = await fetch(url, { method, headers, body: payload })
  if (response.status === 401) {
    setToken(null)
  }
  if (!response.ok) {
    throw new ApiError(response.status, await parseError(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

export const api = {
  get: <T>(url: string) => request<T>('GET', url),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body),
  put: <T>(url: string, body?: unknown) => request<T>('PUT', url, body),
  del: <T>(url: string) => request<T>('DELETE', url),
  upload: <T>(url: string, form: FormData) => request<T>('POST', url, form),
}
