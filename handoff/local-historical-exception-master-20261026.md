# One maintained exception master

- Environment local (variable unset, marker absent); branch main; worktree D:/ZSJ-OS; base 8279212fb97129ba6b006dbb0452ad843dfde4b5; owner Codex /root; integration None.
- Goal: follow user correction to maintain one simple workbook at the existing fixed 待确认学员.xlsx path, with one worksheet, concrete per-person reasons and retained human handling notes. Update that file in place on future runs instead of creating versioned Excel outputs.
- Ownership: script/historical-student-import/exceptions.mjs, prepare_master.py, docs/operations/historical-student-import.md, docs/operations/historical-student-identity-matching.md, this record, and the explicitly generated exception-output directory. Prior exception work has delivered/released its ownership.
- Preserve original source workbook, database, import rules, existing services and unrelated dirty work. Do not run database operations. Source identity ambiguity is not silently merged: group only identical source name plus contact, retaining all original source coordinates and variant values.
- User requests only one retained master. Clean up only the six known generated exception workbooks and their inspect sidecars in the exact output directory after a private recovery ZIP and validated master save; keep the fixed master path. Check each resolved path is within that output directory, no recursive filesystem deletion.
- Verification: all latest 45 source exception rows represented in 41 name/contact review rows, concrete reasons and alternatives preserved, one sheet and fixed path, note-preserving in-place update, atomic save, readback/ZIP integrity and visual inspection. No production/application changes, dependencies or Git publication.

## Delivery

- Replaced versioned multi-sheet exports with one fixed 待确认学员.xlsx containing only 异常主表 and nine business-readable columns. Reasons remain first; notes explain exact discrepancies and decisions needed. Same source name/contact records combine variants and retain all source coordinates: 41 review rows cover all 45 original exception records; disputed names/contacts are not silently merged.
- Added prepare_master.py and changed exceptions.mjs to update the fixed file atomically; later human 处理意见 persist by name/contact or source reference. No suffix/date-named new workbooks. Verified a real second update of the same path, exact values/ZIP/sheet dimensions, all source memberships, and note preservation by both lookup routes without changing actual user notes during tests.
- Visually inspected header and reason layout. Updated operational and identity documentation to make the single maintained master the current delivery contract; legacy multi-sheet preservation helpers remain historical only.
- Validated a private recovery ZIP of the 12 former generated outputs/sidecars, then removed only the five obsolete workbook variants and six inspect files from the checked exact output directory. Final directory listing contains only 待确认学员.xlsx. Original source SHA256 unchanged. No database reads/writes, import-rule changes, dependencies or Git operations.
- Python compile and scoped diff checks passed. Ownership released.

## Follow-up registration: adjacent comparisons

- Reopen ownership for prepare_master.py, docs/operations/historical-student-import.md, this record and the same fixed master. User requests duplicate/related records adjacent. Reorder only, matching exact source name or contact; retain all values and handling notes, do not merge disputed identities or add worksheets/files. Verify adjacency, row multiset and source coverage, and keep future refresh ordering consistent.

## Follow-up delivery: adjacent comparisons

- Reordered existing 41 review rows in the same fixed master so exact same-name/contact groups are contiguous. Preserved row multiset, all source references, reasons and handling notes; no identity merge or added sheet. Future prepare_master.py refreshes use the same stable grouping.
- First replacement was blocked by an open workbook. User explicitly closed it; re-read the original to verify no intervening content edits, then atomically replaced the fixed file with the validated sorted content. Temporary workbook/inspect sidecar removed; output directory still contains one workbook.
- Saved-file checks passed for every duplicate group, exact values, one sheet/workbook, stable repeat ordering and ZIP. Preview inspected; Python compile and scoped diff checks passed. No database operations. Ownership released.

## Follow-up registration: separate disputed records

- User clarifies that the actual records requiring a choice must appear together. Reopen the same ownership. Display each original exception record separately, especially the four collapsed specification pairs; retain one sheet/file and adjacent same-name/contact records. Preserve handling notes, show each row's SKU/class/planner and the counterpart explicitly. Use original source reasons to distinguish name conflicts from SKU conflicts. Verify all 45 source records exactly once, adjacency and unchanged source/database state.

## Follow-up delivery: separate disputed records

- Updated the same fixed master to display all 45 original exceptions as individual rows. Four previously collapsed specification pairs now appear as eight adjacent records, each with its own SKU/class/planner/source row and explicit counterpart details. Original source reasons distinguish name conflicts from specification conflicts; no extra file, sheet or database change.
- Future preparation retains per-source handling notes before falling back to person-level notes, so two alternatives can receive different decisions. Verified all 45 source positions exactly once, every name/contact group adjacent, counterpart SKUs in details, distinct notes preserved without touching actual user notes, one file/sheet, ZIP/readback, Python compile and scoped diff checks. Inspected a rendered actual SKU pair. Documentation synchronized; ownership released.
