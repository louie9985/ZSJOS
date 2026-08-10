import request from '@/config/axios'
import type { Timestamp } from '../types'

export interface UserRelationSceneVO {
  id?: number
  name: string
  code: string
  sourceLabel: string
  targetLabel: string
  sourcePostCode: string
  targetPostCode: string
  status: number
  remark?: string
  createTime?: Timestamp
  updateTime?: Timestamp
}

export interface RelationUserVO {
  id: number
  nickname: string
  maskedMobile?: string
  deptId?: number
  deptName?: string
  avatar?: string
  status: number
}

export interface UserRelationVO extends RelationUserVO {
  targetUsers: RelationUserVO[]
  validTargetCount: number
  invalidTargetCount: number
  updateTime?: Timestamp
}

export interface UserRelationLogVO {
  id: number
  sourceUsers: string
  targetUsers: string
  actionType: 'append' | 'replace' | 'remove'
  operatorUserId: number
  operatorName: string
  createTime: Timestamp
}

export const getScenePage = (params: PageParam & Record<string, any>) =>
  request.get({ url: '/zsjos/user-relation/scene/page', params })

export const getScene = (id: number): Promise<UserRelationSceneVO> =>
  request.get({ url: '/zsjos/user-relation/scene/get', params: { id } })

export const getSceneSimpleList = (): Promise<UserRelationSceneVO[]> =>
  request.get({ url: '/zsjos/user-relation/scene/simple-list' })

export const createScene = (data: UserRelationSceneVO) =>
  request.post({ url: '/zsjos/user-relation/scene/create', data })

export const updateScene = (data: UserRelationSceneVO) =>
  request.put({ url: '/zsjos/user-relation/scene/update', data })

export const deleteScene = (id: number) =>
  request.delete({ url: '/zsjos/user-relation/scene/delete', params: { id } })

export const getRelationPage = (
  params: PageParam & Record<string, any>
): Promise<{ list: UserRelationVO[]; total: number }> =>
  request.get({ url: '/zsjos/user-relation/relation/page', params })

export const getEligibleTargets = (sceneCode: string): Promise<RelationUserVO[]> =>
  request.get({ url: '/zsjos/user-relation/target/simple-list', params: { sceneCode } })

export const saveRelations = (data: {
  sceneCode: string
  sourceUserIds: number[]
  targetUserIds: number[]
  mode: 'append' | 'replace' | 'remove'
}) => request.put({ url: '/zsjos/user-relation/relation/save', data })

export const getLogPage = (
  params: PageParam & Record<string, any>
): Promise<{ list: UserRelationLogVO[]; total: number }> =>
  request.get({ url: '/zsjos/user-relation/log/page', params })
