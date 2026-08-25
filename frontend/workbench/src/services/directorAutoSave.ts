export type DirectorAutoSaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error' | 'conflict'

export type DirectorAutoSaveState = {
  status: DirectorAutoSaveStatus
  savedAt?: string
  error?: string
}

type SaveJob = {
  sequence: number
  session: number
  idempotencyKey: string
  run: (idempotencyKey: string, session: number) => Promise<void>
  promise?: Promise<void>
  started?: boolean
  cancelled?: boolean
  failure?: unknown
  terminal?: boolean
}

export class DirectorAutoSaveCoordinator {
  private timer?: ReturnType<typeof setTimeout>
  private session = 0
  private sequence = 0
  private savedSequence = 0
  private chain: Promise<void> = Promise.resolve()
  private pending?: SaveJob
  private failed?: SaveJob

  constructor(
    private readonly delayMs: number,
    private readonly onState: (state: DirectorAutoSaveState) => void,
    private readonly keyFactory: () => string = () => crypto.randomUUID(),
    private readonly isTerminalError: (error: unknown) => boolean = () => false
  ) {}

  begin() {
    this.clearTimer()
    this.session += 1
    this.sequence = 0
    this.savedSequence = 0
    // A stale session may still own an HTTP request; keep it in the chain so sessions never write concurrently.
    this.chain = this.chain.catch(() => undefined)
    this.pending = undefined
    this.failed = undefined
    this.onState({ status: 'idle' })
    return this.session
  }

  isCurrent(session: number) {
    return session === this.session
  }

  schedule(run: SaveJob['run']) {
    this.clearTimer()
    if (this.failed?.terminal) {
      this.onState({ status: 'conflict', error: '草稿版本已变化，请重新加载后继续' })
      return
    }
    const job = this.newJob(run)
    this.pending = job
    this.onState({ status: 'dirty' })
    this.timer = setTimeout(() => { void this.enqueue(job).catch(() => undefined) }, this.delayMs)
  }

  async flush() {
    this.clearTimer()
    if (!this.pending || this.pending.sequence <= this.savedSequence) return this.chain
    return this.saveThrough(this.pending)
  }

  async saveNow(run: SaveJob['run']) {
    this.clearTimer()
    this.throwIfTerminalFailure()
    const job = this.newJob(run)
    this.pending = job
    this.onState({ status: 'dirty' })
    return this.saveThrough(job)
  }

  async prepareSubmit() {
    this.clearTimer()
    if (this.pending && !this.pending.started) {
      this.pending.cancelled = true
      this.pending = undefined
    }
    await this.chain
    if (this.failed) throw this.failed.failure
  }

  async retry() {
    if (!this.failed) return Promise.resolve()
    this.throwIfTerminalFailure()
    const failed = this.failed
    await this.enqueue(failed)
    const pending = this.pending
    if (pending && pending.sequence > this.savedSequence) await this.enqueue(pending)
  }

  invalidate() {
    this.clearTimer()
    this.session += 1
    this.pending = undefined
    this.failed = undefined
  }

  dispose() {
    this.invalidate()
  }

  private newJob(run: SaveJob['run']): SaveJob {
    return { sequence: ++this.sequence, session: this.session, idempotencyKey: this.keyFactory(), run }
  }

  private enqueue(job: SaveJob) {
    if (job.promise) return job.promise
    const execute = this.chain.catch(() => undefined).then(async () => {
      if (!this.isCurrent(job.session) || job.cancelled) return
      if (this.failed && this.failed !== job) return
      job.started = true
      this.onState({ status: 'saving' })
      try {
        await job.run(job.idempotencyKey, job.session)
        if (!this.isCurrent(job.session)) return
        this.savedSequence = Math.max(this.savedSequence, job.sequence)
        if (this.pending?.sequence === job.sequence) this.pending = undefined
        if (this.failed?.sequence === job.sequence) this.failed = undefined
        job.failure = undefined
        if (job.sequence === this.sequence) this.onState({ status: 'saved', savedAt: new Date().toISOString() })
      } catch (cause) {
        if (this.isCurrent(job.session)) {
          job.failure = cause
          job.terminal = this.isTerminalError(cause)
          this.failed = job
          throw cause
        }
      }
    })
    job.promise = execute.finally(() => { job.promise = undefined })
    this.chain = job.promise
    return job.promise
  }

  private async saveThrough(job: SaveJob) {
    this.throwIfTerminalFailure()
    const failed = this.failed
    if (failed && failed !== job) await this.enqueue(failed)
    return this.enqueue(job)
  }

  private throwIfTerminalFailure() {
    if (this.failed?.terminal) throw this.failed.failure
  }

  private clearTimer() {
    if (this.timer) clearTimeout(this.timer)
    this.timer = undefined
  }
}
