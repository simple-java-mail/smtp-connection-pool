# Implementation plan for Jakarta Mail provider support

This plan executes the locked local planning baseline in [PRODUCT-VISION.md](PRODUCT-VISION.md). Phases 0–5 are implemented in the current working tree; unchecked entries identify release work or explicit hardening still outstanding. The upstream work is tracked by [smtp-connection-pool #10](https://github.com/simple-java-mail/smtp-connection-pool/issues/10); review acceptance and release scope remain decisions recorded in that issue. The dependent Simple Java Mail work is tracked separately by [Simple Java Mail #698](https://github.com/bbottema/simple-java-mail/issues/698), with [Simple Java Mail #699](https://github.com/bbottema/simple-java-mail/issues/699) treated as a related physical-transport initiative rather than a dependency.

## Delivery target

- Proposed target release: `3.2.0` (SemVer minor, provided the implemented candidate is accepted in #10).
- Release contents: preserved core artifact, optional Jakarta Mail provider artifact, and optional Camel adapter artifact.
- Downstream order: publish and verify `smtp-connection-pool 3.2.0` first; update Simple Java Mail afterwards.
- Cross-initiative order: #699 may proceed independently; neither release waits for the other.
- Release date: deliberately unset. The `3.2.0` milestone receives a due date when implementation is scheduled, as required by [RELEASING.md](RELEASING.md).

## Locked architecture decision: hybrid delegate selection

- The facade registers only the `smtppool` protocol. It never spoofs, replaces, or globally overrides `smtp` or `smtps`.
- Plain Jakarta Mail and Spring select `smtppool`; the Camel adapter explicitly makes the same selection instead of relying on Camel's ordinary `smtp` lookup.
- Camel selection glue is isolated in the separate `smtp-connection-pool-camel` module; it contains no independent pooling, provider registry, or delegate-resolution implementation.
- Normal configuration selects the physical delegate by an explicit protocol name.
- Programmatic integration can supply a concrete Jakarta Mail `Provider` or a resolver/factory.
- Both branches converge before pool-key construction on a resolved provider identity and the same allocator/lifecycle implementation.
- Property names, type names, method names, the declarative default, and the Camel compatibility line are frozen by the implementation and module references. The hybrid model itself is no longer open.

## Coordination assumptions and requirements for Simple Java Mail #699

Assumptions:

- #699 has not selected NioSmtpClient, an Angus enhancement, a Jakarta Mail provider, or `CustomMailer` as its final implementation.
- This repository implements pooling and lifecycle only. It does not implement or verify PIPELINING, CHUNKING, SMTP command scheduling, or provider-specific submission receipts.
- A #699 implementation that wants to compose beneath this pool exposes the standard synchronous Jakarta Mail `Transport` contract. An adapter over an asynchronous client owns its async-to-sync boundary and event-loop safety.
- A delegate `Transport` represents one reusable physical connection and performs fresh capability negotiation on connection/reconnection. It does not hide a second physical-connection pool.

Requirements enforced by this plan:

- Delegate selection follows the locked hybrid model: an arbitrary explicit protocol for normal configuration, or a Jakarta Mail `Provider`/resolver for programmatic use; `smtp` and `smtps` are examples, not a closed list.
- Connection identity includes normalized delegate protocol and provider identity so two implementations registered for the same protocol cannot cross-borrow.
- Pool code, public leases, failure classification, examples, and tests do not import or require concrete Angus or NioSmtpClient types.
- A physical `Transport` is leased exclusively and used serially. A delegate's own thread-safety does not opt one SMTP connection into concurrent sends.
- Delivery outcome remains distinct from connection health. Standard exceptions and transport events reach callers unchanged, while the pool independently releases a demonstrably healthy connection or invalidates a broken/uncertain one.
- The pooled provider rejects itself as a delegate even when selected through an alias, a supplied `Provider`, or address mapping.
- #699 is covered with a deterministic non-Angus test provider in this repository; the real #699 implementation and its protocol-feature tests remain owned by Simple Java Mail.

Downstream boundary:

- If #699 supplies a Jakarta Mail provider, it is another physical delegate under the existing three paths, not a fourth path.
- If #699 uses `CustomMailer`, it owns a separate connection/session lifecycle outside this pool and must not be nested with Simple Java Mail's batch pool.
- Simple Java Mail #698 must expose only Jakarta Mail abstractions and must not make its public batch facade or receipt behavior depend on Angus. Any new transport-neutral receipt adapter required by #699 belongs to the Simple Java Mail plan, not this core pool.

## Phase 0: freeze the public contracts

- [x] Convert the repository to a Maven reactor with a parent/aggregator POM.
- [x] Move the current code into a core child while preserving the exact `org.simplejavamail:smtp-connection-pool` coordinate.
- [x] Add child modules named `smtp-connection-pool-jakarta-provider` and `smtp-connection-pool-camel`.
- [x] Keep all reactor artifacts on one version.
- [x] Confirm the Java baseline for core and provider; keep Camel's Java/Camel constraints isolated in its own module.
- [x] Document the JDK 21 build toolchain and update the inherited compiler, test, JaCoCo, SpotBugs, bundle, and Javadoc plugins so normal verification works without skipping static analysis.
- [x] Select Camel Mail `4.21.x` on Java 17 and record the compatibility matrix.
- [x] Freeze the public `SmtpTransportLease` contract.
- [x] Freeze the provider properties and APIs for hybrid protocol-or-`Provider`/resolver selection, pool sizing, claim timeout, expiry, ownership, and shutdown.
- [x] Define connection identity using the Session-owned manager, normalized delegate protocol/provider/host/effective port, username, and a private HMAC credential identity; diagnostics omit raw secrets.
- [x] Implement a transport-neutral Jakarta Mail failure/health policy without Angus-specific public or shared code.
- [x] Document the one-physical-connection-per-delegate and single-pooling-owner contract for custom providers.
- [x] Add a checksum-pinned japicmp comparison against the published `3.1.0` core and preserve its coordinate, packages, public API, automatic-module name, OSGi identity, Java 8 bytecode, and dependency ranges.
- [x] Rehearse the pinned `github-maven-deploy` orb's reactor-aware `versions:set`: root and every child advance from `3.1.0` to `3.2.0` together.

Exit criterion: the API/configuration review cannot alter the three product paths, their lifecycle ownership, arbitrary explicit real-provider lookup, or the single-pooling-owner boundary defined in the product vision.

## Phase 1: add the shared core lease

- [x] Introduce a public SMTP-specific lease around `PoolableObject<SessionTransport>`.
- [x] Expose the real `Session`, `SessionTransport`, and connected `Transport` through the lease.
- [x] Make success/release and failure/invalidate terminal and idempotent, with failed release falling back to invalidation.
- [x] Add direct and clustered claim methods that return the lease while retaining all existing generic claim APIs.
- [x] Implement one delegate-resolution pipeline for an explicit protocol, supplied `Provider`, or resolver, including protocols beyond `smtp`/`smtps`, without changing the direct API's Session-selected default.
- [x] Keep lease and allocator APIs expressed only in Jakarta Mail/core types; no physical-provider implementation appears in a public signature.
- [x] Preserve OAuth2 fixed-token and token-supplier behavior. The direct allocator resolves suppliers only on physical open/reconnect; the facade resolves one on each facade `connect` so credential rotation participates in pool identity.
- [x] Preserve interruption and claim-timeout semantics, including the thread interrupt flag at the facade boundary.

Tests:

- release returns a healthy transport for reuse;
- invalidation closes and removes it;
- terminal operations are idempotent and mutually exclusive;
- explicit delegate lookup never calls the pooled provider recursively;
- a non-`smtp`/non-Angus test provider can be allocated, reconnected, released, invalidated, and selected by both protocol and `Provider`;
- two supplied providers sharing a protocol remain isolated by connection identity;
- legacy direct API tests remain unchanged and pass;
- direct OAuth2 suppliers are resolved only on physical open/reconnect, while facade suppliers are resolved per facade connection for credential-safe keying.

## Phase 2: implement the Jakarta Mail provider

- [x] Register `smtppool` through Jakarta Mail provider metadata and the `ServiceLoader<Provider>` descriptor, with classpath tests for both discovery mechanisms.
- [ ] Claim full JPMS module-path support after `generic-object-pool` and `clustered-object-pool` publish valid automatic-module names; the first provider release documents this transitive limitation explicitly.
- [x] Implement `PooledTransport(Session, URLName)` as a generation-based lifecycle state machine that supports a fresh `connect()` after `close()`.
- [x] Translate all base Jakarta Mail `connect` routes through `protocolConnect(host, port, user, password)` into a lease claim without losing Spring-supplied values; failed claims leave no lease installed.
- [x] Preserve the interrupt flag when a blocking claim is interrupted and wrap it as a causally linked `MessagingException`.
- [x] Delegate `sendMessage` serially to the exclusively leased physical transport.
- [x] Centralize reusable-versus-invalid failure classification in the facade.
- [x] Make `isConnected`, reconnect-after-close, and `close` conform to generation-scoped Jakarta Mail expectations and serialize send/close/reconnect races.
- [x] Bridge delivered, not-delivered, and partially-delivered transport events on the facade without attaching borrower listeners to a reusable delegate.
- [x] Implement both hybrid selection branches with validation, recursion rejection, and provider identity resolved before key construction.
- [x] Implement provider-owned per-Session management plus per-Session/global shutdown. New claims are rejected immediately; graceful shutdown returns a `Future` that waits for leases, and forced shutdown invalidates them first. Callers choose their wait timeout.
- [x] Use a weak global registry, detach managers, clear retained credential material after shutdown, and cover Session collection with a lifecycle/GC test.
- [x] Allow a validated, explicitly injected manager for containers that own pool lifecycle.
- [x] Keep the provider implementation-neutral and test it with a deterministic non-Angus `Transport`; the application supplies the physical provider at runtime.
- [x] Pass the selected Session, effective connection inputs, and delegate-specific properties through without interpreting SMTP extension capabilities.
- [x] Keep connection health separate from delivery results, including reuse after a connected partial-recipient failure.

Tests:

- provider-metadata and `ServiceLoader` discovery through a real Jakarta Mail implementation;
- deterministic custom-protocol delegation with no concrete Angus type in provider code;
- declarative protocol and programmatic `Provider`/resolver selection, provider-identity isolation, and pooled-provider recursion rejection;
- explicit connection-input forwarding, credential rotation/isolation, and private/redacted connection identity;
- reconnect-after-close on one wrapper and reuse across wrappers;
- disconnected/unknown failure invalidation, failed idle reconnect cleanup, and healthy partial-delivery reuse;
- delivered, not-delivered, and partial listener delivery with no cross-borrower listener leakage;
- maximum-pool exclusivity, claim timeout, and interruption preservation;
- graceful shutdown with an active lease, new-claim rejection, explicit restart, forced test cleanup, and post-shutdown Session collection;
- Spring bulk and separate-send reuse plus Camel success/failure/shutdown coverage in their integration phases.

## Phase 3: verify plain Jakarta Mail and Spring

- [x] Add a plain Jakarta Mail integration fixture that selects `smtppool` and proves physical connection reuse.
- [x] Add a Spring `JavaMailSenderImpl` integration fixture configured with protocol `smtppool`.
- [x] Verify host, port, username, and password reach the physical provider; TLS, timeout, and arbitrary Session properties remain provider-owned and unmodified.
- [x] Run the plain Jakarta Mail fixture with a nonstandard delegate protocol to prove the integration does not hard-code `smtp`/`smtps`.
- [x] Verify Spring's multi-message `send(...)` and separate send calls both use the correct wrapper/lease lifecycle.
- [x] Add copy-paste-ready plain Jakarta Mail and Spring examples to the provider module documentation.

Exit criterion: applications can opt in by configuration and standard Jakarta Mail calls; no business-code pool lifecycle is required.

## Phase 4: add the Camel adapter

- [x] Spike Camel Mail `4.21.x` and record selection at `DefaultJavaMailSender.getTransport(Session)`.
- [x] Implement dedicated `smtppool:` and `smtppools:` Camel components/configuration that select the facade.
- [x] Keep normal `smtp` and `smtps` resolution untouched for unrelated Camel routes and Jakarta Mail Sessions.
- [x] Add a negative producer-route fixture proving ordinary Camel `smtp:` still uses Camel's normal component and Angus provider path.
- [x] Pass endpoint connection/authentication settings through to `PooledTransport` without duplicating pool logic.
- [x] Add real producer-route tests for success, physical reuse, failure/invalidation, and component-owned Session shutdown.
- [x] Document the Camel `4.21.x`/Java 17 matrix and minimal route examples.

Exit criterion: Camel reaches the same provider path through its adapter; the adapter contains selection glue only.

## Phase 5: documentation and examples

- [x] Keep [README.md](README.md) concise: basic direct usage, the three-path chooser, artifact status, and links to detailed material.
- [x] Keep [PRODUCT-VISION.md](PRODUCT-VISION.md) as the canonical ownership and architecture decision.
- [x] Add provider configuration reference and lifecycle/shutdown examples beside the provider module.
- [x] Document the physical-provider interoperability contract, the single-pooling-owner rule, and how a future #699 Jakarta provider composes without adding another usage path.
- [x] Add Camel-specific setup beside the Camel module.
- [x] Add Javadocs for every new public lease, provider manager, configuration, and shutdown API.
- [x] Reconcile the local `README.md` and `RELEASE.txt`; the final GitHub release body remains Phase 6 publication work.
- [x] Mark every `3.2.0` dependency and configuration example as implemented-but-unpublished.
- [x] Add a reactor-only, non-published `smtp-connection-pool-demo` project with a random-port Wiser SMTP server and executable `main` classes.
- [x] Demonstrate direct core usage first, Simple Java Mail as the path-1 reference consumer second, and plain Jakarta Mail/Spring/Camel as path-3 variants.
- [x] Prove three-message/one-physical-connection reuse, deterministic shutdown, and direct invalidation/replacement with smoke tests that execute the example code.
- [x] Document that the Simple Java Mail 9.2.0 demo uses `batch-module` only as the Mailer's internal optional support and imports no batch internals.
- [x] Defer the standalone path-2 demo until Simple Java Mail 10.0.0 publishes the supported #698 facade.

## Phase 6: release `smtp-connection-pool 3.2.0`

Follow [RELEASING.md](RELEASING.md). In summary:

- [ ] Create/reuse milestone `3.2.0` only after a planned release date is known; set that date and leave the milestone description empty.
- [ ] Assign issue #10 and every included PR/issue to the milestone.
- [x] Verify the local candidate reactor with tests, Javadocs, SpotBugs, binary compatibility, artifact packaging, and the documented examples; repeat on the eventual reviewed release commit.
- [x] Inspect the combined effective POM and perform a `skipPublishing` deploy-lifecycle rehearsal; the technical parent plus core, provider, Camel, and reactor-only demo outputs install locally with aligned metadata. Central staging remains publication-time verification because `skipPublishing` intentionally stages nothing.
- [ ] During publication rehearsal, inspect the generated Central bundle and prove that it contains the parent plus all three runtime artifacts, but no `smtp-connection-pool-demo` coordinate.
- [ ] Merge to `master`; let CircleCI own version selection, release commit, and tag creation.
- [ ] With explicit release approval, approve the **minor** deployment lane exactly once.
- [ ] Verify tag `3.2.0` and all three artifacts in Maven Central.
- [ ] Publish one self-contained GitHub release titled `v3.2.0` for tag `3.2.0`.
- [ ] Add a short availability/configuration comment to issue #10 and close it if fully delivered.
- [ ] Reconcile and close milestone `3.2.0`, replacing its due date with the actual publication date.

## Phase 7: update Simple Java Mail (downstream follow-up)

This phase is outside the upstream `3.2.0` release definition of done. It is separate downstream work owned by [Simple Java Mail #698](https://github.com/bbottema/simple-java-mail/issues/698), targeted at Simple Java Mail 10.0.0. That release may follow months after the pool release and does not block the upstream demo or release. [Simple Java Mail #699](https://github.com/bbottema/simple-java-mail/issues/699) is a coordination input: #698 must leave a transport-neutral seam, but it does not implement #699.

- [ ] Upgrade the core pool dependency only after `3.2.0` is available from Maven Central.
- [ ] Keep Simple Java Mail on the direct-integration path.
- [ ] Make the new public batch callback facade and the existing reflective Mailer adapter delegate to one internal engine.
- [ ] Keep the core lease internal to the facade: run callbacks with the selected Session/Transport, release on success, and invalidate on failure.
- [ ] Keep every public batch callback and result expressed in Jakarta Mail/Simple Java Mail abstractions; do not expose Angus or a prospective #699 client type.
- [ ] Verify the direct and batch engines work with a deterministic non-Angus `Transport` provider selected by the registered Session.
- [ ] Separate Simple Java Mail submission receipts from Angus-only `SMTPTransport` inspection so a future #699 Jakarta provider can supply equivalent response and partial-recipient information through a transport-neutral adapter/capability.
- [ ] Publish batch-specific configuration that does not expose `OperationalConfig` or require a `Mailer`.
- [ ] Support cluster-selected and session-sticky operations, executor ownership/injection, synchronous callbacks, asynchronous submission, and deterministic shutdown.
- [ ] Resolve OAuth2 configuration from the actually selected registered Session so clustered selection cannot copy a token supplier from the wrong caller-supplied Session.
- [ ] Add a standalone compile/API test that depends only on `batch-module` and imports no `internal` packages.
- [ ] Preserve the `org.simplejavamail.batch` automatic module name and Java 8 compatibility.
- [ ] Update batch-module Javadocs and Simple Java Mail supporting-library release notes.
- [ ] After Simple Java Mail 10.0.0 is published, add the standalone path-2 example to this repository's non-published demo module and link the website documentation to it.
- [ ] Add `simplejavamail.org/src/pages/batch-module-jakarta-mail.hbs` at `/batch-module-jakarta-mail.html`, register it in the site manifest as a child of `/modules.html`, and link it from the modules and existing batch/pooling documentation.
- [ ] Generalize the website's active-navigation handling for `breadcrumbParent`; do not hand-edit generated navigation or sitemap output.
- [ ] Keep the direct batch path separate from `smtppool`; do not stack one pool over the other.
- [ ] If #699 chooses `CustomMailer`, document and test that it bypasses the batch pool and owns its own reusable-session lifecycle; if it chooses a Jakarta provider, test it as a physical delegate instead.
- [ ] Run the website checks defined in its own release checklist.
- [ ] Release Simple Java Mail through its independently authorized workflow.

## Compatibility commitments

- Existing core consumers keep the same Maven coordinate and direct API.
- The core artifact remains binary-compatible with `3.1.0` unless the release is explicitly reclassified as SemVer major.
- The new provider and Camel dependencies are opt-in.
- Selecting `smtppool` never changes the meaning of `smtp` or `smtps` globally.
- Declarative protocol selection and programmatic `Provider`/resolver selection reach the same allocator and lifecycle semantics.
- The provider's pools are not silently shared with caller-owned direct pools.
- Physical delegates are implementation-neutral, explicitly selected, and never silently double-pooled.
- Core/provider Java compatibility is not raised merely to satisfy Camel.
- Simple Java Mail users retain current high-level behavior; the new batch facade is additive.

## Upstream `3.2.0` definition of done

The upstream release is complete only when:

- direct, plain Jakarta Mail, Spring, and Camel integration tests demonstrate connection reuse and correct failure disposal;
- executable real-server demos demonstrate direct, high-level Simple Java Mail, plain Jakarta Mail, Spring, and Camel usage, while the unsupported pre-10.0.0 standalone batch path remains absent;
- an alternate non-Angus Jakarta `Transport` fixture proves arbitrary delegate selection, provider isolation, standard exception/event propagation, and absence of nested pooling assumptions;
- the recursion, credential-isolation, shutdown, and memory-retention risks have explicit tests;
- all published public APIs and properties are documented with available-version markers;
- the parent and all three runtime artifacts are present in Maven Central under one version, while the reactor-only demo is absent;
- GitHub issue, milestone, tag, release, and release-note records agree; and
- Simple Java Mail #698 remains linked and blocked until the upstream release is actually available.

The overall cross-repository program is complete only after Simple Java Mail #698 delivers its public batch facade, regression tests, release notes, website page, submodule update, and independently authorized Simple Java Mail release.
