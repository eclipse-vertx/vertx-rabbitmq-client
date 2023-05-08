# RabbitMQ Client for Vert.x

[![Build Status (5.x)](https://github.com/eclipse-vertx/vertx-rabbitmq-client/actions/workflows/ci-5.x.yml/badge.svg)](https://github.com/eclipse-vertx/vertx-rabbitmq-client/actions/workflows/ci-5.x.yml)

A Vert.x client allowing applications to interact with a RabbitMQ broker (AMQP 0.9.1)

# Getting Started

Please see the main documentation on the web-site for a full description:

* [Java documentation](https://vertx.io/docs/vertx-rabbitmq-client/java/)
* [JavaScript documentation](https://vertx.io/docs/vertx-rabbitmq-client/js/)
* [Kotlin documentation](https://vertx.io/docs/vertx-rabbitmq-client/kotlin/)
* [Groovy documentation](https://vertx.io/docs/vertx-rabbitmq-client/groovy/)
* [Ruby documentation](https://vertx.io/docs/vertx-rabbitmq-client/ruby/)

# Running the tests

The tests all use a instances of rabbit dynamically created in a local docker instance (i.e. the user running the tests must have permission to create docker containers).

```
% mvn test
```
