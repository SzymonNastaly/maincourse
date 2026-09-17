# Android networking

`AppContainer` supplies Retrofit with `data/network/ApiCallFactory.kt`. Service
tests use the same factory so production connection policy is covered alongside
API routing and authentication.

## Connection recovery and mutations

OkHttp 5's fast fallback races alternate server addresses before sending an HTTP
request. This matters on Wi-Fi networks where DNS returns both IPv6 and IPv4 but
one route is unusable. It also works with connection-retry disabled: establishing
a connection and replaying an HTTP request are separate operations in OkHttp 5.

The factory has two clients sharing a connection pool and dispatcher:

- GET/HEAD use normal connection recovery, including stale pooled connections.
- Mutations disable connection recovery and use one-shot request bodies. The
  latter also blocks response-driven retries such as `503 Retry-After: 0`, which
  the connection-retry flag alone does not prevent. DELETE receives an empty
  one-shot body for the same reason.

Keep both policies when changing dependencies. A connection may safely fall back
before transmitting a mutation, but a lost response must not cause the app to
repeat a mutation whose server-side outcome is unknown. Deliberate user retries
remain separate calls; Room is updated only after acknowledgement.

`ApiCallFactoryTest` covers alternate-address fallback, stale read recovery,
lost mutation responses and response-driven retries. `AuthInterceptorTest` and
`MainCourseServiceTest` exercise this factory too.

## Error reporting and diagnostics

`ApiErrorMessage.kt` distinguishes timeout, DNS, connect, TLS and interrupted
socket failures. Other errors retain their operation-specific message. A network
exception alone is not evidence that the device is offline; local filesystem
errors can also be `IOException`s.

Every failed API call emits a `MainCourseNetwork` warning through
`ApiNetworkDiagnostics`. Fields include the Retrofit operation name, HTTP method,
hostname, last connection/request phase, elapsed time, failure class, last failed
connection's address family and cancellation status. Fast fallback can have
multiple connection attempts; the connection-failure field is only the most
recent failed attempt, not a complete trace.

Logs deliberately exclude URL paths and queries (invitations and device-token
operations contain secrets there), headers, bodies, Retrofit arguments and
exception messages. Do not replace this with full request/response logging.

Capture a reproduction from a connected phone with:

```bash
adb logcat -v time -s MainCourseNetwork:W
```

Include the installed package/version, Android version and network type when
reporting a problem. See `docs/android-release.md` for identifying Play versus
debug installations.

## Refresh state

Recipe detail and its supplementary shopping-list review refresh run as children
of one cancellable ViewModel refresh job. Recipe loading/error state finishes
with the recipe request. Shopping-review readiness waits for its own refresh
attempt and the initial Room emission; failing that request can use cached items.

Shopping normally refreshes cookbooks before its selected list. A cookbook
transport failure can still refresh an already cached selection. Authentication
and other HTTP errors are not swallowed by this fallback, and a successful
server response removing a cookbook is authoritative.
