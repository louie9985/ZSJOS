# ZSJOS Workbench Instructions

These rules apply to the independent employee workbench under `frontend/workbench/` and
extend the repository root instructions.

## Runtime and boundaries

- The runtime **MUST** remain React + Vite + TypeScript + Ant Design 6 and Ant Design Pro Components unless the user approves an architecture change.
- Employee-only pages **MUST NOT** be duplicated in the Vue administration frontend by default.
- The workbench is a presentation client for existing system and business APIs. It **MUST NOT** introduce generic `/zsjos/workbench/*` aggregation endpoints as a parallel source of truth.
- Backend `component` strings may be metadata for a local component registry. They **MUST NOT** be dynamically imported or executed as arbitrary code.

## Data and API access

- React components **MUST NOT** call Axios directly. Authentication, `tenant-id`, token refresh, response unwrapping, and HTTP errors belong in typed modules under `src/services`.
- Request and response types **MUST** be explicit. `any` is allowed only at a narrow external-data boundary and should be validated or normalized immediately.
- Menus, names, ordering, visibility, icons, and permissions **MUST** originate from the permission response. The client may normalize paths and derive display navigation, but **MUST NOT** infer access from role names.
- Dictionary-backed controls **MUST** submit stable dictionary values and display backend labels.
- Administrator-maintained options **MUST NOT** silently fall back to hard-coded production data.

## Constants and ownership

- Protocol-wide values such as route constants, dictionary type codes, storage keys, and normalized application configuration belong in `src/constants.ts` or a focused shared protocol module when that file becomes too broad.
- Values used only by one feature, component, validation rule, or visual implementation **SHOULD** stay beside their owner.
- Runtime objects, hooks, contexts, functions, and administrator-maintained data **MUST NOT** be moved into a constants file.
- Components **MUST NOT** repeat literal API paths, dictionary type codes, or authentication storage keys.

## UI implementation

- Before changing shared layout, themes, navigation, or component styling, **MUST** read `frontend/workbench/docs/ui-guidelines.md` for token usage, page skeletons, and naming conventions.
- Visual decisions (dimensions, tokens, hierarchy, icons, interaction) **MUST** be grounded in the documented design system and existing page implementations. **MUST NOT** approximate from memory.
- Prefer existing Ant Design, Pro Components, icon, and theme primitives over new custom controls.
- Remote views **MUST** provide the applicable loading, empty, error, retry, and unauthorized states.
- Fixed-format UI elements **SHOULD** use stable responsive dimensions so dynamic labels and states do not shift or overlap the layout.
- User-facing text **MUST** use the Zhongshijian employee-work-platform context and avoid upstream developer-oriented branding or technology marketing copy.

## Verification commands

Run checks appropriate to the change from `frontend/workbench/`:

```powershell
npm test
npm run typecheck
npm run build
```

- UI behavior changes **MUST** also be checked in a real browser at desktop and mobile widths.
- Menu, permission, dictionary, authentication, and business API changes **MUST** be checked with real response shapes when the backend is available.
- If a command or backend is unavailable, report the exact unverified behavior and residual risk.
