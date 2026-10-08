export const PERFORMANCE_CACHE_TTL = 600_000
const MAX_RESULTS = 32
type Entry = { data: unknown; updatedAt: number }
type Pending = { controller: AbortController; promise: Promise<Entry> }

export function performanceQueryKey(resource: string, query: object): string {
  return JSON.stringify([resource, Object.entries(query).filter(([, value]) => value !== undefined)
    .sort(([a], [b]) => a.localeCompare(b))])
}

/** Owned by one mounted statistics page. Identity changes destroy the owner and its requests. */
export class PerformanceCache {
  private results = new Map<string, Entry>()
  private pending = new Map<string, Pending>()
  private listeners = new Set<() => void>()
  private revision = 0
  generation = 0
  blockedError = ''
  constructor(private now: () => number = Date.now) {}
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  snapshot = () => this.revision
  private emit() { this.revision++; this.listeners.forEach(listener => listener()) }
  peek(key: string): Entry | undefined {
    const entry = this.results.get(key)
    return entry && this.now() - entry.updatedAt < PERFORMANCE_CACHE_TTL ? entry : undefined
  }
  load<T>(key: string, loader: (signal: AbortSignal) => Promise<T>): Promise<Entry> {
    const cached = this.peek(key)
    if (cached) {
      this.results.delete(key); this.results.set(key, cached)
      return Promise.resolve(cached)
    }
    const pending = this.pending.get(key)
    if (pending) return pending.promise
    const controller = new AbortController(), generation = this.generation
    const promise = Promise.resolve().then(() => loader(controller.signal)).then(data => {
      if (controller.signal.aborted || generation !== this.generation) throw new DOMException('请求已取消', 'AbortError')
      const entry = { data, updatedAt: this.now() }
      this.results.delete(key); this.results.set(key, entry)
      while (this.results.size > MAX_RESULTS) this.results.delete(this.results.keys().next().value!)
      this.emit()
      return entry
    }).catch((error: unknown) => {
      if (controller.signal.aborted || generation !== this.generation) throw new DOMException('请求已取消', 'AbortError')
      throw error
    }).finally(() => { if (this.pending.get(key)?.controller === controller) this.pending.delete(key) })
    this.pending.set(key, { controller, promise })
    return promise
  }
  invalidate(key: string) {
    this.pending.get(key)?.controller.abort(); this.pending.delete(key); this.results.delete(key)
    this.emit()
  }
  clear(blockedError = '') {
    this.blockedError = blockedError
    this.generation++
    this.pending.forEach(request => request.controller.abort()); this.pending.clear(); this.results.clear()
    this.emit()
  }
}
