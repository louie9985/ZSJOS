import request from '@/config/axios'

export interface ChecklistItem { id: number; itemKey: string; itemType: string; title: string; sort: number; enabled: boolean; systemRequired: boolean }
export interface ChecklistVersion { id: number; versionNo: number; status: string; publishedAt?: string; items: ChecklistItem[] }
export interface ChecklistConfig { templateId: number; templateVersion: number; published?: ChecklistVersion; draft?: ChecklistVersion }
export const getRegistrationChecklistConfig = () => request.get<ChecklistConfig>({ url: '/zsjos/registration-checklist-config' })
export const copyRegistrationChecklistDraft = (version: number) => request.post<number>({ url: '/zsjos/registration-checklist-config/draft/copy', data: { version, idempotencyKey: crypto.randomUUID() } })
export const saveRegistrationChecklistDraft = (data: { templateVersion: number; items: ChecklistItem[] }) => request.put<boolean>({ url: '/zsjos/registration-checklist-config/draft', data: { ...data, idempotencyKey: crypto.randomUUID() } })
export const publishRegistrationChecklist = (version: number) => request.post<boolean>({ url: '/zsjos/registration-checklist-config/publish', data: { version, idempotencyKey: crypto.randomUUID() } })
