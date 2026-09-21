# Keeping recipe screens awake

Loaded recipe details automatically request that the display stay on. There is
no manual cooking-mode button. Loading/error screens and recipe lists do not
request this behavior. Leaving the recipe or backgrounding the app releases it;
returning to the recipe requests it again. The device can still be locked manually.

Native clients pause this behavior in system Low Power Mode / Battery Saver and
react when that mode changes. This is an app policy, not an iOS requirement.
There is no app-defined battery-percentage cutoff.

- **iOS:** `RecipeDetailContentView` uses `RecipeScreenAwakeModifier`, including
  in the onboarding demo. Visibility, scene activity and power-mode notifications
  update an app-wide set of request owners before setting `isIdleTimerDisabled`.
  Separate owners prevent an outgoing view from clearing an incoming recipe's
  request during navigation.
- **Android:** the loaded detail screen uses Compose's `keepScreenOn` modifier,
  gated by the screen lifecycle being resumed and Battery Saver being off.
  It does not acquire a background CPU wake lock or require wake-lock permission.
- **Web:** `screen_wake_lock_controller.js` uses the Screen Wake Lock API on a
  visible, secure recipe page. It releases on page hiding, Turbo caching and
  controller disconnect, and requests again on return. Unsupported browsers,
  denied requests and OS revocation are accepted without a retry loop. The
  browser decides whether battery conditions permit keeping the screen awake.

Regression tests cover native ownership/lifecycle and power mode, and web Turbo
navigation, visibility, refusal and asynchronous request completion after leaving.
Simulator/browser tests verify API ownership; physical-device battery drain and
vendor-specific power policies require hardware checks.
