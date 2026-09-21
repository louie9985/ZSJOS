import request from '@/config/axios'

export interface ProductPaymentSubjectVO {
  productId: number
  productName?: string
  paymentSubjectId?: number
  subjectName?: string
  configTime?: number
}

export interface ProductPaymentSubjectConfigReqVO {
  productIds: number[]
  paymentSubjectId: number
}

export interface ProductPaymentSubjectPageParams {
  pageNo: number
  pageSize: number
  productName?: string
  paymentSubjectId?: number
}

// 查询产品支付主体配置分页
export const getProductPaymentSubjectPage = async (params: ProductPaymentSubjectPageParams) => {
  return await request.get<{ list: ProductPaymentSubjectVO[]; total: number }>({
    url: '/zsjos/product-payment-subject/page',
    params
  })
}

// 配置产品支付主体
export const configProductPaymentSubject = async (data: ProductPaymentSubjectConfigReqVO) => {
  return await request.post({ url: '/zsjos/product-payment-subject/batch-configure', data })
}
