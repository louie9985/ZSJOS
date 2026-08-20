import request from '@/config/axios'

export interface ChecklistItem { key: string; title: string; type: string; enabled: boolean; attachmentRequired?: boolean; sort: number }
export interface Version { id: number; versionNo: number; version: number; firstContactTimeoutMinutes: number; studyPlanTimeoutMinutes: number; checklist: ChecklistItem[]; quickNotes: string[]; collaboratorTabs: Record<string, string[]> }
export interface Config { published?: Version; draft?: Version }

export const getStudentContactConfig = () => request.get<Config>({ url: '/zsjos/student-contact-config' })
export const copyStudentContactDraft = () => request.post<number>({ url: '/zsjos/student-contact-config/draft/copy' })
export const saveStudentContactDraft = (data: { id: number; version: number; firstContactTimeoutMinutes: number; studyPlanTimeoutMinutes: number; checklist: ChecklistItem[]; quickNotes: string[]; collaboratorTabs: Record<string, string[]> }) => request.put<boolean>({ url: '/zsjos/student-contact-config/draft', data })
export const publishStudentContactConfig = (id: number, version: number) => request.post<boolean>({ url: '/zsjos/student-contact-config/publish', data: { id, version } })
