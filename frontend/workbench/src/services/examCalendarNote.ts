import { http, unwrap } from './api'

export type ExamNoteImage = { fileId: number | string; url: string }
export type ExamCalendarNote = { content: string; version: number; images: ExamNoteImage[] }
const ROOT = '/zsjos/exam-calendar/note'
export const EXAM_NOTE_CONFLICT = 1900018021
export const examCalendarNote = {
  get: async () => unwrap<ExamCalendarNote>(await http.get(ROOT)),
  save: async (content: string, version: number) => unwrap<ExamCalendarNote>(await http.put(ROOT, { content, version })),
  upload: async (file: File) => {
    const form = new FormData(); form.append('file', file)
    return unwrap<ExamNoteImage>(await http.post(`${ROOT}/image`, form))
  }
}

export function noteDisplayHtml(content: string, images: ExamNoteImage[]) {
  const doc = new DOMParser().parseFromString(content, 'text/html')
  const urls = new Map(images.map(image => [`exam-note-image:${image.fileId}`, image.url]))
  doc.querySelectorAll('img').forEach(image => {
    const url = urls.get(image.getAttribute('src') || '')
    if (url) image.setAttribute('src', url)
    else image.remove()
  })
  return doc.body.innerHTML
}

export function noteSaveHtml(content: string, images: ExamNoteImage[]) {
  if (new TextEncoder().encode(content).length > 204800) throw new Error('说明HTML不能超过200KB')
  const doc = new DOMParser().parseFromString(content, 'text/html')
  const urls = new Map(images.map(image => [image.url, `exam-note-image:${image.fileId}`]))
  doc.querySelectorAll('img').forEach(image => {
    const reference = urls.get(image.getAttribute('src') || '')
    if (!reference) throw new Error('正文包含未上传的图片，请使用图片上传或粘贴图片文件')
    image.setAttribute('src', reference)
  })
  if (Array.from(doc.body.textContent || '').length > 5000 || doc.images.length > 20) throw new Error('说明最多5000字、20张图片')
  if (new TextEncoder().encode(doc.body.innerHTML).length > 204800) throw new Error('说明HTML不能超过200KB')
  return doc.body.innerHTML
}
