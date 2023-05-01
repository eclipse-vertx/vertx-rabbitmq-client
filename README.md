# RabbitMQ Client for Vert.x

[![Build Status](https://github.com/vert-x3/vertx-rabbitmq-client/workflows/CI/badge.svg?branch=master)](https://github.com/vert-x3/vertx-rabbitmq-client/actions?query=workflow%3ACI)

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
