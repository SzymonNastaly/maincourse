# Lifecycle Notifications

Push reminders that bring a dormant user back. This document says when a
notification is sent, what it says, and when the iOS app asks for permission.

## The schedule

`EvaluateLifecycleNotificationsJob` runs every hour at minute 5
(`config/recurring.yml`). It looks at every user with
`lifecycle_notifications_enabled = true`.

## The four gates

A user gets nothing unless all four are true:

1. **Local hour is 17:00** (`SEND_HOUR`). The hour is read in the user's own
   time zone. A user with no valid time zone never passes.
2. **The user has an active device token.** Active means `last_used_at` is
   inside 90 days.
3. **The user was not active in the last 24 hours** (`ACTIVE_SUPPRESSION`,
   read from `last_active_at`). Someone already using the app does not need
   to be told to use the app.
4. **The user got no notification in the last 4 days** (`FREQUENCY_CAP`).
   This cap is shared by every campaign.

## The campaigns

The gates pass, then the campaigns run in priority order. The **first** one
that produces a candidate wins. At most one push per user per evaluation.

### 1. Import follow-up (`import_follow_up`) — live

> You saved "X" a couple of days ago — add the ingredients to your shopping
> list?

The recipe was saved 2 to 7 days ago (`MIN_AGE`, `MAX_AGE`), is complete, is
in a cookbook the user is still a member of, and has:

- no shopping list items
- no earlier suggestion (`last_suggested_at`)
- no view later than 1 hour after the import (`IMPORT_VIEW_GRACE`) — a view
  inside that hour is the import flow itself
- no `added_to_list_at` and no `cooked_at`

The newest such recipe is used.

Shopping list rows are not durable: `ShoppingListItem.cleanup_stale_checked_for`
destroys checked items after an hour. So `where.missing(:shopping_list_items)`
alone cannot tell "never added" from "added, checked off, cleaned up". The
`RecipeEngagement` row survives the cleanup and is the durable signal.

### 2. Stale shopping list (`stale_shopping_list`) — live

> N items are still on your shopping list.

Evaluated per cookbook, in order. A cookbook is stale when:

- it holds 3 or more unchecked items (`MIN_ITEMS`)
- the oldest unchecked item is older than 3 days (`STALENESS`)
- no item was touched in the last 3 days

The same cookbook is not used again for 14 days (`RESUGGEST_AFTER`). Without
that cooldown an abandoned list stays stale forever and re-fires the same
message, which starves the lower-priority campaigns.

### 3. Resurface (`resurface`) — written, but switched off

> You saved "X" a while back. Cook it this week?

The class is deliberately absent from
`EvaluateLifecycleNotificationsJob::CAMPAIGNS`. It reads "forgotten" from the
absence of a view. A client that does not send view pings makes every recipe
look forgotten.

Its rule: the recipe was saved **after `VIEW_TRACKING_SINCE`** and **more than
14 days ago** (`MIN_AGE`), was never viewed, never added to a list, never
cooked, and was not suggested in the last 30 days (`RESUGGEST_AFTER`). The
oldest such recipe is used.

**To switch it on:**

1. Ship the view-ping client to the **App Store** — not only to TestFlight.
   Users on an older build send no view pings.
2. Set `VIEW_TRACKING_SINCE` to that release date.
3. Add `Notifications::ResurfaceCampaign` to `CAMPAIGNS` in the same commit.

Step 3 can go out the same day as step 2. `VIEW_TRACKING_SINCE` is a floor on
the recipe's creation date, so the eligible window is empty for the first 14
days by itself. No waiting period is needed.

## Delivery

`Notifications::Deliver` writes the delivery row first, then pushes. If no
push reaches a device, it destroys the row again and returns `nil`. It also
returns `nil` for a candidate whose cookbook the user has left. The job treats
both cases the same and falls through to the next campaign.

A tapped notification routes to its recipe or to the shopping list, and
reports the delivery as opened.

## When the iOS app asks for permission

iOS shows the permission dialog **once, ever**. Once `authorizationStatus`
leaves `.notDetermined` there is no second chance except the Settings app. So
the app asks only at moments where the payoff is visible:

- **Recipes are on screen** — after the splash lifts and the library is not
  empty. This covers a reinstall by a user who already has recipes.
- **The shopping list holds 3 or more unchecked items** — on opening the
  list, on returning to the tab, and after a cookbook switch. The threshold
  mirrors `StaleShoppingListCampaign::MIN_ITEMS`.
- **Right after "Add N"** in the recipe review sheet, if the list then holds
  3 or more items. The typed path never asks: the keyboard stays up while
  someone types a list, and the third item is the worst moment to interrupt.
- **In Settings**, when the switch is turned on. If the status is already
  `.denied` an alert offers the iOS Settings deep link, which is the only
  route back.

Login itself never asks. It only refreshes the token and time zone for a user
who already said yes.

## Sending one by hand

```ruby
u = User.find_by!(email_address: "someone@example.com")
r = u.recipes.completed.order(created_at: :desc).first

c = Notifications::Candidate.new(
  campaign: "resurface",
  recipe: r,
  cookbook: r.cookbook,
  title: "Hauptgang",
  body: %(You saved "#{r.name}" a while back. Cook it this week?)
)

Notifications::Deliver.new(user: u, candidate: c).call
```

Run it with `bin/kamal app exec -i --reuse "bin/rails runner -" < script.rb`.
This bypasses the job, so it ignores the send hour, the active-user
suppression, and the frequency cap.
