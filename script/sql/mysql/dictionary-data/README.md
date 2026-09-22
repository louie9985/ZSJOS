# Dictionary data synchronization

Sales stage: `lead-sales-stage.sql` is the separately approved five-option seed for
`zsjos_lead_sales_stage` (see `docs/api/lead-sales-stage.md`). It requires the System
dictionary tables and `zsjos_schema_version`, establishes UTF-8, and records
`lead_sales_stage_dictionary_v1`. Replays do not restore renamed, disabled or deleted
administrator choices. It does not assign role permissions. The sales-stage upgrade
V275 invokes it before bounded Lead initialization.

The bootstrap creates dictionary types and system-owned dictionary data, but it
intentionally creates no `zsjos_lead_category` or `zsjos_lead_source_channel`
data. Business dictionary data belongs in a separately reviewed, versioned SQL
file under this directory.

Before creating or executing a data file, confirm the exact dictionary types and
whether production data should be replaced, appended, or left unchanged. Never
put passwords, tokens, or personal data in these files or in repository guidance.

`current-database-dictionary-snapshot.sql` is the approved active dictionary
snapshot used by `sync-existing-server-config.sql`. It contains all current
non-deleted `system_dict_type` and `system_dict_data` rows, including ZSJOS
lead category/source values. It contains no users, credentials, tokens,
business instances, or Flowable runtime data. The file is replayable with
`INSERT IGNORE` and should be regenerated from a reviewed source database when
the authoritative dictionary set changes.
