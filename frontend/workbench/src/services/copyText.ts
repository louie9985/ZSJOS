/** Keep HTTP workbench deployments usable where the async Clipboard API is unavailable. */
export async function copyText(value: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value)
      return
    } catch {
      // Embedded browsers may deny the async API but still allow a user-initiated copy.
    }
  }

  const focused = document.activeElement
  const selection = document.getSelection()
  const ranges = selection
    ? Array.from({ length: selection.rangeCount }, (_, index) => selection.getRangeAt(index).cloneRange())
    : []
  const inputSelection = focused instanceof HTMLInputElement || focused instanceof HTMLTextAreaElement
    ? { start: focused.selectionStart, end: focused.selectionEnd, direction: focused.selectionDirection }
    : undefined
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.readOnly = true
  textarea.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0;'
  // Stay inside a modal/dialog focus boundary when the trigger belongs to one.
  const container = focused instanceof Element ? focused.closest('[role="dialog"], dialog') : null
  ;(container ?? document.body).appendChild(textarea)
  try {
    textarea.focus({ preventScroll: true })
    textarea.select()
    if (!document.execCommand('copy')) throw new Error('Clipboard copy was rejected')
  } finally {
    textarea.remove()
    if (focused instanceof HTMLElement) focused.focus({ preventScroll: true })
    if (selection) {
      selection.removeAllRanges()
      ranges.forEach((range) => selection.addRange(range))
    }
    if (inputSelection?.start != null && inputSelection.end != null
      && (focused instanceof HTMLInputElement || focused instanceof HTMLTextAreaElement)) {
      focused.setSelectionRange(inputSelection.start, inputSelection.end, inputSelection.direction ?? undefined)
    }
  }
}
