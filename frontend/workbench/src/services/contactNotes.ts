import { http, unwrap } from './api'
import type { TimestampValue } from './time'

export type NoteSubject = 'lead' | 'student'
export type NotePage = {
  list: { id: number; authorName: string; createdAt: TimestampValue; content: string }[]
  total: number
  canAppend: boolean
}

export async function getNotes(subject: NoteSubject, id: number, pageNo: number) {
  return unwrap<NotePage>(await http.get(`/zsjos/contact-note/${subject}/${id}`, { params: { pageNo, pageSize: 10 } }))
}

export async function appendNote(subject: NoteSubject, id: number, content: string, requestKey: string) {
  return unwrap<number>(await http.post(`/zsjos/contact-note/${subject}/${id}`, { content, requestKey }))
}
