import { useCallback, useRef, useState } from 'react'
import { createIdempotencyKey } from './idempotency'

type SubmissionContext = {
  idempotencyKey: string
  complete: () => void
}

export function createSubmissionGate() {
  let locked = false
  let idempotencyKey = createIdempotencyKey()
  return {
    get key() { return idempotencyKey },
    get locked() { return locked },
    resetIntent() { if (!locked) idempotencyKey = createIdempotencyKey() },
    async run(task: (context: SubmissionContext) => Promise<void>) {
      if (locked) return false
      locked = true
      let completed = false
      try {
        await task({ idempotencyKey, complete: () => { completed = true } })
        if (completed) idempotencyKey = createIdempotencyKey()
        return true
      } finally {
        locked = false
      }
    }
  }
}

export function useSubmissionGuard() {
  const [submitting, setSubmitting] = useState(false)
  const gateRef = useRef<ReturnType<typeof createSubmissionGate>>(undefined)
  if (!gateRef.current) gateRef.current = createSubmissionGate()

  const resetIntent = useCallback(() => {
    gateRef.current!.resetIntent()
  }, [])

  const run = useCallback(async (task: (context: SubmissionContext) => Promise<void>) => {
    if (gateRef.current!.locked) return false
    setSubmitting(true)
    try {
      return await gateRef.current!.run(task)
    } finally {
      setSubmitting(false)
    }
  }, [])

  return { submitting, run, resetIntent }
}
