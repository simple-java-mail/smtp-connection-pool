[![APACHE v2 License](https://img.shields.io/badge/license-apachev2-blue.svg?style=flat)](LICENSE-2.0.txt) [![Latest Release](https://img.shields.io/maven-central/v/org.simplejavamail/smtp-connection-pool.svg?style=flat)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.simplejavamail%22%20AND%20a%3A%22smtp-connection-pool%22) [![Build Status](https://img.shields.io/badge/CircleCI-build-brightgreen.svg?style=flat)](https://circleci.com/gh/simple-java-mail/smtp-connection-pool) [![Codacy](https://img.shields.io/codacy/grade/00571b6adbdb490b8cb18b175034f7b6.svg?style=flat)](https://www.codacy.com/app/b-bottema/smtp-connection-pool)

# smtp-connection-pool

smtp-connection-pool is a lightweight SMTP connection pool with clustering support, wait/release mechanism, 
connection lifecycle management, eager/lazy loading pool with auto-expiry policy support.

[API Documentation](https://www.javadoc.io/doc/org.simplejavamail/smtp-connection-pool/1.0.0)

## about

This library aims to improve performance for sending emails using Java Mail (now Jakarta Mail).

It represents three improvements over usual manual `Session.getTransport().connect()` approach:
   
   1. Support Transport (open) connection reuse over multiple threads
   2. Implement an SMTP connection pool so we have multiple reusable Transport connections (including lazy / eager initialization)
   3. Take performance to the next level and support clustered SMTP servers, so you can really scale up using multiple SMPT server if your use case requires it.

## Setup

Maven Dependency Setup

```xml
<dependency>
	<groupId>org.simplejavamail</groupId>
	<artifactId>smtp-connection-pool</artifactId>
	<version>1.0.1</version>
</dependency>
```

## Usage

This library builds on top of [clustered-object-pool](https://github.com/bbottema/clustered-object-pool).

There are a couple of scenario's you can solve with clustered-object-pool:
- Have **1 cluster with 1 pool of size 1**. Where you have one SMTP connection, but can share/reuse it among threads.
- Have **1 cluster with 1 pool of size n**. Where multiple resources are shared/reused among threads.
- Have **1 cluster with n pools of size 1**. If you have one cluster with rotating pools to draw a shareable/reusable SMTP connection from. Usefull when you want to spread load around different servers.
- Have **1 cluster with n pools of size n**. Same as above, except with multiple SMTP connections. For example multiple connections to multiple servers. 
- Have **n clusters ....** Same as all the above except you have dedicated clusters for different purposes. For example a cluster for handling internal mails and a cluster for outgoing mails. 

To keep API simple, this library provides both a simple Connection Pool class as well as a Clustered Connection Pool class.

#### Creating a simple SMTP connection pool

```java
// Simple on-demand (lazy loading) connection pool with default size of 4, 
// where the connections remain open until the pool is shut down.
SmtpConnectionPool pool = new SmtpConnectionPool(new SmtpClusterConfig());

PoolableObject<Transport> pollableTransport = pool.claimResourceFromCluster(session);
// ... send the email
pollableTransport.release(); // make available in the connection pool again
```
The pool looks like a cluster and you still claim connections from a cluster, but for each server (Session instance) a new cluster is defined under the hood so effectively nothing is clustered.

#### Creating a completely customized clustering SMTP connection pool

Let's see what options we have:

```java
SmtpClusterConfig smtpClusterConfig = new SmtpClusterConfig();
smtpClusterConfig.getConfigBuilder()
        .allocatorFactory(new MyCustomTransportAllocatorFactory())
        .defaultCorePoolSize(10) // eagerly start making up to 10 SMTP connections
        .defaultMaxPoolSize(10) // maximum pool size, after which claims become blocking
        // default is never-expire, this one closes connections randomly between 5 to 10 seconds after last use
        .defaultExpirationPolicy(new SpreadedTimeoutSinceLastAllocationExpirationPolicy<Transport>(5, 10, SECONDS)) 
        .cyclingStrategy(new RandomAccessCyclingStrategy()) // default is round-robin
        .claimTimeout(new Timeout(30, SECONDS)); // wait for available connection until max 30 seconds, default is indefinately
        
SmtpConnectionPoolClustered pool = new SmtpConnectionPoolClustered(smtpClusterConfig);
```

New clusters and pools are created on-demand with the global defaults, based on cluster keys (for example a UUID) and pool keys (Session instances) passed to the claim invocations. You can however... 

#### Configure different behavior for specific clusters and pools (servers)

```java
// continuing the above code example...
UUID keyCluster1 = UUID.randomUUID();
UUID keyCluster2 = UUID.randomUUID();

Session sessionServerA = ...;
Session sessionServerB = ...;

// define different behavior only for server A in cluster 1
pool.registerResourcePool(new ResourceClusterAndPoolKey<>(keyCluster1, sessionServerA),
    new TimeoutSinceCreationExpirationPolicy<Transport>(30, SECONDS),
    4, // core pool size of eagerly opened and available connections
    10); // max pool size
```