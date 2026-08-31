export type SalesOrderApprovalWorkType = 'approval' | 'supervisor'

export interface SalesOrderApprovalAccess {
  canReview: boolean
  canConfirmSupervisor: boolean
  defaultWorkType?: SalesOrderApprovalWorkType
  showWorkTypeSwitch: boolean
}

export function resolveSalesOrderApprovalAccess(permissions: string[]): SalesOrderApprovalAccess {
  const canReview = permissions.includes('zsjos:sales-order:review')
  const canConfirmSupervisor = permissions.includes('zsjos:sales-order:supervisor-confirm')
  return {
    canReview,
    canConfirmSupervisor,
    defaultWorkType: canReview ? 'approval' : canConfirmSupervisor ? 'supervisor' : undefined,
    showWorkTypeSwitch: canReview && canConfirmSupervisor
  }
}
