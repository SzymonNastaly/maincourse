# iOS cooking timers

A recipe step that names a duration ("Simmer for 10–12 minutes") shows that
duration as a chip inside the sentence. A tap starts a timer. Running timers
dock above the tab bar on every screen, ring through AlarmKit when the app is
in the background, and come back after the app was killed.

## Data source

The server parses durations; the app does not. Recipe detail and batch JSON
carry `instruction_timers`, one array per step, aligned with `instructions`:

```json
"instruction_timers": [
  [{"text": "1 minute", "min_seconds": 60, "max_seconds": 60, "label": "Cook gently", "per_side": false}],
  [{"text": "6–8 minutes", "min_seconds": 360, "max_seconds": 480, "label": "Cook", "per_side": false}],
  []
]
```

- `InstructionTimer` (`Models/InstructionTimer.swift`) decodes one entry.
  `label` is already in the recipe's language. `per_side` adds "per side" to
  the timer's label and nothing else.
- `RecipeDetail.instructionTimers` is optional: older servers omit the key.
  Read it through `timers(forStep:)`, which treats a missing key, a short array
  and an empty step the same way: no timers.
- `StepTimerLocator` (`Utilities/StepTimerLocator.swift`) finds each `text` in
  its step: the first occurrence after the previous timer's match, preferring
  one that is not part of a longer word or number. A timer whose text is
  missing is skipped. `words(in:located:)` splits the step into the units the
  layout wraps.

## Offline cache

`PersistedRecipe.instructionTimersJson` stores the arrays as JSON. It arrived in
SwiftData schema V10 with a lightweight migration from V9; V9's recipe model is
frozen in `HauptgangSchemaV9.swift`. Recipes cached before V10 have no timers
until their next detail sync. `HauptgangMigrationPlanTests` covers V9 → V10.

## The store

`CookingTimerStore` (`ViewModels/CookingTimerStore.swift`) is the app-wide list
of `CookingTimer` values. The app injects `CookingTimerStore.shared` at the root
in `HauptgangApp`.

- A timer stores an end date while running and the time left while paused, not
  a ticking counter. Views compute the countdown from the clock with
  `TimelineView(TimerTick.everySecond)`.
- A timer keeps its recipe id and name, step number, label and the step's
  source text. A chip finds its running timer by recipe, step and text.
- `start`, `pause`, `resume`, `add(seconds:to:)` and `cancel` change the list
  and then tell the alerting seam. Starting the same duration again replaces
  its timer.
- `takeNewlyFinished()` reports each finish once; the tab view uses it for a
  success haptic while the app is open.
- The store takes a `now` closure, so the tests drive the clock.

## The alerting seam

`TimerAlerting` (`Services/CookingTimers/TimerAlerting.swift`) mirrors the
store's timers as system timers and reports changes made outside the app as
`SystemTimerChange` values. `SystemTimerAlerting.make()` picks the
implementation:

| System | Implementation | What rings |
| --- | --- | --- |
| iOS 26.1 and later, alarms allowed | `AlarmKitTimerAlerting` | An AlarmKit countdown, like the Clock timer. It sounds in silent mode and shows a Live Activity. |
| Earlier, or alarms declined, or AlarmKit refuses one | `NotificationTimerAlerting` | One time-sensitive local notification at the end time. |

Each implementation runs its system calls in order through `SerialTasks`, so a
pause issued while the schedule call is still waiting on the permission prompt
lands after it.

### AlarmKit and the Live Activity

- AlarmKit is weak-linked (`OTHER_LDFLAGS` in `project.yml`), so iOS 18 still
  launches. `NSAlarmKitUsageDescription` in `Info.plist` and
  `InfoPlist.xcstrings` explains the permission prompt, which appears on the
  first timer.
- AlarmKit cannot change a countdown's length, so `+1 min` cancels the alarm and
  schedules a new one under the same id.
- The alert offers "+1 min" as a second countdown (`postAlert`). Stop on the
  alert removes the alarm; the app sees it vanish from `alarmUpdates` and
  removes the timer.
- `CookingTimersWidget` is a widget extension (iOS 26.1 deployment target) that
  draws the lock screen and Dynamic Island for `AlarmAttributes<CookingTimerMetadata>`.
  The system draws alarm UI on black, so the tint is `mcLime`; deep green
  disappears there. Its pause, resume and stop buttons are `LiveActivityIntent`s
  that call `AlarmManager` from the app's process.
- `CookingTimerAlarm.swift` is compiled into both targets: the metadata, the
  intents and the clock formatter. The widget has its own string catalog.

### Notification fallback

The request's `userInfo` carries the encoded `CookingTimer`. Pausing removes the
request and resuming schedules it again. The fallback needs the time-sensitive
entitlement (`com.apple.developer.usernotifications.time-sensitive`) to break
through Focus.

## Rebuilding after the app was killed

AlarmKit keeps running alarms after the app is killed; the in-memory list does
not survive. `CookingTimerStore.restoreFromSystem()` runs when the tab view
appears and asks the seam for the timers the system still holds:

- AlarmKit: `AlarmManager.alarms` joined by id with
  `Activity<AlarmAttributes<CookingTimerMetadata>>.activities`. `Alarm` has no
  metadata and no fire date; the activity has both. The metadata carries every
  field of the timer, and the activity's `AlarmPresentationState.Mode` gives the
  fire date, the time left while paused, or an alert (finished).
- Notifications: pending requests with the `recipe-timer-` prefix, decoded from
  `userInfo`.

Timers already in the list stay as they are, so restoring twice is harmless. A
restored timer that is already ringing does not play the haptic again.

Two gaps remain. An alarm without a Live Activity (Live Activities turned off
for the app) still rings but does not come back into the app. A timer paused
under the notification fallback has no pending request and is lost when the
app is killed.

## The interface

- `StepRow` keeps a step without timers as one `Text` in a button. A step with
  timers becomes `TimedStepRow`: `StepTimerText` lays the words out with
  `WordFlowLayout`, so each chip is a real button. The layout wraps at word
  boundaries, aligns each line on its first baseline, and takes the space width
  and line spacing from the body font, so the step reads like a plain `Text`.
  A chip and the punctuation touching it never split across lines.
- The chip's hit area reaches 11 points past the drawn chip, and chips sit
  above their neighbours, so a tap near a chip starts it rather than marking
  the step done.
- A single duration starts on a tap. A range opens `StepTimerPopover` with its
  shortest, middle (when it is a whole minute) and longest time. A tap on a
  running chip opens pause, +1 min and stop; on a finished chip, +1 min and Done.
- VoiceOver reads a step as one button. Each timer is an accessibility action
  on it: "Start Simmer timer, 10 minutes to 12 minutes", or "Show Simmer timer"
  once it runs. A range action opens the same popover.
- `RunningTimersDock` shows the timer that needs the cook next (finished
  first, then the soonest), a count of the others, and pause. A tap opens
  `RunningTimersSheet`, every timer grouped by recipe. On iOS 26.1 and later the
  dock is the tab view's bottom accessory, which collapses with the tab bar.
  Before that it is a bar above the tab bar inside each tab
  (`cookingTimersFallbackDock`). The accessory is hosted apart from the tab view
  and does not inherit environment objects, so the store is passed in
  explicitly.

## Tests

- `CookingTimerStoreTests`: start, pause, resume, +1 min and cancel arithmetic,
  finish detection, display order, changes from the system, and rebuilding from
  the system through `FakeTimerAlerting`.
- `AlarmKitTimerRestoreTests`: a Live Activity's countdown, paused and alert
  states turned back into timers.
- `StepTimerLocatorTests`: order, repeats, missing text, embedded numbers,
  emoji before the text, punctuation kept with the chip.
- `InstructionTimerTests`: decoding with the key present, absent and empty,
  range choices, the cache round trip.
- `LocalizationTests`: German and Polish timer strings in the app, `InfoPlist`
  and the widget.

## What needs a real device or the developer portal

- The widget's bundle id `app.hauptgang.ios.timers` and the time-sensitive
  notification capability must exist for the team; automatic signing creates
  them on the first device build.
- Alarm sound in silent mode, Focus break-through of the notification fallback,
  and the success haptic. The simulator plays none of them.
- The iOS 18 to 26.0 path: the fallback bar and the notification. It compiles,
  but the simulators here run iOS 26.5 only.
- Rebuilding with Live Activities turned off, and the Dynamic Island on a real
  phone.
