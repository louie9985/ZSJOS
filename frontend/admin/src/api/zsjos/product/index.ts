import request from '@/config/axios'
import type { Timestamp } from '../types'

export interface ZsjosProductVO {
  id: number
  productRef: string
  level1CategoryId?: number
  level1CategoryName?: string
  level2CategoryId?: number
  level2CategoryName?: string
  name: string
  subtitle?: string
  description?: string
  targetAudience?: string
  studyDuration?: string
  studyMode?: string
  coverImage?: string
  validCashbackAmount?: number
  dealCashbackRate?: number
  categoryId: number
  categoryName?: string
  categoryPath?: Array<{ id: number; name: string }>
  status: number
  sort: number
  remark?: string
  createTime?: Timestamp
  updateTime?: Timestamp
}

export interface ZsjosProductSaveReqVO {
  id?: number
  name: string
  subtitle?: string
  description?: string
  targetAudience?: string
  studyDuration?: string
  studyMode?: string
  coverImage?: string
  validCashbackAmount?: number
  dealCashbackRate?: number
  categoryId: number
  status: number
  sort: number
  remark?: string
}

export interface ZsjosProductPageReqVO {
  pageNo: number
  pageSize: number
  name?: string
  productRef?: string
  status?: number
  categoryId?: number
}

export interface ZsjosProductCategoryVO {
  id: number
  parentId: number
  level: number
  name: string
  defaultValidCashbackAmount?: number
  defaultDealCashbackRate?: number
  status: number
  sort: number
  remark?: string
  hasProducts?: boolean
  children?: ZsjosProductCategoryVO[]
}

export interface ProductAttrVO {
  attrKey?: string
  attrName: string
  required: boolean
  sort: number
  values: Array<{ value: string; label: string; sort: number }>
}

export interface ProductSkuVO {
  id: number
  spuId: number
  skuRef: string
  skuName: string
  attrValues: Record<string, string>
  price: number
  status: number
  sort: number
  remark?: string
  updateTime?: Timestamp
}

export interface ProductSkuSaveReqVO {
  id?: number
  spuId: number
  skuName: string
  attrValues: Record<string, string>
  price: number
  status: number
  sort: number
  remark?: string
}

export const getProductPage = (params: ZsjosProductPageReqVO) =>
  request.get({ url: '/zsjos/product/page', params })

export const getProduct = (id: number) => request.get({ url: `/zsjos/product/get?id=${id}` })

export const createProduct = (data: ZsjosProductSaveReqVO) =>
  request.post({ url: '/zsjos/product/create', data })

export const updateProduct = (data: ZsjosProductSaveReqVO) =>
  request.put({ url: '/zsjos/product/update', data })

export const deleteProduct = (id: number) =>
  request.delete({ url: `/zsjos/product/delete?id=${id}` })

export const updateProductStatus = (data: { id: number; status: number }) =>
  request.put({ url: '/zsjos/product/update-status', data })

export const getCategoryTree = () => request.get({ url: '/zsjos/product/category/tree' })
export const createCategory = (data: Partial<ZsjosProductCategoryVO>) =>
  request.post({ url: '/zsjos/product/category/create', data })
export const updateCategory = (data: Partial<ZsjosProductCategoryVO>) =>
  request.put({ url: '/zsjos/product/category/update', data })
export const deleteCategory = (id: number) =>
  request.delete({ url: `/zsjos/product/category/delete?id=${id}` })
export const updateCategoryStatus = (id: number, status: number) =>
  request.put({ url: `/zsjos/product/category/update-status?id=${id}&status=${status}` })

export const getProductAttrs = (spuId: number) =>
  request.get({ url: `/zsjos/product/sku/attrs?spuId=${spuId}` })
export const saveProductAttrs = (spuId: number, attrs: ProductAttrVO[]) =>
  request.put({ url: '/zsjos/product/sku/attrs', data: { spuId, attrs } })
export const getSkuList = (spuId: number) =>
  request.get({ url: `/zsjos/product/sku/list?spuId=${spuId}` })
export const createSku = (data: ProductSkuSaveReqVO) =>
  request.post({ url: '/zsjos/product/sku/create', data })
export const generateSkus = (spuId: number) =>
  request.post({ url: `/zsjos/product/sku/generate?spuId=${spuId}` })
export const updateSku = (data: ProductSkuSaveReqVO) =>
  request.put({ url: '/zsjos/product/sku/update', data })
export const deleteSku = (id: number) =>
  request.delete({ url: `/zsjos/product/sku/delete?id=${id}` })
export const updateSkuStatus = (id: number, status: number) =>
  request.put({ url: '/zsjos/product/sku/update-status', data: { id, status } })
