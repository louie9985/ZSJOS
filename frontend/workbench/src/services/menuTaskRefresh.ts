export const MENU_TASK_INVALIDATED_EVENT = 'zsjos-workbench-task-invalidated'

// Dispatch only after an authenticated command succeeds, never while a write is still pending.
export function invalidateMenuTasks<T>(result: T): T {
  window.dispatchEvent(new Event(MENU_TASK_INVALIDATED_EVENT))
  return result
}
