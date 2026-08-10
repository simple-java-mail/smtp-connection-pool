[![APACHE v2 License](https://img.shields.io/badge/license-apachev2-blue.svg?style=flat)](LICENSE-2.0.txt)
[![Latest Release](https://img.shields.io/maven-central/v/org.simplejavamail/smtp-connection-pool.svg?style=flat)](https://central.sonatype.com/artifact/org.simplejavamail/smtp-connection-pool)
[![Javadocs](https://www.javadoc.io/badge/org.simplejavamail/smtp-connection-pool.svg)](https://www.javadoc.io/doc/org.simplejavamail/smtp-connection-pool)
[![Codacy](https://img.shields.io/codacy/grade/dd513d3737cd4f82bfbabe01558c4878.svg?style=flat)](https://app.codacy.com/gh/simple-java-mail/smtp-connection-pool)

# smtp-connection-pool

`smtp-connection-pool` keeps Jakarta Mail `Transport` connections open and leases each physical connection exclusively to one caller at a time. It supports lazy or eager allocation, bounded waiting, expiration, and clusters of SMTP servers.

It does not build messages or decide how SMTP works. The selected Jakarta Mail provider still owns authentication, TLS, EHLO, PIPELINING, CHUNKING, response parsing, and the actual send.

> The provider and Camel modules documented below are implemented for the proposed `3.2.0` release but are not part of the currently published `3.1.0` release. Publication still requires the release process in [RELEASING.md](RELEASING.md).

## Start here: executable demos

The best introduction is the non-published [demo project](smtp-connection-pool-demo/README.md). Every example runs against a real dummy SMTP server on a random port and asserts message delivery, physical connection reuse, and clean shutdown.

| Runnable example | Integration shown | Verified result |
| --- | --- | --- |
| [DirectPoolDemo](smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/DirectPoolDemo.java) | Direct core leases, release, forced failure, invalidation, and recovery | 3 messages over 1 connection; then replacement after a dropped connection |
| [SimpleJavaMailDemo](smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/SimpleJavaMailDemo.java) | Simple Java Mail as the higher-level path-1 consumer | 3 messages over 1 connection |
| [JakartaMailDemo](smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/JakartaMailDemo.java) | Plain Jakarta Mail with `smtppool` | 3 messages over 1 connection |
| [SpringDemo](smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/SpringDemo.java) | Spring `JavaMailSenderImpl` with `smtppool` | 3 messages over 1 connection |
| [CamelDemo](smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/CamelDemo.java) | Camel with the separate `smtppool:` adapter | 3 messages over 1 connection |

Run the complete executable suite with JDK 21:

```shell
mvn -pl smtp-connection-pool-demo -am test
```

Or run `DemoLauncher` or any individual demo directly from IntelliJ. The project is part of the reactor and CI, but is deliberately excluded from Maven Central. A standalone `batch-module` demo will join it after Simple Java Mail 10.0.0 publishes the supported path-2 API from [#698](https://github.com/bbottema/simple-java-mail/issues/698).

## Choose one of three usage paths

These are usage paths, not three abstraction levels inside this repository.

| Path | Choose it when | Lifecycle owner | Status |
| --- | --- | --- | --- |
| **1. Integrate the core pool directly** | Your application or a higher-level library needs clustering, explicit leases, and complete failure/shutdown control. Simple Java Mail itself belongs here. | Your integration | Available; explicit `SmtpTransportLease` is new in the next release |
| **2. Use Simple Java Mail's `batch-module` directly** | You create Jakarta Mail messages yourself but want Simple Java Mail's asynchronous batch engine and a safe callback facade without adopting `EmailBuilder` and `Mailer`. | The batch facade | Planned for Simple Java Mail 10.0.0 in [#698](https://github.com/bbottema/simple-java-mail/issues/698) |
| **3. Use the standard Jakarta Mail facade** | Plain Jakarta Mail, Spring, or Camel already obtains and closes `Transport` instances. | `PooledTransport` | Implemented here for [#10](https://github.com/simple-java-mail/smtp-connection-pool/issues/10); not published yet |

Simple Java Mail stays on path 1 internally. Path 2 is a narrower public facade over part of its batch engine. Path 3 makes pooling look like a normal Jakarta Mail transport protocol.

The architecture, ownership rules, and five flow diagrams are in [PRODUCT-VISION.md](PRODUCT-VISION.md).

## Artifacts

All three runtime artifacts release together at one version. Maven Central also receives the `smtp-connection-pool-parent` reactor POM because the child POMs inherit their aligned metadata; it is build metadata, not a fourth runtime dependency.

| Artifact | Purpose | Java |
| --- | --- | --- |
| `org.simplejavamail:smtp-connection-pool` | Direct and clustered pool APIs plus `SmtpTransportLease` | 8+ |
| `org.simplejavamail:smtp-connection-pool-jakarta-provider` | Discoverable `smtppool` Jakarta Mail provider and Session-scoped lifecycle registry | 8+ |
| `org.simplejavamail:smtp-connection-pool-camel` | Optional Camel Mail selection adapter; pooling remains in the provider module | 17+ (Camel 4.21) |

The reactor also contains `smtp-connection-pool-demo`. It is a build-tested example project, not a fourth runtime artifact, and is explicitly excluded from Maven Central.

The provider artifact contains the Jakarta Mail facade, not a physical SMTP implementation. Applications supply Angus Mail or another compatible `Transport` provider. A delegate must represent one reusable physical connection; do not put a hidden second connection pool beneath this pool.

## Path 1: direct core API

```xml
<dependency>
    <groupId>org.simplejavamail</groupId>
    <artifactId>smtp-connection-pool</artifactId>
    <version>3.2.0</version><!-- once published -->
</dependency>
```

Create a Session normally, then claim one exclusive lease. Closing an active lease releases it; invalidate it first when a failure makes the connection uncertain.

```java
SmtpConnectionPool pool = new SmtpConnectionPool(new SmtpClusterConfig<Session>());

try (SmtpTransportLease lease = pool.claimTransport(session)) {
    try {
        Session selectedSession = lease.getSession();
        Transport transport = lease.getTransport();
        transport.sendMessage(message, message.getAllRecipients());
    } catch (MessagingException | RuntimeException failure) {
        lease.invalidate();
        throw failure;
    }
}

// Application shutdown: wait until active leases return and connections close.
pool.shutDown().get();
```

`claimTransport` can block and throws `InterruptedException`. Preserve the thread's interruption policy. The default pool is lazy, has a maximum of four physical connections per Session, and makes an available transport eligible for expiration ten seconds after its last claim.

For partial-recipient failures, an advanced integration may release rather than invalidate only when the delegate is demonstrably still connected. Unknown failures should be treated conservatively.

### Clustered pools

```java
SmtpClusterConfig<UUID> config = new SmtpClusterConfig<>();
config.getConfigBuilder()
        .defaultCorePoolSize(0)
        .defaultMaxPoolSize(10)
        .loadBalancingStrategy(new RandomAccessLoadBalancing<>())
        .claimTimeout(new Timeout(30, SECONDS));

SmtpConnectionPoolClustered<UUID> pool = new SmtpConnectionPoolClustered<>(config);

ResourceClusterAndPoolKey<UUID, Session> server =
        new ResourceClusterAndPoolKey<>(clusterId, session);
try (SmtpTransportLease lease = pool.claimTransport(server)) {
    lease.getTransport().sendMessage(message, message.getAllRecipients());
}
```

Clusters and pools are created on demand. Use `registerResourceCluster` or `registerResourcePool` when a cluster or server needs different sizing, expiration, or load-balancing behavior.

### OAuth2 tokens

The direct allocator resolves a thread-safe token supplier only when it opens or reconnects a physical transport:

```java
session.getProperties().put(
        SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY,
        (Supplier<String>) tokenProvider::getAccessToken);
```

The supplier owns caching and refresh. A fixed token remains available through `OAUTH2_TOKEN_PROPERTY` for short-lived use.

## Path 2: Simple Java Mail `batch-module`

This path is deliberately delivered in Simple Java Mail, not in this repository. [Simple Java Mail #698](https://github.com/bbottema/simple-java-mail/issues/698) plans a small public callback facade for Simple Java Mail 10.0.0, for applications that already create Jakarta Mail messages but want the batch engine's asynchronous execution, clustering, and safe release/invalidate handling without adopting the full `EmailBuilder`/`Mailer` API.

The batch facade will use the core lease internally and remain separate from `smtppool`; stacking both pooling owners would be incorrect. Until Simple Java Mail 10.0.0 ships that facade, `batch-module` remains an internal implementation module rather than a supported standalone API. The executable demo project therefore does not demonstrate this path yet.

## Path 3: standard Jakarta Mail facade

Add the provider plus a physical Jakarta Mail implementation:

```xml
<dependency>
    <groupId>org.simplejavamail</groupId>
    <artifactId>smtp-connection-pool-jakarta-provider</artifactId>
    <version>3.2.0</version><!-- once published -->
</dependency>
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
    <version>2.0.5</version>
</dependency>
```

The facade registers only `smtppool`; it never replaces or pretends to be `smtp` or `smtps`.

```java
Properties properties = new Properties();
properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "smtp"); // default
Session session = Session.getInstance(properties);

Transport transport = session.getTransport("smtppool");
transport.connect(host, port, username, password); // claims and connects if needed
try {
    transport.sendMessage(message, message.getAllRecipients());
} finally {
    transport.close(); // releases a healthy lease; invalidates an unhealthy one
}

Future<?> shutdown = SmtpPoolRegistry.shutdown(session); // graceful
shutdown.get();
```

Graceful shutdown stops new claims and waits for active leases. If a bounded wait expires, `shutdownNow(session)` invalidates active leases and returns the same completion handle, which completes only after physical `Transport.close()` calls finish. A Session can be reused only after that shutdown completes and `SmtpPoolRegistry.restart(session)` is called explicitly.

When credentials or OAuth tokens rotate for the same endpoint, a new credential-isolated pool becomes current and the superseded pool drains. Its retained credential material is cleared as soon as its last active lease finishes; inactive credential generations do not accumulate.

Spring uses the same provider by configuring `JavaMailSenderImpl` with protocol `smtppool`. Camel uses the separate adapter and `smtppool:` or `smtppools:` endpoint schemes:

```xml
<dependency>
    <groupId>org.simplejavamail</groupId>
    <artifactId>smtp-connection-pool-camel</artifactId>
    <version>3.2.0</version><!-- once published -->
</dependency>
```

```java
to("smtppool://smtp.example.com:587"
        + "?username=user&password=secret&to=recipient@example.com");
```

See the [provider reference](smtp-connection-pool-jakarta-provider/README.md) and [Camel reference](smtp-connection-pool-camel/README.md) for configuration, programmatic delegate selection, credential rotation, and shutdown semantics.

## Build and verification

Build the reactor with JDK 21 and Maven. Core and provider bytecode still target Java 8; the Camel and demo modules target Java 17.

```shell
mvn clean verify
```

Verification runs the core/provider/Camel tests, the real-server demo smoke tests, SpotBugs, Javadocs, and a checksum-pinned japicmp comparison with the published `3.1.0` core. CircleCI also compiles and tests the core plus Jakarta provider on an actual JDK 8; its JDK 21 release lane advances the parent and all children as one reactor while excluding the demo from publication.

## Related custom transports

[Simple Java Mail #699](https://github.com/bbottema/simple-java-mail/issues/699) may produce a faster physical transport. If it implements Jakarta Mail's synchronous `Transport` contract with one reusable physical session per instance, it can be selected beneath these paths just like Angus. If it instead uses Simple Java Mail's `CustomMailer`, it owns its own lifecycle and must not be stacked on this pool.

## Release notes

Next release (proposed `3.2.0`)

- [#10](https://github.com/simple-java-mail/smtp-connection-pool/issues/10): add the explicit lease API, optional `smtppool` Jakarta Mail provider, and separate Camel adapter while preserving the existing core coordinate and direct API. Harden credential rotation and graceful/forced shutdown using `generic-object-pool 2.4.1` and `clustered-object-pool 4.0.2`.

`3.1.0` (7 August 2026)

- [#9](https://github.com/simple-java-mail/smtp-connection-pool/issues/9): resolve a current OAuth2 token whenever a physical SMTP transport is opened or reconnected.

`3.0.1` (6 July 2026)

- [#8](https://github.com/simple-java-mail/smtp-connection-pool/issues/8): update `clustered-object-pool` to 4.0.1 so clustered SMTP pools can use cluster-specific defaults.
