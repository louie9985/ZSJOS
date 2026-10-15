import request from '@/config/axios'

export const updateKnowledgeShareSettings = (data: {
  documentId: number
  shareUserIds: number[]
  attachmentFileIds: number[]
}) => request.put({ url: '/pms/kb/document-share/settings', data })

// PMS 知识库文档分享 VO
export interface PmsKnowledgeDocumentShareVO {
  attachmentFileIds?: number[]
  id: number // 分享编号
  documentId: number // 文档编号
  shareUserIds: number[] // 内部分享成员用户编号列表
  token: string // 外部查看令牌
  url?: string // 后端按公开域名生成；配置不可用时不返回
  status: number // 分享状态
  createTime: number // 创建时间
  closeUserId?: number // 关闭人用户编号
  closeTime?: number // 关闭时间
}

// PMS 知识库文档公开分享 VO
export interface PmsKnowledgeDocumentSharePublicVO {
  attachments?: import('../../content/document').KnowledgeAttachment[]
  id: number // 文档编号
  title: string // 文档标题
  content?: string // 富文本文档内容
  previewUrl?: string // 文件临时预览地址
  type: number // 文档类型
  fileType?: string // 文件类型
  updateTime: number // 更新时间
}

// 查询文档分享信息
export const getKnowledgeDocumentShare = (documentId: number) => {
  return request.get<PmsKnowledgeDocumentShareVO | undefined>({
    url: '/pms/kb/document-share/get',
    params: { documentId }
  })
}

// 开启文档分享
export const openKnowledgeDocumentShare = (data: {
  attachmentFileIds?: number[]
  documentId: number
  shareUserIds: number[]
}) => {
  return request.post<PmsKnowledgeDocumentShareVO>({ url: '/pms/kb/document-share/open', data })
}

// 修改文档分享成员列表
export const updateKnowledgeDocumentShareMemberList = (data: {
  documentId: number
  shareUserIds: number[]
}) => {
  return request.put({ url: '/pms/kb/document-share/update-member-list', data })
}

// 关闭文档分享
export const closeKnowledgeDocumentShare = (documentId: number) => {
  return request.put({ url: '/pms/kb/document-share/close', params: { documentId } })
}

// 查询公开分享的文档内容
export const getPublicKnowledgeDocument = (token: string) => {
  return request.get<PmsKnowledgeDocumentSharePublicVO>({
    url: '/pms/kb/document-share/get-by-token',
    params: { token },
    headers: { isToken: false }
  })
}
