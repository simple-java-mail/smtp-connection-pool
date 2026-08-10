# Jakarta Mail `smtppool` provider

This module adapts the core connection pool to Jakarta Mail's ordinary `Transport` lifecycle. It is available since `4.0.0`.

It registers only the protocol `smtppool`. A real transport is selected underneath it; `smtp` and `smtps` are never replaced globally.

## See it run

Start with the real-server [plain Jakarta Mail demo](../smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/JakartaMailDemo.java) and [Spring demo](../smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/SpringDemo.java). Both send three messages over one physical SMTP connection and verify that explicit Session shutdown closes it. The [complete demo guide](../smtp-connection-pool-demo/README.md) explains how to run them.

## Setup

```xml
<dependency>
    <groupId>org.simplejavamail</groupId>
    <artifactId>smtp-connection-pool-jakarta-provider</artifactId>
    <version>4.0.0</version>
</dependency>
```

Also supply a physical Jakarta Mail implementation such as Angus Mail. This module deliberately depends only on the Jakarta Mail API and the core pool.

## Plain Jakarta Mail

```java
Properties properties = new Properties();
properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "smtp");
Session session = Session.getInstance(properties);

Transport transport = session.getTransport(SmtpPoolProperties.PROTOCOL);
transport.connect(host, port, username, password);
try {
    transport.sendMessage(message, message.getAllRecipients());
} finally {
    transport.close();
}

SmtpPoolRegistry.shutdown(session).get();
```

Each facade `Transport` owns at most one lease generation. `connect` claims it, `sendMessage` delegates serially, and `close` releases it only when the physical delegate remains healthy. The same facade object may connect again after close and receives a fresh generation.

## Spring

```java
Properties properties = new Properties();
properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "smtp");

JavaMailSenderImpl sender = new JavaMailSenderImpl();
sender.setJavaMailProperties(properties);
sender.setProtocol(SmtpPoolProperties.PROTOCOL);
sender.setHost(host);
sender.setPort(port);
sender.setUsername(username);
sender.setPassword(password);

sender.send(mimeMessage);
```

Spring continues to supply host, port, username, password, message, and recipients through its normal calls. Only transport selection changes.

## Hybrid physical-provider selection

Declarative configuration names a protocol. It defaults to `smtp`:

```java
properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "smtps");
```

Programmatic integrations can supply a concrete provider or resolver without exposing its implementation through the lease API:

```java
SmtpPoolProperties.setDelegateProvider(session, provider);

SmtpPoolProperties.setDelegateProviderResolver(session,
        (mailSession, requestedProtocol) -> chooseProvider(mailSession, requestedProtocol));
```

The resolver branch takes precedence over a configured `Provider`, which takes precedence over `Session.getProvider(delegateProtocol)`. All branches reject the pooled provider itself, including aliases, before a pool is created.

## Configuration reference

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `mail.smtppool.delegate.protocol` | String | `smtp` | Protocol used to resolve the physical provider |
| `mail.smtppool.delegate.provider` | `jakarta.mail.Provider` object | none | Programmatically selected physical provider |
| `mail.smtppool.delegate.provider-resolver` | `SmtpDelegateProviderResolver` object | none | Programmatic provider resolver |
| `mail.smtppool.credential.identity` | object/string | none | Extra private credential generation identity for dynamic authenticator rotation |
| `mail.smtppool.pool.core-size` | integer | `0` | Eager physical connections per identity |
| `mail.smtppool.pool.max-size` | integer | `4` | Maximum physical connections per identity |
| `mail.smtppool.pool.claim-timeout-millis` | long | `30000` | Maximum wait for an exclusive lease |
| `mail.smtppool.pool.expiration-millis` | long | `10000` | Eligibility threshold measured since an available transport's last claim |

Provider and resolver values are objects placed with `Properties.put`, not strings loaded from a properties file.

Endpoint identity includes the Session-scoped manager, normalized delegate protocol and resolved provider metadata, host, effective port, username, and an HMAC credential fingerprint. Raw passwords/tokens are excluded from equality and `toString` and retained only while needed for reconnect/allocation. When credentials rotate, the new generation becomes current immediately; the superseded pool accepts no new claims, drains existing leases, and then clears its credential material and retained pool record. Remaining material is cleared on Session-pool shutdown.

Passwords supplied by Jakarta Mail and saved Session authentication are fingerprinted automatically. The core OAuth token supplier is resolved for each facade `connect` so token rotation creates a separate identity. If a custom authenticator rotates credentials without exposing the effective password to the facade, update `mail.smtppool.credential.identity` with its generation/version.

## Delivery events and connection health

Listeners registered on `PooledTransport` receive delivered, not-delivered, and partially-delivered events from that wrapper; they are never attached to the reusable delegate and cannot leak to a later borrower.

A connected delegate may remain reusable after `SendFailedException`, including partial-recipient delivery. Unknown `MessagingException`, runtime failure, or a disconnected delegate invalidates the physical transport conservatively.

## Shutdown

```java
SmtpPoolRegistry.shutdown(session).get();     // graceful: reject claims, wait for active leases
SmtpPoolRegistry.shutdownNow(session).get();  // force: invalidate active leases first
SmtpPoolRegistry.shutdownAll();               // starts all registry-owned shutdowns; does not wait
```

Shutdown marks a Session closed to new pool claims. A later `shutdownNow(session)` escalates an in-progress graceful shutdown, invalidates active leases, and returns the same `Future`. That handle completes only after allocator deallocation—including each physical `Transport.close()`—has finished. The manager remains registered during cleanup so escalation cannot create or target a replacement lifecycle.

If a deliberately reconfigured Session must be used again, wait for shutdown completion and then call `SmtpPoolRegistry.restart(session)` explicitly. Installing a shutting-down manager or restarting while one is still registered is rejected.

`PooledTransport.close()` ends one lease generation; it does not shut down the Session's pool. Applications and containers must invoke a registry shutdown hook.

An injected manager is container-owned and is therefore not included in `shutdownAll()`; its owner calls `manager.shutdown()` or `manager.shutdownNow()` directly.

## Physical-provider contract

- One delegate `Transport` instance represents one reusable physical SMTP connection.
- A connection is leased exclusively and used serially, never concurrently.
- The delegate owns SMTP negotiation, TLS, extensions, response parsing, and any async-to-sync bridge.
- Do not hide a second physical connection pool in the delegate.
- Use standard `MessagingException`, `SendFailedException`, and Jakarta Mail listener semantics; do not require Angus-specific types.

The module publishes both Jakarta Mail provider metadata and the standard `ServiceLoader` descriptor. Full JPMS module-path execution remains blocked by invalid legacy automatic-module names in the current `generic-object-pool` and `clustered-object-pool` releases; classpath discovery is covered by integration tests.
