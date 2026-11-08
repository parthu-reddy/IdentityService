# Scoped Maps identity v1

This opt-in `maps` profile is a dedicated instance of the existing authority,
sharing its current account/person-session stores. It is not a second account
service. Default Food Delivery endpoints/contracts remain covered by their
existing test suite. The Maps instance refuses ordinary auth/admin routes.

Supply external signing/verification files, current key ID, HTTPS authority,
exact audience, allowed adapter URI SANs and native client-authenticated TLS
keystore/truststore. Startup refuses missing/optional client verification and
the packaged development signing authority, including in verifier overlap.
Do not expose `/internal/maps/v1/` publicly. Configure network ACLs independently
of certificate validation. Config Server/environment must supply the actual
database/Redis/notification configuration; inspect effective packaged values.
Set `SERVER_PORT=8446` on this instance: an imported settings file outranks the
profile, and `identity-service.yml` sets `server.port` to 8086. Redis must have no
password, because the shared `RateLimitingService` connects without one.

The five POST operations are `otp`, `session`, `validate`, `refresh`, `logout`.
They use strict bounded UTF-8 JSON with version, configured audience and a fresh
nonce. Current validation checks scoped RS256 claims and the primary account's
active/entitlement version, existing LOGIN person session, blacklist and expiry.
No Food Delivery role is returned or interpreted as a Maps grant. Phone OTP is
reported only as OTP with `mfa=false`. Refresh cannot revive a mismatched version.

Maps sessions count towards the person's shared session limit. A Maps sign-in at
that limit gets `SESSION_LIMIT` and must name the session to end. Unlike a Food
Delivery login, it never replaces a same-device session automatically.

Each whole request, including upload, has a two-second deadline. Bounded workers
have no task queue; a timed-out operation retains its executing worker until it
actually exits. Dependency failures use fixed finite responses and safe metadata.
Writes may commit despite a lost response; no automatic mutation retry is added.
A lost response is counted (`maps.identity.responses.lost`, by operation) and logged at WARN
with its correlation id (`stage=response_lost`).
The exact contract, limits, selected D01–D10 decisions and remaining gates are in
[the integration handoff](https://github.com/parthu-reddy/RandomDocuments/pull/6).

Tests include strict persisted session authority, scoped token crypto/time/type,
key overlap/retirement, current service behavior, actual servlet mTLS/peer/slow
upload boundaries, profile guards and existing compatibility/architecture suites.
These are unit tests with mocked DB and cache ports. Integration tests against the Go
consumer or real stores belong in the Go repository, never in this one.

Build shared artifacts from their exact current source in a private Maven cache;
do not trust an unrelated installed SNAPSHOT or copy generated schemas to hide
that drift. The companion handoff supplies a reproducible JDK 21 runner and
first-failure/current artifact identities. No production secrets, admission,
privileged mode, deployment or recovery reopening is supplied here.

The Maps profile disables raw ORM SQL/bind/exception diagnostics because database
errors can echo caller-supplied unique values. Fixed Maps operation/stage/cause logs
remain available. Effective external logging configuration is still an operator gate.
