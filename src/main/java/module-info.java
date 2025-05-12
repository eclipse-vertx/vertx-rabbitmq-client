module io.vertx.rabbitmq.client {

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  requires com.rabbitmq.client;
  requires io.vertx.core;
  requires io.vertx.core.logging;
  requires java.logging;
  requires java.management;

  exports io.vertx.rabbitmq;

}
