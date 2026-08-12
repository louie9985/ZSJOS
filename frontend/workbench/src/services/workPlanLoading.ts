import type { PageResult, SimpleDept, SimpleUser, WorkPlan, WorkPlanTemplate } from './api'

const TEMPLATE_PERMISSIONS = new Set(['zsjos:work-plan:create'])
const PEOPLE_PERMISSIONS = new Set([
  'zsjos:work-plan:create',
  'zsjos:work-plan:update',
  'zsjos:work-plan:assign',
  'zsjos:work-plan:decompose',
  'zsjos:work-plan:complete',
  'zsjos:work-plan:close'
])

export type WorkPlanPageResources = {
  page: PageResult<WorkPlan>
  templates: WorkPlanTemplate[]
  users: SimpleUser[]
  departments: SimpleDept[]
  auxiliaryErrors: string[]
}

type WorkPlanResourceApi = {
  workPlanPage: (params: { pageNo: number; pageSize: number }) => Promise<PageResult<WorkPlan>>
  workPlanTemplates: () => Promise<WorkPlanTemplate[]>
  simpleUsers: () => Promise<SimpleUser[]>
  simpleDepartments: () => Promise<SimpleDept[]>
}

const hasAnyPermission = (permissions: string[], expected: Set<string>) =>
  permissions.some(permission => expected.has(permission))

async function optionalResource<T>(label: string, request?: () => Promise<T[]>): Promise<{ data: T[]; error?: string }> {
  if (!request) return { data: [] }
  try {
    return { data: await request() }
  } catch {
    return { data: [], error: `${label}加载失败，相关操作暂不可用` }
  }
}

export async function loadWorkPlanPageResources(
  resourceApi: WorkPlanResourceApi,
  pageNo: number,
  permissions: string[]
): Promise<WorkPlanPageResources> {
  const loadTemplates = hasAnyPermission(permissions, TEMPLATE_PERMISSIONS)
  const loadPeople = hasAnyPermission(permissions, PEOPLE_PERMISSIONS)
  const [page, templates, users, departments] = await Promise.all([
    resourceApi.workPlanPage({ pageNo, pageSize: 12 }),
    optionalResource('计划模板', loadTemplates ? resourceApi.workPlanTemplates : undefined),
    optionalResource('用户选项', loadPeople ? resourceApi.simpleUsers : undefined),
    optionalResource('部门选项', loadPeople ? resourceApi.simpleDepartments : undefined)
  ])

  return {
    page,
    templates: templates.data,
    users: users.data,
    departments: departments.data,
    auxiliaryErrors: [templates.error, users.error, departments.error].filter((error): error is string => Boolean(error))
  }
}
