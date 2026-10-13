export interface OrderPartnerOwnerSnapshot {
  employeeUserId?: number | null
  employeeName?: string | null
}

export function orderPartnerOwnerText(sourceType?: string, snapshot?: OrderPartnerOwnerSnapshot | null): string {
  if (sourceType !== 'partner') return '—'
  if (snapshot == null) return '历史未记录'
  if (snapshot.employeeUserId == null) return '提交时未分配'
  return snapshot.employeeName || '姓名未记录'
}
