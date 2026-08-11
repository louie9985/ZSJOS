export const WORKBENCH_COMPONENT = {
  LEAD_APPEAL: 'leadAppeal'
} as const

export type WorkbenchComponent = typeof WORKBENCH_COMPONENT[keyof typeof WORKBENCH_COMPONENT]

const COMPONENT_REGISTRY: Record<string, WorkbenchComponent> = {
  'zsjos/leadAppeal/index': WORKBENCH_COMPONENT.LEAD_APPEAL
}

export function resolveWorkbenchComponent(component?: string) {
  return component ? COMPONENT_REGISTRY[component.trim()] : undefined
}
