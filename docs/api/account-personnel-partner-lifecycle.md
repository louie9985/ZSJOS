# Account, Personnel, and Partner Lifecycle

System remains authoritative for accounts, credentials, posts, roles, menus, departments, tenant
isolation, and sessions. ZSJOS owns only personnel business state and partner history.

## Account contract

- `POST /system/auth/login` accepts username or mobile plus password.
- Username: 4-32 ASCII letters, digits, or underscore.
- Newly created or reset password: 8-20 characters containing letters and digits.
- Username/mobile are unique in their own fields and cannot collide across fields for new changes.
- Historical ambiguous identifiers return a distinct correction-required login failure.
- Password changes and resets revoke all access and refresh tokens for the user.
- Disabling or deleting the final enabled `super_admin` is rejected.

## Personnel contract

- `GET /zsjos/personnel/{userId}/state` requires `zsjos:personnel:query`.
- `PUT /zsjos/personnel/{userId}/state` requires `zsjos:personnel:update-state` and a reason.
- States are `enabled`, `disabled`, and `departed`.
- Disabled/departed states disable the System account and revoke sessions; enabled restores it.
- Existing Lead/order assignments and historical BPM reviewer snapshots do not change.

## Partner contract

- `POST /zsjos/partner/create` creates the System account and partner profile in one transaction.
- `/zsjos/partner/{id}/disable` and `/enable` preserve the profile and history.
- `/zsjos/partner/{id}/convert` reuses the bound account and accepts only
  `new_media_employee` or `new_media_manager` plus a System department.
- `migrateHistoricalOrganization=false` preserves Lead submission snapshots; `true` migrates all
  Leads submitted through that partner to the selected department snapshot.
- No role is automatically granted by the migration or APIs; administrators use existing System
  role and menu assignments.
