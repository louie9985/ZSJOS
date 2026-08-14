import { useCallback, useRef, useState } from 'react'

type SubmissionContext = {
  idempotencyKey: string
  complete: () => void
}

export function createSubmissionGate() {
  let locked = false
  let idempotencyKey = crypto.randomUUID()
  return {
    get key() { return idempotencyKey },
    get locked() { return locked },
    resetIntent() { if (!locked) idempotencyKey = crypto.randomUUID() },
    async run(task: (context: SubmissionContext) => Promise<void>) {
      if (locked) return false
      locked = true
      let completed = false
      try {
        await task({ idempotencyKey, complete: () => { completed = true } })
        if (completed) idempotencyKey = crypto.randomUUID()
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
