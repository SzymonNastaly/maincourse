# Migration: Hetzner → Netcup VPS, Hetzner Object Storage → Cloudflare R2

**Date:** 2026-08-26
**Status:** Design approved, pending spec review
**Driver:** Cost reduction. Netcup offers dedicated vCores at Hetzner's shared-vCPU price point; R2 removes Hetzner Object Storage's flat monthly minimum.

## Current State

| Piece | Today |
|---|---|
| Host | Kamal 2 → Hetzner VPS `49.13.125.220`, kamal-proxy + Let's Encrypt |
| Public hostname | `cook.hauptgang.app`, **proxied** through Cloudflare (`188.114.x.x`) |
| Database | SQLite ×4 (primary, cache, queue, cable) in docker volume `hauptgang_storage` |
| Backups | Litestream accessory → Hetzner OS bucket `hauptgang-backups` (fsn1) |
| Blobs | Active Storage `:hetzner` → bucket `hauptgang-production` (fsn1) |
| Credentials | Rails credentials `hetzner.*`, shared by Litestream and Active Storage |

## Decisions

1. **Target host:** Netcup x86 VPS (VPS 2000 G11 class). ARM was considered and rejected — the ARM line is frequently sold out, and x86 requires no Dockerfile arch work. `builder.arch` stays `amd64`.
2. **Target storage:** Cloudflare R2, **EU jurisdiction** (`<account_id>.eu.r2.cloudflarestorage.com`), for **both** buckets. Jurisdiction is fixed at bucket creation and cannot be changed later.
3. **Approach:** Two-phase — storage first (zero downtime), host second (short window). Only one variable changes at a time.
4. **Blob migration:** `rclone copy` + hard cutover. Active Storage keys are opaque and preserved, so **no database rewrite is required**.
5. **Downtime budget:** ~5–10 minutes for the host cutover.
6. **Litestream:** pin **≥ 0.5.4** on both the app image and the accessory.

## Pre-existing Defects Found During Design

These are latent bugs in the current setup, not migration work, but the migration depends on them being fixed.

### D1 — Litestream version skew
`Dockerfile:22` pins Litestream **0.3.13**; `config/deploy.yml` runs the accessory as `litestream/litestream` **unpinned** (`:latest`). Litestream 0.5.x uses the LTX format, which 0.3.x cannot read. The replicating sidecar and the `bin/kamal restore` alias may already be mutually incompatible. **Verify what is actually running in production before relying on restore.**

### D2 — Incomplete backup coverage
`config/litestream.yml` replicates only `production.sqlite3`. Production runs four SQLite databases (`config/database.yml`). `production_queue.sqlite3` holds Solid Queue state, including in-flight and scheduled jobs, and is currently **not backed up**.

### D3 — R2 checksum incompatibility
The project uses `aws-sdk-s3 1.217.0`, past the version where AWS enabled CRC32 request checksums by default. R2 rejects these headers; Active Storage uploads will fail immediately without the workaround flags in Section 1.

### Version pinning caution
Litestream 0.5.0 shipped with restore bugs. Separately, AWS SDK Go v2 broke uploads to all S3-compatible providers via aws-chunked encoding, fixed in Litestream **0.5.4**. Pin ≥ 0.5.4.

---

## Phase 1 — Storage to R2 (zero downtime, on the existing Hetzner box)

### 1.1 Buckets and credentials
Create two EU-jurisdiction R2 buckets: `hauptgang-production` (blobs) and `hauptgang-backups` (Litestream). Create an R2 API token scoped to both. Add an `r2:` namespace via `bin/rails credentials:edit`. The `hetzner:` namespace stays untouched for the whole phase.

### 1.2 Active Storage service
Add to `config/storage.yml`, alongside the existing `:hetzner` service (which is kept, not deleted):

```yaml
r2:
  service: S3
  endpoint: https://<account_id>.eu.r2.cloudflarestorage.com
  access_key_id: <%= Rails.application.credentials.dig(:r2, :access_key_id) %>
  secret_access_key: <%= Rails.application.credentials.dig(:r2, :secret_access_key) %>
  region: auto
  bucket: <%= Rails.application.credentials.dig(:r2, :bucket) || "hauptgang-production" %>
  request_checksum_calculation: when_required
  response_checksum_validation: when_required
```

The last two keys address D3. No bucket CORS configuration is needed — the app uses no Active Storage direct uploads.

### 1.3 Blob copy
1. `rclone copy` `fsn1.your-objectstorage.com/hauptgang-production` → R2 bucket. Run from the Hetzner box to avoid a round trip.
2. Immediately before the flip, re-run `rclone copy` to catch uploads that landed during the bulk copy.
3. `rclone check` to prove parity. This is the acceptance gate.
4. Flip `config.active_storage.service` to `:r2` in `config/environments/production.rb` and deploy.

**Rollback:** revert to `:hetzner` and redeploy. The Hetzner bucket is not deleted during this phase, so it remains a live fallback.

### 1.4 Litestream to R2
Three changes that must land together:

- `Dockerfile:22` — bump the pinned `.deb` from 0.3.13 to the chosen ≥0.5.4 release
- `config/deploy.yml` — pin the accessory to `litestream/litestream:<same-version>`
- `config/litestream.yml` — repoint at R2 with `region: auto`; 0.5.x auto-detects R2 and self-limits concurrency

Also extend `litestream.yml` to cover `production_queue.sqlite3` (addresses D2). Cache and cable remain excluded as genuinely disposable.

**To verify during implementation** (not assumed here): whether 0.5.x's config schema still uses a `replicas:` list or moved to a single `replica:` key, and the exact 0.5.x `.deb` asset filename.

**Format break:** 0.5.x cannot read 0.3.x generations, so the R2 replica starts as a fresh generation with no history. Until retention fills (72h), the fallbacks are the untouched Hetzner backup bucket and a manual volume tarball taken before the switch.

### 1.5 Soak
Run on R2 for roughly a week before starting Phase 2. Confirm image serving, uploads, and at least one successful `litestream restore` drill.

---

## Phase 2 — Host to Netcup

### 2.1 Provision
Netcup x86 VPS. Debian, SSH key auth, root login disabled, firewall limited to 22/80/443, Docker installed. Sizing note: the workload is SQLite + Puma + libvips variants + network-bound `ruby_llm` calls; 8 cores / 16 GB is generous, half that is sufficient.

### 2.2 Dry run (before the window)
`kamal setup` against Netcup while the Cloudflare origin still points at Hetzner. The new box builds, boots, and is exercised by IP or a temporary hostname. Nothing is committed.

### 2.3 The window (~5–10 min)
1. `kamal app stop` on Hetzner — writes cease, eliminating split-brain risk
2. Final Litestream sync
3. Tar the whole volume: `docker run --rm -v hauptgang_storage:/v ... tar czf` — captures all four DBs
4. Ship the tarball to Netcup, restore into the volume there
5. Boot on Netcup, smoke-test by IP
6. **Cross-check:** independently `litestream restore` the primary DB from R2 and compare row counts against the shipped copy. Two independent paths agreeing is the acceptance criterion for step 7.
7. Flip the Cloudflare origin A record. Because the hostname is proxied, this takes effect in seconds with no TTL wait.

### 2.4 Rollback
Flip the origin back and `kamal app boot` on Hetzner. The Hetzner box is stopped, never destroyed. Keep it paid-for and idle for ~2 weeks.

---

## Phase 3 — Cleanup (after ~2 week soak)

- Delete the `:hetzner` service from `config/storage.yml`
- Delete the `hetzner:` credentials namespace
- Remove the old IP from `config/deploy.yml` (both `servers.web` and the litestream accessory `host`)
- Update `docs/sqlite-backups-litestream.md`
- Cancel the Hetzner VPS and both Hetzner buckets last, together

## Files Touched

| File | Phase | Change |
|---|---|---|
| `config/storage.yml` | 1, 3 | Add `:r2`; later remove `:hetzner` |
| `config/environments/production.rb` | 1 | `active_storage.service` → `:r2` |
| `config/litestream.yml` | 1 | Repoint to R2; add queue DB |
| `Dockerfile` | 1 | Litestream 0.3.13 → ≥0.5.4 |
| `config/deploy.yml` | 1, 2, 3 | Pin accessory image; swap server IPs |
| `.kamal/secrets` | 1 | `hetzner.*` → `r2.*` fetches; R2 endpoint |
| `docs/sqlite-backups-litestream.md` | 3 | Update for R2 + new version |

## Open Items

- **Size of `hauptgang-production`** — drives rclone timing and confirms the cost premise. Not yet known.
- Cloudflare account: R2 enabled, EU jurisdiction available.
- Netcup VPS not yet ordered.
- Exact Litestream 0.5.x version to pin, and its config schema (see 1.4).

## Success Criteria

1. `rclone check` reports parity between the Hetzner and R2 blob buckets.
2. Active Storage images serve from R2 in production with no client-visible change.
3. A `litestream restore` from R2 produces a primary DB matching the live one by row count.
4. Host cutover completes within the downtime budget.
5. A rollback path exists at every step, and the Hetzner box survives the full soak period.
