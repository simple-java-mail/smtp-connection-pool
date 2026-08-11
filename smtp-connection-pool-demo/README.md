# Start here: executable demos

This demo module is the runnable introduction to `smtp-connection-pool`. It is built and tested with the rest of the repository, but it is deliberately excluded from Maven Central and must not be added as an application dependency.

The verification build also compiles a JPMS consumer requiring the stable generic pool, clustered pool,
SMTP pool, Jakarta provider, Camel adapter, and Simple Java Mail batch module names.

Every scenario starts its own Wiser/SubEtha dummy SMTP server on a random loopback port. The smoke tests verify both message delivery and physical connection behavior, so the examples demonstrate actual reuse rather than only showing configuration.

## Run the complete suite

Use JDK 21 to build the complete project:

```shell
mvn -pl smtp-connection-pool-demo -am test
```

In IntelliJ, run `org.simplejavamail.smtpconnectionpool.demo.DemoLauncher`. Every individual demo class also has a `main` method.

## Demonstrated paths

The examples follow the product order from the main README.

| Order | Class | What it proves | Expected dummy-server result |
| --- | --- | --- | --- |
| 1 | `DirectPoolDemo` | Direct lease/release, explicit shutdown, and invalidate/replace after a forced disconnect | Reuse: 3 messages / 1 connection. Recovery: 2 healthy messages / 2 connections. |
| 2 | `SimpleJavaMailDemo` | Simple Java Mail as a higher-level library built directly on the pool | 3 messages / 1 connection |
| 3 | `BatchModuleDemo` | Simple Java Mail's standalone batch callback API over caller-created Jakarta Mail messages | 3 messages / 1 connection |
| 4 | `JakartaMailDemo` | Plain Jakarta Mail selecting the `smtppool` provider | 3 messages / 1 connection |
| 5 | `SpringDemo` | Spring `JavaMailSenderImpl` selecting `smtppool` | 3 messages / 1 connection |
| 6 | `CamelDemo` | Camel selecting the separate `smtppool:` adapter | 3 messages / 1 connection |

Every scenario also verifies that the responsible application or framework leaves zero physical connections open after shutdown.

## Standalone batch-module demo

`BatchModuleDemo` uses the public `BatchTransportExecutor` API released in Simple Java Mail 9.3.0. It registers a caller-owned Jakarta Mail `Session`, creates each `MimeMessage` with the Session actually selected by the cluster, and sends it inside the callback-scoped `Transport`. Normal callback completion releases the physical connection for reuse, and closing the executor shuts down the pool.

This remains a different product path from `SimpleJavaMailDemo`: the standalone example uses neither `EmailBuilder` nor `Mailer`, and imports no `internal` package. The facade owns orchestration while `smtp-connection-pool` remains the only physical connection pool.
