import request from '@/config/axios'

export interface TransferVO {
  id?: number
  no?: string
  type: number
  assetId: number
  assetName?: string
  assetCode?: string
  fromEmployeeId?: number
  fromEmployeeName?: string
  fromDeptId?: number
  toEmployeeId?: number
  toEmployeeName?: string
  toDeptId?: number
  expectedReturnDate?: string
  actualReturnDate?: string
  status?: number
  processInstanceId?: string
  reason?: string
  applyUserId?: number
  applyUserName?: string
  applyTime?: Date
}

/** 流转类型 */
export const TransferType = {
  RECEIVE: 1,
  RETURN: 2,
  BORROW: 3,
  GIVE_BACK: 4,
  ALLOCATE: 5
} as const

/** 需要审批的流转类型，与后端 EamTransferTypeEnum.NEED_APPROVAL 保持一致 */
export const NEED_APPROVAL_TYPES: number[] = [
  TransferType.RECEIVE,
  TransferType.BORROW,
  TransferType.ALLOCATE
]

/** 流转单状态 */
export const TransferStatus = {
  APPROVING: 0,
  APPROVED: 1,
  REJECTED: 2,
  CANCELLED: 3
} as const

// 查询流转单分页
export const getTransferPage = async (params: any) => {
  return await request.get({ url: '/eam/transfer/page', params })
}

// 查询流转单详情
export const getTransfer = async (id: number) => {
  return await request.get({ url: '/eam/transfer/get?id=' + id })
}

// 创建流转单
export const createTransfer = async (data: TransferVO) => {
  return await request.post({ url: '/eam/transfer/create', data })
}

// 审批通过
export const approveTransfer = async (id: number) => {
  return await request.put({ url: '/eam/transfer/approve?id=' + id })
}

// 驳回
export const rejectTransfer = async (id: number, reason?: string) => {
  return await request.put({
    url: `/eam/transfer/reject?id=${id}${reason ? '&reason=' + encodeURIComponent(reason) : ''}`
  })
}

// 取消
export const cancelTransfer = async (id: number) => {
  return await request.put({ url: '/eam/transfer/cancel?id=' + id })
}
