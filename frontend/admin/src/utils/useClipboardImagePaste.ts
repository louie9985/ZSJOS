import { clipboardImageFiles } from './clipboardImage'

export function useClipboardImagePaste(onFile: (file: File) => void, canPaste: () => boolean = () => true) {
  return (event: ClipboardEvent) => {
    if (!canPaste()) return
    const file = clipboardImageFiles(event)[0]
    if (!file) return
    event.preventDefault()
    onFile(file)
  }
}
