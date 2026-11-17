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
The exact contract, limits, selected D01–D10 decisions and remaining gates are in
[the integration handoff](https://github.com/parthu-reddy/RandomDocuments/pull/6).

Tests include strict persisted session authority, scoped token crypto/time/type,
key overlap/retirement, current service behavior, actual servlet mTLS/peer/slow
upload boundaries, profile guards and existing compatibility/architecture suites.
`MAPS_GO_CONTRACT_PROBE` may point to the owned compiled Maps identity test
executable; the HTTPS transport test invokes its real Go decoder against active
and disabled current Java decisions. DB/cache ports in that fixture are isolated
mocks. It is not complete Java+Redis+DB+product or recovery/load acceptance.

Build shared artifacts from their exact current source in a private Maven cache;
do not trust an unrelated installed SNAPSHOT or copy generated schemas to hide
that drift. The companion handoff supplies a reproducible JDK 21 runner and
first-failure/current artifact identities. No production secrets, admission,
privileged mode, deployment or recovery reopening is supplied here.

## Native authority continuation

The owning TLS test now has an explicit disposable PostgreSQL/Redis variant using
production Flyway/JPA/transaction/Redis code and a restricted non-superuser runtime
role. It checks concurrent OTP consumption/replay, passive TTL/activity non-renewal,
current disable/version/revocation, refresh/logout ordering, corrupt/unavailable
authority and denial of schema/migration writes. The real Go relay joins this
fixture for OTP/login/current checks, cross-adapter persistence, refresh and confirmed
Java logout. Its gateway request boundary is the existing in-process test fixture;
notifications and unused entitlement/audit ports remain test doubles.

The companion PR supplies `tools/native_authority.py` with pinned capped ARM64
images, protected disposable configuration, private logs, exact cleanup and optional
`--clean-verify`. A complete clean run passed 268 tests with no failures/errors/skips.
Default invocations explicitly skip the native case unless the owned native config
is supplied. See the current source-bound evidence in
[the review package](https://github.com/parthu-reddy/RandomDocuments/pull/6).

The Maps profile disables raw ORM SQL/bind/exception diagnostics because database
errors can echo caller-supplied unique values. Fixed Maps operation/stage/cause logs
remain available. Effective external logging configuration is still an operator gate.

## Credential time, key rotation and refusal diagnostics

The verifier applies zero clock skew. Issue time is inclusive: `iat` equal to
the current second is current, and any later `iat` is premature. Expiry is
exclusive: `exp` equal to the current second is expired. Credentials are RS256
only, carry exactly `alg`, `typ` and `kid` in the header, and are verified only
against the configured `verification-key`/`verification-keys` set. A header that
names its own key source (`jku`, `x5u`, `jwk`, `x5c`) or any other field is
refused before key lookup; nothing is fetched.

Rotation uses three configuration states of the same instance:

1. Old only: `signing-key-id` names the old key, verified by the old key.
2. Overlap: `signing-key-id` and the private key name the new key, and
   `verification-keys` retains the old public key. Existing sessions keep their
   old-key credentials until they refresh, while new credentials use the new key.
3. Revoked: the old key is removed from `verification-keys`. Its credentials are
   refused at key lookup on the next validation, refresh or logout check.

Every change takes effect when the instance restarts with the new configuration.
The overlap must last at least the 15-minute credential lifetime for old
credentials to expire or refresh naturally. Removing the old key ends the
overlap early for emergency revocation.

Each refusal returns the same `INACTIVE` result. The service logs only
`maps_credential operation=verify stage=<check> cause=rejected correlation=<id>`.
The check is one of `format`, `header`, `claim_set`, `algorithm`, `type`,
`key_id`, `signature`, `issuer`, `audience`, `subject`, `premature`, `expired`
or `lifetime`. The correlation is the adapter's opaque request ID. Token bytes,
claim values and subjects are never logged.
