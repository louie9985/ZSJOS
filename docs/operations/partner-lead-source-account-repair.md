# Zhongshijian test Partner Lead source-account repair

## Scope and diagnosis

The 2026-10-08 audit examined all 5,439 Leads in `zsjos-mysql-1/zsjos`
(5,436 active). The 699 Leads with `provider_owner_type='partner'` included
584 valid account references and 115 missing references. No populated reference
pointed to a missing account or a different Partner. A `source_type='partner'`
alone does not identify a Partner account: employee-origin historical rows also
use that source type with `provider_owner_type='system_user'`.

The three reported business numbers LD202609010076, LD202609020090 and
LD202609070088 already referenced the correct Partner account. Different identity
tables can contain identical numeric IDs. Before repair, 245 active Partner Leads
referenced numbers also present in `system_users`, across 29 Partner accounts.
Renumbering those valid references would corrupt their identity relationships.
The management query currently compares `source_user_id` without selecting the
submitter identity space; database repair does not correct that filter behavior.

## Evidence and execution

The user authorized correcting erroneous Partner references in the test database.
For each of the 115 missing references, the original `ptml.leads_lead` business
number and import-marker source ID matched. Its original submitter linked through
`ptml.accounts_user.phone` to a unique current account with the same tenant and
Partner. No names, current ownership or contribution attribution were used to
infer historical submitters. This is a targeted operational repair, not a bootstrap
change or a migration replay; V122 and version ledgers remain untouched.

The executable source is
`script/sql/mysql/repairs/repair_partner_lead_source_accounts_20261008.py`.
It requires the resolved environment to be `test`, existing PyMySQL, MySQL 8 at
`127.0.0.1:3306`, the original `ptml` source tables and the unchanged frozen plan.
Run preparation without `--apply`, then apply using identical arguments:

```bash
python3 script/sql/mysql/repairs/repair_partner_lead_source_accounts_20261008.py \
  --password-file /opt/zsjos-runtime/secrets/mysql-root-password \
  --evidence-dir /tmp/zsjos-partner-source-20261008
```

Append `--apply` to execute the authorized repair. Preparation rehearses the real
update and rolls it back; applying commits it. Both modes validate temporary-table
initial application, repeat execution, partial recovery and conflict rejection.
Original sources are locked and checked again before the guarded Lead updates.
An unexpected mapping or target change aborts execution; it is not automatically
reconciled. The frozen live batch must be entirely pending or entirely repaired.

Only `source_user_id` changed. `update_time` was explicitly retained, and every
other column, including version, owner, contribution snapshots, status and
timestamps, was compared before commit. No rows were inserted or deleted;
no accounts, permissions, schema, services or notifications were changed.
There are no dependent insertions or ordering changes.

## Verification and recovery

Execution repaired exactly 115 rows; a second execution changed zero. Independent
committed readback found all 699 Partner references present and matching their
Partners. A full-library digest proved all 5,439 Lead rows unchanged except the
115 authorized fields. Representative stored Partner-name HEX decoded as UTF-8;
the repair wrote numeric fields only, with the client configured as `utf8mb4`.

Protected evidence is under `/tmp/zsjos-partner-source-20261008/` (directory 0700,
files 0600): frozen mapping, complete affected before/after images and full-library
preservation digest. The directory contains private business data and must not be
published. The plan SHA-256 is
`a9eb64462aec1e87442baea45af133d714dd4301350498209a206e20f4ea5e11`.
Temporary-directory retention is limited; preserve these protected files in the
established restricted backup location if longer operational retention is needed.

An execution failure before commit rolls back the whole batch. After commit,
reversal requires a separately reviewed guarded transaction for precisely the
frozen plan, restoring the original null account references only while the saved
post-images still match. Do not overwrite intervening business changes or restore
entire rows blindly. This script does not automatically reverse a committed repair.

The submitter filter still needs a separate code correction to distinguish employee
and Partner identities. After filling missing accounts, 282 active Partner Leads
have valid IDs numerically shared with employees, across 29 Partner accounts;
that count includes previously unfilterable missing-account rows. It is not a new
misassignment and must not be remedied by substituting arbitrary account numbers.
