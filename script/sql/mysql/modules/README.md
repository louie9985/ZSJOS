# Database modules

`core.json` is the authoritative database manifest for the modules assembled by
`yudao-server`. Optional modules are added only after their runtime dependency is
approved. Each optional module gets its own manifest, desired schema, migration
directory, and verification SQL; do not add optional tables to the Core baseline.

A module manifest declares its dependencies and repository-relative paths. Migration
versions are local to the module, start at `V001`, are forward-only, and must never be
edited after production execution. Removing a module does not remove its tables or
business data.

The release explicitly enables modules through the comma-separated
`ZSJOS_DB_MODULES` setting. It defaults to `core`; merely adding an optional manifest
does not install that module. The command rejects an enabled module when any declared
dependency is absent. On a fresh database, Core Bootstrap runs first and enabled
optional modules then install through their own migrations starting at `V001`.
