# Test disk cleanup — test-disk-cleanup-20261109

## Registration — 2026-11-09T10:04:54.316635+08:00
- Owner: Codex /root; environment test from /etc/zsjos/agent-environment.
- Branch main; worktree /opt/zsjos; base/HEAD e23674d600e61ac87f3b6c8a13a8d81051d7b6a0; target branch/integration order None.
- Goal/authorization: inspect disk; user explicitly requested retaining only the latest backup after the Docker MySQL backup volume was identified.
- Ownership: this record and the 18 older SQL backups under /var/lib/docker/volumes/zsjos_mysql-backups/_data.
- Non-goals: other backups, live database, services, containers, volumes, caches, unrelated repository changes, commits or pushes.
- Impact: permanently delete 18 older backups; retain zsjos-20261106-004041.sql. No recovery copy created; deleted history requires an independent existing copy.
- Dependencies: exact manifest and dump completion marker; no SQL execution.
- Verification: revalidate every target before unlink; verify retained metadata and remaining entries; compare filesystem free space. Restore testing is outside this cleanup.

## Pre-deletion manifest (last entry retained)

```json
[
  {
    "name": "zsjos-20261020-130109.sql",
    "inode": 971891,
    "size": 3061342635,
    "mtime_ns": 1792472508862090803,
    "allocated": 3061350400
  },
  {
    "name": "zsjos-20261021-000938.sql",
    "inode": 974356,
    "size": 3200842582,
    "mtime_ns": 1792512624049882529,
    "allocated": 3200847872
  },
  {
    "name": "zsjos-20261021-002315.sql",
    "inode": 974359,
    "size": 3201081718,
    "mtime_ns": 1792513442758118662,
    "allocated": 3201089536
  },
  {
    "name": "zsjos-20261021-122809.sql",
    "inode": 973004,
    "size": 3285341571,
    "mtime_ns": 1792556932779866145,
    "allocated": 3285360640
  },
  {
    "name": "zsjos-20261026-013747.sql",
    "inode": 974361,
    "size": 3866583159,
    "mtime_ns": 1792949941146803606,
    "allocated": 3866611712
  },
  {
    "name": "zsjos-20261026-020143.sql",
    "inode": 974365,
    "size": 3866614785,
    "mtime_ns": 1792951355379310715,
    "allocated": 3866685440
  },
  {
    "name": "zsjos-20261028-005133.sql",
    "inode": 918011,
    "size": 4306958170,
    "mtime_ns": 1793119950771729727,
    "allocated": 4306964480
  },
  {
    "name": "zsjos-20261028-010958.sql",
    "inode": 918020,
    "size": 4306961182,
    "mtime_ns": 1793121053493094072,
    "allocated": 4306968576
  },
  {
    "name": "zsjos-20261028-123837.sql",
    "inode": 917657,
    "size": 4416357939,
    "mtime_ns": 1793162374124134567,
    "allocated": 4416364544
  },
  {
    "name": "zsjos-20261028-232547.sql",
    "inode": 918064,
    "size": 4582861732,
    "mtime_ns": 1793201215843428290,
    "allocated": 4582866944
  },
  {
    "name": "zsjos-20261030-011936.sql",
    "inode": 918126,
    "size": 4808627215,
    "mtime_ns": 1793294436336374178,
    "allocated": 4808634368
  },
  {
    "name": "zsjos-20261031-002402.sql",
    "inode": 919473,
    "size": 4995374434,
    "mtime_ns": 1793377504689688871,
    "allocated": 4995379200
  },
  {
    "name": "zsjos-20261031-235516.sql",
    "inode": 919476,
    "size": 5110157780,
    "mtime_ns": 1793462185637218629,
    "allocated": 5110165504
  },
  {
    "name": "zsjos-20261103-000007.sql",
    "inode": 919480,
    "size": 5412179419,
    "mtime_ns": 1793635272631375283,
    "allocated": 5412184064
  },
  {
    "name": "zsjos-20261103-005942.sql",
    "inode": 919485,
    "size": 5412198308,
    "mtime_ns": 1793638848009857265,
    "allocated": 5412204544
  },
  {
    "name": "zsjos-20261103-123252.sql",
    "inode": 919488,
    "size": 5480077950,
    "mtime_ns": 1793680436624294913,
    "allocated": 5480083456
  },
  {
    "name": "zsjos-20261103-233412.sql",
    "inode": 919491,
    "size": 5575227930,
    "mtime_ns": 1793720118360405598,
    "allocated": 5575233536
  },
  {
    "name": "zsjos-20261104-232637.sql",
    "inode": 919507,
    "size": 5745881974,
    "mtime_ns": 1793806064795779069,
    "allocated": 5745889280
  },
  {
    "name": "zsjos-20261106-004041.sql",
    "inode": 919510,
    "size": 5926639277,
    "mtime_ns": 1793896913193133794,
    "allocated": 5926645760
  }
]
```

## Delivery — 2026-11-09T10:05:06.661325+08:00
- Branch/worktree/HEAD unchanged from registration.
- Completed authorized backup cleanup; ownership released.
- Changed repository file: handoff/test-disk-cleanup-20261109.md only.
- Verification: exact manifest checked before deletion; only latest file remains, unchanged inode/size/mtime/allocated bytes and completion marker checked.
- Initial registration attempt had a Python quoting syntax error before any file mutation; corrected registration succeeded.
- Dependency/integration impact: None. Remaining work: None for authorized cleanup. No restore test performed.

```json
{
  "deleted_count": 18,
  "deleted_allocated_GiB": 75.097,
  "retained": "zsjos-20261106-004041.sql",
  "retained_GiB": 5.52,
  "retained_metadata_unchanged": true,
  "free_before_GiB": 6.71,
  "free_after_GiB": 81.807
}
```
