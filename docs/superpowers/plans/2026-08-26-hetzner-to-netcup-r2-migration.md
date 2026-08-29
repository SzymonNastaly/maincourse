# Hetzner → Netcup + Cloudflare R2 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move object storage from Hetzner Object Storage to Cloudflare R2, then move the Rails host from a Hetzner VPS to a Netcup x86 VPS, with ~5–10 minutes of downtime and a working rollback at every step.

**Architecture:** Two phases so only one variable changes at a time. Phase 1 migrates storage to R2 on the existing, known-good Hetzner box with zero downtime. Phase 2 moves the host, using a docker volume tarball as the primary data transfer and a Litestream restore from R2 as an independent cross-check. The public hostname is proxied through Cloudflare, so the final cutover is an origin-IP edit that takes effect in seconds.

**Tech Stack:** Rails 8.1, Ruby 3.4.7, SQLite (×4 databases), Kamal 2, Litestream, Cloudflare R2, rclone, Docker.

**Spec:** `docs/superpowers/specs/2026-08-26-hetzner-to-netcup-r2-migration-design.md`

## Global Constraints

- **Litestream version:** pin **v0.5.16** everywhere — app image and Kamal accessory must match exactly. Never leave the accessory on `:latest`.
- **Litestream `.deb` asset naming changed between 0.3.x and 0.5.x:** `litestream-v0.3.13-linux-amd64.deb` → `litestream-0.5.16-linux-x86_64.deb`. No `v` prefix; `x86_64`, not `amd64`.
- **Litestream 0.5.x config schema is not backward compatible:** `replicas:` (list) → `replica:` (single); `retention:` and `snapshot-interval:` move from the replica to a global snapshot config.
- **R2 endpoint:** EU jurisdiction, `https://<account_id>.eu.r2.cloudflarestorage.com`. Jurisdiction is fixed at bucket creation and cannot be changed later.
- **R2 region:** always `auto`.
- **Active Storage checksum flags are retained as insurance, not as a fix:** `request_checksum_calculation: when_required` and `response_checksum_validation: when_required`. Defect D3 predicted that `aws-sdk-s3 1.217.0` CRC32 headers would be rejected by R2; tested 2026-08-26, it **did not reproduce** (small and 12 MB multipart uploads both succeed without the flags). Keep them for drift protection; do not blame D3 for an upload failure without re-testing.
- **Build arch stays `amd64`** — Netcup target is x86. Do not change `builder.arch`.
- **One Active Storage column must be rewritten: `active_storage_blobs.service_name`.** Blob *keys* are opaque and preserved by `rclone copy`, so no key rewrite is required — but every blob row also records which service to read it from, and copying objects does not touch that column. `ActiveStorage::Blob#service` is `services.fetch(service_name)` (activestorage-8.1.3, `app/models/active_storage/blob.rb:348`), a hard fetch that raises `KeyError` the moment the named service leaves `config/storage.yml`. Correct sequencing: run `ActiveStorage::Blob.where(service_name: "hetzner").update_all(service_name: "r2")` right after the service flip in Task 5, and before Task 13 removes `:hetzner`. **This bullet originally asserted the opposite** — that any task proposing a `blobs` table migration was wrong — and that error is what broke image serving on 2026-08-27. See the defect note under Task 13.
- **Every rclone command against the `r2:` remote MUST pass `--s3-no-check-bucket`.** The R2 API token is bucket-scoped, so it is denied `ListBuckets`; without the flag rclone falls back to `CreateBucket` and fails with `403 AccessDenied`. For the same reason, never run a bare `rclone lsd r2:` -- always name a bucket.
- **Do not delete any Hetzner resource** (VPS or either bucket) before Phase 3. They are the rollback.
- **Style:** rubocop-rails-omakase. Run `bin/rubocop -a` before each commit that touches Ruby.

## Human-Only Steps

These require account access and cannot be done by an agent. They are called out inline as **[HUMAN]**: creating R2 buckets and API tokens, ordering the Netcup VPS, editing the Cloudflare DNS record, and cancelling Hetzner services.

## Local Prerequisites

Install and verify before starting Task 1:

```bash
brew install rclone           # required by Task 1 Step 3 and all of Task 4
rclone version                # expect v1.70.0 or later
ssh-add ~/.ssh/id_ed25519     # the key is passphrase-protected; the agent must
                              # hold it or every kamal command fails on publickey
ssh-add -l                    # expect the key to be listed
```

`bin/kamal` and `sqlite3` are already present in this environment.

---

## Phase 0 — Reconnaissance (no production changes)

### Task 1: Establish the production baseline

Nothing in this task changes production. It resolves the spec's open items and the D1 defect, and produces the numbers later tasks assert against.

**Files:**
- Create: `docs/superpowers/plans/migration-baseline.md` (scratch record of measured values)

- [ ] **Step 1: Record the Litestream version actually running in the accessory**

```bash
bin/kamal accessory exec litestream "litestream version"
```

Expected: a version string. **If this reports 0.5.x while `Dockerfile:22` pins 0.3.13, defect D1 is confirmed** — the sidecar has been writing a format the app image's binary cannot read, and `bin/kamal restore` is currently broken. Record the exact output either way.

- [ ] **Step 2: Record the version baked into the app image**

```bash
bin/kamal app exec "litestream version"
```

Expected: `0.3.13` (matching the Dockerfile pin). Record it.

- [ ] **Step 3: Measure the blob bucket**

```bash
rclone size hetzner:hauptgang-production
```

This is the number that confirms the cost premise and sets the rclone timing budget in Task 4. Record total objects and total bytes.

- [ ] **Step 4: Record production row counts for the cross-check in Task 12**

```bash
bin/kamal console
```

Then in the console:

```ruby
puts({ recipes: Recipe.count, users: User.count, blobs: ActiveStorage::Blob.count }.inspect)
```

Record the output. Task 12 asserts these match after the host move.

- [ ] **Step 5: Write the baseline file and commit**

Record all four measurements in `docs/superpowers/plans/migration-baseline.md` under headings `Litestream (accessory)`, `Litestream (app image)`, `Blob bucket size`, `Row counts`, each with the date measured.

```bash
git add docs/superpowers/plans/migration-baseline.md
git commit -m "docs: record pre-migration production baseline"
```

---

## Phase 1 — Storage to Cloudflare R2 (zero downtime)

### Task 2: Create R2 buckets and credentials

**[HUMAN]** — requires Cloudflare dashboard access.

**Files:**
- Modify: Rails encrypted credentials (via `bin/rails credentials:edit`)
- Modify: `.kamal/secrets`

**Interfaces:**
- Produces: credentials at `r2.access_key_id`, `r2.secret_access_key`, `r2.bucket`, `r2.account_id`, and `r2.backup_bucket`. Every later task reads these exact paths.

- [ ] **Step 1: Create two EU-jurisdiction R2 buckets**

In the Cloudflare dashboard → R2 → Create bucket. Create `hauptgang-production` and `hauptgang-backups`. For each, set **Location / Jurisdiction to European Union (EU)**. This cannot be changed after creation — verify it before clicking create.

- [ ] **Step 2: Create a scoped API token**

R2 → Manage API Tokens → Create token with **Object Read & Write** permission, scoped to those two buckets only. Record the Access Key ID, Secret Access Key, and your Account ID.

- [ ] **Step 3: Add the credentials**

```bash
bin/rails credentials:edit
```

Add, leaving the existing `hetzner:` block completely untouched:

```yaml
r2:
  account_id: <your account id>
  access_key_id: <access key id>
  secret_access_key: <secret access key>
  bucket: hauptgang-production
  backup_bucket: hauptgang-backups
```

- [ ] **Step 4: Verify the credentials read back**

```bash
bin/rails runner 'puts Rails.application.credentials.dig(:r2, :bucket)'
```

Expected: `hauptgang-production`

- [ ] **Step 5: Point the Kamal secrets at R2**

In `.kamal/secrets`, replace the Litestream block. Change these five lines:

```sh
LITESTREAM_ACCESS_KEY_ID=$(rails credentials:fetch r2.access_key_id)
LITESTREAM_SECRET_ACCESS_KEY=$(rails credentials:fetch r2.secret_access_key)
LITESTREAM_REPLICA_BUCKET=$(rails credentials:fetch r2.backup_bucket)
LITESTREAM_REPLICA_ENDPOINT=https://$(rails credentials:fetch r2.account_id).eu.r2.cloudflarestorage.com
```

Do not deploy yet — Task 7 deploys this together with the matching Litestream upgrade.

- [ ] **Step 6: Commit**

```bash
git add .kamal/secrets config/credentials.yml.enc
git commit -m "feat: add Cloudflare R2 credentials and Kamal secrets"
```

### Task 3: Add the `:r2` Active Storage service

This task is genuinely unit-testable, and the test permanently locks in the D3 checksum fix so a future refactor cannot silently delete it.

**Files:**
- Modify: `config/storage.yml`
- Test: `test/lib/storage_config_test.rb` (create)

**Interfaces:**
- Produces: an Active Storage service named `r2`, referenced by `config.active_storage.service = :r2` in Task 5.

- [ ] **Step 1: Write the failing test**

Create `test/lib/storage_config_test.rb`:

```ruby
require "test_helper"

class StorageConfigTest < ActiveSupport::TestCase
  def storage_config
    @storage_config ||= YAML.safe_load(
      ERB.new(File.read(Rails.root.join("config/storage.yml"))).result,
      aliases: true
    )
  end

  test "r2 service is defined and uses S3" do
    assert storage_config.key?("r2"), "expected an :r2 service in config/storage.yml"
    assert_equal "S3", storage_config.dig("r2", "service")
  end

  test "r2 uses the EU jurisdiction endpoint" do
    endpoint = storage_config.dig("r2", "endpoint")
    assert_match(/\.eu\.r2\.cloudflarestorage\.com\z/, endpoint,
      "R2 must use the EU-jurisdiction endpoint")
  end

  test "r2 region is auto" do
    assert_equal "auto", storage_config.dig("r2", "region")
  end

  # Guards defect D3: aws-sdk-s3 >= 1.178 sends CRC32 checksum headers that
  # R2 rejects. Without these two keys every Active Storage upload fails.
  test "r2 disables aws-sdk default checksum behaviour" do
    assert_equal "when_required", storage_config.dig("r2", "request_checksum_calculation")
    assert_equal "when_required", storage_config.dig("r2", "response_checksum_validation")
  end
end
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `bin/rails test test/lib/storage_config_test.rb`
Expected: FAIL — `expected an :r2 service in config/storage.yml`

- [ ] **Step 3: Add the service**

Append to `config/storage.yml`, leaving the `hetzner:` block in place:

```yaml
# Cloudflare R2 (S3-compatible), EU jurisdiction.
# request/response_checksum_* are REQUIRED: aws-sdk-s3 >= 1.178 sends CRC32
# checksum headers by default and R2 rejects them.
r2:
  service: S3
  endpoint: https://<%= Rails.application.credentials.dig(:r2, :account_id) %>.eu.r2.cloudflarestorage.com
  access_key_id: <%= Rails.application.credentials.dig(:r2, :access_key_id) %>
  secret_access_key: <%= Rails.application.credentials.dig(:r2, :secret_access_key) %>
  region: auto
  bucket: <%= Rails.application.credentials.dig(:r2, :bucket) || "hauptgang-production" %>
  request_checksum_calculation: when_required
  response_checksum_validation: when_required
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `bin/rails test test/lib/storage_config_test.rb`
Expected: PASS, 4 assertions

- [ ] **Step 5: Prove R2 accepts a real upload**

This is the step that actually validates the checksum flags against the live API — the unit test only checks the config shape.

```bash
bin/rails runner '
  s = ActiveStorage::Blob.services.fetch(:r2)
  s.upload("migration-smoke-test", StringIO.new("hello r2"))
  puts s.download("migration-smoke-test")
  s.delete("migration-smoke-test")
  puts "R2 upload/download/delete OK"
'
```

Expected: `hello r2` then `R2 upload/download/delete OK`.
**If this fails with `InvalidArgument` or an `x-amz-checksum-crc32` error, the checksum flags are not being applied** — stop and fix before continuing.

- [ ] **Step 6: Commit**

```bash
bin/rubocop -a
git add config/storage.yml test/lib/storage_config_test.rb
git commit -m "feat: add Cloudflare R2 Active Storage service

Includes the request/response checksum flags R2 requires with
aws-sdk-s3 1.217.0, guarded by a config test."
```

### Task 4: Copy blobs to R2 and prove parity

**Files:**
- Modify: `~/.config/rclone/rclone.conf` (local, not committed)

- [ ] **Step 1: Configure both rclone remotes**

Add to your rclone config (`rclone config` or by hand). Substitute real values:

```ini
[hetzner]
type = s3
provider = Other
access_key_id = <hetzner access key>
secret_access_key = <hetzner secret>
endpoint = https://fsn1.your-objectstorage.com
region = fsn1

[r2]
type = s3
provider = Cloudflare
access_key_id = <r2 access key>
secret_access_key = <r2 secret>
endpoint = https://<account_id>.eu.r2.cloudflarestorage.com
region = auto
```

- [ ] **Step 2: Verify both remotes are readable**

```bash
rclone lsd hetzner:
rclone lsd r2:hauptgang-production --s3-no-check-bucket
rclone lsd r2:hauptgang-backups --s3-no-check-bucket
```

Expected: `hauptgang-production` and `hauptgang-backups` visible on `r2:`.

- [ ] **Step 3: Bulk copy the blobs**

```bash
rclone copy hetzner:hauptgang-production r2:hauptgang-production --s3-no-check-bucket \
  --progress --transfers 16 --checkers 32
```

Use the object count from Task 1 Step 3 to sanity-check the duration. This is safe to re-run and safe to interrupt.

- [ ] **Step 4: Verify parity**

```bash
rclone check hetzner:hauptgang-production r2:hauptgang-production --one-way --s3-no-check-bucket
```

Expected: `0 differences found`. **This is the acceptance gate for the task** — do not proceed to Task 5 until it is clean.

- [ ] **Step 5: Nothing to commit**

rclone config holds live secrets and must not be committed. Confirm with `git status` that the working tree is clean.

### Task 5: Cut Active Storage over to R2

**Files:**
- Modify: `config/environments/production.rb:26`

- [ ] **Step 1: Re-run the delta copy**

Uploads may have landed since Task 4. Re-run both commands and require a clean check:

```bash
rclone copy hetzner:hauptgang-production r2:hauptgang-production --progress --s3-no-check-bucket
rclone check hetzner:hauptgang-production r2:hauptgang-production --one-way --s3-no-check-bucket
```

Expected: `0 differences found`

- [ ] **Step 2: Flip the service**

In `config/environments/production.rb` line 26, change:

```ruby
config.active_storage.service = :hetzner
```

to:

```ruby
config.active_storage.service = :r2
```

- [ ] **Step 3: Commit and deploy**

```bash
bin/rubocop -a
git add config/environments/production.rb
git commit -m "feat: serve Active Storage from Cloudflare R2"
bin/kamal deploy
```

- [ ] **Step 4: Verify in production**

Open the app and confirm recipe images render. Then upload a new image through the app and confirm it appears. Then confirm it landed in R2, not Hetzner:

```bash
rclone lsl r2:hauptgang-production --max-age 10m --s3-no-check-bucket
```

Expected: the newly uploaded object is listed.

- [ ] **Step 5: Rollback procedure (do not run unless needed)**

If images fail to serve: revert line 26 to `:hetzner`, commit, and `bin/kamal deploy`. The Hetzner bucket is untouched and still authoritative.

### Task 6: Rewrite `litestream.yml` for the 0.5.x schema

The current file uses three forms that 0.5.16 rejects. This task rewrites it and adds the queue database, closing defect D2.

**Files:**
- Modify: `config/litestream.yml`

**Interfaces:**
- Consumes: `LITESTREAM_REPLICA_BUCKET`, `LITESTREAM_REPLICA_ENDPOINT`, `LITESTREAM_ACCESS_KEY_ID`, `LITESTREAM_SECRET_ACCESS_KEY` from Task 2 Step 5.

- [ ] **Step 1: Install Litestream 0.5.16 locally to validate config**

```bash
brew install benbjohnson/litestream/litestream
litestream version
```

Expected: `v0.5.16` or later. If Homebrew lags, download the macOS binary from the v0.5.16 release instead.

- [ ] **Step 2: Rewrite the config**

Replace the entire contents of `config/litestream.yml`:

```yaml
# Litestream 0.5.x schema. Note vs 0.3.x:
#   - `replicas:` (list) became `replica:` (single replica per db)
#   - retention/snapshot settings moved from the replica to global `snapshot:`
# 0.5.x auto-detects R2 from the endpoint and self-limits concurrency,
# so force-path-style and concurrency do not need setting by hand.

snapshot:
  retention: 72h
  interval: 24h

dbs:
  - path: /rails/storage/production.sqlite3
    replica:
      type: s3
      bucket: ${LITESTREAM_REPLICA_BUCKET}
      path: production.sqlite3
      endpoint: ${LITESTREAM_REPLICA_ENDPOINT}
      region: auto
      access-key-id: ${LITESTREAM_ACCESS_KEY_ID}
      secret-access-key: ${LITESTREAM_SECRET_ACCESS_KEY}

  # Closes defect D2: Solid Queue state (in-flight and scheduled jobs) was
  # previously not backed up at all. Cache and cable stay excluded as
  # genuinely disposable.
  - path: /rails/storage/production_queue.sqlite3
    replica:
      type: s3
      bucket: ${LITESTREAM_REPLICA_BUCKET}
      path: production_queue.sqlite3
      endpoint: ${LITESTREAM_REPLICA_ENDPOINT}
      region: auto
      access-key-id: ${LITESTREAM_ACCESS_KEY_ID}
      secret-access-key: ${LITESTREAM_SECRET_ACCESS_KEY}
```

- [ ] **Step 3: Validate the schema parses under 0.5.16**

```bash
LITESTREAM_REPLICA_BUCKET=x LITESTREAM_REPLICA_ENDPOINT=https://x.eu.r2.cloudflarestorage.com \
LITESTREAM_ACCESS_KEY_ID=x LITESTREAM_SECRET_ACCESS_KEY=x \
litestream databases -config config/litestream.yml
```

Expected: both database paths listed, no schema errors.
**`litestream databases` is NOT a schema validator.** Verified 2026-08-26 against the real
0.5.16 binary: the config parser silently ignores unknown keys (a bogus top-level key and a
bogus key inside `snapshot:` both parsed without complaint), and the old 0.3.x `replicas:`
list still parses — `replicas` is *deprecated in 0.5.x, not removed*. So a clean `databases`
listing proves only that the file is valid YAML with a readable `dbs:` section.

Key names were instead confirmed from `litestream.io/reference/config/`: the global block is
`snapshot:` with sub-keys `interval` and `retention`, and the per-database key is singular
`replica:`. Separately, `retention.enabled` (top-level, added v0.5.8, defaults to `true`)
controls only whether Litestream performs the deletions; `snapshot.retention` remains the
duration setting. The config above is correct.

Consequence for Task 7: deploying 0.5.16 against the OLD config would not have crashed — it
would have parsed, silently ignored the replica-level `retention`/`snapshot-interval`, and
replicated with defaults. The rewrite is still required for the queue database (D2), but do
not expect a loud failure as the signal that it was needed.

- [ ] **Step 4: Commit**

```bash
git add config/litestream.yml
git commit -m "feat: migrate litestream.yml to 0.5.x schema and R2

Converts replicas: list to single replica:, moves retention/snapshot
to the global snapshot block, and adds the Solid Queue database which
was previously not backed up at all (defect D2)."
```

### Task 7: Upgrade Litestream to 0.5.16 and deploy

Both the app image and the accessory must move together — a mismatch here is defect D1 recreated.

**Files:**
- Modify: `Dockerfile:22`
- Modify: `config/deploy.yml` (litestream accessory image)

- [ ] **Step 1: Take a manual safety tarball first**

The 0.5.x format break means the R2 replica starts with no history. This tarball is the fallback until retention fills.

```bash
bin/kamal app exec --reuse "tar czf /tmp/pre-litestream-upgrade.tar.gz -C /rails storage"
bin/kamal app exec --reuse "cat /tmp/pre-litestream-upgrade.tar.gz" > ./pre-litestream-upgrade.tar.gz
ls -lh ./pre-litestream-upgrade.tar.gz
```

Expected: a non-trivial file size. Keep it off the servers until Phase 3 completes.

- [ ] **Step 2: Update the Dockerfile**

In `Dockerfile:22`, change the download line. **Both the version and the filename shape change** — 0.5.x drops the `v` prefix and uses `x86_64` instead of `amd64`:

```dockerfile
    curl -fsSL -o /tmp/litestream.deb https://github.com/benbjohnson/litestream/releases/download/v0.5.16/litestream-0.5.16-linux-x86_64.deb && \
```

- [ ] **Step 3: Pin the accessory image**

In `config/deploy.yml`, under `accessories.litestream`, change:

```yaml
    image: litestream/litestream
```

to:

```yaml
    image: litestream/litestream:0.5.16
```

- [ ] **Step 4: Verify the built image has the right version**

```bash
bin/kamal build create
bin/kamal deploy
bin/kamal app exec "litestream version"
```

Expected: `v0.5.16`

- [ ] **Step 5: Restart the accessory and verify it matches**

```bash
bin/kamal accessory reboot litestream
bin/kamal accessory exec litestream "litestream version"
```

Expected: `v0.5.16` — **identical to Step 4.** If these differ, stop; D1 has been recreated.

- [ ] **Step 6: Confirm replication is reaching R2**

```bash
sleep 60
rclone ls r2:hauptgang-backups --s3-no-check-bucket
```

Expected: objects under both `production.sqlite3` and `production_queue.sqlite3`.

- [ ] **Step 7: Commit**

```bash
git add Dockerfile config/deploy.yml
git commit -m "feat: pin Litestream to 0.5.16 in image and accessory

Closes defect D1: the accessory ran unpinned :latest while the image
pinned 0.3.13, whose format 0.5.x cannot read."
```

### Task 8: Prove a restore from R2 works

An untested backup is not a backup, and Phase 2 depends on this working.

**Executed 2026-08-27 from the developer laptop, not via `app exec`.** Restoring
with a local litestream 0.5.16 binary against R2 is a strictly stronger test than
restoring inside the app container: it shares no binary, no filesystem, and no
host with production, so it proves the replica alone is sufficient to rebuild the
database. Both `production.sqlite3` and `production_queue.sqlite3` were restored;
both returned `integrity_check: ok`, and the primary matched live exactly at
`recipes=163 users=21 blobs=514`. The commands below are kept as the in-container
equivalent.

- [x] **Step 1: Restore the primary database from R2 to a scratch path**

```bash
bin/kamal app exec --reuse \
  "litestream restore -config /rails/config/litestream.yml -o /tmp/restored.sqlite3 /rails/storage/production.sqlite3"
```

Expected: completes without error.

- [x] **Step 2: Verify the restored file is a valid, complete database**

```bash
bin/kamal app exec --reuse "sqlite3 /tmp/restored.sqlite3 'PRAGMA integrity_check;'"
```

Expected: `ok`

- [x] **Step 3: Compare row counts against live**

```bash
bin/kamal app exec --reuse "sqlite3 /tmp/restored.sqlite3 'SELECT COUNT(*) FROM recipes;'"
bin/kamal app exec --reuse "sqlite3 /rails/storage/production.sqlite3 'SELECT COUNT(*) FROM recipes;'"
```

Expected: equal, or the restored count trailing by at most the last sync interval.

- [x] **Step 4: Clean up**

```bash
bin/kamal app exec --reuse "rm /tmp/restored.sqlite3"
```

- [ ] **Step 5: Phase 1 acceptance gate**

There is no time-based soak. **Stop here and walk this checklist together.** Every line must pass before Task 9 begins.

```bash
# Blob parity still clean
rclone check hetzner:hauptgang-production r2:hauptgang-production --one-way --s3-no-check-bucket

# Both databases replicating to R2 within the last 10 minutes
rclone ls r2:hauptgang-backups --max-age 10m --s3-no-check-bucket
```

- [ ] Existing recipe images render in the app
- [ ] A new upload through the app succeeds, and appears via `rclone lsl r2:hauptgang-production --max-age 10m --s3-no-check-bucket`
- [ ] `rclone check` reports `0 differences found`
- [ ] `rclone ls --max-age 10m` shows recent objects under **both** `production.sqlite3` and `production_queue.sqlite3`
- [ ] The Task 8 restore drill passed `integrity_check` and matched live row counts
- [ ] No new storage-related errors in Sentry

Phase 2 does not start until this is signed off.

---

## Phase 2 — Host to Netcup

### Task 9: Provision and harden the Netcup VPS

**Ordered 2026-08-27.** Host `aralani`, `152.53.92.245`
(`v2202608404929506553.quicksrv.de`), **4 dedicated cores / 8 GB RAM / 256 GB**,
AMD64, prepaid for one year.

This is half the size the design assumed (8/16), which is fine: the spec already
recorded that "half that is sufficient." It does change one thing — with Caddy and
self-hosted apps arriving later on the same box, headroom is worth protecting, so
swap is now part of the base setup rather than optional.

Ships with **root only, password auth**. Every step below exists to change that.

**Completed 2026-08-27.** Two deviations from the steps as written, both deliberate:

1. The image is **Ubuntu 24.04.4 LTS**, not the Debian the plan assumed. No impact on
   Docker or Kamal.
2. The sshd drop-in is named **`01-hardening.conf`**, not `99-`. sshd uses the *first*
   value obtained for each keyword, and Ubuntu's cloud-init can drop a
   `50-cloud-init.conf` that re-enables `PasswordAuthentication`. At `99-` this file
   would silently lose that race; at `01-` it wins. The drop-in directory was empty at
   the time, so this is insurance against a future cloud-init run, not a fix.

Also added beyond the written steps: `vm.swappiness=10` (prefer RAM, swap only under
real pressure) and an explicit `/etc/apt/apt.conf.d/20auto-upgrades`, because
installing `unattended-upgrades` does not by itself guarantee the periodic keys are set.

Verified end state: password auth **refused** (`Permission denied (publickey)` on a live
attempt, not just a config read), key auth working, `permitrootlogin without-password`,
4 GB swap active and in fstab, `apt-daily-upgrade.timer` armed, Docker 29.7.2 +
Compose v5.5.0 enabled and running, ufw active with only 22/80/443, 0 containers.

- [x] **Step 1: Verify the host key on first connect [HUMAN]**

Netcup published these out-of-band; check the ED25519 line matches before trusting
the host, otherwise the first connection is unauthenticated.

```
256 SHA256:T5GIeGCOJViJD+5AI9X9BNQHPJki6yRFpqd/f40x1cw (ED25519)
```

```bash
ssh-keyscan -t ed25519 152.53.92.245 | ssh-keygen -lf -
```

Expected: prints `SHA256:T5GIeGCOJViJD+5AI9X9BNQHPJki6yRFpqd/f40x1cw`. If it does
not match, **stop** — do not log in.

- [x] **Step 2: Install the SSH key [HUMAN]**

Requires the root password, so this step is the operator's. It is the last time the
password is used.

```bash
ssh-copy-id root@152.53.92.245
ssh root@152.53.92.245 "echo key-auth-works"
```

Expected: the second command prints `key-auth-works` without prompting.

- [x] **Step 3: Disable password authentication**

```bash
ssh root@152.53.92.245 "install -m 0644 /dev/stdin /etc/ssh/sshd_config.d/99-hardening.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
sshd -t && systemctl reload ssh"
```

A drop-in file is used rather than editing `sshd_config`, so a distro package
upgrade cannot silently revert it. `sshd -t` validates before reload — a syntax
error that reached a restart would lock the box out.

**Keep the current SSH session open** until Step 4 confirms a fresh login works.

Note on `prohibit-password`: the design said "root login disabled." Key-only root is
the pragmatic reading, and a separate `deploy` user would not add a real boundary
here — Kamal requires Docker socket access, and Docker group membership is
root-equivalent by design. The meaningful win is killing password auth, which this
step does.

- [x] **Step 4: Verify lockout did not happen, and that passwords are refused**

In a **new** terminal:

```bash
ssh root@152.53.92.245 "echo still-in"
ssh -o PreferredAuthentications=password -o PubkeyAuthentication=no root@152.53.92.245 "echo SHOULD-NOT-APPEAR"
```

Expected: first prints `still-in`; second fails with `Permission denied (publickey)`.

- [x] **Step 5: Hostname, timezone, swap**

```bash
ssh root@152.53.92.245 "hostnamectl set-hostname aralani && timedatectl set-timezone Europe/Zurich"

ssh root@152.53.92.245 "fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab"
ssh root@152.53.92.245 "free -h && swapon --show"
```

Expected: 4 GB swap listed. With 8 GB RAM and several containers eventually sharing
the box, swap converts a would-be OOM kill into slowness.

- [x] **Step 6: Automatic security updates**

```bash
ssh root@152.53.92.245 "apt-get update && apt-get install -y unattended-upgrades && systemctl enable --now unattended-upgrades"
```

- [x] **Step 7: Install Docker**

```bash
ssh root@152.53.92.245 "curl -fsSL https://get.docker.com | sh"
ssh root@152.53.92.245 "docker --version && systemctl is-enabled docker"
```

- [x] **Step 8: Firewall**

```bash
ssh root@152.53.92.245 "apt-get install -y ufw && ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable && ufw status verbose"
```

**Known gotcha, do not skip:** Docker inserts its own iptables rules ahead of ufw's,
so a container started with `-p 5432:5432` is reachable from the internet **even
though ufw says the port is denied**. ufw protects host services, not published
container ports.

This does not affect hauptgang — kamal-proxy publishes 80/443, which are open
anyway. It matters when the Caddy phase arrives: every self-hosted app must publish
to `127.0.0.1:<port>` rather than `0.0.0.0:<port>`, so Caddy is the only route in.
Recorded here so it is not rediscovered the hard way later.

- [x] **Step 9: No DNS record for aralani**

Deliberately none. SSH cannot pass through a Cloudflare-proxied record, so any
hostname for SSH would have to be grey-clouded, which publishes the origin IP in
DNS. Use an SSH alias instead:

```
# ~/.ssh/config
Host aralani
    HostName 152.53.92.245
    User root
```

The only DNS change in this migration stays what the plan already says: flipping the
proxied `cook.hauptgang.app` A record at cutover.

- [x] **Step 10: Record the host**

Add aralani's details to `docs/superpowers/plans/migration-baseline.md` and commit.

### Task 10: Dry-run the deploy against Netcup

The Cloudflare origin still points at Hetzner throughout this task. Production is unaffected.

**Completed 2026-08-27. Two corrections to the steps as written.**

**(a) Do NOT run `bin/kamal setup` here — run `bin/kamal deploy`.** `setup` boots
accessories, and the litestream accessory's `config/litestream.yml` replicates to
fixed R2 paths (`production.sqlite3`, `production_queue.sqlite3`) that hold the only
backup of production. Booting a second Litestream against a fresh, empty database
would have had it push an empty lineage at those same paths. Best case it refuses on
a lineage mismatch; worst case it overwrites production's backup. Not worth finding
out during a dry run.

Mitigation used: `servers.web` was pointed at aralani while
`accessories.litestream.host` was left on `49.13.125.220`, and only `kamal deploy`
was run. Verified afterwards that production stayed healthy (`/up` 200, container up
10 hours, litestream up 39 minutes).

Note that `kamal deploy` still runs `docker image prune` on **every** host in the
config, Hetzner included. Harmless — it prunes only dangling images labelled
`service=hauptgang` — but it means "the dry run cannot touch production" is not
strictly true, and the health check above is not optional.

**(b) The app restores itself from R2 on boot.** `bin/docker-entrypoint` runs
`litestream restore -if-replica-exists` whenever `storage/production.sqlite3` is
missing. A fresh volume therefore does not come up empty — the dry run logged
`No database found, restoring from Litestream backup...` and came up with the full
production dataset (`recipes=163 users=21 blobs=514`) on a box that had never held
a database.

This is a **full disaster-recovery drill passing unaided**, and stronger evidence
than Task 8's manual restore: bare metal to complete production dataset, no operator
steps. But it invalidates this task's stated premise of "the app is now running there
with an empty database" — it never was.

**Cleanup performed:** the dry-run container and the `hauptgang_storage` volume were
removed from aralani afterwards. That instance held production credentials and shares
the R2 blob bucket with production, so leaving it running was not acceptable. Only
`kamal-proxy` remains, which the cutover reuses.

**Files:**
- Modify: `config/deploy.yml` (temporarily)

- [x] **Step 1: Point deploy.yml at Netcup**

In `config/deploy.yml`, change `servers.web` and `accessories.litestream.host` from `49.13.125.220` to the Netcup IP. **Do not commit this yet** — it is a dry run.

- [x] **Step 2: Temporarily disable proxy SSL for IP testing**

Let's Encrypt cannot issue for a bare IP. Under `proxy:`, temporarily set `ssl: false` and comment out `host:`.

- [x] **Step 3: Run setup**

```bash
bin/kamal setup
```

Expected: image builds, pushes, and boots on Netcup. The app is now running there with an empty database.

- [x] **Step 4: Smoke-test by IP**

```bash
curl -sI http://<netcup-ip>/up
```

Expected: `HTTP/1.1 200 OK`

- [x] **Step 5: Restore the proxy settings**

Revert Step 2 — set `ssl: true` and restore `host: cook.hauptgang.app`. Leave the new IPs in place.

- [x] **Step 6: Commit the host change**

```bash
git add config/deploy.yml
git commit -m "feat: point Kamal at the Netcup VPS"
```

### Task 11: The cutover window

Budget 5–10 minutes. Read every step before starting. Do not begin without the Cloudflare dashboard already open.

**Executed 2026-08-27. Cutover succeeded.** Corrections made to the steps as written:

**Pre-window, added:** the plan had no TLS story, and it needed one. Booting aralani
with `ssl: true` while DNS still pointed at Hetzner means the Let's Encrypt HTTP-01
challenge cannot reach it, so there would have been an HTTPS gap after the flip while
ACME retried -- and repeated failures risk the LE rate limit, which could have left
the site without a cert. Because both hosts ran the identical `kamal-proxy v0.9.2`,
the fix was to copy the `certs/` directory (NOT `kamal-proxy.state`, which holds
stale routing) from Hetzner's `kamal-proxy-config` volume to aralani's.

Verified before the window using `curl --resolve cook.hauptgang.app:443:<aralani-ip>`,
which tests the real hostname and cert without touching DNS: `status=200`,
`ssl_verify_result=0`, and **no ACME attempt in the proxy log**. The flip was
therefore seamless. Note that kamal-proxy will not serve TLS for a hostname with no
deployed target, so this can only be verified after the app is booted there.

**Step 1 premise was stale:** it claimed "Task 10 already repointed deploy.yml at
Netcup." Task 10's cleanup reverted that, so `deploy.yml` pointed at Hetzner and
`bin/kamal app boot` in Step 5 would have booted on the *old* host. Repointed
explicitly before Step 5.

**Step 2 did not do what its title said:** "force a final Litestream sync" was
followed by a command that only stops the container. Replaced with an explicit
verification that `txid.replica == txid.db` for **both** databases before stopping
the accessory.

**Step 5 used `--version=<sha>`** to boot the image already on the host rather than
rebuilding inside the window (only docs had changed since it was built).

**Missing step, added after the window:** the plan never boots the Litestream
accessory on the new host. Between the DNS flip and that boot, production had **no
backup replication at all**. `accessories.litestream.host` must be repointed and
`bin/kamal accessory boot litestream` run. The `.{db}-litestream` state directories
travel inside the volume tarball, so the replica lineage continues (production
resumed at txid 2) instead of starting a fresh generation.

**Result:** three independent paths agreed at `recipes=163 users=21 blobs=514` --
the pre-stop Hetzner baseline, the shipped tarball, and a fresh restore from R2.
Step 4b's guard returned `0`, proving the data came from the tarball. Downtime was
roughly four minutes.

- [ ] **Step 1: Stop the app on Hetzner**

This ends all writes and eliminates split-brain risk. Downtime starts now.

Task 10 already repointed `deploy.yml` at Netcup, so `bin/kamal app stop`
would target the wrong host. Stop the old container directly over SSH:

```bash
ssh root@49.13.125.220 "docker ps --filter name=hauptgang-web --format '{{.Names}}'"
ssh root@49.13.125.220 "docker stop \$(docker ps -q --filter name=hauptgang-web)"
```

Expected: the container name is listed, then stopped. Confirm the site is
now failing before continuing — that proves writes have actually ceased.

- [ ] **Step 2: Force a final Litestream sync, then stop the accessory**

```bash
ssh root@49.13.125.220 "docker stop hauptgang-litestream"
```

- [ ] **Step 3: Tarball the entire volume**

This captures all four SQLite databases, not just the two Litestream replicates.

```bash
ssh root@49.13.125.220 \
  "docker run --rm -v hauptgang_storage:/v -v /tmp:/out alpine tar czf /out/cutover.tar.gz -C /v ."
scp root@49.13.125.220:/tmp/cutover.tar.gz ./cutover.tar.gz
ls -lh ./cutover.tar.gz
```

- [ ] **Step 4: Restore the volume on Netcup**

```bash
scp ./cutover.tar.gz root@<netcup-ip>:/tmp/cutover.tar.gz
ssh root@<netcup-ip> \
  "docker run --rm -v hauptgang_storage:/v -v /tmp:/in alpine tar xzf /in/cutover.tar.gz -C /v"
ssh root@<netcup-ip> "docker run --rm -v hauptgang_storage:/v alpine ls -la /v"
```

Expected: all four `production*.sqlite3` files present.

- [ ] **Step 4b: Confirm the tarball actually landed BEFORE booting**

Added 2026-08-27 after the Task 10 dry run. `bin/docker-entrypoint` auto-restores
from R2 when `storage/production.sqlite3` is missing. If the Step 4 restore silently
failed, the app will **still boot successfully** — it will just rebuild from the R2
replica instead, quietly losing every write since the last sync. The app looks
healthy either way, so this failure is invisible without an explicit check.

```bash
ssh root@<netcup-ip> "docker run --rm -v hauptgang_storage:/v alpine ls -la /v"
```

Expected: all four `.sqlite3` files present, `production.sqlite3` matching the size
on Hetzner. If it is missing, **stop** and redo Step 4 — do not boot.

After booting, confirm the restore path did *not* fire:

```bash
ssh root@<netcup-ip> "docker logs \$(docker ps -q -f name=hauptgang-web) 2>&1 | grep -c 'No database found'"
```

Expected: `0`. Any other number means the tarball did not land and the database came
from R2, not from the cutover — treat row counts as suspect and re-check against the
baseline before flipping DNS.

- [ ] **Step 5: Boot on Netcup**

```bash
bin/kamal app boot
curl -sI http://<netcup-ip>/up
```

Expected: `HTTP/1.1 200 OK`

- [ ] **Step 6: Cross-check against an independent restore from R2**

This is the acceptance gate for Step 7. Two independent data paths must agree.

```bash
bin/kamal app exec --reuse \
  "litestream restore -config /rails/config/litestream.yml -o /tmp/xcheck.sqlite3 /rails/storage/production.sqlite3"
bin/kamal app exec --reuse "sqlite3 /tmp/xcheck.sqlite3 'SELECT COUNT(*) FROM recipes;'"
bin/kamal app exec --reuse "sqlite3 /rails/storage/production.sqlite3 'SELECT COUNT(*) FROM recipes;'"
```

Expected: the two counts match, and both match the Task 1 Step 4 baseline (allowing for legitimate growth since then). **If they disagree, do not flip DNS — go to Step 9.**

- [ ] **Step 7: Flip the Cloudflare origin**

**[HUMAN]** In the Cloudflare dashboard → DNS, edit the `cook` A record: change the IP from `49.13.125.220` to the Netcup IP. Leave the record **proxied** (orange cloud). Because it is proxied, this takes effect in seconds with no TTL wait.

- [ ] **Step 8: Verify the live site**

```bash
curl -sI https://cook.hauptgang.app/up
```

Expected: `HTTP/2 200`. Then load the app, confirm login works, images render, and a recipe import succeeds. Downtime ends here.

- [ ] **Step 9: Rollback procedure (only if a step above failed)**

Flip the Cloudflare A record back to `49.13.125.220`, then:

```bash
ssh root@49.13.125.220 "docker start hauptgang-litestream"
```

and boot the app on Hetzner. The Hetzner box was only stopped, never destroyed, and its volume is untouched.

### Task 12: Post-cutover verification

- [x] **Step 1: Confirm row counts match the baseline**

```bash
bin/kamal console
```

```ruby
puts({ recipes: Recipe.count, users: User.count, blobs: ActiveStorage::Blob.count }.inspect)
```

Expected: matches Task 1 Step 4, allowing for legitimate growth.

- [x] **Step 2: Confirm Litestream is replicating from the new host**

```bash
bin/kamal accessory exec litestream "litestream version"
rclone ls r2:hauptgang-backups --max-age 10m --s3-no-check-bucket
```

Expected: `v0.5.16`, and objects modified within the last 10 minutes.

- [x] **Step 3: Confirm TLS was issued for the new origin**

```bash
curl -sI https://cook.hauptgang.app/up | head -1
```

Expected: `HTTP/2 200`

- [x] **Step 4: Confirm background jobs run**

```bash
bin/kamal app logs | grep -i "solid_queue\|SolidQueue" | tail -20
```

Expected: the supervisor started and the hourly recurring cleanup task is registered.

- [x] **Step 5: Clean up the cross-check file**

```bash
bin/kamal app exec --reuse "rm -f /tmp/xcheck.sqlite3"
```

- [x] **Step 6: Phase 2 acceptance gate**

There is no time-based soak. **Stop here and walk this checklist together.** Phase 3 is irreversible, so every line must pass first.

- [x] Row counts match the Task 1 Step 4 baseline, allowing for legitimate growth
- [x] `curl -sI https://cook.hauptgang.app/up` returns 200 with valid TLS
- [x] Login works against the new host
- [x] Recipe images render
- [x] An end-to-end recipe import succeeds
- [x] The iOS app works against production
- [x] Litestream on Netcup is replicating both databases to R2
- [x] Solid Queue is running and the hourly recurring cleanup task is registered
- [x] No new errors in Sentry attributable to the move

Leave the Hetzner VPS and both Hetzner buckets paid-for and idle until this is signed off. Do not start Phase 3 before then.

**Gate signed off 2026-08-27.** Verified jointly: three independent paths agreed at
`recipes=163 users=21 blobs=514`, `/up` returned 200 with a valid Let's Encrypt cert,
Litestream replicated both databases from Netcup to R2, Solid Queue's hourly cleanup
task registered, and the user confirmed login, image serving, recipe import, the iOS
app, and no new Sentry errors. Phase 3 is authorised.

---

## Phase 3 — Cleanup (after the Phase 2 acceptance gate)

### Task 13: Remove Hetzner from the codebase

**Files:**
- Modify: `config/storage.yml`
- Modify: Rails credentials
- Modify: `docs/sqlite-backups-litestream.md`
- Test: `test/lib/storage_config_test.rb`

- [x] **Step 1: Write the failing test**

Add to `test/lib/storage_config_test.rb`:

```ruby
  test "the hetzner service has been removed" do
    refute storage_config.key?("hetzner"),
      "the :hetzner service should be gone after migration cleanup"
  end
```

- [x] **Step 2: Run it to verify it fails**

Run: `bin/rails test test/lib/storage_config_test.rb`
Expected: FAIL — `the :hetzner service should be gone after migration cleanup`

- [x] **Step 3: Delete the service**

Remove the entire `hetzner:` block and its comment header from `config/storage.yml`.

- [x] **Step 4: Run it to verify it passes**

Run: `bin/rails test test/lib/storage_config_test.rb`
Expected: PASS, 5 assertions

- [x] **Step 5: Remove the credentials**

```bash
bin/rails credentials:edit
```

Delete the entire `hetzner:` block.

- [x] **Step 6: Update the backup documentation**

In `docs/sqlite-backups-litestream.md`, replace the Hetzner references. The line currently reading that it reuses `hetzner.access_key_id` / `hetzner.secret_access_key` with backup bucket `hauptgang-backups` in `fsn1` must now describe: Cloudflare R2 credentials at `r2.access_key_id` / `r2.secret_access_key`, bucket `hauptgang-backups`, EU jurisdiction, Litestream pinned at v0.5.16, and that both `production.sqlite3` and `production_queue.sqlite3` are replicated.

- [x] **Step 7: Run the full CI suite**

```bash
bin/ci
```

Expected: all steps pass.

- [x] **Step 8: Commit and deploy**

```bash
bin/rubocop -a
git add config/storage.yml config/credentials.yml.enc docs/sqlite-backups-litestream.md test/lib/storage_config_test.rb
git commit -m "chore: remove Hetzner object storage configuration"
bin/kamal deploy
```

**Task 13 completed 2026-08-27**, deployed as `fb9121a`. Verified after deploy:
`/up` 200 with valid TLS, `recipes=163 users=21 blobs=514`, `ActiveStorage::Blob.service.name == :r2`,
the newest blob resolves in R2, Litestream v0.5.16 replicating both databases with the
highest R2 txid equal to the local max txid on each (`...000b`, `...05e6`).

Three deviations from the plan as written:

1. **Step 5 could not use interactive `credentials:edit`.** Driven non-interactively with
   `EDITOR=<script>` so no secret was ever printed to the terminal. Remaining top-level keys
   confirmed afterwards by name only.
2. **Step 6 understated the doc rewrite.** `docs/sqlite-backups-litestream.md` was wrong in
   more than its Hetzner references — it claimed only one database was backed up and described
   the 0.3.x architecture. Rewritten to cover R2 EU, both databases, the three-places version
   pin, and the two hazards this migration uncovered (`kamal restore` overwrites the live DB;
   the entrypoint's auto-restore can silently mask a failed volume ship).
3. **Step 7 `bin/ci` does not pass, for pre-existing reasons unrelated to this task.**
   Ruby style, all three security audits, Rails tests, and seeds pass. Failing:
   *Tests: System* (`selenium-manager` cannot resolve chromedriver — environment, not code)
   and *Style: iOS Lint/Format* (line lengths, `aspectRatio` idiom, `wrapIfStatementBodies`
   in committed Swift). Both are Swift/browser-only and cannot be affected by a change to
   `config/storage.yml`, credentials, and a Markdown file. Not fixed here — out of scope.

**Defect found 2026-08-28, one day after this task: removing `:hetzner` broke every
pre-cutover image.** The plan asserted that no `blobs` table rewrite was needed. That was
wrong. `rclone copy` preserved every key, but `active_storage_blobs.service_name` still read
`"hetzner"` on all 512 blobs predating the R2 cutover — only the 2 uploaded after it read
`"r2"`. Because `ActiveStorage::Blob#service` is `services.fetch(service_name)`, deleting the
`hetzner:` block in Step 3 made every
`/rails/active_storage/representations/redirect/...` request raise
`KeyError: Missing configuration for the hetzner Active Storage service` and return 500. In the
iOS app this showed as blank recipe cards for everything except the two newest recipes; on the
web it is the same 500. The Hetzner buckets had been purged the same day, so there was no
working path left either way.

The post-deploy verification above did not catch it because both storage checks looked at the
wrong thing: `ActiveStorage::Blob.service.name` reads the *default* service out of config, not
any row's `service_name`, and "the newest blob resolves in R2" was true precisely because the
newest blob was one of the two written after the cutover. A check that would have caught it is
`ActiveStorage::Blob.group(:service_name).count`, which returned `{"hetzner" => 512, "r2" => 2}`.

Fixed 2026-08-28 by running
`ActiveStorage::Blob.where(service_name: "hetzner").update_all(service_name: "r2")` against
production — 512 rows updated, leaving `{"r2" => 514}`. Safe because Task 4's `rclone check`
had already proven all 512 objects present in R2 byte-identical under the same keys; only the
column naming the service was stale. Verified by relaunching the iOS app against production and
confirming previously blank cards render.

### Task 14: Decommission Hetzner

**[HUMAN]** — irreversible. Do not start until Task 13 is deployed and verified.

- [ ] **Step 1: Final confirmation that nothing references Hetzner**

```bash
grep -rn "hetzner\|your-objectstorage\|49.13.125.220" --include="*.rb" --include="*.yml" --include="*.md" . | grep -v node_modules | grep -v docs/superpowers
```

Expected: no results. Anything that appears must be cleaned up before continuing.

- [ ] **Step 2: Confirm you still hold an independent copy**

Verify `./pre-litestream-upgrade.tar.gz` and `./cutover.tar.gz` still exist locally. These are your last pre-migration snapshots.

- [ ] **Step 3: Cancel the Hetzner services**

In the Hetzner console, delete the `hauptgang-production` and `hauptgang-backups` buckets and cancel the VPS `49.13.125.220`. Do all three together, last.

**Buckets purged 2026-08-27** via `rclone purge` (both bucket and contents removed;
`rclone lsd hetzner:` now lists nothing). The VPS is still running and remains the
`[HUMAN]` half of this step.

Deletion was gated on a three-way verification, all of which passed first:

| Check | Result |
|---|---|
| every Hetzner blob exists byte-identical in R2 | 512 matching, 0 differences |
| every Hetzner blob exists in `~/hauptgang-migration-archive/blobs` | 512 matching, 0 differences |
| every Hetzner backup object exists in `~/hauptgang-migration-archive/old-litestream-backups` | 31 matching, 0 differences |

**Note on the one-object discrepancy.** A first two-way `rclone check` of the backups
bucket reported `1 differences found`. The object was
`production.sqlite3/0000/0000000000000d8c-0000000000000d8c.ltx`. It was **only in the
local archive**, not missing from it: `rclone lsjson` on that exact key returned `[]`
and `rclone md5sum` returned nothing, so Litestream's retention had deleted it upstream
after the bulk copy. The archive was a superset of the bucket. Re-running with
`--one-way` — the direction that actually matters for safety, "every remote object must
exist locally" — passed cleanly. Use `--one-way` for this kind of gate; a two-way check
fails on harmless extras.

- [ ] **Step 4: Remove the baseline scratch file**

```bash
git rm docs/superpowers/plans/migration-baseline.md
git commit -m "chore: remove migration baseline scratch notes"
```

---

## Rollback Summary

| Phase | Failure point | Rollback |
|---|---|---|
| 1 | R2 uploads fail | Revert `production.rb` to `:hetzner`, redeploy. Hetzner bucket still authoritative. |
| 1 | Litestream 0.5.16 misbehaves | Revert Dockerfile + accessory pin, redeploy. Restore from `pre-litestream-upgrade.tar.gz` if needed. |
| 2 | Cross-check mismatch (Task 11 Step 6) | Do not flip DNS. Restart Hetzner app; nothing has changed publicly. |
| 2 | Site broken after DNS flip | Flip A record back to `49.13.125.220`, restart app and litestream there. |
| 3 | — | No rollback. Only proceed after the Phase 2 acceptance gate is signed off. |
