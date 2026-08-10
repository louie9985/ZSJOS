import { JSEncrypt } from 'jsencrypt'
import { STORAGE_KEYS } from '../constants'

const LOGIN_FORM_CACHE_VERSION = 1
export const LOGIN_FORM_CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000

const publicKey =
  'MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdH\n' +
  'nzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ=='

const privateKey =
  'MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAqhHyZfSsYourNxaY\n' +
  '7Nt+PrgrxkiA50efORdI5U5lsW79MmFnusUA355oaSXcLhu5xxB38SMSyP2KvuKN\n' +
  'PuH3owIDAQABAkAfoiLyL+Z4lf4Myxk6xUDgLaWGximj20CUf+5BKKnlrK+Ed8gA\n' +
  'kM0HqoTt2UZwA5E2MzS4EI2gjfQhz5X28uqxAiEA3wNFxfrCZlSZHb0gn2zDpWow\n' +
  'cSxQAgiCstxGUoOqlW8CIQDDOerGKH5OmCJ4Z21v+F25WaHYPxCFMvwxpcw99Ecv\n' +
  'DQIgIdhDTIqD2jfYjPTY8Jj3EDGPbH2HHuffvflECt3Ek60CIQCFRlCkHpi7hthh\n' +
  'YhovyloRYsM+IS9h/0BzlEAuO0ktMQIgSPT3aFAgJYwKpqRYKlLDVcflZFCKY7u3\n' +
  'UP8iWi1Qw0Y='

interface LoginFormCacheEntry {
  version: number
  username: string
  encryptedPassword: string
  expiresAt: number
}

export interface RememberedLoginForm {
  username: string
  password: string
}

const getDefaultStorage = (): Storage => window.localStorage

export const clearLoginFormCache = (storage: Storage = getDefaultStorage()) => {
  try {
    storage.removeItem(STORAGE_KEYS.LOGIN_FORM)
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
}

const isValidEntry = (value: unknown): value is LoginFormCacheEntry => {
  if (!value || typeof value !== 'object') return false
  const entry = value as Partial<LoginFormCacheEntry>
  return entry.version === LOGIN_FORM_CACHE_VERSION
    && typeof entry.username === 'string'
    && entry.username.length > 0
    && typeof entry.encryptedPassword === 'string'
    && entry.encryptedPassword.length > 0
    && typeof entry.expiresAt === 'number'
    && Number.isFinite(entry.expiresAt)
}

export const loadLoginFormCache = (
  storage: Storage = getDefaultStorage(),
  now = Date.now()
): RememberedLoginForm | null => {
  try {
    const raw = storage.getItem(STORAGE_KEYS.LOGIN_FORM)
    if (!raw) return null
    const entry: unknown = JSON.parse(raw)
    if (!isValidEntry(entry) || entry.expiresAt <= now) {
      clearLoginFormCache(storage)
      return null
    }

    const decryptor = new JSEncrypt()
    decryptor.setPrivateKey(privateKey)
    const password = decryptor.decrypt(entry.encryptedPassword)
    if (!password) {
      clearLoginFormCache(storage)
      return null
    }
    return { username: entry.username, password }
  } catch {
    clearLoginFormCache(storage)
    return null
  }
}

export const saveLoginFormCache = (
  username: string,
  password: string,
  storage: Storage = getDefaultStorage(),
  now = Date.now()
) => {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  const encryptedPassword = encryptor.encrypt(password)
  if (!encryptedPassword) throw new Error('登录密码缓存加密失败')

  const entry: LoginFormCacheEntry = {
    version: LOGIN_FORM_CACHE_VERSION,
    username,
    encryptedPassword,
    expiresAt: now + LOGIN_FORM_CACHE_TTL_MS
  }
  storage.setItem(STORAGE_KEYS.LOGIN_FORM, JSON.stringify(entry))
}
