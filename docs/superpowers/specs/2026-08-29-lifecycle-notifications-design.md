# Lifecycle Notifications & Engagement Tracking — Design

Date: 2026-08-29
Status: Proposed

## Problem

Users import recipes and then stop using the app. Importing is a one-shot act with
its own reward; nothing pulls the user back to the recipe afterwards. With meal
planning being removed, the remaining loop is **import → shopping list → cook**,
and the only naturally recurring behaviour is grocery shopping.

The app currently sends push notifications only for collaborative events in shared
cookbooks (`PendingNotification`, categories `shopping_list` and
`meal_plan_activity`). There are no lifecycle or re-engagement notifications, and no
record of whether a user ever looked at a recipe again after saving it.

## Goals

1. Track the minimum engagement signal needed to tell a used recipe from a forgotten one.
2. Send a small number of genuinely useful notifications that pull the user back into
   the shopping-list / cooking half of the loop.
3. Be able to tell, by reading the database, whether a notification led to anything.

## Non-goals

- Meal-plan-based notifications (the feature is being removed).
- A general-purpose analytics pipeline, event firehose, or third-party analytics gem.
  At this app's scale (`AGENTS.md`: "used by a handful of people") the console is the
  analytics tool, and campaign eligibility is better expressed as a query over domain
  state than as an aggregation over event blobs.
- A/B holdout cohorts. With this few users a control group is statistically
  meaningless and halves an already tiny sample.
- Dwell-time / "scrolled into instructions" tracking. Real client-side complexity
  (foreground/background transitions, offline queueing) for a refinement that cannot
  be measured at this size.
- Rich (image) notifications. These need `mutable-content: 1` plus a Notification
  Service Extension in the iOS app; deferred as a follow-up. See "Deferred".
- The home-screen widget, and in-app prompts to invite a household member. Both are
  worthwhile retention levers but are iOS-side work independent of this subsystem.

## What already exists (and needs no new tracking)

- `shopping_list_items.source_recipe_id` + `checked_at` — ingredients added from a
  recipe and later checked off is a good "this was cooked" inference, available today.
- `recipes.created_at` plus the absence of any `shopping_list_items` for that recipe
  is "saved but never used", available today.
- `DeviceToken` (with `environment`) and `Apns::Client.push` — the delivery path.
- `Recipe#cover_image` — for the deferred rich-notification work.

## Schema

Three migrations, no rollup jobs, no raw event table.

### `users` — new columns

| column | type | notes |
| --- | --- | --- |
| `time_zone` | string, default `"UTC"`, not null | IANA identifier. Prerequisite for scheduled sends. |
| `last_active_at` | datetime, nullable | Dormancy signal and suppression input. |
| `lifecycle_notifications_enabled` | boolean, default `true`, not null | Single user-facing toggle for this whole category. |

`time_zone` is supplied by the iOS client on `POST /api/v1/device_tokens` (an extra
`time_zone` param) and written to the user. `last_active_at` is touched from the API
base controller on any authenticated request, throttled to at most once per hour to
avoid a write on every request.

### `recipe_engagements` — new table

One row per (user, recipe). Created lazily on first view, list-add, or suggestion.
Per-user rather than columns on `recipes` because recipes live in shared cookbooks and
two members have different histories with the same recipe.

| column | type |
| --- | --- |
| `user_id` | integer, not null, FK |
| `recipe_id` | integer, not null, FK |
| `last_viewed_at` | datetime, nullable |
| `view_count` | integer, default 0, not null |
| `added_to_list_at` | datetime, nullable |
| `cooked_at` | datetime, nullable |
| `last_suggested_at` | datetime, nullable |
| `suggested_count` | integer, default 0, not null |
| timestamps | |

Unique index on `[user_id, recipe_id]`; index on `[user_id, last_viewed_at]` for the
resurface query.

`last_suggested_at` / `suggested_count` are what stop a campaign re-pushing the same
recipe forever and let it rotate fairly through a library.

### `notification_deliveries` — new table

| column | type |
| --- | --- |
| `user_id` | integer, not null, FK |
| `campaign` | string, not null |
| `recipe_id` | integer, nullable, FK |
| `cookbook_id` | integer, nullable, FK |
| `sent_at` | datetime, not null |
| `opened_at` | datetime, nullable |
| `action_taken` | string, nullable |
| timestamps | |

Index on `[user_id, sent_at]` — read by the frequency cap on every evaluation.

## Signal capture

### Views

New endpoint `POST /api/v1/recipe_views`, accepting a batch:

```json
{ "views": [ { "recipe_id": 12, "viewed_at": "2026-08-29T18:03:11Z" } ] }
```

The iOS client records a view when the recipe detail screen appears, queues them
locally, and flushes on the next sync — following the existing offline-sync patterns
(`docs/ios-offline-sync-patterns.md`). Kitchens have bad wifi; without local queueing
"never viewed" would be wrong for exactly the users who cook the most.

For each entry the server upserts the `recipe_engagement`, sets
`last_viewed_at = max(existing, viewed_at)` and increments `view_count`. Recipes the
user cannot access are silently skipped.

**Idempotency:** deliberately not exact. A retried batch may double-count
`view_count`. `last_viewed_at` is idempotent under `max`, and it is the field
campaigns actually read, so a dedupe key is not worth the complexity.

### List adds and cooked

Hooked into the existing shopping-list flow:

- On creating a `shopping_list_item` with a `source_recipe_id`, set
  `added_to_list_at` (first write wins) on the engagement row of **every member of the
  cookbook**, creating rows as needed.
- On an item transitioning to checked (`checked_at` going from nil to set), set
  `cooked_at` on the engagement row of **every member of the cookbook** for
  `source_recipe_id`.

A shared cookbook is treated as a household that cooks together, so one member
shopping or checking off credits everyone. This is the deliberate worst-case
assumption: it is better to consider a recipe used for someone who only ate it than to
push them a reminder about a meal they had last week.

The write fans out over `cookbook.cookbook_memberships`, so a member joining later
gets no retroactive credit — acceptable, since they were not in the household when it
was cooked.

`cooked_at` is an inference, not a fact — it misses cooking from ingredients already
at home. That is accepted for now; an explicit "cooked it" tap is a natural later
addition and would write the same column.

## Campaigns

Each campaign is a plain class under `app/models/notifications/` exposing
`eligible_for(user)` returning a target (or nil) and building the notification. They
are evaluated in the priority order below; **at most one lifecycle notification is
sent to a user per evaluation.**

### 1. `ImportFollowUpCampaign`

The highest-intent moment there is — the recipe is still fresh in the user's mind.

Eligible when the user has a recipe where:
- `import_status` is `completed` and `created_at` is between 2 and 7 days ago
- no `shopping_list_items` exist with that `source_recipe_id`
- the engagement row is missing, or `last_viewed_at` is before `created_at + 1 hour`
  (i.e. never returned to it after the import itself)
- `last_suggested_at` is nil

Newest matching recipe wins. Body: `You saved "{name}" a couple of days ago — add the
ingredients to your shopping list?` Deep-links to the recipe.

### 2. `StaleShoppingListCampaign`

Triggered by list state rather than by an inferred shopping day-of-week. A
day-of-week histogram over `checked_at` was considered and rejected: at this scale
there is not enough history to infer a shopping day reliably, and state-based
triggering needs no accumulation to start working. The histogram is a later
refinement if this proves too blunt.

Eligible when, in a cookbook the user belongs to:
- there are at least 3 unchecked `shopping_list_items`
- the oldest unchecked item is at least 3 days old
- no item in that cookbook was created, checked, or otherwise updated in the last 3
  days (deletions leave no row, so `updated_at` is the available proxy)

Body: `{n} items are still on your shopping list.` Deep-links to the shopping list.

### 3. `ResurfaceCampaign`

Eligible when the user has a recipe where:
- `created_at` is at least 14 days ago
- `created_at` is after `VIEW_TRACKING_SINCE` (a constant set to the deploy date of
  view tracking). Without this gate every pre-existing recipe looks "never viewed"
  and the campaign would fire on the entire library on day one.
- the engagement row is missing or `last_viewed_at` is nil
- `added_to_list_at` is nil
- `last_suggested_at` is nil or more than 30 days ago

Oldest matching recipe wins, so the library rotates. Body: `You saved "{name}" a while
back. Cook it this week?` Deep-links to the recipe.

## Scheduling and suppression

A Solid Queue recurring task runs `EvaluateLifecycleNotificationsJob` **hourly**. For
each user it:

1. Skips unless the user's local time (from `users.time_zone`) is within the send
   window — a single hour, 17:00–18:00 local, chosen as the point in the day when
   someone is deciding what to cook and can still act on it.
2. Skips unless `lifecycle_notifications_enabled` and the user has at least one active
   `DeviceToken`.
3. Skips if `last_active_at` is within the last 24 hours. Someone already using the
   app does not need to be told to use the app.
4. Skips if any `notification_deliveries` row exists for the user in the last 4 days.
   This is the frequency cap, and it spans all lifecycle campaigns.
5. Evaluates the campaigns in priority order and sends the first match.

On send: write a `notification_deliveries` row, bump `last_suggested_at` and
`suggested_count` on the engagement row when the target is a recipe, then push via
`Apns::Client` to each active device token, destroying tokens rejected with the
existing `INVALID_TOKEN_REASONS` list (mirroring `DeliverPendingNotificationJob`).

The APNs custom payload carries `{ campaign, delivery_id, recipe_id, cookbook_id }`.
The iOS client routes on `recipe_id` / `cookbook_id` to the recipe detail or the
shopping list, and posts `POST /api/v1/notification_deliveries/:id/opened` on tap,
which sets `opened_at`.

The same endpoint accepts an optional `action` string. The client posts it a second
time, with `action` set, if the user performs the campaign's intended action — adding
the recipe's ingredients to the list, or checking off a list item — within 24 hours of
the tap. Later posts overwrite `action_taken`; only the last one is kept.

## Preferences

`lifecycle_notifications_enabled` is exposed through the existing
`PATCH /api/v1/account` endpoint and surfaced as one switch in iOS settings. It is
independent of the existing collaborative notifications, which stay always-on as they
are today.

## Error handling

- APNs failures are logged; invalid tokens are destroyed. A failed push still leaves
  its `notification_deliveries` row, so a failing device cannot be re-pushed in a loop.
- Missing APNs credentials raise `Apns::Client::MissingCredentialsError` — the hourly
  job rescues per user so one broken user cannot stall the rest.
- Campaign evaluation is read-only until the moment of send; a raise mid-evaluation
  cannot leave partial state.

## Testing

- Model tests for `RecipeEngagement` upsert semantics (`max` on `last_viewed_at`,
  counter increments) and for the shopping-list hooks setting `added_to_list_at` /
  `cooked_at` — including the fan-out to every member of a shared cookbook, and that a
  member who joins afterwards is not credited.
- One test per campaign covering an eligible case and each exclusion, using
  `travel_to` for the time-based conditions.
- Job tests for the suppression rules (send window, active-in-24h, 4-day cap,
  one-notification-per-evaluation), with `Apns::Client` stubbed.
- Request tests for `POST /api/v1/recipe_views` (batch, access control, unknown ids)
  and the delivery-opened endpoint.

## Rollout

1. Migrations and `RecipeEngagement` + shopping-list hooks. Passive — nothing sends.
2. View-tracking endpoint and iOS view pings. Set `VIEW_TRACKING_SINCE` to this
   deploy date.
3. Campaigns 1 and 2, which need no accumulated data.
4. Campaign 3, once view tracking has roughly two weeks of history.

A rake task `notifications:preview` prints, per user, which campaign would fire and
with what body, without sending. At this scale a dry run read by eye is a more
reliable check than any dashboard, and it is the intended way to sanity-check each
campaign before enabling it.

## Deferred

- **Rich notifications** (recipe photo in the push). Needs `mutable-content: 1` — a
  small addition to `Apns::Client` — plus a Notification Service Extension target in
  the iOS project and an authenticated image URL the extension can fetch. Worth doing
  for `ResurfaceCampaign`, whose appeal is largely visual, but text-only works first.
- **Explicit "cooked it" tap**, which would make `cooked_at` a fact rather than an
  inference and feed the future discovery algorithm.
- **Shopping-day inference** from a `checked_at` day-of-week histogram, if state-based
  triggering proves too blunt.
- **Import-source tracking** (web / Instagram / TikTok / photo) as a retention
  predictor, and **cookbook composition** (solo vs. shared) as a segmentation axis.
