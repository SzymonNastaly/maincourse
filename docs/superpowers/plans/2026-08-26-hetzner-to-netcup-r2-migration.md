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
- **No Active Storage database rewrite.** Blob keys are opaque and preserved by `rclone copy`. Any task proposing a `blobs` table migration is wrong.
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
**If `snapshot.retention`/`snapshot.interval` are rejected, consult `litestream.io/reference/config/` for the exact global-snapshot key names and correct them here** — the two-key structure above is the documented 0.5.x shape but the field names are the one item not verified against the running binary.

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

- [ ] **Step 1: Restore the primary database from R2 to a scratch path**

```bash
bin/kamal app exec --reuse \
  "litestream restore -config /rails/config/litestream.yml -o /tmp/restored.sqlite3 /rails/storage/production.sqlite3"
```

Expected: completes without error.

- [ ] **Step 2: Verify the restored file is a valid, complete database**

```bash
bin/kamal app exec --reuse "sqlite3 /tmp/restored.sqlite3 'PRAGMA integrity_check;'"
```

Expected: `ok`

- [ ] **Step 3: Compare row counts against live**

```bash
bin/kamal app exec --reuse "sqlite3 /tmp/restored.sqlite3 'SELECT COUNT(*) FROM recipes;'"
bin/kamal app exec --reuse "sqlite3 /rails/storage/production.sqlite3 'SELECT COUNT(*) FROM recipes;'"
```

Expected: equal, or the restored count trailing by at most the last sync interval.

- [ ] **Step 4: Clean up**

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

### Task 9: Provision the Netcup VPS

**[HUMAN]** — requires a Netcup account and payment.

- [ ] **Step 1: Order the server**

Order a Netcup x86 VPS (VPS 2000 G11 class: 8 dedicated cores / 16 GB / NVMe). Do **not** order ARM — the plan and `builder.arch` assume x86. Choose Debian 12 or 13 as the OS image.

- [ ] **Step 2: Harden SSH**

```bash
ssh-copy-id root@<netcup-ip>
```

Then on the server, in `/etc/ssh/sshd_config`, set `PasswordAuthentication no` and `PermitRootLogin prohibit-password`, then `systemctl restart sshd`.

- [ ] **Step 3: Install Docker**

```bash
ssh root@<netcup-ip> "curl -fsSL https://get.docker.com | sh"
ssh root@<netcup-ip> "docker --version"
```

- [ ] **Step 4: Firewall to 22/80/443**

```bash
ssh root@<netcup-ip> "apt-get install -y ufw && ufw allow 22 && ufw allow 80 && ufw allow 443 && ufw --force enable"
```

- [ ] **Step 5: Record the IP**

Add the new IP to `docs/superpowers/plans/migration-baseline.md` under a `Netcup IP` heading, and commit.

### Task 10: Dry-run the deploy against Netcup

The Cloudflare origin still points at Hetzner throughout this task. Production is unaffected.

**Files:**
- Modify: `config/deploy.yml` (temporarily)

- [ ] **Step 1: Point deploy.yml at Netcup**

In `config/deploy.yml`, change `servers.web` and `accessories.litestream.host` from `49.13.125.220` to the Netcup IP. **Do not commit this yet** — it is a dry run.

- [ ] **Step 2: Temporarily disable proxy SSL for IP testing**

Let's Encrypt cannot issue for a bare IP. Under `proxy:`, temporarily set `ssl: false` and comment out `host:`.

- [ ] **Step 3: Run setup**

```bash
bin/kamal setup
```

Expected: image builds, pushes, and boots on Netcup. The app is now running there with an empty database.

- [ ] **Step 4: Smoke-test by IP**

```bash
curl -sI http://<netcup-ip>/up
```

Expected: `HTTP/1.1 200 OK`

- [ ] **Step 5: Restore the proxy settings**

Revert Step 2 — set `ssl: true` and restore `host: cook.hauptgang.app`. Leave the new IPs in place.

- [ ] **Step 6: Commit the host change**

```bash
git add config/deploy.yml
git commit -m "feat: point Kamal at the Netcup VPS"
```

### Task 11: The cutover window

Budget 5–10 minutes. Read every step before starting. Do not begin without the Cloudflare dashboard already open.

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

- [ ] **Step 1: Confirm row counts match the baseline**

```bash
bin/kamal console
```

```ruby
puts({ recipes: Recipe.count, users: User.count, blobs: ActiveStorage::Blob.count }.inspect)
```

Expected: matches Task 1 Step 4, allowing for legitimate growth.

- [ ] **Step 2: Confirm Litestream is replicating from the new host**

```bash
bin/kamal accessory exec litestream "litestream version"
rclone ls r2:hauptgang-backups --max-age 10m --s3-no-check-bucket
```

Expected: `v0.5.16`, and objects modified within the last 10 minutes.

- [ ] **Step 3: Confirm TLS was issued for the new origin**

```bash
curl -sI https://cook.hauptgang.app/up | head -1
```

Expected: `HTTP/2 200`

- [ ] **Step 4: Confirm background jobs run**

```bash
bin/kamal app logs | grep -i "solid_queue\|SolidQueue" | tail -20
```

Expected: the supervisor started and the hourly recurring cleanup task is registered.

- [ ] **Step 5: Clean up the cross-check file**

```bash
bin/kamal app exec --reuse "rm -f /tmp/xcheck.sqlite3"
```

- [ ] **Step 6: Phase 2 acceptance gate**

There is no time-based soak. **Stop here and walk this checklist together.** Phase 3 is irreversible, so every line must pass first.

- [ ] Row counts match the Task 1 Step 4 baseline, allowing for legitimate growth
- [ ] `curl -sI https://cook.hauptgang.app/up` returns 200 with valid TLS
- [ ] Login works against the new host
- [ ] Recipe images render
- [ ] An end-to-end recipe import succeeds
- [ ] The iOS app works against production
- [ ] Litestream on Netcup is replicating both databases to R2
- [ ] Solid Queue is running and the hourly recurring cleanup task is registered
- [ ] No new errors in Sentry attributable to the move

Leave the Hetzner VPS and both Hetzner buckets paid-for and idle until this is signed off. Do not start Phase 3 before then.

---

## Phase 3 — Cleanup (after the Phase 2 acceptance gate)

### Task 13: Remove Hetzner from the codebase

**Files:**
- Modify: `config/storage.yml`
- Modify: Rails credentials
- Modify: `docs/sqlite-backups-litestream.md`
- Test: `test/lib/storage_config_test.rb`

- [ ] **Step 1: Write the failing test**

Add to `test/lib/storage_config_test.rb`:

```ruby
  test "the hetzner service has been removed" do
    refute storage_config.key?("hetzner"),
      "the :hetzner service should be gone after migration cleanup"
  end
```

- [ ] **Step 2: Run it to verify it fails**

Run: `bin/rails test test/lib/storage_config_test.rb`
Expected: FAIL — `the :hetzner service should be gone after migration cleanup`

- [ ] **Step 3: Delete the service**

Remove the entire `hetzner:` block and its comment header from `config/storage.yml`.

- [ ] **Step 4: Run it to verify it passes**

Run: `bin/rails test test/lib/storage_config_test.rb`
Expected: PASS, 5 assertions

- [ ] **Step 5: Remove the credentials**

```bash
bin/rails credentials:edit
```

Delete the entire `hetzner:` block.

- [ ] **Step 6: Update the backup documentation**

In `docs/sqlite-backups-litestream.md`, replace the Hetzner references. The line currently reading that it reuses `hetzner.access_key_id` / `hetzner.secret_access_key` with backup bucket `hauptgang-backups` in `fsn1` must now describe: Cloudflare R2 credentials at `r2.access_key_id` / `r2.secret_access_key`, bucket `hauptgang-backups`, EU jurisdiction, Litestream pinned at v0.5.16, and that both `production.sqlite3` and `production_queue.sqlite3` are replicated.

- [ ] **Step 7: Run the full CI suite**

```bash
bin/ci
```

Expected: all steps pass.

- [ ] **Step 8: Commit and deploy**

```bash
bin/rubocop -a
git add config/storage.yml config/credentials.yml.enc docs/sqlite-backups-litestream.md test/lib/storage_config_test.rb
git commit -m "chore: remove Hetzner object storage configuration"
bin/kamal deploy
```

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
