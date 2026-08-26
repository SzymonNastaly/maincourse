# Pre-Migration Production Baseline

Measured 2026-08-26 against Hetzner VPS `49.13.125.220` (`hauptgang-1`, up 196 days).
All measurements read-only. Referenced by Task 11 Step 6 and Task 12 Step 1.

## Litestream (accessory)

`v0.5.11` — container `hauptgang-litestream`, up 4 months, pulled from unpinned `litestream/litestream:latest`.

## Litestream (app image)

`v0.3.13` — pinned at `Dockerfile:22`.

## Defect D1: CONFIRMED, and production disaster recovery is currently broken

The sidecar writes LTX (0.5.x); the app image's binary reads only the 0.3.x WAL format.
The documented `bin/kamal restore` alias runs the **in-image 0.3.13** binary, so it cannot
see any existing backup:

```
$ docker exec hauptgang-web-... litestream restore -config /rails/config/litestream.yml \
    -o /tmp/d1-proof.sqlite3 /rails/storage/production.sqlite3
level=ERROR msg="failed to run" error="no matching backups found"
```

**The backups themselves are fine.** The same restore run with the 0.5.11 sidecar succeeded:

| Check | Restored from R2 | Live |
|---|---|---|
| `PRAGMA integrity_check` | `ok` | — |
| recipes | 161 | 161 |
| users | 21 | 21 |

So this is a *tooling* failure, not data loss: backups are valid and current, but the
documented recovery procedure does not work. Task 7 fixes it by pinning both sides to
v0.5.16. Until then, recovery requires the 0.5.x sidecar binary, not `bin/kamal restore`.

## Blob bucket size

`hetzner:hauptgang-production` — **509 objects, 90.624 MiB** (95,026,549 bytes).

Matches `active_storage_blobs` exactly (509), so no orphaned or missing objects.
At this size an `rclone copy` to R2 takes seconds, not hours.

## Backup bucket size

`hetzner:hauptgang-backups` — 5 objects, 643.529 KiB.

## Row counts

| Table | Count |
|---|---|
| recipes | 161 |
| users | 21 |
| active_storage_blobs | 509 |
| active_storage_attachments | 509 |

## Volume contents (`hauptgang_storage`, 12 MB total, 64 GB free on host)

`production.sqlite3` (1.1M), `production_cache.sqlite3` (52K),
`production_queue.sqlite3` (888K), `production_cable.sqlite3` (40K), plus `-wal`/`-shm`.

## Cost premise: confirmed, and stronger than assumed

90.6 MiB is **inside R2's 10 GB free tier**, so blob storage on R2 costs approximately
nothing. Hetzner Object Storage bills a flat monthly minimum regardless of usage.
The storage half of this migration is a near-total cost elimination, not a reduction.

This also retroactively settles the R2-vs-Backblaze question: at this scale the
price difference between them was zero either way.
