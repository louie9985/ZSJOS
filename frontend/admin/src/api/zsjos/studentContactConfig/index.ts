import request from '@/config/axios'

export interface ChecklistItem { key: string; title: string; type: string; enabled: boolean; attachmentRequired?: boolean; sort: number }
export interface FormField { key: string; title: string; type: string; required: boolean; sort: number; description?: string; dictType?: string; multiple?: boolean }
export interface Version { id: number; versionNo: number; version: number; firstContactTimeoutMinutes: number; studyPlanTimeoutMinutes: number; checklist: ChecklistItem[]; quickNotes: string[]; collaboratorTabs: Record<string, string[]>; forms?: Record<string, FormField[]> }
export interface Config { published?: Version; draft?: Version }

export const getStudentContactConfig = () => request.get<Config>({ url: '/zsjos/student-contact-config' })
export const copyStudentContactDraft = (publishedId: number, publishedVersion: number, idempotencyKey: string) => request.post<number>({ url: '/zsjos/student-contact-config/draft/copy', data: { publishedId, publishedVersion, idempotencyKey } })
export const saveStudentContactDraft = (data: { id: number; version: number; idempotencyKey: string; firstContactTimeoutMinutes: number; studyPlanTimeoutMinutes: number; checklist: ChecklistItem[]; quickNotes: string[]; collaboratorTabs: Record<string, string[]>; forms?: Record<string, FormField[]> }) => request.put<boolean>({ url: '/zsjos/student-contact-config/draft', data })
export const publishStudentContactConfig = (id: number, version: number, idempotencyKey: string) => request.post<boolean>({ url: '/zsjos/student-contact-config/publish', data: { id, version, idempotencyKey } })
