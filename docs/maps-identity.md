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

The five POST operations are `otp`, `session`, `validate`, `refresh`, `logout`.
They use strict bounded UTF-8 JSON with version, configured audience and a fresh
nonce. Current validation checks scoped RS256 claims and the primary account's
active/entitlement version, existing LOGIN person session, blacklist and expiry.
No Food Delivery role is returned or interpreted as a Maps grant. Phone OTP is
reported only as OTP with `mfa=false`. Refresh cannot revive a mismatched version.

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
