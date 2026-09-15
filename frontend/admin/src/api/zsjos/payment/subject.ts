import request from '@/config/axios'

export interface PaymentSubjectVO {
  id: number
  subjectName: string
  subjectCode: string
  cusid: string
  merchantPrivateKey: string
  platformPublicKey: string
  appid: string
  orgid?: string
  rsaType?: string
  isDefault?: boolean
  sort?: number
  status: number
  remark?: string
  createTime?: number
}

export type PaymentSubjectSaveVO = Omit<PaymentSubjectVO, 'id'> & { id?: number }
export type PaymentSubjectSummaryVO = Pick<
  PaymentSubjectVO,
  'id' | 'subjectCode' | 'subjectName' | 'cusid' | 'status' | 'createTime'
>
export interface PaymentSubjectPageParams {
  pageNo: number
  pageSize: number
  subjectName?: string
  status?: number
}

// 查询支付主体分页
export const getPaymentSubjectPage = async (params: PaymentSubjectPageParams) => {
  return await request.get<{ list: PaymentSubjectSummaryVO[]; total: number }>({
    url: '/zsjos/payment-subject/page',
    params
  })
}

// 查询支付主体详情
export const getPaymentSubject = async (id: number) => {
  return await request.get<PaymentSubjectVO>({ url: '/zsjos/payment-subject/get?id=' + id })
}

// 查询支付主体精简信息列表
export const getPaymentSubjectSimpleList = async () => {
  return await request.get<PaymentSubjectSummaryVO[]>({ url: '/zsjos/payment-subject/simple-list' })
}

// 新增支付主体
export const createPaymentSubject = async (data: PaymentSubjectSaveVO) => {
  return await request.post({ url: '/zsjos/payment-subject/create', data })
}

// 修改支付主体
export const updatePaymentSubject = async (data: PaymentSubjectSaveVO) => {
  return await request.put({ url: '/zsjos/payment-subject/update', data })
}

// 修改支付主体状态
export const updatePaymentSubjectStatus = async (id: number, status: number) => {
  return await request.put({
    url: '/zsjos/payment-subject/update-status',
    params: { id, status }
  })
}

// 删除支付主体
export const deletePaymentSubject = async (id: number) => {
  return await request.delete({ url: '/zsjos/payment-subject/delete?id=' + id })
}
