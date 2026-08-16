const TOKEN_KEY = 'h5_access_token'
const REFRESH_TOKEN_KEY = 'h5_refresh_token'
const TENANT_KEY = 'h5_tenant_id'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function removeRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export function getTenantId(): string {
  return localStorage.getItem(TENANT_KEY) || import.meta.env.VITE_APP_TENANT_ID || '1'
}

export function setTenantId(id: string): void {
  localStorage.setItem(TENANT_KEY, id)
}
