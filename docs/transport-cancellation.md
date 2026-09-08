# Physical transport cancellation boundary

The pool can stop a pending claim without owning an SMTP engine. Interrupting physical SMTP work is a different
operation: the physical provider must own a way to abort its connection without waiting behind the operation being stopped.

## Angus Mail 2.0.5

The released Angus implementation synchronizes `protocolConnect`, `sendMessage`, `isConnected` and `close` on the
transport. Calling `close()` on another thread therefore waits behind a blocked send or connection setup. Cancelling
a wrapper future does not stop SMTP or erase an accepted final reply. The loopback tests in
[`AngusCancellationBoundaryTest`](../smtp-connection-pool/src/test/java/org/simplejavamail/smtpconnectionpool/AngusCancellationBoundaryTest.java)
retain this boundary at MAIL, RCPT, DATA and the final reply.

Angus does offer `SMTPTransport.connect(Socket)`. The local greeting probe confirms that closing an explicitly owned
socket can unblock that narrow path. It does not give this library a socket for an arbitrary transport obtained from a
caller-owned Session, cover reconnect, or replace the provider's DNS, authentication and proxy setup.

Angus socket factories are selected through Session properties. STARTTLS replaces the provider's socket through
`SocketFetcher.startTLS`; implicit TLS and HTTP/SOCKS proxy paths also have provider-owned setup. Capturing one old
socket is not a complete abort capability. Installing request-scoped factories on a shared Session would change other
borrowers' behavior, so this library does not do that.

A custom provider could own all these socket transitions, but doing so here would introduce an SMTP-provider maintenance
surface merely to implement cancellation. This release neither replaces Angus implicitly nor forks its engine. No upstream
PR is required for the independently useful acquisition API.

## Explicit provider support

`TransportCancellationSupport` is the opt-in integration point for a provider that can meet the physical contract. Its
factory runs before first connect and produces a latched, non-blocking action for that exact transport, including future
connects and replacement sockets. Return `Optional.empty()` when that cannot be guaranteed. The provider must document
its actual DNS, TLS, authentication, proxy, readiness and send boundaries; this SPI alone certifies none of them.

The pool registers that action only during the requesting acquisition. Successful handoff detaches it. Each returned lease
then has its own optional control: the active-to-invalidated transition consumes that lease generation before the physical
action runs. A delayed request from a released lease is ineffective. An effective abort can never return the transport as healthy.

The capable fixture tests exercise operation exit, replacement-connection state, stale handles, release races and disposal
failure independently of Angus. They are a capability-contract fixture, not a replacement SMTP engine or a claim that Angus
supports those phases. For default Angus plain SMTP, STARTTLS, implicit TLS and proxy use, physical abort remains unsupported.

Always observe both the actual operation's exit and required disposal. Keep an accepted SMTP result if one was returned;
the pool cannot determine retry safety after a missing final reply and does not manufacture delivery receipts.
