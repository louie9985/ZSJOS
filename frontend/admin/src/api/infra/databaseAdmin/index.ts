import request from '@/config/axios'

export interface DatabaseAdminColumnVO {
  name: string
  typeName: string
  jdbcType: number
  columnSize?: number
  decimalDigits?: number
  defaultValue?: string
  generated: boolean
  valueKind:
    | 'boolean'
    | 'integer'
    | 'decimal'
    | 'float'
    | 'date'
    | 'time'
    | 'datetime'
    | 'text'
    | 'json'
    | 'readonly'
  remarks?: string
  nullable: boolean
  primaryKey: boolean
  autoIncrement: boolean
  sensitive: boolean
  editable: boolean
}

export interface DatabaseAdminTableVO {
  name: string
  remarks?: string
  primaryKeyColumn?: string
  writable: boolean
}

export interface DatabaseAdminTableDetailVO extends DatabaseAdminTableVO {
  columns: DatabaseAdminColumnVO[]
}

export interface DatabaseAdminTableDataVO {
  table: DatabaseAdminTableDetailVO
  total: number
  rows: Array<Record<string, unknown>>
}

export interface DatabaseAdminTableQuery {
  dataSourceConfigId: number
  name?: string
  comment?: string
}

export interface DatabaseAdminDataPageQuery extends PageParam {
  dataSourceConfigId: number
  tableName: string
  keyword?: string
}

export interface DatabaseAdminRowCreateReqVO {
  dataSourceConfigId: number
  tableName: string
  values: Record<string, unknown>
}

export interface DatabaseAdminRowUpdateReqVO extends DatabaseAdminRowCreateReqVO {
  primaryKeyValue: unknown
}

export interface DatabaseAdminRowDeleteReqVO {
  dataSourceConfigId: number
  tableName: string
  primaryKeyValue: unknown
}

export const getTableList = (params: DatabaseAdminTableQuery) => {
  return request.get<DatabaseAdminTableVO[]>({ url: '/infra/database-admin/table/list', params })
}

export const getTableDetail = (dataSourceConfigId: number, tableName: string) => {
  return request.get<DatabaseAdminTableDetailVO>({
    url: '/infra/database-admin/table/detail',
    params: { dataSourceConfigId, tableName }
  })
}

export const getTableDataPage = (params: DatabaseAdminDataPageQuery) => {
  return request.get<DatabaseAdminTableDataVO>({ url: '/infra/database-admin/data/page', params })
}

export const createRow = (data: DatabaseAdminRowCreateReqVO) => {
  return request.post({ url: '/infra/database-admin/row/create', data })
}

export const updateRow = (data: DatabaseAdminRowUpdateReqVO) => {
  return request.put({ url: '/infra/database-admin/row/update', data })
}

export const deleteRow = (data: DatabaseAdminRowDeleteReqVO) => {
  return request.delete({ url: '/infra/database-admin/row/delete', data })
}
