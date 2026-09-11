# Android cookbook collaboration

Android supports the same single-shared-cookbook lifecycle as the Rails and
iOS clients: create a shared cookbook, optionally move personal content into
it, inspect members, invite another person, join or decline an invitation,
leave as a collaborator, and delete as the owner.

## Data and mutation flow

`CookbookRepository` bridges the cookbook and invitation endpoints with Room.
`cookbooks` and `selected_cookbooks` remain the observable source for every
cookbook-scoped screen. Confirmed create, leave, and delete mutations update
that cache immediately; accepting an invitation refreshes the authoritative
cookbook list and selects the joined cookbook. A minimal selected placeholder
keeps that acknowledged membership usable if the follow-up list refresh fails;
the next refresh replaces it with authoritative member and recipe-count data.
Network mutations are not queued or replayed.

`CookbookManagementViewModel` owns finite management actions and
`InvitationViewModel` owns one invitation preview and its accept/decline
actions. Both observe `CookbookRepository`, so the recipe, shopping, search,
import, and management screens see the same selected cookbook without a
separate coordinator.

The Rails invariant allows one personal cookbook and at most one shared
cookbook per user. The management UI therefore shows either the shared
cookbook or the creation form. Ownership is derived from the current user ID
and the cookbook's returned member roles; only owners can create invite links
or delete, while collaborators can leave.

## Invitation links

`MainActivity` accepts canonical and legacy web invitation URLs plus the
custom scheme:

- `https://app.getmaincourse.com/invite/{token}`
- `https://cook.hauptgang.app/invite/{token}`
- `hauptgang://invite/{token}`

`InvitationLink` validates the scheme, host, and exact path before exposing a
token. A valid token stays in activity state while authentication is pending,
then Navigation 3 opens the invitation preview. It is marked consumed once
the token has been copied into the serializable navigation route, preventing
duplicate screens across activity recreation.

The HTTPS filters request Android App Links verification. Production must
serve a matching `/.well-known/assetlinks.json` for the release application ID
and signing-certificate fingerprint; the custom scheme remains useful for
development and explicit app-to-app launches.

## Verification

Run `bin/android-build`, `bin/android-test`, and `bin/android-test --device`.
JVM coverage validates Retrofit contracts, link parsing, and ViewModel state.
Device coverage validates Room selection reconciliation, manifest intent
resolution, settings navigation, and the post-authentication invitation flow.
