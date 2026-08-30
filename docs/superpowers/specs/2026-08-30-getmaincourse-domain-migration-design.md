# Domain migration: cook.hauptgang.app → app.getmaincourse.com

Date: 2026-08-30
Status: approved, not yet implemented

## Why

The app was renamed from Hauptgang to MainCourse. The landing page now runs on
`getmaincourse.com`. The Rails API still runs on `cook.hauptgang.app`, and the
App Store listing still points at old domains.

**Hard deadline: `hauptgang.app` expires in January 2027 and will not be
renewed.** Everything that depends on that domain must be gone before then.
This is the constraint that drives the schedule.

## Scope

In scope: the API domain, the links in the App Store listing, the redirects for
the old landing pages, and retiring `hauptgang.app` on schedule.

Out of scope, deliberately: renaming `Hauptgang` to `MainCourse` inside the code
(directories, Rails module, Xcode targets). That is a separate project with a
~100-file mechanical diff and no user-visible effect. It must not land on top of
this migration. See "Follow-up work".

## Current state (verified 2026-08-30)

| Thing | Value |
|---|---|
| Server | `152.53.92.245`, deployed with kamal 2.11.0 |
| Cloudflare zone `getmaincourse.com` | `1abf182e852f7684e57326a49363f4c9`, SSL mode `full`, no Worker routes |
| Cloudflare zone `hauptgang.app` | `044bbc56097b8c3c997a0182d6405703`, SSL mode `strict` |
| Cloudflare zone `szymonnastaly.com` | `0e69092a724a4b226738343773ddb8d7` |
| `getmaincourse.com` DNS | apex + `www`, AAAA `100::` (Pages/Workers custom domain), proxied |
| `cook.hauptgang.app` | proxied through Cloudflare (188.114.x) |
| App Store app | ID `6758990872`, locales `en-US`, `en-GB`, `de-DE` |
| Version 1.0.3 | `d9b0825e-96a2-4c87-aeec-7160e6bacd4d`, state `WAITING_FOR_REVIEW` |
| 1.0.3 metadata URLs | `maincourse.szymonnastaly.com` (marketing, support, privacy) |
| Live 1.0.2 metadata | privacy URL `hauptgang.app/privacy` |
| `getmaincourse.com/privacy` | 307 → `/privacy/` → 200. Same for `/support`. |
| `hauptgang.app/*` | 301 → `maincourse.szymonnastaly.com` |

Files referencing the old domain:

- `config/deploy.yml` — `proxy.host`
- `config/environments/production.rb:66` — mailer host
- `config/environments/production.rb:89` — `config.hosts`
- `app/controllers/api/v1/cookbook_invitations_controller.rb:96` — invite URL
- `hauptgang-ios/Hauptgang/Utilities/Constants.swift:24,31` — API host and base URL
- `hauptgang-ios/Hauptgang/Services/DeepLinkRouter.swift:64` — host equality check
- `hauptgang-ios/Hauptgang/Hauptgang.entitlements` — associated domains
- `hauptgang-ios/Hauptgang/Hauptgang.Release.entitlements` — associated domains
- `README.md:11` — landing page link

`public/.well-known/apple-app-site-association` needs no edit: Rails serves it,
so it is automatically correct on whatever hosts route to the app.

## Design

### 1. Both domains served, no redirect between them

One Kamal app serves `cook.hauptgang.app` and `app.getmaincourse.com`
identically. Old app installs keep working untouched; new installs use the new
host.

Not chosen: redirecting the old host to the new one. Old iOS builds' API calls
would have to survive a cross-domain redirect, which can drop the auth header on
POSTs. Not worth the risk when serving both is one config line.

### 2. One canonical host, many accepted hosts

Add `config.x.canonical_host`, set per environment. Two different lists:

- **Accepted** hosts (`config.hosts`) — both domains. What the server answers to.
- **Canonical** host — the new domain only. What the server puts *into* URLs it
  generates: mailer links and invite links.

So new invite links use the new domain from day one, while links already sent
out keep resolving. Changing the canonical domain later is one value, not a
grep.

Not chosen: generating invite URLs from `request.host`. Old clients would keep
minting old-domain links, which would extend the problem past the deadline.

### 3. iOS keeps both domains until the app is safe to break

Both entitlement files list `applinks:` and `webcredentials:` for **both**
domains during the transition. `DeepLinkRouter` accepts both via a shared
allowlist instead of an equality check.

Keeping the old `webcredentials:` entry is what preserves users' saved
passwords during the transition — see "What users will experience".

### 4. Watching the old host

Add the request host to production log tags so `bin/logs` shows which domain
each request arrived on. This tells us how many users are still on old builds as
the deadline approaches. No new infrastructure.

## What users will experience

**Logins: nothing happens.** `KeychainService` stores the auth token as a
generic-password item keyed on `Constants.Keychain.service` (`"com.hauptgang.ios"`)
plus an account key, in access group `$(AppIdentifierPrefix)app.hauptgang.shared`.
No hostname appears in that query, and auth is a `Bearer` header rather than a
cookie. Changing the API host does not log anyone out, and the share extension
keeps reading the same shared token.

This makes three identifiers load-bearing. Renaming any of them would log every
user out and orphan their token:

- `Constants.Keychain.service` = `"com.hauptgang.ios"`
- keychain access group `app.hauptgang.shared`
- app group `group.app.hauptgang.shared`

Also untouchable: the bundle IDs, and the RevenueCat entitlement ID
`"Hauptgang Pro"` (configured server-side at RevenueCat; changing the app's copy
breaks subscriptions).

**Saved passwords: fine now, one-time inconvenience in January 2027.** iOS files
saved passwords under a domain name — for every existing user, that is
`cook.hauptgang.app`. The password appears at login because the app's
entitlement claims that domain, and iOS verifies the claim by fetching the
association file from it.

While both entitlement entries are present, saved passwords keep appearing as
they do today. When `hauptgang.app` lapses, the association file becomes
unfetchable and the password stops being offered at login.

Nothing is lost. The password is still on the phone in Settings → Passwords
under `cook.hauptgang.app`, and the account is unaffected because login is email
and password checked server-side. Affected users type their password once, it
gets saved under the new domain, and it is normal from then on. For an app with
a handful of users this is acceptable; a short heads-up email near the date is
cheaper than engineering around it.

**Web UI users: one re-login.** The Rails session cookie is scoped to the host,
so anyone using the web UI at `cook.hauptgang.app` arrives logged out at
`app.getmaincourse.com`, and Safari will not offer the old-domain password
automatically. One re-login, for very few people.

## Constraints

- **Each associated domain must serve its own `apple-app-site-association` over
  HTTPS with a valid certificate and no redirects.** Cloudflare redirect rules
  must therefore never match `/.well-known/*` on either domain.
- Apple fetches association files through a CDN and devices refresh roughly
  weekly. Universal links on the new domain become active for a user when they
  install or update to 1.0.4, not the instant we deploy. Old links keep working
  in the meantime, so this is not a problem — just not instant.
- Cookbook invitations already carry an `expires_at`, so old-domain invite links
  are short-lived by nature and will not survive to January 2027.

## Plan

### Phase 1 — Server (September 2026)

Order matters. The new host must be live and verified before any app build
points at it.

1. Cloudflare: add `app.getmaincourse.com` → A `152.53.92.245`, created
   **DNS-only (grey cloud)** so Kamal's Let's Encrypt HTTP-01 challenge reaches
   the origin directly.
2. `config/deploy.yml`: replace `proxy.host` with a `proxy.hosts` list holding
   both domains. Deploy. Confirm certificates are issued for both.
3. Flip the new record to proxied, matching how `cook.hauptgang.app` runs.
   Zone SSL mode is already `full`, which is compatible.
4. Rails changes: `config.x.canonical_host`, both hosts in `config.hosts`,
   mailer `default_url_options` from the canonical host, `invite_url` from the
   canonical host, request host in `config.log_tags`.
5. Cloudflare redirect rules, 301, path-preserving, **excluding
   `/.well-known/*`**:
   - `hauptgang.app/*` and `www.hauptgang.app/*` → `https://getmaincourse.com/$1`.
     Must not match `cook.hauptgang.app`, which keeps serving the app.
   - `maincourse.szymonnastaly.com/*` → `https://getmaincourse.com/$1`.
6. `README.md:11` → `getmaincourse.com`.

Verify: `bin/ci` green; `/up` and `/.well-known/apple-app-site-association`
answer correctly on both hosts with valid certificates on each; old-domain
requests still work end to end.

### Phase 2 — App (September 2026)

7. Cancel the in-flight 1.0.3 submission:
   `asc submit cancel --version-id d9b0825e-96a2-4c87-aeec-7160e6bacd4d --confirm`
8. `Constants.swift`: production host → `https://app.getmaincourse.com`, with
   `baseURL` derived from `host` rather than repeating the literal.
9. Both entitlement files: add `applinks:` and `webcredentials:` for
   `app.getmaincourse.com`, keep the two existing entries.
10. `DeepLinkRouter`: accept both hosts via a shared allowlist. Extend
    `DeepLinkRouterTests` — old host routes, new host routes, `evil.com` still
    rejected.
11. Bump to 1.0.4 / build 64 in `hauptgang-ios/project.yml`, `xcodegen generate`,
    ship via the `deploying-maincourse` skill.
12. App Store Connect metadata, all three locales:
    - `marketingUrl` → `https://getmaincourse.com/`
    - `supportUrl` → `https://getmaincourse.com/support/`
    - `privacyPolicyUrl` (app-info) → `https://getmaincourse.com/privacy/`

    Trailing slashes are intentional — the unslashed forms 307 to them, and
    App Store Connect's URL check should not have to follow a hop.

Verify: `bin/ios-test` green. On a device running 1.0.4, log in, then open both
an old `cook.hauptgang.app` invite link and a new one — both must open the app.
An already-installed 1.0.3 build must still work against the old host.

### Phase 3 — Decommission (December 2026, before the January 2027 expiry)

13. Check the logs for remaining `cook.hauptgang.app` traffic. Contact any
    stragglers directly — the user base is small enough that this is a message,
    not a migration system.
14. Send a short heads-up that saved passwords may need re-entering once.
15. Ship 1.0.5 removing `applinks:cook.hauptgang.app` and
    `webcredentials:cook.hauptgang.app` from both entitlement files.

    **This must ship before the domain lapses.** Association is verified only by
    the domain serving a file that names our app ID — a string anyone can write.
    If the app still claims a domain someone else has registered, that new owner
    could serve an association file and capture universal links and shared web
    credentials for our app.

    This protects users who update to 1.0.5. Installs that never update keep the
    stale claim, which is a further reason to make sure Phase 3 step 13 actually
    reaches everyone.
16. Remove `cook.hauptgang.app` from `config/deploy.yml` and `config.hosts`.
    Deploy.
17. Delete the `hauptgang.app` Cloudflare zone and its redirect rules. Confirm
    nothing in the App Store listing still points at `hauptgang.app` — after
    Phase 2 the live listing points at `getmaincourse.com`.

## Follow-up work

- **Internal rename `Hauptgang` → `MainCourse`.** Separate spec. Roughly 52
  Ruby/ERB files, 49 Swift files, the `hauptgang-ios/` directory, Xcode targets
  and schemes via XcodeGen, `bin/ios-*` scripts, and the Rails module in
  `config/application.rb`. Must respect the load-bearing identifiers listed
  under "What users will experience".
- The `hauptgang://` custom URL scheme (development only).
- Kamal service name, Docker image name, and the `hauptgang_storage` volume.
  Renaming the volume means migrating data for no user benefit; leave it.
