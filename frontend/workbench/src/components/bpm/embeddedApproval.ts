/** The task host owns selection and leave protection; business views own all decisions. */
export interface EmbeddedApproval {
  query: Record<string, string | number | boolean>
  taskId: string
  done: boolean
  onState: (dirty: boolean, busy: boolean) => void
  onComplete: () => void
}
