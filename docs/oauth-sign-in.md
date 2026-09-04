# OAuth sign-in

MainCourse supports Sign in with Apple and Google on the Rails web app and the
native iOS app. Email and password remain available. Provider identity is
resolved by the stable `(provider, uid)` pair. A verified provider may join an
existing OAuth-only user with the same email. It does not automatically join a
password account because password signup does not verify email ownership; the
user is asked to use their password instead. Authenticated account linking can
be added separately when needed.

## Architecture

Browser sign-in runs through OmniAuth middleware configured in
`config/initializers/omniauth.rb`. The callback creates the existing database
`Session` and signed `session_id` cookie.

Native sign-in runs through Apple's `AuthenticationServices` framework or the
official `GoogleSignIn-iOS` package. iOS sends the signed ID token and a nonce to
`POST /api/v1/oauth_session`. Rails verifies the provider signature, issuer,
audience, expiry, email verification, and nonce before issuing the same opaque
`ApiToken` used by password login. Apple authorization codes are also exchanged
server-side so MainCourse can retain a refresh token for account-deletion
revocation.

Provider accounts live in `identities`. Apple refresh tokens use Rails Active
Record Encryption, so the SQLite database and Litestream backups contain only
ciphertext. OAuth-only users have a nullable `password_digest`; they can later
set a password through the existing reset-password flow.

## Apple configuration

In Apple Developer Certificates, Identifiers & Profiles:

1. Enable **Sign in with Apple** for App ID `app.hauptgang.ios`.
2. Create a Services ID, such as `app.hauptgang.web`, and configure its primary
   App ID as `app.hauptgang.ios`. This grouping is what gives web and iOS the
   same Apple subject identifier.
3. Configure domain `app.getmaincourse.com` and return URL
   `https://app.getmaincourse.com/auth/apple/callback` on that Services ID.
4. Create a Sign in with Apple key for the same primary App ID. Download the
   `.p8` once and record its Key ID.
5. Register the production email domain and sender with Apple's private email
   relay before sending lifecycle or password-reset mail to relay addresses.

Add the values to encrypted Rails credentials with
`bin/rails credentials:edit`:

```yaml
apple:
  team_id: YOUR_APPLE_TEAM_ID
  key_id: YOUR_SIGN_IN_WITH_APPLE_KEY_ID
  private_key: |
    -----BEGIN PRIVATE KEY-----
    ...
    -----END PRIVATE KEY-----
  services_id: app.hauptgang.web
  bundle_id: app.hauptgang.ios
```

Keep the final newline after the private-key footer. The web provider button is
hidden until all browser credentials are present. The native entitlement is in
both `Hauptgang.entitlements` files, but Apple must also enable the capability
on the App ID and regenerate provisioning profiles.

Web provider buttons are also hidden on the temporary legacy host because OAuth
state cookies cannot cross to the canonical callback host.

Apple web callbacks use `response_mode=form_post`. Production therefore sets
the transient Rails session cookie to `SameSite=None; Secure`; otherwise the
browser withholds OmniAuth's state and nonce on the cross-site POST. The actual
signed login cookie remains `SameSite=Lax`.

## Google configuration

Create both clients in one Google Cloud project so their subject identifiers
match:

1. Configure the OAuth consent screen with `openid`, `email`, and `profile`.
2. Create a Web application client. Add
   `https://app.getmaincourse.com/auth/google_oauth2/callback` and, for local
   development, `http://localhost:3000/auth/google_oauth2/callback`.
3. Create an iOS client for bundle ID `app.hauptgang.ios`.

Add the web client to Rails credentials:

```yaml
google:
  client_id: YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
  client_secret: YOUR_WEB_CLIENT_SECRET
  ios_client_id: YOUR_IOS_CLIENT_ID.apps.googleusercontent.com
```

Replace the three `REPLACE_WITH_...` build settings in
`hauptgang-ios/project.yml`:

```yaml
GOOGLE_IOS_CLIENT_ID: YOUR_IOS_CLIENT_ID.apps.googleusercontent.com
GOOGLE_SERVER_CLIENT_ID: YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
GOOGLE_REVERSED_CLIENT_ID: com.googleusercontent.apps.YOUR_IOS_CLIENT_ID
```

Run `xcodegen generate` after changing `project.yml`. The native Google button
is hidden while the placeholder client ID remains.

## Account deletion

Both Rails account-deletion controllers call `Oauth::AppleTokenRevoker` before
destroying a user. Each Apple identity keeps an encrypted refresh token per
client ID because Apple revocation must use the same client that obtained each
token.
Revocation failures are reported through `Rails.error` but do not prevent local
account deletion.

Google ID tokens are used only to authenticate; MainCourse does not retain a
Google refresh token or request Google API access.

## Development and verification

Google browser sign-in works on localhost using the registered callback. Apple
does not accept localhost web return URLs; use an HTTPS domain registered on
the Services ID or test after deployment. Native Apple sign-in can authenticate
against local Rails from the simulator once the Apple capability and Rails
credentials are configured.

Provider JWKS and Apple token endpoint calls are stubbed in the Rails test
suite. Run `bin/ci`, `bin/ios-build`, and `bin/ios-test` after authentication
changes. End-to-end provider tests still require real Apple/Google accounts and
configured provider consoles.
