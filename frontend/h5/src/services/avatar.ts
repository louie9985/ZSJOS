import type { AvatarOptions } from '@/vendor/avatar-kit/index.mjs'

interface AvatarWorkerResponse {
  id: number
  dataUri?: string
  error?: string
}

const CACHE_LIMIT = 300
const cache = new Map<string, string>()
const inFlight = new Map<string, Promise<string>>()
const pending = new Map<number, { resolve: (value: string) => void; reject: (cause: Error) => void }>()

let worker: Worker | undefined
let workerUnavailable = false
let requestId = 0

function cacheKey(options: AvatarOptions) {
  return JSON.stringify([
    options.namespace || 'app',
    String(options.seed),
    options.variant || 'geometric',
    options.theme || 'light',
    options.size || 40
  ])
}

function saveCache(key: string, dataUri: string) {
  cache.delete(key)
  cache.set(key, dataUri)
  if (cache.size > CACHE_LIMIT) {
    const oldestKey = cache.keys().next().value
    if (oldestKey) cache.delete(oldestKey)
  }
}

function rejectPending(cause: Error) {
  pending.forEach(request => request.reject(cause))
  pending.clear()
}

function getWorker() {
  if (workerUnavailable) return undefined
  if (worker) return worker
  try {
    worker = new Worker(new URL('../workers/avatar.worker.ts', import.meta.url), { type: 'module' })
    worker.addEventListener('message', (event: MessageEvent<AvatarWorkerResponse>) => {
      const request = pending.get(event.data.id)
      if (!request) return
      pending.delete(event.data.id)
      if (event.data.dataUri) request.resolve(event.data.dataUri)
      else request.reject(new Error(event.data.error || '头像生成失败'))
    })
    worker.addEventListener('error', () => {
      workerUnavailable = true
      worker?.terminate()
      worker = undefined
      rejectPending(new Error('头像生成线程不可用'))
    })
    return worker
  } catch {
    workerUnavailable = true
    return undefined
  }
}

function generateInWorker(options: AvatarOptions) {
  const activeWorker = getWorker()
  if (!activeWorker) return Promise.reject(new Error('头像生成线程不可用'))
  return new Promise<string>((resolve, reject) => {
    const id = ++requestId
    pending.set(id, { resolve, reject })
    activeWorker.postMessage({ id, options })
  })
}

async function generateOnMainThread(options: AvatarOptions) {
  await new Promise<void>(resolve => window.setTimeout(resolve, 0))
  const { createAvatar } = await import('@/vendor/avatar-kit/index.mjs')
  return createAvatar(options).dataUri
}

export function loadGeneratedAvatar(options: AvatarOptions) {
  const key = cacheKey(options)
  const cached = cache.get(key)
  if (cached) {
    cache.delete(key)
    cache.set(key, cached)
    return Promise.resolve(cached)
  }
  const existing = inFlight.get(key)
  if (existing) return existing

  const request = generateInWorker(options)
    .catch(() => generateOnMainThread(options))
    .then((dataUri) => {
      saveCache(key, dataUri)
      return dataUri
    })
    .finally(() => inFlight.delete(key))
  inFlight.set(key, request)
  return request
}
