# Registration — test-gift-purchase-export-20261029

- Owner: /root; environment: test from /etc/zsjos/agent-environment; branch: main; worktree: /opt/zsjos; base: ba3e687e678ce92b67f16615244e47007d0892d4; target branch/integration: None.
- Goal: user-requested one-time Excel export of the current gift purchase list using the existing seven business columns and multiline contact/source presentation. Use the page's default unpurchased filter, all matching records, generated_at/id descending.
- Ownership: this record; private temporary export script /tmp/zsjos-gift-purchase-export-20261029.py; delivery workbook /tmp/zsjos-gift-purchase-export-20261029/礼品采购清单_未采购_20261029.xlsx. One writer /root; concurrent unrelated work uses separate records. /mnt/data is unavailable (filesystem permission); use a restricted task-specific output directory.
- Non-goals: product implementation/export button, database writes, permissions, service changes, commits/pushes, unrelated edits. No private record values in logs or this record.
- Dependencies: existing test database connector; existing openpyxl/pymysql; tenant 1 Admin purchase-management scope and existing historical projection contract.
- Verification: database-enforced read-only consistent snapshot, exact filter/count/order, stored gift/actor evidence only, worksheet round-trip comparison of all seven cells per row, Chinese text/UTF-8 preservation, string cell types (including phone numbers and formula-like values), wrapping/column widths/frozen header/filter. No runtime behavior changes; browser application testing not applicable to this one-time file.

## Delivery

- Exported 54 unpurchased records, seven columns matching the current page, all matching pages, generated_at/id descending. Current active tenant-1 records are all unpurchased. Contact/source cells retain line breaks; action controls are excluded; header is frozen and filterable.
- All 54 records have linked active orders and approval snapshots, with no imported/repaired actor evidence; read source names from those snapshots only. Gift names come from saved gift snapshots. No current employee-name or gift-catalog substitution.
- PASS: read-only repeatable-read database snapshot; exact count/unique IDs; all 378 data cells read back equal generated values; representative source Chinese UTF-8 bytes checked via HEX for every student-name field; no Excel formulas; wrapping, seven headers, frozen header and filter range verified. Workbook SHA-256: a9f6f96da6e363a849822c80d87b518bf2b4958f77fb95bc99775e9cf0d9eb39.
- Output: /tmp/zsjos-gift-purchase-export-20261029/礼品采购清单_未采购_20261029.xlsx (restricted file/directory permissions). No database writes, product code changes, service operations, commits or pushes. Existing unrelated changes preserved. No interactive Excel rendering claimed; workbook content and layout properties verified through openpyxl.
- Initial aggregate reporting needed MySQL BIT-to-number conversion; fixed before final verification. Initial /mnt/data output directory was not writable; no artifact was created there and the registered restricted output path was used instead. Ownership released.
