import { http, unwrap } from './api'
export interface ExamProgressStudent { serviceRelationId: number; name: string; stage: string; stageLabel: string; outcome?: string; outcomeLabel?: string; supervisionReady: boolean }
export interface ExamProgressView { stage: string; stageLabel: string; version: number; actions: string[]; students: ExamProgressStudent[]; outcomes: { value: string; label: string }[] }
export interface ExamProgressResult { changed: boolean; message: string; view: ExamProgressView; blockers: ExamProgressStudent[] }
const path = (id: number) => `/zsjos/delivery-class/${id}/exam-progress`
export const classExamProgress = {
  get: async (id: number) => unwrap<ExamProgressView>(await http.get(path(id))),
  transition: async (id: number, action: string, version: number) => unwrap<ExamProgressResult>(await http.post(path(id), { action, version })),
  outcome: async (id: number, serviceRelationId: number, value: string | null, version: number) => unwrap<ExamProgressView>(await http.put(`${path(id)}/outcome`, { serviceRelationId, value, version })),
}
