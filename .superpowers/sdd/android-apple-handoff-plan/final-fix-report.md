# Final Android Apple handoff fix report

## Outcome

Both Low correctness findings from `final-review.md` are fixed in scoped Rails
code and integration tests. The implementation commit is `4f9b712` (`Close
Android Apple callback cleanup gaps`).

- An Apple callback with an unknown captured Android handle now remains inside
  the refresh-token cleanup path. It renders the local fixed 400 response,
  creates no user, identity, API token, or browser session, and revokes the
  unused Services ID refresh token exactly once.
- Every failed OmniAuth request phase now removes the shared
  `omniauth.params` session slot. Only Apple request-phase failures additionally
  clear Apple state and nonce. Apple still snapshots a handoff handle only from
  OmniAuth's captured request params, and callback query/body params remain
  untrusted.
- A real-middleware regression covers a Google CSRF failure injected between a
  legitimate Apple web request and its callback. Google retains its default
  failure response, Apple state/nonce survive, the callback completes as a web
  sign-in, and the attacker-selected Android transaction remains unauthorized
  with no exchange code.

Android and iOS sources and their contracts are unchanged. Their previously
recorded gates were not rerun. Existing credential, provider JSON, and skill
changes were not read, staged, reverted, or modified.

## RED / GREEN evidence

Focused tests ran with `CI` unset; the test environment reports
`eager_load=0`.

```text
RED: bin/rails test test/controllers/omniauth_callbacks_controller_test.rb
     27 runs, 191 assertions, 2 failures, 0 errors, 0 skips
     - unknown captured handle recorded zero expected revocations
     - Google CSRF injection authorized the Android transaction instead of
       returning through the normal Apple web callback

GREEN: bin/rails test test/controllers/omniauth_callbacks_controller_test.rb \
         test/controllers/android/apple_authentications_controller_test.rb \
         test/lib/omniauth/strategies/main_course_apple_test.rb
       46 runs, 336 assertions, 0 failures, 0 errors, 0 skips

env -u CI RAILS_ENV=test bin/rails runner \
  'puts "eager_load=#{Rails.application.config.eager_load ? 1 : 0}"'
eager_load=0

bin/rubocop app/controllers/omniauth_callbacks_controller.rb \
  lib/oauth/android_apple_failure_endpoint.rb \
  test/controllers/omniauth_callbacks_controller_test.rb
3 files inspected, no offenses detected
```

Focused logs are in ignored files `tmp/android-apple-focused-red.log` and
`tmp/android-apple-focused-green.log`, with corresponding `.exit` files.

## Full CI gate

```text
bin/ci > tmp/android-apple-ci.log 2>&1
exit recorded in tmp/android-apple-ci.exit: 0
295 Ruby files inspected, no offenses
991 Rails runs, 2967 assertions, 0 failures, 0 errors, 2 expected corpus skips
9 system runs, 41 assertions, 0 failures, 0 errors, 0 skips
Bundler audit, importmap audit, Brakeman, iOS lint/format, setup, and seeds passed
Continuous Integration passed in 23.77s
```

Four earlier full captures reached the same clean 991-test Rails result but each
hit one pre-existing system UI timing failure (cookbook switcher, desktop rail,
add-recipe dialog, or narrow-window drawer). The first three passed immediately
when isolated; all nine system tests passed in the final fresh full run. The
passing run replaced `tmp/android-apple-ci.log` and `.exit`.
