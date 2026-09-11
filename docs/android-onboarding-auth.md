# Android onboarding and authentication

The Android app has a first-run onboarding flow followed by email authentication. It borrows the questions and backend contract from iOS while using native Material 3 structure and controls. Apple and Google sign-in are intentionally outside this flow.

## First-run state

`features/auth/PreAuthViewModel.kt` owns the welcome, household, current-recipe-storage, diet, and authentication steps. The state survives activity recreation through the activity-owned view model. `data/onboarding/OnboardingPreferences.kt` persists only the durable boundaries:

- onboarding was completed or skipped;
- the user reached authentication, so process recreation resumes there;
- the anonymous UUID used to submit and later link answers.

Question selections stay in memory. They are product-research answers, not user data that should block onboarding or require a durable offline queue. A failed submission is therefore best-effort and does not prevent account creation. Shared preferences are excluded from Android backup and device transfer by `data_extraction_rules.xml`, so an anonymous UUID is not restored onto another device.

Successful authentication marks first-run onboarding complete in memory and preferences. This is also important when an existing installation restores a saved session before it has an onboarding completion flag: a later sign-out must show the standalone login rather than onboarding.

## API linking

The final question submits `POST /api/v1/onboarding_response` without authentication. Android generates its own UUID because it does not depend on RevenueCat. The accepted answer keys and values match the iOS/Rails contract.

After the question flow reaches authentication, `SessionViewModel` includes that UUID as `onboarding_device_id` in email sign-in and sign-up. Rails links the anonymous response to the authenticated user. Android clears the pending UUID only after it has persisted and published a successful session.

## UI conventions

`features/auth/PreAuthScreen.kt` uses an Android back handler, a Material linear progress indicator, `FilterChip` answer controls, edge-to-edge safe-area handling, and full-width primary actions. The final onboarding step reuses the complete brand-forward `features/auth/AuthScreen.kt` presentation and starts it in sign-up mode. The Android system back action still returns to the final question. Sign-in and sign-up share validation, autofill semantics, password visibility, busy state, and server-error rendering.

Keep provider buttons out of the email form until their authentication flows are implemented. Add them as explicit actions around the shared form rather than coupling provider state to the onboarding steps.

## Verification

Run `bin/android-build`, `bin/android-test`, and `bin/android-test --device`. Unit coverage exercises step rules, answer serialization, API routing, and onboarding ID linkage. Device tests cover the first-run welcome/question transition and the branded sign-up presentation.
