# ZSJOS Workbench Instructions

These rules apply to the independent employee workbench under `frontend/workbench/` and
extend the repository root instructions.

## Runtime and boundaries

- The runtime **MUST** remain React + Vite + TypeScript + Ant Design 6 and Ant Design Pro Components unless the user approves an architecture change.
- Employee-only pages **MUST NOT** be duplicated in the Vue administration frontend by default.
- **Exception — FMS (财务管理):** The FMS module exists in both `frontend/admin/src/views/fms/` and `frontend/workbench/src/pages/fms/` by explicit user decision (2026-08-24). FMS serves both employee self-service and administrator scenarios; the dual-frontend coexistence is authorized and **MUST NOT** be treated as prohibited duplication.
- The workbench is a presentation client for existing system and business APIs. It **MUST NOT** introduce generic `/zsjos/workbench/*` aggregation endpoints as a parallel source of truth.
- Backend `component` strings may be metadata for a local component registry. They **MUST NOT** be dynamically imported or executed as arbitrary code.

## Data and API access

- React components **MUST NOT** call Axios directly. Authentication, `tenant-id`, token refresh, response unwrapping, and HTTP errors belong in typed modules under `src/services`.
- Request and response types **MUST** be explicit. `any` is allowed only at a narrow external-data boundary and should be validated or normalized immediately.
- Menus, names, ordering, visibility, icons, and permissions **MUST** originate from the permission response. The client may normalize paths and derive display navigation, but **MUST NOT** infer access from role names.
- Workbench navigation **MUST** preserve server-owned menu hierarchy and visible descendant order. The five supported layout modes may present that hierarchy as sidebars, top-level dropdowns, collapsible directories, or popup flyouts; layout choice must not change authorization or route identity.
- Dictionary-backed controls **MUST** submit stable dictionary values and display backend labels.
- Administrator-maintained options **MUST NOT** silently fall back to hard-coded production data.

## Constants and ownership

- Protocol-wide values such as route constants, dictionary type codes, storage keys, and normalized application configuration belong in `src/constants.ts` or a focused shared protocol module when that file becomes too broad.
- Values used only by one feature, component, validation rule, or visual implementation **SHOULD** stay beside their owner.
- Runtime objects, hooks, contexts, functions, and administrator-maintained data **MUST NOT** be moved into a constants file.
- Components **MUST NOT** repeat literal API paths, dictionary type codes, or authentication storage keys.

## UI implementation

- When changing shared layout, themes, navigation, or component styling, **MUST** consult the relevant parts of `frontend/workbench/docs/ui-guidelines.md` for token usage, page skeletons, and naming conventions. Reuse material already read in the current context unless it changed or is no longer available.
- Visual decisions (dimensions, tokens, hierarchy, icons, interaction) **MUST** be grounded in the documented design system and existing page implementations. **MUST NOT** approximate from memory.
- Prefer existing Ant Design, Pro Components, icon, and theme primitives over new custom controls.
- HRM tabular views **MUST** use the shared `HrmProTable`; management lists enable its advanced toolbar, while detail and editable child tables use its compact mode.
- Remote views **MUST** provide the applicable loading, empty, error, retry, and unauthorized states.
- Fixed-format UI elements **SHOULD** use stable responsive dimensions so dynamic labels and states do not shift or overlap the layout.
- User-facing text **MUST** use the Zhongshijian employee-work-platform context and avoid upstream developer-oriented branding or technology marketing copy.

## Verification commands

Select checks under root AGENTS.md section 6 from `frontend/workbench/`. These are entry points, not a requirement to run all commands for every edit; use existing test filters for focused coverage:

```powershell
npm test
npm run typecheck
npm run build
```

- Visual or interaction changes **MUST** be checked in a real browser on the affected flows and widths. Shared layout or responsive changes require both desktop and mobile widths; bundling, dependencies, routes, assets, build configuration or release acceptance require a production build.
- Menu, permission, dictionary, authentication, and business API changes **MUST** be checked with real response shapes when the backend is available.
- Permission/authentication/tenant changes require the affected allowed/denied and isolation cases. Verify every affected consumer of a shared contract under the root rules.
- If a necessary command or backend is unavailable, report the exact unverified behavior and residual risk separately from checks that do not apply. Reuse valid results; rerun or broaden checks only for new changes, failures or unresolved risks.
