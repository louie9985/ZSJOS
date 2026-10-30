# Registration — test-october-order-export-20261030

- Owner: /root; environment: test from /etc/zsjos/agent-environment; branch: main; worktree: /opt/zsjos; base: ba8c788f470eb96d22e8729a2d8f711d83c0c370; target branch/integration: None.
- Goal: one-time Zhongshijian Excel export of tenant-1 undeleted orders created during 2026-10, with exactly 学员姓名、联系方式、备注、来源、生成时间. Include every status and empty remarks; one row per order, creation time/id descending. Contact includes mobile and WeChat; source uses order number and historical submitter; time is order creation time in Asia/Shanghai; remark is order.remark verbatim.
- Ownership: this record; private script /tmp/zsjos-october-order-export-20261030.py; workbook /tmp/zsjos-october-order-export-20261030/10月份订单_2026年.xlsx. Sole writer /root, separate record to preserve unrelated work.
- Non-goals: product changes, database writes, permission/service changes, dependencies, branch/commit/push operations. No personal data in logs or handoff.
- Dependencies: configured test database, existing pymysql/openpyxl, gift purchase contact/source presentation and order historical actor contracts. Preliminary scope: 229 orders, including 74 superseded orders; no imported or repaired actor snapshots.
- Verification: database-enforced read-only consistent snapshot; exact date/tenant/deletion filters, counts and unique order IDs; UTF-8 HEX comparison; historical source evidence; workbook round-trip of every cell; literal text/formula safety, header/filter/freeze/wrapping. No application behavior change or browser acceptance required.

## Delivery — 2026-10-30T10:09:25+08:00

- Context: registration above; test/main /opt/zsjos; HEAD unchanged ba8c788f470eb96d22e8729a2d8f711d83c0c370; owner /root; integration None.
- Result: 229 October orders, 155 effective and 74 superseded; 160 nonempty remarks. Exactly five requested columns, one row per order, all states retained. All source names available from historical approval evidence; order number included in source to distinguish orders. Data reflects the export-time snapshot; October has not ended.
- Verification PASS: read-only repeatable-read consistent database snapshot, date/tenant/deletion scope, count/unique order IDs/order; UTF-8 HEX for names and remarks; all four business fields equal corresponding approval display snapshots; all 1,145 data cells round-trip unchanged; no formula cells or truncation; five headers, frozen header, filter and text wrapping verified. No interactive Excel rendering claimed.
- Output: /tmp/zsjos-october-order-export-20261030/10月份订单_2026年.xlsx; restricted permissions. SHA-256: 781255149b524bfc6fa8baf79449c2b723eebb30b77777361959e2ed272a0f45.
- Changed repository files: this handoff only. Private script and workbook are outside the repository. Existing unrelated edits preserved. No database writes, application implementation changes, new dependencies, service operations, commits or pushes. Remaining: None; ownership released.
