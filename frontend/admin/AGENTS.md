# Vue Administration Frontend Instructions

These rules apply to the Vue administration frontend and extend the repository root
instructions.

## Runtime and ownership

- **MUST** preserve the existing Vue 3, TypeScript, Vite, Pinia, and Element Plus architecture and repository conventions.
- **MUST** use `pnpm`. Do not introduce npm or Yarn lock files in this subtree.
- Menu, permission, dictionary, tenant, request, form, and table behavior **SHOULD** reuse the frontend's existing utilities and components before adding a parallel implementation.
- Employee-only workbench pages **MUST NOT** be implemented here unless the user explicitly requires an administrator-facing version or shared management workflow.
- User-visible product text **MUST** use the Zhongshijian context. Internal upstream identifiers **MUST NOT** be renamed as part of a visual branding change.

## Change rules

- Preserve the existing route-generation and permission-store flow unless a confirmed task changes that contract.
- API calls belong in the existing `src/api` structure, not directly in Vue page components.
- Dictionary-backed fields **MUST** use the existing dictionary infrastructure rather than local option arrays.
- **SHOULD** follow nearby page, form, dialog, table, and composable patterns instead of creating a second design system.
- Formatting and lint commands that rewrite files **MUST** be scoped to task files and run only during implementation, never during read-only analysis.

## Verification commands

Select checks under root AGENTS.md section 6 from this directory. These are entry points, not a requirement to run all commands for every edit; file-rewriting lint/format operations remain scoped under the change rules above:

```powershell
pnpm ts:check
pnpm lint
pnpm build:local
```

- Use the build mode required by the task when it is not `local`.
- Visual or interaction changes **MUST** be verified in a real browser on the affected flows and widths. Shared layout or responsive changes require both desktop and mobile widths; bundling, dependencies, routes, assets, build configuration or release acceptance require a production build.
- Permission/authentication/tenant changes require the affected allowed/denied and isolation cases. Verify every affected consumer of a shared contract under the root rules.
- Report necessary checks blocked by the environment and their residual risks separately from checks that do not apply. Reuse valid results; rerun or broaden checks only for new changes, failures or unresolved risks.
