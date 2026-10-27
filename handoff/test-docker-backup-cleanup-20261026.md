# Docker backup cleanup — test-docker-backup-cleanup-20261026

## Registration — 2026-10-26T00:55:10.015715+00:00
- Owner: Codex /root; environment: test from /etc/zsjos/agent-environment; branch main; absolute worktree /opt/zsjos; base 8279212fb97129ba6b006dbb0452ad843dfde4b5; target branch/integration order None.
- Goal and authorization: user explicitly requested deleting Docker MySQL backups dated before October 20; cutoff 2026-10-20 exclusive, based on the timestamp in each filename.
- Ownership: /var/lib/docker/volumes/zsjos_mysql-backups/_data/zsjos-YYYYMMDD-HHMMSS.sql strictly before cutoff; this uniquely owned handoff record. Existing dirty files and other workstreams are preserved.
- Non-goals: other backup directories, retained backups, Docker volumes/containers, live MySQL data, service lifecycle, source changes, commits or pushes.
- Dependencies: exact directory and regular-file inspection; reject changed inode, size or mtime before deletion. No SQL execution or active database write.
- Impact: permanently unlink 15 backup files, 25704439918 logical bytes, 23.939 GiB allocated. Keep 6 backups dated on/after cutoff. No recovery copy created; restoration of deleted files requires an independent existing copy.
- Verification: revalidate all targets before unlink; compare retained files by inode/size/mtime; assert no matching older backups remain; inspect filesystem free space.
- Context: earlier diagnosis saw 97 backups; pre-deletion inspection sees 21 and approximately 50 GiB available. Earlier changes were not performed by this workstream.
- Initial guard rejected an incorrectly transcribed expected allocated-byte total before any writes/deletions. Re-inspection confirms the original observed logical-byte total and 15/6 file counts; exact current stat manifest follows.

## Exact deletion manifest

```json
[
  {
    "name": "zsjos-20260927-051733.sql",
    "inode": 974304,
    "size": 1019182467,
    "mtime_ns": 1790457469316454041,
    "allocated": 1019187200
  },
  {
    "name": "zsjos-20260929-140149.sql",
    "inode": 974311,
    "size": 1216087988,
    "mtime_ns": 1790661728532999462,
    "allocated": 1216094208
  },
  {
    "name": "zsjos-20260930-003816.sql",
    "inode": 974320,
    "size": 1260228645,
    "mtime_ns": 1790699923840661601,
    "allocated": 1260236800
  },
  {
    "name": "zsjos-20260930-004703.sql",
    "inode": 974325,
    "size": 1260266036,
    "mtime_ns": 1790700446129800019,
    "allocated": 1260273664
  },
  {
    "name": "zsjos-20260930-124401.sql",
    "inode": 974331,
    "size": 1294909937,
    "mtime_ns": 1790743469223544466,
    "allocated": 1294917632
  },
  {
    "name": "zsjos-20260930-125952.sql",
    "inode": 974336,
    "size": 1294911337,
    "mtime_ns": 1790744418251243297,
    "allocated": 1294917632
  },
  {
    "name": "zsjos-20261008-231558.sql",
    "inode": 974076,
    "size": 1652773168,
    "mtime_ns": 1791472616294614200,
    "allocated": 1652781056
  },
  {
    "name": "zsjos-20261008-233304.sql",
    "inode": 974082,
    "size": 1652784400,
    "mtime_ns": 1791473609317003277,
    "allocated": 1652789248
  },
  {
    "name": "zsjos-20261013-023528.sql",
    "inode": 974302,
    "size": 1977715094,
    "mtime_ns": 1791830158576051596,
    "allocated": 1977720832
  },
  {
    "name": "zsjos-20261013-024314.sql",
    "inode": 974308,
    "size": 1977715094,
    "mtime_ns": 1791830623304949720,
    "allocated": 1977720832
  },
  {
    "name": "zsjos-20261014-001856.sql",
    "inode": 974045,
    "size": 2065516560,
    "mtime_ns": 1791908367780477062,
    "allocated": 2065522688
  },
  {
    "name": "zsjos-20261015-125334.sql",
    "inode": 974236,
    "size": 2224820131,
    "mtime_ns": 1792040045182864006,
    "allocated": 2224824320
  },
  {
    "name": "zsjos-20261015-131225.sql",
    "inode": 974344,
    "size": 2224831542,
    "mtime_ns": 1792041176291655045,
    "allocated": 2224836608
  },
  {
    "name": "zsjos-20261015-133328.sql",
    "inode": 974345,
    "size": 2226942873,
    "mtime_ns": 1792042440605833372,
    "allocated": 2226950144
  },
  {
    "name": "zsjos-20261016-010336.sql",
    "inode": 974351,
    "size": 2355754646,
    "mtime_ns": 1792083849125790623,
    "allocated": 2355761152
  }
]
```

## Retained manifest

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
  }
]
```

## Delivery

```json
{
  "deleted_count": 15,
  "deleted_allocated_GiB": 23.939,
  "retained_count": 6,
  "retained_allocated_GiB": 19.075,
  "remaining_older_count": 0,
  "free_before_GiB": 49.247,
  "free_after_GiB": 73.186,
  "retained_metadata_unchanged": true
}
```

- Authorized deletion complete; ownership released. Other paths, live database data, services and existing Git changes were not modified.
