# Start here: executable demos

This reactor-only module is the canonical runnable introduction to `smtp-connection-pool`. It is compiled and tested with the repository, but it is deliberately excluded from Maven Central and must not be added as an application dependency.

Every scenario starts its own Wiser/SubEtha dummy SMTP server on a random loopback port. The smoke tests verify both message delivery and physical connection behavior, so the examples demonstrate actual reuse rather than only showing configuration.

## Run the complete suite

Use JDK 21 for the reactor build:

```shell
mvn -pl smtp-connection-pool-demo -am test
```

In IntelliJ, run `org.simplejavamail.smtpconnectionpool.demo.DemoLauncher`. Every individual demo class also has a `main` method.

## Demonstrated paths

The examples follow the product order from the main README.

| Order | Class | What it proves | Expected dummy-server result |
| --- | --- | --- | --- |
| 1 | `DirectPoolDemo` | Direct lease/release, explicit shutdown, and invalidate/replace after a forced disconnect | Reuse: 3 messages / 1 connection. Recovery: 2 healthy messages / 2 connections. |
| 2 | `SimpleJavaMailDemo` | Simple Java Mail as the higher-level reference consumer of path 1 | 3 messages / 1 connection |
| 3 | `JakartaMailDemo` | Plain Jakarta Mail selecting the `smtppool` provider | 3 messages / 1 connection |
| 4 | `SpringDemo` | Spring `JavaMailSenderImpl` selecting `smtppool` | 3 messages / 1 connection |
| 5 | `CamelDemo` | Camel selecting the separate `smtppool:` adapter | 3 messages / 1 connection |

Every scenario also verifies that its lifecycle owner leaves zero physical connections open after shutdown.

## Why there is no standalone batch-module demo yet

Simple Java Mail 9.2.0 uses `batch-module` internally when its high-level `Mailer` enables connection pooling, so that optional JAR is present here only to make `SimpleJavaMailDemo` realistic. The demo never imports or calls an `internal` batch type.

Using `batch-module` directly is a different product path. Its supported public facade is planned in [Simple Java Mail #698](https://github.com/bbottema/simple-java-mail/issues/698) for Simple Java Mail 10.0.0. A standalone batch demo belongs in this module after that release is available; until then, adding one would teach an unsupported internal API.
