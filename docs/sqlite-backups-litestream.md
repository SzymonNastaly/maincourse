# SQLite Backups with Litestream

Litestream continuously replicates the production SQLite databases to **Cloudflare R2** (S3-compatible, EU jurisdiction). It runs as a Kamal accessory alongside the app container, sharing the same Docker volume.

## Architecture

- **Replication**: A `litestream/litestream:0.5.16` container runs as a Kamal accessory, watching `production.sqlite3` and `production_queue.sqlite3` and streaming changes to R2.
- **Restore**: The same Litestream version is installed in the app image (`Dockerfile:21`). On boot, `bin/docker-entrypoint` auto-restores from R2 **if the database file is missing** (`litestream restore -if-replica-exists`).
- **Scope**: `production.sqlite3` and `production_queue.sqlite3` are replicated. Solid Queue holds in-flight and scheduled jobs, so it is not disposable. Cache and cable are excluded as genuinely disposable — the Solid suite recreates them.
- **UID alignment**: The accessory runs as `user: 1000:1000` (via `options:` in `config/deploy.yml`) to match the Rails app user. Without this, Litestream cannot read WAL files or create its metadata directory on the shared `hauptgang_storage` volume.

## Version pinning — keep these three in lockstep

Litestream 0.5.x uses the **LTX** format, which 0.3.x cannot read. A version skew between the replicating sidecar and the restoring binary silently produces an unrestorable backup, so all three must name the same version:

| Location | Current |
|---|---|
| `Dockerfile:21` (app image binary) | `0.5.16` |
| `config/deploy.yml` (accessory image) | `litestream/litestream:0.5.16` |
| `config/litestream.yml` | 0.5.x schema (`replica:`, global `snapshot:`) |

Pin **≥ 0.5.4**: 0.5.0 shipped restore bugs, and AWS SDK Go v2 broke uploads to all S3-compatible providers via aws-chunked encoding until 0.5.4.

Upgrading across the 0.3.x → 0.5.x boundary **resets the replica lineage** — the new generation starts with no history. Take a volume tarball before any such upgrade; it is your only pre-upgrade recovery point until retention refills.

## Key files

| File | Role |
|---|---|
| `config/litestream.yml` | Replication config (DB paths, R2 bucket, retention) |
| `config/deploy.yml` | Kamal accessory definition + `restore` alias |
| `.kamal/secrets` | Extracts R2 credentials from Rails credentials |
| `bin/docker-entrypoint` | Auto-restore logic on missing DB |
| `Dockerfile` | Installs the `litestream` binary in the app image |

## Credentials

Uses the Cloudflare R2 credentials from Rails credentials: `r2.access_key_id`, `r2.secret_access_key`, `r2.account_id`, `r2.backup_bucket`. Backup bucket: `hauptgang-backups`, **EU jurisdiction**.

The endpoint is assembled in `.kamal/secrets` as `https://<r2.account_id>.eu.r2.cloudflarestorage.com`. Jurisdiction is fixed at bucket creation and is also baked into the API token — a token minted for one jurisdiction will not reach a bucket in another.

## Common operations

```bash
# Check replication status
kamal accessory logs litestream

# Reboot the Litestream accessory
kamal accessory reboot litestream

# Test restore to a temporary file (non-destructive)
kamal app exec "litestream restore -config /rails/config/litestream.yml -o /tmp/test.sqlite3 storage/production.sqlite3"
kamal app exec "sqlite3 /tmp/test.sqlite3 'PRAGMA integrity_check; SELECT count(*) FROM recipes;'"
```

### ⚠️ `kamal restore` overwrites the live database

```bash
kamal restore   # DESTRUCTIVE
```

The `restore` alias in `config/deploy.yml` restores **over** `storage/production.sqlite3` in the running container. Stop the app first, and prefer the non-destructive `-o /tmp/...` form above for any drill or verification.

A restore drill run **from your laptop** is a strictly stronger test than one run via `kamal app exec`: it shares no binary, no filesystem, and no host with production, so it proves the replica is restorable by something other than the machine that wrote it.

## Disaster recovery (fresh server)

On a new server, `kamal deploy` + `kamal accessory boot litestream` is all that is needed. The entrypoint detects the missing database and restores from R2 automatically before running `db:prepare`.

### ⚠️ The auto-restore can mask data loss during a host migration

When migrating a host by shipping a volume tarball, the entrypoint's auto-restore is a hazard: if the tarball fails to arrive, the app still boots healthy-looking — restored from R2, but **stale**, missing everything written since the last replication.

Before booting on a new host, verify the volume actually contains the shipped database, then confirm the boot logs did *not* take the restore path:

```bash
kamal app logs | grep -c 'No database found'   # must be 0 when a tarball was shipped
```

## Retention

Configured in `config/litestream.yml`: 72h retention, 24h snapshot interval. Point-in-time recovery is possible within that window via `litestream restore -timestamp`.

Note the consequence: **R2 holds no recovery point older than 72 hours.** It is a replica, not an archive. If you need a pre-migration or pre-upgrade snapshot to survive longer, take a volume tarball and store it off the server.
