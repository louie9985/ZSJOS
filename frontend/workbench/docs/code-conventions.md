# ZSJOS Workbench Code Conventions

## 1. Constants and enums

- Application-wide protocol values, dictionary types, shared route paths, storage keys, and normalized application configuration belong in `src/constants.ts` or a focused shared protocol module when that file becomes too broad.
- Use grouped objects with `as const`, such as `DICT_TYPE`, `APP_ROUTES`, and `STORAGE_KEYS`. Do not repeat string or numeric protocol values in pages.
- Components import constants by name. They must not redeclare dictionary codes, status values, route paths, or storage keys.
- Feature-private display options, validation rules, and implementation constants stay beside their owning feature unless they gain a second owner.
- Runtime objects, hooks, functions, component contexts, and implementation-only styling fragments stay in their owning module. They are not application constants.
- Data that administrators need to maintain must come from a dictionary or business API, not from `constants.ts`.

## 2. Dictionary-backed fields

- Dictionary type codes are declared in `DICT_TYPE` and must match the backend `system_dict_type.type` exactly.
- Form options use the dictionary `label` for display and `value` for submission.
- Store stable codes in business data. Do not use a mutable display label as a protocol value for new fields.
- Every dictionary select must provide loading, empty, and failure states.

## 3. API access

- Components do not call `axios` directly. HTTP access belongs in `src/services` and is exposed through typed functions.
- Authentication headers, tenant headers, refresh behavior, and response unwrapping remain centralized in the HTTP client.
- Repeated domain APIs should be split into domain service files instead of growing one global `api` object indefinitely.
- API request and response types must be explicit. Avoid `any` except at a narrow external-data boundary, then validate or normalize immediately.

## 4. Types and contracts

- Page-private form types stay beside the page. Types shared by multiple features belong in `src/types` or the relevant service module.
- Prefer types derived from constants, for example `typeof ENUM[keyof typeof ENUM]`, so constants and TypeScript unions cannot drift apart.
- Frontend field names and submitted value types must match backend request objects.

## 5. Components and hooks

- Pages coordinate data and layout; reusable controls belong in `src/components`.
- Extract repeated data-loading and stateful behavior into hooks when a second consumer appears.
- Keep render code declarative. Move mapping, normalization, and branching logic out of large JSX blocks.
- Effects that perform requests must handle unmounting and stale responses.

## 6. Forms

- Define stable initial values and reset behavior. Asynchronously loaded defaults must also be restored after reset or submit.
- Required remote selects cannot silently fall back to hard-coded options.
- Submit buttons expose loading state and prevent duplicate submission once real APIs are connected.
- Validation rules shared by multiple forms belong in constants or a validation utility.

## 7. State and persistence

- Do not access `localStorage` with literal keys outside the authentication/theme infrastructure.
- Persist only state that must survive refresh. Server state should be re-fetched or cached through a dedicated data layer.
- Clear all authentication-related storage keys together on logout or failed session recovery.

## 8. Routing and permissions

- Route paths referenced in code belong in `APP_ROUTES`.
- Backend menu and permission data remain the source of truth. A frontend route must not bypass backend authorization.
- Unknown or unmigrated menu components render an explicit placeholder instead of failing silently.

## 9. Error, loading, and empty states

- Every remote-data view handles loading, success, empty, and error states.
- Do not use empty `catch` blocks for business requests. Show an actionable message or propagate the error to a boundary.
- User messages describe the failed operation, not low-level implementation details.

## 10. Security and configuration

- Secrets, production credentials, and environment-specific URLs never belong in source constants.
- Environment-specific values use `VITE_*` variables and are normalized once in `APP_CONFIG`.
- Do not log tokens, passwords, personal data, or complete backend error payloads.

## 11. Quality gates

- New service behavior and pure transformation logic require focused unit tests.
- Before handoff, run `npm test`, `npm run typecheck`, and `npm run build` for behavior changes.
- UI behavior changes also require real-browser checks at desktop and mobile widths.
- Keep imports acyclic: constants may depend on type-only declarations, but feature modules must not be imported by general services.
- Do not mix unrelated refactors into a feature change.
