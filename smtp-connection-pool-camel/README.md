# Camel Mail adapter

This optional module makes Camel Mail select the `smtppool` Jakarta Mail facade. It contains no pool registry, allocator, delegate resolver, or independent failure policy; those stay in `smtp-connection-pool-jakarta-provider`.

It targets Camel Mail `4.21.x` and Java 17. It is available since `4.0.0`.

## See it run

Start with the real-server [Camel demo](../smtp-connection-pool-demo/src/main/java/org/simplejavamail/smtpconnectionpool/demo/CamelDemo.java). It sends three messages through the `smtppool:` component over one physical SMTP connection and proves that stopping Camel closes the component-owned pool. The [complete demo guide](../smtp-connection-pool-demo/README.md) explains how to run it.

## Setup

```xml
<dependency>
    <groupId>org.simplejavamail</groupId>
    <artifactId>smtp-connection-pool-camel</artifactId>
    <version>4.0.0</version>
</dependency>
```

The module registers two Camel component schemes:

- `smtppool:` keeps Camel's normal `smtp` configuration as the physical delegate.
- `smtppools:` keeps Camel's normal `smtps` configuration as the physical delegate.

```java
from("direct:mail")
    .to("smtppool://smtp.example.com:587"
            + "?username=user&password=secret"
            + "&from=sender@example.com&to=recipient@example.com");
```

All ordinary Camel Mail endpoint options are delegated to Camel's own configuration and generated property configurers. The adapter changes only the protocol used by `DefaultJavaMailSender.getTransport(Session)`.

To select a custom physical provider protocol, pass the normal provider property:

```java
to("smtppool://smtp.example.com:2525"
        + "?mail.smtppool.delegate.protocol=custom-smtp"
        + "&username=user&password=secret&to=recipient@example.com");
```

Internally created Jakarta Mail Sessions are tracked and shut down gracefully when the Camel component stops. Camel waits up to 30 seconds; on timeout or interruption it escalates to forced shutdown and also waits for physical cleanup before stop returns. If an externally supplied Session is used, its owner must call `SmtpPoolRegistry.shutdown(session)`.

Ordinary Camel `smtp:`/`smtps:` components and ordinary Jakarta Mail `session.getTransport("smtp")` lookups are untouched. This adapter never registers `PooledTransport` under `smtp` and never spoofs the physical provider procurement mechanism.
