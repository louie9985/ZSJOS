import request from '@/config/axios'

export type WorkbenchLayoutScopeType = 'GLOBAL' | 'ROLE'

export interface WorkbenchLayoutNode {
  key: string
  type: 'GROUP' | 'PAGE'
  sourceMenuId?: number
  name: string
  icon?: string
  hidden: boolean
  sort?: number
  children: WorkbenchLayoutNode[]
}

export interface WorkbenchLayoutSnapshot {
  schemaVersion: number
  scopeType: WorkbenchLayoutScopeType
  enabled: boolean
  priority?: number
  nodes: WorkbenchLayoutNode[]
  operations?: unknown[]
}

export interface WorkbenchLayoutCandidateRespVO {
  pages: Array<{
    sourceMenuId: number
    name: string
    icon?: string
    path: string
    workbenchRenderMode: 'native' | 'admin_embed'
  }>
  roles: Array<{
    id: number
    name: string
    code: string
    status: number
    publishedVersionNo?: number
    publishedEnabled?: boolean
    publishedPriority?: number
  }>
}

export interface WorkbenchLayoutDraftRespVO {
  scopeType: WorkbenchLayoutScopeType
  scopeId: number
  draftRevision: number
  snapshot: WorkbenchLayoutSnapshot
  candidatePages: WorkbenchLayoutCandidateRespVO['pages']
  publishedVersionId?: number
  publishedVersionNo?: number
  publishedEnabled?: boolean
  publishedPriority?: number
}

export interface WorkbenchMenuMeta {
  globalVersionId?: number
  globalVersionNo?: number
  appliedRoleLayouts: Array<{
    roleId: number
    versionId?: number
    versionNo?: number
    priority?: number
  }>
  fallback: boolean
  fallbackReason?: string
}

export interface PreviewMenu {
  id: number
  sourceMenuId?: number
  layoutKey?: string
  name: string
  path: string
  icon?: string
  children: PreviewMenu[]
}

export interface WorkbenchLayoutPreviewRespVO {
  userId: number
  userName: string
  roleIds: number[]
  permissions: string[]
  finalTree: PreviewMenu[]
  meta: WorkbenchMenuMeta
  filteredItems: Array<{ sourceMenuId?: number; name?: string; reason: string }>
}

export interface WorkbenchLayoutImpactRespVO {
  publishable: boolean
  affectedRoleCount: number
  issues: Array<{ roleId?: number; roleName?: string; message: string }>
}

export interface WorkbenchLayoutVersionRespVO {
  id: number
  versionNo: number
  enabled: boolean
  priority?: number
  publishRemark: string
  restoredFromVersionId?: number
  publisherUserId: number
  publishTime: Date
}

export const getCandidates = (): Promise<WorkbenchLayoutCandidateRespVO> =>
  request.get({ url: '/system/workbench-layout/candidates' })

export const getDraft = (
  scopeType: WorkbenchLayoutScopeType,
  scopeId: number
): Promise<WorkbenchLayoutDraftRespVO> =>
  request.get({ url: '/system/workbench-layout/draft', params: { scopeType, scopeId } })

export const saveDraft = (data: {
  scopeType: WorkbenchLayoutScopeType
  scopeId: number
  draftRevision: number
  snapshot: WorkbenchLayoutSnapshot
}): Promise<number> => request.put({ url: '/system/workbench-layout/draft', data })

export const preview = (data: {
  userId: number
  scopeType: WorkbenchLayoutScopeType
  scopeId: number
  snapshot: WorkbenchLayoutSnapshot
}): Promise<WorkbenchLayoutPreviewRespVO> =>
  request.post({ url: '/system/workbench-layout/preview', data })

export const getPublishImpact = (
  scopeType: WorkbenchLayoutScopeType,
  scopeId: number
): Promise<WorkbenchLayoutImpactRespVO> =>
  request.get({ url: '/system/workbench-layout/publish-impact', params: { scopeType, scopeId } })

export const publish = (data: {
  scopeType: WorkbenchLayoutScopeType
  scopeId: number
  draftRevision: number
  publishRemark: string
}): Promise<number> => request.post({ url: '/system/workbench-layout/publish', data })

export const getVersions = (
  scopeType: WorkbenchLayoutScopeType,
  scopeId: number
): Promise<WorkbenchLayoutVersionRespVO[]> =>
  request.get({ url: '/system/workbench-layout/versions', params: { scopeType, scopeId } })

export const restoreDraft = (data: {
  scopeType: WorkbenchLayoutScopeType
  scopeId: number
  versionId: number
  draftRevision: number
}): Promise<number> => request.post({ url: '/system/workbench-layout/restore-draft', data })
