import { http, unwrap } from './api'

// Use BPM's configured starter cancellation; material status follows its result event.
export const materialLifecycleApi = {
  cancel: async (id: string, reason: string) => unwrap<boolean>(
    await http.delete('/bpm/process-instance/cancel-by-start-user', { data: { id, reason } })
  )
}
