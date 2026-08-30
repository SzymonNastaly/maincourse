# getmaincourse.com Domain Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Rails API from `cook.hauptgang.app` to `app.getmaincourse.com`, serving both during a transition, and update the App Store listing links — all before `hauptgang.app` expires in January 2027.

**Architecture:** One Kamal app serves both domains (kamal 2.11 `proxy.hosts`). Rails distinguishes *accepted* hosts (`config.hosts`, both) from the *canonical* host (`config.x.canonical_host`, new only) used to generate outbound URLs. iOS lists both domains in its associated-domains entitlement so existing saved passwords and existing invite links keep working, and accepts both hosts for universal links.

**Tech Stack:** Rails 8.1 / Ruby 3.4.7, Kamal 2.11, Cloudflare (DNS + redirect rulesets, via the `cloudflare-api` MCP `execute` tool), SwiftUI / XcodeGen, `asc` CLI for App Store Connect.

**Spec:** `docs/superpowers/specs/2026-08-30-getmaincourse-domain-migration-design.md`

## Global Constraints

- New API domain: `app.getmaincourse.com`. Old API domain: `cook.hauptgang.app` (kept until December 2026).
- Origin server: `152.53.92.245`.
- Cloudflare zone IDs: `getmaincourse.com` = `1abf182e852f7684e57326a49363f4c9`; `hauptgang.app` = `044bbc56097b8c3c997a0182d6405703`; `szymonnastaly.com` = `0e69092a724a4b226738343773ddb8d7`.
- App Store Connect app ID `6758990872`. Locales: `en-US`, `en-GB`, `de-DE`.
- **Never rename these** — doing so logs out every user or breaks subscriptions:
  `Constants.Keychain.service` = `"com.hauptgang.ios"`; keychain access group `app.hauptgang.shared`; app group `group.app.hauptgang.shared`; bundle IDs `app.hauptgang.ios*`; RevenueCat entitlement ID `"Hauptgang Pro"`.
- **No Cloudflare redirect rule may match `/.well-known/*`** on any zone. Apple requires each associated domain to serve its own `apple-app-site-association` over HTTPS with no redirects.
- Order is not optional: DNS → Rails config → deploy → verify both hosts → only then ship an app build pointing at the new host.
- Do not rename `Hauptgang` to `MainCourse` anywhere in code. That is a separate project.
- Style: `rubocop-rails-omakase`. Run `bin/rubocop -a` before committing Ruby.

---

### Task 1: Create the new DNS record (DNS-only)

Created grey-cloud first so Kamal's Let's Encrypt HTTP-01 challenge reaches the origin directly. It gets proxied in Task 3 after certificates are issued.

**Files:** None. Cloudflare API only.

**Interfaces:**
- Produces: `app.getmaincourse.com` resolving directly to `152.53.92.245`.

- [ ] **Step 1: Create the A record**

Use the `cloudflare-api` MCP `execute` tool:

```javascript
async () => {
  return cloudflare.request({
    method: "POST",
    path: "/zones/1abf182e852f7684e57326a49363f4c9/dns_records",
    body: {
      type: "A",
      name: "app",
      content: "152.53.92.245",
      proxied: false,
      ttl: 60,
      comment: "Rails app origin; proxied after LE cert issuance"
    }
  });
}
```

- [ ] **Step 2: Verify it resolves to the origin**

```bash
dig +short app.getmaincourse.com
```

Expected: `152.53.92.245` (not a 188.114.x or 104.21.x Cloudflare address). If it still shows nothing, wait 60s and retry — TTL is 60.

---

### Task 2: Rails canonical host

Must be deployed *before* traffic arrives on the new domain — `config.hosts` rejects unknown hosts, so a deploy without this returns errors on `app.getmaincourse.com`.

Extracts invite-URL construction out of the controller into a tiny class so it can actually be unit tested; the controller currently hides it in a private method.

**Files:**
- Create: `app/models/invite_link.rb`
- Create: `test/models/invite_link_test.rb`
- Modify: `app/controllers/api/v1/cookbook_invitations_controller.rb` (remove private `invite_url`, call `InviteLink.url_for`)
- Modify: `config/environments/production.rb:66,89` (canonical host, hosts list, log tags)
- Modify: `config/environments/test.rb` (canonical host, so the test can assert it)

**Interfaces:**
- Produces: `InviteLink.url_for(token) -> String`; `Rails.application.config.x.canonical_host -> String`.

- [ ] **Step 1: Write the failing test**

Create `test/models/invite_link_test.rb`:

```ruby
require "test_helper"

class InviteLinkTest < ActiveSupport::TestCase
  test "uses the canonical host over https in production" do
    Rails.env.stub(:production?, true) do
      assert_equal "https://app.getmaincourse.com/invite/tok123",
                   InviteLink.url_for("tok123")
    end
  end

  test "uses the custom scheme outside production" do
    assert_equal "hauptgang://invite/tok123", InviteLink.url_for("tok123")
  end
end
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `bin/rails test test/models/invite_link_test.rb`
Expected: FAIL with `NameError: uninitialized constant InviteLink`.

- [ ] **Step 3: Set the canonical host in test config**

In `config/environments/test.rb`, inside the `Rails.application.configure do` block, add:

```ruby
  # Canonical host used to build outbound URLs (see InviteLink, mailer).
  config.x.canonical_host = "app.getmaincourse.com"
```

- [ ] **Step 4: Create the class**

Create `app/models/invite_link.rb`:

```ruby
# Builds the URL a cookbook invitation is shared as.
#
# Production uses a universal link on the canonical host so the link opens the
# iOS app; other environments use the custom scheme, since a simulator has no
# associated-domain association to resolve.
class InviteLink
  def self.url_for(token)
    if Rails.env.production?
      "https://#{Rails.application.config.x.canonical_host}/invite/#{token}"
    else
      "hauptgang://invite/#{token}"
    end
  end
end
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `bin/rails test test/models/invite_link_test.rb`
Expected: 2 runs, 2 assertions, 0 failures.

- [ ] **Step 6: Use it from the controller**

In `app/controllers/api/v1/cookbook_invitations_controller.rb`, replace the call site (around line 43) — change `invite_url: invite_url(invitation.token),` to:

```ruby
          invite_url: InviteLink.url_for(invitation.token),
```

Then delete the now-unused private method at the end of the class:

```ruby
      def invite_url(token)
        if Rails.env.production?
          "https://cook.hauptgang.app/invite/#{token}"
        else
          "hauptgang://invite/#{token}"
        end
      end
```

- [ ] **Step 7: Update production config**

In `config/environments/production.rb`, replace line 66:

```ruby
  config.action_mailer.default_url_options = { host: "cook.hauptgang.app" }
```

with:

```ruby
  # Canonical host used to build outbound URLs. Accepted hosts (below) is a
  # wider list during the domain transition; this is the one we advertise.
  config.x.canonical_host = "app.getmaincourse.com"

  # Set host to be used by links generated in mailer templates.
  config.action_mailer.default_url_options = { host: config.x.canonical_host }
```

Then replace the `config.hosts` block (around line 87-90):

```ruby
  config.hosts = [
    "cook.hauptgang.app"
  ]
```

with:

```ruby
  # Both are served during the transition. cook.hauptgang.app is removed in
  # December 2026, before the domain expires — see
  # docs/superpowers/specs/2026-08-30-getmaincourse-domain-migration-design.md
  config.hosts = [
    "app.getmaincourse.com",
    "cook.hauptgang.app"
  ]

  # Tag logs with the request host so `bin/logs` shows how much traffic still
  # arrives on the old domain.
  config.log_tags = [ :request_id, ->(request) { request.host } ]
```

- [ ] **Step 8: Run the full Rails test suite**

Run: `bin/rubocop -a && bin/rails test`
Expected: all green. `test/controllers/api/v1/cookbook_invitations_controller_test.rb` asserts only that `invite_url` is present and contains the token, so it passes unchanged.

- [ ] **Step 9: Commit**

```bash
git add app/models/invite_link.rb test/models/invite_link_test.rb \
        app/controllers/api/v1/cookbook_invitations_controller.rb \
        config/environments/production.rb config/environments/test.rb
git commit -m "feat: build outbound URLs from a canonical host

Accepts both cook.hauptgang.app and app.getmaincourse.com while
generating links only for the new domain. Tags production logs with the
request host so old-domain traffic can be watched."
```

---

### Task 3: Serve both domains from Kamal, then proxy the new one

**Files:**
- Modify: `config/deploy.yml` (the `proxy:` block)

**Interfaces:**
- Consumes: `config.hosts` from Task 2 — deploying without it makes the new host return errors.
- Produces: valid TLS on both domains.

- [ ] **Step 1: Replace the single proxy host with a list**

In `config/deploy.yml`, replace:

```yaml
proxy:
  ssl: true
  host: cook.hauptgang.app
```

with:

```yaml
proxy:
  ssl: true
  hosts:
    - app.getmaincourse.com
    - cook.hauptgang.app
```

- [ ] **Step 2: Commit before deploying**

```bash
git add config/deploy.yml
git commit -m "chore: serve both cook.hauptgang.app and app.getmaincourse.com"
```

- [ ] **Step 3: Deploy**

```bash
bin/kamal deploy
```

Expected: completes without error. Kamal requests a Let's Encrypt certificate for the new host on first deploy; this can add a minute.

- [ ] **Step 4: Verify both hosts serve correctly**

```bash
for h in cook.hauptgang.app app.getmaincourse.com; do
  echo "== $h"
  curl -s -o /dev/null -w "  up: %{http_code}\n" "https://$h/up"
  curl -s -o /dev/null -w "  aasa: %{http_code}\n" "https://$h/.well-known/apple-app-site-association"
done
```

Expected: `200` for all four. A `403` on `/up` means `config.hosts` is missing that host — Task 2 did not deploy. A TLS error on the new host means the certificate was not issued; re-run `bin/kamal deploy` and check `bin/kamal proxy logs`.

- [ ] **Step 5: Confirm the association file content is identical on both**

```bash
curl -s https://app.getmaincourse.com/.well-known/apple-app-site-association | tee /dev/stderr | \
  diff - <(curl -s https://cook.hauptgang.app/.well-known/apple-app-site-association) && echo IDENTICAL
```

Expected: `IDENTICAL`, and the JSON names `R69J54A3P5.app.hauptgang.ios`.

- [ ] **Step 6: Turn on the Cloudflare proxy for the new record**

Only now — the certificate exists, so the origin no longer needs direct reachability.

```javascript
async () => {
  const zone = "1abf182e852f7684e57326a49363f4c9";
  const list = await cloudflare.request({
    method: "GET", path: `/zones/${zone}/dns_records`, query: { name: "app.getmaincourse.com" }
  });
  const id = list.result[0].id;
  return cloudflare.request({
    method: "PATCH", path: `/zones/${zone}/dns_records/${id}`, body: { proxied: true }
  });
}
```

- [ ] **Step 7: Re-verify through the proxy**

```bash
dig +short app.getmaincourse.com
curl -s -o /dev/null -w "%{http_code}\n" https://app.getmaincourse.com/up
```

Expected: a Cloudflare address (104.21.x or 172.67.x), and `200`. If this returns a 5xx, the zone's SSL mode is wrong — it must be `full` (it already is; do not set it to `flexible`).

---

### Task 4: Redirect the old landing domains

`cook.hauptgang.app` must keep serving the app, so the rules match the apex and `www` by name rather than by wildcard. `/.well-known/*` is excluded so association files are never redirected.

**Files:** None. Cloudflare API only.

- [ ] **Step 1: Create the redirect ruleset on the hauptgang.app zone**

```javascript
async () => {
  return cloudflare.request({
    method: "PUT",
    path: "/zones/044bbc56097b8c3c997a0182d6405703/rulesets/phases/http_request_dynamic_redirect/entrypoint",
    body: {
      name: "Redirect old landing to getmaincourse.com",
      rules: [{
        description: "hauptgang.app + www -> getmaincourse.com, excluding .well-known",
        expression: '(http.host in {"hauptgang.app" "www.hauptgang.app"} and not starts_with(http.request.uri.path, "/.well-known/"))',
        action: "redirect",
        action_parameters: {
          from_value: {
            status_code: 301,
            target_url: { expression: 'concat("https://getmaincourse.com", http.request.uri.path)' },
            preserve_query_string: true
          }
        }
      }]
    }
  });
}
```

- [ ] **Step 2: Create the same rule on the szymonnastaly.com zone**

```javascript
async () => {
  return cloudflare.request({
    method: "PUT",
    path: "/zones/0e69092a724a4b226738343773ddb8d7/rulesets/phases/http_request_dynamic_redirect/entrypoint",
    body: {
      name: "Redirect maincourse.szymonnastaly.com to getmaincourse.com",
      rules: [{
        description: "maincourse.szymonnastaly.com -> getmaincourse.com, excluding .well-known",
        expression: '(http.host eq "maincourse.szymonnastaly.com" and not starts_with(http.request.uri.path, "/.well-known/"))',
        action: "redirect",
        action_parameters: {
          from_value: {
            status_code: 301,
            target_url: { expression: 'concat("https://getmaincourse.com", http.request.uri.path)' },
            preserve_query_string: true
          }
        }
      }]
    }
  });
}
```

**Note:** `PUT` on a phase entrypoint replaces every rule in that phase. If either zone already has redirect rules, `GET` the entrypoint first and include the existing rules in the `rules` array.

- [ ] **Step 3: Verify the redirects, and that the app host is untouched**

```bash
curl -sI https://hauptgang.app/privacy            | grep -i "^HTTP\|^location"
curl -sI https://maincourse.szymonnastaly.com/    | grep -i "^HTTP\|^location"
curl -s -o /dev/null -w "app host still serving: %{http_code}\n" https://cook.hauptgang.app/up
curl -s -o /dev/null -w "old aasa not redirected: %{http_code}\n" https://cook.hauptgang.app/.well-known/apple-app-site-association
```

Expected: the first two show `301` to `getmaincourse.com` with the path preserved; the last two show `200` with no redirect.

---

### Task 5: Update the README link

**Files:**
- Modify: `README.md:11`

- [ ] **Step 1: Change the landing page link**

Replace line 11:

```markdown
Landing page: [hauptgang.app](https://hauptgang.app)
```

with:

```markdown
Landing page: [getmaincourse.com](https://getmaincourse.com)
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: point the README at getmaincourse.com"
```

---

### Task 6: Point the iOS app at the new host and accept both for deep links

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Utilities/Constants.swift:22-34` (production host/baseURL, new `DeepLinks` enum)
- Modify: `hauptgang-ios/Hauptgang/Services/DeepLinkRouter.swift:46,62,64`
- Test: `hauptgang-ios/HauptgangTests/Services/DeepLinkRouterTests.swift`

**Interfaces:**
- Produces: `Constants.DeepLinks.universalLinkHosts -> Set<String>`.

- [ ] **Step 1: Write the failing tests**

Add to `DeepLinkRouterTests.swift`, after `testExtractToken_universalLink_longToken`:

```swift
    func testExtractToken_universalLink_newHost() throws {
        let url = try XCTUnwrap(URL(string: "https://app.getmaincourse.com/invite/abc123"))
        XCTAssertEqual(DeepLinkRouter.extractInvitationToken(from: url), "abc123")
    }

    func testExtractToken_universalLink_legacyHostStillAccepted() throws {
        let url = try XCTUnwrap(URL(string: "https://cook.hauptgang.app/invite/abc123"))
        XCTAssertEqual(DeepLinkRouter.extractInvitationToken(from: url), "abc123")
    }

    func testExtractToken_universalLink_newHost_wrongPath() throws {
        let url = try XCTUnwrap(URL(string: "https://app.getmaincourse.com/recipes/abc123"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }
```

- [ ] **Step 2: Run them to make sure the new-host one fails**

Run: `bin/ios-test`
Expected: `testExtractToken_universalLink_newHost` FAILS (returns nil); the legacy-host test passes.

- [ ] **Step 3: Update Constants.swift**

Replace the production branch (the `#else` arm, lines 22-34) so the domain appears once:

```swift
        #else
        /// Production API domain. Both this and the legacy cook.hauptgang.app are
        /// served until December 2026; this is the one new builds talk to.
        private static let productionHost = "app.getmaincourse.com"

        static let host: URL = {
            guard let url = URL(string: "https://\(productionHost)") else {
                preconditionFailure("Invalid API host URL")
            }
            return url
        }()

        static let baseURL: URL = {
            guard let url = URL(string: "https://\(productionHost)/api/v1") else {
                preconditionFailure("Invalid API base URL")
            }
            return url
        }()
        #endif
```

Then add a new enum inside `Constants`, as a sibling of `enum API` (place it directly after the closing brace of `enum API`):

```swift
    enum DeepLinks {
        /// Hosts whose /invite/{token} links open this app. The legacy host stays
        /// until the cook.hauptgang.app entitlement is dropped in December 2026.
        static let universalLinkHosts: Set<String> = [
            "app.getmaincourse.com",
            "cook.hauptgang.app"
        ]
    }
```

- [ ] **Step 4: Update DeepLinkRouter**

In `DeepLinkRouter.swift`, replace line 64:

```swift
        guard url.host == "cook.hauptgang.app" else { return nil }
```

with:

```swift
        guard let host = url.host, Constants.DeepLinks.universalLinkHosts.contains(host) else { return nil }
```

Update the two doc comments (lines 46 and 62) that read `https://cook.hauptgang.app/invite/{token}` to `https://app.getmaincourse.com/invite/{token}`.

- [ ] **Step 5: Run the tests**

Run: `bin/ios-test`
Expected: all pass, including `testExtractToken_universalLink_wrongHost` (`evil.com` still rejected) and the existing `cook.hauptgang.app` tests.

- [ ] **Step 6: Lint and commit**

```bash
bin/ios-format && bin/ios-lint
git add hauptgang-ios/Hauptgang/Utilities/Constants.swift \
        hauptgang-ios/Hauptgang/Services/DeepLinkRouter.swift \
        hauptgang-ios/HauptgangTests/Services/DeepLinkRouterTests.swift
git commit -m "feat: talk to app.getmaincourse.com and accept both invite hosts"
```

---

### Task 7: Add the new domain to both entitlement files

Both files must change. Debug builds use `Hauptgang.entitlements`; App Store builds use `Hauptgang.Release.entitlements`. The old entries stay — removing them would stop iOS offering users' saved passwords at login.

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Hauptgang.entitlements:12-15`
- Modify: `hauptgang-ios/Hauptgang/Hauptgang.Release.entitlements:12-15`

- [ ] **Step 1: Update both files**

In each file, replace:

```xml
	<key>com.apple.developer.associated-domains</key>
	<array>
		<string>applinks:cook.hauptgang.app</string>
		<string>webcredentials:cook.hauptgang.app</string>
	</array>
```

with:

```xml
	<key>com.apple.developer.associated-domains</key>
	<array>
		<string>applinks:app.getmaincourse.com</string>
		<string>webcredentials:app.getmaincourse.com</string>
		<string>applinks:cook.hauptgang.app</string>
		<string>webcredentials:cook.hauptgang.app</string>
	</array>
```

- [ ] **Step 2: Confirm both files changed**

```bash
grep -c "getmaincourse" hauptgang-ios/Hauptgang/Hauptgang.entitlements \
                        hauptgang-ios/Hauptgang/Hauptgang.Release.entitlements
```

Expected: `2` for each file.

- [ ] **Step 3: Build to confirm the entitlements are still valid**

```bash
bin/ios-build
```

Expected: build succeeds. A code-signing failure here means the Associated Domains capability is not enabled for the App ID — enable it in the Apple Developer portal for `app.hauptgang.ios`.

- [ ] **Step 4: Commit**

```bash
git add hauptgang-ios/Hauptgang/Hauptgang.entitlements \
        hauptgang-ios/Hauptgang/Hauptgang.Release.entitlements
git commit -m "feat: associate the app with app.getmaincourse.com

Keeps the cook.hauptgang.app entries so existing saved passwords and
already-sent invite links keep working."
```

---

### Task 8: Cancel 1.0.3 and release 1.0.4

Version 1.0.3 is sitting in review with the old URLs and a binary pointing at the old host. It is replaced rather than amended.

**Files:**
- Modify: `hauptgang-ios/project.yml` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`)

**Interfaces:**
- Consumes: Tasks 3, 6, 7 — the new host must be live and the app must point at it before submitting.

- [ ] **Step 1: Confirm the new host is live**

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://app.getmaincourse.com/up
```

Expected: `200`. **Do not proceed otherwise** — shipping a build aimed at a dead host breaks every install that updates.

- [ ] **Step 2: Cancel the in-flight submission**

```bash
asc submit cancel --version-id d9b0825e-96a2-4c87-aeec-7160e6bacd4d --confirm
asc review status --app 6758990872
```

Expected: 1.0.3 leaves `WAITING_FOR_REVIEW`.

- [ ] **Step 3: Check what version and build numbers are free**

```bash
asc versions list --app 6758990872 --output table
asc builds info --latest --app 6758990872 --pretty
```

Both the new marketing version and build number must exceed everything listed. The plan assumes `1.0.4` / `64`; use higher numbers if these are taken.

- [ ] **Step 4: Bump the version**

In `hauptgang-ios/project.yml`, under `settings:`:

```yaml
  MARKETING_VERSION: "1.0.4"
  CURRENT_PROJECT_VERSION: "64"
```

- [ ] **Step 5: Regenerate the project and commit**

```bash
cd hauptgang-ios && xcodegen generate && cd ..
git add hauptgang-ios/project.yml hauptgang-ios/Hauptgang.xcodeproj
git commit -m "chore: bump to 1.0.4 build 64"
```

- [ ] **Step 6: Release**

Follow the `deploying-maincourse` skill from its step 3 onward (stage the build with an explicit `--build-number`, set What's New for all three locales, validate, submit). Note the returned `versionId` — Task 9 needs it.

Suggested What's New: `"Bug fixes and improvements."` (en-US, en-GB) and `"Fehlerbehebungen und Verbesserungen."` (de-DE).

---

### Task 9: Update the App Store Connect links

Trailing slashes are intentional: `getmaincourse.com/privacy` and `/support` both 307 to the slashed form, and App Store Connect's URL check should not have to follow a hop.

**Files:** None. `asc` CLI only.

**Interfaces:**
- Consumes: the 1.0.4 `versionId` from Task 8.

- [ ] **Step 1: Set marketing and support URLs on all three locales**

Substitute the 1.0.4 version ID for `<VERSION_ID>`:

```bash
for loc in en-US en-GB de-DE; do
  asc localizations update --version <VERSION_ID> --locale "$loc" \
    --marketing-url "https://getmaincourse.com/" \
    --support-url "https://getmaincourse.com/support/"
done
```

Flag names verified against `asc localizations update --help` on 2026-08-30.

- [ ] **Step 2: Find the editable app info**

```bash
asc apps info list --app 6758990872
```

Pick the one whose state is **not** `READY_FOR_DISTRIBUTION` — that is the editable record. (On 2026-08-30 the editable one was `0b35aa81-c19b-4c53-b090-5841dcc043f8`, but this changes per release cycle.)

- [ ] **Step 3: Set the privacy policy URL on all three locales**

```bash
for loc in en-US en-GB de-DE; do
  asc localizations update --app 6758990872 --app-info <APP_INFO_ID> --type app-info \
    --locale "$loc" --privacy-policy-url "https://getmaincourse.com/privacy/"
done
```

- [ ] **Step 4: Verify no old domain remains in the metadata**

```bash
asc localizations list --version <VERSION_ID> --pretty | grep -i "url"
asc localizations list --app 6758990872 --app-info <APP_INFO_ID> --type app-info --pretty | grep -i "privacy"
```

Expected: every URL is on `getmaincourse.com`. No `hauptgang.app`, no `maincourse.szymonnastaly.com`.

- [ ] **Step 5: Validate and submit**

```bash
asc validate --app 6758990872 --version 1.0.4
```

Expected: `0 blocking`. Then submit per the `deploying-maincourse` skill step 6.

---

### Task 10: Manual device verification

Cannot be automated; do it once 1.0.4 is installable via TestFlight.

- [ ] **Step 1: Fresh install and login**

Install 1.0.4 on a device, log in. Confirm recipes load — this proves the new API host works end to end.

- [ ] **Step 2: Old invite link opens the app**

Send yourself `https://cook.hauptgang.app/invite/<a real pending token>` in Messages and tap it. Expected: the app opens to the invitation.

- [ ] **Step 3: New invite link opens the app**

Create a fresh invitation in the app (it will now be minted on `app.getmaincourse.com`), send it to yourself, tap it. Expected: the app opens to the invitation.

If a link opens Safari instead, the association file has not propagated yet — Apple's CDN refreshes within 24 hours. Delete and reinstall the app to force a re-fetch before investigating further.

- [ ] **Step 4: An old build still works**

On a second device still running 1.0.2 or 1.0.3, confirm recipes still load. This proves the old host is still serving.

- [ ] **Step 5: Saved password still offered**

On a device that had a password saved for the app, log out and back in. The saved password should still appear above the keyboard — this confirms the legacy `webcredentials` entry survived.

---

## Deferred: December 2026 decommission

**Do not run these until 1.0.4+ adoption is confirmed.** They must all complete before `hauptgang.app` expires in January 2027.

### Task 11: Check remaining old-host traffic and warn users

- [ ] **Step 1: Measure**

```bash
bin/logs
```

Log lines are tagged with the request host (Task 2). Count how many still show `cook.hauptgang.app`.

- [ ] **Step 2: Contact stragglers**

The user base is small enough to message directly. Tell them to update, and that they may need to re-enter their password once after updating.

### Task 12: Drop the legacy domain from the app

Must ship **before** the domain expires. Association is verified only by the domain serving a file naming our app ID — a string anyone can write. If the app still claims a domain someone else owns, that owner could capture universal links and shared web credentials for our app.

- [ ] **Step 1: Remove the legacy entries from both entitlement files**

Delete these two lines from `hauptgang-ios/Hauptgang/Hauptgang.entitlements` and `hauptgang-ios/Hauptgang/Hauptgang.Release.entitlements`:

```xml
		<string>applinks:cook.hauptgang.app</string>
		<string>webcredentials:cook.hauptgang.app</string>
```

- [ ] **Step 2: Remove the legacy host from the deep link allowlist**

In `hauptgang-ios/Hauptgang/Utilities/Constants.swift`, reduce `DeepLinks.universalLinkHosts` to:

```swift
        static let universalLinkHosts: Set<String> = ["app.getmaincourse.com"]
```

- [ ] **Step 3: Update the tests**

In `DeepLinkRouterTests.swift`, change every `cook.hauptgang.app` URL to `app.getmaincourse.com`, and change `testExtractToken_universalLink_legacyHostStillAccepted` to assert nil, renaming it `testExtractToken_universalLink_legacyHostRejected`.

- [ ] **Step 4: Test, bump, release**

Run `bin/ios-test`, then release 1.0.5 per the `deploying-maincourse` skill.

### Task 13: Remove the legacy host from the server

- [ ] **Step 1: Drop it from Rails and Kamal**

Remove `"cook.hauptgang.app"` from `config.hosts` in `config/environments/production.rb`, and remove the `- cook.hauptgang.app` line from `proxy.hosts` in `config/deploy.yml`.

- [ ] **Step 2: Deploy and verify**

```bash
bin/kamal deploy
curl -s -o /dev/null -w "new host: %{http_code}\n" https://app.getmaincourse.com/up
```

Expected: `200` on the new host.

- [ ] **Step 3: Commit**

```bash
git add config/environments/production.rb config/deploy.yml
git commit -m "chore: stop serving cook.hauptgang.app"
```

### Task 14: Retire the Cloudflare zone

- [ ] **Step 1: Confirm nothing points at the old domain**

```bash
asc localizations list --app 6758990872 --app-info <CURRENT_APP_INFO_ID> --type app-info --pretty | grep -i privacy
grep -rn "hauptgang.app" --include="*.rb" --include="*.swift" --include="*.yml" --include="*.entitlements" --include="*.md" . | grep -v docs/superpowers
```

Expected: no live references outside the spec and plan documents.

- [ ] **Step 2: Delete the zone**

```javascript
async () => {
  return cloudflare.request({
    method: "DELETE",
    path: "/zones/044bbc56097b8c3c997a0182d6405703"
  });
}
```

- [ ] **Step 3: Let the registration lapse**

No action. The domain expires in January 2027.
